package com.gelafit.control;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

final class AppConfig {
    private static final String PREFS = "gelafit_control";
    static final String DEFAULT_SUPABASE_URL = "https://kihyhoqbrkwbfudttevo.supabase.co";
    static final String DEFAULT_SUPABASE_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImtpaHlob3Ficmt3YmZ1ZHR0ZXZvIiwicm9sZSI6ImFub24iLCJpYXQiOjE3MTU1NTUwMjcsImV4cCI6MjAzMTEzMTAyN30.XtBTlSiqhsuUIKmhAMEyxofV-dRst7240n912m4O4Us";

    private AppConfig() {}

    static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    static String getDeviceId(Context context) {
        SharedPreferences prefs = prefs(context);
        String id = prefs.getString("device_id", null);
        if (id == null || id.trim().isEmpty()) {
            id = UUID.randomUUID().toString();
            prefs.edit().putString("device_id", id).apply();
        }
        return id;
    }

    static String getSupabaseUrl(Context context) {
        return prefs(context).getString("supabase_url", DEFAULT_SUPABASE_URL);
    }

    static String getSupabaseKey(Context context) {
        return prefs(context).getString("supabase_key", DEFAULT_SUPABASE_KEY);
    }

    static String getUnitEmail(Context context) {
        return prefs(context).getString("unit_email", "");
    }

    static String getActivePackage(Context context) {
        return prefs(context).getString("active_package", "");
    }

    static void saveActivePackage(Context context, String packageName) {
        prefs(context).edit().putString("active_package", packageName == null ? "" : packageName).apply();
    }

    static long getLastCommandNonce(Context context) {
        return prefs(context).getLong("last_command_nonce", 0L);
    }

    static void setLastCommandNonce(Context context, long nonce) {
        prefs(context).edit().putLong("last_command_nonce", nonce).apply();
    }

    static List<String> getSelectedPackages(Context context) {
        String raw = prefs(context).getString("selected_packages", "[]");
        ArrayList<String> packages = new ArrayList<>();
        try {
            JSONArray array = new JSONArray(raw);
            for (int i = 0; i < array.length(); i++) {
                packages.add(array.getString(i));
            }
        } catch (Exception ignored) {
        }
        return packages;
    }

    static void saveSelectedPackages(Context context, List<String> packages) {
        JSONArray array = new JSONArray();
        for (String packageName : packages) {
            array.put(packageName);
        }
        prefs(context).edit().putString("selected_packages", array.toString()).apply();
    }
}
