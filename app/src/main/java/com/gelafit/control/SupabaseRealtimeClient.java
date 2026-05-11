package com.gelafit.control;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;

final class SupabaseRealtimeClient {
    interface Listener {
        void onDeviceChanged(JSONObject device);

        void onRealtimeError(String error);
    }

    private static final String TABLE = "gelafit_control_devices";
    private static final long HEARTBEAT_MS = 25_000L;
    private static final long RECONNECT_MS = 30_000L;

    private final Context context;
    private final Listener listener;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final OkHttpClient client = new OkHttpClient.Builder()
            .pingInterval(20, TimeUnit.SECONDS)
            .build();

    private WebSocket socket;
    private boolean running;
    private int ref;

    SupabaseRealtimeClient(Context context, Listener listener) {
        this.context = context.getApplicationContext();
        this.listener = listener;
    }

    void start() {
        if (running) {
            return;
        }
        running = true;
        connect();
    }

    void stop() {
        running = false;
        handler.removeCallbacksAndMessages(null);
        if (socket != null) {
            socket.close(1000, "service stopped");
            socket = null;
        }
    }

    private void connect() {
        if (!running || AppConfig.getUnitEmail(context).trim().isEmpty()) {
            return;
        }
        String url = realtimeUrl();
        Request request = new Request.Builder()
                .url(url)
                .build();
        socket = client.newWebSocket(request, new WebSocketListener() {
            @Override
            public void onOpen(WebSocket webSocket, Response response) {
                joinChannel();
                scheduleHeartbeat();
            }

            @Override
            public void onMessage(WebSocket webSocket, String text) {
                handleMessage(text);
            }

            @Override
            public void onFailure(WebSocket webSocket, Throwable t, Response response) {
                listener.onRealtimeError(t.getMessage());
                reconnectLater();
            }

            @Override
            public void onClosed(WebSocket webSocket, int code, String reason) {
                reconnectLater();
            }
        });
    }

    private String realtimeUrl() {
        String base = AppConfig.getSupabaseUrl(context).trim()
                .replace("https://", "wss://")
                .replace("http://", "ws://");
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base + "/realtime/v1/websocket?apikey=" + AppConfig.getSupabaseKey(context) + "&vsn=1.0.0";
    }

    private void joinChannel() {
        try {
            JSONObject change = new JSONObject();
            change.put("event", "UPDATE");
            change.put("schema", "public");
            change.put("table", TABLE);
            change.put("filter", "unit_email=eq." + AppConfig.getUnitEmail(context).trim());

            JSONObject config = new JSONObject();
            config.put("postgres_changes", new JSONArray().put(change));
            config.put("broadcast", new JSONObject().put("ack", false).put("self", false));
            config.put("presence", new JSONObject().put("enabled", false));

            JSONObject payload = new JSONObject();
            payload.put("config", config);

            send("realtime:public:" + TABLE, "phx_join", payload);
        } catch (Exception e) {
            listener.onRealtimeError(e.getMessage());
        }
    }

    private void scheduleHeartbeat() {
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (!running || socket == null) {
                    return;
                }
                send("phoenix", "heartbeat", new JSONObject());
                handler.postDelayed(this, HEARTBEAT_MS);
            }
        }, HEARTBEAT_MS);
    }

    private void reconnectLater() {
        if (!running) {
            return;
        }
        socket = null;
        handler.postDelayed(this::connect, RECONNECT_MS);
    }

    private void send(String topic, String event, JSONObject payload) {
        if (socket == null) {
            return;
        }
        try {
            JSONObject message = new JSONObject();
            message.put("topic", topic);
            message.put("event", event);
            message.put("payload", payload);
            message.put("ref", String.valueOf(++ref));
            socket.send(message.toString());
        } catch (Exception e) {
            listener.onRealtimeError(e.getMessage());
        }
    }

    private void handleMessage(String text) {
        try {
            JSONObject message = new JSONObject(text);
            String event = message.optString("event", "");
            JSONObject payload = message.optJSONObject("payload");
            JSONObject record = null;
            if (payload != null) {
                JSONObject data = payload.optJSONObject("data");
                if (data != null) {
                    record = data.optJSONObject("record");
                }
                if (record == null) {
                    record = payload.optJSONObject("record");
                }
            }
            if (record != null && ("postgres_changes".equals(event) || "UPDATE".equals(event))) {
                listener.onDeviceChanged(record);
            }
        } catch (Exception e) {
            listener.onRealtimeError(e.getMessage());
        }
    }
}
