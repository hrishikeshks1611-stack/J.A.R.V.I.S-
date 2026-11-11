# J.A.R.V.I.S. 5.0–10.0 engineering roadmap

This is a capability ledger, not a promise that every version number represents a finished release. A button, UI label, successful CI run, or live staging deployment is not proof of a physical-device capability.

## 5.0 — shared foundation and control

Implemented in the 5.0 development line:
- One authenticated shared conversation history across paired phone, Android TV, and laptop PWA sessions.
- User-approved saved memories with review, edit, delete, clear confirmation, and export.
- Owner-controlled pairing, device revocation, owner transfer, and authenticated backup/restore.
- Atomic shared-history appends and bounded model context.
- Mission Control with local time, device type, network, battery when supported, voice state, live backend/session verification, and owner-only paired-device count.
- Alfred-style local daily briefing. It explicitly reports that calendar/weather feeds are not connected.
- Up to eight custom local routines using only Focus, Good night, and Normal modes. They are browser-local preferences, not background automation.
- Preserve Gemini TTS Algieba / en-GB as the primary online voice. Speech remains subject to Google's quota; caching and cooldown reduce repeated requests but do not make it unlimited.

## 6.0 — productivity and file delivery

- Office Engine generation for PDF, DOCX, XLSX, and PPTX is present in the 5.0 development line with automated helper tests.
- A separate `jarvis-6.0-development` line contains a one-time, random-token download endpoint (15-minute expiry, 30 MB cap) and a Render staging service.
- The existing 5.0 staging health response reports `fileCreation: true` but `fileHistoryPersistence: false`; generated-file history must not be treated as durable across restarts. The separate 6.0 staging health endpoint reports version `6.0.0`.
- That download token store is in process memory: a restart or multiple service instances can invalidate or split outstanding links. It is staging-only until the download store is moved to a shared durable backend and expiry/restart behavior is tested.
- The 6.0 branch is not silently merged into this 5.0 branch; it must be reviewed as a separate integration.

## 7.0 — device intelligence

Implemented in part:
- Android bridge actions include supported app launches and explicit Clock/Calendar handoffs with confirmation.
- Cast is only a shortcut to Android system Cast settings. It is not screen mirroring and cannot guarantee compatibility with every television.
- True mirroring requires MediaProjection consent, a receiver/protocol supported by both devices, and physical validation. No universal cast capability is claimed.

## 8.0 — offline autonomy and wake-word

- The offline fallback in the 5.0 line handles a small set of deterministic local commands only; there is no offline conversational language model.
- A separate `jarvis-intelligence-tools` branch contains a Vosk-based Android foreground-service prototype. It processes microphone audio locally, shows an ongoing notification with an OFF action, uses `START_NOT_STICKY`, and downloads the small English model on first setup.
- That prototype uses general speech recognition plus word matching, not a dedicated keyword model. It has not been merged into the 5.0 development line. The model archive is size/path bounded but does not yet have a pinned SHA-256 checksum; false activations, battery use, service recovery, and physical Android 10 behavior still require testing.
- The Android 10 emulator start/model-load/stop smoke test is separate from physical-device validation. Do not call wake-word reliable or installed until the emulator run passes and the user's phone is tested.
- Local Android TTS fallback is not the same voice as online Gemini Algieba.

## 9.0 — permissioned agent workflows

Implemented in the 5.0 development line as a bounded first tool-calling layer:
- Gemini function calling can select from six explicit tools: safe arithmetic, supplied device status, pending alarm request, pending calendar-event request, saved-memory search, and shared-conversation search.
- The server enforces an allow-list, a six-call limit, a five-step model limit, bounded arguments, and model fallbacks. It never evaluates model output as JavaScript, shell, or arbitrary URLs.
- Alarm and calendar tools only return pending actions. The UI requires a separate user click and opens the native Clock/Calendar editor; the user must confirm/save there.
- Memory and conversation search are read-only. Device status reports only fields explicitly supplied by the paired app.
- This is a permissioned foundation, not an unrestricted autonomous agent. External account actions, arbitrary files, messaging, and shell execution remain out of scope.
- Regression tests cover tool validation, explicit-intent checks, confirmation-gated actions, and the chat agent loop.

## 10.0 — coordinated personal assistant

Current shared conversation and saved memory are cross-device. Custom routines remain local to each browser/device. Not yet implemented:
- Cross-device scheduled actions and reliable background notifications.
- Proactive calendar/weather briefings.
- Smart-home integrations and universal TV/media control.
- Long-running agent orchestration across external accounts.

These require an opt-in event/queue service, device presence and revocation semantics, explicit scopes, delivery retries, rate limits, audit history, and hardware/provider-specific integration tests.

## Release gates and current evidence

- PR #5 is merged. The follow-on 5.0 development branch remains separate from production.
- The latest 5.0 branch quality run failed because the workflow did not install the declared `pdfkit` runtime dependency; this session fixes the workflow and adds regression assertions for Mission Control diagnostics and custom routines.
- Read-only API smoke tests succeeded against the existing Render 5.0 and 6.0 staging services: both `/healthz` endpoints returned HTTP 200 after warm-up, and `/api/pair` status returned HTTP 200 with the owner already configured. The Vercel production pairing status also returned HTTP 200; unauthenticated `/api/chat` and `/api/speech` correctly returned HTTP 401. These checks did not read or modify user conversation data.
- The newly committed 5.0 UI is not yet confirmed on Render: the staging URL still serves service-worker cache `jarvis-shell-v18` and does not contain the new Mission Control diagnostics/custom-routine markers. Render's control-plane API then returned transport errors, so the follow-up deploy state could not be confirmed. The existing staging deployment is therefore still treated as older than commit `4cb49fd30aa1b56a4882cad52ee432816117eee6`.
- The current Vercel production deployment is READY but is on an older main commit. The latest main commit has a Vercel deployment-rate-limit failure, so no new production deployment is authorised by the release gates.
- GitHub Pages successfully deployed the current `main` commit `f1c10fdba6d2239703471deb2351cb9bc6273807` (workflow run `43015795043`). That is the main-branch static frontend, not this development branch; this session did not publish the 5.0 development UI to Pages. The Vercel production backend remains on an older READY deployment (`869166cfb452fc5a822623b044e45b6ef71bcf29`), while the latest main deployment check is rate-limited. Frontend/backend compatibility for the newest main UI therefore remains a separate release check.
- Production environment-variable entries for Gemini and Upstash are present by key name; secret values were not read or printed. Their runtime validity was not independently verified in this session.
- Build 39 and later debug APKs have different signing-certificate fingerprints. The original Build 39 private keystore is not available in the repository or CI. Do not publish a replacement APK or tell the user to uninstall the installed app.
- No physical test on the user's phone, Android TV, or laptop PWA was performed in this session.
