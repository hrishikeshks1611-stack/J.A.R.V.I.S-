package com.hrishikesh.jarvis;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.media.Image;
import android.media.ImageReader;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.view.Gravity;
import android.view.Surface;
import android.view.TextureView;
import android.util.Size;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.Locale;

public final class CameraActivity extends Activity {
    private TextureView preview;
    private TextView status;
    private CameraDevice camera;
    private CameraCaptureSession session;
    private ImageReader imageReader;
    private HandlerThread thread;
    private Handler handler;
    private String cameraId;
    private boolean useFrontCamera;
    private CaptureRequest.Builder previewRequest;
    private Size previewSize;
    private Size jpegSize;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().getDecorView().setSystemUiVisibility(0);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(3, 9, 20));
        status = new TextView(this);
        status.setText("J.A.R.V.I.S. CAMERA");
        status.setTextColor(Color.rgb(75, 210, 255));
        status.setGravity(Gravity.CENTER);
        status.setPadding(8, 14, 8, 14);
        root.addView(status, new LinearLayout.LayoutParams(-1, -2));
        preview = new TextureView(this);
        root.addView(preview, new LinearLayout.LayoutParams(-1, 0, 1f));
        LinearLayout controls = new LinearLayout(this);
        controls.setGravity(Gravity.CENTER);
        Button capture = button("CAPTURE");
        capture.setOnClickListener(v -> capturePhoto());
        Button flip = button("SWITCH CAMERA");
        flip.setOnClickListener(v -> switchCamera());
        Button close = button("CLOSE");
        close.setOnClickListener(v -> finish());
        controls.addView(flip, new LinearLayout.LayoutParams(0, -2, 1f));
        controls.addView(capture, new LinearLayout.LayoutParams(0, -2, 1f));
        controls.addView(close, new LinearLayout.LayoutParams(0, -2, 1f));
        root.addView(controls, new LinearLayout.LayoutParams(-1, -2));
        setContentView(root);
        startThread();
        preview.setSurfaceTextureListener(surfaceListener);
    }

    private Button button(String label) {
        Button b = new Button(this);
        b.setText(label);
        b.setTextColor(Color.rgb(75, 210, 255));
        b.setTextSize(11);
        b.setAllCaps(false);
        return b;
    }

    @Override protected void onResume() {
        super.onResume();
        startThread();
        if (preview != null && preview.isAvailable()) openCamera();
        else if (preview != null) preview.setSurfaceTextureListener(surfaceListener);
    }

    @Override protected void onPause() {
        closeCamera();
        stopThread();
        super.onPause();
    }

    private final TextureView.SurfaceTextureListener surfaceListener = new TextureView.SurfaceTextureListener() {
        @Override public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) { openCamera(); }
        @Override public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) { }
        @Override public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) { return true; }
        @Override public void onSurfaceTextureUpdated(SurfaceTexture surface) { }
    };

    private void startThread() {
        if (thread != null) return;
        thread = new HandlerThread("JARVIS-Camera");
        thread.start();
        handler = new Handler(thread.getLooper());
    }

    private void stopThread() {
        if (thread == null) return;
        thread.quitSafely();
        try { thread.join(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        thread = null;
        handler = null;
    }

    private void openCamera() {
        if (isFinishing() || camera != null) return;
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.CAMERA}, 92);
            return;
        }
        try {
            CameraManager manager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
            cameraId = chooseCamera(manager, useFrontCamera);
            if (cameraId == null) { setStatus("NO COMPATIBLE CAMERA FOUND"); return; }
            CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraId);
            StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            if (map == null) { setStatus("CAMERA OUTPUTS NOT SUPPORTED"); return; }
            previewSize = chooseSize(map.getOutputSizes(SurfaceTexture.class), 1280, 720);
            jpegSize = chooseSize(map.getOutputSizes(android.graphics.ImageFormat.JPEG), 1920, 1080);
            if (previewSize == null || jpegSize == null) { setStatus("CAMERA RESOLUTION UNAVAILABLE"); return; }
            manager.openCamera(cameraId, new CameraDevice.StateCallback() {
                @Override public void onOpened(CameraDevice device) {
                    camera = device;
                    createPreviewSession();
                }
                @Override public void onDisconnected(CameraDevice device) { device.close(); camera = null; }
                @Override public void onError(CameraDevice device, int error) {
                    device.close(); camera = null; setStatus("CAMERA ERROR " + error);
                }
            }, handler);
            setStatus(useFrontCamera ? "FRONT CAMERA STARTING" : "REAR CAMERA STARTING");
        } catch (SecurityException e) { setStatus("CAMERA PERMISSION REQUIRED"); }
        catch (Exception e) { setStatus("CAMERA START FAILED: " + e.getClass().getSimpleName()); }
    }

    private String chooseCamera(CameraManager manager, boolean front) throws CameraAccessException {
        for (String id : manager.getCameraIdList()) {
            CameraCharacteristics c = manager.getCameraCharacteristics(id);
            Integer facing = c.get(CameraCharacteristics.LENS_FACING);
            if (facing != null && facing == (front ? CameraCharacteristics.LENS_FACING_FRONT : CameraCharacteristics.LENS_FACING_BACK)) return id;
        }
        if (front) return chooseCamera(manager, false);
        return null;
    }

    private void createPreviewSession() {
        try {
            SurfaceTexture texture = preview.getSurfaceTexture();
            if (texture == null || camera == null) return;
            texture.setDefaultBufferSize(previewSize.getWidth(), previewSize.getHeight());
            Surface previewSurface = new Surface(texture);
            imageReader = ImageReader.newInstance(jpegSize.getWidth(), jpegSize.getHeight(), android.graphics.ImageFormat.JPEG, 2);
            imageReader.setOnImageAvailableListener(reader -> {
                Image image = reader.acquireNextImage();
                if (image == null) return;
                try { saveImage(image); }
                finally { image.close(); }
            }, handler);
            previewRequest = camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            previewRequest.addTarget(previewSurface);
            camera.createCaptureSession(Arrays.asList(previewSurface, imageReader.getSurface()),
                    new CameraCaptureSession.StateCallback() {
                @Override public void onConfigured(CameraCaptureSession configured) {
                    if (camera == null) return;
                    session = configured;
                    try {
                        previewRequest.set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO);
                        session.setRepeatingRequest(previewRequest.build(), null, handler);
                        setStatus(useFrontCamera ? "FRONT CAMERA READY" : "REAR CAMERA READY");
                    } catch (CameraAccessException e) { setStatus("PREVIEW FAILED"); }
                }
                @Override public void onConfigureFailed(CameraCaptureSession failed) { setStatus("CAMERA PREVIEW FAILED"); }
            }, handler);
        } catch (Exception e) { setStatus("CAMERA PREVIEW ERROR"); }
    }

    private Size chooseSize(Size[] choices, int targetWidth, int targetHeight) {
        if (choices == null || choices.length == 0) return null;
        Size best = choices[0];
        long bestScore = Long.MAX_VALUE;
        for (Size candidate : choices) {
            long score = Math.abs(candidate.getWidth() - targetWidth) * 2L
                    + Math.abs(candidate.getHeight() - targetHeight) * 2L;
            if (candidate.getWidth() > 2560 || candidate.getHeight() > 1440) score += 100000;
            if (score < bestScore) { bestScore = score; best = candidate; }
        }
        return best;
    }

    private void capturePhoto() {
        if (camera == null || session == null || imageReader == null) {
            setStatus("CAMERA IS NOT READY");
            return;
        }
        try {
            CaptureRequest.Builder still = camera.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            still.addTarget(imageReader.getSurface());
            still.set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO);
            still.set(CaptureRequest.JPEG_ORIENTATION, jpegOrientation());
            session.capture(still.build(), new CameraCaptureSession.CaptureCallback() {
                @Override public void onCaptureCompleted(CameraCaptureSession s, CaptureRequest r,
                        android.hardware.camera2.TotalCaptureResult result) {
                    setStatus("PHOTO CAPTURED • SAVING");
                }
            }, handler);
        } catch (Exception e) { setStatus("PHOTO CAPTURE FAILED"); }
    }

    private int jpegOrientation() {
        int rotation = getWindowManager().getDefaultDisplay().getRotation();
        int degrees = rotation == Surface.ROTATION_90 ? 90 : rotation == Surface.ROTATION_180 ? 180 : rotation == Surface.ROTATION_270 ? 270 : 0;
        return (useFrontCamera ? (360 - degrees) % 360 : degrees);
    }

    private void saveImage(Image image) {
        try {
            File dir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
            if (dir == null) throw new IllegalStateException("Storage unavailable");
            if (!dir.exists() && !dir.mkdirs()) throw new IllegalStateException("Cannot create photo folder");
            String stamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
            File file = new File(dir, "JARVIS_" + stamp + ".jpg");
            ByteBuffer buffer = image.getPlanes()[0].getBuffer();
            byte[] bytes = new byte[buffer.remaining()];
            buffer.get(bytes);
            try (FileOutputStream output = new FileOutputStream(file)) { output.write(bytes); output.flush(); }
            runOnUiThread(() -> {
                setStatus("PHOTO SAVED • " + file.getName());
                Toast.makeText(this, "Photo saved in JARVIS/Pictures", Toast.LENGTH_SHORT).show();
            });
        } catch (Exception e) { runOnUiThread(() -> setStatus("PHOTO SAVE FAILED")); }
    }

    private void switchCamera() {
        useFrontCamera = !useFrontCamera;
        closeCamera();
        openCamera();
    }

    private void closeCamera() {
        if (session != null) { try { session.close(); } catch (Exception ignored) { } session = null; }
        if (camera != null) { try { camera.close(); } catch (Exception ignored) { } camera = null; }
        if (imageReader != null) { try { imageReader.close(); } catch (Exception ignored) { } imageReader = null; }
    }

    private void setStatus(String value) {
        if (status != null) runOnUiThread(() -> status.setText(value));
    }

    @Override public void onRequestPermissionsResult(int request, String[] permissions, int[] grants) {
        super.onRequestPermissionsResult(request, permissions, grants);
        if (request == 92 && grants.length > 0 && grants[0] == PackageManager.PERMISSION_GRANTED) openCamera();
        else if (request == 92) setStatus("CAMERA PERMISSION DENIED");
    }
}
