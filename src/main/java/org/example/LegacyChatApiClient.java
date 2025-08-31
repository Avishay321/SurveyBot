package org.example;

import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.Response;
import org.json.JSONObject;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

public class LegacyChatApiClient {

    private static final OkHttpClient HTTP = new OkHttpClient();

    private final String baseUrl;
    private final long sessionId;

    public LegacyChatApiClient(String baseUrl, long sessionId) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.sessionId = sessionId;
    }

    public void clearHistory() throws IOException {
        String url = baseUrl + "/clear-history?id=" + sessionId;
        Request req = new Request.Builder().url(url).build();

        Response resp = null;
        try {
            resp = HTTP.newCall(req).execute();
            if (!resp.isSuccessful()) {
                throw new IOException("clear-history failed: HTTP " + resp.code());
            }

            if (resp.body() != null) resp.body().close();
        } finally {
            if (resp != null && resp.body() != null) {
                try { resp.body().close(); } catch (Exception ignored) {}
            }
        }
    }

    public JSONObject sendMessage(String text) throws IOException {
        String encoded = URLEncoder.encode(text, StandardCharsets.UTF_8.name());
        String url = baseUrl + "/send-message?id=" + sessionId + "&text=" + encoded;
        Request req = new Request.Builder().url(url).build();

        Response resp = null;
        try {
            resp = HTTP.newCall(req).execute();
            if (!resp.isSuccessful()) {
                throw new IOException("send-message failed: HTTP " + resp.code());
            }
            String body = (resp.body() != null) ? resp.body().string() : "{}";
            return new JSONObject(body);
        } finally {
            if (resp != null && resp.body() != null) {
                try { resp.body().close(); } catch (Exception ignored) {}
            }
        }
    }
}
