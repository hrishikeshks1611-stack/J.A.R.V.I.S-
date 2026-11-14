package com.hrishikesh.jarvis;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.speech.tts.Voice;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Set;

/**
 * User-started foreground wake listener. Android's installed recognition provider performs
 * recognition; this is not an offline hotword engine and requires a compatible provider.
 */
public final class WakeListeningService extends Service {
    private static final String CHANNEL = "jarvis_wake_listener";
    private static final int NOTIFICATION_ID = 7104;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private SpeechRecognizer recognizer;
    private TextToSpeech tts;
    private ConversationStore memory;
    private boolean ready;
    private boolean listening;
    private boolean stopping;
    private boolean onDeviceRecognition;

    @Override public void onCreate() {
        super.onCreate();
        memory = new ConversationStore(this);
        createChannel();
        startForeground(NOTIFICATION_ID, buildNotification("Listening for your wake phrase"));
        initialiseTts();
        beginRecognition();
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && "STOP".equals(intent.getAction())) stopSelf();
        return START_STICKY;
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel = new NotificationChannel(CHANNEL, "JARVIS voice activation",
                    NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("Persistent notification while JARVIS listens for a wake phrase");
            getSystemService(NotificationManager.class).createNotificationChannel(channel);
        }
    }

    private Notification buildNotification(String message) {
        Intent stop = new Intent(this, WakeListeningService.class);
        stop.setAction("STOP");
        PendingIntent pending = PendingIntent.getService(this, 7105, stop,
                PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0));
        Intent open = new Intent(this, MainActivity.class);
        open.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent openPending = PendingIntent.getActivity(this, 7106, open,
                PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0));
        Notification.Builder builder = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, CHANNEL) : new Notification.Builder(this);
        return builder.setSmallIcon(android.R.drawable.ic_btn_speak_now)
                .setContentTitle("J.A.R.V.I.S. is active")
                .setContentText(message)
                .setContentIntent(openPending)
                .setOngoing(true)
                .addAction(android.R.drawable.ic_media_pause, "Stop", pending)
                .build();
    }

    private void updateNotification(String message) {
        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (manager != null) manager.notify(NOTIFICATION_ID, buildNotification(message));
    }

    private void initialiseTts() {
        try {
            tts = new TextToSpeech(this, status -> {
                if (status == TextToSpeech.SUCCESS) {
                    ready = configureJarvisVoice();
                    if (ready) { tts.setSpeechRate(0.92f); tts.setPitch(0.84f); }
                    else updateNotification("No male English voice installed • select one in Android TTS settings");
                }
            });
        } catch (Throwable ignored) { ready = false; }
    }

    /** Wake replies use the same male-only JARVIS voice policy as the main app. */
    private boolean configureJarvisVoice() {
        try {
            Set<Voice> voices = tts.getVoices();
            Voice chosen = null;
            if (voices != null) {
                if (chosen == null) for (Voice voice : voices) {
                    Locale locale = voice.getLocale();
                    String name = voice.getName().toLowerCase(Locale.ROOT);
                    boolean male = name.contains("male") || name.contains("masculine");
                    boolean female = name.contains("female") || name.contains("feminine");
                    if ("en".equals(locale.getLanguage()) && male && !female) { chosen = voice; break; }
                }
            }
            if (chosen == null) return false;
            int language = tts.setLanguage(chosen.getLocale());
            if (language == TextToSpeech.LANG_MISSING_DATA || language == TextToSpeech.LANG_NOT_SUPPORTED) return false;
            return tts.setVoice(chosen) == TextToSpeech.SUCCESS;
        } catch (Exception ignored) { return false; }
    }

    private void beginRecognition() {
        if (stopping || listening) return;
        boolean localAvailable = Build.VERSION.SDK_INT >= 31
                && SpeechRecognizer.isOnDeviceRecognitionAvailable(this);
        if (!localAvailable && !SpeechRecognizer.isRecognitionAvailable(this)) {
            updateNotification("Speech recognition provider unavailable");
            handler.postDelayed(this::beginRecognition, 5000);
            return;
        }
        try {
            if (recognizer == null) {
                if (localAvailable) {
                    recognizer = SpeechRecognizer.createOnDeviceSpeechRecognizer(this);
                    onDeviceRecognition = true;
                } else {
                    recognizer = SpeechRecognizer.createSpeechRecognizer(this);
                    onDeviceRecognition = false;
                }
                recognizer.setRecognitionListener(new RecognitionListener() {
                    @Override public void onReadyForSpeech(Bundle params) {
                        listening = true;
                        updateNotification(onDeviceRecognition
                                ? "Offline on-device listening • four wake phrases enabled"
                                : "Listening with Android speech provider • offline support depends on provider");
                    }
                    @Override public void onBeginningOfSpeech() { }
                    @Override public void onRmsChanged(float rmsdB) { }
                    @Override public void onBufferReceived(byte[] buffer) { }
                    @Override public void onEndOfSpeech() { listening = false; }
                    @Override public void onError(int error) {
                        listening = false;
                        if (!stopping) handler.postDelayed(WakeListeningService.this::beginRecognition, 900);
                    }
                    @Override public void onResults(Bundle results) {
                        listening = false;
                        ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                        if (matches != null && !matches.isEmpty()) processRecognised(matches.get(0));
                        if (!stopping) handler.postDelayed(WakeListeningService.this::beginRecognition, 350);
                    }
                    @Override public void onPartialResults(Bundle partialResults) { }
                    @Override public void onEvent(int eventType, Bundle params) { }
                });
            }
            Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-GB");
            intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false);
            intent.putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true);
            recognizer.startListening(intent);
        } catch (Throwable error) {
            listening = false;
            updateNotification("Voice provider stopped; retrying");
            handler.postDelayed(this::beginRecognition, 2500);
        }
    }

    private void processRecognised(String phrase) {
        String trailing = WakePhraseMatcher.extractCommand(phrase);
        if (trailing == null) return;
        updateNotification("Wake phrase detected");
        speak("J.A.R.V.I.S online. At your service, Sir.");
        if (!trailing.isEmpty()) handler.postDelayed(() -> forwardCommandToChat(trailing), 700);
    }

    private void forwardCommandToChat(String spoken) {
        Intent command = new Intent(this, MainActivity.class);
        command.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        command.putExtra("JARVIS_COMMAND", spoken);
        try { startActivity(command); }
        catch (Exception error) { speak("I could not open the command interface, Sir."); }
    }

    private void launchApp(String requested) {
        PackageManager pm = getPackageManager();
        for (ApplicationInfo app : pm.getInstalledApplications(0)) {
            String label = pm.getApplicationLabel(app).toString();
            if (label.equalsIgnoreCase(requested) || label.toLowerCase(Locale.ROOT).contains(requested)) {
                Intent launch = pm.getLaunchIntentForPackage(app.packageName);
                if (launch != null) {
                    launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(launch);
                    speak("Opening " + label + ", Sir.");
                    return;
                }
            }
        }
        speak("I could not find " + requested + ", Sir.");
    }

    private void speak(String message) {
        if (memory != null) memory.add("assistant", message);
        if (ready && tts != null) tts.speak(message, TextToSpeech.QUEUE_FLUSH, null, "jarvis-wake");
    }

    @Override public void onDestroy() {
        stopping = true;
        handler.removeCallbacksAndMessages(null);
        if (recognizer != null) { try { recognizer.cancel(); recognizer.destroy(); } catch (Exception ignored) { } recognizer = null; }
        if (tts != null) { try { tts.stop(); tts.shutdown(); } catch (Exception ignored) { } tts = null; }
        if (memory != null) { memory.close(); memory = null; }
        stopForeground(true);
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) { return null; }
}
