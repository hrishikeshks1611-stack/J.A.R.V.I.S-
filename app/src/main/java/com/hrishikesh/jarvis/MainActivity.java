package com.hrishikesh.jarvis;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.IntentFilter;
import android.Manifest;
import android.content.pm.PackageManager;
import android.content.pm.ApplicationInfo;
import android.content.ContentValues;
import android.content.ClipData;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.provider.MediaStore;
import android.provider.ContactsContract;
import android.provider.CallLog;
import android.provider.AlarmClock;
import android.provider.CalendarContract;
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
import android.webkit.PermissionRequest;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.Locale;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.List;
import java.util.Collections;
import java.util.Comparator;

public class MainActivity extends Activity {
    private static final int REQUEST_AUDIO_INPUT = 4102;
    private static final int REQUEST_FILE = 4103;
    private static final int REQUEST_CAMERA = 4104;
    private static final int REQUEST_LOCATION = 4105;
    private static final int REQUEST_CONTACTS = 4107;
    private static final int REQUEST_CALL_LOG = 4108;
    private static final int REQUEST_WEB_AUDIO = 4106;
    private PermissionRequest pendingWebAudioRequest;
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
        getWindow().setStatusBarColor(android.graphics.Color.rgb(5, 11, 24));
        getWindow().setNavigationBarColor(android.graphics.Color.rgb(5, 11, 24));
        registerApps();
        initializeOfflineSpeech();
        wakeWordReceiver = new BroadcastReceiver() {
            @Override public void onReceive(android.content.Context context, Intent intent) {
                String action = intent == null ? null : intent.getAction();
                if (WakeWordService.ACTION_WAKE.equals(action)) {
                    if (webView != null) webView.evaluateJavascript("window.jarvisNative && window.jarvisNative.onWakeWordDetected && window.jarvisNative.onWakeWordDetected()", null);
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
        webView.addJavascriptInterface(new JarvisBridge(), "AndroidJarvis");
        webView.setWebChromeClient(new WebChromeClient() {
            @Override public void onPermissionRequest(PermissionRequest request) {
                runOnUiThread(() -> {
                    boolean audioOnly = true;
                    for (String resource : request.getResources()) {
                        if (!PermissionRequest.RESOURCE_AUDIO_CAPTURE.equals(resource)) audioOnly = false;
                    }
                    if (!audioOnly) {
                        request.deny();
                        return;
                    }
                    if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                        request.grant(new String[]{PermissionRequest.RESOURCE_AUDIO_CAPTURE});
                    } else {
                        if (pendingWebAudioRequest != null) pendingWebAudioRequest.deny();
                        pendingWebAudioRequest = request;
                        requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQUEST_WEB_AUDIO);
                    }
                });
            }
            @Override public void onPermissionRequestCanceled(PermissionRequest request) {
                if (pendingWebAudioRequest == request) pendingWebAudioRequest = null;
            }
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
        loadJarvisInterface();
        restoreWakeWordServiceIfEnabled();
    }


    public static boolean isFinancialApp(String packageName, String label) {
        String pkg = packageName == null ? "" : packageName.toLowerCase(Locale.ROOT);
        String name = label == null ? "" : label.toLowerCase(Locale.ROOT);
        String[] packageMarkers = {
                "com.google.android.apps.nbu.paisa", "com.phonepe", "net.one97.paytm", "com.paytm",
                "in.org.npci.upiapp", "com.sbi", "com.hdfc", "com.icicibank", "com.axis.mobile",
                "com.kotak", "com.idfcfirstbank", "com.bankofbaroda", "com.federalbank", "com.canarabank",
                "com.pnb", "com.indusind", "com.rbl", "com.yesbank", "com.cred", "com.mobikwik",
                "com.freecharge", "com.razorpay", "com.slice", "com.jupiter", "com.supermoney"
        };
        for (String marker : packageMarkers) if (pkg.contains(marker)) return true;
        String[] labelMarkers = {"bank", "upi", "wallet", "finance", "payment", "paytm", "phonepe", "gpay", "google pay", "bhim", "cred", "mobikwik", "freecharge"};
        for (String marker : labelMarkers) if (name.contains(marker)) return true;
        return false;
    }

    private String normalizeAppLabel(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    private void loadJarvisInterface() {
        try (InputStream input = getAssets().open("index.html");
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[16384];
            int count;
            while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
            String html = new String(output.toByteArray(), StandardCharsets.UTF_8);
            webView.loadDataWithBaseURL(
                    "https://hrishikeshks1611-stack.github.io/J.A.R.V.I.S-/",
                    html, "text/html", "UTF-8", null);
        } catch (Exception error) {
            android.util.Log.e("JARVIS", "Could not load the bundled JARVIS interface", error);
            Toast.makeText(this, "J.A.R.V.I.S. could not load its built-in interface. Please reopen the app, Sir.", Toast.LENGTH_LONG).show();
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
                // Prefer voices explicitly marked male. Some Android engines omit gender
                // metadata, so accept an unlabelled UK voice rather than disabling fallback.
                for (Voice candidate : textToSpeech.getVoices()) {
                    if (!isBritish(candidate) || candidate.isNetworkConnectionRequired()) continue;
                    if (isExplicitlyMale(candidate)) {
                        if (chosen == null || !isExplicitlyMale(chosen)
                                || candidate.getQuality() > chosen.getQuality()) chosen = candidate;
                    } else if (!isExplicitlyFemale(candidate)
                            && (chosen == null || (!isExplicitlyMale(chosen)
                            && candidate.getQuality() > chosen.getQuality()))) {
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
        @JavascriptInterface public String openApp(String requested) {
            String name = requested == null ? "" : requested.toLowerCase(Locale.ROOT).trim();
            if (name.equals("settings") || name.equals("phone settings")) {
                try { startActivity(new Intent(android.provider.Settings.ACTION_SETTINGS)); return "Opening Settings, Sir."; }
                catch (Exception e) { return "Android could not open Settings, Sir."; }
            }
            if (isFinancialApp("", name)) return "Financial apps and money-related actions are outside my J.A.R.V.I.S. permissions, Sir.";
            String[] packages = appPackages.get(name);
            if (packages != null) {
                for (String packageName : packages) {
                    if (isFinancialApp(packageName, name)) return "Financial apps are outside my J.A.R.V.I.S. permissions, Sir.";
                    Intent launch = getPackageManager().getLaunchIntentForPackage(packageName);
                    if (launch != null) {
                        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        try { startActivity(launch); return "Opening " + title(name) + ", Sir."; }
                        catch (Exception ignored) { }
                    }
                }
            }
            List<ApplicationInfo> installed = getPackageManager().getInstalledApplications(PackageManager.GET_META_DATA);
            List<ApplicationInfo> matches = new ArrayList<>();
            String query = normalizeAppLabel(name);
            for (ApplicationInfo info : installed) {
                String label;
                try { label = String.valueOf(getPackageManager().getApplicationLabel(info)); }
                catch (Exception ignored) { continue; }
                if (isFinancialApp(info.packageName, label)) continue;
                if (!query.isEmpty() && normalizeAppLabel(label).equals(query)
                        && getPackageManager().getLaunchIntentForPackage(info.packageName) != null) matches.add(info);
            }
            if (matches.size() == 1) {
                Intent launch = getPackageManager().getLaunchIntentForPackage(matches.get(0).packageName);
                if (launch != null) {
                    launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    try { startActivity(launch); return "Opening " + String.valueOf(getPackageManager().getApplicationLabel(matches.get(0))) + ", Sir."; }
                    catch (Exception ignored) { }
                }
            }
            if (matches.size() > 1) return "I found more than one app named " + name + ". Please give me its full app name, Sir.";
            return title(name) + " is not installed or is outside my supported non-financial app access, Sir.";
        }

        @JavascriptInterface public String getInstalledApps() {
            JSONArray apps = new JSONArray();
            try {
                List<ApplicationInfo> installed = getPackageManager().getInstalledApplications(PackageManager.GET_META_DATA);
                Collections.sort(installed, Comparator.comparing((ApplicationInfo info) -> {
                    try { return String.valueOf(getPackageManager().getApplicationLabel(info)).toLowerCase(Locale.ROOT); }
                    catch (Exception ignored) { return info.packageName.toLowerCase(Locale.ROOT); }
                }));
                for (ApplicationInfo info : installed) {
                    Intent launch = getPackageManager().getLaunchIntentForPackage(info.packageName);
                    if (launch == null) continue;
                    String label;
                    try { label = String.valueOf(getPackageManager().getApplicationLabel(info)); }
                    catch (Exception ignored) { label = info.packageName; }
                    if (isFinancialApp(info.packageName, label)) continue;
                    JSONObject item = new JSONObject();
                    item.put("name", label);
                    item.put("packageName", info.packageName);
                    apps.put(item);
                    if (apps.length() >= 300) break;
                }
                return apps.toString();
            } catch (Exception error) {
                return "[]";
            }
        }


        @JavascriptInterface public String playYoutubeMusic(String videoId) {
            String id = videoId == null ? "" : videoId.trim();
            if (!id.matches("[A-Za-z0-9_-]{11}")) return "ERROR: The selected YouTube Music track ID is invalid.";
            runOnUiThread(() -> {
                // Hand YouTube Music the canonical HTTPS watch URL for this exact
                // catalogue video ID. The nonstandard vnd.youtube.music scheme is
                // not consistently registered across YouTube Music versions.
                Uri trackUri = Uri.parse("https://music.youtube.com/watch?v=" + Uri.encode(id));
                Intent playTrack = new Intent(Intent.ACTION_VIEW, trackUri);
                playTrack.setPackage("com.google.android.apps.youtube.music");
                playTrack.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                        | Intent.FLAG_ACTIVITY_CLEAR_TOP
                        | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                try {
                    if (playTrack.resolveActivity(getPackageManager()) == null) {
                        throw new ActivityNotFoundException("YouTube Music cannot handle direct track links");
                    }
                    startActivity(playTrack);
                } catch (ActivityNotFoundException | SecurityException noHandler) {
                    Toast.makeText(MainActivity.this, "YouTube Music could not open the selected track, Sir. Please check that the app is installed and updated.", Toast.LENGTH_LONG).show();
                }
            });
            return "TRACK_OPEN_REQUESTED:" + id;
        }

        @JavascriptInterface public String controlMusic(String requestedAction) {
            String action = requestedAction == null ? "" : requestedAction.trim().toLowerCase(Locale.ROOT);
            if (!action.matches("play|pause|next|previous")) return "ERROR:Unsupported music control.";
            try {
                android.media.session.MediaSessionManager manager =
                        (android.media.session.MediaSessionManager) getSystemService(MEDIA_SESSION_SERVICE);
                if (manager == null) return "ERROR:Android media controls are unavailable on this device.";
                java.util.List<android.media.session.MediaController> sessions =
                        manager.getActiveSessions(new android.content.ComponentName(
                                MainActivity.this, JarvisMediaNotificationListener.class));
                android.media.session.MediaController target = null;
                for (android.media.session.MediaController session : sessions) {
                    if ("com.google.android.apps.youtube.music".equals(session.getPackageName())) {
                        target = session;
                        break;
                    }
                }
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

        @JavascriptInterface public String getPhoneContacts() {
            if (Build.VERSION.SDK_INT >= 23 && checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
                runOnUiThread(() -> requestPermissions(new String[]{Manifest.permission.READ_CONTACTS}, REQUEST_CONTACTS));
                return "PERMISSION_REQUIRED";
            }
            try {
                Map<Long, JSONArray> nicknamesByContact = new HashMap<>();
                try (Cursor cursor = getContentResolver().query(
                        ContactsContract.Data.CONTENT_URI,
                        new String[]{ContactsContract.Data.CONTACT_ID, ContactsContract.CommonDataKinds.Nickname.NAME},
                        ContactsContract.Data.MIMETYPE + "=?",
                        new String[]{ContactsContract.CommonDataKinds.Nickname.CONTENT_ITEM_TYPE},
                        null)) {
                    if (cursor != null) {
                        while (cursor.moveToNext()) {
                            long contactId = cursor.getLong(0);
                            String nickname = cursor.getString(1);
                            if (nickname == null || nickname.trim().isEmpty()) continue;
                            JSONArray names = nicknamesByContact.get(contactId);
                            if (names == null) { names = new JSONArray(); nicknamesByContact.put(contactId, names); }
                            if (!containsIgnoreCase(names, nickname.trim())) names.put(nickname.trim());
                        }
                    }
                }
                JSONArray contacts = new JSONArray();
                Set<String> seen = new HashSet<>();
                try (Cursor cursor = getContentResolver().query(
                        ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                        new String[]{
                                ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
                                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                                ContactsContract.CommonDataKinds.Phone.NUMBER
                        },
                        null, null,
                        ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " COLLATE NOCASE ASC")) {
                    if (cursor != null) {
                        while (cursor.moveToNext() && contacts.length() < 1500) {
                            long contactId = cursor.getLong(0);
                            String name = cursor.getString(1);
                            String number = cursor.getString(2);
                            if (name == null || name.trim().isEmpty() || number == null || number.trim().isEmpty()) continue;
                            String key = contactId + "|" + number.replaceAll("[^0-9+]", "");
                            if (!seen.add(key)) continue;
                            JSONObject item = new JSONObject();
                            item.put("name", name.trim());
                            item.put("number", number.trim());
                            item.put("sourceId", String.valueOf(contactId));
                            item.put("nicknames", nicknamesByContact.get(contactId) == null
                                    ? new JSONArray() : nicknamesByContact.get(contactId));
                            contacts.put(item);
                        }
                    }
                }
                return contacts.toString();
            } catch (Exception error) {
                return "ERROR:" + (error.getMessage() == null ? "Contacts could not be read." : error.getMessage());
            }
        }

        private boolean containsIgnoreCase(JSONArray values, String candidate) {
            for (int i = 0; i < values.length(); i++) {
                if (candidate.equalsIgnoreCase(values.optString(i))) return true;
            }
            return false;
        }

        @JavascriptInterface public String getRecentNotifications() {
            String enabled = android.provider.Settings.Secure.getString(getContentResolver(), "enabled_notification_listeners");
            String component = new android.content.ComponentName(MainActivity.this, JarvisMediaNotificationListener.class).flattenToString();
            if (enabled == null || !enabled.contains(component)) {
                runOnUiThread(() -> {
                    try { startActivity(new Intent(android.provider.Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)); }
                    catch (ActivityNotFoundException error) { Toast.makeText(MainActivity.this, "Open Android Notification Access settings and enable J.A.R.V.I.S., Sir.", Toast.LENGTH_LONG).show(); }
                });
                return "PERMISSION_REQUIRED";
            }
            return JarvisMediaNotificationListener.getRecentNotifications().toString();
        }

        @JavascriptInterface public String getRecentCallLogs(int requestedLimit) {
            if (Build.VERSION.SDK_INT >= 23 && checkSelfPermission(Manifest.permission.READ_CALL_LOG) != PackageManager.PERMISSION_GRANTED) {
                runOnUiThread(() -> requestPermissions(new String[]{Manifest.permission.READ_CALL_LOG}, REQUEST_CALL_LOG));
                return "PERMISSION_REQUIRED";
            }
            int limit = Math.max(1, Math.min(50, requestedLimit));
            JSONArray calls = new JSONArray();
            try (Cursor cursor = getContentResolver().query(
                    CallLog.Calls.CONTENT_URI,
                    new String[]{
                            CallLog.Calls.CACHED_NAME,
                            CallLog.Calls.NUMBER,
                            CallLog.Calls.TYPE,
                            CallLog.Calls.DATE,
                            CallLog.Calls.DURATION
                    },
                    null, null, CallLog.Calls.DATE + " DESC")) {
                if (cursor != null) {
                    while (cursor.moveToNext() && calls.length() < limit) {
                        JSONObject item = new JSONObject();
                        String name = cursor.getString(0);
                        String number = cursor.getString(1);
                        int type = cursor.getInt(2);
                        item.put("name", name == null ? "" : name);
                        item.put("number", number == null ? "" : number);
                        item.put("type", type == CallLog.Calls.INCOMING_TYPE ? "incoming"
                                : type == CallLog.Calls.OUTGOING_TYPE ? "outgoing"
                                : type == CallLog.Calls.MISSED_TYPE ? "missed"
                                : type == CallLog.Calls.REJECTED_TYPE ? "rejected"
                                : type == CallLog.Calls.BLOCKED_TYPE ? "blocked" : "other");
                        item.put("date", cursor.getLong(3));
                        item.put("durationSeconds", cursor.getLong(4));
                        calls.put(item);
                    }
                }
                return calls.toString();
            } catch (Exception error) {
                return "ERROR:" + (error.getMessage() == null ? "Call history could not be read." : error.getMessage());
            }
        }

        @JavascriptInterface public String dialNumber(String requestedNumber) {
            String number = requestedNumber == null ? "" : requestedNumber.trim();
            if (!number.matches("\\+?[0-9][0-9 .()\\-]{5,24}")) {
                return "That phone number is invalid, Sir.";
            }
            Intent intent = new Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", number, null));
            runOnUiThread(() -> {
                try {
                    if (intent.resolveActivity(getPackageManager()) == null) {
                        Toast.makeText(MainActivity.this, "No compatible dialler is available, Sir.", Toast.LENGTH_LONG).show();
                        return;
                    }
                    startActivity(intent);
                } catch (ActivityNotFoundException error) {
                    Toast.makeText(MainActivity.this, "Android could not open the dialler, Sir.", Toast.LENGTH_LONG).show();
                }
            });
            return "Opening the dialler for your review. No call is placed automatically, Sir.";
        }

        @JavascriptInterface public String openSmsComposer(String requestedNumber, String requestedBody) {
            String number = requestedNumber == null ? "" : requestedNumber.trim();
            String body = requestedBody == null ? "" : requestedBody.trim();
            if (!number.matches("\\+?[0-9][0-9 .()\\-]{5,24}") || body.isEmpty() || body.length() > 2000) {
                return "A valid contact number and message of up to 2,000 characters are required, Sir.";
            }
            Intent intent = new Intent(Intent.ACTION_SENDTO, Uri.fromParts("smsto", number, null));
            intent.putExtra("sms_body", body);
            runOnUiThread(() -> {
                try {
                    if (intent.resolveActivity(getPackageManager()) == null) {
                        Toast.makeText(MainActivity.this, "No compatible messaging app is available, Sir.", Toast.LENGTH_LONG).show();
                        return;
                    }
                    startActivity(intent);
                } catch (ActivityNotFoundException error) {
                    Toast.makeText(MainActivity.this, "Android could not open the messaging app, Sir.", Toast.LENGTH_LONG).show();
                }
            });
            return "Opening your messaging app with the message ready. Review and press Send yourself, Sir.";
        }

        @JavascriptInterface public String shareText(String requestedText, String requestedTitle) {
            String text = requestedText == null ? "" : requestedText.trim();
            String title = requestedTitle == null ? "Share with" : requestedTitle.trim();
            if (text.isEmpty() || text.length() > 8000) return "Provide text of up to 8,000 characters to share, Sir.";
            Intent intent = new Intent(Intent.ACTION_SEND);
            intent.setType("text/plain");
            intent.putExtra(Intent.EXTRA_TEXT, text);
            Intent chooser = Intent.createChooser(intent, title.isEmpty() ? "Share with" : title);
            runOnUiThread(() -> {
                try { startActivity(chooser); }
                catch (ActivityNotFoundException error) {
                    Toast.makeText(MainActivity.this, "No app can receive shared text on this device, Sir.", Toast.LENGTH_LONG).show();
                }
            });
            return "Android share sheet opened. Choose any compatible installed app and complete the action yourself, Sir.";
        }

        @JavascriptInterface public String openWebUrl(String requestedUrl) {
            String value = requestedUrl == null ? "" : requestedUrl.trim();
            Uri uri;
            try { uri = Uri.parse(value); }
            catch (Exception error) { return "That link is invalid, Sir."; }
            String scheme = uri.getScheme();
            if (uri.getHost() == null || !("https".equalsIgnoreCase(scheme) || "http".equalsIgnoreCase(scheme))) {
                return "Only valid HTTP or HTTPS links can be opened, Sir.";
            }
            Intent intent = new Intent(Intent.ACTION_VIEW, uri);
            runOnUiThread(() -> {
                try {
                    if (intent.resolveActivity(getPackageManager()) == null) {
                        Toast.makeText(MainActivity.this, "No compatible browser is installed, Sir.", Toast.LENGTH_LONG).show();
                        return;
                    }
                    startActivity(intent);
                } catch (ActivityNotFoundException error) {
                    Toast.makeText(MainActivity.this, "Android could not open that link, Sir.", Toast.LENGTH_LONG).show();
                }
            });
            return "Opening the link in your selected browser, Sir.";
        }

        @JavascriptInterface public String setAlarm(int hour, int minute, String label) {
            if (hour < 0 || hour > 23 || minute < 0 || minute > 59) {
                return "That alarm time is invalid, Sir.";
            }
            Intent intent = new Intent(AlarmClock.ACTION_SET_ALARM);
            intent.putExtra(AlarmClock.EXTRA_HOUR, hour);
            intent.putExtra(AlarmClock.EXTRA_MINUTES, minute);
            intent.putExtra(AlarmClock.EXTRA_MESSAGE, label == null ? "JARVIS reminder" : label.trim());
            intent.putExtra(AlarmClock.EXTRA_SKIP_UI, false);
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

    private void startOfflineDictation() {
        try {
            Intent intent = new Intent(this, WakeWordService.class).setAction(WakeWordService.ACTION_DICTATION);
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(intent);
            else startService(intent);
            if (webView != null) webView.evaluateJavascript(
                    "if(typeof status==='function')status('Listening · private offline voice input','listening')", null);
            Toast.makeText(this, "JARVIS microphone ready. Speak your command.", Toast.LENGTH_SHORT).show();
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
        if (requestCode == REQUEST_WEB_AUDIO) {
            PermissionRequest pending = pendingWebAudioRequest;
            pendingWebAudioRequest = null;
            if (pending != null) {
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED
                        && webView != null && !isFinishing()) {
                    pending.grant(new String[]{PermissionRequest.RESOURCE_AUDIO_CAPTURE});
                } else {
                    pending.deny();
                    Toast.makeText(this, "Allow microphone access to use Live Voice, Sir.", Toast.LENGTH_LONG).show();
                }
            }
            return;
        }
        if (requestCode == REQUEST_CONTACTS) {
            boolean granted = grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED;
            Toast.makeText(this, granted
                    ? "Contacts access enabled. Say “import contacts” again, Sir."
                    : "Contacts access was not granted. You can still import a VCF contacts file, Sir.",
                    Toast.LENGTH_LONG).show();
            return;
        }
        if (requestCode == REQUEST_CALL_LOG) {
            boolean granted = grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED;
            Toast.makeText(this, granted
                    ? "Call history access enabled. Repeat your recent-calls command, Sir."
                    : "Call history access was not granted, Sir.",
                    Toast.LENGTH_LONG).show();
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
        // A foreground microphone service must outlive this Activity so wake-word
        // listening continues when the user presses Home or switches apps.
        // The persistent notification and WakeWordActivity provide explicit OFF controls.
        super.onStop();
    }

    private void restoreWakeWordServiceIfEnabled() {
        boolean enabled = getSharedPreferences("jarvis_wake_word", MODE_PRIVATE)
                .getBoolean("enabled", false);
        if (!enabled || checkSelfPermission(Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED || WakeWordService.isActive()) return;
        try {
            Intent service = new Intent(this, WakeWordService.class);
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(service);
            else startService(service);
        } catch (RuntimeException error) {
            android.util.Log.e("JARVIS", "Could not restore the opted-in wake-word service", error);
            Toast.makeText(this,
                    "Wake-word listening could not be restored. Open Wake-word microphone settings to retry, Sir.",
                    Toast.LENGTH_LONG).show();
        }
    }

    @Override protected void onDestroy() {
        if (wakeWordReceiver != null) { try { unregisterReceiver(wakeWordReceiver); } catch (Exception ignored) { } wakeWordReceiver = null; }
        if (textToSpeech != null) { textToSpeech.stop(); textToSpeech.shutdown(); }
        if (pendingWebAudioRequest != null) { try { pendingWebAudioRequest.deny(); } catch (Exception ignored) { } pendingWebAudioRequest = null; }
        if (webView != null) webView.destroy();
        super.onDestroy();
    }

    @Override public void onBackPressed() {
        if (webView != null && webView.canGoBack()) webView.goBack();
        else super.onBackPressed();
    }
}
