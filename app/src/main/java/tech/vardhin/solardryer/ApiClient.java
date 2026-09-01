package tech.vardhin.solardryer;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public class ApiClient {
    private final String baseUrl;

    public ApiClient(String baseUrl) {
        String normalized = baseUrl == null ? "" : baseUrl.trim();
        if (!normalized.startsWith("http://") && !normalized.startsWith("https://")) {
            normalized = "http://" + normalized;
        }
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        this.baseUrl = normalized;
    }

    public JSONObject get(String path) throws Exception {
        return request("GET", path, null);
    }

    public JSONObject post(String path) throws Exception {
        return request("POST", path, null);
    }

    public JSONObject post(String path, JSONObject body) throws Exception {
        return request("POST", path, body == null ? null : body.toString());
    }

    private JSONObject request(String method, String path, String body) throws Exception {
        HttpURLConnection connection = null;
        try {
            URL url = new URL(baseUrl + path);
            connection = (HttpURLConnection) url.openConnection();
            connection.setConnectTimeout(4000);
            connection.setReadTimeout(5000);
            connection.setRequestMethod(method);
            connection.setRequestProperty("Accept", "application/json");
            connection.setUseCaches(false);

            if (body != null) {
                connection.setDoOutput(true);
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                byte[] data = body.getBytes(StandardCharsets.UTF_8);
                connection.setFixedLengthStreamingMode(data.length);
                try (OutputStream os = connection.getOutputStream()) {
                    os.write(data);
                }
            }

            int code = connection.getResponseCode();
            InputStream stream = code >= 200 && code < 400
                    ? connection.getInputStream()
                    : connection.getErrorStream();

            StringBuilder response = new StringBuilder();
            if (stream != null) {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        response.append(line);
                    }
                }
            }

            String text = response.toString().trim();
            JSONObject json = text.isEmpty() ? new JSONObject() : new JSONObject(text);
            if (code < 200 || code >= 300) {
                String message = json.optString("error", "HTTP " + code);
                throw new Exception(message);
            }
            return json;
        } finally {
            if (connection != null) connection.disconnect();
        }
    }
}
