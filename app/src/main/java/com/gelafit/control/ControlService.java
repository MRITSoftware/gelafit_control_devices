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
    static final String ACTION_LAUNCH_SELECTED = "com.gelafit.control.LAUNCH_SELECTED";
    private static final String CHANNEL_ID = "gelafit_control";
    private static final int NOTIFICATION_ID = 1042;
    private static final long KIOSK_DELAY_MS = 20 * 1000L;
    private static final long LOCAL_LOOP_MS = 1 * 1000L;
    private static final long STATUS_UPDATE_MS = 15 * 60 * 1000L;
    private static final long KIOSK_RELAUNCH_INTERVAL_MS = 3 * 1000L;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private boolean running;
    private boolean registered;
    private long lastStatusUpdateAt;
    private long lastErrorUpdateAt;
    private long kioskPausedUntil;
    private long lastKioskLaunchAt;
    private SupabaseRealtimeClient realtimeClient;

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
            handler.postDelayed(this, LOCAL_LOOP_MS);
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
        if (intent != null && ACTION_LAUNCH_SELECTED.equals(intent.getAction())) {
            executor.execute(() -> launchSupportThenKiosk(
                    AppConfig.getSelectedPackages(this),
                    AppConfig.getActivePackage(this),
                    false,
                    AppConfig.isKioskEnabled(this)));
        }
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        running = false;
        handler.removeCallbacks(loop);
        if (realtimeClient != null) {
            realtimeClient.stop();
            realtimeClient = null;
        }
        executor.shutdownNow();
        super.onDestroy();
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        Intent restart = new Intent(getApplicationContext(), ControlService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(restart);
        } else {
            startService(restart);
        }
        super.onTaskRemoved(rootIntent);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void tick() throws Exception {
        List<String> selected = AppConfig.getSelectedPackages(this);
        String activePackage = AppConfig.getActivePackage(this);
        boolean kioskEnabled = AppConfig.isKioskEnabled(this);
        if (hasSupabaseConfig()) {
            ensureRealtimeStarted();
            maybeRegisterAndUpdateStatus(selected, activePackage);
        }
        maintainSelectedApps(selected, activePackage, kioskEnabled);
    }

    private void ensureRealtimeStarted() {
        if (realtimeClient != null) {
            return;
        }
        realtimeClient = new SupabaseRealtimeClient(this, new SupabaseRealtimeClient.Listener() {
            @Override
            public void onDeviceChanged(JSONObject device) {
                executor.execute(() -> {
                    try {
                        List<String> selected = syncSelectedAppsFromServer(device, AppConfig.getSelectedPackages(ControlService.this));
                        syncActivePackageFromServer(device, AppConfig.getActivePackage(ControlService.this), selected);
                        syncKioskEnabledFromServer(device);
                        applyCommandIfNeeded(new SupabaseClient(ControlService.this), device, selected);
                    } catch (Exception e) {
                        tryUpdateStatus("error", e.getMessage());
                    }
                });
            }

            @Override
            public void onRealtimeError(String error) {
                tryUpdateStatus("realtime_error", error);
            }
        });
        realtimeClient.start();
    }

    private void maybeRegisterAndUpdateStatus(List<String> selected, String activePackage) throws Exception {
        long now = SystemClock.elapsedRealtime();
        SupabaseClient client = new SupabaseClient(this);
        if (!registered) {
            JSONObject device = client.fetchDevice();
            selected = syncSelectedAppsFromServer(device, selected);
            activePackage = syncActivePackageFromServer(device, activePackage, selected);
            syncKioskEnabledFromServer(device);
            applyCommandIfNeeded(client, device, selected);
            client.updateStatus("online", selected, activePackage, null);
            registered = true;
            lastStatusUpdateAt = now;
            return;
        }
        if (now - lastStatusUpdateAt >= STATUS_UPDATE_MS) {
            client.updateStatus("online", selected, activePackage, null);
            lastStatusUpdateAt = now;
        }
    }

    private void maintainSelectedApps(List<String> selected, String activePackage, boolean kioskEnabled) {
        if (selected.isEmpty()) {
            return;
        }
        long now = SystemClock.elapsedRealtime();
        if (kioskEnabled
                && activePackage != null
                && !activePackage.isEmpty()
                && now >= kioskPausedUntil
                && now - lastKioskLaunchAt >= KIOSK_RELAUNCH_INTERVAL_MS) {
            launchPackage(activePackage);
            lastKioskLaunchAt = now;
        }
    }

    private boolean hasSupabaseConfig() {
        return !AppConfig.getSupabaseUrl(this).trim().isEmpty()
                && !AppConfig.getSupabaseKey(this).trim().isEmpty()
                && !AppConfig.getUnitEmail(this).trim().isEmpty();
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

    private boolean syncKioskEnabledFromServer(JSONObject device) {
        boolean enabled = device.optBoolean("kiosk_enabled", true);
        AppConfig.saveKioskEnabled(this, enabled);
        return enabled;
    }

    private void applyCommandIfNeeded(SupabaseClient client, JSONObject device, List<String> selected) throws Exception {
        long nonce = device.optLong("command_nonce", 0L);
        if (nonce <= AppConfig.getLastCommandNonce(this)) {
            return;
        }
        String command = device.optString("command", "");
        String targetPackage = device.optString("target_package", "");
        String activePackage = AppConfig.getActivePackage(this);
        boolean kioskEnabled = AppConfig.isKioskEnabled(this);
        if ("open".equals(command) && !targetPackage.isEmpty()) {
            launchPackage(targetPackage);
            if (kioskEnabled) {
                returnToKioskAfterSupport(targetPackage, activePackage);
            }
        } else if ("restart".equals(command) && !targetPackage.isEmpty()) {
            softRestartPackage(targetPackage);
            if (kioskEnabled) {
                returnToKioskAfterSupport(targetPackage, activePackage);
            }
        } else if ("restart_selected".equals(command)) {
            launchSupportThenKiosk(selected, activePackage, true, kioskEnabled);
        } else if ("open_selected".equals(command)) {
            launchSupportThenKiosk(selected, activePackage, false, kioskEnabled);
        }
        AppConfig.setLastCommandNonce(this, nonce);
        client.markCommandDone(nonce);
    }

    private void launchSupportThenKiosk(List<String> selected, String activePackage, boolean restart, boolean kioskEnabled) {
        boolean openedSupport = false;
        for (String packageName : selected) {
            if (!packageName.equals(activePackage)) {
                if (restart) {
                    softRestartPackage(packageName);
                } else {
                    launchPackage(packageName);
                }
                openedSupport = true;
            }
        }
        if (activePackage != null && !activePackage.isEmpty()) {
            if (openedSupport) {
                if (kioskEnabled) {
                    pauseKioskBriefly();
                    handler.postDelayed(() -> launchPackage(activePackage), KIOSK_DELAY_MS);
                }
            } else if (restart) {
                softRestartPackage(activePackage);
            } else {
                launchPackage(activePackage);
            }
        }
    }

    private void returnToKioskAfterSupport(String targetPackage, String activePackage) {
        if (activePackage == null || activePackage.isEmpty() || activePackage.equals(targetPackage)) {
            return;
        }
        pauseKioskBriefly();
        handler.postDelayed(() -> launchPackage(activePackage), KIOSK_DELAY_MS);
    }

    private void pauseKioskBriefly() {
        kioskPausedUntil = SystemClock.elapsedRealtime() + KIOSK_DELAY_MS;
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
        long now = SystemClock.elapsedRealtime();
        if (now - lastErrorUpdateAt < STATUS_UPDATE_MS) {
            return;
        }
        lastErrorUpdateAt = now;
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
