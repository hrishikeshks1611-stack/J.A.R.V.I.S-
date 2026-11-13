package com.hrishikesh.jarvis;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class MainActivityLaunchTest {
    @Test
    public void launcherIdentityAndMainActivityLaunch() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();

        CharSequence label = context.getPackageManager()
                .getApplicationLabel(context.getApplicationInfo());
        assertEquals("J.A.R.V.I.S", label.toString());

        Drawable launcherIcon = context.getPackageManager()
                .getApplicationIcon(context.getPackageName());
        assertNotNull("The installed app must expose its ARC reactor launcher icon", launcherIcon);

        String html;
        try (InputStream input = context.getAssets().open("jarvis.html");
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int count;
            while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
            html = output.toString("UTF-8");
        }
        assertTrue("The bundled page must use the exact J.A.R.V.I.S title", html.contains("<title>J.A.R.V.I.S</title>"));
        assertTrue("Alfred must remain a personality inside J.A.R.V.I.S", html.contains("<option value=\\"alfred\\""));
        assertTrue("The HUD must include perspective-based ARC reactor depth", html.contains("perspective:900px"));
        assertTrue("The ARC reactor must preserve 3D transforms", html.contains("transform-style:preserve-3d"));
        assertTrue("Wake-word controls must remain in the bundled interface", html.contains("id=\\"wakeWordButton\\""));
        assertTrue("Music search must remain in the bundled interface", html.contains("lookupYoutubeMusicTrack"));

        Intent launch = new Intent(context, MainActivity.class);
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        Activity activity = InstrumentationRegistry.getInstrumentation()
                .startActivitySync(launch);
        try {
            assertNotNull(activity);
            assertFalse("MainActivity must remain open after launch", activity.isFinishing());

            View content = activity.findViewById(android.R.id.content);
            assertNotNull(content);
            assertTrue("MainActivity must host a view container", content instanceof ViewGroup);
            ViewGroup container = (ViewGroup) content;
            assertTrue("The J.A.R.V.I.S interface view must be attached", container.getChildCount() > 0);
            assertTrue("The main interface must be hosted in WebView",
                    container.getChildAt(0) instanceof WebView);

            WebView webView = (WebView) container.getChildAt(0);
            assertEquals("https://hrishikeshks1611-stack.github.io/J.A.R.V.I.S-/", webView.getUrl());

            boolean interfaceReady = false;
            for (int attempt = 0; attempt < 20 && !interfaceReady; attempt++) {
                CountDownLatch evaluated = new CountDownLatch(1);
                AtomicReference<String> result = new AtomicReference<>();
                InstrumentationRegistry.getInstrumentation().runOnMainSync(() ->
                        webView.evaluateJavascript(
                                "document.readyState === 'complete' && document.title === 'J.A.R.V.I.S' && !!document.querySelector('.reactor-core')",
                                value -> { result.set(value); evaluated.countDown(); }
                        )
                );
                if (evaluated.await(1, TimeUnit.SECONDS) && "true".equals(result.get())) {
                    interfaceReady = true;
                } else {
                    Thread.sleep(250);
                }
            }
            assertTrue("The bundled J.A.R.V.I.S page and ARC reactor must render", interfaceReady);
        } finally {
            InstrumentationRegistry.getInstrumentation().runOnMainSync(activity::finish);
        }
    }
}
