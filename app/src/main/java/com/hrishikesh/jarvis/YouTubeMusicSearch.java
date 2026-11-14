package com.hrishikesh.jarvis;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Locale;
import java.util.Set;
import java.util.TimeZone;

/** Read-only YouTube Music catalogue search used by the phone app.
 * Playback itself remains under the installed YouTube Music app and Android media sessions.
 */
final class YouTubeMusicSearch {
    private static final String ENDPOINT =
            "https://music.youtube.com/youtubei/v1/search?alt=json&key=AIzaSyC9XL3ZjWddXya6X74dJoCTL-WEYFDNX30";
    private static final String SONG_FILTER = "EgWKAQIIAWoKEAoQCRADEAQQBQ==";
    private YouTubeMusicSearch() { }

    static JSONObject search(String query) throws Exception {
        String clean = query == null ? "" : query.trim();
        if (clean.isEmpty() || clean.length() > 160) {
            throw new IllegalArgumentException("Enter a song title of up to 160 characters.");
        }
        String today = new SimpleDateFormat("yyyyMMdd", Locale.US) {{
            setTimeZone(TimeZone.getTimeZone("UTC"));
        }}.format(new Date());
        JSONObject client = new JSONObject()
                .put("clientName", "WEB_REMIX")
                .put("clientVersion", "1." + today + ".01.00")
                .put("hl", "en")
                .put("gl", "IN");
        JSONObject body = new JSONObject()
                .put("context", new JSONObject().put("client", client).put("user", new JSONObject()))
                .put("query", clean)
                .put("params", SONG_FILTER);

        HttpURLConnection connection = (HttpURLConnection) new URL(ENDPOINT).openConnection();
        try {
            connection.setRequestMethod("POST");
            connection.setConnectTimeout(7000);
            connection.setReadTimeout(7000);
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("Origin", "https://music.youtube.com");
            connection.setRequestProperty("Referer", "https://music.youtube.com/");
            connection.setRequestProperty("User-Agent",
                    "Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36 Chrome/131.0.0.0 Mobile Safari/537.36");
            byte[] request = body.toString().getBytes("UTF-8");
            connection.setFixedLengthStreamingMode(request.length);
            try (OutputStream output = connection.getOutputStream()) {
                output.write(request);
            }
            int code = connection.getResponseCode();
            InputStream stream = code >= 200 && code < 300
                    ? connection.getInputStream() : connection.getErrorStream();
            String response = readAll(stream);
            if (code < 200 || code >= 300) {
                throw new IllegalStateException("YouTube Music search returned HTTP " + code + ".");
            }
            JSONObject payload = new JSONObject(response);
            JSONArray items = parseItems(payload);
            if (items.length() == 0) {
                throw new IllegalStateException("YouTube Music returned no matching songs.");
            }
            return new JSONObject()
                    .put("items", items)
                    .put("selected", items.getJSONObject(0))
                    .put("source", "YouTube Music catalogue");
        } finally {
            connection.disconnect();
        }
    }

    static JSONArray parseItems(JSONObject payload) throws JSONException {
        JSONArray items = new JSONArray();
        Set<String> seenIds = new HashSet<>();
        collect(payload, items, seenIds, 0);
        return items;
    }

    private static void collect(Object value, JSONArray items, Set<String> seenIds, int depth)
            throws JSONException {
        if (value == null || depth > 48 || items.length() >= 5) return;
        if (value instanceof JSONObject) {
            JSONObject object = (JSONObject) value;
            JSONObject renderer = object.optJSONObject("musicResponsiveListItemRenderer");
            if (renderer != null) {
                String videoId = findVideoId(renderer, 0);
                if (!videoId.isEmpty() && seenIds.add(videoId)) {
                    JSONArray columns = renderer.optJSONArray("flexColumns");
                    String title = columns != null && columns.length() > 0
                            ? columnText(columns.optJSONObject(0)) : "";
                    String subtitle = columns != null && columns.length() > 1
                            ? columnText(columns.optJSONObject(1)) : "";
                    String duration = columns != null && columns.length() > 2
                            ? columnText(columns.optJSONObject(2)) : "";
                    String[] artistParts = subtitle.split("\\s*[•·]\\s*");
                    String artist = artistParts.length > 0 ? artistParts[0].trim() : "";
                    items.put(new JSONObject()
                            .put("videoId", videoId)
                            .put("title", title.isEmpty() ? "YouTube Music track" : title.substring(0, Math.min(180, title.length())))
                            .put("artist", artist.isEmpty() ? "Unknown artist" : artist.substring(0, Math.min(180, artist.length())))
                            .put("duration", duration.substring(0, Math.min(24, duration.length())))
                            .put("url", "https://music.youtube.com/watch?v=" + videoId));
                }
            }
            Iterator<String> keys = object.keys();
            while (keys.hasNext() && items.length() < 5) {
                String key = keys.next();
                collect(object.opt(key), items, seenIds, depth + 1);
            }
        } else if (value instanceof JSONArray) {
            JSONArray array = (JSONArray) value;
            for (int i = 0; i < array.length() && items.length() < 5; i++) {
                collect(array.opt(i), items, seenIds, depth + 1);
            }
        }
    }

    private static String findVideoId(Object value, int depth) {
        if (value == null || depth > 48) return "";
        if (value instanceof JSONObject) {
            JSONObject object = (JSONObject) value;
            String id = object.optString("videoId", "");
            if (id.matches("[A-Za-z0-9_-]{11}")) return id;
            Iterator<String> keys = object.keys();
            while (keys.hasNext()) {
                String found = findVideoId(object.opt(keys.next()), depth + 1);
                if (!found.isEmpty()) return found;
            }
        } else if (value instanceof JSONArray) {
            JSONArray array = (JSONArray) value;
            for (int i = 0; i < array.length(); i++) {
                String found = findVideoId(array.opt(i), depth + 1);
                if (!found.isEmpty()) return found;
            }
        }
        return "";
    }

    private static String columnText(JSONObject column) {
        if (column == null) return "";
        JSONObject flex = column.optJSONObject("musicResponsiveListItemFlexColumnRenderer");
        if (flex == null) return "";
        JSONObject text = flex.optJSONObject("text");
        if (text == null) return "";
        String simple = text.optString("simpleText", "");
        if (!simple.isEmpty()) return simple.trim();
        JSONArray runs = text.optJSONArray("runs");
        if (runs == null) return "";
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < runs.length(); i++) {
            JSONObject run = runs.optJSONObject(i);
            if (run != null) result.append(run.optString("text", ""));
        }
        return result.toString().trim();
    }

    private static String readAll(InputStream input) throws Exception {
        if (input == null) return "";
        try (InputStream stream = input; ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int count;
            while ((count = stream.read(buffer)) != -1) output.write(buffer, 0, count);
            return output.toString("UTF-8");
        }
    }
}
