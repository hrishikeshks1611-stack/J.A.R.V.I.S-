# J.A.R.V.I.S. 5.1–10.0 Upgrade Roadmap

This roadmap carries forward the work agreed for the next development cycle. It is an engineering and acceptance plan, not a claim that every feature is already implemented.

## Operating constraints

- Preserve the existing JARVIS installation, package identity, owner access, paired devices, shared conversation/memory, animated 3D reactor/HUD, Gemini conversations, and Algieba as the primary voice.
- Do not change paid plans, voice identity, production deployment, or the installed app as part of these upgrades.
- Keep development on the feature branch. Do not merge, deploy, publish an update APK, uninstall, or install a second app.
- The original Build 39 signing key is not recovered. A same-package in-place update is blocked until its signing certificate is recovered and verified.
- Microphone audio for offline wake-word detection must stay on-device. The speech model may be downloaded once; no continuous audio upload.
- Consequential device actions require clear user intent and confirmation. Agent tools remain allow-listed, bounded, and auditable.

## Version status and scope

### 5.1 — Mission Control and personal routines

**Status:** Shipped in the development history.

- Mission Control diagnostics.
- Daily Briefing.
- Up to eight locally stored routines, including Focus, Good Night, and Normal.
- Algieba voice-identity protection.

Shared memory, device pairing, memory editing, and backup/restore are implemented in the current codebase; end-to-end multi-device and recovery drills remain acceptance work.

### 6.0 — Private offline wake word and safe actions

**Status:** In development; Android 10 emulator smoke test and physical-device validation remain open.

- On-device Vosk wake-word service with an explicit microphone switch.
- Persistent foreground-service notification and a visible stop action.
- Local-only audio processing; one-time speech-model download only.
- Wake-word events routed into the existing JARVIS conversation/command flow.
- Allow-listed AI tool calling with bounded iterations, argument validation, and confirmation gates.
- YouTube Music play-from-search request with a search fallback; report playback as unconfirmed until the device confirms it.

**Acceptance:** Android 10 service start, foreground notification, microphone permission denial/regrant, model-present and first-download paths, wake detection, command handoff, explicit stop, and no unexpected restart. Then test on the owner's actual Android 10 phone. Emulator success alone does not prove reliable real-world wake recognition.

### 7.0 — Native screen mirroring/casting

**Status:** Planned; not implemented by the existing Cast-settings shortcut.

- Add an Android MediaProjection consent flow; screen capture must begin only after the system prompt is accepted.
- Provide a clear active-capture indicator and immediate stop control.
- Define a receiver and transport before implementation (for example, a same-network receiver); do not claim that opening Android Cast settings is in-app mirroring.
- Handle rotation, reconnect, network loss, protected/DRM content restrictions, and receiver discovery.
- Keep capture sessions local/private by default; no hidden recording or background capture.

**Acceptance:** user-consent tests, start/stop lifecycle, receiver pairing, network interruption, orientation changes, and supported-device matrix.

### 8.0 — On-device conversational AI

**Status:** Planned; current offline fallback is command-only, not a local conversational model.

- Benchmark candidate quantized models against actual phone RAM, CPU/GPU/NPU, storage, and thermal limits before selecting one.
- Make model download, storage use, model status, and deletion visible and owner-controlled.
- Route offline prompts to the local model only when it is installed and healthy; otherwise disclose that cloud Gemini is required.
- Preserve conversation history and tool safety boundaries; local model output must not bypass the same allow-list or confirmation rules.
- Keep model inference and prompts on-device when offline mode is selected.

**Acceptance:** airplane-mode conversation tests, memory/storage/thermal measurements, restart recovery, model integrity checks, and safe tool-call regression tests.

### 9.0 — Automatic recurring reminders

**Status:** Planned; alarm/calendar handoff exists, but recurring reminder delivery is not complete.

- Add reminder create, edit, snooze, complete, and delete flows.
- Use Android scheduling APIs appropriate to the reminder type; avoid exact-alarm permission unless the feature truly requires it.
- Restore schedules after reboot and handle time-zone changes, daylight-saving changes, missed alarms, duplicate delivery, and app updates.
- Show notification permission and battery-optimization limitations clearly.
- Keep reminder state synchronized across paired devices with a single authoritative delivery owner to prevent duplicate alerts.

**Acceptance:** one-time and recurring delivery, reboot restore, time-zone/DST cases, permission denial, snooze/cancel, offline operation, and duplicate-delivery tests.

### 10.0 — Connected home and multi-device agent coordination

**Status:** Planned; provider choice and supported device scope are not yet finalized.

- Integrate smart-home control through an explicitly selected provider or local hub. Do not collect home credentials until the provider and permission model are agreed.
- Expose only discovered, authorized devices and allow-listed actions.
- Require confirmation for consequential actions such as unlocking doors, opening gates, disabling alarms, or changing safety/security settings.
- Add bounded multi-step agent workflows with visible plans, per-step results, cancellation, timeouts, and audit history.
- Coordinate routines across paired devices with owner authorization, idempotency, conflict handling, and offline behavior.

**Acceptance:** provider authentication/security review, least-privilege device scopes, confirmation tests, workflow cancellation/recovery, multi-device conflict tests, and audit verification.

## Cross-version release gates

A feature is not complete because its screen or API exists. Each version must pass:
1. Automated unit and regression tests.
2. Android 10 compatibility checks where native Android behavior is involved.
3. Permission-denial, cancellation, offline, timeout, and recovery tests.
4. Privacy and security review for audio, screen capture, account data, and external integrations.
5. End-to-end tests on actual supported hardware.
6. Release-signing verification before any installable update is offered.

## Immediate development order

1. Finish the Android 10 6.0 foreground microphone smoke test with bounded ADB commands and timestamped diagnostics.
2. Complete the 6.0 phone-level wake-word and YouTube Music verification without touching the installed app.
3. Implement 7.0 MediaProjection consent and receiver proof-of-concept.
4. Benchmark 8.0 local models on the actual device class before selecting a model.
5. Implement 9.0 reminder persistence and scheduling with reboot/time-zone tests.
6. Select the 10.0 smart-home provider and define the multi-device trust model before wiring actions.

No version should be marked ready until its acceptance criteria are met.
