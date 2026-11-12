package com.hrishikesh.jarvis;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.SystemClock;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class CameraCaptureCloudTest {
    @Test
    public void rearCameraCapturesAndPersistsPhotoOnPhysicalDevice() throws Exception {
        android.app.Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        Context context = instrumentation.getTargetContext();
        Assume.assumeFalse("Physical-device camera validation runs in Firebase Test Lab, not the emulator.",
                android.os.Build.FINGERPRINT.startsWith("generic"));
        instrumentation.getUiAutomation().grantRuntimePermission(context.getPackageName(), Manifest.permission.CAMERA);
        context.getSharedPreferences("jarvis_camera", Context.MODE_PRIVATE).edit().remove("last_photo_uri").commit();
        context.startActivity(new Intent(context, CameraCaptureActivity.class)
                .putExtra(CameraCaptureActivity.EXTRA_SELFIE, false)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        String saved = waitForPhoto(context);
        assertSavedPhoto(context, saved, "rear-camera photo");
    }

    @Test
    public void frontCameraCapturesAndPersistsSelfieOnPhysicalDevice() throws Exception {
        android.app.Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        Context context = instrumentation.getTargetContext();
        Assume.assumeFalse("Physical-device camera validation runs in Firebase Test Lab, not the emulator.",
                android.os.Build.FINGERPRINT.startsWith("generic"));
        instrumentation.getUiAutomation().grantRuntimePermission(context.getPackageName(), Manifest.permission.CAMERA);
        context.getSharedPreferences("jarvis_camera", Context.MODE_PRIVATE).edit().remove("last_photo_uri").commit();
        context.startActivity(new Intent(context, CameraCaptureActivity.class)
                .putExtra(CameraCaptureActivity.EXTRA_SELFIE, true)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        String saved = waitForPhoto(context);
        assertSavedPhoto(context, saved, "front-camera selfie");
    }

    private String waitForPhoto(Context context) throws Exception {
        long deadline = SystemClock.elapsedRealtime() + 25000;
        while (SystemClock.elapsedRealtime() < deadline) {
            String saved = context.getSharedPreferences("jarvis_camera", Context.MODE_PRIVATE)
                    .getString("last_photo_uri", null);
            if (saved != null) return saved;
            SystemClock.sleep(250);
        }
        return null;
    }

    private void assertSavedPhoto(Context context, String saved, String description) {
        org.junit.Assert.assertNotNull("CameraX should capture and save a " + description, saved);
        Uri uri = Uri.parse(saved);
        try (android.database.Cursor cursor = context.getContentResolver().query(uri,
                new String[]{android.provider.MediaStore.Images.Media.SIZE}, null, null, null)) {
            org.junit.Assert.assertNotNull("Saved " + description + " must be present in MediaStore", cursor);
            org.junit.Assert.assertTrue("Saved " + description + " row must exist", cursor.moveToFirst());
            org.junit.Assert.assertTrue("Saved " + description + " must not be empty", cursor.getLong(0) > 0);
        }
    }
}
