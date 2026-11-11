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
- CSV export neutralises formula-like untrusted values before spreadsheet applications can evaluate them. XLSX formulas are parsed against a strict grammar limited to local A1 references, arithmetic, ranges, and a small deterministic function allow-list; external workbook links, named ranges, dynamic/network functions, and arbitrary formula syntax are rejected.
- The staging health endpoint reports that generated-file history is not durable. Conversation history may be stored separately in Upstash; the generated artifact itself is not retained as a durable file record.
- A separate `jarvis-6.0-development` branch contains one-time download links with an in-process token store. That token store is not safe for multi-instance/restart durability and is not merged into this release line.

## 7.0 — device intelligence

Implemented in part:
- Device-aware diagnostics and Android TV-oriented layout/remote focus styling.
- Supported Android app launches, camera/photo selection, and explicit Clock/Calendar handoffs.
- YouTube Music search/open commands are available through the web shell and platform URL/app handoff where the device supports it.
- The Cast control is explicitly labelled as an Android Cast-settings shortcut. It opens the OS Cast panel when available, otherwise Settings/Quick Settings, and states that JARVIS does not stream or mirror the screen itself. This does not guarantee compatibility with every TV.
- True mirroring still requires MediaProjection consent, a compatible receiver/protocol, network handling, and physical validation. It is not implemented.

## 8.0 — offline autonomy and wake-word

- Offline fallback handles a small deterministic command set such as greetings, local date/time, permissioned location, and common web searches. There is no offline conversational language model.
- `jarvis-intelligence-tools` / [PR #11](https://github.com/hrishikeshks1611-stack/J.A.R.V.I.S-/pull/11) contains an opt-in Vosk foreground-service prototype with a visible microphone notification and an OFF action. It is separate from the continuation release and is not merged or released.
- PR #11's latest quality suite passed 82/82 tests and its phone/TV debug verification APKs compiled. The latest Android 10 API 29 emulator run (workflow 43028996091) successfully built the phone APK, verified its path, and installed the emulator image; the foreground-service/model-load/stop smoke step was still running at this report and has not yet passed.
- The service uses a general US-English Vosk recogniser plus phrase matching, not a dedicated keyword-spotting model. False activations, British-accent command recognition, CPU/battery use, and recovery under real device conditions remain unverified. The archive's SHA-256 is not yet pinned to an independently verified publisher-controlled digest. Do not call this reliable or 24/7-ready.
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
- The latest continuation quality run passed **70/70 tests** on commit `ebb983413064207beb1ae0780db59279be92efa9`: syntax checks, shared-memory/agent/file/frontend regressions, and the read-only production API smoke all passed ([workflow 43030707175](https://github.com/hrishikeshks1611-stack/J.A.R.V.I.S-/actions/runs/43030707175)).
- Android phone + TV compile checks passed on commit `2e41b6510caa80703b31091e646a007a5f333f44` ([workflow 43030454558](https://github.com/hrishikeshks1611-stack/J.A.R.V.I.S-/actions/runs/43030454558)). The later `ebb983` change only touched the web UI and frontend test; no Android source or Gradle files changed afterward. This is compile evidence, not physical-device testing.
- The production smoke checks only prove that pairing status returns HTTP 200 and unauthenticated chat/speech/memory/backup requests return HTTP 401. They do not verify the production Gemini credential, authenticated user data, or the new continuation APIs.
- Vercel still reports `Deployment rate limited — retry in 24 hours` for the latest continuation commit. The previously confirmed READY production deployment serves older main commit `869166cfb452fc5a822623b044e45b6ef71bcf29`; no production deployment is authorised.
- GitHub Pages successfully deployed main commit `f1c10fdba6d2239703471deb2351cb9bc6273807`; it does not contain the continuation branch's tool-calling UI.
- The Render workspace is visible, but the service-inventory/deployment action required interactive authorisation in this non-interactive run. No Render service was created and no continuation staging deployment is verified.
- Production Gemini and Upstash environment-variable values were not read or printed. Presence by key name is not proof that the deployed runtime can use them.
- Build 39 and later debug APKs have different signing-certificate fingerprints. The original Build 39 private keystore is not available in the repository or CI. Do not publish a replacement APK or instruct the user to uninstall.
- No physical test on the user's phone, Android TV, or laptop PWA was performed. No production or Pages deployment was made from this continuation branch.
