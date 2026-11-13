package com.hrishikesh.jarvis;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.json.JSONObject;
import org.vosk.Model;
import org.vosk.Recognizer;
import org.vosk.android.RecognitionListener;
import org.vosk.android.SpeechService;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class WakeWordService extends Service implements RecognitionListener {
    public static final String ACTION_WAKE = "com.hrishikesh.jarvis.WAKE_DETECTED";
    public static final String ACTION_COMMAND = "com.hrishikesh.jarvis.WAKE_COMMAND";
    public static final String EXTRA_COMMAND = "command";
    private static final String ACTION_STOP = "com.hrishikesh.jarvis.STOP_WAKE";
    private static final String CHANNEL = "jarvis_private_microphone";
    private static final int NOTIFICATION_ID = 7202;
    private static final String MODEL_URL = "https://alphacephei.com/vosk/models/vosk-model-small-en-us-0.15.zip";
    private volatile boolean stopped = false;
    private volatile boolean awaitingCommand = false;
    private volatile boolean preparing = false;
    // Partial Vosk hypotheses arrive before endpointing; remember a wake already
    // dispatched so the matching final result cannot dispatch it twice.
    private volatile boolean wakeHandledInUtterance = false;
    private final Handler commandTimeoutHandler = new Handler(Looper.getMainLooper());
    private final Runnable commandTimeout = () -> {
        awaitingCommand = false;
        updateNotification("Microphone ON · offline wake-word detection active");
    };
    private volatile HttpURLConnection modelConnection;
    private static volatile boolean active = false;
    private Model model;
    private SpeechService speechService;

    public static boolean isActive() { return active; }

    @Override public void onCreate() {
        super.onCreate();
        Log.i("WakeWordService", "onCreate: foreground microphone service process entered");
        active = true;
        createChannel();
        try {
            startForegroundCompat("Preparing offline wake-word engine. Audio stays on this phone.");
            Log.i("WakeWordService", "onCreate: foreground notification established");
        } catch (RuntimeException error) {
            Log.e("WakeWordService", "onCreate: unable to establish foreground notification", error);
            active = false;
            throw error;
        }
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        Log.i("WakeWordService", "onStartCommand: startId=" + startId + ", action=" + (intent == null ? "null" : intent.getAction()));
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            getSharedPreferences("jarvis_wake_word", MODE_PRIVATE).edit().putBoolean("enabled", false).apply();
            stopSelf();
            return START_NOT_STICKY;
        }
        synchronized (this) {
            if (!preparing && model == null && speechService == null) {
                preparing = true;
                stopped = false;
                Log.i("WakeWordService", "onStartCommand: launching offline model preparation thread");
                new Thread(this::prepareAndListen, "JarvisOfflineWake").start();
            }
        }
        // Do not silently restart microphone capture after Android kills the process.
        // The owner must explicitly turn the microphone back ON from JARVIS.
        return START_NOT_STICKY;
    }

    private void prepareAndListen() {
        try {
            Log.i("WakeWordService", "prepareAndListen: checking local Vosk model");
            File modelDir = new File(getFilesDir(), "vosk-model-small-en-us-0.15");
            if (!new File(modelDir, "am").isDirectory() || !new File(modelDir, "conf").isDirectory()) {
                Log.i("WakeWordService", "prepareAndListen: local model absent; beginning one-time download");
                updateNotification("Downloading offline speech model once. No microphone audio is uploaded.");
                File archive = new File(getCacheDir(), "vosk-model-small-en-us-0.15.zip");
                downloadModel(archive);
                Log.i("WakeWordService", "prepareAndListen: model archive downloaded; extracting");
                unpackModel(archive, modelDir);
                Log.i("WakeWordService", "prepareAndListen: model extraction complete");
                // Keep only the extracted model; remove the downloaded archive.
                //noinspection ResultOfMethodCallIgnored
                archive.delete();
            }
            if (stopped) {
                Log.i("WakeWordService", "prepareAndListen: cancelled before model load");
                return;
            }
            Log.i("WakeWordService", "prepareAndListen: loading native Vosk model");
            model = new Model(modelDir.getAbsolutePath());
            Log.i("WakeWordService", "prepareAndListen: native Vosk model loaded");
            Recognizer recognizer = new Recognizer(model, 16000.0f);
            Log.i("WakeWordService", "prepareAndListen: recognizer created");
            speechService = new SpeechService(recognizer, 16000.0f);
            Log.i("WakeWordService", "prepareAndListen: speech service created; starting microphone recognition");
            boolean listeningStarted = speechService.startListening(this);
            if (!listeningStarted) throw new IllegalStateException("Vosk refused to start microphone recognition");
            Log.i("WakeWordService", "prepareAndListen: microphone recognition listener thread launched");
            updateNotification("Microphone ON · offline wake-word detection active");
            Log.i("WakeWordService", "offline wake-word service ready");
        } catch (Exception | LinkageError error) {
            Log.e("WakeWordService", "Failed to prepare offline wake-word service", error);
            updateNotification("Wake-word setup failed. Open JARVIS and switch the microphone OFF.");
            getSharedPreferences("jarvis_wake_word", MODE_PRIVATE).edit().putBoolean("enabled", false).apply();
            stopSelf();
        } finally {
            preparing = false;
        }
    }

    private void downloadModel(File destination) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(MODEL_URL).openConnection();
        modelConnection = connection;
        connection.setConnectTimeout(20000);
        connection.setReadTimeout(60000);
        connection.setInstanceFollowRedirects(true);
        connection.setRequestProperty("User-Agent", "JARVIS-Offline-WakeWord/1.0");
        try {
            int code = connection.getResponseCode();
            if (code != 200) throw new IllegalStateException("Model download HTTP " + code);
            long declared = connection.getContentLengthLong();
            if (declared > 120L * 1024L * 1024L) throw new IllegalStateException("Model archive is too large");
            try (InputStream input = new BufferedInputStream(connection.getInputStream());
                 FileOutputStream output = new FileOutputStream(destination)) {
                byte[] buffer = new byte[16384];
                int read;
                long total = 0;
                while ((read = input.read(buffer)) != -1) {
                    if (stopped) throw new InterruptedException("Stopped by user");
                    total += read;
                    if (total > 120L * 1024L * 1024L) throw new IllegalStateException("Model archive exceeded size limit");
                    output.write(buffer, 0, read);
                }
                output.getFD().sync();
            }
        } finally {
            connection.disconnect();
            modelConnection = null;
        }
    }

    private void unpackModel(File archive, File destination) throws Exception {
        File staging = new File(getFilesDir(), "vosk-model-staging");
        deleteTree(staging);
        if (!staging.mkdirs() && !staging.isDirectory()) throw new IllegalStateException("Cannot create model folder");
        long expanded = 0;
        try (ZipInputStream zip = new ZipInputStream(new BufferedInputStream(new java.io.FileInputStream(archive)))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (stopped) throw new InterruptedException("Stopped by user");
                String name = entry.getName().replace('\\', '/');
                if (name.startsWith("/") || name.contains("../")) throw new IllegalStateException("Unsafe model archive path");
                String[] parts = name.split("/", 2);
                if (parts.length < 2 || parts[1].isEmpty()) continue;
                File output = new File(staging, parts[1]);
                String root = staging.getCanonicalPath() + File.separator;
                if (!output.getCanonicalPath().startsWith(root)) throw new IllegalStateException("Unsafe model path");
                if (entry.isDirectory()) {
                    output.mkdirs();
                } else {
                    File parent = output.getParentFile();
                    if (parent != null) parent.mkdirs();
                    try (FileOutputStream stream = new FileOutputStream(output)) {
                        byte[] buffer = new byte[16384];
                        int count;
                        while ((count = zip.read(buffer)) != -1) {
                            expanded += count;
                            if (expanded > 220L * 1024L * 1024L) throw new IllegalStateException("Expanded model is too large");
                            stream.write(buffer, 0, count);
                        }
                    }
                }
                zip.closeEntry();
            }
        }
        if (!new File(staging, "am").isDirectory() || !new File(staging, "conf").isDirectory())
            throw new IllegalStateException("The downloaded speech model is incomplete");
        deleteTree(destination);
        if (!staging.renameTo(destination)) throw new IllegalStateException("Could not install offline model");
    }

    private void deleteTree(File file) {
        if (file == null || !file.exists()) return;
        File[] children = file.listFiles();
        if (children != null) for (File child : children) deleteTree(child);
        //noinspection ResultOfMethodCallIgnored
        file.delete();
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel = new NotificationChannel(CHANNEL, "JARVIS private microphone", NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("Visible notification while local wake-word detection uses the microphone.");
            channel.setSound(null, null);
            getSystemService(NotificationManager.class).createNotificationChannel(channel);
        }
    }

    private Notification buildNotification(String message) {
        Intent stop = new Intent(this, WakeWordService.class).setAction(ACTION_STOP);
        PendingIntent stopPending = PendingIntent.getService(this, 7203, stop,
                PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0));
        Notification.Builder builder = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, CHANNEL) : new Notification.Builder(this);
        builder.setSmallIcon(android.R.drawable.ic_btn_speak_now)
                .setContentTitle("J.A.R.V.I.S. private microphone")
                .setContentText(message)
                .setOngoing(true)
                .addAction(android.R.drawable.ic_media_pause, "Turn microphone OFF", stopPending);
        return builder.build();
    }

    private void startForegroundCompat(String message) {
        Notification notification = buildNotification(message);
        if (Build.VERSION.SDK_INT >= 30)
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE);
        else startForeground(NOTIFICATION_ID, notification);
    }

    private void updateNotification(String message) {
        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        manager.notify(NOTIFICATION_ID, buildNotification(message));
    }

    private void broadcast(String action, String command) {
        Intent intent = new Intent(action).setPackage(getPackageName());
        if (command != null) intent.putExtra(EXTRA_COMMAND, command);
        sendBroadcast(intent);
    }

    private String transcript(String json) {
        try { return new JSONObject(json).optString("text", "").trim(); }
        catch (Exception ignored) { return ""; }
    }

    // Vosk emits each completed phrase through onResult when silence is detected.
    // onFinalResult is only emitted when the audio stream ends, so handling only
    // that callback would make a continuously-running wake word appear unresponsive.
    @Override public void onResult(String hypothesis) {
        handleRecognizedPhrase(hypothesis, false);
    }

    // Do not wait for Vosk's end-of-utterance silence timeout to wake JARVIS.
    // This is important when the user says the wake phrase and immediately
    // continues with a command.
    @Override public void onPartialResult(String hypothesis) {
        handleRecognizedPhrase(hypothesis, true);
    }

    @Override public void onFinalResult(String hypothesis) {
        handleRecognizedPhrase(hypothesis, false);
    }

    private void handleRecognizedPhrase(String hypothesis, boolean partial) {
        String text = transcript(hypothesis).toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9 ]", " ").replaceAll("\\s+", " ").trim();
        if (text.isEmpty()) {
            if (!partial) wakeHandledInUtterance = false;
            return;
        }

        // A partial hypothesis can grow over several callbacks. Once it has
        // dispatched a wake/command, ignore the corresponding final result.
        if (!partial && wakeHandledInUtterance) {
            wakeHandledInUtterance = false;
            return;
        }

        if (awaitingCommand) {
            commandTimeoutHandler.removeCallbacks(commandTimeout);
            awaitingCommand = false;
            broadcast(ACTION_COMMAND, text);
            if (partial) wakeHandledInUtterance = true;
            updateNotification("Microphone ON · offline wake-word detection active");
            return;
        }

        // Match "jarvis" as a complete spoken token, never as a substring
        // inside another word. Vosk is general speech recognition, not a
        // dedicated keyword model, so this reduces (but cannot eliminate)
        // false activations.
        java.util.regex.Matcher wakeMatcher = java.util.regex.Pattern
                .compile("\\bjarvis\\b").matcher(text);
        int wake = -1;
        while (wakeMatcher.find()) wake = wakeMatcher.start();
        if (wake >= 0) {
            String command = text.substring(wake + "jarvis".length()).trim();
            if (command.isEmpty()) {
                awaitingCommand = true;
                commandTimeoutHandler.removeCallbacks(commandTimeout);
                commandTimeoutHandler.postDelayed(commandTimeout, 8000);
                broadcast(ACTION_WAKE, null);
                if (partial) wakeHandledInUtterance = true;
                updateNotification("JARVIS awake · listening for your command");
            } else {
                broadcast(ACTION_COMMAND, command);
                if (partial) wakeHandledInUtterance = true;
                updateNotification("Microphone ON · offline wake-word detection active");
            }
        }
    }

    @Override public void onError(Exception exception) {
        Log.e("WakeWordService", "Offline speech recognition error", exception);
        updateNotification("Offline microphone error. Open JARVIS to turn it OFF.");
        getSharedPreferences("jarvis_wake_word", MODE_PRIVATE).edit().putBoolean("enabled", false).apply();
        stopSelf();
    }

    @Override public void onTimeout() { }

    @Override public IBinder onBind(Intent intent) { return null; }

    @Override public void onDestroy() {
        Log.i("WakeWordService", "onDestroy: stopping offline wake-word service");
        commandTimeoutHandler.removeCallbacks(commandTimeout);
        awaitingCommand = false;
        stopped = true;
        active = false;
        HttpURLConnection connection = modelConnection;
        if (connection != null) connection.disconnect();
        if (speechService != null) {
            speechService.stop();
            speechService.shutdown();
            speechService = null;
        }
        if (model != null) {
            model.close();
            model = null;
        }
        getSharedPreferences("jarvis_wake_word", MODE_PRIVATE).edit().putBoolean("enabled", false).apply();
        if (Build.VERSION.SDK_INT >= 24) stopForeground(STOP_FOREGROUND_REMOVE);
        else stopForeground(true);
        super.onDestroy();
        Log.i("WakeWordService", "onDestroy: cleanup complete");
    }
}
