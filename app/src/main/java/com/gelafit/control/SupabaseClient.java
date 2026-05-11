package com.gelafit.control;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.OutputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

final class SupabaseClient {
    private static final String DEVICES_TABLE = "gelafit_control_devices";

    private final Context context;

    SupabaseClient(Context context) {
        this.context = context.getApplicationContext();
    }

    JSONObject fetchDevice() throws Exception {
        String unitEmail = AppConfig.getUnitEmail(context);
        String lookup = unitEmail.trim().isEmpty()
                ? "device_id=eq." + url(AppConfig.getDeviceId(context))
                : "unit_email=eq." + url(unitEmail.trim());
        String endpoint = restUrl(DEVICES_TABLE + "?" + lookup + "&select=*");
        HttpURLConnection connection = open(endpoint, "GET");
        String body = read(connection);
        JSONArray array = new JSONArray(body);
        if (array.length() == 0) {
            JSONObject created = new JSONObject();
            created.put("device_id", AppConfig.getDeviceId(context));
            created.put("unit_email", AppConfig.getUnitEmail(context));
            created.put("status", "online");
            created.put("command", JSONObject.NULL);
            created.put("command_nonce", 0);
            created.put("selected_apps", new JSONArray(AppConfig.getSelectedPackages(context)));
            created.put("active_package", AppConfig.getActivePackage(context));
            upsertDevice(created);
            return created;
        }
        JSONObject existing = array.getJSONObject(0);
        JSONObject payload = new JSONObject();
        payload.put("device_id", AppConfig.getDeviceId(context));
        payload.put("unit_email", unitEmail);
        payload.put("status", "online");
        payload.put("selected_apps", new JSONArray(AppConfig.getSelectedPackages(context)));
        payload.put("active_package", AppConfig.getActivePackage(context));
        patchByLookup(lookup, payload);
        existing.put("device_id", AppConfig.getDeviceId(context));
        existing.put("unit_email", unitEmail);
        existing.put("selected_apps", payload.getJSONArray("selected_apps"));
        existing.put("active_package", payload.optString("active_package", ""));
        return existing;
    }

    void updateStatus(String status, List<String> selectedPackages, String activePackage, String lastError) throws Exception {
        JSONObject payload = new JSONObject();
        payload.put("unit_email", AppConfig.getUnitEmail(context));
        payload.put("status", status);
        payload.put("last_seen_at", isoNow());
        payload.put("selected_apps", new JSONArray(selectedPackages));
        payload.put("active_package", activePackage == null || activePackage.isEmpty() ? JSONObject.NULL : activePackage);
        payload.put("last_error", lastError == null ? JSONObject.NULL : lastError);
        patchDevice(payload);
    }

    void markCommandDone(long nonce) throws Exception {
        JSONObject payload = new JSONObject();
        payload.put("last_command_nonce", nonce);
        payload.put("command", JSONObject.NULL);
        payload.put("status", "online");
        patchDevice(payload);
    }

    private void upsertDevice(JSONObject payload) throws Exception {
        HttpURLConnection connection = open(restUrl(DEVICES_TABLE), "POST");
        connection.setRequestProperty("Prefer", "resolution=merge-duplicates,return=minimal");
        write(connection, payload);
        read(connection);
    }

    private void patchDevice(JSONObject payload) throws Exception {
        String unitEmail = AppConfig.getUnitEmail(context);
        String lookup = unitEmail.trim().isEmpty()
                ? "device_id=eq." + url(AppConfig.getDeviceId(context))
                : "unit_email=eq." + url(unitEmail.trim());
        patchByLookup(lookup, payload);
    }

    private void patchByLookup(String lookup, JSONObject payload) throws Exception {
        String endpoint = restUrl(DEVICES_TABLE + "?" + lookup);
        HttpURLConnection connection = open(endpoint, "PATCH");
        connection.setRequestProperty("Prefer", "return=minimal");
        write(connection, payload);
        read(connection);
    }

    private String restUrl(String path) {
        String base = AppConfig.getSupabaseUrl(context).trim();
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base + "/rest/v1/" + path;
    }

    private String url(String value) throws Exception {
        return URLEncoder.encode(value, StandardCharsets.UTF_8.name());
    }

    private String isoNow() {
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US);
        format.setTimeZone(TimeZone.getTimeZone("UTC"));
        return format.format(new Date());
    }

    private HttpURLConnection open(String endpoint, String method) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(endpoint).openConnection();
        connection.setRequestMethod(method);
        connection.setConnectTimeout(15000);
        connection.setReadTimeout(15000);
        connection.setRequestProperty("apikey", AppConfig.getSupabaseKey(context));
        connection.setRequestProperty("Authorization", "Bearer " + AppConfig.getSupabaseKey(context));
        connection.setRequestProperty("Content-Type", "application/json");
        return connection;
    }

    private void write(HttpURLConnection connection, JSONObject payload) throws Exception {
        connection.setDoOutput(true);
        byte[] bytes = payload.toString().getBytes(StandardCharsets.UTF_8);
        try (OutputStream output = connection.getOutputStream()) {
            output.write(bytes);
        }
    }

    private String read(HttpURLConnection connection) throws Exception {
        int code = connection.getResponseCode();
        BufferedReader reader = new BufferedReader(new InputStreamReader(
                code >= 400 ? connection.getErrorStream() : connection.getInputStream(),
                StandardCharsets.UTF_8));
        StringBuilder builder = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            builder.append(line);
        }
        if (code >= 400) {
            throw new IllegalStateException("Supabase HTTP " + code + ": " + builder);
        }
        return builder.toString();
    }
}
