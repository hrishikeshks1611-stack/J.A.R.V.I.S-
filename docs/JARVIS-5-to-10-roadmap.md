# J.A.R.V.I.S. 5.0–10.0 capability ledger

This document distinguishes implemented code, automated verification, deployed services, and capabilities that still require hardware, permissions, external providers, or further engineering. A UI control or successful build is not proof of a physical-device capability.

## 5.0 — shared foundation and control

Implemented in the continuation line:
- One authenticated shared conversation history for paired phone, Android TV, and laptop PWA sessions. It is one shared conversation, not independent per-device threads.
- Owner-controlled pairing, one-time pairing codes, device revocation, ownership transfer, and one-time owner recovery when the server recovery credential is configured.
- User-approved long-term memories with list, add, edit, delete, clear confirmation, bounded model context, and export. Conversations are not silently promoted to long-term memory.
- Authenticated full backup export and owner-only restore for recent conversation plus saved memories, with bounded validation and atomic Redis replacement.
- Mission Control reports local device/time/network/voice state and verifies the shared API session. Battery and paired-device count appear only where supported/authorised.
- The animated 3D arc-reactor HUD remains the default. Users who enable the operating system's reduced-motion preference receive a static reactor and shortened transitions.
- Local Daily Briefing and local Focus / Good Night / Normal routines. They do not read a calendar, fetch weather, schedule jobs, or control another device.
- Owner-only security audit and privacy-protected source fingerprints.
- Gemini TTS Algieba / en-GB remains the primary online voice. The client caches identical audio and applies cooldowns; Google quotas still apply. Regression tests cover split SSE events, PCM chunk delivery, Algieba/en-GB request configuration, HTTP 429 quota handling, empty streams, and the paired-session gate. The Android British-English TTS fallback is a different local voice, not Algieba.

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
- PR #11's latest quality suite passed 82/82 tests and its phone/TV debug verification APKs compiled. Run 43031225661 failed before APK installation because the emulator could not find its `jarvis-api29` AVD registration; the wake-word service was never launched, so that result does not test service startup. The CI harness was corrected in commit `a76d76bbe3328aae669dfc0635c4ac647c622bf7` with an explicit `ANDROID_AVD_HOME`, AVD data path and registration checks. The new Android 10 API 29 run (workflow 43031928218) now builds both APK variants, registers the AVD and installs the emulator image; its foreground-service/model-load/stop smoke step is still in progress. The workflow caps the job at 25 minutes and bounds `adb wait-for-device` to 180 seconds.
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

- PR #5 is merged into `main`. The active review candidate is [PR #12 — JARVIS 5.0–10.0 continuation](https://github.com/hrishikeshks1611-stack/J.A.R.V.I.S-/pull/12), branch `jarvis-5-to-10-continuation`, head `894166d0ce3c96f9ae56759372850e5e09d813b4` (140 commits ahead of `main`, 0 behind). It remains a draft and is not merged.
- The latest continuation test run executed **79/79 tests successfully**, and server/API syntax checks passed. The same run then failed the production API smoke gate for a real configuration issue described below: [quality workflow 43038476484](https://github.com/hrishikeshks1611-stack/J.A.R.V.I.S-/actions/runs/43038476484).
- Android phone and Android TV variants compiled successfully on the same head: [Android compile workflow 43038476476](https://github.com/hrishikeshks1611-stack/J.A.R.V.I.S-/actions/runs/43038476476). This is compile evidence only, not physical-device testing or an installable signed release.
- The smoke script was corrected to derive its target from the `JARVIS_API` value in `index.html`. The current Pages frontend is configured to call `https://jarvis-6-0-yys5.onrender.com/api`. Its Render `/healthz` endpoint responded HTTP 200, but reported `sharedConversationPersistenceConfigured: false`. At least one of `UPSTASH_REDIS_REST_URL` or `UPSTASH_REDIS_REST_TOKEN` is therefore absent from that Render runtime. The smoke test stopped at this gate; it did not test authenticated chat, speech, pairing or user data against Render.
- The earlier 315a7 smoke passed against the script's old default Vercel URL. That was not the API host configured in the frontend and is not evidence that the live Render backend is healthy. The updated smoke deliberately fails rather than reporting a false green.
- The Render Blueprint on the separate `jarvis-6.0-development` branch declares a free Singapore service and imports Gemini/Upstash variables from a service named `jarvis-5-0-staging`. Render service inventory required interactive authorisation in this run, so that source service and the current Render service configuration could not be inspected or changed. No Render resource or production setting was modified.
- The Vercel preview `jarvis-8qw90pq3r-hrishikesh-k-s.vercel.app` is READY for the older continuation head `315a7f8517f868ba3c108e3b1a8237a263dbff8b`; it is preview-only and does not update the PWA's Pages frontend. The latest continuation head `894166d` has no new Vercel deployment because the account still reports `Deployment rate limited — retry in 24 hours`. The latest production-target Vercel deployment is READY but serves older `main` commit `869166cfb452fc5a822623b044e45b6ef71bcf29`.
- GitHub Pages successfully deployed current `main` commit `f1c10fdba6d2239703471deb2351cb9bc6273807` ([Pages workflow 43015795043](https://github.com/hrishikeshks1611-stack/J.A.R.V.I.S-/actions/runs/43015795043)). The laptop PWA and the existing Android WebView shells load this main-branch frontend; the continuation UI has not been published there.
- Upstash was tested only with isolated temporary keys: eight concurrent atomic Lua appends preserved all eight writers, and the two-key atomic backup/restore script wrote and read both records with expiry. Both temporary keys were deleted. This is a focused Redis correctness check, not a production load or recovery drill.
- Build 39 and later debug APKs have different signing-certificate fingerprints, and the original Build 39 private keystore is unavailable. Do not publish a replacement APK or ask the user to uninstall the current apps.
- No physical test was performed on the user's phone, Android TV or laptop PWA. No production deployment, Pages update, APK release or live service mutation was made from this continuation branch.


## Five-pillar upgrade — active engineering track

The five pillars are a product goal, not a claim that every capability is already production-ready.

1. **Intelligence:** preserve the shared Gemini brain, bounded memory context, and allow-listed tool loop. Improve grounded multi-step planning and tool-result verification; do not expose private chain-of-thought.
2. **Personality:** use one shared, tested personality contract for Professional, Witty, and Philosophical modes. JARVIS should sound composed and recognisable without allowing humour to obscure accuracy.
3. **Device control:** expand only through explicit Android bridge capabilities and OS-supported handoffs. Every action must report whether it was proposed, handed off, or actually confirmed.
4. **Independence:** plan multi-step goals, sequence approved tools, respect dependencies and confirmation gates, and verify each result. No arbitrary shell, JavaScript evaluation, unrestricted URL execution, or silent consequential actions.
5. **Presence:** retain the animated 3D ARC-reactor HUD, voice identity, voice-first interaction, and wake-word controls. The wake-word prototype remains gated on Android 10 smoke, model integrity, physical-device recognition, battery/false-trigger testing, and release-signing continuity.

### Active implementation in `jarvis-five-pillars-upgrade`

- Added `lib/jarvis-personality.js` as the shared system-behaviour contract.
- Updated `api/chat.js` to use that contract, including explicit multi-step planning, truthful action-state reporting, permission boundaries, and natural spoken responses.
- Added `tests/personality-contract.test.mjs` to protect the three personality modes and the autonomy/verification safeguards.
- This is a development branch based on PR #12's continuation branch. It does not change `main`, GitHub Pages, Render, Vercel production, or the installed Android app.

### Remaining release gates

- Run the complete quality suite and Android phone/TV compile checks on this branch.
- Resolve the Render shared-memory configuration gate before relying on the live shared brain.
- Integrate the PR #11 wake-word work only after its Android 10 service smoke and security/reliability gates pass.
- Verify YouTube Music playback on a real supported device; an app launch or search result is not proof of playback.
- Perform physical Android 10 testing and preserve Build 39 signing continuity. The unavailable original signing key means no replacement APK should be offered as an in-place update.
- Do not mark any pillar complete solely because source code compiles or a preview deploys.
