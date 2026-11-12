package com.hrishikesh.jarvis;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.media.audiofx.AcousticEchoCanceler;
import android.media.audiofx.NoiseSuppressor;
import android.os.SystemClock;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class WakeWordServiceSmokeTest {
    @Test
    public void recognizesAllRequestedWakePhraseForms() {
        org.junit.Assert.assertEquals("", WakeWordService.extractWakeCommand("Hey JARVIS"));
        org.junit.Assert.assertEquals("", WakeWordService.extractWakeCommand("Hello, JARVIS!"));
        org.junit.Assert.assertEquals("", WakeWordService.extractWakeCommand("Wake up, JARVIS"));
        org.junit.Assert.assertEquals("are you there", WakeWordService.extractWakeCommand("JARVIS, are you there?"));
        org.junit.Assert.assertEquals("open the lights", WakeWordService.extractWakeCommand("Hey JARVIS, open the lights"));
        org.junit.Assert.assertNull(WakeWordService.extractWakeCommand("jarvisian system"));
    }


    @Test
    public void androidVoiceMicrophoneCanCapturePcmFrames() throws Exception {
        android.app.Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        Context context = instrumentation.getTargetContext();
        instrumentation.getUiAutomation().grantRuntimePermission(
                context.getPackageName(), Manifest.permission.RECORD_AUDIO);

        int rate = 16000;
        int minimum = AudioRecord.getMinBufferSize(rate,
                AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
        org.junit.Assert.assertTrue("Android must provide a valid 16 kHz microphone buffer", minimum > 0);

        AudioRecord recorder = new AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION,
                rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT,
                Math.max(minimum * 2, 6400));
        org.junit.Assert.assertEquals("Voice microphone must initialize",
                AudioRecord.STATE_INITIALIZED, recorder.getState());

        NoiseSuppressor ns = null;
        AcousticEchoCanceler aec = null;
        try {
            int session = recorder.getAudioSessionId();
            if (NoiseSuppressor.isAvailable()) {
                ns = NoiseSuppressor.create(session);
                if (ns != null) ns.setEnabled(true);
            }
            if (AcousticEchoCanceler.isAvailable()) {
                aec = AcousticEchoCanceler.create(session);
                if (aec != null) aec.setEnabled(true);
            }

            recorder.startRecording();
            org.junit.Assert.assertEquals(AudioRecord.RECORDSTATE_RECORDING, recorder.getRecordingState());
            byte[] pcm = new byte[3200];
            int captured = 0;
            long deadline = SystemClock.elapsedRealtime() + 5000;
            while (captured == 0 && SystemClock.elapsedRealtime() < deadline) {
                captured = recorder.read(pcm, 0, pcm.length, AudioRecord.READ_NON_BLOCKING);
                if (captured == 0) SystemClock.sleep(20);
            }
            org.junit.Assert.assertTrue("Android must deliver PCM frames from the microphone; read result=" + captured,
                    captured > 0);
        } finally {
            try { recorder.stop(); } catch (Exception ignored) { }
            if (ns != null) { try { ns.setEnabled(false); ns.release(); } catch (Exception ignored) { } }
            if (aec != null) { try { aec.setEnabled(false); aec.release(); } catch (Exception ignored) { } }
            recorder.release();
        }
    }

    @Test
    public void microphoneServiceIsDeclaredAsPrivateForegroundService() throws Exception {
        android.content.Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        android.content.pm.ServiceInfo info = context.getPackageManager().getServiceInfo(
                new android.content.ComponentName(context, WakeWordService.class),
                android.content.pm.PackageManager.GET_META_DATA);
        org.junit.Assert.assertFalse("Wake-word microphone service must not be exported", info.exported);
        if (Build.VERSION.SDK_INT >= 29) {
            org.junit.Assert.assertTrue("Service must declare microphone foreground-service type",
                    (info.foregroundServiceType & android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE) != 0);
        }
    }
}
