package com.hrishikesh.jarvis;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;

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
        } finally {
            InstrumentationRegistry.getInstrumentation().runOnMainSync(activity::finish);
        }
    }
}
