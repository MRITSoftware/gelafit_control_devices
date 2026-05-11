package com.gelafit.control;

import android.Manifest;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
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
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class MainActivity extends android.app.Activity {
    private static final String DEFAULT_SUPPORT_PACKAGE = "com.mritsoftware.mritserver";
    private static final String DEFAULT_KIOSK_PACKAGE = "com.mrit.gelafitgo";

    private LinearLayout appsContainer;
    private EditText unitEmail;
    private EditText searchApps;
    private TextView permissionStatus;
    private TextView supportSelection;
    private TextView kioskSelection;
    private Button permissionButton;
    private final ArrayList<InstalledApp> allApps = new ArrayList<>();
    private String supportDraft = "";
    private String kioskDraft = "";
    private boolean editingUnlocked;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        buildUi();
        if (hasRegisteredEmail() && !isFullyConfigured()) {
            requestRequiredPermissions();
        }
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
        root.setPadding(dp(20), dp(20), dp(20), dp(22));
        root.setBackgroundColor(Color.rgb(241, 245, 249));
        scroll.addView(root);

        TextView title = label("GelaFit Control", 24, true);
        root.addView(title);

        boolean registeredEmail = hasRegisteredEmail();
        TextView hint = label(registeredEmail
                ? "Selecione o MRIT Server e depois o app kiosk."
                : "Registre o e-mail da unidade para continuar.", 14, false);
        hint.setTextColor(Color.rgb(71, 85, 105));
        hint.setPadding(0, dp(4), 0, dp(14));
        root.addView(hint);

        if (isFullyConfigured() && !editingUnlocked) {
            renderOperationScreen(root);
            setContentView(scroll);
            return;
        }

        unitEmail = input("E-mail da unidade", AppConfig.getUnitEmail(this));
        root.addView(unitEmail);

        if (!registeredEmail) {
            Button registerEmail = button("Registrar unidade");
            registerEmail.setOnClickListener(v -> saveEmailAndContinue());
            root.addView(registerEmail);
            addFooter(root);
            setContentView(scroll);
            return;
        }

        permissionStatus = label("", 13, true);
        permissionStatus.setTextColor(Color.rgb(185, 28, 28));
        permissionStatus.setPadding(0, dp(8), 0, 0);
        root.addView(permissionStatus);

        permissionButton = button("Liberar permissões");
        permissionButton.setOnClickListener(v -> requestRequiredPermissions());
        root.addView(permissionButton);

        allApps.clear();
        allApps.addAll(loadLaunchableApps(this));
        loadDraftSelection();

        LinearLayout selectedBox = sectionBox();
        supportSelection = label("", 14, true);
        kioskSelection = label("", 14, true);
        selectedBox.addView(supportSelection);
        selectedBox.addView(kioskSelection);
        root.addView(selectedBox);
        updateSelectionSummary();

        TextView appsTitle = label(currentStepTitle(), 18, true);
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
        renderApps();

        Button save = button("Salvar e iniciar controle");
        save.setOnClickListener(v -> saveSettings());
        root.addView(save);

        addFooter(root);
        setContentView(scroll);
        refreshPermissionStatus();
    }

    private void loadDraftSelection() {
        List<String> selected = AppConfig.getSelectedPackages(this);
        kioskDraft = AppConfig.getActivePackage(this);
        supportDraft = "";
        for (String packageName : selected) {
            if (!packageName.equals(kioskDraft)) {
                supportDraft = packageName;
                break;
            }
        }
        if (supportDraft.isEmpty() && hasPackage(DEFAULT_SUPPORT_PACKAGE)) {
            supportDraft = DEFAULT_SUPPORT_PACKAGE;
        }
        if (kioskDraft.isEmpty() && hasPackage(DEFAULT_KIOSK_PACKAGE)) {
            kioskDraft = DEFAULT_KIOSK_PACKAGE;
        }
    }

    private void renderOperationScreen(LinearLayout root) {
        LinearLayout box = sectionBox();
        TextView status = label("GelaFit Control está em operação", 18, true);
        status.setTextColor(Color.rgb(15, 118, 110));
        TextView detail = label("O tablet está mantendo os apps configurados e ouvindo comandos remotos.", 14, false);
        detail.setTextColor(Color.rgb(71, 85, 105));
        detail.setPadding(0, dp(8), 0, 0);
        box.addView(status);
        box.addView(detail);
        root.addView(box);

        Button edit = button("Alterar configuração");
        edit.setOnClickListener(v -> askEmailToEdit());
        root.addView(edit);

        Button launch = button("Iniciar apps agora");
        launch.setOnClickListener(v -> {
            startController(ControlService.ACTION_LAUNCH_SELECTED);
            showMessage("Comando local enviado", "O MRIT Server será aberto e depois o kiosk voltará para frente.");
        });
        root.addView(launch);
        addFooter(root);
    }

    private void askEmailToEdit() {
        EditText email = input("E-mail da unidade", "");
        new AlertDialog.Builder(this)
                .setTitle("Confirmar unidade")
                .setView(email)
                .setPositiveButton("Continuar", (dialog, which) -> {
                    String typed = email.getText().toString().trim();
                    if (typed.equalsIgnoreCase(AppConfig.getUnitEmail(this).trim())) {
                        editingUnlocked = true;
                        buildUi();
                    } else {
                        showMessage("E-mail inválido", "Informe o e-mail cadastrado nesta unidade.");
                    }
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void renderApps() {
        appsContainer.removeAllViews();
        String query = searchApps == null ? "" : searchApps.getText().toString().trim().toLowerCase(Locale.US);
        boolean choosingKiosk = !supportDraft.isEmpty();
        for (InstalledApp app : allApps) {
            if (!query.isEmpty()
                    && !app.label.toLowerCase(Locale.US).contains(query)
                    && !app.packageName.toLowerCase(Locale.US).contains(query)) {
                continue;
            }
            if (choosingKiosk && app.packageName.equals(supportDraft)) {
                continue;
            }
            appsContainer.addView(appRow(app, choosingKiosk));
        }
        if (appsContainer.getChildCount() == 0) {
            TextView empty = label("Nenhum app encontrado.", 14, false);
            empty.setTextColor(Color.rgb(71, 85, 105));
            empty.setPadding(0, dp(12), 0, 0);
            appsContainer.addView(empty);
        }
    }

    private View appRow(InstalledApp app, boolean choosingKiosk) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(dp(12), dp(10), dp(12), dp(10));
        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        rowParams.setMargins(0, dp(8), 0, 0);
        row.setLayoutParams(rowParams);
        row.setBackground(cardBackground());

        TextView name = label(app.label, 15, true);
        TextView packageName = label(app.packageName, 12, false);
        packageName.setTextColor(Color.rgb(100, 116, 139));
        row.addView(name);
        row.addView(packageName);

        Button select = button(choosingKiosk ? "Selecionar como app kiosk" : "Selecionar como MRIT Server");
        select.setOnClickListener(v -> {
            if (choosingKiosk) {
                kioskDraft = app.packageName;
            } else {
                supportDraft = app.packageName;
                if (supportDraft.equals(kioskDraft)) {
                    kioskDraft = "";
                }
            }
            updateSelectionSummary();
            renderApps();
        });
        row.addView(select);
        return row;
    }

    private void updateSelectionSummary() {
        if (supportSelection == null || kioskSelection == null) {
            return;
        }
        supportSelection.setText("MRIT Server: " + displayPackage(supportDraft));
        kioskSelection.setText("App kiosk: " + displayPackage(kioskDraft));
        kioskSelection.setPadding(0, dp(6), 0, 0);
    }

    private String displayPackage(String packageName) {
        if (packageName == null || packageName.isEmpty()) {
            return "não selecionado";
        }
        for (InstalledApp app : allApps) {
            if (app.packageName.equals(packageName)) {
                return app.label + " (" + app.packageName + ")";
            }
        }
        return packageName;
    }

    private String currentStepTitle() {
        return supportDraft.isEmpty() ? "Selecione o MRIT Server" : "Selecione o app kiosk";
    }

    private void saveSettings() {
        String email = unitEmail.getText().toString().trim();
        if (email.isEmpty()) {
            showMessage("E-mail obrigatório", "Informe o e-mail da unidade antes de cadastrar.");
            return;
        }
        if (supportDraft.isEmpty()) {
            showMessage("MRIT Server obrigatório", "Selecione o app MRIT Server.");
            return;
        }
        if (kioskDraft.isEmpty()) {
            showMessage("Kiosk obrigatório", "Selecione o app kiosk.");
            return;
        }
        if (supportDraft.equals(kioskDraft)) {
            showMessage("Seleção inválida", "O MRIT Server e o app kiosk precisam ser apps diferentes.");
            return;
        }
        if (!hasRequiredPermissions()) {
            requestRequiredPermissions();
            showMessage("Permissões pendentes", "Libere as permissões solicitadas e toque em salvar novamente.");
            return;
        }
        ArrayList<String> selected = new ArrayList<>();
        selected.add(supportDraft);
        selected.add(kioskDraft);
        AppConfig.saveSelectedPackages(this, selected);
        AppConfig.saveActivePackage(this, kioskDraft);
        AppConfig.prefs(this).edit()
                .putString("unit_email", email)
                .putString("supabase_url", AppConfig.DEFAULT_SUPABASE_URL)
                .putString("supabase_key", AppConfig.DEFAULT_SUPABASE_KEY)
                .apply();
        AppConfig.setLastCommandNonce(this, 0L);
        startController(null);
        editingUnlocked = false;
        buildUi();
        showMessage(
                "Controle ativo",
                "Controle salvo. O MRIT Server abre primeiro e o kiosk volta para frente automaticamente.");
    }

    private void saveEmailAndContinue() {
        String email = unitEmail.getText().toString().trim();
        if (email.isEmpty()) {
            showMessage("E-mail obrigatório", "Informe o e-mail da unidade para continuar.");
            return;
        }
        AppConfig.prefs(this).edit()
                .putString("unit_email", email)
                .putString("supabase_url", AppConfig.DEFAULT_SUPABASE_URL)
                .putString("supabase_key", AppConfig.DEFAULT_SUPABASE_KEY)
                .apply();
        buildUi();
        requestRequiredPermissions();
    }

    private boolean hasRegisteredEmail() {
        return !AppConfig.getUnitEmail(this).trim().isEmpty();
    }

    private boolean isFullyConfigured() {
        return hasRegisteredEmail()
                && AppConfig.getSelectedPackages(this).size() >= 2
                && !AppConfig.getActivePackage(this).trim().isEmpty();
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
        permissionStatus.setVisibility(ready ? View.GONE : View.VISIBLE);
        permissionStatus.setText("Libere as permissões para manter o controle ativo.");
        permissionStatus.setTextColor(Color.rgb(185, 28, 28));
        if (permissionButton != null) {
            permissionButton.setVisibility(ready ? View.GONE : View.VISIBLE);
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

    private void startController(String action) {
        Intent serviceIntent = new Intent(this, ControlService.class);
        if (action != null) {
            serviceIntent.setAction(action);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
        }
    }

    private void showMessage(String title, String message) {
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton("OK", null)
                .show();
    }

    private void addFooter(LinearLayout root) {
        TextView footer = label("\u00A9 GelaFit \u2022 Tecnologia MRIT", 12, false);
        footer.setGravity(Gravity.CENTER);
        footer.setTextColor(Color.rgb(100, 116, 139));
        footer.setPadding(0, dp(24), 0, 0);
        root.addView(footer);
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

    private boolean hasPackage(String packageName) {
        for (InstalledApp app : allApps) {
            if (app.packageName.equals(packageName)) {
                return true;
            }
        }
        return false;
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
        input.setPadding(dp(12), dp(8), dp(12), dp(8));
        input.setBackground(cardBackground());
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
        button.setTextColor(Color.WHITE);
        button.setTextSize(14);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        String normalized = text.toLowerCase(Locale.US);
        if (normalized.contains("permiss")) {
            GradientDrawable bg = new GradientDrawable();
            bg.setColor(Color.WHITE);
            bg.setCornerRadius(dp(6));
            bg.setStroke(dp(1), Color.rgb(15, 118, 110));
            button.setBackground(bg);
            button.setTextColor(Color.rgb(15, 118, 110));
        } else {
            GradientDrawable bg = new GradientDrawable();
            bg.setColor(Color.rgb(15, 118, 110));
            bg.setCornerRadius(dp(6));
            button.setBackground(bg);
        }
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(48));
        params.setMargins(0, dp(12), 0, 0);
        button.setLayoutParams(params);
        return button;
    }

    private LinearLayout sectionBox() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(12), dp(12), dp(12), dp(12));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, dp(14), 0, 0);
        box.setLayoutParams(params);
        box.setBackground(cardBackground());
        return box;
    }

    private GradientDrawable cardBackground() {
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.WHITE);
        bg.setCornerRadius(dp(8));
        bg.setStroke(dp(1), Color.rgb(226, 232, 240));
        return bg;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
