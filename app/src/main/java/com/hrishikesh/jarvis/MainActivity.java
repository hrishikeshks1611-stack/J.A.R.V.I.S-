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
    private SpeechRecognizer recognizer;
    private TextView status, transcript;
    private boolean ttsReady = false;
    private boolean wakeListeningRequested = false;
    private final int cyan = Color.rgb(75, 210, 255);

    @Override protected void onCreate(Bundle state) {
        setTheme(R.style.AppTheme);
        super.onCreate(state);
        try {
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
        root.setPadding(dp(22), dp(24), dp(22), dp(24));
        root.setBackgroundColor(Color.rgb(3, 9, 20));
        scroll.addView(root);

        TextView title = new TextView(this);
        title.setText("J.A.R.V.I.S.");
        title.setTextColor(cyan);
        title.setTextSize(26);
        title.setLetterSpacing(.2f);
        title.setGravity(Gravity.CENTER);
        root.addView(title, matchWrap());

        ImageView reactor = new ImageView(this);
        reactor.setImageResource(R.drawable.jarvis_reactor);
        reactor.setScaleType(ImageView.ScaleType.FIT_CENTER);
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(dp(220), dp(220));
        rp.setMargins(0, dp(18), 0, dp(12));
        root.addView(reactor, rp);
        RotateAnimation rotation = new RotateAnimation(0, 360, Animation.RELATIVE_TO_SELF, .5f, Animation.RELATIVE_TO_SELF, .5f);
        rotation.setDuration(18000);
        rotation.setRepeatCount(Animation.INFINITE);
        reactor.startAnimation(rotation);

        status = new TextView(this);
        status.setTextColor(Color.rgb(142, 213, 240));
        status.setTextSize(12);
        status.setGravity(Gravity.CENTER);
        root.addView(status, matchWrap());

        transcript = new TextView(this);
        transcript.setTextColor(Color.WHITE);
        transcript.setTextSize(15);
        transcript.setGravity(Gravity.CENTER);
        transcript.setPadding(0, dp(14), 0, dp(14));
        root.addView(transcript, matchWrap());

        Button listen = makeButton("TALK TO JARVIS");
        listen.setOnClickListener(v -> startListening());
        root.addView(listen, matchWrap());

        Button wake = makeButton("START BACKGROUND WAKE LISTENING");
        wake.setOnClickListener(v -> startWakeListening());
        root.addView(wake, matchWrap());

        Button stopWake = makeButton("STOP WAKE LISTENING");
        stopWake.setOnClickListener(v -> stopWakeListening());
        root.addView(stopWake, matchWrap());

        Button camera = makeButton("OPEN CAMERA");
        camera.setOnClickListener(v -> openCamera());
        root.addView(camera, matchWrap());

        Button apps = makeButton("DISCOVER INSTALLED APPS");
        apps.setOnClickListener(v -> showApps());
        root.addView(apps, matchWrap());

        Button mediaAccess = makeButton("ENABLE MEDIA CONTROLS");
        mediaAccess.setOnClickListener(v -> {
            if (MediaControl.hasAccess(this)) setStatus("MEDIA SESSION ACCESS ENABLED");
            else MediaControl.openAccessSettings(this);
        });
        root.addView(mediaAccess, matchWrap());

        Button music = makeButton("PLAY MUSIC");
        music.setOnClickListener(v -> playMusic("Back in Black"));
        root.addView(music, matchWrap());

        TextView note = new TextView(this);
        note.setText("VOICE INPUT • FOREGROUND WAKE LISTENER • APP LAUNCH • IN-APP CAMERA • MEDIA SEARCH\nEncrypted pairing, shared memory and full media-session control are still in development.");
        note.setTextColor(Color.rgb(105, 145, 170));
        note.setTextSize(11);
        note.setGravity(Gravity.CENTER);
        note.setPadding(0, dp(18), 0, dp(4));
        root.addView(note, matchWrap());
        setContentView(scroll);
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
                ttsReady = true;
                configureVoice();
                speak("At your service, Sir.");
            }
        });
        } catch (Throwable ttsError) {
            tts = null;
            ttsReady = false;
        }
    }

    private void configureVoice() {
        if (tts == null) return;
        tts.setLanguage(Locale.UK);
        try {
            Set<Voice> voices = tts.getVoices();
            Voice chosen = null;
            if (voices != null) {
                for (Voice voice : voices) {
                    String n = voice.getName().toLowerCase(Locale.ROOT);
                    if (n.contains("algieba") && voice.getLocale().getLanguage().equals("en")) { chosen = voice; break; }
                }
                if (chosen == null) for (Voice voice : voices) {
                    String n = voice.getName().toLowerCase(Locale.ROOT);
                    if (voice.getLocale().getLanguage().equals("en") && voice.getLocale().getCountry().equals("GB")
                            && !n.contains("female")) { chosen = voice; break; }
                }
            }
            if (chosen != null) tts.setVoice(chosen);
        } catch (Exception ignored) { }
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
                speak("Always here, Sir.");
                setStatus("JARVIS ONLINE");
                break;
            default:
                speak("I heard you, Sir. That command is not supported yet.");
                setStatus("COMMAND RECEIVED • ACTION NOT IMPLEMENTED");
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
        i.setData(android.net.Uri.parse("https://www.youtube.com/results?search_query=" + android.net.Uri.encode(query)));
        try { startActivity(i); setStatus("OPENING MUSIC SEARCH • " + query.toUpperCase(Locale.ROOT)); speak("Searching for " + query + ", Sir."); }
        catch (Exception e) { setStatus("NO MUSIC HANDLER AVAILABLE"); }
    }

    private void speak(String text) {
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
    @Override protected void onDestroy() {
        if (recognizer != null) { try { recognizer.destroy(); } catch (Exception ignored) { } recognizer = null; }
        if (tts != null) { try { tts.stop(); tts.shutdown(); } catch (Exception ignored) { } tts = null; }
        super.onDestroy();
    }
}