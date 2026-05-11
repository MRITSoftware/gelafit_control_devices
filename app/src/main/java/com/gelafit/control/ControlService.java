package com.gelafit.control;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ControlService extends Service {
    private static final String CHANNEL_ID = "gelafit_control";
    private static final int NOTIFICATION_ID = 1042;
    private static final long SUPPORT_RELAUNCH_MS = 5 * 60 * 1000L;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private boolean running;
    private long lastSupportLaunchAt;

    private final Runnable loop = new Runnable() {
        @Override
        public void run() {
            if (!running) {
                return;
            }
            executor.execute(() -> {
                try {
                    tick();
                } catch (Exception e) {
                    tryUpdateStatus("error", e.getMessage());
                }
            });
            handler.postDelayed(this, 15000);
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        createChannel();
        startForeground(NOTIFICATION_ID, notification("Monitorando apps selecionados"));
        running = true;
        handler.post(loop);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        running = true;
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        running = false;
        handler.removeCallbacks(loop);
        executor.shutdownNow();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void tick() throws Exception {
        List<String> selected = AppConfig.getSelectedPackages(this);
        String activePackage = AppConfig.getActivePackage(this);
        if (hasSupabaseConfig()) {
            SupabaseClient client = new SupabaseClient(this);
            JSONObject device = client.fetchDevice();
            selected = syncSelectedAppsFromServer(device, selected);
            activePackage = syncActivePackageFromServer(device, activePackage, selected);
            applyCommandIfNeeded(client, device, selected);
            client.updateStatus("online", selected, activePackage, null);
        }
        maintainSelectedApps(selected, activePackage);
    }

    private void maintainSelectedApps(List<String> selected, String activePackage) {
        long now = SystemClock.elapsedRealtime();
        boolean relaunchSupport = lastSupportLaunchAt == 0 || now - lastSupportLaunchAt >= SUPPORT_RELAUNCH_MS;
        if (relaunchSupport) {
            for (String packageName : selected) {
                if (!packageName.equals(activePackage)) {
                    launchPackage(packageName);
                }
            }
            lastSupportLaunchAt = now;
        }
        if (activePackage != null && !activePackage.isEmpty()) {
            launchPackage(activePackage);
            return;
        }
        for (String packageName : selected) {
            launchPackage(packageName);
        }
    }

    private boolean hasSupabaseConfig() {
        return !AppConfig.getSupabaseUrl(this).trim().isEmpty()
                && !AppConfig.getSupabaseKey(this).trim().isEmpty();
    }

    private List<String> syncSelectedAppsFromServer(JSONObject device, List<String> fallback) {
        JSONArray apps = device.optJSONArray("selected_apps");
        if (apps == null) {
            return fallback;
        }
        ArrayList<String> selected = new ArrayList<>();
        for (int i = 0; i < apps.length(); i++) {
            String packageName = apps.optString(i, "");
            if (!packageName.isEmpty()) {
                selected.add(packageName);
            }
        }
        AppConfig.saveSelectedPackages(this, selected);
        return selected;
    }

    private String syncActivePackageFromServer(JSONObject device, String fallback, List<String> selected) {
        String activePackage = device.optString("active_package", "");
        if (activePackage.isEmpty()) {
            activePackage = fallback;
        }
        if (activePackage.isEmpty() && selected.size() == 1) {
            activePackage = selected.get(0);
        }
        if (!activePackage.isEmpty()) {
            AppConfig.saveActivePackage(this, activePackage);
        }
        return activePackage;
    }

    private void applyCommandIfNeeded(SupabaseClient client, JSONObject device, List<String> selected) throws Exception {
        long nonce = device.optLong("command_nonce", 0L);
        if (nonce <= AppConfig.getLastCommandNonce(this)) {
            return;
        }
        String command = device.optString("command", "");
        String targetPackage = device.optString("target_package", "");
        if ("open".equals(command) && !targetPackage.isEmpty()) {
            launchPackage(targetPackage);
        } else if ("restart".equals(command) && !targetPackage.isEmpty()) {
            softRestartPackage(targetPackage);
        } else if ("restart_selected".equals(command)) {
            for (String packageName : selected) {
                softRestartPackage(packageName);
            }
        } else if ("open_selected".equals(command)) {
            for (String packageName : selected) {
                launchPackage(packageName);
            }
        }
        AppConfig.setLastCommandNonce(this, nonce);
        client.markCommandDone(nonce);
    }

    private void softRestartPackage(String packageName) {
        Intent home = new Intent(Intent.ACTION_MAIN);
        home.addCategory(Intent.CATEGORY_HOME);
        home.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(home);
        handler.postDelayed(() -> launchPackage(packageName), 1200);
    }

    private void launchPackage(String packageName) {
        Intent launch = getPackageManager().getLaunchIntentForPackage(packageName);
        if (launch == null) {
            return;
        }
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        startActivity(launch);
    }

    private void tryUpdateStatus(String status, String error) {
        if (!hasSupabaseConfig()) {
            return;
        }
        try {
            new SupabaseClient(this).updateStatus(status, AppConfig.getSelectedPackages(this), AppConfig.getActivePackage(this), error);
        } catch (Exception ignored) {
        }
    }

    private Notification notification(String text) {
        Intent intent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this,
                0,
                intent,
                Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0);
        Notification.Builder builder = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, CHANNEL_ID)
                : new Notification.Builder(this);
        return builder
                .setContentTitle("GelaFit Control ativo")
                .setContentText(text)
                .setSmallIcon(android.R.drawable.stat_notify_sync)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .build();
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT < 26) {
            return;
        }
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "GelaFit Control",
                NotificationManager.IMPORTANCE_LOW);
        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        manager.createNotificationChannel(channel);
    }
}
