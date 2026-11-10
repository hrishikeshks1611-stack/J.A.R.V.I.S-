# J.A.R.V.I.S. 5.0 — unified engineering plan

This document is the release acceptance checklist. A capability is not considered complete merely because its UI exists or a workflow succeeds.

## Current implementation status (development branch)

Implemented and covered by passing GitHub Actions tests:
- Atomic Redis/Lua append for completed chat turns to prevent stale read/replace writes from silently losing another device's turn.
- Explicit saved-memory store with add, edit, list, delete, and clear operations; bounded to 100 notes of at most 1,000 characters each.
- Saved memories are included as bounded context (latest 15 notes, 300 characters each) and are never automatically created from conversations.
- Saved-memory UI includes edit, delete, clear, and JSON export-to-clipboard controls, plus a warning not to store credentials or highly sensitive information.
- Owner transfer between paired devices, allowing a new device to become owner before a future app reinstall.
- Offline command fallback is now connected to the chat failure path for local time/date, greetings, known website lookups, and location when permissioned. Native Android app launches, alarm requests, and calendar-event requests are handled locally and appended to shared conversation history; Clock/Calendar actions require user confirmation.
- Automated tests cover shared-store bounds, atomic append invocation, memory lifecycle, owner transfer, API authorization/CORS, explicit clear confirmation, and inline frontend JavaScript syntax.

Still not complete or not physically verified:
- High-volume real Upstash concurrency stress test; CI uses mocked Redis responses, but the actual atomic append, memory add/edit/delete, and owner-transfer Lua scripts have passed isolated smoke tests on temporary keys in the connected Upstash database. All temporary QA keys were deleted afterward.
- Full backup and restore of conversation plus memory, beyond copying saved-memory JSON.
- Continuous wake-word listening and foreground microphone service.
- Native MediaProjection screen mirroring.
- Background notification scheduling, recurring reminders, and automatic reminder delivery. Basic alarm handoff and calendar-event creation are implemented through Android Clock/Calendar confirmation screens; the user must review and save them.
- A local language model; offline fallback is command-only, not offline conversational AI.
- Stable Android signing key and in-place update path; current workflow uses ephemeral debug signing.
- Physical testing on the user's phone and Android TV.

This branch is not production. Keep it isolated until the remaining acceptance tests, release-signing migration, and deployment verification are complete.

## Preserve throughout
- Gemini TTS model, Algieba voice, and en-GB delivery remain the primary online voice.
- Preserve the British butler persona, animated reactor/HUD, attachments, local clock and permissioned location, device pairing, and all three form factors.
- Keep Gemini credentials and Upstash credentials server-side.
- Keep the current production deployment and installed APKs usable during development.

## 2.0 — durable shared memory and reliability
- Replace the single blind read/modify/write conversation update with atomic or version-checked writes so concurrent devices cannot silently erase each other's turns.
- Add durable user-approved memory records separately from recent chat history; include review, edit, delete, export, and retention controls.
- Add bounded retrieval and explicit context provenance; never silently infer sensitive personal facts.
- Add backups/restore verification and operational health checks.
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
- Screen mirroring requires a real Android MediaProjection consent flow and a compatible receiver/protocol; opening Settings is not screen casting.
- Acceptance: action allow-list, confirmation tests, permission denial tests, and device-by-device validation.

## 5.0 — independence and interface
- Provide an offline command set that works without network; evaluate local language models only against the actual phone/TV hardware and storage limits.
- Offline speech is a fallback and cannot be represented as identical to Gemini Algieba.
- Keep the animated reactor and validate responsive touch, keyboard, and TV remote navigation.
- Acceptance: airplane-mode tests, memory/storage measurements, accessibility/navigation checks, and physical-device tests.

## Release engineering blockers
- Current Android workflow creates debug APKs on ephemeral GitHub runners. A stable signing identity is required for in-place updates. Do not ask the user to uninstall existing builds or publish an APK update until signing continuity is verified.
- The repository currently has no automated application test suite. Add quality gates before changing shared-memory or Android lifecycle code.
- GitHub Pages and Vercel deploy separately; a release checklist must verify both before announcing a unified release.
- Keep development isolated on a feature branch until all checks pass. Do not deploy unverified changes to production.
