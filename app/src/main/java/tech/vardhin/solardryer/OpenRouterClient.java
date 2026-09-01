package tech.vardhin.solardryer;

import android.util.Base64;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public class OpenRouterClient {
    public static final String CHAT_MODEL = "deepseek/deepseek-v4-flash-0731";
    public static final String STT_MODEL = "openai/whisper-1";

    private final String apiKey;

    public OpenRouterClient(String apiKey) {
        this.apiKey = apiKey == null ? "" : apiKey.trim();
    }

    public String transcribe(File wav) throws Exception {
        byte[] audio = readAllBytes(wav);
        JSONObject body = new JSONObject();
        body.put("model", STT_MODEL);
        body.put("temperature", 0);
        JSONObject inputAudio = new JSONObject();
        inputAudio.put("data", Base64.encodeToString(audio, Base64.NO_WRAP));
        inputAudio.put("format", "wav");
        body.put("input_audio", inputAudio);

        JSONObject response = requestJson("https://openrouter.ai/api/v1/audio/transcriptions", body, 60000);
        String text = response.optString("text", "").trim();
        if (text.isEmpty()) throw new Exception("Speech transcription returned no text");
        return text;
    }

    public JSONObject chat(JSONArray messages, JSONArray tools) throws Exception {
        JSONObject body = new JSONObject();
        body.put("model", CHAT_MODEL);
        body.put("messages", messages);
        body.put("tools", tools);
        body.put("tool_choice", "auto");
        body.put("temperature", 0.2);
        body.put("max_tokens", 800);
        return requestJson("https://openrouter.ai/api/v1/chat/completions", body, 45000);
    }

    public static JSONObject firstMessage(JSONObject response) throws Exception {
        JSONArray choices = response.optJSONArray("choices");
        if (choices == null || choices.length() == 0) {
            throw new Exception("LLM returned no choices");
        }
        JSONObject message = choices.getJSONObject(0).optJSONObject("message");
        if (message == null) throw new Exception("LLM returned no message");
        return message;
    }

    private JSONObject requestJson(String endpoint, JSONObject body, int timeoutMs) throws Exception {
        if (apiKey.isEmpty()) throw new Exception("OpenRouter API key is not configured");
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(endpoint).openConnection();
            connection.setRequestMethod("POST");
            connection.setConnectTimeout(12000);
            connection.setReadTimeout(timeoutMs);
            connection.setDoOutput(true);
            connection.setUseCaches(false);
            connection.setRequestProperty("Authorization", "Bearer " + apiKey);
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("X-Title", "Smart Solar Dryer");

            byte[] data = body.toString().getBytes(StandardCharsets.UTF_8);
            connection.setFixedLengthStreamingMode(data.length);
            try (OutputStream os = connection.getOutputStream()) {
                os.write(data);
            }

            int code = connection.getResponseCode();
            InputStream stream = code >= 200 && code < 300 ? connection.getInputStream() : connection.getErrorStream();
            String text = readText(stream);
            JSONObject json = text.isEmpty() ? new JSONObject() : new JSONObject(text);
            if (code < 200 || code >= 300) {
                String message = "HTTP " + code;
                JSONObject error = json.optJSONObject("error");
                if (error != null) message = error.optString("message", message);
                throw new Exception(message);
            }
            return json;
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private static String readText(InputStream stream) throws Exception {
        if (stream == null) return "";
        StringBuilder b = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) b.append(line);
        }
        return b.toString().trim();
    }

    private static byte[] readAllBytes(File file) throws Exception {
        long length = file.length();
        if (length > 20 * 1024 * 1024L) throw new Exception("Recording is too long");
        byte[] data = new byte[(int) length];
        try (FileInputStream in = new FileInputStream(file)) {
            int offset = 0;
            while (offset < data.length) {
                int n = in.read(data, offset, data.length - offset);
                if (n < 0) break;
                offset += n;
            }
            if (offset != data.length) throw new Exception("Could not read recording");
        }
        return data;
    }
}
