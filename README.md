# J.A.R.V.I.S. — Fresh Android Build

Clean-room app ID: `com.hrishikesh.jarvis.fresh`. No old JARVIS conversations,
databases, or backups are imported.

## Implemented in source
- ARC-reactor launcher artwork and an animated OpenGL ES 2.0 3D reactor with rotating torus rings and a luminous core.
- Functional HUD-style panels grouping voice/system, device/media, and private-memory controls.
- User-started foreground wake listener for four agreed wake phrases.
- Command continuation for app launch, camera, music search, and media controls.
- Native Camera2 preview, front/rear switching, and JPEG capture saved to the app's private Pictures folder.
- UK English TTS preference; Algieba is selected only when available in the installed engine.
- Active media-session play/pause/next/previous commands, after the user enables JARVIS in Android Notification Access settings.
- Fresh local conversation history encrypted with AES-GCM; the AES key is held in Android Keystore.
- UI to review and clear local memory.
- Unit tests for command parsing and wake phrase matching.

## Runtime limits / owner setup
- Wake listening is user-started and shown as a foreground notification. Recognition uses Android's installed provider; offline recognition is requested but not guaranteed.
- To control music already playing in another app, enable **J.A.R.V.I.S. media controls** under Android Settings → Notification access.
- Music search opens a search page; it does not guarantee automatic track playback.
- App launch visibility depends on Android package visibility and installed launchable activities.
- Algieba depends on the installed TTS engine.
- Photos are stored in app-specific external Pictures storage; they are not automatically published to the public gallery.
- Physical-device camera, wake listener, media session and background behavior still require testing on the owner's Android 10 phone.

## Still to build
- Dedicated offline hotword detection.
- Expanded device command support and diagnostics.
- Owner-controlled encrypted pairing and optional multi-device sync.
- True 3D ARC reactor and functional holographic panels.
- Broader Android compatibility and physical-device validation.

## Data policy
The encrypted conversation database starts empty on a fresh install. App backup is disabled.
