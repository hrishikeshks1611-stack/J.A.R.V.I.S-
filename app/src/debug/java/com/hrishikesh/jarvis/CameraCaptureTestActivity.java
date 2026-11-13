package com.hrishikesh.jarvis;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

/** Debug-only, shell-restricted trampoline that invokes the private production camera activity. */
public final class CameraCaptureTestActivity extends Activity {
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        boolean selfie = getIntent().getBooleanExtra(CameraCaptureActivity.EXTRA_SELFIE, false);
        Intent capture = new Intent(this, CameraCaptureActivity.class);
        capture.putExtra(CameraCaptureActivity.EXTRA_SELFIE, selfie);
        Log.i("CameraCaptureTest", "Launching private CameraX activity; selfie=" + selfie);
        startActivityForResult(capture, 9401);
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 9401) {
            Log.i("CameraCaptureTest", "CameraX activity returned resultCode=" + resultCode);
            setResult(resultCode);
            finish();
        }
    }
}
