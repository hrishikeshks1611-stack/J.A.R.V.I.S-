package com.hrishikesh.jarvis;

import android.app.Activity;
import android.os.Bundle;
import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.ScaleAnimation;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

public final class MainActivity extends Activity {
    @Override protected void onCreate(Bundle state) {
        setTheme(R.style.AppTheme);
        super.onCreate(state);
        try {
            LinearLayout root = new LinearLayout(this);
            root.setOrientation(LinearLayout.VERTICAL);
            root.setGravity(Gravity.CENTER);
            root.setPadding(28, 36, 28, 36);
            root.setBackgroundColor(Color.rgb(3, 9, 20));

            TextView title = new TextView(this);
            title.setText("J.A.R.V.I.S.");
            title.setTextColor(Color.rgb(110, 218, 255));
            title.setTextSize(28);
            title.setGravity(Gravity.CENTER);
            title.setLetterSpacing(0.18f);

            ImageView reactor = new ImageView(this);
            reactor.setImageResource(R.drawable.jarvis_reactor);
            reactor.setScaleType(ImageView.ScaleType.FIT_CENTER);
            LinearLayout.LayoutParams reactorParams = new LinearLayout.LayoutParams(260, 260);
            reactorParams.setMargins(0, 28, 0, 28);
            reactor.setLayoutParams(reactorParams);
            ScaleAnimation pulse = new ScaleAnimation(0.96f, 1.04f, 0.96f, 1.04f,
                    Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF, 0.5f);
            pulse.setDuration(1800);
            pulse.setRepeatMode(Animation.REVERSE);
            pulse.setRepeatCount(Animation.INFINITE);
            reactor.startAnimation(pulse);

            TextView status = new TextView(this);
            status.setText("FRESH CORE  •  INITIALISING");
            status.setTextColor(Color.rgb(160, 205, 230));
            status.setTextSize(12);
            status.setGravity(Gravity.CENTER);
            status.setLetterSpacing(0.12f);

            root.addView(title);
            root.addView(reactor);
            root.addView(status);
            setContentView(root);
        } catch (RuntimeException error) {
            TextView fallback = new TextView(this);
            fallback.setText("J.A.R.V.I.S.\nCore startup error");
            fallback.setTextColor(Color.WHITE);
            fallback.setBackgroundColor(Color.rgb(3, 9, 20));
            fallback.setGravity(Gravity.CENTER);
            setContentView(fallback);
        }
    }
}