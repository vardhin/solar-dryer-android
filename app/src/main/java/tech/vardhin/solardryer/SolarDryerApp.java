package tech.vardhin.solardryer;

import android.app.Activity;
import android.app.Application;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

public class SolarDryerApp extends Application implements Application.ActivityLifecycleCallbacks {
    private static final int FOOTER_ID = 0x53DA001;

    @Override
    public void onCreate() {
        super.onCreate();
        registerActivityLifecycleCallbacks(this);
    }

    @Override
    public void onActivityResumed(Activity activity) {
        if (!(activity instanceof MainActivity)) return;
        FrameLayout content = activity.findViewById(android.R.id.content);
        if (content == null || content.findViewById(FOOTER_ID) != null) return;

        LinearLayout bar = new LinearLayout(activity);
        bar.setId(FOOTER_ID);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(activity, 14), dp(activity, 8), dp(activity, 14), dp(activity, 8));
        bar.setBackgroundColor(Color.rgb(31, 57, 33));
        bar.setElevation(dp(activity, 10));

        TextView label = new TextView(activity);
        label.setText("Ask the dryer");
        label.setTextColor(Color.WHITE);
        label.setTextSize(16);
        label.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        bar.addView(label, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        Button key = new Button(activity);
        key.setText("AI Key");
        key.setAllCaps(false);
        key.setOnClickListener(v -> {
            Intent i = new Intent(activity, VoiceAgentActivity.class);
            i.putExtra(VoiceAgentActivity.EXTRA_OPEN_SETTINGS, true);
            activity.startActivity(i);
        });
        bar.addView(key, new LinearLayout.LayoutParams(dp(activity, 88), dp(activity, 48)));

        Button mic = new Button(activity);
        mic.setText("🎤");
        mic.setTextSize(22);
        mic.setAllCaps(false);
        mic.setContentDescription("Open voice assistant");
        mic.setOnClickListener(v -> {
            Intent i = new Intent(activity, VoiceAgentActivity.class);
            i.putExtra(VoiceAgentActivity.EXTRA_START_RECORDING, true);
            activity.startActivity(i);
        });
        LinearLayout.LayoutParams micParams = new LinearLayout.LayoutParams(dp(activity, 64), dp(activity, 48));
        micParams.setMargins(dp(activity, 8), 0, 0, 0);
        bar.addView(mic, micParams);

        FrameLayout.LayoutParams p = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(activity, 66),
                Gravity.BOTTOM);
        content.addView(bar, p);
    }

    private int dp(Activity a, int value) {
        return Math.round(value * a.getResources().getDisplayMetrics().density);
    }

    @Override public void onActivityCreated(Activity a, Bundle b) {}
    @Override public void onActivityStarted(Activity a) {}
    @Override public void onActivityPaused(Activity a) {}
    @Override public void onActivityStopped(Activity a) {}
    @Override public void onActivitySaveInstanceState(Activity a, Bundle b) {}
    @Override public void onActivityDestroyed(Activity a) {}
}
