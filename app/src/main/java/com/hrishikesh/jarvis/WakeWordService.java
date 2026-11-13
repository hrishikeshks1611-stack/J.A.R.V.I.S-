package com.hrishikesh.jarvis;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.media.audiofx.AcousticEchoCanceler;
import android.media.audiofx.NoiseSuppressor;
import android.os.Build;
import android.os.IBinder;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.json.JSONObject;
import org.vosk.Model;
import org.vosk.Recognizer;
import org.vosk.android.RecognitionListener;

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
    public static final String ACTION_PARTIAL = "com.hrishikesh.jarvis.WAKE_PARTIAL";
    public static final String ACTION_READY = "com.hrishikesh.jarvis.MICROPHONE_READY";
    public static final String ACTION_ERROR = "com.hrishikesh.jarvis.MICROPHONE_ERROR";
    public static final String EXTRA_ERROR = "error";
    public static final String EXTRA_COMMAND = "command";
    public static final String ACTION_DICTATION = "com.hrishikesh.jarvis.START_DICTATION";
    private static final String ACTION_STOP = "com.hrishikesh.jarvis.STOP_WAKE";
    private static final String CHANNEL = "jarvis_private_microphone";
    private static final int NOTIFICATION_ID = 7202;
    private static final String MODEL_URL = "https://alphacephei.com/vosk/models/vosk-model-small-en-in-0.4.zip";
    private volatile boolean stopped = false;
    private volatile boolean awaitingCommand = false;
    private volatile boolean dictationMode = false;
    private volatile boolean wakeDetectedInUtterance = false;
    private volatile boolean preparing = false;
    private volatile boolean readyBroadcasted = false;
    private final Handler commandTimeoutHandler = new Handler(Looper.getMainLooper());
    private final Handler dictationTimeoutHandler = new Handler(Looper.getMainLooper());
    private final StringBuilder dictationTranscript = new StringBuilder();
    private final Object dictationLock = new Object();
    private static final long DICTATION_SILENCE_MS = 1900L;
    private final Runnable dictationFinalize = () -> finishDictation("pause");
    private final Runnable dictationTimeout = () -> {
        if (!dictationMode) return;
        boolean wasReady = readyBroadcasted;
        if (!wasReady) {
            dictationMode = false;
            String detail = "Microphone startup timed out before audio capture became active.";
            updateNotification("Microphone startup timed out");
            broadcast(ACTION_ERROR, detail);
            if (!getSharedPreferences("jarvis_wake_word", MODE_PRIVATE).getBoolean("enabled", false)) stopSelf();
        } else {
            finishDictation("timeout");
        }
    };
    private final Runnable commandTimeout = () -> {
        awaitingCommand = false;
        updateNotification("Microphone ON · offline wake-word detection active");
    };
    private volatile HttpURLConnection modelConnection;
    private static volatile boolean active = false;
    private Model model;
    private Recognizer recognizer;
    private volatile AudioRecord audioRecord;
    private volatile boolean recording = false;
    private Thread audioThread;
    private NoiseSuppressor noiseSuppressor;
    private AcousticEchoCanceler echoCanceler;

    public static boolean isActive() { return active; }

    @Override public void onCreate() {
        super.onCreate();
        Log.i("WakeWordService", "onCreate: foreground microphone service process entered");
        active = false;
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
        if (intent != null && ACTION_DICTATION.equals(intent.getAction())) {
            dictationMode = true;
            dictationTimeoutHandler.removeCallbacks(dictationTimeout);
            dictationTimeoutHandler.postDelayed(dictationTimeout, 60000);
            if (audioRecord != null && recording) {
                dictationTimeoutHandler.removeCallbacks(dictationTimeout);
                dictationTimeoutHandler.postDelayed(dictationTimeout, 60000);
                updateNotification("JARVIS voice input · speak now · audio stays on this phone");
                if (readyBroadcasted) broadcast(ACTION_READY, null);
            }
        }
        synchronized (this) {
            if (!preparing && model == null && audioRecord == null) {
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
            File modelDir = new File(getFilesDir(), "vosk-model-small-en-in-0.4");
            if (!new File(modelDir, "am").isDirectory() || !new File(modelDir, "conf").isDirectory()) {
                Log.i("WakeWordService", "prepareAndListen: local model absent; beginning one-time download");
                updateNotification("Downloading offline speech model once. No microphone audio is uploaded.");
                File archive = new File(getCacheDir(), "vosk-model-small-en-in-0.4.zip");
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
            recognizer = new Recognizer(model, 16000.0f);
            Log.i("WakeWordService", "prepareAndListen: recognizer created");
            Log.i("WakeWordService", "prepareAndListen: opening 16 kHz mono voice microphone");
            startAudioCapture();
            Log.i("WakeWordService", "prepareAndListen: microphone capture thread launched");
            updateNotification("Microphone initialised · waiting for first audio frame");
            Log.i("WakeWordService", "Vosk is loaded and AudioRecord started; waiting for first PCM frame before reporting ready");
        } catch (Exception | LinkageError error) {
            Log.e("WakeWordService", "Failed to prepare offline wake-word service", error);
            active = false;
            recording = false;
            String detail = error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
            updateNotification("Microphone setup failed · " + detail);
            broadcast(ACTION_ERROR, detail);
            getSharedPreferences("jarvis_wake_word", MODE_PRIVATE).edit().putBoolean("enabled", false).apply();
            stopSelf();
        } finally {
            preparing = false;
        }
    }


    /**
     * Captures mono 16 kHz PCM using Android's voice-recognition input route.
     * Where supported, Android's hardware noise suppression and echo cancellation
     * are enabled for this microphone audio session.
     */
    private void startAudioCapture() throws Exception {
        final int sampleRate = 16000;
        final int channelConfig = AudioFormat.CHANNEL_IN_MONO;
        final int encoding = AudioFormat.ENCODING_PCM_16BIT;
        int minimum = AudioRecord.getMinBufferSize(sampleRate, channelConfig, encoding);
        if (minimum <= 0) throw new IllegalStateException("Invalid microphone buffer size: " + minimum);

        int bufferSize = Math.max(minimum * 2, 6400);
        AudioRecord recorder = new AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION,
                sampleRate, channelConfig, encoding, bufferSize);
        if (recorder.getState() != AudioRecord.STATE_INITIALIZED) {
            recorder.release();
            throw new IllegalStateException("Android could not initialize the voice microphone");
        }
        audioRecord = recorder;

        int sessionId = recorder.getAudioSessionId();
        if (NoiseSuppressor.isAvailable()) {
            try {
                noiseSuppressor = NoiseSuppressor.create(sessionId);
                if (noiseSuppressor != null) {
                    noiseSuppressor.setEnabled(true);
                    Log.i("WakeWordService", "Hardware noise suppression enabled");
                }
            } catch (RuntimeException error) {
                Log.w("WakeWordService", "Noise suppression could not be enabled", error);
            }
        } else {
            Log.i("WakeWordService", "Hardware noise suppression unsupported on this device");
        }
        if (AcousticEchoCanceler.isAvailable()) {
            try {
                echoCanceler = AcousticEchoCanceler.create(sessionId);
                if (echoCanceler != null) {
                    echoCanceler.setEnabled(true);
                    Log.i("WakeWordService", "Hardware acoustic echo cancellation enabled");
                }
            } catch (RuntimeException error) {
                Log.w("WakeWordService", "Echo cancellation could not be enabled", error);
            }
        }

        try {
            recorder.startRecording();
            if (recorder.getRecordingState() != AudioRecord.RECORDSTATE_RECORDING) {
                throw new IllegalStateException("Android did not start microphone recording");
            }
        } catch (Exception error) {
            releaseAudioEffects();
            recorder.release();
            audioRecord = null;
            throw error;
        }

        recording = true;
        audioThread = new Thread(() -> {
            byte[] pcm = new byte[3200]; // 100 ms at 16 kHz, mono, PCM16.
            Log.i("WakeWordService", "Audio capture active: VOICE_RECOGNITION / 16000 Hz / mono / PCM16");
            try {
                while (recording && !stopped) {
                    int count = recorder.read(pcm, 0, pcm.length);
                    if (count > 0) {
                        if (!readyBroadcasted) {
                            readyBroadcasted = true;
                            active = true;
                            if (dictationMode) {
                                dictationTimeoutHandler.removeCallbacks(dictationTimeout);
                                dictationTimeoutHandler.postDelayed(dictationTimeout, 20000);
                                updateNotification("JARVIS voice input · microphone active · speak now");
                            } else {
                                updateNotification("Microphone ON · offline wake-word detection active");
                            }
                            broadcast(ACTION_READY, null);
                            Log.i("WakeWordService", "First PCM frame received; microphone readiness confirmed");
                        }
                        Recognizer activeRecognizer = recognizer;
                        if (activeRecognizer == null) break;
                        if (activeRecognizer.acceptWaveForm(pcm, count)) onResult(activeRecognizer.getResult());
                        else onPartialResult(activeRecognizer.getPartialResult());
                    } else if (count < 0) {
                        throw new IllegalStateException("Microphone read failed with code " + count);
                    }
                }
            } catch (Exception | LinkageError error) {
                if (recording && !stopped) {
                    Log.e("WakeWordService", "Microphone capture failed", error);
                    active = false;
                    recording = false;
                    onError(error instanceof Exception ? (Exception) error
                            : new IllegalStateException("Native microphone processing failed", error));
                }
            } finally {
                Log.i("WakeWordService", "Audio capture thread stopped");
            }
        }, "JarvisAudioCapture");
        audioThread.start();
    }

    private void releaseAudioEffects() {
        if (noiseSuppressor != null) {
            try { noiseSuppressor.setEnabled(false); noiseSuppressor.release(); } catch (Exception ignored) { }
            noiseSuppressor = null;
        }
        if (echoCanceler != null) {
            try { echoCanceler.setEnabled(false); echoCanceler.release(); } catch (Exception ignored) { }
            echoCanceler = null;
        }
    }

    private void downloadModel(File destination) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(MODEL_URL).openConnection();
        modelConnection = connection;
        connection.setConnectTimeout(20000);
        connection.setReadTimeout(60000);
        connection.setInstanceFollowRedirects(true);
        connection.setRequestProperty("User-Agent", "JARVIS-Offline-Voice/1.1");
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

        // Replace the model transactionally. Never delete a previously working
        // model until the new extraction has been validated and moved into place.
        File backup = new File(getFilesDir(), "vosk-model-backup");
        deleteTree(backup);
        boolean hadPreviousModel = destination.exists();
        if (hadPreviousModel && !destination.renameTo(backup))
            throw new IllegalStateException("Could not preserve the existing offline model");
        if (!staging.renameTo(destination)) {
            if (hadPreviousModel && !backup.renameTo(destination))
                throw new IllegalStateException("Model install failed and previous model restore also failed");
            throw new IllegalStateException("Could not install offline model; previous model was restored");
        }
        deleteTree(backup);
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

    private String partialTranscript(String json) {
        try { return new JSONObject(json).optString("partial", "").trim(); }
        catch (Exception ignored) { return ""; }
    }

    // Vosk emits each completed phrase through onResult when silence is detected.
    // onFinalResult is only emitted when the audio stream ends, so handling only
    // that callback would make a continuously-running wake word appear unresponsive.
    @Override public void onResult(String hypothesis) {
        handleRecognizedPhrase(hypothesis);
    }

    @Override public void onPartialResult(String hypothesis) {
        String partial = partialTranscript(hypothesis);
        if (partial.isEmpty()) return;
        if (dictationMode) {
            String combined;
            synchronized (dictationLock) {
                combined = dictationTranscript.length() == 0
                        ? partial : dictationTranscript.toString() + " " + partial;
            }
            broadcast(ACTION_PARTIAL, combined.trim());
            return;
        }
        String normalized = partial.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9 ]", " ").replaceAll("\\s+", " ").trim();
        boolean wakeNamePresent = containsWakeWord(normalized);
        if (!awaitingCommand && !wakeDetectedInUtterance && wakeNamePresent) {
            wakeDetectedInUtterance = true;
            broadcast(ACTION_WAKE, null);
            updateNotification("JARVIS detected · listening for your command");
            Log.i("WakeWordService", "Wake phrase detected in partial speech");
        }
    }

    @Override public void onFinalResult(String hypothesis) {
        handleRecognizedPhrase(hypothesis);
    }

    /** Returns true only when JARVIS appears as a complete spoken word. */
    public static boolean containsWakeWord(String recognizedText) {
        return WakePhraseParser.containsWakeWord(recognizedText);
    }

    /**
     * Extracts the words spoken after the wake name. A null result means no
     * wake word was present; an empty result means the wake phrase was spoken
     * by itself and JARVIS should wait for the follow-up command.
     */
    public static String extractWakeCommand(String recognizedText) {
        return WakePhraseParser.extractWakeCommand(recognizedText);
    }

    private void handleRecognizedPhrase(String hypothesis) {
        String text = transcript(hypothesis).toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9 ]", " ").replaceAll("\\s+", " ").trim();
        if (text.isEmpty()) return;

        if (dictationMode) {
            synchronized (dictationLock) {
                if (dictationTranscript.length() > 0) dictationTranscript.append(' ');
                dictationTranscript.append(text);
            }
            // Vosk can split one spoken sentence into several final segments when
            // it hears a natural pause. Keep collecting them and submit only after
            // a deliberate silence window, rather than ending on the first segment.
            dictationTimeoutHandler.removeCallbacks(dictationFinalize);
            dictationTimeoutHandler.postDelayed(dictationFinalize, DICTATION_SILENCE_MS);
            String combined;
            synchronized (dictationLock) { combined = dictationTranscript.toString(); }
            broadcast(ACTION_PARTIAL, combined);
            updateNotification("JARVIS voice input · keep speaking or pause to send");
            return;
        }

        if (wakeDetectedInUtterance) {
            wakeDetectedInUtterance = false;
            String partialWakeCommand = extractWakeCommand(text);
            if (partialWakeCommand == null) {
                Log.w("WakeWordService", "Partial wake hypothesis was not present in final transcript; ignoring it");
            } else if (!partialWakeCommand.isEmpty()) {
                broadcast(ACTION_COMMAND, partialWakeCommand);
                updateNotification("Microphone ON · offline wake-word detection active");
            } else {
                awaitingCommand = true;
                commandTimeoutHandler.removeCallbacks(commandTimeout);
                commandTimeoutHandler.postDelayed(commandTimeout, 8000);
                updateNotification("JARVIS awake · listening for your command");
            }
            return;
        }

        if (awaitingCommand) {
            commandTimeoutHandler.removeCallbacks(commandTimeout);
            awaitingCommand = false;
            broadcast(ACTION_COMMAND, text);
            updateNotification("Microphone ON · offline wake-word detection active");
            return;
        }

        // Match JARVIS as a complete token, including after "Hey",
        // "Hello", or "Wake up". Keep any words after the name as a command.
        String command = extractWakeCommand(text);
        if (command != null) {
            if (command.isEmpty()) {
                awaitingCommand = true;
                commandTimeoutHandler.removeCallbacks(commandTimeout);
                commandTimeoutHandler.postDelayed(commandTimeout, 8000);
                broadcast(ACTION_WAKE, null);
                updateNotification("JARVIS awake · listening for your command");
            } else {
                broadcast(ACTION_COMMAND, command);
                updateNotification("Microphone ON · offline wake-word detection active");
            }
        }
    }

    private void finishDictation(String reason) {
        if (!dictationMode) return;
        dictationTimeoutHandler.removeCallbacks(dictationTimeout);
        dictationTimeoutHandler.removeCallbacks(dictationFinalize);
        String complete;
        synchronized (dictationLock) {
            complete = dictationTranscript.toString().trim();
            dictationTranscript.setLength(0);
        }
        dictationMode = false;
        if (!complete.isEmpty()) {
            broadcast(ACTION_COMMAND, complete);
            Log.i("WakeWordService", "Dictation submitted after " + reason + "; characters=" + complete.length());
        } else {
            Log.i("WakeWordService", "Dictation ended after " + reason + " without recognised speech");
        }
        boolean wakeWordEnabled = getSharedPreferences("jarvis_wake_word", MODE_PRIVATE)
                .getBoolean("enabled", false);
        if (wakeWordEnabled) {
            updateNotification("Microphone ON · offline wake-word detection active");
        } else {
            updateNotification(complete.isEmpty()
                    ? "No speech recognised · microphone stopped"
                    : "Voice input complete · microphone stopped");
            stopSelf();
        }
    }

    @Override public void onError(Exception exception) {
        Log.e("WakeWordService", "Offline speech recognition error", exception);
        active = false;
        recording = false;
        String detail = exception == null || exception.getMessage() == null ? "Offline speech recognition failed." : exception.getMessage();
        broadcast(ACTION_ERROR, detail);
        updateNotification("Offline microphone error · " + detail);
        getSharedPreferences("jarvis_wake_word", MODE_PRIVATE).edit().putBoolean("enabled", false).apply();
        stopSelf();
    }

    @Override public void onTimeout() { }

    @Override public IBinder onBind(Intent intent) { return null; }

    @Override public void onDestroy() {
        Log.i("WakeWordService", "onDestroy: stopping offline wake-word service");
        commandTimeoutHandler.removeCallbacks(commandTimeout);
        dictationTimeoutHandler.removeCallbacks(dictationTimeout);
        dictationTimeoutHandler.removeCallbacks(dictationFinalize);
        synchronized (dictationLock) { dictationTranscript.setLength(0); }
        dictationMode = false;
        awaitingCommand = false;
        wakeDetectedInUtterance = false;
        stopped = true;
        active = false;
        HttpURLConnection connection = modelConnection;
        if (connection != null) connection.disconnect();
        recording = false;
        readyBroadcasted = false;
        AudioRecord recorder = audioRecord;
        if (recorder != null) {
            try { if (recorder.getRecordingState() == AudioRecord.RECORDSTATE_RECORDING) recorder.stop(); }
            catch (Exception error) { Log.w("WakeWordService", "Error stopping microphone capture", error); }
        }
        Thread capture = audioThread;
        if (capture != null && capture != Thread.currentThread()) {
            try { capture.join(1500); } catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
        }
        releaseAudioEffects();
        if (recorder != null) {
            try { recorder.release(); } catch (Exception ignored) { }
            audioRecord = null;
        }
        audioThread = null;
        if (recognizer != null) {
            recognizer.close();
            recognizer = null;
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
