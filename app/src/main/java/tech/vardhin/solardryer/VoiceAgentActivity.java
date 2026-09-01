package tech.vardhin.solardryer;

import android.Manifest;
import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class VoiceAgentActivity extends android.app.Activity {
    public static final String EXTRA_START_RECORDING = "start_recording";
    public static final String EXTRA_OPEN_SETTINGS = "open_settings";

    private static final int REQ_MIC = 901;
    private static final String PREFS = "solar_dryer";
    private static final String KEY_BASE_URL = "base_url";
    private static final String DEFAULT_URL = "http://solar-dryer.local";

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final WavRecorder recorder = new WavRecorder();

    private SharedPreferences prefs;
    private LinearLayout chat;
    private ScrollView chatScroll;
    private TextView status;
    private Button micButton;
    private EditText textInput;
    private boolean startAfterPermission = false;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        setContentView(buildUi());

        if (getIntent().getBooleanExtra(EXTRA_OPEN_SETTINGS, false) || !SecureKeyStore.hasKey(this)) {
            showKeyDialog(getIntent().getBooleanExtra(EXTRA_START_RECORDING, false));
        } else if (getIntent().getBooleanExtra(EXTRA_START_RECORDING, false)) {
            micButton.postDelayed(this::startRecordingWithPermission, 250);
        }
    }

    @Override
    protected void onDestroy() {
        recorder.cancel();
        executor.shutdownNow();
        super.onDestroy();
    }

    private View buildUi() {
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setBackgroundColor(0xFFF7F8F4);

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(10), dp(8), dp(10), dp(8));
        header.setBackgroundColor(0xFF1F3921);

        Button back = new Button(this);
        back.setText("‹ Back");
        back.setAllCaps(false);
        back.setOnClickListener(v -> finish());
        header.addView(back, new LinearLayout.LayoutParams(dp(90), dp(48)));

        TextView title = new TextView(this);
        title.setText("Dryer Assistant");
        title.setTextColor(Color.WHITE);
        title.setTextSize(20);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        title.setGravity(Gravity.CENTER);
        header.addView(title, new LinearLayout.LayoutParams(0, dp(48), 1f));

        Button key = new Button(this);
        key.setText("Key");
        key.setAllCaps(false);
        key.setOnClickListener(v -> showKeyDialog(false));
        header.addView(key, new LinearLayout.LayoutParams(dp(70), dp(48)));
        page.addView(header);

        status = new TextView(this);
        status.setText("Ready • Whisper STT • DeepSeek V4 Flash");
        status.setTextSize(12);
        status.setTextColor(0xFF5D675B);
        status.setPadding(dp(14), dp(8), dp(14), dp(8));
        page.addView(status);

        chatScroll = new ScrollView(this);
        chat = new LinearLayout(this);
        chat.setOrientation(LinearLayout.VERTICAL);
        chat.setPadding(dp(12), dp(8), dp(12), dp(18));
        chatScroll.addView(chat);
        page.addView(chatScroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        addAssistantMessage("Ask me about temperature, humidity, weight, drying progress, the OLED, battery, or tell me to control the fan. You can speak Tamil, Telugu, Hindi, or English.");

        LinearLayout inputBar = new LinearLayout(this);
        inputBar.setOrientation(LinearLayout.HORIZONTAL);
        inputBar.setGravity(Gravity.CENTER_VERTICAL);
        inputBar.setPadding(dp(8), dp(8), dp(8), dp(10));
        inputBar.setBackgroundColor(Color.WHITE);

        textInput = new EditText(this);
        textInput.setHint("Type or use the mic…");
        textInput.setSingleLine(false);
        textInput.setMaxLines(3);
        inputBar.addView(textInput, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        Button send = new Button(this);
        send.setText("Send");
        send.setAllCaps(false);
        send.setOnClickListener(v -> {
            String text = textInput.getText().toString().trim();
            if (!text.isEmpty()) {
                textInput.setText("");
                submitUserText(text);
            }
        });
        inputBar.addView(send, new LinearLayout.LayoutParams(dp(78), dp(52)));

        micButton = new Button(this);
        micButton.setText("🎤");
        micButton.setTextSize(22);
        micButton.setAllCaps(false);
        micButton.setContentDescription("Start or stop voice recording");
        micButton.setOnClickListener(v -> {
            if (recorder.isRecording()) stopAndTranscribe();
            else startRecordingWithPermission();
        });
        LinearLayout.LayoutParams mp = new LinearLayout.LayoutParams(dp(64), dp(52));
        mp.setMargins(dp(5), 0, 0, 0);
        inputBar.addView(micButton, mp);

        page.addView(inputBar);
        return page;
    }

    private void startRecordingWithPermission() {
        if (!SecureKeyStore.hasKey(this)) {
            showKeyDialog(true);
            return;
        }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            startAfterPermission = true;
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQ_MIC);
            return;
        }
        try {
            File wav = new File(getCacheDir(), "dryer_voice.wav");
            recorder.start(wav);
            micButton.setText("■");
            status.setText("Listening… tap ■ when finished");
        } catch (Exception e) {
            toast("Microphone error: " + e.getMessage());
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_MIC && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            if (startAfterPermission) {
                startAfterPermission = false;
                startRecordingWithPermission();
            }
        } else if (requestCode == REQ_MIC) {
            toast("Microphone permission is required for voice input");
        }
    }

    private void stopAndTranscribe() {
        micButton.setEnabled(false);
        micButton.setText("…");
        status.setText("Transcribing speech…");
        executor.execute(() -> {
            try {
                File wav = recorder.stop();
                OpenRouterClient openRouter = new OpenRouterClient(SecureKeyStore.load(this));
                String transcript = openRouter.transcribe(wav);
                runOnUiThread(() -> {
                    micButton.setEnabled(true);
                    micButton.setText("🎤");
                    submitUserText(transcript);
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    micButton.setEnabled(true);
                    micButton.setText("🎤");
                    status.setText("Ready");
                    addAssistantMessage("I couldn't transcribe that recording: " + e.getMessage());
                });
            }
        });
    }

    private void submitUserText(String text) {
        addUserMessage(text);
        status.setText("Understanding request…");
        executor.execute(() -> runAgent(text));
    }

    private void runAgent(String userText) {
        try {
            String key = SecureKeyStore.load(this);
            if (key.isEmpty()) throw new Exception("OpenRouter API key is not configured");

            OpenRouterClient llm = new OpenRouterClient(key);
            JSONArray messages = new JSONArray();
            messages.put(new JSONObject()
                    .put("role", "system")
                    .put("content", systemPrompt()));
            messages.put(new JSONObject()
                    .put("role", "user")
                    .put("content", userText));

            JSONArray tools = buildTools();
            for (int round = 0; round < 5; round++) {
                JSONObject response = llm.chat(messages, tools);
                JSONObject assistantMessage = OpenRouterClient.firstMessage(response);
                messages.put(assistantMessage);

                JSONArray calls = assistantMessage.optJSONArray("tool_calls");
                if (calls == null || calls.length() == 0) {
                    String finalText = assistantMessage.optString("content", "").trim();
                    if (finalText.isEmpty()) finalText = "Done.";
                    final String answer = finalText;
                    runOnUiThread(() -> {
                        addAssistantMessage(answer);
                        status.setText("Ready");
                    });
                    return;
                }

                for (int i = 0; i < calls.length(); i++) {
                    JSONObject call = calls.getJSONObject(i);
                    String callId = call.optString("id", "call_" + round + "_" + i);
                    JSONObject fn = call.getJSONObject("function");
                    String name = fn.getString("name");
                    String argumentsText = fn.optString("arguments", "{}");
                    JSONObject args;
                    try { args = new JSONObject(argumentsText); }
                    catch (Exception ignored) { args = new JSONObject(); }

                    final String progress = progressText(name);
                    runOnUiThread(() -> status.setText(progress));

                    JSONObject toolResult;
                    try {
                        toolResult = executeTool(name, args);
                    } catch (Exception e) {
                        toolResult = new JSONObject().put("ok", false).put("error", e.getMessage());
                    }

                    messages.put(new JSONObject()
                            .put("role", "tool")
                            .put("tool_call_id", callId)
                            .put("content", toolResult.toString()));
                }
            }
            throw new Exception("The assistant used too many tool steps");
        } catch (Exception e) {
            runOnUiThread(() -> {
                addAssistantMessage("I couldn't complete that request: " + e.getMessage());
                status.setText("Ready");
            });
        }
    }

    private JSONObject executeTool(String name, JSONObject args) throws Exception {
        ApiClient api = new ApiClient(prefs.getString(KEY_BASE_URL, DEFAULT_URL));
        switch (name) {
            case "get_status": return api.get("/api/status");
            case "get_temperature": return api.get("/api/temp");
            case "get_weight": return api.get("/api/weight");
            case "get_oled": return api.get("/api/oled");
            case "get_battery": return api.get("/api/battery");
            case "get_notice": return api.get("/api/notice");
            case "get_fan": return api.get("/api/fan");
            case "fan_on": return api.post("/api/fan/on");
            case "fan_off": return api.post("/api/fan/off");
            case "fan_auto": return api.post("/api/fan/auto");
            case "tare_weight": return api.post("/api/weight/tare");
            case "set_initial_weight": {
                if (args.has("grams")) return api.post("/api/weight/initial", new JSONObject().put("grams", args.getDouble("grams")));
                return api.post("/api/weight/initial");
            }
            case "set_weight_threshold":
                return api.post("/api/weight/threshold", new JSONObject().put("percent", args.getDouble("percent")));
            case "set_calibration":
                return api.post("/api/weight/calibration", new JSONObject().put("factor", args.getDouble("factor")));
            case "default_calibration": return api.post("/api/weight/calibration/default");
            case "calibrate_known":
                return api.post("/api/weight/calibrate-known", new JSONObject().put("known_grams", args.getDouble("known_grams")));
            case "clear_notice": return api.post("/api/notice/clear");
            case "set_fan_logic": {
                JSONObject body = new JSONObject();
                body.put("temp_on", args.getDouble("temp_on"));
                body.put("temp_off", args.getDouble("temp_off"));
                if (args.has("humidity_enabled")) body.put("humidity_enabled", args.getBoolean("humidity_enabled"));
                if (args.has("humidity_diff_on")) body.put("humidity_diff_on", args.getDouble("humidity_diff_on"));
                if (args.has("humidity_diff_off")) body.put("humidity_diff_off", args.getDouble("humidity_diff_off"));
                return api.post("/api/fan/logic", body);
            }
            case "set_fan_schedule": {
                JSONObject body = new JSONObject();
                body.put("start", args.getString("start"));
                body.put("end", args.getString("end"));
                body.put("enabled", args.optBoolean("enabled", true));
                return api.post("/api/fan/schedule", body);
            }
            default: throw new Exception("Unknown tool: " + name);
        }
    }

    private JSONArray buildTools() throws Exception {
        JSONArray tools = new JSONArray();
        tools.put(tool("get_status", "Get the complete current dryer status.", props(), new JSONArray()));
        tools.put(tool("get_temperature", "Get inside and outside temperature and humidity.", props(), new JSONArray()));
        tools.put(tool("get_weight", "Get current weight, initial weight, remaining percentage and calibration.", props(), new JSONArray()));
        tools.put(tool("get_oled", "Read exactly what the dryer OLED is displaying.", props(), new JSONArray()));
        tools.put(tool("get_battery", "Get battery voltage and estimated charge if battery monitoring hardware is installed.", props(), new JSONArray()));
        tools.put(tool("get_notice", "Get the drying weight-threshold notice state.", props(), new JSONArray()));
        tools.put(tool("get_fan", "Get current fan state, mode, logic thresholds and schedule.", props(), new JSONArray()));
        tools.put(tool("fan_on", "Force the fan ON.", props(), new JSONArray()));
        tools.put(tool("fan_off", "Force the fan OFF.", props(), new JSONArray()));
        tools.put(tool("fan_auto", "Return the fan to automatic rule-based control.", props(), new JSONArray()));
        tools.put(tool("tare_weight", "Tare the scale. Use only when the user explicitly asks to tare/zero the scale.", props(), new JSONArray()));

        tools.put(tool("set_initial_weight", "Set the initial food weight used as 100 percent. If grams is omitted the ESP32 uses the current measured weight.",
                props().put("grams", num("Optional initial weight in grams")), new JSONArray()));
        tools.put(tool("set_weight_threshold", "Set notice threshold as percentage of initial weight.",
                props().put("percent", num("Percent from 1 to 100")), new JSONArray().put("percent")));
        tools.put(tool("set_calibration", "Set HX711 calibration factor directly.",
                props().put("factor", num("Calibration factor")), new JSONArray().put("factor")));
        tools.put(tool("default_calibration", "Reset weight calibration factor to the firmware default.", props(), new JSONArray()));
        tools.put(tool("calibrate_known", "Calibrate using a known weight currently on the already-tared scale.",
                props().put("known_grams", num("Known weight in grams")), new JSONArray().put("known_grams")));
        tools.put(tool("clear_notice", "Acknowledge and clear the current drying notice.", props(), new JSONArray()));

        JSONObject logicProps = props()
                .put("temp_on", num("Temperature in C at or above which fan turns on"))
                .put("temp_off", num("Temperature in C at or below which fan turns off"))
                .put("humidity_enabled", bool("Whether humidity-difference logic is enabled"))
                .put("humidity_diff_on", num("Inside minus outside RH percentage-point difference to turn on"))
                .put("humidity_diff_off", num("RH difference at or below which fan may turn off"));
        tools.put(tool("set_fan_logic", "Configure automatic fan hysteresis logic. temp_on must be greater than temp_off.",
                logicProps, new JSONArray().put("temp_on").put("temp_off")));

        JSONObject scheduleProps = props()
                .put("start", str("Start time HH:MM in the ESP32 local timezone"))
                .put("end", str("End time HH:MM"))
                .put("enabled", bool("Enable the schedule"));
        tools.put(tool("set_fan_schedule", "Configure and switch to daily fan schedule mode.",
                scheduleProps, new JSONArray().put("start").put("end")));
        return tools;
    }

    private JSONObject tool(String name, String description, JSONObject properties, JSONArray required) throws Exception {
        JSONObject parameters = new JSONObject()
                .put("type", "object")
                .put("properties", properties)
                .put("required", required)
                .put("additionalProperties", false);
        JSONObject fn = new JSONObject()
                .put("name", name)
                .put("description", description)
                .put("parameters", parameters);
        return new JSONObject().put("type", "function").put("function", fn);
    }

    private JSONObject props() { return new JSONObject(); }
    private JSONObject num(String d) throws Exception { return new JSONObject().put("type", "number").put("description", d); }
    private JSONObject str(String d) throws Exception { return new JSONObject().put("type", "string").put("description", d); }
    private JSONObject bool(String d) throws Exception { return new JSONObject().put("type", "boolean").put("description", d); }

    private String systemPrompt() {
        return "You are the local voice assistant for a Smart Solar Dryer. The user may speak English, Tamil, Telugu, Hindi, or mix them. " +
                "Understand the transcript directly and reply naturally in the same language or language mix as the user when practical. " +
                "Use the provided tools whenever the user asks for live dryer data or requests a device action. Never invent sensor values or claim an action succeeded without a tool result. " +
                "For potentially disruptive operations such as tare or calibration, only execute them when explicitly requested. " +
                "For normal fan ON/OFF/AUTO, schedules, thresholds, and fan logic, execute the requested action directly. " +
                "Keep replies concise and useful. If a tool reports unavailable battery monitoring, explain that battery sensing hardware is not installed. " +
                "Temperature values are Celsius, weight is grams, and schedule times use 24-hour HH:MM. The dryer REST server is on the phone's local Wi-Fi.";
    }

    private String progressText(String tool) {
        switch (tool) {
            case "get_temperature": return "Reading temperature and humidity…";
            case "get_weight": return "Reading food weight…";
            case "get_oled": return "Reading OLED…";
            case "get_battery": return "Checking battery…";
            case "fan_on": return "Turning fan on…";
            case "fan_off": return "Turning fan off…";
            case "fan_auto": return "Switching fan to auto…";
            case "set_fan_logic": return "Updating fan logic…";
            case "set_fan_schedule": return "Updating fan schedule…";
            default: return "Talking to the dryer…";
        }
    }

    private void showKeyDialog(boolean recordAfterSave) {
        EditText input = new EditText(this);
        input.setHint("sk-or-v1-…");
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        String existing = SecureKeyStore.load(this);
        if (!existing.isEmpty()) input.setText(existing);

        new AlertDialog.Builder(this)
                .setTitle("OpenRouter API key")
                .setMessage("Used directly on this phone for Whisper speech-to-text and DeepSeek V4 Flash. It is encrypted with Android Keystore and is not committed to GitHub.")
                .setView(input)
                .setPositiveButton("Save", (d, w) -> {
                    try {
                        SecureKeyStore.save(this, input.getText().toString());
                        toast("OpenRouter key saved securely");
                        if (recordAfterSave) micButton.postDelayed(this::startRecordingWithPermission, 200);
                    } catch (Exception e) {
                        toast("Could not save key: " + e.getMessage());
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void addUserMessage(String message) { addBubble("You", message, true); }
    private void addAssistantMessage(String message) { addBubble("Dryer Assistant", message, false); }

    private void addBubble(String who, String message, boolean user) {
        LinearLayout holder = new LinearLayout(this);
        holder.setOrientation(LinearLayout.VERTICAL);
        holder.setGravity(user ? Gravity.END : Gravity.START);
        holder.setPadding(0, dp(5), 0, dp(5));

        TextView name = new TextView(this);
        name.setText(who);
        name.setTextSize(11);
        name.setTextColor(0xFF657064);
        holder.addView(name);

        TextView bubble = new TextView(this);
        bubble.setText(message);
        bubble.setTextSize(16);
        bubble.setTextColor(user ? Color.WHITE : 0xFF182017);
        bubble.setPadding(dp(13), dp(10), dp(13), dp(10));
        bubble.setBackgroundColor(user ? 0xFF315F36 : 0xFFE9EEE6);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        p.width = (int)(getResources().getDisplayMetrics().widthPixels * 0.84f);
        bubble.setLayoutParams(p);
        holder.addView(bubble);
        chat.addView(holder);
        chatScroll.post(() -> chatScroll.fullScroll(View.FOCUS_DOWN));
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void toast(String text) {
        Toast.makeText(this, text, Toast.LENGTH_SHORT).show();
    }
}
