package com.hrishikesh.jarvis;

import android.Manifest;
import androidx.activity.ComponentActivity;
import android.content.ContentValues;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class CameraCaptureActivity extends ComponentActivity {
    public static final String EXTRA_SELFIE = "camera_selfie";
    private static final int REQUEST_CAMERA_PERMISSION = 9301;
    private PreviewView previewView;
    private TextView statusView;
    private ImageCapture imageCapture;
    private ProcessCameraProvider cameraProvider;
    private ExecutorService cameraExecutor;
    private boolean captureStarted = false;
    private boolean selfie = false;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        selfie = getIntent().getBooleanExtra(EXTRA_SELFIE, false);
        cameraExecutor = Executors.newSingleThreadExecutor();
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0xff050b18);
        previewView = new PreviewView(this);
        previewView.setImplementationMode(PreviewView.ImplementationMode.COMPATIBLE);
        root.addView(previewView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        statusView = new TextView(this);
        statusView.setText(selfie ? "Preparing front camera…" : "Preparing camera…");
        statusView.setTextColor(0xffffffff);
        statusView.setTextSize(18);
        statusView.setGravity(Gravity.CENTER);
        statusView.setBackgroundColor(0xaa050b18);
        FrameLayout.LayoutParams statusParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.TOP);
        statusParams.setMargins(20, 36, 20, 0);
        root.addView(statusView, statusParams);
        TextView cancel = new TextView(this);
        cancel.setText("CANCEL");
        cancel.setTextColor(0xffffffff);
        cancel.setTextSize(15);
        cancel.setGravity(Gravity.CENTER);
        cancel.setBackgroundColor(0xcc102c45);
        FrameLayout.LayoutParams cancelParams = new FrameLayout.LayoutParams(150, 54, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        cancelParams.bottomMargin = 36;
        root.addView(cancel, cancelParams);
        cancel.setOnClickListener(v -> finish());
        setContentView(root);
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) startCamera();
        else requestPermissions(new String[]{Manifest.permission.CAMERA}, REQUEST_CAMERA_PERMISSION);
    }

    private void startCamera() {
        statusView.setText(selfie ? "Front camera · capturing shortly…" : "Camera ready · capturing shortly…");
        com.google.common.util.concurrent.ListenableFuture<ProcessCameraProvider> providerFuture =
                ProcessCameraProvider.getInstance(this);
        providerFuture.addListener(() -> {
            try {
                cameraProvider = providerFuture.get();
                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(previewView.getSurfaceProvider());
                imageCapture = new ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY).build();
                CameraSelector selector = selfie ? CameraSelector.DEFAULT_FRONT_CAMERA : CameraSelector.DEFAULT_BACK_CAMERA;
                if (!cameraProvider.hasCamera(selector)) {
                    if (selfie && cameraProvider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA)) {
                        statusView.setText("No front camera found. Using the rear camera…");
                        selector = CameraSelector.DEFAULT_BACK_CAMERA;
                    } else {
                        fail("This device does not have the requested camera, Sir.");
                        return;
                    }
                }
                cameraProvider.unbindAll();
                cameraProvider.bindToLifecycle(this, selector, preview, imageCapture);
                previewView.postDelayed(this::capturePhoto, 1400);
            } catch (Exception error) {
                fail("JARVIS could not start the camera. Please try again, Sir.");
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void capturePhoto() {
        if (isFinishing() || captureStarted || imageCapture == null) return;
        captureStarted = true;
        statusView.setText("Capturing photo…");
        String fileName = "JARVIS_" + (selfie ? "selfie_" : "photo_") + System.currentTimeMillis() + ".jpg";
        ContentValues values = new ContentValues();
        values.put(MediaStore.Images.Media.DISPLAY_NAME, fileName);
        values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
        if (Build.VERSION.SDK_INT >= 29) values.put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/JARVIS");
        ImageCapture.OutputFileOptions options = new ImageCapture.OutputFileOptions.Builder(
                getContentResolver(), MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values).build();
        imageCapture.takePicture(options, cameraExecutor, new ImageCapture.OnImageSavedCallback() {
            @Override public void onImageSaved(@NonNull ImageCapture.OutputFileResults result) {
                Uri uri = result.getSavedUri();
                if (uri == null) {
                    runOnUiThread(() -> fail("Android did not return the saved photo, Sir."));
                    return;
                }
                getSharedPreferences("jarvis_camera", MODE_PRIVATE).edit().putString("last_photo_uri", uri.toString()).apply();
                runOnUiThread(() -> {
                    Toast.makeText(CameraCaptureActivity.this,
                            selfie ? "Selfie saved to Pictures/JARVIS, Sir." : "Photo saved to Pictures/JARVIS, Sir.",
                            Toast.LENGTH_LONG).show();
                    setResult(RESULT_OK);
                    finish();
                });
            }
            @Override public void onError(@NonNull ImageCaptureException error) {
                runOnUiThread(() -> fail("The photo could not be captured. Please try again, Sir."));
            }
        });
    }

    private void fail(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
        setResult(RESULT_CANCELED);
        finish();
    }

    @Override public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CAMERA_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) startCamera();
            else fail("Camera permission is needed to take a photo, Sir.");
        }
    }

    @Override protected void onDestroy() {
        if (cameraProvider != null) cameraProvider.unbindAll();
        if (cameraExecutor != null) cameraExecutor.shutdown();
        super.onDestroy();
    }
}
