package com.hrishikesh.jarvis;

import android.os.Handler;
import android.os.Looper;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;

/** Sends recent encrypted local conversation context to the JARVIS cloud reasoning API. */
public final class AiConversationClient {
    private static final String ENDPOINT = "https://jarvis-ai-core.vercel.app/api/chat";
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    public interface Callback {
        void onSuccess(String answer);
        void onError(String message);
    }

    private AiConversationClient() { }

    public static void send(List<ConversationStore.Entry> history, Callback callback) {
        new Thread(() -> {
            HttpURLConnection connection = null;
            try {
                JSONObject payload = new JSONObject();
                JSONArray messages = new JSONArray();
                if (history != null) {
                    int start = Math.max(0, history.size() - 14);
                    for (int i = start; i < history.size(); i++) {
                        ConversationStore.Entry entry = history.get(i);
                        if (entry == null || entry.text == null) continue;
                        if (!"user".equals(entry.role) && !"assistant".equals(entry.role)) continue;
                        JSONObject item = new JSONObject();
                        item.put("role", entry.role);
                        item.put("content", entry.text.length() > 6000
                                ? entry.text.substring(0, 6000) : entry.text);
                        messages.put(item);
                    }
                }
                payload.put("messages", messages);
                connection = (HttpURLConnection) new URL(ENDPOINT).openConnection();
                connection.setRequestMethod("POST");
                connection.setConnectTimeout(15000);
                connection.setReadTimeout(45000);
                connection.setDoOutput(true);
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                connection.setRequestProperty("Accept", "application/json");
                byte[] body = payload.toString().getBytes(StandardCharsets.UTF_8);
                connection.setFixedLengthStreamingMode(body.length);
                try (OutputStream out = connection.getOutputStream()) { out.write(body); }
                int status = connection.getResponseCode();
                InputStream stream = status >= 200 && status < 300
                        ? connection.getInputStream() : connection.getErrorStream();
                StringBuilder response = new StringBuilder();
                if (stream != null) {
                    try (BufferedReader reader = new BufferedReader(
                            new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                        String line;
                        while ((line = reader.readLine()) != null) response.append(line);
                    }
                }
                JSONObject result = new JSONObject(response.toString());
                if (status < 200 || status >= 300) {
                    String error = result.optString("error", "HTTP " + status);
                    throw new IllegalStateException(error);
                }
                String answer = result.optString("reply", "").trim();
                if (answer.isEmpty()) throw new IllegalStateException("The reasoning service returned an empty answer.");
                MAIN.post(() -> callback.onSuccess(answer));
            } catch (Exception error) {
                String message = error.getMessage();
                if (message == null || message.trim().isEmpty()) message = error.getClass().getSimpleName();
                final String safe = message.length() > 180 ? message.substring(0, 180) : message;
                MAIN.post(() -> callback.onError(safe));
            } finally {
                if (connection != null) connection.disconnect();
            }
        }, "jarvis-ai-request").start();
    }
}
