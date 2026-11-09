package com.hrishikesh.jarvis;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;
import android.speech.RecognizerIntent;
import android.speech.tts.TextToSpeech;
import android.speech.tts.Voice;
import android.speech.tts.UtteranceProgressListener;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class MainActivity extends Activity {
    private static final int REQUEST_SPEECH = 4102;
    private WebView webView;
    private TextToSpeech textToSpeech;
    private boolean ttsReady = false;
    private boolean voiceChosen = false;
    private SharedPreferences voicePrefs;
    private String pendingSpeech = null;
    private final Map<String, String[]> appPackages = new HashMap<>();

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(android.graphics.Color.rgb(5, 11, 24));
        getWindow().setNavigationBarColor(android.graphics.Color.rgb(5, 11, 24));
        registerApps();
        voicePrefs = getSharedPreferences("jarvis_voice", MODE_PRIVATE);
        textToSpeech = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                // Restore an explicitly selected British voice first. Otherwise retain the
                // voice already configured as the Android TTS engine's system default.
                // Android does not expose reliable gender metadata for every installed voice.
                Voice selected = findVoice(voicePrefs.getString("voice_name", ""));
                if (selected == null) selected = textToSpeech.getDefaultVoice();
                if (selected != null && textToSpeech.setVoice(selected) != TextToSpeech.ERROR) {
                    voiceChosen = true;
                } else {
                    // Keep speech available using the system's configured English (UK) voice.
                    int languageResult = textToSpeech.setLanguage(Locale.UK);
                    voiceChosen = languageResult != TextToSpeech.LANG_MISSING_DATA
                            && languageResult != TextToSpeech.LANG_NOT_SUPPORTED;
                }
                textToSpeech.setSpeechRate(1.0f);
                textToSpeech.setPitch(1.0f);
                ttsReady = true;
                if (pendingSpeech != null) {
                    textToSpeech.speak(pendingSpeech, TextToSpeech.QUEUE_FLUSH, null, "JARVIS_STARTUP");
                    pendingSpeech = null;
                }
            }
        });
        webView = new WebView(this);
        webView.setBackgroundColor(android.graphics.Color.rgb(5, 11, 24));
        webView.getSettings().setJavaScriptEnabled(true);
        webView.getSettings().setDomStorageEnabled(true);
        webView.getSettings().setMediaPlaybackRequiresUserGesture(false);
        webView.getSettings().setAllowFileAccess(false);
        webView.getSettings().setAllowContentAccess(false);
        webView.addJavascriptInterface(new JarvisBridge(), "AndroidJarvis");
        webView.setWebChromeClient(new WebChromeClient());
        webView.setWebViewClient(new WebViewClient() {
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
        webView.loadUrl("https://hrishikeshks1611-stack.github.io/J.A.R.V.I.S-/");
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
        for (Voice voice : textToSpeech.getVoices()) if (name.equals(voice.getName()) && isBritish(voice)) return voice;
        return null;
    }

    private JSONArray availableBritishVoices() {
        JSONArray result = new JSONArray();
        if (textToSpeech == null || textToSpeech.getVoices() == null) return result;
        for (Voice voice : textToSpeech.getVoices()) {
            if (!isBritish(voice)) continue;
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


        @JavascriptInterface public String getAvailableVoices() {
            if (!ttsReady) return "[]";
            return availableBritishVoices().toString();
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
                if (!voiceChosen) return;
                textToSpeech.speak(message, TextToSpeech.QUEUE_FLUSH, null, "JARVIS_" + System.currentTimeMillis());
            });
        }

        @JavascriptInterface public void stopSpeaking() {
            runOnUiThread(() -> { if (textToSpeech != null) textToSpeech.stop(); });
        }

        @JavascriptInterface public void startVoiceRecognition() {
            runOnUiThread(() -> {
                Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
                intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
                intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-GB");
                intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak to J.A.R.V.I.S.");
                try { startActivityForResult(intent, REQUEST_SPEECH); }
                catch (ActivityNotFoundException e) {
                    Toast.makeText(MainActivity.this, "Speech recognition is unavailable on this device.", Toast.LENGTH_LONG).show();
                }
            });
        }
    }

    private String title(String value) {
        if (value == null || value.isEmpty()) return "that app";
        return value.substring(0, 1).toUpperCase(Locale.ROOT) + value.substring(1);
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_SPEECH && resultCode == RESULT_OK && data != null) {
            ArrayList<String> results = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
            if (results != null && !results.isEmpty()) {
                String spoken = org.json.JSONObject.quote(results.get(0));
                webView.evaluateJavascript("window.jarvisNative && window.jarvisNative.onSpeechResult(" + spoken + ")", null);
            }
        }
    }

    @Override protected void onDestroy() {
        if (textToSpeech != null) { textToSpeech.stop(); textToSpeech.shutdown(); }
        if (webView != null) webView.destroy();
        super.onDestroy();
    }

    @Override public void onBackPressed() {
        if (webView != null && webView.canGoBack()) webView.goBack();
        else super.onBackPressed();
    }
}
