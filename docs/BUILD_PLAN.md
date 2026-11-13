# Build plan

1. Establish a reproducible Android build and package identity.
2. Add deterministic TTS voice selection and a voice diagnostics screen.
3. Implement an offline wake-word engine with microphone lifecycle controls.
4. Add Android app discovery and explicit launch actions.
5. Add camera capture with permission and storage tests.
6. Add background audio/media controls and concurrency tests.
7. Add owner-device pairing and encrypted optional sync (fresh identity).
8. Build the animated 3D ARC-reactor HUD and command panels.
9. Test Android 10 compatibility, current Android versions, and physical devices.

Each capability must have an automated test where possible and a clearly stated
manual-device verification step where emulator testing cannot establish reality.
