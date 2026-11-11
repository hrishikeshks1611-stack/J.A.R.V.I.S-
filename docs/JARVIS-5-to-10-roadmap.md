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
- That download token store is in process memory: a restart or multiple service instances can invalidate or split outstanding links. It is staging-only until the download store is moved to a shared durable backend and expiry/restart behavior is tested.
- The 6.0 branch is not silently merged into this 5.0 branch; it must be reviewed as a separate integration.

## 7.0 — device intelligence

Implemented in part:
- Android bridge actions include supported app launches and explicit Clock/Calendar handoffs with confirmation.
- Cast is only a shortcut to Android system Cast settings. It is not screen mirroring and cannot guarantee compatibility with every television.
- True mirroring requires MediaProjection consent, a receiver/protocol supported by both devices, and physical validation. No universal cast capability is claimed.

## 8.0 — offline autonomy and wake-word

- The offline fallback handles a small set of deterministic local commands only.
- There is no offline conversational language model and no native continuous wake-word/background microphone service in this release line.
- A real Android wake-word service needs a foreground-service design, persistent microphone notification, runtime permission and kill switch, battery/reboot handling, and Android 10 hardware testing. Android TV and laptop browsers have different constraints.
- Local Android TTS fallback is not the same voice as online Gemini Algieba.

## 9.0 — permissioned agent workflows

Not yet shipped as a general-purpose autonomous tool-calling system:
- Existing actions are explicit and allow-listed; they do not grant arbitrary shell, contact, message, file-system, or account access.
- A safe future tool registry needs typed schemas, per-tool permission scopes, confirmation for consequential actions, timeouts, idempotency, audit records, cancellation, and regression tests.
- External services require user-authorised connectors and their own API/permission constraints.

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
- Render reports the 5.0 and 6.0 staging deployments as live. An HTTP-level smoke request could not be completed from this runner because outbound DNS resolution was unavailable; control-plane deployment status is not represented as an API smoke test.
- The current Vercel production deployment is READY but is on an older main commit. The latest main commit has a Vercel deployment-rate-limit failure, so no new production deployment is authorised by the release gates.
- Production environment-variable entries for Gemini and Upstash are present by key name; secret values were not read or printed. Their runtime validity was not independently verified in this session.
- Build 39 and later debug APKs have different signing-certificate fingerprints. The original Build 39 private keystore is not available in the repository or CI. Do not publish a replacement APK or tell the user to uninstall the installed app.
- No physical test on the user's phone, Android TV, or laptop PWA was performed in this session.
