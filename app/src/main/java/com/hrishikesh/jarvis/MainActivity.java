package com.hrishikesh.jarvis;

import android.app.Activity;
import android.os.Bundle;
import android.graphics.Color;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

public final class MainActivity extends Activity {
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setPadding(28, 28, 28, 28);
        root.setBackgroundColor(Color.rgb(3, 9, 20));
        TextView title = new TextView(this);
        title.setText("J.A.R.V.I.S.");
        title.setTextColor(Color.rgb(87, 202, 255));
        title.setTextSize(30);
        title.setGravity(Gravity.CENTER);
        title.setLetterSpacing(0.18f);
        TextView reactor = new TextView(this);
        reactor.setText("◉");
        reactor.setTextColor(Color.rgb(61, 180, 255));
        reactor.setTextSize(112);
        reactor.setGravity(Gravity.CENTER);
        TextView status = new TextView(this);
        status.setText("FRESH CORE  •  INITIALISING");
        status.setTextColor(Color.rgb(160, 190, 220));
        status.setTextSize(12);
        status.setGravity(Gravity.CENTER);
        status.setLetterSpacing(0.12f);
        root.addView(title);
        root.addView(reactor);
        root.addView(status);
        setContentView(root);
    }
}
