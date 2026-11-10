# J.A.R.V.I.S. 5.0 — unified engineering plan

This document is the release acceptance checklist. A capability is not considered complete merely because its UI exists or a workflow succeeds.

## Current implementation status (development branch)

Implemented and covered by automated tests:
- Atomic Redis/Lua append for completed chat turns to prevent stale read/replace writes from silently losing another device's turn.
- Explicit saved-memory store with add, edit, list, delete, and clear operations; bounded to 100 notes of at most 1,000 characters each.
- Saved memories are included as bounded context (latest 15 notes, 300 characters each) and are never automatically created from conversations.
- Saved-memory UI includes edit, delete, clear, and JSON export-to-clipboard controls, plus a warning not to store credentials or highly sensitive information.
- Authenticated full backup export and owner-only restore for the recent shared conversation and saved memories, with format/version checks, bounded validation, explicit destructive-action confirmation, and an atomic two-key Redis restore.
- Owner transfer between paired devices, allowing a new device to become owner before a future app reinstall.
- Offline command fallback is connected to the chat failure path for local time/date, greetings, known website lookups, and location when permissioned. Native Android app launches, alarm requests, and calendar-event requests are handled locally and appended to shared conversation history; Clock/Calendar actions require user confirmation.
- Automated tests cover shared-store bounds, atomic append invocation, memory lifecycle, owner transfer, backup export/restore validation and atomicity, API authorization/CORS, explicit clear confirmation, native bridge contracts, Android version metadata, APK release safety, and inline frontend JavaScript syntax.
- Android version metadata is set to versionCode 500 and versionName 5.0.0 so a correctly signed release can advance beyond the existing versionCode 1.

Still not complete or not physically verified:
- High-volume real Upstash concurrency stress test; CI uses mocked Redis responses, but the actual atomic append, memory add/edit/delete, and owner-transfer Lua scripts have passed isolated smoke tests on temporary keys in the connected Upstash database. All temporary QA keys were deleted afterward.
- Continuous wake-word listening and foreground microphone service.
- Native MediaProjection screen mirroring.
- Background notification scheduling, recurring reminders, and automatic reminder delivery. Basic alarm handoff and calendar-event creation are implemented through Android Clock/Calendar confirmation screens; the user must review and save them.
- A local language model; offline fallback is command-only, not offline conversational AI.
- Stable Android signing key and in-place update path.
- Physical testing on the user's phone and Android TV.

## Android signing: verified blocker

- The existing Build 39 phone and TV APKs were signed with certificate SHA-256 fingerprint `57:11:FB:FF:0E:96:4B:83:5A:DC:DF:83:60:27:A5:91:27:97:A5:FD:ED:DB:E3:D4:63:53:0E:D8:8F:50:57:D4`.
- Later Build 41+ APKs were signed with a different certificate: `4B:BE:66:C7:87:56:7A:27:8D:A0:DD:5A:00:8B:24:AA:16:3B:D3:87:D3:3B:5D:87:9F:E8:46:21:4D:EA:DA:35`.
- The Android workflow builds debug variants on hosted runners and contains no persistent signing configuration or keystore secret. No keystore file is present in the repository. A certificate fingerprint is public information and cannot recreate its private signing key.
- Android will reject a same-package APK signed by the later/different key as an in-place update. Raising versionCode does not fix a signing mismatch.
- Therefore, do not publish a new APK as an update or tell the user to uninstall the installed Build 39 app. The original signing keystore must first be recovered; otherwise a fresh install or separate package identity would be required and needs the user's explicit approval.
- The current installed WebView shells can still load website changes from GitHub Pages. New native bridge methods, however, require a compatible APK and will not appear through a website-only update.

This branch is not production. Keep it isolated until the remaining acceptance tests, release-signing decision, and deployment verification are complete.

## Preserve throughout
- Gemini TTS model, Algieba voice, and en-GB delivery remain the primary online voice.
- Preserve the British butler persona, animated reactor/HUD, attachments, local clock and permissioned location, device pairing, and all three form factors.
- Keep Gemini credentials and Upstash credentials server-side.
- Keep the current production deployment and installed APKs usable during development.

## 2.0 — durable shared memory and reliability
- Replace the single blind read/modify/write conversation update with atomic or version-checked writes so concurrent devices cannot silently erase each other's turns.
- Add durable user-approved memory records separately from recent chat history; include review, edit, delete, export, and retention controls.
- Add bounded retrieval and explicit context provenance; never silently infer sensitive personal facts.
- Add backups/restore verification and operational health checks. A version-1 full backup endpoint and UI are now implemented; real-data recovery drills remain a release acceptance task.
- Acceptance: concurrent-write tests, session/revocation tests, memory permission/deletion tests, and recovery test pass.

## 3.0 — Alfred mode and voice
- Preserve Algieba as primary; no voice substitution while the online service works.
- Add an opt-in Android foreground listening service only after explaining its persistent microphone notification, battery cost, permission requirements, and platform restrictions.
- Keep push-to-talk available and provide a clear kill switch.
- Do not promise continuous listening on Android TV or laptop browsers.
- Acceptance: service lifecycle, permission-denial, reboot, battery restriction, and stop/disable tests on actual supported Android hardware.

## 4.0 — device intelligence
- Add explicit, permission-scoped actions with confirmation for consequential operations.
- Add reminders/calendar/notification integration only through supported Android APIs and user-granted permissions.
- The Android app now attempts to open the system Cast settings panel directly, falling back to general Settings/Quick Settings when the OEM does not expose it. This is only a shortcut to OS controls, not in-app mirroring.
- True screen mirroring still requires an Android MediaProjection consent flow and a compatible receiver/protocol.
- Acceptance: action allow-list, confirmation tests, permission denial tests, and device-by-device validation.

## 5.0 — independence and interface
- Provide an offline command set that works without network; evaluate local language models only against the actual phone/TV hardware and storage limits.
- Offline speech is a fallback and cannot be represented as identical to Gemini Algieba.
- Keep the animated reactor and validate responsive touch, keyboard, and TV remote navigation.
- Acceptance: airplane-mode tests, memory/storage measurements, accessibility/navigation checks, and physical-device tests.

## Release engineering blockers
- Vercel's latest deployment attempt was rejected by its 24-hour deployment-rate limit. Do not treat the older READY preview as a build of the latest branch commit.
- GitHub Pages and Vercel deploy separately; a release checklist must verify both before announcing a unified release.
- Keep development isolated on a feature branch until all checks pass. Do not deploy unverified changes to production.
