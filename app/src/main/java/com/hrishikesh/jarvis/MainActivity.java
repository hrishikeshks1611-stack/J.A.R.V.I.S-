package com.hrishikesh.jarvis;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.speech.tts.Voice;
import android.view.Gravity;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.RotateAnimation;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Set;

public final class MainActivity extends Activity {
    private static final int REQ_AUDIO = 41, REQ_CAMERA = 42, REQ_SPEECH = 43;
    private TextToSpeech tts;
    private ConversationStore memory;
    private ArcReactorView reactorView;
    private SpeechRecognizer recognizer;
    private TextView status, transcript;
    private boolean ttsReady = false;
    private boolean wakeListeningRequested = false;
    private final int cyan = Color.rgb(75, 210, 255);

    @Override protected void onCreate(Bundle state) {
        setTheme(R.style.AppTheme);
        super.onCreate(state);
        try {
            memory = new ConversationStore(this);
            buildInterface();
            initialiseSpeech();
            setStatus("CORE ONLINE • READY FOR COMMAND");
        } catch (Throwable error) {
            showStartupFailure(error);
        }
    }

    private void buildInterface() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(dp(18), dp(18), dp(18), dp(26));
        root.setBackgroundColor(Color.rgb(2, 8, 18));
        scroll.addView(root);

        TextView title = new TextView(this);
        title.setText("J.A.R.V.I.S.");
        title.setTextColor(cyan);
        title.setTextSize(27);
        title.setLetterSpacing(.24f);
        title.setGravity(Gravity.CENTER);
        root.addView(title, matchWrap());

        reactorView = new ArcReactorView(this);
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(dp(260), dp(260));
        rp.setMargins(0, dp(6), 0, dp(8));
        root.addView(reactorView, rp);

        LinearLayout corePanel = addPanel(root, "CORE STATUS");
        status = new TextView(this);
        status.setTextColor(Color.rgb(142, 213, 240));
        status.setTextSize(12);
        status.setGravity(Gravity.CENTER);
        status.setPadding(0, dp(4), 0, dp(6));
        corePanel.addView(status, matchWrap());

        transcript = new TextView(this);
        transcript.setTextColor(Color.WHITE);
        transcript.setTextSize(14);
        transcript.setGravity(Gravity.CENTER);
        transcript.setPadding(dp(4), dp(8), dp(4), dp(8));
        corePanel.addView(transcript, matchWrap());

        LinearLayout voicePanel = addPanel(root, "VOICE & SYSTEM CONTROL");
        Button listen = makeButton("TALK TO JARVIS");
        listen.setOnClickListener(v -> startListening());
        voicePanel.addView(listen, matchWrap());

        Button wake = makeButton("START BACKGROUND WAKE LISTENING");
        wake.setOnClickListener(v -> startWakeListening());
        voicePanel.addView(wake, matchWrap());

        Button stopWake = makeButton("STOP WAKE LISTENING");
        stopWake.setOnClickListener(v -> stopWakeListening());
        voicePanel.addView(stopWake, matchWrap());

        Button apps = makeButton("DISCOVER INSTALLED APPS");
        apps.setOnClickListener(v -> showApps());
        voicePanel.addView(apps, matchWrap());

        LinearLayout devicePanel = addPanel(root, "DEVICE & MEDIA CONTROL");
        Button camera = makeButton("OPEN JARVIS CAMERA");
        camera.setOnClickListener(v -> openCamera());
        devicePanel.addView(camera, matchWrap());

        Button mediaAccess = makeButton("ENABLE MEDIA SESSION ACCESS");
        mediaAccess.setOnClickListener(v -> {
            if (MediaControl.hasAccess(this)) setStatus("MEDIA SESSION ACCESS ENABLED");
            else MediaControl.openAccessSettings(this);
        });
        devicePanel.addView(mediaAccess, matchWrap());

        Button music = makeButton("SEARCH & PLAY MUSIC");
        music.setOnClickListener(v -> playMusic("Back in Black"));
        devicePanel.addView(music, matchWrap());

        LinearLayout memoryPanel = addPanel(root, "PRIVATE MEMORY");
        Button viewMemory = makeButton("VIEW RECENT MEMORY");
        viewMemory.setOnClickListener(v -> showMemory());
        memoryPanel.addView(viewMemory, matchWrap());

        Button clearMemory = makeButton("CLEAR LOCAL MEMORY");
        clearMemory.setOnClickListener(v -> confirmClearMemory());
        memoryPanel.addView(clearMemory, matchWrap());

        TextView note = new TextView(this);
        note.setText("USER-CONTROLLED MICROPHONE • ENCRYPTED LOCAL MEMORY • PRIVATE CAMERA CAPTURE");
        note.setTextColor(Color.rgb(90, 132, 158));
        note.setTextSize(10);
        note.setGravity(Gravity.CENTER);
        note.setPadding(0, dp(14), 0, dp(2));
        root.addView(note, matchWrap());
        setContentView(scroll);
    }

    private LinearLayout addPanel(LinearLayout root, String heading) {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(12), dp(10), dp(12), dp(12));
        android.graphics.drawable.GradientDrawable background = new android.graphics.drawable.GradientDrawable();
        background.setColor(Color.argb(210, 5, 20, 37));
        background.setCornerRadius(dp(13));
        background.setStroke(dp(1), Color.argb(170, 42, 155, 210));
        panel.setBackground(background);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.setMargins(0, dp(7), 0, dp(7));
        root.addView(panel, params);
        TextView label = new TextView(this);
        label.setText(heading);
        label.setTextColor(Color.rgb(75, 210, 255));
        label.setTextSize(11);
        label.setLetterSpacing(.12f);
        label.setPadding(dp(3), 0, dp(3), dp(5));
        panel.addView(label, matchWrap());
        return panel;
    }

    private void initialiseSpeech() {
        try {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            setStatus("VOICE RECOGNITION UNAVAILABLE ON THIS DEVICE");
        } else {
            recognizer = SpeechRecognizer.createSpeechRecognizer(this);
            recognizer.setRecognitionListener(new android.speech.RecognitionListener() {
                public void onReadyForSpeech(Bundle p) { setStatus("LISTENING…"); }
                public void onBeginningOfSpeech() { }
                public void onRmsChanged(float r) { }
                public void onBufferReceived(byte[] b) { }
                public void onEndOfSpeech() { setStatus("PROCESSING COMMAND…"); }
                public void onError(int e) { setStatus("VOICE ERROR " + e + " • TAP TO RETRY"); }
                public void onResults(Bundle b) {
                    ArrayList<String> results = b.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                    if (results != null && !results.isEmpty()) {
                        String command = results.get(0);
                        transcript.setText("“" + command + "”");
                        handleCommand(command);
                    } else setStatus("NO SPEECH DETECTED");
                }
                public void onPartialResults(Bundle b) { }
                public void onEvent(int t, Bundle p) { }
            });
        }
        } catch (Throwable speechError) {
            recognizer = null;
            setStatus("VOICE SERVICE UNAVAILABLE • APP CORE STILL READY");
        }
        try {
        tts = new TextToSpeech(this, result -> {
            if (result == TextToSpeech.SUCCESS) {
                ttsReady = configureVoice();
                if (ttsReady) speak("At your service, Sir.");
                else setStatus("ALFRED UK MALE VOICE UNAVAILABLE • AUDIO MUTED");
            }
        });
        } catch (Throwable ttsError) {
            tts = null;
            ttsReady = false;
        }
    }

    /**
     * Strict Alfred voice gate: never use the engine default or an unclassified voice.
     * Only an en-GB Algieba voice or an explicitly male-labelled en-GB voice is allowed.
     */
    private boolean configureVoice() {
        if (tts == null) return false;
        try {
            Set<Voice> voices = tts.getVoices();
            if (voices == null) return false;
            Voice chosen = null;
            for (Voice voice : voices) {
                String name = voice.getName().toLowerCase(Locale.ROOT);
                Locale locale = voice.getLocale();
                if (!"en".equals(locale.getLanguage()) || !"GB".equalsIgnoreCase(locale.getCountry())) continue;
                if (name.contains("female")) continue;
                if (name.contains("algieba") || name.contains("male") || name.contains("masculine")) {
                    chosen = voice;
                    if (name.contains("algieba")) break;
                }
            }
            if (chosen == null) return false;
            int language = tts.setLanguage(Locale.UK);
            if (language == TextToSpeech.LANG_MISSING_DATA || language == TextToSpeech.LANG_NOT_SUPPORTED) return false;
            return tts.setVoice(chosen) == TextToSpeech.SUCCESS;
        } catch (Exception ignored) { return false; }
    }

    private void startWakeListening() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            wakeListeningRequested = true;
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQ_AUDIO);
            return;
        }
        Intent service = new Intent(this, WakeListeningService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(service);
        else startService(service);
        setStatus("BACKGROUND WAKE LISTENING REQUESTED • SEE NOTIFICATION");
    }

    private void stopWakeListening() {
        stopService(new Intent(this, WakeListeningService.class));
        setStatus("BACKGROUND WAKE LISTENING STOPPED");
    }

    private void startListening() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQ_AUDIO);
            return;
        }
        if (recognizer == null) { setStatus("SPEECH SERVICE NOT AVAILABLE"); return; }
        Intent i = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-GB");
        i.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
        recognizer.startListening(i);
    }

    private void handleCommand(String raw) {
        if (memory != null) memory.add("user", raw);
        CommandParser.Command command = CommandParser.parse(raw);
        switch (command.action) {
            case OPEN_CAMERA:
                openCamera();
                break;
            case OPEN_APP:
                launchApp(command.argument);
                break;
            case PLAY_MUSIC:
                playMusic(command.argument.isEmpty() ? "music" : command.argument);
                break;
            case PAUSE_MEDIA:
            case RESUME_MEDIA:
            case NEXT_TRACK:
            case PREVIOUS_TRACK:
                if (MediaControl.execute(this, command.action)) {
                    speak("As you wish, Sir."); setStatus("MEDIA COMMAND SENT");
                } else {
                    speak("Media access is unavailable, Sir. Please enable JARVIS media controls.");
                    setStatus("ENABLE MEDIA CONTROLS IN SETTINGS");
                }
                break;
            case GREETING:
                speak(PersonaEngine.respond(raw, memory == null ? null : memory.recent(12)));
                setStatus("JARVIS ONLINE • ALFRED PERSONA ACTIVE");
                break;
            default:
                String reply = PersonaEngine.respond(raw, memory == null ? null : memory.recent(12));
                if (transcript != null) transcript.setText("“" + raw + "”\n\n" + reply);
                speak(reply);
                setStatus("ALFRED PERSONA • OFFLINE RESPONSE");
                break;
        }
    }

    private void launchApp(String requested) {
        try {
            PackageManager pm = getPackageManager();
            for (ApplicationInfo app : pm.getInstalledApplications(0)) {
                String label = pm.getApplicationLabel(app).toString();
                if (label.equalsIgnoreCase(requested) || label.toLowerCase(Locale.ROOT).contains(requested)) {
                    Intent launch = pm.getLaunchIntentForPackage(app.packageName);
                    if (launch != null) { launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); startActivity(launch); speak("Opening " + label + ", Sir."); setStatus("LAUNCHING " + label.toUpperCase(Locale.ROOT)); return; }
                }
            }
            speak("I could not find " + requested + ", Sir.");
            setStatus("APP NOT FOUND: " + requested);
        } catch (Exception e) { setStatus("APP LAUNCH FAILED"); }
    }

    private void showApps() {
        StringBuilder names = new StringBuilder("AVAILABLE APPS\n");
        int count = 0;
        for (ApplicationInfo app : getPackageManager().getInstalledApplications(0)) {
            if ((app.flags & ApplicationInfo.FLAG_SYSTEM) == 0) {
                names.append("• ").append(getPackageManager().getApplicationLabel(app)).append("\n");
                count++;
            }
        }
        transcript.setText(names.toString());
        setStatus(count + " USER APPS DISCOVERED");
    }

    private void openCamera() {
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.CAMERA}, REQ_CAMERA); return;
        }
        try {
            startActivity(new Intent(this, CameraActivity.class));
            setStatus("OPENING JARVIS CAMERA");
        } catch (Exception e) { setStatus("CAMERA COULD NOT BE OPENED"); speak("I could not open the camera, Sir."); }
    }

    private void playMusic(String query) {
        Intent i = new Intent(Intent.ACTION_VIEW);
        i.setData(android.net.Uri.parse("https://music.youtube.com/search?q=" + android.net.Uri.encode(query)));
        try { startActivity(i); setStatus("OPENING MUSIC SEARCH • " + query.toUpperCase(Locale.ROOT)); speak("Searching for " + query + ", Sir."); }
        catch (Exception e) { setStatus("NO MUSIC HANDLER AVAILABLE"); }
    }

    private void showMemory() {
        if (memory == null) return;
        StringBuilder output = new StringBuilder("ENCRYPTED LOCAL MEMORY");
        output.append((char) 10);
        java.util.List<ConversationStore.Entry> entries = memory.recent(20);
        if (entries.isEmpty()) output.append("No saved conversation yet.");
        for (ConversationStore.Entry entry : entries) {
            output.append(entry.role.equals("user") ? "YOU: " : "JARVIS: ")
                    .append(entry.text).append((char) 10);
        }
        transcript.setText(output.toString());
        setStatus(entries.size() + " RECENT ENTRIES");
    }

    private void confirmClearMemory() {
        new android.app.AlertDialog.Builder(this)
                .setTitle("Clear JARVIS memory?")
                .setMessage("This permanently clears the local conversation history on this device.")
                .setNegativeButton("CANCEL", (dialog, which) -> dialog.dismiss())
                .setPositiveButton("CLEAR", (dialog, which) -> {
                    if (memory != null) memory.clear();
                    transcript.setText("Local memory cleared.");
                    setStatus("LOCAL MEMORY CLEARED");
                }).show();
    }

    private void speak(String text) {
        if (memory != null) memory.add("assistant", text);
        if (ttsReady && tts != null) tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "jarvis-" + System.currentTimeMillis());
    }
    private void setStatus(String value) { if (status != null) status.setText(value); }
    private Button makeButton(String text) {
        Button b = new Button(this); b.setText(text); b.setTextColor(cyan); b.setAllCaps(false);
        b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.rgb(10, 37, 60)));
        LinearLayout.LayoutParams p = matchWrap(); p.setMargins(0, dp(6), 0, dp(6)); b.setLayoutParams(p); return b;
    }
    private LinearLayout.LayoutParams matchWrap() { return new LinearLayout.LayoutParams(-1, -2); }
    private int dp(int v) { return (int)(v * getResources().getDisplayMetrics().density + .5f); }
    private void showStartupFailure(Throwable e) {
        TextView t = new TextView(this);
        t.setText("J.A.R.V.I.S.\nStartup error: " + e.getClass().getSimpleName() + "\n" + String.valueOf(e.getMessage()));
        t.setTextColor(Color.WHITE); t.setBackgroundColor(Color.rgb(3,9,20)); t.setGravity(Gravity.CENTER); t.setPadding(24,24,24,24);
        setContentView(t);
    }
    @Override public void onRequestPermissionsResult(int request, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(request, permissions, results);
        if (results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) {
            if (request == REQ_AUDIO) { if (wakeListeningRequested) { wakeListeningRequested = false; startWakeListening(); } else startListening(); }
            else if (request == REQ_CAMERA) openCamera();
        } else setStatus("PERMISSION DENIED • FEATURE UNAVAILABLE");
    }
    @Override protected void onResume() { super.onResume(); if (reactorView != null) reactorView.onResume(); }
    @Override protected void onPause() { if (reactorView != null) reactorView.onPause(); super.onPause(); }
    @Override protected void onDestroy() {
        if (recognizer != null) { try { recognizer.destroy(); } catch (Exception ignored) { } recognizer = null; }
        if (tts != null) { try { tts.stop(); tts.shutdown(); } catch (Exception ignored) { } tts = null; }
        if (memory != null) { memory.close(); memory = null; }
        super.onDestroy();
    }
}