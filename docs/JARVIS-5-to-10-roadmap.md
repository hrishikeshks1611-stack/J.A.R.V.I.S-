# J.A.R.V.I.S. 5.0–10.0 capability ledger

This document distinguishes implemented code, automated verification, deployed services, and capabilities that still require hardware, permissions, external providers, or further engineering. A UI control or successful build is not proof of a physical-device capability.

## 5.0 — shared foundation and control

Implemented in the continuation line:
- One authenticated shared conversation history for paired phone, Android TV, and laptop PWA sessions. It is one shared conversation, not independent per-device threads.
- Owner-controlled pairing, one-time pairing codes, device revocation, ownership transfer, and one-time owner recovery when the server recovery credential is configured.
- User-approved long-term memories with list, add, edit, delete, clear confirmation, bounded model context, and export. Conversations are not silently promoted to long-term memory.
- Authenticated full backup export and owner-only restore for recent conversation plus saved memories, with bounded validation and atomic Redis replacement.
- Mission Control reports local device/time/network/voice state and verifies the shared API session. Battery and paired-device count appear only where supported/authorised.
- Local Daily Briefing and local Focus / Good Night / Normal routines. They do not read a calendar, fetch weather, schedule jobs, or control another device.
- Owner-only security audit and privacy-protected source fingerprints.
- Gemini TTS Algieba / en-GB remains the primary online voice. The client caches identical audio and applies cooldowns; Google quotas still apply. The Android British-English TTS fallback is a different local voice, not Algieba.

## 6.0 — productivity and file delivery

Implemented in the continuation line:
- Office Engine creates PDF, DOCX, XLSX, PPTX, CSV, TXT, and Markdown from bounded, validated model output.
- File responses are returned as inline base64 with a 3 MB generated-file cap and can be saved through the Android Downloads bridge when that bridge exists.
- CSV export neutralises formula-like untrusted values before spreadsheet applications can evaluate them. Explicit XLSX formulas remain limited by a conservative allow-list.
- The staging health endpoint reports that generated-file history is not durable. Conversation history may be stored separately in Upstash; the generated artifact itself is not retained as a durable file record.
- A separate `jarvis-6.0-development` branch contains one-time download links with an in-process token store. That token store is not safe for multi-instance/restart durability and is not merged into this release line.

## 7.0 — device intelligence

Implemented in part:
- Device-aware diagnostics and Android TV-oriented layout/remote focus styling.
- Supported Android app launches, camera/photo selection, and explicit Clock/Calendar handoffs.
- YouTube Music search/open commands are available through the web shell and platform URL/app handoff where the device supports it.
- Cast opens Android's Cast settings when the OS exposes that panel, otherwise it falls back to Settings/Quick Settings. This is not in-app screen mirroring and does not guarantee compatibility with every TV.
- True mirroring still requires MediaProjection consent, a compatible receiver/protocol, network handling, and physical validation. It is not implemented.

## 8.0 — offline autonomy and wake-word

- Offline fallback handles a small deterministic command set such as greetings, local date/time, permissioned location, and common web searches. There is no offline conversational language model.
- `jarvis-intelligence-tools` contains a Vosk foreground-service prototype with a visible notification and OFF action. It is not merged or released.
- Its latest quality suite passed and phone/TV APK compilation passed, but the Android 10 emulator smoke job failed before APK installation because the test script did not preserve its APK path variable between shell commands. Model load, foreground listening, stop behaviour, false activations, battery use, and service recovery therefore remain unverified.
- The prototype uses general speech recognition plus phrase matching, not a dedicated keyword model. The downloaded model still needs a pinned SHA-256 checksum. Do not describe wake-word as ready or reliable.
- Local Android TTS is a fallback and is not the Algieba voice.

## 9.0 — permissioned agent workflows

Implemented in the continuation line:
- Gemini function calling can select from six allow-listed tools: safe arithmetic, supplied device status, pending alarm request, pending calendar-event request, saved-memory search, and shared-conversation search.
- Server execution is bounded to six tool calls and five model steps. It does not evaluate arbitrary JavaScript, shell commands, or arbitrary URLs.
- Alarm/calendar tools only create pending proposals. The UI requires a separate click and opens the native Clock/Calendar editor; the user must confirm/save there.
- Memory and conversation searches are read-only. Device status contains only values explicitly supplied by the paired client.
- Chat responses remain JSON request/response; they are not token-streamed. Gemini speech uses the existing streaming audio path and local cache.

## 10.0 — coordinated personal assistant

Not implemented:
- Cross-device scheduled actions, recurring reminders, and reliable background notifications.
- Proactive calendar/weather-connected briefings.
- Smart-home integrations and universal TV/media control.
- Long-running orchestration across external accounts and services.
- Offline conversational AI.

These require durable queues/state, explicit provider scopes, per-device presence and revocation, user approval, retries, audit history, rate limits, and hardware/provider-specific validation. Local model selection also requires measured RAM, storage, CPU, latency, licence, and thermal behaviour on the actual phone and TV.

## Verification and release gates

- PR #5 is merged. The continuation branch combines the latest `main` line with the follow-up 5.0 development work; review it before merging.
- The latest pre-continuation 5.0 quality run passed 61/61 tests. The Android phone + TV compile workflow passed on the last native-source commit; this is compilation evidence, not physical-device testing.
- The continuation adds syntax checks, regression coverage for the tool UI, and a CSV formula-injection test. Its own CI results must be checked before merge.
- The current Vercel production deployment is READY but serves main commit `869166cfb452fc5a822623b044e45b6ef71bcf29`. Latest main commit `f1c10fdba6d2239703471deb2351cb9bc6273807` has a Vercel deployment-rate-limit failure. No production deployment is authorised.
- GitHub Pages successfully deployed main commit `f1c10fdba6d2239703471deb2351cb9bc6273807`; it does not contain the continuation branch's tool-calling UI.
- The Render control-plane connector returned transport errors during this run, so the staging service and current staging deployment could not be re-verified. Previous session notes recorded read-only 200 health/pairing checks, but those do not prove the continuation is deployed.
- Production Gemini and Upstash environment-variable values were not read or printed. Presence by key name is not proof that the deployed runtime can use them.
- Build 39 and later debug APKs have different signing-certificate fingerprints. The original Build 39 private keystore is not available in the repository or CI. Do not publish a replacement APK or instruct the user to uninstall.
- No physical test on the user's phone, Android TV, or laptop PWA was performed. No production or Pages deployment was made from this continuation branch.
