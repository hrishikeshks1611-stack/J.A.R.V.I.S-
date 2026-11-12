# JARVIS Rebirth 6.0 — integration audit

Date: 2026-11-12

## New integration branch

This branch was created as `jarvis-rebirth-6.0` from `jarvis-6.0-development`. The previous production branch and the 6.0 development branch are intentionally left unchanged.

## What the two existing code lines contain

### Production `main`

- Existing production web interface and animated central ARC reactor.
- Native Android phone/TV project with separate build flavors.
- CameraX capture implementation and Android camera permissions.
- Production UI compatibility work for the currently installed Build 39 app.
- Recent wake-phrase parser and native bridge work.

### `jarvis-6.0-development`

- Shared JARVIS web interface and Node API backend.
- Gemini model fallback, Algieba speech path, conversation context and personality.
- Device pairing, owner recovery, shared conversation history and memory APIs.
- File/document generation and download endpoints.
- YouTube Music search and handoff.
- Native Vosk offline microphone/wake-word service and microphone settings activity.
- Phone and Android TV Gradle flavors.

## Integration risks found

1. The branches have diverged substantially: development is 92 commits ahead and 224 commits behind production at the audit point. A blind merge is not a safe integration strategy.
2. The 6.0 development Android manifest and Gradle dependencies do not currently include the production CameraX implementation. Camera must be ported and tested before the phone build can be called feature-complete.
3. The Android TV flavor uses a separate application ID suffix and needs its own installation, remote-navigation, audio and lifecycle tests.
4. The laptop edition is currently the shared web/PWA interface, not a native Windows executable. It needs a deliberate desktop layout and browser/PWA acceptance test.
5. The current Android manifest sets `allowBackup=false`. The shared backup endpoint covers cloud conversation history and saved memories, not every local preference, temporary cache, microphone setting or device session. A backup/export and re-pair plan is required before replacing any existing installation.
6. The open production wake-word compatibility PR's emulator workflow uninstalls the baseline app before installing the candidate; it therefore does not prove in-place update or local-data preservation. The new clean-install plan will use a disposable emulator and separately test data export/restore.

## Rebirth release gates

- Integrate production UI/native camera work with the 6.0 intelligence/backend work without losing either feature set.
- Make one authoritative shared web client and backend contract for phone, TV and laptop.
- Keep the central moving 3D ARC reactor and blue holographic HUD as required interface elements.
- Run the complete Node test suite and Android phone/TV unit/compile checks.
- Produce separate phone and TV APKs and a laptop PWA/web deployment.
- Test fresh install and core workflows on the actual Android phone, actual Mi Android TV, and HP Windows 10 laptop before any old installation is removed.
- Export/verify owner data and confirm recovery before replacement.

## Explicit non-claims

A passing CI build is not proof of real-device wake-word accuracy, camera operation, TV remote usability, or Windows browser compatibility. Those remain device acceptance tests until performed.
