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
import java.io.File;
import java.io.FileInputStream;
import java.io.OutputStream;
import java.io.IOException;

public final class MainActivity extends Activity {
    private static final int REQ_AUDIO = 41, REQ_CAMERA = 42, REQ_SPEECH = 43, REQ_SAVE_OFFICE = 44, REQ_OPEN_ATTACHMENT = 45;
    private String importedText = "";
    private File pendingOfficeFile;
    private TextToSpeech tts;
    private ConversationStore memory;
    private ArcReactorView reactorView;
    private SpeechRecognizer recognizer;
    private TextView status, transcript;
    private android.widget.EditText chatInput;
    private LinearLayout chatMessages;
    private ScrollView chatScroll;
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
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(12), dp(16), dp(10));
        root.setBackgroundColor(Color.rgb(2, 8, 18));

        TextView title = new TextView(this);
        title.setText("J.A.R.V.I.S.");
        title.setTextColor(cyan);
        title.setTextSize(25);
        title.setLetterSpacing(.22f);
        title.setGravity(Gravity.CENTER);
        root.addView(title, matchWrap());

        reactorView = new ArcReactorView(this);
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(dp(174), dp(174));
        rp.gravity = Gravity.CENTER_HORIZONTAL;
        rp.setMargins(0, dp(2), 0, dp(3));
        root.addView(reactorView, rp);

        status = new TextView(this);
        status.setTextColor(Color.rgb(120, 205, 235));
        status.setTextSize(11);
        status.setGravity(Gravity.CENTER);
        status.setPadding(dp(4), dp(5), dp(4), dp(8));
        root.addView(status, matchWrap());

        TextView chatHeading = new TextView(this);
        chatHeading.setText("ALFRED  •  CONVERSATION");
        chatHeading.setTextColor(cyan);
        chatHeading.setTextSize(12);
        chatHeading.setLetterSpacing(.12f);
        chatHeading.setPadding(dp(4), dp(5), dp(4), dp(7));
        root.addView(chatHeading, matchWrap());

        chatScroll = new ScrollView(this);
        chatScroll.setFillViewport(true);
        chatScroll.setBackgroundColor(Color.rgb(4, 17, 31));
        chatMessages = new LinearLayout(this);
        chatMessages.setOrientation(LinearLayout.VERTICAL);
        chatMessages.setPadding(dp(10), dp(10), dp(10), dp(10));
        chatScroll.addView(chatMessages);
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(-1, 0, 1f);
        root.addView(chatScroll, cp);

        transcript = new TextView(this);
        transcript.setTextColor(Color.WHITE);
        transcript.setTextSize(15);
        transcript.setGravity(Gravity.START);
        transcript.setText("ALFRED\nAt your service, Sir. What would you like me to do?");
        transcript.setPadding(dp(12), dp(12), dp(12), dp(12));
        android.graphics.drawable.GradientDrawable bubble = new android.graphics.drawable.GradientDrawable();
        bubble.setColor(Color.rgb(10, 37, 60));
        bubble.setCornerRadius(dp(14));
        transcript.setBackground(bubble);
        chatMessages.addView(transcript, matchWrap());

        LinearLayout composer = new LinearLayout(this);
        composer.setOrientation(LinearLayout.HORIZONTAL);
        composer.setGravity(Gravity.CENTER_VERTICAL);
        composer.setPadding(0, dp(8), 0, dp(4));
        chatInput = new android.widget.EditText(this);
        chatInput.setSingleLine(true);
        chatInput.setTextColor(Color.WHITE);
        chatInput.setHintTextColor(Color.rgb(120, 150, 170));
        chatInput.setHint("Message J.A.R.V.I.S...");
        chatInput.setTextSize(15);
        chatInput.setPadding(dp(13), dp(8), dp(13), dp(8));
        android.graphics.drawable.GradientDrawable inputBg = new android.graphics.drawable.GradientDrawable();
        inputBg.setColor(Color.rgb(8, 27, 45));
        inputBg.setCornerRadius(dp(24));
        inputBg.setStroke(dp(1), Color.rgb(42, 155, 210));
        chatInput.setBackground(inputBg);
        composer.addView(chatInput, new LinearLayout.LayoutParams(0, dp(50), 1f));

        Button mic = makeButton("MIC");
        mic.setTextSize(11);
        LinearLayout.LayoutParams mp = new LinearLayout.LayoutParams(dp(58), dp(48));
        mp.setMargins(dp(5), 0, dp(4), 0);
        composer.addView(mic, mp);
        mic.setOnClickListener(v -> startListening());

        Button send = makeButton("SEND");
        send.setTextSize(11);
        composer.addView(send, new LinearLayout.LayoutParams(dp(72), dp(48)));
        send.setOnClickListener(v -> sendChatMessage());
        chatInput.setOnEditorActionListener((v, actionId, event) -> { sendChatMessage(); return true; });
        root.addView(composer, matchWrap());

        Button tools = makeButton("TOOLS & DEVICE CONTROLS  ▾");
        tools.setTextSize(12);
        tools.setOnClickListener(v -> showTools());
        root.addView(tools, matchWrap());
        setContentView(root);
    }

    private void sendChatMessage() {
        if (chatInput == null) return;
        String message = chatInput.getText().toString().trim();
        if (message.isEmpty()) return;
        chatInput.setText("");
        addChatLine("YOU", message, true);
        handleCommand(message);
    }

    private void addChatLine(String speaker, String message, boolean user) {
        if (chatMessages == null) return;
        TextView line = new TextView(this);
        line.setText(speaker + "\n" + message);
        line.setTextColor(Color.WHITE);
        line.setTextSize(14);
        line.setPadding(dp(12), dp(10), dp(12), dp(10));
        android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
        bg.setColor(user ? Color.rgb(13, 54, 76) : Color.rgb(10, 37, 60));
        bg.setCornerRadius(dp(14));
        line.setBackground(bg);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(user ? (int)(getResources().getDisplayMetrics().widthPixels * .82f) : -1, -2);
        lp.gravity = user ? Gravity.END : Gravity.START;
        lp.setMargins(0, dp(4), 0, dp(6));
        chatMessages.addView(line, lp);
        if (chatScroll != null) chatScroll.post(() -> chatScroll.fullScroll(View.FOCUS_DOWN));
    }

    private void showTools() {
        String[] tools = {"Talk to J.A.R.V.I.S", "Start background wake listening", "Stop wake listening", "Discover installed apps", "Open J.A.R.V.I.S camera", "Search music", "Create Office document", "Import attachment", "Build study revision pack", "View private memory", "Clear local memory"};
        new android.app.AlertDialog.Builder(this).setTitle("J.A.R.V.I.S TOOLS")
            .setItems(tools, (dialog, which) -> {
                switch (which) {
                    case 0: startListening(); break;
                    case 1: startWakeListening(); break;
                    case 2: stopWakeListening(); break;
                    case 3: showApps(); break;
                    case 4: openCamera(); break;
                    case 5: playMusic("music"); break;
                    case 6: showOfficeComposer(); break;
                    case 7: importAttachment(); break;
                    case 8: showStudyComposer(); break;
                    case 9: showMemory(); break;
                    case 10: confirmClearMemory(); break;
                }
            }).setNegativeButton("CLOSE", null).show();
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
                        addChatLine("YOU • VOICE", command, true);
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
                addChatLine("J.A.R.V.I.S", "Opening the camera, Sir.", false);
                openCamera();
                break;
            case OPEN_APP:
                addChatLine("J.A.R.V.I.S", "Opening " + command.argument + ", Sir.", false);
                launchApp(command.argument);
                break;
            case PLAY_MUSIC:
                addChatLine("J.A.R.V.I.S", "Searching for " + (command.argument.isEmpty() ? "music" : command.argument) + ", Sir.", false);
                playMusic(command.argument.isEmpty() ? "music" : command.argument);
                break;
            case PAUSE_MEDIA:
            case RESUME_MEDIA:
            case NEXT_TRACK:
            case PREVIOUS_TRACK:
                String mediaReply = "I can open music apps and search for tracks, Sir, but direct controls of other apps are not available in this security-focused build.";
                addChatLine("ALFRED", mediaReply, false); speak(mediaReply);
                setStatus("DIRECT THIRD-PARTY MEDIA CONTROL NOT INCLUDED");
                break;
            default:
                String reply = PersonalityEngine.respond(raw, memory == null ? null : memory.recent(12));
                addChatLine("ALFRED", reply, false);
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
        addChatLine("SYSTEM", names.toString(), false);
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



    private void importAttachment() {
        Intent pick = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        pick.addCategory(Intent.CATEGORY_OPENABLE);
        pick.setType("*/*");
        pick.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"text/plain","text/csv","text/markdown",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.openxmlformats-officedocument.presentationml.presentation",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"});
        startActivityForResult(pick, REQ_OPEN_ATTACHMENT);
    }

    private void readAttachment(android.net.Uri uri) {
        String displayName = uri.getLastPathSegment();
        android.database.Cursor cursor = null;
        try {
            cursor = getContentResolver().query(uri, new String[]{android.provider.OpenableColumns.DISPLAY_NAME}, null, null, null);
            if (cursor != null && cursor.moveToFirst()) displayName = cursor.getString(0);
            try (java.io.InputStream in = getContentResolver().openInputStream(uri)) {
                importedText = OfficeReader.read(in, displayName);
            }
            addChatLine("SYSTEM • ATTACHMENT", displayName + "\n" +
                (importedText.length() > 2500 ? importedText.substring(0,2500) + "\n…" : importedText), false);
            setStatus("ATTACHMENT TEXT EXTRACTED • READY FOR STUDY PACK");
            speak("Attachment loaded, Sir.");
        } catch (Exception e) {
            importedText = "";
            setStatus("ATTACHMENT READ FAILED: " + e.getMessage());
        } finally { if (cursor != null) cursor.close(); }
    }

    private void showOfficeComposer() {
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(12), dp(8), dp(12), 0);
        android.widget.EditText titleInput = new android.widget.EditText(this);
        titleInput.setHint("Document title");
        android.widget.EditText bodyInput = new android.widget.EditText(this);
        bodyInput.setHint("Enter document content");
        bodyInput.setMinLines(5);
        bodyInput.setGravity(Gravity.TOP);
        form.addView(titleInput, matchWrap());
        form.addView(bodyInput, matchWrap());
        String[] formats = {"Word (.docx)", "PowerPoint (.pptx)", "Excel (.xlsx)", "PDF (.pdf)"};
        new android.app.AlertDialog.Builder(this).setTitle("OFFICE DOCUMENT")
            .setView(form).setItems(formats, (dialog, which) -> {
                String title = titleInput.getText().toString().trim();
                String body = bodyInput.getText().toString();
                if (title.isEmpty()) title = "JARVIS_Document";
                OfficeEngine.Format format = which == 0 ? OfficeEngine.Format.DOCX :
                    which == 1 ? OfficeEngine.Format.PPTX : which == 2 ? OfficeEngine.Format.XLSX : OfficeEngine.Format.PDF;
                try {
                    pendingOfficeFile = OfficeEngine.create(getCacheDir(), format, title, body);
                    String mime = which == 0 ? "application/vnd.openxmlformats-officedocument.wordprocessingml.document" :
                        which == 1 ? "application/vnd.openxmlformats-officedocument.presentationml.presentation" :
                        which == 2 ? "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" : "application/pdf";
                    Intent save = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                    save.addCategory(Intent.CATEGORY_OPENABLE);
                    save.setType(mime);
                    save.putExtra(Intent.EXTRA_TITLE, pendingOfficeFile.getName());
                    startActivityForResult(save, REQ_SAVE_OFFICE);
                    setStatus("DOCUMENT GENERATED • CHOOSE WHERE TO SAVE");
                } catch (Exception e) {
                    pendingOfficeFile = null;
                    setStatus("OFFICE EXPORT FAILED: " + e.getClass().getSimpleName());
                }
            }).setNegativeButton("CANCEL", null).show();
    }

    private void showStudyComposer() {
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(12), dp(8), dp(12), 0);
        android.widget.EditText topic = new android.widget.EditText(this);
        topic.setHint("Subject / topic");
        android.widget.EditText notes = new android.widget.EditText(this);
        notes.setHint("Paste your notes or source material here");
        notes.setMinLines(6);
        notes.setGravity(Gravity.TOP);
        if (!importedText.isEmpty()) notes.setText(importedText);
        form.addView(topic, matchWrap());
        form.addView(notes, matchWrap());
        new android.app.AlertDialog.Builder(this).setTitle("STUDY REVISION PACK")
            .setView(form).setPositiveButton("GENERATE PDF", (d, w) -> {
                String subject = topic.getText().toString().trim();
                String source = notes.getText().toString().trim();
                if (subject.isEmpty() || source.isEmpty()) {
                    setStatus("ENTER A TOPIC AND SOURCE NOTES FIRST"); return;
                }
                String pack = buildRevisionPack(subject, source);
                try {
                    pendingOfficeFile = OfficeEngine.create(getCacheDir(), OfficeEngine.Format.PDF,
                        subject + "_Revision_Pack", pack);
                    Intent save = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                    save.addCategory(Intent.CATEGORY_OPENABLE);
                    save.setType("application/pdf");
                    save.putExtra(Intent.EXTRA_TITLE, pendingOfficeFile.getName());
                    startActivityForResult(save, REQ_SAVE_OFFICE);
                    setStatus("REVISION PACK GENERATED • CHOOSE SAVE LOCATION");
                } catch (Exception e) { setStatus("STUDY PACK FAILED"); }
            }).setNegativeButton("CANCEL", null).show();
    }

    private String buildRevisionPack(String subject, String source) {
        StringBuilder out = new StringBuilder("REVISION NOTES: ").append(subject).append("\n\n");
        String[] lines = source.split("\\r?\\n");
        int count = 0;
        for (String line : lines) {
            String clean = line.trim();
            if (clean.isEmpty()) continue;
            out.append("KEY POINT ").append(++count).append(": ").append(clean).append("\n");
            if (count >= 30) break;
        }
        out.append("\nSELF-TEST\n");
        for (int i = 0; i < Math.min(count, 15); i++) {
            String clean = lines[i].trim();
            if (!clean.isEmpty()) out.append(i + 1).append(". Explain in your own words: ").append(clean).append("\n");
        }
        out.append("\nNOTE: This is an offline revision organiser based on the material you supplied; verify facts against your textbook or teacher.");
        return out.toString();
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_OPEN_ATTACHMENT) {
            if (resultCode == RESULT_OK && data != null && data.getData() != null) readAttachment(data.getData());
            return;
        }
        if (requestCode == REQ_SAVE_OFFICE) {
            if (resultCode == RESULT_OK && data != null && data.getData() != null && pendingOfficeFile != null) {
                try (FileInputStream in = new FileInputStream(pendingOfficeFile);
                     OutputStream out = getContentResolver().openOutputStream(data.getData(), "w")) {
                    if (out == null) throw new IOException("Destination unavailable");
                    byte[] buffer = new byte[8192]; int n;
                    while ((n = in.read(buffer)) != -1) out.write(buffer, 0, n);
                    out.flush();
                    setStatus("DOCUMENT SAVED SUCCESSFULLY");
                    speak("Your document has been saved, Sir.");
                } catch (Exception e) { setStatus("SAVE FAILED: " + e.getClass().getSimpleName()); }
            } else setStatus("DOCUMENT SAVE CANCELLED");
            pendingOfficeFile = null;
        }
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
        addChatLine("PRIVATE MEMORY", output.toString(), false);
        setStatus(entries.size() + " RECENT ENTRIES");
    }

    private void confirmClearMemory() {
        new android.app.AlertDialog.Builder(this)
                .setTitle("Clear JARVIS memory?")
                .setMessage("This permanently clears the local conversation history on this device.")
                .setNegativeButton("CANCEL", (dialog, which) -> dialog.dismiss())
                .setPositiveButton("CLEAR", (dialog, which) -> {
                    if (memory != null) memory.clear();
                    addChatLine("SYSTEM", "Local memory cleared.", false);
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