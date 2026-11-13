# J.A.R.V.I.S. — Fresh Build

Clean-room Android application baseline with a new application ID
(com.hrishikesh.jarvis.fresh), a new version line, no imported app data,
and no dependency on the previous JARVIS source tree.

## Current milestone
- Fresh Android project and package identity.
- Minimal native Android launch screen.
- CI workflow that builds and verifies a debug APK.
- Runtime capabilities are not declared complete until separately implemented
  and tested on Android 10 and a physical device.

## Planned capabilities
Voice personality and TTS selection; offline wake phrase recognition;
foreground microphone operation; app discovery and launch; camera capture;
background media controls; owner-device pairing; and a moving 3D ARC-reactor HUD.

## Data policy
No old conversations, databases, or device backups are imported. The app starts
with an empty local state.
