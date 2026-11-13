# J.A.R.V.I.S. — Fresh Android Build

Application ID: `com.hrishikesh.jarvis.fresh`. This is a clean-room app identity. It does not import old JARVIS conversations, databases, or backups.

## Implemented in source
- ARC-reactor launcher artwork and a rotating OpenGL ES 2.0 3D reactor with torus rings and a luminous core.
- Functional HUD-style panels grouping voice/system, device/media, and private-memory controls.
- User-started foreground wake listener for “Hey JARVIS”, “Wake up, JARVIS”, “Hello JARVIS”, and “JARVIS, are you there?”
- On Android 12+ the listener prefers Android's on-device speech recognizer when one is available; otherwise it uses the installed Android recognition provider and requests offline recognition.
- Command continuation after a wake phrase for app launch, camera, music search, and media controls.
- Native Camera2 preview with supported output-size selection, front/rear switching, and JPEG capture saved in the app's private Pictures folder.
- Strict Alfred voice gate: only an explicitly identified male en-GB Algieba/UK male voice is permitted; otherwise all spoken output is muted (no engine-default or female fallback).
- Active media-session play/pause/next/previous commands after the owner enables JARVIS in Android Notification Access settings.
- Fresh local conversation history encrypted with AES-GCM; the AES key is held by Android Keystore.
- UI to review and clear local memory.
- Voice commands for app launch, camera, music search, and media-session controls.

## Runtime limits
- Wake listening must be started by the user and is shown as a foreground notification. Android 10 does not expose the modern on-device recognizer API; offline behavior there depends on the installed speech provider honoring Android's offline preference.
- This is speech-recognition-based wake listening, not a dedicated low-power always-on hotword DSP. Battery use and recognition reliability depend on the phone and recognition provider.
- Android may restrict background microphone operation under battery-management policies. The service can be stopped by the system or user.
- To control music already playing in another app, enable **J.A.R.V.I.S. media controls** under Android Settings → Notification access.
- Music search opens YouTube Music search; it does not guarantee automatic track playback. Playback control works only when a compatible active media session is exposed.
- App launch visibility depends on Android package visibility and installed launchable activities.
- Algieba depends on the installed TTS engine.
- Photos are stored in app-specific external Pictures storage and are not automatically published to the public gallery.
- Build/test success is not a substitute for physical-device validation of microphone, camera, media sessions, and background behavior.

## Still to build
- Dedicated low-power offline hotword engine for dependable always-on wake detection, including Android 10.
- Owner-controlled encrypted pairing and optional multi-device sync, including a Windows companion.
- Expanded device commands, setup diagnostics, and broader Android compatibility.
- Physical-device validation on the owner's Android 10 phone.

## Data policy
The encrypted conversation database starts empty on a fresh install. Android app backup is disabled.


## Voice guarantee and current limits
- JARVIS will not intentionally fall back to a female or unclassified TTS voice. If the phone's installed TTS engine does not expose a qualifying UK male voice, JARVIS remains silent and reports that the Alfred voice is unavailable. The exact Alfred timbre and phrasing cannot be guaranteed by Android TTS alone.
- Office engine, Education engine, and document-attachment ingestion are not present in this source branch; they are not included in this APK.
- Launcher icon and splash use a circular multi-ring ARC reactor design, with no triangular centrepiece.
