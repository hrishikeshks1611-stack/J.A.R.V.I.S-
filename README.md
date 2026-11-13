# J.A.R.V.I.S. — Fresh Android Build

A clean-room Android application using package ID `com.hrishikesh.jarvis.fresh`.
It does not import old JARVIS conversations, databases, or backups.

## Implemented
- ARC-reactor launcher artwork and animated in-app reactor.
- User-started foreground voice listener with four wake phrases.
- Commands after the wake phrase for launching apps, opening the camera, and music search.
- Native Camera2 preview, front/rear camera switching, and JPEG photo capture.
- Photos saved to the app's private external Pictures folder.
- UK English TTS preference, selecting Algieba only if the installed engine exposes it.
- Unit tests for wake phrase matching and command parsing.
- CI build and APK verification.

## Runtime constraints
- Wake recognition uses Android's speech-recognition provider. Offline mode is requested but not guaranteed.
- The microphone listener must be started by the user and remains visible through a foreground notification.
- App launch visibility depends on Android package visibility rules and installed launchable activities.
- Music search opens a handler/search page; automatic playback and full media-session control are not yet implemented.
- Algieba availability depends on the installed TTS engine.
- Physical-device camera, wake-listener, and background behavior still require verification on the owner's Android 10 phone.

## Still to build
- Reliable offline wake-word detection.
- Expanded command handling and diagnostics.
- Background media-session controls.
- Owner-controlled encrypted device pairing and optional sync.
- Fresh local memory/conversation store.
- True 3D ARC reactor and interactive holographic panels.
- Expanded compatibility testing across Android versions.

## Data policy
No old app data is migrated. The application starts with a clean local state.
