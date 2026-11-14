package com.hrishikesh.jarvis;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageInstaller;
import java.io.File;
import java.io.FileOutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import android.content.BroadcastReceiver;
import android.content.IntentFilter;
import android.Manifest;
import android.content.pm.PackageManager;
import android.content.ContentValues;
import android.content.ClipData;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.provider.MediaStore;
import android.provider.AlarmClock;
import android.provider.CalendarContract;
import android.provider.ContactsContract;
import android.util.Base64;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Build;
import android.os.Environment;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;
import android.speech.tts.TextToSpeech;
import android.speech.tts.Voice;
import android.speech.tts.UtteranceProgressListener;
import android.webkit.JavascriptInterface;
import android.webkit.GeolocationPermissions;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public class MainActivity extends Activity {
    private static final int REQUEST_AUDIO_INPUT = 4102;
    private static final int REQUEST_FILE = 4103;
    private static final int REQUEST_CAMERA = 4104;
    private static final int REQUEST_LOCATION = 4105;
    private static final int REQUEST_CONTACTS = 4106;
    private static final int REQUEST_REMINDER_NOTIFICATION = 4108;
    private long pendingReminderAt = 0L;
    private String pendingReminderTitle = null;
    private String pendingContactCall;
    private JarvisBridge jarvisBridge;
    private GeolocationPermissions.Callback pendingGeoCallback;
    private String pendingGeoOrigin;
    private Uri pendingCameraUri;
    private WebView webView;
    private TextToSpeech textToSpeech;
    private boolean ttsReady = false;
    private boolean voiceChosen = false;
    private SharedPreferences voicePrefs;
    private String pendingSpeech = null;
    private final Map<String, String[]> appPackages = new HashMap<>();
    private BroadcastReceiver wakeWordReceiver;

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Preserve the owner's explicit wake-word choice across Activity recreation.
        // A previously enabled foreground service is resumed only while this Activity is foregrounded.
        SharedPreferences wakePrefs = getSharedPreferences("jarvis_wake_word", MODE_PRIVATE);
        if (wakePrefs.getBoolean("enabled", false)
                && checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            Intent wakeIntent = new Intent(this, WakeWordService.class);
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(wakeIntent);
            else startService(wakeIntent);
        }
        getWindow().setStatusBarColor(android.graphics.Color.rgb(5, 11, 24));
        getWindow().setNavigationBarColor(android.graphics.Color.rgb(5, 11, 24));
        registerApps();
        initializeOfflineSpeech();
        wakeWordReceiver = new BroadcastReceiver() {
            @Override public void onReceive(android.content.Context context, Intent intent) {
                String action = intent == null ? null : intent.getAction();
                if (WakeWordService.ACTION_WAKE.equals(action)) {
                    if (webView != null) webView.evaluateJavascript("window.jarvisNative && window.jarvisNative.onWakeWordDetected && window.jarvisNative.onWakeWordDetected()", null);
                } else if (WakeWordService.ACTION_READY.equals(action)) {
                    if (webView != null) webView.evaluateJavascript("window.jarvisNative && window.jarvisNative.onMicrophoneReady && window.jarvisNative.onMicrophoneReady()", null);
                    Toast.makeText(MainActivity.this, "Microphone ready. Speak your command, Sir.", Toast.LENGTH_SHORT).show();
                } else if (WakeWordService.ACTION_ERROR.equals(action)) {
                    String message = intent.getStringExtra(WakeWordService.EXTRA_ERROR);
                    String safe = message == null ? "The microphone could not start." : message;
                    if (webView != null) webView.evaluateJavascript("window.jarvisNative && window.jarvisNative.onMicrophoneError && window.jarvisNative.onMicrophoneError(" + JSONObject.quote(safe) + ")", null);
                    Toast.makeText(MainActivity.this, "JARVIS microphone error: " + safe, Toast.LENGTH_LONG).show();
                } else if (WakeWordService.ACTION_PARTIAL.equals(action)) {
                    String partial = intent.getStringExtra(WakeWordService.EXTRA_COMMAND);
                    if (partial != null && webView != null) webView.evaluateJavascript("window.jarvisNative && window.jarvisNative.onSpeechPartial && window.jarvisNative.onSpeechPartial(" + JSONObject.quote(partial) + ")", null);
                } else if (WakeWordService.ACTION_COMMAND.equals(action)) {
                    String command = intent.getStringExtra(WakeWordService.EXTRA_COMMAND);
                    if (command != null && webView != null) webView.evaluateJavascript("window.jarvisNative && window.jarvisNative.onSpeechResult(" + JSONObject.quote(command) + ")", null);
                }
            }
        };
        IntentFilter wakeFilter = new IntentFilter();
        wakeFilter.addAction(WakeWordService.ACTION_WAKE);
        wakeFilter.addAction(WakeWordService.ACTION_READY);
        wakeFilter.addAction(WakeWordService.ACTION_ERROR);
        wakeFilter.addAction(WakeWordService.ACTION_COMMAND);
        wakeFilter.addAction(WakeWordService.ACTION_PARTIAL);
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(wakeWordReceiver, wakeFilter, android.content.Context.RECEIVER_NOT_EXPORTED);
        else registerReceiver(wakeWordReceiver, wakeFilter);
        webView = new WebView(this);
        webView.setFocusable(true);
        webView.setFocusableInTouchMode(true);
        webView.setBackgroundColor(android.graphics.Color.rgb(5, 11, 24));
        webView.getSettings().setJavaScriptEnabled(true);
        webView.getSettings().setDomStorageEnabled(true);
        webView.getSettings().setMediaPlaybackRequiresUserGesture(false);
        webView.getSettings().setAllowFileAccess(false);
        webView.getSettings().setAllowContentAccess(false);
        webView.getSettings().setGeolocationEnabled(true);
        jarvisBridge = new JarvisBridge();
        webView.addJavascriptInterface(jarvisBridge, "AndroidJarvis");
        webView.setWebChromeClient(new WebChromeClient() {
            @Override public void onGeolocationPermissionsShowPrompt(String origin, GeolocationPermissions.Callback callback) {
                if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                    callback.invoke(origin, true, false);
                    return;
                }
                pendingGeoOrigin = origin;
                pendingGeoCallback = callback;
                requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, REQUEST_LOCATION);
            }
        });
        webView.setWebViewClient(new WebViewClient() {
            @Override public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                if (BuildConfig.JARVIS_TV) {
                    String js = "(function(){document.documentElement.classList.add('jarvis-tv-mode');" +
                        "var s=document.createElement('style');s.textContent=" +
                        "'body{font-size:19px!important}.shell{max-width:1180px!important;padding:24px 34px!important}" +
                        ".core-wrap{height:340px!important}.core{transform:scale(1.12)}" +
                        ".controls{gap:14px!important;margin:24px 0!important}" +
                        ".control,select{font-size:16px!important;padding:14px 20px!important;min-height:54px}" +
                        ".composer{padding:10px!important;gap:10px!important}.composer input{font-size:19px!important}" +
                        ".mic,.attach-btn,.send{min-width:58px!important;min-height:54px!important;font-size:20px!important}" +
                        "button:focus,select:focus,input:focus{outline:3px solid #62d9ff!important;outline-offset:4px!important;box-shadow:0 0 20px #218bff99!important}" +
                        ".messages{max-height:34vh!important}.msg{font-size:17px!important;padding:14px 18px!important}';" +
                        "document.head.appendChild(s);document.querySelectorAll('button,select,input').forEach(function(e){e.tabIndex=0});" +
                        "var first=document.getElementById('mic');if(first)first.focus({preventScroll:true});})();";
                    view.evaluateJavascript(js, null);
                }
            }
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                String host = uri.getHost() == null ? "" : uri.getHost();
                if ("hrishikeshks1611-stack.github.io".equals(host)) return false;
                try { startActivity(new Intent(Intent.ACTION_VIEW, uri)); }
                catch (ActivityNotFoundException ignored) { }
                return true;
            }
        });
        setContentView(webView);
        webView.requestFocus();
        if (BuildConfig.JARVIS_TV) applyTvImmersiveMode();
        loadBundledInterface();
    }

    private void scheduleReminderNotification(long triggerAtMillis, String title) {
        try {
            ReminderScheduler.schedule(this, triggerAtMillis, title);
            runOnUiThread(() -> Toast.makeText(this, "Reminder scheduled, Sir. It will be restored after a restart.", Toast.LENGTH_SHORT).show());
        } catch (Exception error) {
            runOnUiThread(() -> Toast.makeText(this, "I could not schedule that reminder. Please try again, Sir.", Toast.LENGTH_LONG).show());
        }
    }

    private void loadBundledInterface() {
        try (InputStream input = getAssets().open("index.html");
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int count;
            while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
            String html = output.toString("UTF-8");
            // Keep the trusted GitHub Pages origin for existing read-only API calls,
            // but render the exact UI shipped in this APK rather than a mutable remote page.
            webView.loadDataWithBaseURL(
                    "https://hrishikeshks1611-stack.github.io/J.A.R.V.I.S-/v7/",
                    html, "text/html", "UTF-8", null);
        } catch (Exception error) {
            String fallback = "<html><body style='background:#050b18;color:#eaf7ff;font-family:sans-serif;padding:24px'>"
                    + "<h2>J.A.R.V.I.S</h2><p>The bundled interface could not be loaded.</p></body></html>";
            webView.loadDataWithBaseURL("https://hrishikeshks1611-stack.github.io/",
                    fallback, "text/html", "UTF-8", null);
        }
    }


    // Local British-English speech is a quota/network fallback only.
    // Gemini Algieba remains the primary voice and is never replaced while available.
    private Voice offlineFallbackVoice;

    private void initializeOfflineSpeech() {
        voicePrefs = getSharedPreferences("jarvis_voice", MODE_PRIVATE);
        textToSpeech = new TextToSpeech(this, status -> {
            if (status != TextToSpeech.SUCCESS || textToSpeech == null) return;
            ttsReady = true;
            Voice chosen = null;
            String savedName = voicePrefs.getString("voice_name", null);
            if (savedName != null) {
                Voice saved = findVoice(savedName);
                if (saved != null && !saved.isNetworkConnectionRequired()) chosen = saved;
            }
            if (chosen == null && textToSpeech.getVoices() != null) {
                // Strict male-only fallback: never select an unlabelled or female voice.
                // If Android cannot identify a male British voice, speech stays unavailable.
                for (Voice candidate : textToSpeech.getVoices()) {
                    if (!isBritish(candidate) || candidate.isNetworkConnectionRequired()
                            || !isExplicitlyMale(candidate)) continue;
                    if (chosen == null || candidate.getQuality() > chosen.getQuality()) {
                        chosen = candidate;
                    }
                }
            }
            if (chosen != null && textToSpeech.setVoice(chosen) != TextToSpeech.ERROR) {
                offlineFallbackVoice = chosen;
            }
        });
    }


    private boolean isBritish(Voice voice) {
        Locale locale = voice.getLocale();
        return locale != null && "en".equalsIgnoreCase(locale.getLanguage())
                && "GB".equalsIgnoreCase(locale.getCountry());
    }

    private boolean isExplicitlyMale(Voice voice) {
        String name = voice.getName() == null ? "" : voice.getName().toLowerCase(Locale.ROOT);
        if (name.matches(".*(^|[-_ ])(male|masculine)([-_ ]|$).*")) return true;
        if (name.matches(".*(^|[-_ ])(female|feminine)([-_ ]|$).*")) return false;
        if (voice.getFeatures() == null) return false;
        for (String feature : voice.getFeatures()) {
            String f = feature.toLowerCase(Locale.ROOT);
            if (f.equals("gender=male") || f.equals("gender_male") || f.equals("male")) return true;
        }
        return false;
    }

    private Voice findVoice(String name) {
        if (name == null || name.isEmpty() || textToSpeech == null || textToSpeech.getVoices() == null) return null;
        for (Voice voice : textToSpeech.getVoices()) if (name.equals(voice.getName()) && isBritish(voice) && isExplicitlyMale(voice)) return voice;
        return null;
    }

    private boolean isExplicitlyFemale(Voice voice) {
        String name = voice.getName() == null ? "" : voice.getName().toLowerCase(Locale.ROOT);
        if (name.matches(".*(^|[-_ ])(female|feminine)([-_ ]|$).*")) return true;
        if (voice.getFeatures() == null) return false;
        for (String feature : voice.getFeatures()) {
            String f = feature.toLowerCase(Locale.ROOT);
            if (f.equals("gender=female") || f.equals("gender_female") || f.equals("female")) return true;
        }
        return false;
    }

    private JSONArray availableBritishVoices() {
        JSONArray result = new JSONArray();
        if (textToSpeech == null || textToSpeech.getVoices() == null) return result;
        for (Voice voice : textToSpeech.getVoices()) {
            if (!isBritish(voice) || !isExplicitlyMale(voice)) continue;
            JSONObject item = new JSONObject();
            try {
                item.put("name", voice.getName());
                item.put("locale", voice.getLocale().toLanguageTag());
                item.put("quality", voice.getQuality());
                item.put("offline", !voice.isNetworkConnectionRequired());
                item.put("male", isExplicitlyMale(voice));
                item.put("features", new JSONArray(voice.getFeatures() == null ? new ArrayList<String>() : new ArrayList<String>(voice.getFeatures())));
                result.put(item);
            } catch (Exception ignored) { }
        }
        return result;
    }

    private void registerApps() {
        appPackages.put("whatsapp", new String[]{"com.whatsapp"});
        appPackages.put("youtube", new String[]{"com.google.android.youtube"});
        appPackages.put("youtube music", new String[]{"com.google.android.apps.youtube.music"});
        appPackages.put("chrome", new String[]{"com.android.chrome"});
        appPackages.put("gmail", new String[]{"com.google.android.gm"});
        appPackages.put("maps", new String[]{"com.google.android.apps.maps"});
        appPackages.put("spotify", new String[]{"com.spotify.music"});
        appPackages.put("instagram", new String[]{"com.instagram.android"});
        appPackages.put("facebook", new String[]{"com.facebook.katana"});
        appPackages.put("telegram", new String[]{"org.telegram.messenger"});
        appPackages.put("camera", new String[]{"com.android.camera2", "com.vivo.camera"});
        appPackages.put("play store", new String[]{"com.android.vending"});
        appPackages.put("messages", new String[]{"com.google.android.apps.messaging"});
        appPackages.put("phone", new String[]{"com.google.android.dialer", "com.android.dialer"});
        appPackages.put("x", new String[]{"com.twitter.android"});
        appPackages.put("twitter", new String[]{"com.twitter.android"});
        appPackages.put("reddit", new String[]{"com.reddit.frontpage"});
        appPackages.put("photos", new String[]{"com.google.android.apps.photos"});
        appPackages.put("calendar", new String[]{"com.google.android.calendar"});
        appPackages.put("keep", new String[]{"com.google.android.keep"});
        appPackages.put("contacts", new String[]{"com.google.android.contacts"});
        appPackages.put("drive", new String[]{"com.google.android.apps.docs"});
        appPackages.put("files", new String[]{"com.google.android.apps.nbu.files", "com.vivo.filemanager"});
        appPackages.put("amazon", new String[]{"com.amazon.mShop.android.shopping"});
        appPackages.put("flipkart", new String[]{"com.flipkart.android"});
        appPackages.put("browser", new String[]{"com.android.chrome", "com.vivo.browser"});
        appPackages.put("calculator", new String[]{"com.vivo.calculator"});
        appPackages.put("gallery", new String[]{"com.vivo.gallery", "com.google.android.apps.photos"});
        appPackages.put("swiggy", new String[]{"in.swiggy.android"});
        appPackages.put("zomato", new String[]{"com.application.zomato"});
    }

    private class JarvisBridge {
        @JavascriptInterface public String callContact(String requested) {
            final String contactName = requested == null ? "" : requested.trim();
            if (contactName.isEmpty() || contactName.length() > 100) return "ERROR:Please say the contact name, Sir.";
            if (Build.VERSION.SDK_INT >= 23 && checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
                pendingContactCall = contactName;
                runOnUiThread(() -> requestPermissions(new String[]{Manifest.permission.READ_CONTACTS}, REQUEST_CONTACTS));
                return "PERMISSION_REQUIRED:Contacts permission is needed to find " + contactName + ".";
            }
            return resolveContactForCall(contactName);
        }

        private String resolveContactForCall(String requestedName) {
            Cursor cursor = null;
            try {
                Uri filter = Uri.withAppendedPath(ContactsContract.CommonDataKinds.Phone.CONTENT_FILTER_URI, Uri.encode(requestedName));
                cursor = getContentResolver().query(filter,
                        new String[]{ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME, ContactsContract.CommonDataKinds.Phone.NUMBER},
                        null, null, ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC");
                if (cursor == null || !cursor.moveToFirst()) return "NOT_FOUND:No saved phone contact matched " + requestedName + ".";
                java.util.LinkedHashMap<String, String[]> matches = new java.util.LinkedHashMap<>();
                java.util.LinkedHashMap<String, String[]> exactMatches = new java.util.LinkedHashMap<>();
                do {
                    String displayName = cursor.getString(0);
                    String number = cursor.getString(1);
                    if (number == null || number.trim().isEmpty()) continue;
                    String key = number.replaceAll("[^0-9+]", "");
                    if (key.isEmpty()) continue;
                    matches.put(key, new String[]{displayName, number});
                    if (displayName != null && displayName.equalsIgnoreCase(requestedName))
                        exactMatches.put(key, new String[]{displayName, number});
                } while (cursor.moveToNext());
                java.util.Map<String, String[]> selected = exactMatches.isEmpty() ? matches : exactMatches;
                if (selected.isEmpty()) return "NOT_FOUND:No phone number was saved for " + requestedName + ".";
                if (selected.size() != 1) return "AMBIGUOUS:More than one phone number matched " + requestedName + ".";
                String[] match = selected.values().iterator().next();
                String name = match[0] == null ? requestedName : match[0];
                String number = match[1];
                runOnUiThread(() -> requestOrPlaceCall(name, number));
                return "DIALER_OPENED:Dialler opened for " + name + ". Review and confirm the call.";
            } catch (SecurityException error) {
                return "ERROR:Contacts permission was not granted, Sir.";
            } finally {
                if (cursor != null) cursor.close();
            }
        }

        private void requestOrPlaceCall(String name, String number) {
            if (number == null || number.trim().isEmpty()) return;
            try {
                Intent dial = new Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", number, null));
                startActivity(dial);
            } catch (ActivityNotFoundException error) {
                Toast.makeText(MainActivity.this, "No compatible Phone dialler is available, Sir.", Toast.LENGTH_LONG).show();
            }
        }
        @JavascriptInterface public int getInstalledVersionCode() {
            return BuildConfig.VERSION_CODE;
        }

        @JavascriptInterface public String installJarvisUpdate(String apkUrl, int expectedVersionCode) {
            if (expectedVersionCode <= BuildConfig.VERSION_CODE) return "ERROR:This update is not newer than the installed JARVIS version.";
            Uri uri;
            try { uri = Uri.parse(apkUrl); }
            catch (Exception error) { return "ERROR:The update address is invalid."; }
            String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
            if (!"https".equalsIgnoreCase(uri.getScheme()) || !("github.com".equals(host))) {
                return "ERROR:Updates must come from the official JARVIS GitHub release.";
            }
            if (Build.VERSION.SDK_INT >= 26 && !getPackageManager().canRequestPackageInstalls()) {
                runOnUiThread(() -> {
                    try {
                        Intent settings = new Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                                Uri.parse("package:" + getPackageName()));
                        startActivity(settings);
                        Toast.makeText(MainActivity.this, "Allow J.A.R.V.I.S. to request updates, then return and check again, Sir.", Toast.LENGTH_LONG).show();
                    } catch (Exception error) {
                        Toast.makeText(MainActivity.this, "Please allow app installs for J.A.R.V.I.S. in Android Settings, Sir.", Toast.LENGTH_LONG).show();
                    }
                });
                return "ALLOW_INSTALL";
            }
            new Thread(() -> downloadAndInstallJarvisUpdate(uri.toString(), expectedVersionCode)).start();
            return "DOWNLOAD_STARTED";
        }
        @JavascriptInterface public String openApp(String requested) {
            String name = requested == null ? "" : requested.toLowerCase(Locale.ROOT).trim();
            if (name.equals("settings") || name.equals("phone settings")) {
                try { startActivity(new Intent(android.provider.Settings.ACTION_SETTINGS)); return "Opening Settings, Sir."; }
                catch (Exception e) { return "Android could not open Settings, Sir."; }
            }
            String[] packages = appPackages.get(name);
            if (packages == null) return "I do not have a launch command for " + name + ", Sir.";
            for (String packageName : packages) {
                Intent launch = getPackageManager().getLaunchIntentForPackage(packageName);
                if (launch != null) {
                    launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    try { startActivity(launch); return "Opening " + title(name) + ", Sir."; }
                    catch (Exception ignored) { }
                }
            }
            return title(name) + " does not appear to be installed, Sir.";
        }


        @JavascriptInterface public String searchYouTubeVideos(String query) {
            String cleanQuery = query == null ? "" : query.trim();
            if (cleanQuery.isEmpty()) return "ERROR:Tell me what you would like to find on YouTube.";
            if (cleanQuery.length() > 300) cleanQuery = cleanQuery.substring(0, 300);
            Uri searchUri = Uri.parse("https://www.youtube.com/results?search_query=" + Uri.encode(cleanQuery));
            Intent intent = new Intent(Intent.ACTION_VIEW, searchUri);
            if (getPackageManager().getLaunchIntentForPackage("com.google.android.youtube") != null) {
                intent.setPackage("com.google.android.youtube");
            }
            AtomicReference<String> outcome = new AtomicReference<>("ERROR:Android did not confirm that YouTube search opened.");
            CountDownLatch completed = new CountDownLatch(1);
            final String requested = cleanQuery;
            runOnUiThread(() -> {
                try {
                    startActivity(intent);
                    outcome.set("SEARCH_OPENED:YouTube search opened for " + requested + ".");
                } catch (ActivityNotFoundException error) {
                    outcome.set("ERROR:No YouTube app or browser can open search results.");
                } catch (RuntimeException error) {
                    outcome.set("ERROR:Android could not open YouTube search.");
                } finally {
                    completed.countDown();
                }
            });
            try {
                if (!completed.await(5, TimeUnit.SECONDS)) return "ERROR:Timed out while opening YouTube search.";
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
                return "ERROR:Opening YouTube search was interrupted.";
            }
            return outcome.get();
        }

        @JavascriptInterface public void searchYouTubeMusic(String query, String requestId) {
            final String callbackId = requestId == null ? "" : requestId.trim();
            final String cleanQuery = query == null ? "" : query.trim();
            if (!callbackId.matches("[A-Za-z0-9_-]{1,80}")) return;
            new Thread(() -> {
                JSONObject result;
                try {
                    result = YouTubeMusicSearch.search(cleanQuery);
                } catch (Exception error) {
                    result = new JSONObject();
                    try {
                        result.put("error", error.getMessage() == null
                                ? "YouTube Music search is unavailable." : error.getMessage());
                    } catch (Exception ignored) { }
                }
                final String payload = result.toString();
                runOnUiThread(() -> {
                    if (webView != null) {
                        String script = "window.jarvisNativeMusicSearchResult && window.jarvisNativeMusicSearchResult("
                                + JSONObject.quote(callbackId) + "," + JSONObject.quote(payload) + ")";
                        webView.evaluateJavascript(script, null);
                    }
                });
            }, "JarvisYouTubeMusicSearch").start();
        }

        @JavascriptInterface public String playYoutubeMusic(String videoId) {
            String id = videoId == null ? "" : videoId.trim();
            if (!id.matches("[A-Za-z0-9_-]{11}")) return "ERROR: The selected YouTube Music track ID is invalid.";
            Uri trackUri = Uri.parse("https://music.youtube.com/watch?v=" + Uri.encode(id));
            Intent playTrack = new Intent(Intent.ACTION_VIEW, trackUri);
            playTrack.setPackage("com.google.android.apps.youtube.music");
            playTrack.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            if (playTrack.resolveActivity(getPackageManager()) == null) {
                return "ERROR: YouTube Music is not installed or cannot open this track link.";
            }
            AtomicReference<String> result = new AtomicReference<>(
                    "ERROR: Android did not confirm that YouTube Music opened the track.");
            CountDownLatch completed = new CountDownLatch(1);
            runOnUiThread(() -> {
                try {
                    startActivity(playTrack);
                    result.set("TRACK_OPENED:" + id);
                } catch (ActivityNotFoundException | SecurityException error) {
                    result.set("ERROR: YouTube Music could not open the selected track.");
                } finally {
                    completed.countDown();
                }
            });
            try {
                if (!completed.await(4, TimeUnit.SECONDS)) {
                    return "ERROR: Android timed out while opening YouTube Music.";
                }
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                return "ERROR: Track launch was interrupted.";
            }
            return result.get();
        }

        private android.media.session.MediaController getYouTubeMusicController() {
            android.media.session.MediaSessionManager manager =
                    (android.media.session.MediaSessionManager) getSystemService(MEDIA_SESSION_SERVICE);
            if (manager == null) return null;
            java.util.List<android.media.session.MediaController> sessions =
                    manager.getActiveSessions(new android.content.ComponentName(
                            MainActivity.this, JarvisMediaNotificationListener.class));
            for (android.media.session.MediaController session : sessions) {
                if ("com.google.android.apps.youtube.music".equals(session.getPackageName())) return session;
            }
            return null;
        }

        @JavascriptInterface public String getYouTubeMusicPlaybackStatus() {
            JSONObject result = new JSONObject();
            try {
                android.media.session.MediaController controller = getYouTubeMusicController();
                if (controller == null) {
                    result.put("state", "NO_SESSION");
                    return result.toString();
                }
                android.media.session.PlaybackState playback = controller.getPlaybackState();
                int state = playback == null
                        ? android.media.session.PlaybackState.STATE_NONE : playback.getState();
                String label;
                switch (state) {
                    case android.media.session.PlaybackState.STATE_PLAYING: label = "PLAYING"; break;
                    case android.media.session.PlaybackState.STATE_PAUSED: label = "PAUSED"; break;
                    case android.media.session.PlaybackState.STATE_BUFFERING: label = "BUFFERING"; break;
                    case android.media.session.PlaybackState.STATE_CONNECTING: label = "CONNECTING"; break;
                    case android.media.session.PlaybackState.STATE_ERROR: label = "ERROR"; break;
                    default: label = "IDLE"; break;
                }
                android.media.MediaMetadata metadata = controller.getMetadata();
                result.put("state", label);
                result.put("package", controller.getPackageName());
                if (metadata != null) {
                    result.put("title", metadata.getString(android.media.MediaMetadata.METADATA_KEY_TITLE));
                    result.put("artist", metadata.getString(android.media.MediaMetadata.METADATA_KEY_ARTIST));
                    result.put("mediaId", metadata.getString(android.media.MediaMetadata.METADATA_KEY_MEDIA_ID));
                }
            } catch (SecurityException permissionMissing) {
                try { result.put("state", "PERMISSION_REQUIRED"); } catch (Exception ignored) { }
            } catch (Exception error) {
                try {
                    result.put("state", "ERROR");
                    result.put("message", "Android could not read YouTube Music playback state.");
                } catch (Exception ignored) { }
            }
            return result.toString();
        }

        @JavascriptInterface public void openNotificationAccessSettings() {
            runOnUiThread(() -> {
                try {
                    startActivity(new Intent(android.provider.Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS));
                } catch (ActivityNotFoundException ignored) {
                    startActivity(new Intent(android.provider.Settings.ACTION_SETTINGS));
                }
            });
        }

        @JavascriptInterface public String controlMusic(String requestedAction) {
            String action = requestedAction == null ? "" : requestedAction.trim().toLowerCase(Locale.ROOT);
            if (!action.matches("play|pause|next|previous")) return "ERROR:Unsupported music control.";
            try {
                android.media.session.MediaSessionManager manager =
                        (android.media.session.MediaSessionManager) getSystemService(MEDIA_SESSION_SERVICE);
                if (manager == null) return "ERROR:Android media controls are unavailable on this device.";
                android.media.session.MediaController target = getYouTubeMusicController();
                if (target == null) return "ERROR:YouTube Music has no active playback session. Start a song in YouTube Music, then try again, Sir.";
                android.media.session.MediaController.TransportControls controls = target.getTransportControls();
                switch (action) {
                    case "play": controls.play(); break;
                    case "pause": controls.pause(); break;
                    case "next": controls.skipToNext(); break;
                    case "previous": controls.skipToPrevious(); break;
                }
                return "MEDIA_KEY_SENT:" + action;
            } catch (SecurityException permissionMissing) {
                runOnUiThread(() -> {
                    try {
                        startActivity(new Intent(android.provider.Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS));
                    } catch (ActivityNotFoundException ignored) {
                        Toast.makeText(MainActivity.this,
                                "Enable notification access for J.A.R.V.I.S. in Android Settings, Sir.",
                                Toast.LENGTH_LONG).show();
                    }
                });
                return "ERROR:Please enable notification access for J.A.R.V.I.S. in Android Settings, then repeat the command, Sir.";
            } catch (Exception error) {
                return "ERROR:Android could not send that YouTube Music control, Sir.";
            }
        }

        @JavascriptInterface public String openCastSettings() {
            runOnUiThread(() -> {
                Intent cast = new Intent(android.provider.Settings.ACTION_CAST_SETTINGS);
                try {
                    if (cast.resolveActivity(getPackageManager()) != null) {
                        startActivity(cast);
                    } else {
                        startActivity(new Intent(android.provider.Settings.ACTION_SETTINGS));
                        Toast.makeText(MainActivity.this,
                                "Choose Cast, Screen Cast, Smart View, or Wireless Display if your device provides it, Sir.",
                                Toast.LENGTH_LONG).show();
                    }
                } catch (ActivityNotFoundException e) {
                    Toast.makeText(MainActivity.this,
                            "Open Android Quick Settings and choose Cast or Screen Cast, Sir.",
                            Toast.LENGTH_LONG).show();
                }
            });
            return "Opening Android's Cast settings, Sir. Select a compatible TV or wireless display.";
        }

        @JavascriptInterface public String setReminder(long triggerAtMillis, String title) {
            String reminderTitle = title == null ? "" : title.trim();
            if (reminderTitle.isEmpty() || reminderTitle.length() > 120) return "ERROR:Please provide a reminder under 120 characters.";
            if (triggerAtMillis <= System.currentTimeMillis() || triggerAtMillis - System.currentTimeMillis() > 366L * 24 * 60 * 60 * 1000) {
                return "ERROR:Choose a reminder time within the next year.";
            }
            if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                pendingReminderAt = triggerAtMillis;
                pendingReminderTitle = reminderTitle;
                runOnUiThread(() -> requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQUEST_REMINDER_NOTIFICATION));
                return "PERMISSION_REQUIRED:Allow notifications so J.A.R.V.I.S. can deliver this reminder.";
            }
            scheduleReminderNotification(triggerAtMillis, reminderTitle);
            return "REMINDER_SCHEDULED:Reminder scheduled for " + new java.text.SimpleDateFormat("EEE, d MMM · h:mm a", Locale.getDefault()).format(new java.util.Date(triggerAtMillis)) + ".";
        }

        @JavascriptInterface public String setAlarm(int hour, int minute, String label) {
            final Intent intent;
            try {
                intent = AlarmIntentFactory.create(hour, minute, label);
            } catch (IllegalArgumentException invalidTime) {
                return "That alarm time is invalid, Sir.";
            }
            if (intent.resolveActivity(getPackageManager()) == null) {
                return "No compatible Clock app is available on this device, Sir.";
            }
            runOnUiThread(() -> {
                try { startActivity(intent); }
                catch (ActivityNotFoundException e) {
                    Toast.makeText(MainActivity.this, "No compatible Clock app is available, Sir.", Toast.LENGTH_LONG).show();
                }
            });
            return "Opening Clock so you can review and confirm that alarm, Sir.";
        }

        @JavascriptInterface public String createCalendarEvent(String title, long beginMillis, long endMillis, String description) {
            String eventTitle = title == null ? "" : title.trim();
            if (eventTitle.isEmpty() || beginMillis <= 0 || endMillis <= beginMillis) {
                return "That calendar event needs a title and a valid start and end time, Sir.";
            }
            Intent intent = new Intent(Intent.ACTION_INSERT);
            intent.setData(CalendarContract.Events.CONTENT_URI);
            intent.putExtra(CalendarContract.Events.TITLE, eventTitle);
            intent.putExtra(CalendarContract.Events.DESCRIPTION, description == null ? "" : description.trim());
            intent.putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, beginMillis);
            intent.putExtra(CalendarContract.EXTRA_EVENT_END_TIME, endMillis);
            runOnUiThread(() -> {
                try { startActivity(intent); }
                catch (ActivityNotFoundException e) {
                    Toast.makeText(MainActivity.this, "No compatible Calendar app is available, Sir.", Toast.LENGTH_LONG).show();
                }
            });
            return "Opening Calendar so you can review and save " + eventTitle + ", Sir.";
        }

        @JavascriptInterface public String saveFile(String requestedName, String mimeType, String base64Data) {
            String safeName = requestedName == null ? "JARVIS_file" : requestedName.replaceAll("[^A-Za-z0-9._ -]", "_").trim();
            if (safeName.isEmpty() || safeName.length() > 120) safeName = "JARVIS_file";
            Map<String, String> allowedTypes = new HashMap<>();
            allowedTypes.put("application/pdf", ".pdf");
            allowedTypes.put("application/vnd.openxmlformats-officedocument.wordprocessingml.document", ".docx");
            allowedTypes.put("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", ".xlsx");
            allowedTypes.put("application/vnd.openxmlformats-officedocument.presentationml.presentation", ".pptx");
            String requiredExtension = allowedTypes.get(mimeType);
            if (requiredExtension == null) return "ERROR:This file type is not supported for native download.";
            if (!safeName.toLowerCase(Locale.ROOT).endsWith(requiredExtension)) safeName += requiredExtension;
            if (base64Data == null || base64Data.isEmpty() || base64Data.length() > 42 * 1024 * 1024) {
                return "ERROR:The file is empty or exceeds the 30 MB download limit.";
            }
            Uri savedUri = null;
            try {
                byte[] bytes = Base64.decode(base64Data, Base64.DEFAULT);
                if (bytes.length == 0 || bytes.length > 30 * 1024 * 1024) {
                    return "ERROR:The file is empty or exceeds the 30 MB download limit.";
                }
                if (android.os.Build.VERSION.SDK_INT < 29) {
                    return "ERROR:Saving directly to Downloads requires Android 10 or newer on this app build.";
                }
                ContentValues values = new ContentValues();
                values.put(MediaStore.Downloads.DISPLAY_NAME, safeName);
                values.put(MediaStore.Downloads.MIME_TYPE, mimeType);
                values.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/JARVIS");
                values.put(MediaStore.Downloads.IS_PENDING, 1);
                savedUri = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
                if (savedUri == null) throw new IllegalStateException("Android could not create the Downloads entry.");
                try (OutputStream output = getContentResolver().openOutputStream(savedUri, "w")) {
                    if (output == null) throw new IllegalStateException("Android could not open the Downloads file.");
                    output.write(bytes);
                    output.flush();
                }
                ContentValues complete = new ContentValues();
                complete.put(MediaStore.Downloads.IS_PENDING, 0);
                getContentResolver().update(savedUri, complete, null, null);
                final String notice = safeName + " saved in Downloads/JARVIS.";
                runOnUiThread(() -> Toast.makeText(MainActivity.this, notice, Toast.LENGTH_LONG).show());
                return "SAVED:" + notice;
            } catch (Exception error) {
                if (savedUri != null) {
                    try { getContentResolver().delete(savedUri, null, null); } catch (Exception ignored) { }
                }
                runOnUiThread(() -> Toast.makeText(MainActivity.this,
                        "JARVIS could not save that file. Please try again, Sir.", Toast.LENGTH_LONG).show());
                return "ERROR:Android could not save the file to Downloads.";
            }
        }

        @JavascriptInterface public void chooseFile() {
            runOnUiThread(() -> {
                Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("*/*");
                intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{
                        "image/*", "application/pdf", "text/plain", "text/csv",
                        "application/json", "text/markdown"
                });
                try { startActivityForResult(intent, REQUEST_FILE); }
                catch (ActivityNotFoundException e) {
                    Toast.makeText(MainActivity.this, "No file picker is available.", Toast.LENGTH_LONG).show();
                }
            });
        }

        @JavascriptInterface public void openCamera() {
            runOnUiThread(() -> {
                Intent intent = new Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA);
                try {
                    if (intent.resolveActivity(getPackageManager()) == null) intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                    startActivity(intent);
                } catch (ActivityNotFoundException error) {
                    Toast.makeText(MainActivity.this, "No camera app is available, Sir.", Toast.LENGTH_LONG).show();
                }
            });
        }

        @JavascriptInterface public void capturePhoto(boolean selfie) {
            runOnUiThread(() -> {
                Intent capture = new Intent(MainActivity.this, CameraCaptureActivity.class);
                capture.putExtra(CameraCaptureActivity.EXTRA_SELFIE, selfie);
                try { startActivity(capture); }
                catch (ActivityNotFoundException error) {
                    Toast.makeText(MainActivity.this, "JARVIS could not open its camera, Sir.", Toast.LENGTH_LONG).show();
                }
            });
        }

        @JavascriptInterface public void takePhoto() {
            runOnUiThread(() -> {
                Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                if (intent.resolveActivity(getPackageManager()) == null) {
                    Toast.makeText(MainActivity.this, "No camera app is available.", Toast.LENGTH_LONG).show();
                    return;
                }
                ContentValues values = new ContentValues();
                values.put(MediaStore.Images.Media.DISPLAY_NAME, "JARVIS_" + System.currentTimeMillis() + ".jpg");
                values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
                pendingCameraUri = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
                if (pendingCameraUri != null) {
                    intent.putExtra(MediaStore.EXTRA_OUTPUT, pendingCameraUri);
                    intent.setClipData(ClipData.newUri(getContentResolver(), "JARVIS camera output", pendingCameraUri));
                    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                }
                try { startActivityForResult(intent, REQUEST_CAMERA); }
                catch (ActivityNotFoundException error) {
                    pendingCameraUri = null;
                    Toast.makeText(MainActivity.this, "The camera could not be opened.", Toast.LENGTH_LONG).show();
                }
            });
        }

        @JavascriptInterface public void showLastPhoto() {
            runOnUiThread(() -> {
                String saved = getSharedPreferences("jarvis_camera", MODE_PRIVATE).getString("last_photo_uri", null);
                if (saved == null || saved.trim().isEmpty()) {
                    Intent gallery = new Intent(Intent.ACTION_VIEW);
                    gallery.setDataAndType(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, "image/*");
                    try { startActivity(gallery); }
                    catch (ActivityNotFoundException error) {
                        Toast.makeText(MainActivity.this, "No photo gallery is available, Sir.", Toast.LENGTH_LONG).show();
                    }
                    return;
                }
                Uri photo = Uri.parse(saved);
                Intent view = new Intent(Intent.ACTION_VIEW);
                view.setDataAndType(photo, "image/jpeg");
                view.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                try { startActivity(view); }
                catch (ActivityNotFoundException error) {
                    Intent gallery = new Intent(Intent.ACTION_VIEW);
                    gallery.setDataAndType(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, "image/*");
                    try { startActivity(gallery); }
                    catch (ActivityNotFoundException ignored) {
                        Toast.makeText(MainActivity.this, "The saved photo is on this device, but no gallery app is available, Sir.", Toast.LENGTH_LONG).show();
                    }
                }
            });
        }

        @JavascriptInterface public String speakOfflineFallback(String text) {
            if (!ttsReady || textToSpeech == null || offlineFallbackVoice == null) return "unavailable";
            final String message = text == null ? "" : text.trim();
            if (message.isEmpty()) return "unavailable";
            runOnUiThread(() -> {
                if (textToSpeech == null || offlineFallbackVoice == null) return;
                textToSpeech.setVoice(offlineFallbackVoice);
                textToSpeech.speak(message, TextToSpeech.QUEUE_FLUSH, null,
                        "JARVIS_OFFLINE_" + System.currentTimeMillis());
            });
            return "started";
        }

        @JavascriptInterface public String getOfflineFallbackStatus() {
            if (!ttsReady) return "starting";
            return offlineFallbackVoice == null ? "unavailable" : "ready";
        }

        @JavascriptInterface public String getAvailableVoices() {
            if (!ttsReady) return "[]";
            return availableBritishVoices().toString();
        }

        @JavascriptInterface public String getSpeechStatus() {
            if (!ttsReady) return "Android speech engine is still starting, Sir.";
            if (textToSpeech == null) return "No Android speech engine is available, Sir.";
            if (availableBritishVoices().length() == 0) {
                return "No explicitly identified male English (UK) voice is available. Install a male British voice in Android Text-to-speech settings, Sir.";
            }
            if (!voiceChosen) return "Choose the male British voice, test it, then select Use this voice, Sir.";
            return "Your selected British voice is ready, Sir.";
        }

        @JavascriptInterface public void openLocationSettings() {
            runOnUiThread(() -> {
                try {
                    Intent intent = new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.parse("package:" + getPackageName()));
                    startActivity(intent);
                } catch (Exception e) {
                    Toast.makeText(MainActivity.this, "Open Android Settings > Apps > J.A.R.V.I.S. > Permissions > Location, Sir.", Toast.LENGTH_LONG).show();
                }
            });
        }

        @JavascriptInterface public void openTextToSpeechSettings() {
            runOnUiThread(() -> {
                try {
                    Intent intent = new Intent("com.android.settings.TTS_SETTINGS");
                    if (intent.resolveActivity(getPackageManager()) == null) {
                        intent = new Intent(android.provider.Settings.ACTION_SETTINGS);
                    }
                    startActivity(intent);
                } catch (Exception e) {
                    Toast.makeText(MainActivity.this, "Open Settings and search for Text-to-speech output, Sir.", Toast.LENGTH_LONG).show();
                }
            });
        }

        @JavascriptInterface public String previewBritishVoice(String voiceName) {
            if (!ttsReady || voiceName == null) return "Voice engine is not ready, Sir.";
            Voice candidate = findVoice(voiceName);
            if (candidate == null) return "That British voice is not installed, Sir.";
            Voice previous = textToSpeech.getVoice();
            if (textToSpeech.setVoice(candidate) == TextToSpeech.ERROR) return "Android could not preview that voice, Sir.";
            final String previewId = "JARVIS_PREVIEW_" + System.currentTimeMillis();
            textToSpeech.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                private void restore() {
                    runOnUiThread(() -> {
                        if (previous != null) textToSpeech.setVoice(previous);
                    });
                }
                @Override public void onStart(String id) { }
                @Override public void onDone(String id) { if (previewId.equals(id)) restore(); }
                @Override public void onError(String id) { if (previewId.equals(id)) restore(); }
            });
            textToSpeech.speak("Good evening, Sir. I am JARVIS. How may I assist you today?",
                    TextToSpeech.QUEUE_FLUSH, null, previewId);
            return "Previewing " + candidate.getName() + ". This voice has not been saved yet, Sir.";
        }

        @JavascriptInterface public String selectBritishVoice(String voiceName) {
            if (!ttsReady || voiceName == null) return "Voice engine is not ready, Sir.";
            Voice voice = findVoice(voiceName);
            if (voice == null) return "That British voice is not installed, Sir.";
            int result = textToSpeech.setVoice(voice);
            if (result == TextToSpeech.ERROR) return "Android could not activate that voice, Sir.";
            voicePrefs.edit().putString("voice_name", voice.getName()).apply();
            voiceChosen = true;
            return "Selected " + voice.getName() + ", Sir. Use Test voice to audition it.";
        }

        @JavascriptInterface public void speak(String text) {
            runOnUiThread(() -> {
                String message = text == null ? "" : text;
                if (textToSpeech == null || !ttsReady) {
                    pendingSpeech = message;
                    return;
                }
                if (!voiceChosen) {
                    Toast.makeText(MainActivity.this,
                            "Please choose, test, and activate an English (UK) voice first, Sir.",
                            Toast.LENGTH_LONG).show();
                    return;
                }
                textToSpeech.speak(message, TextToSpeech.QUEUE_FLUSH, null, "JARVIS_" + System.currentTimeMillis());
            });
        }

        @JavascriptInterface public void stopSpeaking() {
            runOnUiThread(() -> { if (textToSpeech != null) textToSpeech.stop(); });
        }

        @JavascriptInterface public void openWakeWordSettings() {
            runOnUiThread(() -> startActivity(new Intent(MainActivity.this, WakeWordActivity.class)));
        }

        @JavascriptInterface public void startVoiceRecognition() {
            runOnUiThread(() -> {
                if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                    requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQUEST_AUDIO_INPUT);
                    return;
                }
                startOfflineDictation();
            });
        }
    }

    private void downloadAndInstallJarvisUpdate(String address, int expectedVersionCode) {
        File apk = null;
        HttpURLConnection connection = null;
        try {
            URL url = new URL(address);
            connection = (HttpURLConnection) url.openConnection();
            connection.setInstanceFollowRedirects(true);
            connection.setConnectTimeout(20000);
            connection.setReadTimeout(45000);
            connection.setRequestProperty("User-Agent", "JARVIS-Android-Updater");
            connection.connect();
            String finalHost = connection.getURL().getHost() == null ? "" : connection.getURL().getHost().toLowerCase(Locale.ROOT);
            if (!"github.com".equals(finalHost) && !"release-assets.githubusercontent.com".equals(finalHost)) {
                throw new Exception("The update download did not come from GitHub.");
            }
            if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
                throw new Exception("GitHub could not provide the update (HTTP " + connection.getResponseCode() + ").");
            }
            long length = connection.getContentLengthLong();
            if (length > 150L * 1024L * 1024L) throw new Exception("The update file is larger than the allowed size.");
            apk = new File(getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "jarvis-update-" + expectedVersionCode + ".apk");
            File parent = apk.getParentFile();
            if (parent != null && !parent.exists() && !parent.mkdirs()) throw new Exception("Could not prepare secure update storage.");
            try (InputStream input = connection.getInputStream(); FileOutputStream output = new FileOutputStream(apk, false)) {
                byte[] buffer = new byte[16384];
                int count;
                long total = 0;
                while ((count = input.read(buffer)) != -1) {
                    total += count;
                    if (total > 150L * 1024L * 1024L) throw new Exception("The update file is larger than the allowed size.");
                    output.write(buffer, 0, count);
                }
                output.getFD().sync();
            }
            if (!apk.isFile() || apk.length() == 0) throw new Exception("The downloaded update is empty.");
            PackageInfo candidate = getPackageManager().getPackageArchiveInfo(apk.getAbsolutePath(), 0);
            if (candidate == null || !getPackageName().equals(candidate.packageName)) {
                throw new Exception("This APK is not the JARVIS phone app. Your installed app has not been changed.");
            }
            long candidateVersion = Build.VERSION.SDK_INT >= 28 ? candidate.getLongVersionCode() : candidate.versionCode;
            if (candidateVersion != expectedVersionCode || candidateVersion <= BuildConfig.VERSION_CODE) {
                throw new Exception("The APK version does not match the update listing or is not newer. Your installed app has not been changed.");
            }
            PackageInstaller installer = getPackageManager().getPackageInstaller();
            PackageInstaller.SessionParams params = new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL);
            params.setAppPackageName(getPackageName());
            params.setSize(apk.length());
            int sessionId = installer.createSession(params);
            PackageInstaller.Session session = installer.openSession(sessionId);
            try (InputStream input = new java.io.FileInputStream(apk);
                 java.io.OutputStream output = session.openWrite("jarvis-update.apk", 0, apk.length())) {
                byte[] buffer = new byte[16384];
                int count;
                while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
                session.fsync(output);
            }
            Intent statusIntent = new Intent(this, UpdateInstallReceiver.class);
            int pendingFlags = PendingIntent.FLAG_UPDATE_CURRENT;
            if (Build.VERSION.SDK_INT >= 31) pendingFlags |= PendingIntent.FLAG_MUTABLE;
            PendingIntent statusPending = PendingIntent.getBroadcast(this, expectedVersionCode, statusIntent, pendingFlags);
            session.commit(statusPending.getIntentSender());
            runOnUiThread(() -> Toast.makeText(this, "JARVIS update checked. Android will ask you to confirm installation if it is compatible, Sir.", Toast.LENGTH_LONG).show());
        } catch (Exception error) {
            final String message = error.getMessage() == null ? "The update could not be prepared." : error.getMessage();
            runOnUiThread(() -> Toast.makeText(this, message + " Your existing JARVIS installation and data have not been deliberately removed.", Toast.LENGTH_LONG).show());
        } finally {
            if (connection != null) connection.disconnect();
            // PackageInstaller has copied the session contents by the time commit is called.
            if (apk != null && apk.exists()) apk.delete();
        }
    }

    private void startOfflineDictation() {
        try {
            Intent intent = new Intent(this, WakeWordService.class).setAction(WakeWordService.ACTION_DICTATION);
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(intent);
            else startService(intent);
            if (webView != null) webView.evaluateJavascript(
                    "if(typeof status==='function')status('Starting microphone…','thinking');if(document.getElementById('hint'))document.getElementById('hint').textContent='Loading the offline speech engine and opening the microphone…'", null);
        } catch (RuntimeException error) {
            Toast.makeText(this, "JARVIS could not start its private microphone. Please try again.", Toast.LENGTH_LONG).show();
        }
    }

    private String title(String value) {
        if (value == null || value.isEmpty()) return "that app";
        return value.substring(0, 1).toUpperCase(Locale.ROOT) + value.substring(1);
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_AUDIO_INPUT) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) startOfflineDictation();
            else Toast.makeText(this, "Microphone permission is needed for private JARVIS voice input.", Toast.LENGTH_LONG).show();
            return;
        }
        if (requestCode == REQUEST_CONTACTS) {
            String contactName = pendingContactCall;
            pendingContactCall = null;
            boolean granted = grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED;
            if (granted && contactName != null) {
                String result = jarvisBridge == null ? "ERROR:Phone controls are unavailable." : jarvisBridge.resolveContactForCall(contactName);
                if (result.startsWith("CALL_STARTING:")) {
                    if (webView != null) webView.evaluateJavascript("if(typeof speak==='function')speak('Certainly, Sir.')", null);
                } else {
                    Toast.makeText(this, result.replaceFirst("^[A-Z_]+:", ""), Toast.LENGTH_LONG).show();
                }
            } else {
                Toast.makeText(this, "Contacts access was not granted. No call was started, Sir.", Toast.LENGTH_LONG).show();
            }
            return;
        }
        if (requestCode == REQUEST_REMINDER_NOTIFICATION) {
            boolean granted = grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED;
            long triggerAt = pendingReminderAt;
            String title = pendingReminderTitle;
            pendingReminderAt = 0L;
            pendingReminderTitle = null;
            if (granted && triggerAt > System.currentTimeMillis() && title != null) {
                scheduleReminderNotification(triggerAt, title);
            } else {
                Toast.makeText(this, "Notifications were not allowed. No reminder was scheduled, Sir.", Toast.LENGTH_LONG).show();
            }
            return;
        }
        if (requestCode == REQUEST_LOCATION && pendingGeoCallback != null) {
            boolean precise = checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
            GeolocationPermissions.Callback callback = pendingGeoCallback;
            String origin = pendingGeoOrigin;
            pendingGeoCallback = null;
            pendingGeoOrigin = null;
            callback.invoke(origin, precise, false);
            if (!precise) {
                runOnUiThread(() -> Toast.makeText(this,
                        "For precise location, choose Precise in the Android permission prompt and allow location access, Sir.",
                        Toast.LENGTH_LONG).show());
            }
        }
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_FILE && resultCode == RESULT_OK && data != null && data.getData() != null) {
            sendAttachment(data.getData(), "attachment");
            return;
        }
        if (requestCode == REQUEST_CAMERA) {
            if (resultCode == RESULT_OK && pendingCameraUri != null) {
                sendAttachment(pendingCameraUri, "JARVIS_camera.jpg");
            } else if (resultCode == RESULT_OK && data != null && data.getExtras() != null) {
                Object image = data.getExtras().get("data");
                if (image instanceof Bitmap) sendBitmapAttachment((Bitmap) image);
            } else if (pendingCameraUri != null) {
                try { getContentResolver().delete(pendingCameraUri, null, null); } catch (Exception ignored) { }
            }
            pendingCameraUri = null;
            return;
        }
    }

    private void sendBitmapAttachment(Bitmap bitmap) {
        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            bitmap.compress(Bitmap.CompressFormat.JPEG, 88, output);
            sendAttachmentPayload("JARVIS_camera.jpg", "image/jpeg", output.toByteArray());
        } catch (Exception e) {
            Toast.makeText(this, "Could not prepare the camera image.", Toast.LENGTH_LONG).show();
        }
    }

    private void sendAttachment(Uri uri, String fallbackName) {
        try {
            String mime = getContentResolver().getType(uri);
            if (mime == null) mime = "application/octet-stream";
            String name = fallbackName;
            Cursor cursor = getContentResolver().query(uri, null, null, null, null);
            if (cursor != null) {
                try {
                    int column = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME);
                    if (cursor.moveToFirst() && column >= 0) name = cursor.getString(column);
                } finally { cursor.close(); }
            }
            InputStream input = getContentResolver().openInputStream(uri);
            if (input == null) throw new Exception("Unable to open file");
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int count;
            while ((count = input.read(buffer)) != -1) {
                if (output.size() + count > 3 * 1024 * 1024) {
                    input.close();
                    throw new Exception("Please choose a file smaller than 3 MB.");
                }
                output.write(buffer, 0, count);
            }
            input.close();
            if (output.size() == 0) throw new Exception("The selected file appears to be empty.");
            sendAttachmentPayload(name, mime, output.toByteArray());
        } catch (Exception e) {
            String message = e.getMessage() == null ? "Could not read that attachment." : e.getMessage();
            Toast.makeText(this, message, Toast.LENGTH_LONG).show();
        }
    }

    private void sendAttachmentPayload(String name, String mime, byte[] bytes) throws Exception {
        JSONObject payload = new JSONObject();
        payload.put("name", name);
        payload.put("mimeType", mime);
        payload.put("data", Base64.encodeToString(bytes, Base64.NO_WRAP));
        String argument = JSONObject.quote(payload.toString());
        runOnUiThread(() -> {
            if (webView != null) webView.evaluateJavascript(
                    "window.jarvisNative && window.jarvisNative.onAttachment(" + argument + ")", null);
        });
    }

    private void applyTvImmersiveMode() {
        getWindow().getDecorView().setSystemUiVisibility(
                android.view.View.SYSTEM_UI_FLAG_FULLSCREEN
                | android.view.View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | android.view.View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                | android.view.View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | android.view.View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | android.view.View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
    }

    @Override public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus && BuildConfig.JARVIS_TV) applyTvImmersiveMode();
    }

    @Override protected void onStop() {
        // The foreground microphone service must outlive this Activity when the owner
        // explicitly enabled wake-word listening. Tap-to-speak remains self-terminating.
        boolean wakeWordEnabled = getSharedPreferences("jarvis_wake_word", MODE_PRIVATE)
                .getBoolean("enabled", false);
        if (!wakeWordEnabled) stopService(new Intent(this, WakeWordService.class));
        super.onStop();
    }

    @Override protected void onDestroy() {
        if (wakeWordReceiver != null) { try { unregisterReceiver(wakeWordReceiver); } catch (Exception ignored) { } wakeWordReceiver = null; }
        if (textToSpeech != null) { textToSpeech.stop(); textToSpeech.shutdown(); }
        if (webView != null) webView.destroy();
        super.onDestroy();
    }

    @Override public void onBackPressed() {
        if (webView != null && webView.canGoBack()) webView.goBack();
        else super.onBackPressed();
    }
}
