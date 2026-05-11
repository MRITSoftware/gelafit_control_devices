package com.gelafit.control;

import android.Manifest;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.ScrollView;
import android.widget.TextView;
import android.graphics.Color;
import android.graphics.Typeface;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class MainActivity extends android.app.Activity {
    private LinearLayout appsContainer;
    private EditText unitEmail;
    private EditText supabaseUrl;
    private EditText supabaseKey;
    private TextView deviceId;
    private final ArrayList<CheckBox> appChecks = new ArrayList<>();
    private final ArrayList<RadioButton> activeChecks = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        buildUi();
        requestRuntimePermissions();
        startController();
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(18), dp(18), dp(28));
        root.setBackgroundColor(Color.rgb(248, 250, 252));
        scroll.addView(root);

        TextView title = label("GelaFit Control", 24, true);
        root.addView(title);

        TextView hint = label("Selecione os apps que o tablet deve manter abertos e configure o Supabase.", 14, false);
        hint.setTextColor(Color.rgb(71, 85, 105));
        hint.setPadding(0, dp(4), 0, dp(16));
        root.addView(hint);

        deviceId = label("Device ID: " + AppConfig.getDeviceId(this), 13, false);
        deviceId.setTextColor(Color.rgb(15, 118, 110));
        root.addView(deviceId);

        unitEmail = input("E-mail da unidade", AppConfig.getUnitEmail(this));
        root.addView(unitEmail);

        supabaseUrl = input("Supabase URL", AppConfig.getSupabaseUrl(this));
        root.addView(supabaseUrl);

        supabaseKey = input("Supabase anon key", AppConfig.getSupabaseKey(this));
        root.addView(supabaseKey);

        Button save = button("Salvar e iniciar controle");
        save.setOnClickListener(v -> saveSettings());
        root.addView(save);

        Button battery = button("Liberar bateria 24/7");
        battery.setOnClickListener(v -> openBatterySettings());
        root.addView(battery);

        TextView appsTitle = label("Apps instalados", 18, true);
        appsTitle.setPadding(0, dp(18), 0, dp(8));
        root.addView(appsTitle);

        appsContainer = new LinearLayout(this);
        appsContainer.setOrientation(LinearLayout.VERTICAL);
        root.addView(appsContainer);
        renderApps();

        setContentView(scroll);
    }

    private void renderApps() {
        appsContainer.removeAllViews();
        appChecks.clear();
        activeChecks.clear();
        Set<String> selected = new HashSet<>(AppConfig.getSelectedPackages(this));
        String activePackage = AppConfig.getActivePackage(this);
        for (InstalledApp app : loadLaunchableApps(this)) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.VERTICAL);
            row.setPadding(0, dp(8), 0, dp(8));

            CheckBox check = new CheckBox(this);
            check.setText(app.label + "\n" + app.packageName);
            check.setTextSize(14);
            check.setTag(app.packageName);
            check.setChecked(selected.contains(app.packageName));
            appChecks.add(check);
            row.addView(check);

            RadioButton active = new RadioButton(this);
            active.setText("App principal (kiosk)");
            active.setTextSize(13);
            active.setTag(app.packageName);
            active.setChecked(app.packageName.equals(activePackage));
            active.setOnClickListener(v -> {
                for (RadioButton button : activeChecks) {
                    button.setChecked(button == v);
                }
                check.setChecked(true);
            });
            activeChecks.add(active);
            row.addView(active);

            appsContainer.addView(row);
        }
    }

    private void saveSettings() {
        ArrayList<String> selected = new ArrayList<>();
        for (CheckBox check : appChecks) {
            if (check.isChecked()) {
                selected.add((String) check.getTag());
            }
        }
        String activePackage = "";
        for (RadioButton active : activeChecks) {
            if (active.isChecked()) {
                activePackage = (String) active.getTag();
                if (!selected.contains(activePackage)) {
                    selected.add(activePackage);
                }
                break;
            }
        }
        AppConfig.saveSelectedPackages(this, selected);
        AppConfig.saveActivePackage(this, activePackage);
        AppConfig.prefs(this).edit()
                .putString("unit_email", unitEmail.getText().toString().trim())
                .putString("supabase_url", supabaseUrl.getText().toString().trim())
                .putString("supabase_key", supabaseKey.getText().toString().trim())
                .apply();
        startController();
        new AlertDialog.Builder(this)
                .setTitle("Controle ativo")
                .setMessage("O serviço vai sincronizar com o Supabase e manter os apps selecionados abertos.")
                .setPositiveButton("OK", null)
                .show();
    }

    private void requestRuntimePermissions() {
        if (Build.VERSION.SDK_INT >= 33) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 10);
        }
    }

    private void openBatterySettings() {
        try {
            Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
            intent.setData(Uri.parse("package:" + getPackageName()));
            startActivity(intent);
        } catch (Exception e) {
            startActivity(new Intent(Settings.ACTION_SETTINGS));
        }
    }

    private void startController() {
        Intent serviceIntent = new Intent(this, ControlService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
        }
    }

    static List<InstalledApp> loadLaunchableApps(Context context) {
        PackageManager pm = context.getPackageManager();
        Intent intent = new Intent(Intent.ACTION_MAIN, null);
        intent.addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> activities = pm.queryIntentActivities(intent, 0);
        ArrayList<InstalledApp> apps = new ArrayList<>();
        for (ResolveInfo info : activities) {
            ApplicationInfo appInfo = info.activityInfo.applicationInfo;
            String label = pm.getApplicationLabel(appInfo).toString();
            String packageName = info.activityInfo.packageName;
            if (!packageName.equals(context.getPackageName())) {
                apps.add(new InstalledApp(label, packageName));
            }
        }
        Collections.sort(apps, (a, b) -> a.label.compareToIgnoreCase(b.label));
        return apps;
    }

    private TextView label(String text, int sp, boolean bold) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(sp);
        view.setTextColor(Color.rgb(15, 23, 42));
        if (bold) {
            view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        }
        return view;
    }

    private EditText input(String hint, String value) {
        EditText input = new EditText(this);
        input.setHint(hint);
        input.setText(value);
        input.setSingleLine(true);
        input.setTextSize(14);
        input.setPadding(dp(10), dp(8), dp(10), dp(8));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, dp(10), 0, 0);
        input.setLayoutParams(params);
        return input;
    }

    private Button button(String text) {
        Button button = new Button(this);
        button.setText(text);
        button.setAllCaps(false);
        button.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(48));
        params.setMargins(0, dp(12), 0, 0);
        button.setLayoutParams(params);
        return button;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
