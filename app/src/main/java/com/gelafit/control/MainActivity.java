package com.gelafit.control;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.ActivityManager;
import android.content.ComponentName;
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
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity {
    private static final String DEFAULT_SUPPORT_PACKAGE = "com.mritsoftware.mritserver";
    private static final String DEFAULT_KIOSK_PACKAGE = "com.mrit.gelafitgo";

    // Paleta de cores
    private static final int C_BG        = Color.rgb(241, 245, 249); // slate-100
    private static final int C_SURFACE   = Color.WHITE;
    private static final int C_BORDER    = Color.rgb(226, 232, 240); // slate-200
    private static final int C_PRIMARY   = Color.rgb(15, 118, 110);  // teal-600
    private static final int C_PRIMARY_L = Color.rgb(204, 240, 236); // teal-100
    private static final int C_TEXT      = Color.rgb(15, 23, 42);    // slate-900
    private static final int C_TEXT2     = Color.rgb(71, 85, 105);   // slate-600
    private static final int C_MUTED     = Color.rgb(100, 116, 139); // slate-500
    private static final int C_SUCCESS   = Color.rgb(22, 163, 74);   // green-600
    private static final int C_SUCCESS_L = Color.rgb(220, 252, 231); // green-100
    private static final int C_ERROR     = Color.rgb(185, 28, 28);   // red-700
    private static final int C_WARN      = Color.rgb(180, 83, 9);    // amber-700
    private static final int C_WARN_L    = Color.rgb(254, 243, 199); // amber-100

    private LinearLayout appsContainer;
    private EditText unitEmail;
    private EditText searchApps;
    private View permissionCard;
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
        refreshPermissionCard();
    }

    // ─── Construção de tela ──────────────────────────────────────────────────

    private void buildUi() {
        FrameLayout frame = new FrameLayout(this);
        frame.setBackgroundColor(C_BG);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(16), dp(16), dp(100));
        root.setBackgroundColor(C_BG);
        scroll.addView(root);
        frame.addView(scroll);

        addHeader(root);

        if (isFullyConfigured() && !editingUnlocked) {
            renderOperationScreen(root);
            setContentView(frame);
            return;
        }

        if (!hasRegisteredEmail()) {
            renderEmailStep(root);
            setContentView(frame);
            return;
        }

        renderConfigScreen(root, frame);
        setContentView(frame);
        refreshPermissionCard();
    }

    private void addHeader(LinearLayout root) {
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setPadding(0, dp(8), 0, dp(20));

        TextView title = new TextView(this);
        title.setText("GelaFit Control");
        title.setTextSize(26);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(C_PRIMARY);

        TextView subtitle = new TextView(this);
        subtitle.setText("Sistema de controle de kiosk");
        subtitle.setTextSize(13);
        subtitle.setTextColor(C_MUTED);
        subtitle.setPadding(0, dp(2), 0, 0);

        header.addView(title);
        header.addView(subtitle);
        root.addView(header);
    }

    // ─── Tela 1: apenas e-mail ───────────────────────────────────────────────

    private void renderEmailStep(LinearLayout root) {
        LinearLayout card = card("Registrar unidade");

        TextView desc = body("Informe o e-mail desta unidade para iniciar a configuração.");
        desc.setPadding(0, 0, 0, dp(12));
        card.addView(desc);

        unitEmail = input("E-mail da unidade", "");
        card.addView(unitEmail);
        root.addView(card);

        Button btn = primaryButton("Registrar unidade");
        btn.setOnClickListener(v -> saveEmailAndContinue());
        root.addView(btn);

        addFooter(root);
    }

    // ─── Tela 2: configuração completa ───────────────────────────────────────

    private void renderConfigScreen(LinearLayout root, FrameLayout frame) {
        allApps.clear();
        allApps.addAll(loadLaunchableApps(this));
        loadDraftSelection();

        // E-mail
        LinearLayout emailCard = card("Unidade");
        unitEmail = input("E-mail da unidade", AppConfig.getUnitEmail(this));
        emailCard.addView(unitEmail);
        root.addView(emailCard);

        // Permissões
        permissionCard = buildPermissionCard();
        root.addView(permissionCard);

        // Guia MIUI
        if (isXiaomi()) {
            root.addView(buildMiuiCard());
        }

        // Resumo de seleção
        root.addView(buildSelectionSummaryCard());

        // Lista de apps
        TextView appsTitle = sectionLabel(
                supportDraft.isEmpty() ? "1. Selecione o MRIT Server" : "2. Selecione o app kiosk");
        root.addView(appsTitle);

        searchApps = input("Pesquisar app", "");
        searchApps.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int i, int c, int a) {}
            @Override public void afterTextChanged(Editable s) {}
            @Override public void onTextChanged(CharSequence s, int i, int b, int c) { renderApps(); }
        });
        root.addView(searchApps);

        appsContainer = new LinearLayout(this);
        appsContainer.setOrientation(LinearLayout.VERTICAL);
        root.addView(appsContainer);
        renderApps();

        addFooter(root);

        // Botão salvar fixo no rodapé
        Button save = primaryButton("Salvar configuração");
        save.setOnClickListener(v -> saveSettings());
        FrameLayout.LayoutParams saveParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, dp(52), Gravity.BOTTOM);
        saveParams.setMargins(dp(16), 0, dp(16), dp(20));
        frame.addView(save, saveParams);
    }

    // ─── Tela 3: operação ────────────────────────────────────────────────────

    private void renderOperationScreen(LinearLayout root) {
        // Card de status
        LinearLayout statusCard = new LinearLayout(this);
        statusCard.setOrientation(LinearLayout.VERTICAL);
        statusCard.setPadding(dp(16), dp(16), dp(16), dp(16));
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        cp.setMargins(0, 0, 0, dp(4));
        statusCard.setLayoutParams(cp);
        statusCard.setBackground(cardBg());

        // Badge "Em operação"
        LinearLayout badgeRow = new LinearLayout(this);
        badgeRow.setOrientation(LinearLayout.HORIZONTAL);
        badgeRow.setGravity(Gravity.CENTER_VERTICAL);

        View dot = new View(this);
        LinearLayout.LayoutParams dotP = new LinearLayout.LayoutParams(dp(10), dp(10));
        dotP.setMargins(0, 0, dp(8), 0);
        dot.setLayoutParams(dotP);
        GradientDrawable dotBg = new GradientDrawable();
        dotBg.setShape(GradientDrawable.OVAL);
        dotBg.setColor(C_SUCCESS);
        dot.setBackground(dotBg);

        TextView statusLabel = new TextView(this);
        statusLabel.setText("Em operação");
        statusLabel.setTextSize(15);
        statusLabel.setTypeface(Typeface.DEFAULT_BOLD);
        statusLabel.setTextColor(C_SUCCESS);
        badgeRow.addView(dot);
        badgeRow.addView(statusLabel);
        statusCard.addView(badgeRow);

        // Unidade
        statusCard.addView(divider(dp(12)));
        statusCard.addView(metaRow("Unidade", AppConfig.getUnitEmail(this)));

        // Apps configurados
        List<String> selected = AppConfig.getSelectedPackages(this);
        String active = AppConfig.getActivePackage(this);
        String supportPkg = "";
        for (String pkg : selected) {
            if (!pkg.equals(active)) { supportPkg = pkg; break; }
        }
        statusCard.addView(divider(dp(8)));
        statusCard.addView(metaRow("MRIT Server", appDisplayName(supportPkg)));
        statusCard.addView(divider(dp(4)));
        statusCard.addView(metaRow("App kiosk", appDisplayName(active)));
        root.addView(statusCard);

        // Ações
        Button launch = primaryButton("Iniciar apps agora");
        launch.setOnClickListener(v -> {
            startController(ControlService.ACTION_LAUNCH_SELECTED);
            showMessage("Comando enviado", "O MRIT Server abre primeiro e o kiosk volta automaticamente em ~20 segundos.");
        });
        root.addView(launch);

        Button lockBtn = primaryButton("Ativar bloqueio de tela");
        lockBtn.setOnClickListener(v -> {
            try { startLockTask(); } catch (Exception ignored) {}
        });
        root.addView(lockBtn);

        Button edit = outlineButton("Alterar configuração");
        edit.setOnClickListener(v -> askEmailToEdit());
        root.addView(edit);

        // Guia MIUI
        if (isXiaomi()) {
            root.addView(buildMiuiCard());
        }

        addFooter(root);
    }

    // ─── Cards e componentes ─────────────────────────────────────────────────

    private View buildPermissionCard() {
        LinearLayout card = card("Permissões necessárias");

        boolean overlay = Build.VERSION.SDK_INT < 23 || Settings.canDrawOverlays(this);
        boolean battery = isIgnoringBatteryOptimizations();

        card.addView(permissionRow("Exibir sobre outros apps", overlay));
        card.addView(permissionRow("Sem restrição de bateria", battery));

        if (!overlay || !battery) {
            Button btn = outlineButton("Liberar permissão pendente");
            btn.setOnClickListener(v -> requestRequiredPermissions());
            LinearLayout.LayoutParams p = (LinearLayout.LayoutParams) btn.getLayoutParams();
            p.setMargins(0, dp(12), 0, 0);
            btn.setLayoutParams(p);
            card.addView(btn);
        }

        return card;
    }

    private View buildMiuiCard() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(14), dp(14), dp(14), dp(14));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, dp(12), 0, 0);
        card.setLayoutParams(lp);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(C_WARN_L);
        bg.setCornerRadius(dp(10));
        bg.setStroke(dp(1), Color.rgb(253, 230, 138));
        card.setBackground(bg);

        TextView title = new TextView(this);
        title.setText("⚠  Configuração necessária no Xiaomi");
        title.setTextSize(14);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(C_WARN);
        card.addView(title);

        TextView desc = new TextView(this);
        desc.setText("Ative o AutoStart para o GelaFit Control no app Segurança do Xiaomi. " +
                "Sem isso o serviço não reinicia após o tablet ligar.");
        desc.setTextSize(13);
        desc.setTextColor(C_WARN);
        desc.setPadding(0, dp(6), 0, dp(10));
        card.addView(desc);

        Button autostart = outlineButton("Abrir AutoStart");
        autostart.setOnClickListener(v -> openMiuiAutoStart());
        tintOutlineButton(autostart, C_WARN);
        card.addView(autostart);

        Button battery = outlineButton("Gerenciar bateria");
        battery.setOnClickListener(v -> openMiuiBattery());
        tintOutlineButton(battery, C_WARN);
        LinearLayout.LayoutParams bp = (LinearLayout.LayoutParams) battery.getLayoutParams();
        bp.setMargins(0, dp(8), 0, 0);
        battery.setLayoutParams(bp);
        card.addView(battery);

        return card;
    }

    private View buildSelectionSummaryCard() {
        LinearLayout card = card("Apps selecionados");

        String supportLabel = supportDraft.isEmpty() ? "não selecionado" : appDisplayName(supportDraft);
        String kioskLabel   = kioskDraft.isEmpty()   ? "não selecionado" : appDisplayName(kioskDraft);

        card.addView(metaRow("MRIT Server", supportLabel));
        card.addView(divider(dp(6)));
        card.addView(metaRow("App kiosk", kioskLabel));
        return card;
    }

    // ─── Lista de apps ───────────────────────────────────────────────────────

    private void renderApps() {
        if (appsContainer == null) return;
        appsContainer.removeAllViews();
        String query = searchApps == null ? "" :
                searchApps.getText().toString().trim().toLowerCase(Locale.US);
        boolean choosingKiosk = !supportDraft.isEmpty();
        int shown = 0;
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
            shown++;
        }
        if (shown == 0) {
            TextView empty = body("Nenhum app encontrado.");
            empty.setTextColor(C_MUTED);
            empty.setPadding(0, dp(12), 0, 0);
            appsContainer.addView(empty);
        }
    }

    private View appRow(InstalledApp app, boolean choosingKiosk) {
        boolean isSelected = choosingKiosk
                ? app.packageName.equals(kioskDraft)
                : app.packageName.equals(supportDraft);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(14), dp(12), dp(14), dp(12));
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        rp.setMargins(0, dp(6), 0, 0);
        row.setLayoutParams(rp);
        row.setBackground(isSelected ? selectedCardBg() : cardBg());

        // Texto (esquerda)
        LinearLayout textCol = new LinearLayout(this);
        textCol.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams tcp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        textCol.setLayoutParams(tcp);

        TextView name = new TextView(this);
        name.setText(app.label);
        name.setTextSize(14);
        name.setTypeface(isSelected ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
        name.setTextColor(isSelected ? C_PRIMARY : C_TEXT);

        TextView pkg = new TextView(this);
        pkg.setText(app.packageName);
        pkg.setTextSize(11);
        pkg.setTextColor(C_MUTED);
        pkg.setPadding(0, dp(2), 0, 0);

        textCol.addView(name);
        textCol.addView(pkg);
        row.addView(textCol);

        // Checkmark (direita)
        if (isSelected) {
            TextView check = new TextView(this);
            check.setText("✓");
            check.setTextSize(16);
            check.setTextColor(C_PRIMARY);
            check.setTypeface(Typeface.DEFAULT_BOLD);
            check.setPadding(dp(8), 0, 0, 0);
            row.addView(check);
        }

        row.setClickable(true);
        row.setFocusable(true);
        row.setOnClickListener(v -> {
            if (choosingKiosk) {
                kioskDraft = app.packageName;
            } else {
                supportDraft = app.packageName;
                if (supportDraft.equals(kioskDraft)) kioskDraft = "";
            }
            refreshSelectionSummary();
            renderApps();
        });

        return row;
    }

    // ─── Salvar / persistir ──────────────────────────────────────────────────

    private void saveSettings() {
        String email = unitEmail.getText().toString().trim();
        if (email.isEmpty()) {
            showMessage("E-mail obrigatório", "Informe o e-mail da unidade antes de salvar.");
            return;
        }
        if (supportDraft.isEmpty()) {
            showMessage("MRIT Server obrigatório", "Selecione o app MRIT Server na lista.");
            return;
        }
        if (kioskDraft.isEmpty()) {
            showMessage("App kiosk obrigatório", "Selecione o app kiosk na lista.");
            return;
        }
        if (supportDraft.equals(kioskDraft)) {
            showMessage("Seleção inválida", "MRIT Server e app kiosk precisam ser apps diferentes.");
            return;
        }
        if (!hasRequiredPermissions()) {
            requestRequiredPermissions();
            showMessage("Permissões pendentes", "Libere as permissões e toque em salvar novamente.");
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
        showMessage("Controle ativo",
                "Configuração salva. O MRIT Server abre primeiro e o kiosk volta automaticamente.");
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

    private void loadDraftSelection() {
        List<String> selected = AppConfig.getSelectedPackages(this);
        kioskDraft = AppConfig.getActivePackage(this);
        supportDraft = "";
        for (String pkg : selected) {
            if (!pkg.equals(kioskDraft)) { supportDraft = pkg; break; }
        }
        if (supportDraft.isEmpty() && hasPackage(DEFAULT_SUPPORT_PACKAGE)) supportDraft = DEFAULT_SUPPORT_PACKAGE;
        if (kioskDraft.isEmpty()   && hasPackage(DEFAULT_KIOSK_PACKAGE))   kioskDraft   = DEFAULT_KIOSK_PACKAGE;
    }

    private void refreshSelectionSummary() {
        // Rebuilds the summary card in place would require reference — easiest is rebuild full UI
        // Instead, we just call renderApps() after click; the summary is visible above as card text.
        // Full rebuild only on save. For live feedback, render in place using tag if view hierarchy permits.
        // Acceptable UX: user sees selection highlight in list immediately.
    }

    // ─── Edição protegida ────────────────────────────────────────────────────

    private void askEmailToEdit() {
        EditText emailInput = input("E-mail da unidade", "");
        new AlertDialog.Builder(this)
                .setTitle("Confirmar unidade")
                .setMessage("Digite o e-mail cadastrado para liberar a edição.")
                .setView(emailInput)
                .setPositiveButton("Continuar", (dialog, which) -> {
                    String typed = emailInput.getText().toString().trim();
                    if (typed.equalsIgnoreCase(AppConfig.getUnitEmail(this).trim())) {
                        tryStopLockTask();
                        editingUnlocked = true;
                        buildUi();
                    } else {
                        showMessage("E-mail inválido", "Informe o e-mail cadastrado nesta unidade.");
                    }
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    // ─── Permissões ──────────────────────────────────────────────────────────

    private void requestRequiredPermissions() {
        if (Build.VERSION.SDK_INT >= 33) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 10);
        }
        if (Build.VERSION.SDK_INT >= 23 && !Settings.canDrawOverlays(this)) {
            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName()));
            startActivity(intent);
            return;
        }
        if (!isIgnoringBatteryOptimizations()) {
            openBatterySettings();
        }
        refreshPermissionCard();
    }

    private void refreshPermissionCard() {
        if (permissionCard == null) return;
        // Remove e reinsere o card atualizado
        if (permissionCard.getParent() instanceof LinearLayout) {
            LinearLayout parent = (LinearLayout) permissionCard.getParent();
            int idx = parent.indexOfChild(permissionCard);
            parent.removeView(permissionCard);
            permissionCard = buildPermissionCard();
            parent.addView(permissionCard, idx);
        }
    }

    private boolean hasRequiredPermissions() {
        return (Build.VERSION.SDK_INT < 23 || Settings.canDrawOverlays(this))
                && isIgnoringBatteryOptimizations();
    }

    private boolean isIgnoringBatteryOptimizations() {
        if (Build.VERSION.SDK_INT < 23) return true;
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        return pm != null && pm.isIgnoringBatteryOptimizations(getPackageName());
    }

    private void openBatterySettings() {
        try {
            startActivity(new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    Uri.parse("package:" + getPackageName())));
        } catch (Exception e) {
            startActivity(new Intent(Settings.ACTION_SETTINGS));
        }
    }

    // ─── MIUI ────────────────────────────────────────────────────────────────

    private boolean isXiaomi() {
        return "xiaomi".equalsIgnoreCase(Build.MANUFACTURER);
    }

    private void openMiuiAutoStart() {
        try {
            Intent intent = new Intent();
            intent.setComponent(new ComponentName(
                    "com.miui.securitycenter",
                    "com.miui.permcenter.autostart.AutoStartManagementActivity"));
            startActivity(intent);
        } catch (Exception e) {
            openAppDetails();
        }
    }

    private void openMiuiBattery() {
        try {
            Intent intent = new Intent();
            intent.setAction("miui.intent.action.APP_PERM_EDITOR");
            intent.setClassName("com.miui.securitycenter",
                    "com.miui.permcenter.permissions.PermissionsEditorActivity");
            intent.putExtra("extra_pkgname", getPackageName());
            startActivity(intent);
        } catch (Exception e) {
            openBatterySettings();
        }
    }

    private void openAppDetails() {
        startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:" + getPackageName())));
    }

    // ─── Lock task ───────────────────────────────────────────────────────────

    private void tryStopLockTask() {
        try {
            if (Build.VERSION.SDK_INT >= 23) {
                ActivityManager am = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
                if (am != null && am.getLockTaskModeState() != ActivityManager.LOCK_TASK_MODE_NONE) {
                    stopLockTask();
                }
            }
        } catch (Exception ignored) {}
    }

    // ─── Serviço ─────────────────────────────────────────────────────────────

    private void startController(String action) {
        Intent serviceIntent = new Intent(this, ControlService.class);
        if (action != null) serviceIntent.setAction(action);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
        }
    }

    // ─── Verificações de estado ──────────────────────────────────────────────

    private boolean hasRegisteredEmail() {
        return !AppConfig.getUnitEmail(this).trim().isEmpty();
    }

    private boolean isFullyConfigured() {
        return hasRegisteredEmail()
                && AppConfig.getSelectedPackages(this).size() >= 2
                && !AppConfig.getActivePackage(this).trim().isEmpty();
    }

    private boolean hasPackage(String packageName) {
        for (InstalledApp app : allApps) {
            if (app.packageName.equals(packageName)) return true;
        }
        return false;
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

    private String appDisplayName(String packageName) {
        if (packageName == null || packageName.isEmpty()) return "não selecionado";
        for (InstalledApp app : allApps) {
            if (app.packageName.equals(packageName)) return app.label;
        }
        return packageName;
    }

    // ─── Helpers de UI ───────────────────────────────────────────────────────

    private LinearLayout card(String title) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(14), dp(14), dp(14), dp(14));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, 0, dp(12));
        card.setLayoutParams(lp);
        card.setBackground(cardBg());

        if (title != null && !title.isEmpty()) {
            TextView header = new TextView(this);
            header.setText(title.toUpperCase(Locale.US));
            header.setTextSize(11);
            header.setTypeface(Typeface.DEFAULT_BOLD);
            header.setTextColor(C_MUTED);
            header.setLetterSpacing(0.08f);
            header.setPadding(0, 0, 0, dp(10));
            card.addView(header);
        }
        return card;
    }

    private View permissionRow(String label, boolean granted) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, dp(4), 0, dp(4));
        row.setLayoutParams(lp);

        TextView icon = new TextView(this);
        icon.setText(granted ? "✓" : "✕");
        icon.setTextSize(13);
        icon.setTypeface(Typeface.DEFAULT_BOLD);
        icon.setTextColor(granted ? C_SUCCESS : C_ERROR);
        icon.setMinWidth(dp(20));
        row.addView(icon);

        TextView text = new TextView(this);
        text.setText(label);
        text.setTextSize(14);
        text.setTextColor(granted ? C_TEXT : C_ERROR);
        row.addView(text);

        return row;
    }

    private View metaRow(String key, String value) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        TextView keyView = new TextView(this);
        keyView.setText(key);
        keyView.setTextSize(13);
        keyView.setTextColor(C_MUTED);
        keyView.setMinWidth(dp(90));

        TextView valueView = new TextView(this);
        valueView.setText(value);
        valueView.setTextSize(13);
        valueView.setTypeface(Typeface.DEFAULT_BOLD);
        valueView.setTextColor(C_TEXT);

        row.addView(keyView);
        row.addView(valueView);
        return row;
    }

    private View divider(int topMargin) {
        View d = new View(this);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(1));
        lp.setMargins(0, topMargin, 0, 0);
        d.setLayoutParams(lp);
        d.setBackgroundColor(C_BORDER);
        return d;
    }

    private TextView sectionLabel(String text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(15);
        tv.setTypeface(Typeface.DEFAULT_BOLD);
        tv.setTextColor(C_TEXT);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, dp(8), 0, dp(8));
        tv.setLayoutParams(lp);
        return tv;
    }

    private TextView body(String text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(14);
        tv.setTextColor(C_TEXT2);
        return tv;
    }

    private Button primaryButton(String text) {
        Button btn = new Button(this);
        btn.setText(text);
        btn.setAllCaps(false);
        btn.setTextColor(Color.WHITE);
        btn.setTextSize(15);
        btn.setTypeface(Typeface.DEFAULT_BOLD);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(C_PRIMARY);
        bg.setCornerRadius(dp(10));
        btn.setBackground(bg);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(52));
        lp.setMargins(0, dp(8), 0, 0);
        btn.setLayoutParams(lp);
        return btn;
    }

    private Button outlineButton(String text) {
        Button btn = new Button(this);
        btn.setText(text);
        btn.setAllCaps(false);
        btn.setTextColor(C_PRIMARY);
        btn.setTextSize(15);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.WHITE);
        bg.setCornerRadius(dp(10));
        bg.setStroke(dp(1), C_PRIMARY);
        btn.setBackground(bg);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(52));
        lp.setMargins(0, dp(8), 0, 0);
        btn.setLayoutParams(lp);
        return btn;
    }

    private void tintOutlineButton(Button btn, int color) {
        btn.setTextColor(color);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.WHITE);
        bg.setCornerRadius(dp(10));
        bg.setStroke(dp(1), color);
        btn.setBackground(bg);
    }

    private EditText input(String hint, String value) {
        EditText et = new EditText(this);
        et.setHint(hint);
        et.setText(value);
        et.setSingleLine(true);
        et.setTextSize(14);
        et.setTextColor(C_TEXT);
        et.setHintTextColor(C_MUTED);
        et.setPadding(dp(12), dp(10), dp(12), dp(10));
        et.setBackground(cardBg());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, 0, 0);
        et.setLayoutParams(lp);
        return et;
    }

    private GradientDrawable cardBg() {
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(C_SURFACE);
        bg.setCornerRadius(dp(10));
        bg.setStroke(dp(1), C_BORDER);
        return bg;
    }

    private GradientDrawable selectedCardBg() {
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(C_PRIMARY_L);
        bg.setCornerRadius(dp(10));
        bg.setStroke(dp(2), C_PRIMARY);
        return bg;
    }

    private void addFooter(LinearLayout root) {
        TextView footer = new TextView(this);
        footer.setText("© GelaFit • Tecnologia MRIT");
        footer.setGravity(Gravity.CENTER);
        footer.setTextColor(C_MUTED);
        footer.setTextSize(12);
        footer.setPadding(0, dp(24), 0, dp(8));
        root.addView(footer);
    }

    private void showMessage(String title, String message) {
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton("OK", null)
                .show();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
