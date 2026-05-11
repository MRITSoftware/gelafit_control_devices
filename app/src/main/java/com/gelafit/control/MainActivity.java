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
import android.os.PowerManager;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
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
import java.util.Locale;
import java.util.Set;

public class MainActivity extends android.app.Activity {
    private LinearLayout appsContainer;
    private EditText unitEmail;
    private EditText searchApps;
    private TextView deviceId;
    private TextView permissionStatus;
    private final ArrayList<CheckBox> appChecks = new ArrayList<>();
    private final ArrayList<RadioButton> activeChecks = new ArrayList<>();
    private final ArrayList<InstalledApp> allApps = new ArrayList<>();
    private final Set<String> selectedDraft = new HashSet<>();
    private String activeDraft = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        buildUi();
        requestRequiredPermissions();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshPermissionStatus();
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

        TextView hint = label("Informe o e-mail da unidade, selecione 2 apps e marque qual deles e o kiosk.", 14, false);
        hint.setTextColor(Color.rgb(71, 85, 105));
        hint.setPadding(0, dp(4), 0, dp(16));
        root.addView(hint);

        deviceId = label("Device ID: " + AppConfig.getDeviceId(this), 13, false);
        deviceId.setTextColor(Color.rgb(15, 118, 110));
        root.addView(deviceId);

        unitEmail = input("E-mail da unidade", AppConfig.getUnitEmail(this));
        root.addView(unitEmail);

        TextView supabaseInfo = label("Supabase configurado automaticamente", 13, false);
        supabaseInfo.setTextColor(Color.rgb(71, 85, 105));
        supabaseInfo.setPadding(0, dp(8), 0, 0);
        root.addView(supabaseInfo);

        permissionStatus = label("", 13, false);
        permissionStatus.setTextColor(Color.rgb(185, 28, 28));
        permissionStatus.setPadding(0, dp(8), 0, 0);
        root.addView(permissionStatus);

        Button save = button("Salvar e iniciar controle");
        save.setOnClickListener(v -> saveSettings());
        root.addView(save);

        Button battery = button("Liberar bateria 24/7");
        battery.setOnClickListener(v -> requestRequiredPermissions());
        root.addView(battery);

        TextView appsTitle = label("Apps instalados", 18, true);
        appsTitle.setPadding(0, dp(18), 0, dp(8));
        root.addView(appsTitle);

        searchApps = input("Pesquisar app", "");
        searchApps.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                renderApps();
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });
        root.addView(searchApps);

        appsContainer = new LinearLayout(this);
        appsContainer.setOrientation(LinearLayout.VERTICAL);
        root.addView(appsContainer);
        allApps.clear();
        allApps.addAll(loadLaunchableApps(this));
        selectedDraft.clear();
        selectedDraft.addAll(AppConfig.getSelectedPackages(this));
        activeDraft = AppConfig.getActivePackage(this);
        renderApps();

        setContentView(scroll);
        refreshPermissionStatus();
    }

    private void renderApps() {
        appsContainer.removeAllViews();
        appChecks.clear();
        activeChecks.clear();
        String query = searchApps == null ? "" : searchApps.getText().toString().trim().toLowerCase(Locale.US);
        for (InstalledApp app : allApps) {
            if (!query.isEmpty()
                    && !app.label.toLowerCase(Locale.US).contains(query)
                    && !app.packageName.toLowerCase(Locale.US).contains(query)) {
                continue;
            }
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.VERTICAL);
            row.setPadding(0, dp(8), 0, dp(8));

            CheckBox check = new CheckBox(this);
            check.setText(app.label + "\n" + app.packageName);
            check.setTextSize(14);
            check.setTag(app.packageName);
            check.setChecked(selectedDraft.contains(app.packageName));
            check.setOnCheckedChangeListener((buttonView, isChecked) -> {
                String packageName = (String) buttonView.getTag();
                if (isChecked) {
                    if (selectedDraft.size() >= 2 && !selectedDraft.contains(packageName)) {
                        buttonView.setChecked(false);
                        showMessage("Selecao obrigatoria", "Escolha exatamente 2 apps: um suporte e um kiosk.");
                        return;
                    }
                    selectedDraft.add(packageName);
                } else {
                    selectedDraft.remove(packageName);
                    if (packageName.equals(activeDraft)) {
                        activeDraft = "";
                    }
                }
                renderApps();
            });
            appChecks.add(check);
            row.addView(check);

            RadioButton active = new RadioButton(this);
            active.setText("App principal (kiosk)");
            active.setTextSize(13);
            active.setTag(app.packageName);
            active.setChecked(app.packageName.equals(activeDraft));
            active.setEnabled(selectedDraft.contains(app.packageName));
            active.setOnClickListener(v -> {
                String packageName = (String) v.getTag();
                if (!selectedDraft.contains(packageName)) {
                    showMessage("Selecione o app primeiro", "O kiosk precisa ser um dos 2 apps escolhidos.");
                    return;
                }
                activeDraft = packageName;
                renderApps();
            });
            activeChecks.add(active);
            row.addView(active);

            appsContainer.addView(row);
        }
        if (appsContainer.getChildCount() == 0) {
            TextView empty = label("Nenhum app encontrado.", 14, false);
            empty.setTextColor(Color.rgb(71, 85, 105));
            appsContainer.addView(empty);
        }
    }

    private void saveSettings() {
        String email = unitEmail.getText().toString().trim();
        if (email.isEmpty()) {
            showMessage("E-mail obrigatorio", "Informe o e-mail da unidade antes de cadastrar.");
            return;
        }
        if (selectedDraft.size() != 2) {
            showMessage("Selecao obrigatoria", "Escolha exatamente 2 apps: um suporte e um kiosk.");
            return;
        }
        if (activeDraft.isEmpty() || !selectedDraft.contains(activeDraft)) {
            showMessage("Kiosk obrigatorio", "Marque qual dos 2 apps escolhidos sera o kiosk.");
            return;
        }
        if (!hasRequiredPermissions()) {
            requestRequiredPermissions();
            showMessage("Permissoes pendentes", "Libere as permissoes solicitadas e toque em salvar novamente.");
            return;
        }
        ArrayList<String> selected = orderedSelectedPackages();
        AppConfig.saveSelectedPackages(this, selected);
        AppConfig.saveActivePackage(this, activeDraft);
        AppConfig.prefs(this).edit()
                .putString("unit_email", email)
                .putString("supabase_url", AppConfig.DEFAULT_SUPABASE_URL)
                .putString("supabase_key", AppConfig.DEFAULT_SUPABASE_KEY)
                .apply();
        AppConfig.setLastCommandNonce(this, 0L);
        startController();
        new AlertDialog.Builder(this)
                .setTitle("Controle ativo")
                .setMessage("O serviço vai sincronizar com o Supabase e manter os apps selecionados abertos.")
                .setPositiveButton("OK", null)
                .show();
    }

    private void requestRequiredPermissions() {
        if (Build.VERSION.SDK_INT >= 33) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 10);
        }
        if (Build.VERSION.SDK_INT >= 23 && !Settings.canDrawOverlays(this)) {
            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION);
            intent.setData(Uri.parse("package:" + getPackageName()));
            startActivity(intent);
            return;
        }
        if (!isIgnoringBatteryOptimizations()) {
            openBatterySettings();
        }
        refreshPermissionStatus();
    }

    private boolean hasRequiredPermissions() {
        return (Build.VERSION.SDK_INT < 23 || Settings.canDrawOverlays(this))
                && isIgnoringBatteryOptimizations();
    }

    private boolean isIgnoringBatteryOptimizations() {
        if (Build.VERSION.SDK_INT < 23) {
            return true;
        }
        PowerManager powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
        return powerManager != null && powerManager.isIgnoringBatteryOptimizations(getPackageName());
    }

    private void refreshPermissionStatus() {
        if (permissionStatus == null) {
            return;
        }
        boolean ready = hasRequiredPermissions();
        permissionStatus.setText(ready
                ? "Permissoes principais liberadas"
                : "Antes de salvar, libere sobreposicao e bateria 24/7.");
        permissionStatus.setTextColor(ready ? Color.rgb(21, 128, 61) : Color.rgb(185, 28, 28));
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

    private ArrayList<String> orderedSelectedPackages() {
        ArrayList<String> selected = new ArrayList<>();
        for (InstalledApp app : allApps) {
            if (selectedDraft.contains(app.packageName)) {
                selected.add(app.packageName);
            }
        }
        return selected;
    }

    private void showMessage(String title, String message) {
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton("OK", null)
                .show();
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
