package com.hrishikesh.jarvis;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.speech.RecognizerIntent;
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
    private final Map<String, String[]> appPackages = new HashMap<>();

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(android.graphics.Color.rgb(5, 11, 24));
        getWindow().setNavigationBarColor(android.graphics.Color.rgb(5, 11, 24));
        registerApps();
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

    @Override public void onBackPressed() {
        if (webView != null && webView.canGoBack()) webView.goBack();
        else super.onBackPressed();
    }
}
