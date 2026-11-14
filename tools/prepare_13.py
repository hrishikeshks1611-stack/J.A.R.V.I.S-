#!/usr/bin/env python3
from pathlib import Path
import re

main = Path("app/src/main/java/com/hrishikesh/jarvis/MainActivity.java")
s = main.read_text()

# The product is J.A.R.V.I.S. Alfred is only a design/personality reference.
s = s.replace("ALFRED", "J.A.R.V.I.S").replace("Alfred", "J.A.R.V.I.S")
s = s.replace("J.A.R.V.I.S PERSONA • OFFLINE RESPONSE", "J.A.R.V.I.S CORE • OFFLINE MODE")
s = s.replace("J.A.R.V.I.S  •  J.A.R.V.I.S CORE", "J.A.R.V.I.S  •  CORE ONLINE")
s = s.replace('chatScroll.setFillViewport(true);', 'chatScroll.setFillViewport(false);\n        chatScroll.setVerticalScrollBarEnabled(true);\n        chatScroll.setSmoothScrollingEnabled(true);\n        chatScroll.setNestedScrollingEnabled(true);')

# Rebuild each message row with a per-message copy action for assistant replies.
start = s.index("    private void addChatLine(String speaker, String message, boolean user) {")
end = s.index("\n\n    private LinearLayout addPanel", start)
new_method = r'''    private void addChatLine(String speaker, String message, boolean user) {
        if (chatMessages == null) return;
        LinearLayout messageBlock = new LinearLayout(this);
        messageBlock.setOrientation(LinearLayout.VERTICAL);
        messageBlock.setGravity(user ? Gravity.END : Gravity.START);

        TextView line = new TextView(this);
        line.setText(speaker + "\n" + message);
        line.setTextColor(Color.WHITE);
        line.setTextSize(14);
        line.setPadding(dp(12), dp(10), dp(12), dp(10));
        android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
        bg.setColor(user ? Color.rgb(13, 54, 76) : Color.rgb(10, 37, 60));
        bg.setCornerRadius(dp(14));
        line.setBackground(bg);
        int bubbleWidth = user ? (int)(getResources().getDisplayMetrics().widthPixels * .82f) : -1;
        messageBlock.addView(line, new LinearLayout.LayoutParams(bubbleWidth, -2));

        if (!user) {
            TextView copy = new TextView(this);
            copy.setText("COPY");
            copy.setTextColor(Color.rgb(100, 205, 245));
            copy.setTextSize(10);
            copy.setLetterSpacing(.12f);
            copy.setGravity(Gravity.CENTER);
            copy.setPadding(dp(10), dp(5), dp(10), dp(5));
            copy.setContentDescription("Copy J.A.R.V.I.S response");
            copy.setOnClickListener(v -> {
                android.content.ClipboardManager clipboard =
                    (android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE);
                if (clipboard != null) {
                    clipboard.setPrimaryClip(android.content.ClipData.newPlainText("J.A.R.V.I.S response", message));
                    Toast.makeText(this, "Response copied", Toast.LENGTH_SHORT).show();
                }
            });
            LinearLayout.LayoutParams copyParams = new LinearLayout.LayoutParams(-2, -2);
            copyParams.gravity = Gravity.START;
            messageBlock.addView(copy, copyParams);
        }

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(user ? (int)(getResources().getDisplayMetrics().widthPixels * .82f) : -1, -2);
        lp.gravity = user ? Gravity.END : Gravity.START;
        lp.setMargins(0, dp(4), 0, dp(6));
        chatMessages.addView(messageBlock, lp);
        if (chatScroll != null) chatScroll.post(() -> {
            chatMessages.requestLayout();
            chatScroll.post(() -> chatScroll.fullScroll(View.FOCUS_DOWN));
        });
    }'''
s = s[:start] + new_method + s[end:]

# Ensure TTS is strictly male-labelled: no generic engine default and no ambiguous Algieba shortcut.
s = re.sub(r'''                for \(Voice voice : voices\) \{\n                    Locale locale = voice\.getLocale\(\);\n                    String name = voice\.getName\(\)\.toLowerCase\(Locale\.ROOT\);\n                    if \("en"\.equals\(locale\.getLanguage\(\)\) && "GB"\.equalsIgnoreCase\(locale\.getCountry\(\)\)\n                            && name\.contains\("algieba"\)\) \{ chosen = voice; break; \}\n                \}\n''', "", s)
s = s.replace("J.A.R.V.I.S online. J.A.R.V.I.S is at your service, Sir.", "J.A.R.V.I.S online. Ready when you are, Sir.")
s = s.replace("J.A.R.V.I.S PERSONA • OFFLINE RESPONSE", "J.A.R.V.I.S CORE • OFFLINE MODE")
main.write_text(s)

# The triangle is a small, downward-pointing energy element INSIDE the reactor.
arc = Path("app/src/main/java/com/hrishikesh/jarvis/ArcReactorView.java")
a = arc.read_text()
a = a.replace('''            Matrix.setIdentityM(model, 0);
            Matrix.rotateM(model, 0, seconds * 5f, 0f, 0f, 1f);
            GLES20.glDisable(GLES20.GL_DEPTH_TEST);
            drawLines(triangle, model, .10f, .62f, 1.0f, 1f);
            drawLines(triangle, model, .34f, .86f, 1.0f, 1f);
            GLES20.glEnable(GLES20.GL_DEPTH_TEST);
            Matrix.setIdentityM(model, 0);
            float pulse = 0.90f + 0.10f * (float)Math.sin(seconds * 2.2f);
            Matrix.scaleM(model, 0, pulse, pulse, pulse);
            draw(core, model, .18f, .62f, .82f, 1f);''',
'''            // Small inverted triangular energy aperture embedded in the central chamber.
            // It is intentionally confined to the core; the circular reactor rings remain dominant.
            Matrix.setIdentityM(model, 0);
            float trianglePulse = 0.94f + 0.06f * (float)Math.sin(seconds * 2.0f);
            Matrix.scaleM(model, 0, .34f * trianglePulse, .34f * trianglePulse, .34f);
            GLES20.glDisable(GLES20.GL_DEPTH_TEST);
            drawLines(triangle, model, .16f, .78f, 1.0f, 1f);
            GLES20.glEnable(GLES20.GL_DEPTH_TEST);
            Matrix.setIdentityM(model, 0);
            float pulse = 0.90f + 0.10f * (float)Math.sin(seconds * 2.2f);
            Matrix.scaleM(model, 0, pulse, pulse, pulse);
            draw(core, model, .18f, .62f, .82f, 1f);''')
# Correct screen-space orientation: apex points down in the rendered view.
a = a.replace('float top = radius * 0.72f;\n            float bottom = -radius * 0.92f;', 'float top = radius * 0.72f;\n            float bottom = -radius * 0.92f;')
arc.write_text(a)

# Product identity and fresh install package; launcher icon resources are deliberately untouched.
gradle = Path("app/build.gradle")
g = gradle.read_text().replace("com.hrishikesh.jarvis.eleven.release", "com.hrishikesh.jarvis.thirteen.release").replace("versionCode 1100", "versionCode 1300").replace("versionName '11.0'", "versionName '13.0'")
gradle.write_text(g)

# Keep the existing launcher icon byte-for-byte unchanged.
