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
    private boolean ready;
    private boolean listening;
    private boolean stopping;

    @Override public void onCreate() {
        super.onCreate();
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
        Notification.Builder builder = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, CHANNEL) : new Notification.Builder(this);
        return builder.setSmallIcon(android.R.drawable.ic_btn_speak_now)
                .setContentTitle("J.A.R.V.I.S. is active")
                .setContentText(message)
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
                    ready = true;
                    try {
                        tts.setLanguage(Locale.UK);
                        Voice algieba = null;
                        Set<Voice> voices = tts.getVoices();
                        if (voices != null) for (Voice voice : voices) {
                            if (voice.getName().toLowerCase(Locale.ROOT).contains("algieba")) {
                                algieba = voice; break;
                            }
                        }
                        if (algieba != null) tts.setVoice(algieba);
                    } catch (Exception ignored) { }
                }
            });
        } catch (Throwable ignored) { ready = false; }
    }

    private void beginRecognition() {
        if (stopping || listening) return;
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            updateNotification("Speech recognition provider unavailable");
            handler.postDelayed(this::beginRecognition, 5000);
            return;
        }
        try {
            if (recognizer == null) {
                recognizer = SpeechRecognizer.createSpeechRecognizer(this);
                recognizer.setRecognitionListener(new RecognitionListener() {
                    @Override public void onReadyForSpeech(Bundle params) {
                        listening = true; updateNotification("Listening for all four JARVIS wake phrases");
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
        speak("At your service, Sir.");
        if (!trailing.isEmpty()) handler.postDelayed(() -> executeCommand(trailing), 700);
    }

    private void executeCommand(String spoken) {
        CommandParser.Command command = CommandParser.parse(spoken);
        switch (command.action) {
            case OPEN_APP: launchApp(command.argument); break;
            case OPEN_CAMERA:
                Intent camera = new Intent(this, CameraActivity.class);
                camera.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(camera);
                break;
            case PLAY_MUSIC:
                Intent music = new Intent(Intent.ACTION_VIEW,
                        android.net.Uri.parse("https://www.youtube.com/results?search_query="
                                + android.net.Uri.encode(command.argument.isEmpty() ? "music" : command.argument)));
                music.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(music);
                break;
            case PAUSE_MEDIA:
            case RESUME_MEDIA:
            case NEXT_TRACK:
            case PREVIOUS_TRACK:
                if (MediaControl.execute(this, command.action)) speak("As you wish, Sir.");
                else speak("Media controls are unavailable. Please enable JARVIS notification access, Sir.");
                break;
            default: speak("I am listening, Sir. Please give me a command."); break;
        }
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
        if (ready && tts != null) tts.speak(message, TextToSpeech.QUEUE_FLUSH, null, "jarvis-wake");
    }

    @Override public void onDestroy() {
        stopping = true;
        handler.removeCallbacksAndMessages(null);
        if (recognizer != null) { try { recognizer.cancel(); recognizer.destroy(); } catch (Exception ignored) { } recognizer = null; }
        if (tts != null) { try { tts.stop(); tts.shutdown(); } catch (Exception ignored) { } tts = null; }
        stopForeground(true);
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) { return null; }
}
