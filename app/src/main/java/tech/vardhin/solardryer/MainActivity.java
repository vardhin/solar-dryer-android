package tech.vardhin.solardryer;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends android.app.Activity {

    private static final String PREFS = "solar_dryer";
    private static final String KEY_BASE_URL = "base_url";
    private static final String DEFAULT_URL = "http://solar-dryer.local";
    private static final String NOTICE_CHANNEL = "dryer_notices";

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler handler = new Handler(Looper.getMainLooper());

    private EditText baseUrlInput;
    private TextView connectionText;
    private TextView statusText;
    private TextView oledText;
    private TextView batteryText;
    private TextView noticeText;

    private EditText tempOnInput;
    private EditText tempOffInput;
    private CheckBox humidityEnabled;
    private EditText humidityOnInput;
    private EditText humidityOffInput;

    private EditText scheduleStartInput;
    private EditText scheduleEndInput;

    private EditText thresholdInput;
    private EditText calibrationInput;
    private EditText knownWeightInput;

    private SharedPreferences preferences;

    private final Runnable liveRefresh = new Runnable() {
        @Override
        public void run() {
            refreshStatus(false);
            handler.postDelayed(this, 5000);
        }
    };

    private final Runnable noticePoll = new Runnable() {
        @Override
        public void run() {
            fetchNotice(true);
            handler.postDelayed(this, 5 * 60 * 1000L);
        }
    };

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        preferences = getSharedPreferences(PREFS, MODE_PRIVATE);
        createNotificationChannel();
        requestNotificationPermissionIfNeeded();
        setContentView(buildUi());
    }

    @Override
    protected void onResume() {
        super.onResume();
        handler.removeCallbacks(liveRefresh);
        handler.removeCallbacks(noticePoll);
        handler.post(liveRefresh);
        handler.post(noticePoll);
    }

    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacks(liveRefresh);
        handler.removeCallbacks(noticePoll);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdownNow();
    }

    private View buildUi() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(18), dp(18), dp(32));
        scroll.addView(root);

        TextView title = text("SMART SOLAR DRYER", 26, true);
        title.setGravity(Gravity.CENTER_HORIZONTAL);
        root.addView(title);
        TextView subtitle = text("ESP32 Local Controller", 14, false);
        subtitle.setGravity(Gravity.CENTER_HORIZONTAL);
        root.addView(subtitle);

        root.addView(space(14));
        root.addView(section("Connection"));
        baseUrlInput = input(preferences.getString(KEY_BASE_URL, DEFAULT_URL), false);
        root.addView(baseUrlInput);

        LinearLayout connectionButtons = row();
        connectionButtons.addView(button("Save", v -> saveBaseUrl()), weightParams());
        connectionButtons.addView(button("Test", v -> testConnection()), weightParams());
        connectionButtons.addView(button("Refresh", v -> refreshAll()), weightParams());
        root.addView(connectionButtons);
        connectionText = text("Waiting for dryer...", 13, false);
        root.addView(connectionText);

        root.addView(section("Live Dashboard"));
        statusText = panel("No data yet");
        root.addView(statusText);

        root.addView(section("Fan Control"));
        LinearLayout fanButtons = row();
        fanButtons.addView(button("ON", v -> postSimple("/api/fan/on", "Fan forced ON")), weightParams());
        fanButtons.addView(button("OFF", v -> postSimple("/api/fan/off", "Fan forced OFF")), weightParams());
        fanButtons.addView(button("AUTO", v -> postSimple("/api/fan/auto", "Fan set to AUTO")), weightParams());
        root.addView(fanButtons);

        root.addView(label("Temperature logic (°C)"));
        LinearLayout tempRow = row();
        tempOnInput = input("30", true);
        tempOnInput.setHint("ON >=");
        tempOffInput = input("28", true);
        tempOffInput.setHint("OFF <=");
        tempRow.addView(tempOnInput, weightParams());
        tempRow.addView(tempOffInput, weightParams());
        root.addView(tempRow);

        humidityEnabled = new CheckBox(this);
        humidityEnabled.setText("Also use inside-vs-outside humidity difference");
        root.addView(humidityEnabled);

        LinearLayout humRow = row();
        humidityOnInput = input("10", true);
        humidityOnInput.setHint("RH diff ON");
        humidityOffInput = input("6", true);
        humidityOffInput.setHint("RH diff OFF");
        humRow.addView(humidityOnInput, weightParams());
        humRow.addView(humidityOffInput, weightParams());
        root.addView(humRow);
        root.addView(button("Save AUTO Logic", v -> setFanLogic()));

        root.addView(label("Daily fan schedule"));
        LinearLayout scheduleRow = row();
        scheduleStartInput = input("10:00", false);
        scheduleStartInput.setHint("Start HH:MM");
        scheduleEndInput = input("16:30", false);
        scheduleEndInput.setHint("End HH:MM");
        scheduleRow.addView(scheduleStartInput, weightParams());
        scheduleRow.addView(scheduleEndInput, weightParams());
        root.addView(scheduleRow);
        root.addView(button("Enable Schedule Mode", v -> setFanSchedule()));

        root.addView(section("Weight & Drying Progress"));
        LinearLayout weightActions = row();
        weightActions.addView(button("Tare", v -> postSimple("/api/weight/tare", "Scale tared")), weightParams());
        weightActions.addView(button("Set Initial", v -> postSimple("/api/weight/initial", "Current load saved as 100%")), weightParams());
        root.addView(weightActions);

        thresholdInput = input("80", true);
        thresholdInput.setHint("Notice threshold %");
        root.addView(thresholdInput);
        root.addView(button("Set Weight Notice Threshold", v -> setThreshold()));

        calibrationInput = input("62.3", true);
        calibrationInput.setHint("Calibration factor");
        root.addView(calibrationInput);
        LinearLayout calRow = row();
        calRow.addView(button("Set Factor", v -> setCalibration()), weightParams());
        calRow.addView(button("Default 62.3", v -> postSimple("/api/weight/calibration/default", "Calibration reset")), weightParams());
        root.addView(calRow);

        knownWeightInput = input("800", true);
        knownWeightInput.setHint("Known weight in grams");
        root.addView(knownWeightInput);
        root.addView(button("Calibrate From Known Weight", v -> calibrateKnown()));

        root.addView(section("Drying Notice"));
        noticeText = panel("No notice");
        root.addView(noticeText);
        LinearLayout noticeButtons = row();
        noticeButtons.addView(button("Check Now", v -> fetchNotice(false)), weightParams());
        noticeButtons.addView(button("Acknowledge", v -> postSimple("/api/notice/clear", "Notice cleared")), weightParams());
        root.addView(noticeButtons);
        root.addView(text("The app checks this notice every 5 minutes while open.", 12, false));

        root.addView(section("OLED Mirror"));
        oledText = panel("Tap refresh to mirror the OLED");
        oledText.setTypeface(android.graphics.Typeface.MONOSPACE);
        root.addView(oledText);
        root.addView(button("Refresh OLED", v -> fetchOled()));

        root.addView(section("Battery"));
        batteryText = panel("Battery monitoring unavailable until the ESP32 voltage-divider hardware is enabled.");
        root.addView(batteryText);
        root.addView(button("Read Battery", v -> fetchBattery()));

        root.addView(space(16));
        TextView footer = text("All control stays on your local Wi-Fi. The dryer keeps its autonomous logic even if this app disconnects.", 12, false);
        footer.setGravity(Gravity.CENTER_HORIZONTAL);
        root.addView(footer);

        return scroll;
    }

    private void refreshAll() {
        refreshStatus(true);
        fetchOled();
        fetchBattery();
        fetchNotice(false);
        fetchFanConfig();
    }

    private void saveBaseUrl() {
        String value = baseUrlInput.getText().toString().trim();
        if (value.isEmpty()) {
            toast("Enter the ESP32 IP or solar-dryer.local");
            return;
        }
        if (!value.startsWith("http://") && !value.startsWith("https://")) value = "http://" + value;
        while (value.endsWith("/")) value = value.substring(0, value.length() - 1);
        baseUrlInput.setText(value);
        preferences.edit().putString(KEY_BASE_URL, value).apply();
        toast("Dryer address saved");
        testConnection();
    }

    private String baseUrl() {
        String value = baseUrlInput.getText().toString().trim();
        if (value.isEmpty()) value = DEFAULT_URL;
        return value;
    }

    private ApiClient api() {
        return new ApiClient(baseUrl());
    }

    private void testConnection() {
        runApi(() -> api().get("/api/status"), json -> {
            connectionText.setText("Connected • " + json.optString("device", "Smart Solar Dryer") + " • " + baseUrl());
            renderStatus(json);
            toast("ESP32 connected");
        }, "Connection failed");
    }

    private void refreshStatus(boolean showToast) {
        runApi(() -> api().get("/api/status"), json -> {
            connectionText.setText("Connected • " + baseUrl());
            renderStatus(json);
            if (showToast) toast("Dashboard refreshed");
        }, null);
    }

    private void renderStatus(JSONObject json) {
        JSONObject inside = json.optJSONObject("inside");
        JSONObject outside = json.optJSONObject("outside");
        JSONObject weight = json.optJSONObject("weight");
        JSONObject fan = json.optJSONObject("fan");
        JSONObject battery = json.optJSONObject("battery");
        JSONObject wifi = json.optJSONObject("wifi");

        StringBuilder b = new StringBuilder();
        b.append("INSIDE\n");
        if (inside != null && inside.optBoolean("valid", false)) {
            b.append(fmt(inside.optDouble("temp_c", Double.NaN))).append(" °C   ")
                    .append(fmt(inside.optDouble("humidity", Double.NaN))).append(" % RH\n\n");
        } else b.append("Sensor unavailable\n\n");

        b.append("OUTSIDE\n");
        if (outside != null && outside.optBoolean("valid", false)) {
            b.append(fmt(outside.optDouble("temp_c", Double.NaN))).append(" °C   ")
                    .append(fmt(outside.optDouble("humidity", Double.NaN))).append(" % RH\n\n");
        } else b.append("Sensor unavailable\n\n");

        b.append("WEIGHT\n");
        if (weight != null && weight.optBoolean("valid", false)) {
            b.append(fmt(weight.optDouble("grams", 0))).append(" g");
            if (weight.has("percent_remaining")) b.append("   •   ").append(fmt(weight.optDouble("percent_remaining", 0))).append(" % remaining");
            b.append("\n\n");
        } else b.append("Sensor unavailable\n\n");

        b.append("FAN\n");
        if (fan != null) b.append(fan.optBoolean("on", false) ? "ON" : "OFF").append("   •   ").append(fan.optString("mode", "?"));
        b.append("\n\n");

        b.append("NOTICE\n").append(json.optBoolean("notice", false) ? "WEIGHT THRESHOLD REACHED" : "None").append("\n\n");

        b.append("NETWORK\n");
        if (wifi != null && wifi.optBoolean("connected", false)) {
            b.append(wifi.optString("ip", "?")).append("   •   RSSI ").append(wifi.optInt("rssi", 0)).append(" dBm");
        } else b.append("Wi-Fi disconnected");

        if (battery != null && battery.optBoolean("available", false)) {
            b.append("\n\nBATTERY\n").append(fmt(battery.optDouble("voltage", 0))).append(" V   •   ")
                    .append(fmt(battery.optDouble("percent", 0))).append(" %");
        }
        statusText.setText(b.toString());
    }

    private void fetchFanConfig() {
        runApi(() -> api().get("/api/fan"), json -> {
            JSONObject logic = json.optJSONObject("logic");
            if (logic != null) {
                tempOnInput.setText(String.valueOf(logic.optDouble("temp_on", 30)));
                tempOffInput.setText(String.valueOf(logic.optDouble("temp_off", 28)));
                humidityEnabled.setChecked(logic.optBoolean("humidity_enabled", false));
                humidityOnInput.setText(String.valueOf(logic.optDouble("humidity_diff_on", 10)));
                humidityOffInput.setText(String.valueOf(logic.optDouble("humidity_diff_off", 6)));
            }
            JSONObject schedule = json.optJSONObject("schedule");
            if (schedule != null) {
                scheduleStartInput.setText(minutesToTime(schedule.optInt("start_minute", 600)));
                scheduleEndInput.setText(minutesToTime(schedule.optInt("end_minute", 990)));
            }
        }, null);
    }

    private void setFanLogic() {
        try {
            JSONObject body = new JSONObject();
            body.put("temp_on", number(tempOnInput));
            body.put("temp_off", number(tempOffInput));
            body.put("humidity_enabled", humidityEnabled.isChecked());
            body.put("humidity_diff_on", number(humidityOnInput));
            body.put("humidity_diff_off", number(humidityOffInput));
            runApi(() -> api().post("/api/fan/logic", body), json -> {
                toast("AUTO fan logic saved");
                refreshStatus(false);
            }, "Could not save fan logic");
        } catch (Exception e) {
            toast("Check the fan logic values");
        }
    }

    private void setFanSchedule() {
        try {
            JSONObject body = new JSONObject();
            body.put("start", scheduleStartInput.getText().toString().trim());
            body.put("end", scheduleEndInput.getText().toString().trim());
            body.put("enabled", true);
            runApi(() -> api().post("/api/fan/schedule", body), json -> {
                toast("Schedule enabled");
                refreshStatus(false);
            }, "Could not save schedule");
        } catch (Exception e) {
            toast("Use HH:MM times such as 10:00");
        }
    }

    private void setThreshold() {
        try {
            JSONObject body = new JSONObject();
            body.put("percent", number(thresholdInput));
            runApi(() -> api().post("/api/weight/threshold", body), json -> {
                toast("Weight threshold saved");
                fetchNotice(false);
            }, "Could not set threshold");
        } catch (Exception e) {
            toast("Enter a threshold from 1 to 100");
        }
    }

    private void setCalibration() {
        try {
            JSONObject body = new JSONObject();
            body.put("factor", number(calibrationInput));
            runApi(() -> api().post("/api/weight/calibration", body), json -> toast("Calibration factor saved"), "Calibration failed");
        } catch (Exception e) {
            toast("Enter a valid calibration factor");
        }
    }

    private void calibrateKnown() {
        try {
            JSONObject body = new JSONObject();
            body.put("known_grams", number(knownWeightInput));
            runApi(() -> api().post("/api/weight/calibrate-known", body), json -> {
                double factor = json.optDouble("factor", Double.NaN);
                if (!Double.isNaN(factor)) calibrationInput.setText(String.valueOf(factor));
                toast("Known-weight calibration complete");
            }, "Known-weight calibration failed");
        } catch (Exception e) {
            toast("Enter the known weight in grams");
        }
    }

    private void fetchNotice(boolean notify) {
        runApi(() -> api().get("/api/notice"), json -> {
            boolean active = json.optBoolean("notice", false);
            if (active) {
                String text = "Drying notice: " + fmt(json.optDouble("percent_remaining", 0)) + "% of initial weight remains";
                noticeText.setText(text);
                if (notify) showNoticeNotification(text);
            } else {
                double pct = json.optDouble("percent_remaining", Double.NaN);
                if (!Double.isNaN(pct)) noticeText.setText("No notice • " + fmt(pct) + "% of initial weight remains");
                else noticeText.setText("No drying notice");
            }
        }, null);
    }

    private void fetchOled() {
        runApi(() -> api().get("/api/oled"), json -> {
            String text = json.optString("text", "");
            if (text.isEmpty()) {
                JSONArray lines = json.optJSONArray("lines");
                if (lines != null) {
                    StringBuilder b = new StringBuilder();
                    for (int i = 0; i < lines.length(); i++) b.append(lines.optString(i)).append(i + 1 < lines.length() ? "\n" : "");
                    text = b.toString();
                }
            }
            oledText.setText(text.isEmpty() ? "OLED returned no text" : text);
        }, "Could not read OLED");
    }

    private void fetchBattery() {
        runApi(() -> api().get("/api/battery"), json -> {
            if (!json.optBoolean("available", false)) {
                batteryText.setText(json.optString("message", "Battery monitor hardware is not enabled on the ESP32."));
                return;
            }
            batteryText.setText(fmt(json.optDouble("voltage", 0)) + " V\n" + fmt(json.optDouble("percent_estimate", 0)) + " % estimated charge");
        }, "Could not read battery endpoint");
    }

    private void postSimple(String path, String success) {
        runApi(() -> api().post(path), json -> {
            toast(success);
            refreshStatus(false);
            if (path.contains("notice/clear")) fetchNotice(false);
        }, "Command failed");
    }

    private interface ApiTask { JSONObject run() throws Exception; }
    private interface ApiSuccess { void run(JSONObject json); }

    private void runApi(ApiTask task, ApiSuccess success, @Nullable String errorPrefix) {
        executor.execute(() -> {
            try {
                JSONObject json = task.run();
                runOnUiThread(() -> success.run(json));
            } catch (Exception e) {
                runOnUiThread(() -> {
                    if (errorPrefix != null) toast(errorPrefix + ": " + e.getMessage());
                    connectionText.setText("Offline / unreachable • " + baseUrl());
                });
            }
        });
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager manager = getSystemService(NotificationManager.class);
            NotificationChannel channel = new NotificationChannel(NOTICE_CHANNEL, "Drying notices", NotificationManager.IMPORTANCE_HIGH);
            channel.setDescription("Alerts when a Smart Solar Dryer weight threshold is reached");
            manager.createNotificationChannel(channel);
        }
    }

    private void requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1001);
        }
    }

    private void showNoticeNotification(String message) {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return;
        android.app.Notification.Builder builder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new android.app.Notification.Builder(this, NOTICE_CHANNEL)
                : new android.app.Notification.Builder(this);
        builder.setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("Solar Dryer Notice")
                .setContentText(message)
                .setStyle(new android.app.Notification.BigTextStyle().bigText(message))
                .setAutoCancel(true);
        ((NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE)).notify(2001, builder.build());
    }

    private double number(EditText input) {
        return Double.parseDouble(input.getText().toString().trim());
    }

    private String fmt(double value) {
        if (Double.isNaN(value)) return "--";
        return String.format(Locale.US, "%.1f", value);
    }

    private String minutesToTime(int minutes) {
        int h = Math.max(0, minutes) / 60;
        int m = Math.max(0, minutes) % 60;
        return String.format(Locale.US, "%02d:%02d", h, m);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private TextView text(String value, int sp, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(sp);
        view.setTextColor(0xFF182017);
        if (bold) view.setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD);
        view.setPadding(0, dp(4), 0, dp(4));
        return view;
    }

    private TextView section(String value) {
        TextView view = text(value, 20, true);
        view.setPadding(0, dp(18), 0, dp(8));
        return view;
    }

    private TextView label(String value) {
        TextView view = text(value, 14, true);
        view.setPadding(0, dp(10), 0, dp(4));
        return view;
    }

    private TextView panel(String value) {
        TextView view = text(value, 15, false);
        view.setPadding(dp(14), dp(14), dp(14), dp(14));
        view.setBackgroundColor(0xFFF0F4ED);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        p.setMargins(0, dp(4), 0, dp(8));
        view.setLayoutParams(p);
        return view;
    }

    private EditText input(String value, boolean numeric) {
        EditText edit = new EditText(this);
        edit.setText(value);
        edit.setTextSize(15);
        edit.setSingleLine(true);
        edit.setPadding(dp(10), dp(8), dp(10), dp(8));
        if (numeric) edit.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL | InputType.TYPE_NUMBER_FLAG_SIGNED);
        else edit.setInputType(InputType.TYPE_CLASS_TEXT);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        p.setMargins(dp(2), dp(3), dp(2), dp(3));
        edit.setLayoutParams(p);
        return edit;
    }

    private Button button(String value, View.OnClickListener listener) {
        Button button = new Button(this);
        button.setText(value);
        button.setOnClickListener(listener);
        button.setAllCaps(false);
        button.setMinHeight(dp(46));
        return button;
    }

    private LinearLayout row() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        return row;
    }

    private LinearLayout.LayoutParams weightParams() {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        p.setMargins(dp(2), dp(3), dp(2), dp(3));
        return p;
    }

    private View space(int heightDp) {
        View view = new View(this);
        view.setLayoutParams(new LinearLayout.LayoutParams(1, dp(heightDp)));
        return view;
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }
}
