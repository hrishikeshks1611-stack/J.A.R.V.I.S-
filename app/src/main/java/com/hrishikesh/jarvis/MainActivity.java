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

public class MainActivity extends Activity {
    private static final int REQUEST_AUDIO_INPUT = 4102;
    private static final int REQUEST_FILE = 4103;
    private static final int REQUEST_CAMERA = 4104;
    private static final int REQUEST_LOCATION = 4105;
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
        webView.loadUrl("https://hrishikeshks1611-stack.github.io/J.A.R.V.I.S-/");
        restoreWakeWordServiceIfEnabled();
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
        if (webView != null) webView.destroy();
        super.onDestroy();
    }

    @Override public void onBackPressed() {
        if (webView != null && webView.canGoBack()) webView.goBack();
        else super.onBackPressed();
    }
}
