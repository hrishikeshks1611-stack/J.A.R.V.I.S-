# J.A.R.V.I.S. Master Brain — Product and Engineering Blueprint

**Status:** Active architecture baseline  
**Target:** One unified personal assistant across Android phone, Android TV, laptop/PWA and the future holographic command station.  
**Compatibility baseline:** Android 10 (API 29) phone; existing owner identity, paired devices, backend and user data must remain intact.

## Non-negotiable principles

1. **One JARVIS, one brain.** All authorised clients use one authenticated intelligence and tool-orchestration service, with device-specific capabilities exposed by each client.
2. **Preserve before replacing.** The currently installed JARVIS 5.0.2, owner access, paired devices, memories, conversations and live backend remain untouched until migration and rollback gates pass.
3. **No destructive migration.** Never require uninstall, app-data clearing, account reset or device re-pairing as a routine upgrade path. Export, validate, import and compare before switching.
4. **Truthful action states.** Distinguish proposed, awaiting approval, handed off, executed and independently verified. Never claim a phone action, playback, reminder or deployment succeeded based only on a request being sent.
5. **Permissioned autonomy.** Use typed, allow-listed tools with input validation, bounded steps, timeouts, audit records and explicit approval for consequential actions. No arbitrary shell, JavaScript evaluation or unrestricted URL execution.
6. **Privacy-first presence.** Wake-word recognition is on-device. No continuous microphone uploads and no saved ambient audio. Microphone is user-toggleable, foreground operation is visible and stoppable, and wake listening does not silently restart after reboot.
7. **Android 10 first.** Keep the phone experience compatible with API 29 and modest hardware; measure CPU, RAM, battery, heat, latency and storage before enabling heavier local models.
8. **Accessible everywhere.** Voice is primary but never mandatory. Provide touch, keyboard, screen-reader labels, large controls, reduced-motion support and TV remote focus navigation.

## Product pillars

### 1. Master intelligence and reasoning
- Shared Gemini-backed conversational brain with model/provider abstraction.
- Bounded multi-step planning and tool calling; current six-tool/five-step limits are a safe starting point, not an entitlement to unbounded autonomy.
- Tool registry with typed schemas, capability scopes, per-tool timeouts, idempotency where possible, audit trail and result verification.
- Hybrid operation: online reasoning when available; deterministic offline essentials when not. Offline conversational LLM remains a separately benchmarked future capability.
- Three consistent personality modes: Professional, Witty and Philosophical. Accuracy and safety override persona.
- Concise spoken answers and richer screen answers, with a consistent British-butler voice identity.

### 2. Memory and continuity
- One authenticated shared conversation across paired phone, TV and laptop clients.
- Explicit user-controlled long-term memories: add, search, edit, delete, clear with confirmation, export.
- Memory is not silently inferred from every conversation.
- Owner-controlled device pairing, one-time codes, revocation, recovery and ownership transfer.
- Encrypted/authenticated backup export and validated restore; atomic replacement only after explicit owner confirmation.
- Durable task and artifact history, with retention controls and deletion/export support.

### 3. Voice, wake word and accessibility
- Online primary speech voice: Gemini TTS Algieba, en-GB; local Android British-English TTS is a fallback and must be labelled as such in diagnostics.
- Offline wake phrases: “JARVIS”, “Hello JARVIS”, and “Wake up JARVIS”.
- On-device recognition (Vosk prototype or a measured keyword-spotting alternative); phrase matching must be robust to noise and accent.
- Microphone ON/OFF control, visible foreground notification with STOP action, no ambient-audio retention, no continuous audio upload, no auto-start after reboot.
- After wake, acknowledge “Yes, Sir?”, accept a command for a bounded window (target eight seconds), then return to idle.
- False-activation suppression, cooldown, battery/thermal monitoring and clear permission diagnostics.
- Camera voice command and accessible text fallback.
- Do not call wake word production-ready until Android 10 emulator and physical-device tests pass.

### 4. Device and media intelligence
- Android app/site launching through explicit native bridge capabilities and supported OS handoffs.
- Device status, local time/date, permissioned location, camera/photo capture and safe diagnostics.
- YouTube Music: search the catalogue, select the exact result/video ID, open its canonical track, request playback, then report only what Android's media session can verify. Never type a generic query into the app search field as a substitute for selecting a result.
- Clock and Calendar actions remain pending proposals until the user confirms in the native app.
- TV controls and Cast: accurately distinguish opening system Cast settings from actual screen mirroring. True mirroring requires separate MediaProjection consent and receiver compatibility.
- Future smart-home and universal media controls only through documented provider APIs, explicit scopes and per-device permissions.

### 5. Productivity and personal operations
- Office Engine: PDF, DOCX, XLSX, PPTX, CSV, TXT and Markdown generation with strict validation, formula-injection protections and bounded file sizes.
- File attachment understanding and safe file delivery/downloads.
- Notes, browser search/open, calculator, device diagnostics and memory/conversation search.
- Daily briefing and Focus / Good Night / Normal routines, upgraded from local-only routines to connected calendar/weather data only after provider permissions and reliable scheduling exist.
- Reminders, recurring jobs and proactive notifications backed by durable scheduling/queue infrastructure, retries, deduplication and per-device notification preferences.
- External account workflows only after connector authorization, least-privilege scopes and explicit approval for consequential changes.

### 6. JARVIS presence and holographic workstation
- Preserve the moving 3D ARC reactor as the central visual identity, surrounded by the blue holographic HUD panels.
- Responsive phone, Android TV and laptop interfaces with one shared account/brain.
- Future portable holographic command station: Pepper’s Ghost-style reflective display, black enclosure, prominent moving reactor and floating panels; no Earth globe as the centrepiece.
- The workstation is a client/display surface, not a separate competing brain.

## Target architecture

- **Clients:** Android native shell + WebView/PWA UI, Android TV client, laptop PWA/desktop bridge, future holographic display.
- **Edge capability layer:** a versioned, typed Android bridge. The client advertises only capabilities it supports and has permission to perform.
- **Brain API:** authenticated session gateway, conversation context builder, model router, planner/tool loop, policy/approval gate and response formatter.
- **Tool services:** allow-listed modules for media, device, camera, files, productivity, memory, search, calendar, reminders and future integrations.
- **State:** durable shared conversation/memory store; task/job store; artifact metadata; device registry; audit events. Keep secrets server-side.
- **Background execution:** durable queue/scheduler with retry policy, idempotency keys, cancellation, per-device delivery and visible job state.
- **Observability:** health checks, structured redacted logs, latency/error metrics, tool traces and release/test evidence. Never log secrets, raw ambient audio or unnecessary personal data.

## Delivery sequence and gates

### Phase 0 — inventory and safety baseline
- Map every current feature to implemented / tested / deployed / physical-device-verified.
- Confirm current mainline, backend target, persistent store configuration, pairing and backup/restore behavior.
- Keep all work on this feature branch until review. No production deployment, Pages switch, APK release or live service mutation.
- Add architecture and migration tests before changing runtime behavior.

### Phase 1 — unified brain core
- Consolidate personality, context, bounded planner, tool registry, approval policy and action-state reporting.
- Add tool contract tests, adversarial input tests, timeout/retry tests and deterministic replay fixtures.
- Improve answer grounding and tool-result verification without exposing private chain-of-thought.

### Phase 2 — memory and continuity
- Add durable task/artifact records and migration tooling.
- Validate export/import checksums, owner authorization, atomicity, rollback and preservation of paired-device access.
- Never merge separate conversation histories without user approval.

### Phase 3 — voice and device control
- Complete exact YouTube Music result selection and media-session verification.
- Gate offline wake-word release on Android 10 service lifecycle, STOP behavior, phrase accuracy, false triggers, heat and battery.
- Add capability discovery and per-action confirmation/verification.

### Phase 4 — proactive and connected operations
- Introduce durable reminders, scheduled briefings, calendar/weather connectors and long-running workflows.
- Require user-granted scopes, revocation, audit history, retry safety and clear notification controls.

### Phase 5 — multi-surface intelligence
- Complete TV/laptop client parity and accessibility.
- Add desktop/holographic workstation bridge only with explicit local permissions and clear device boundaries.

### Phase 6 — migration and retirement
- Produce a migration report covering owner identity, pairing, conversations, saved memories, preferences, files and active jobs.
- Back up and verify data; run old and new clients side-by-side without destructive changes.
- Perform a reversible staged cutover, then observe stability and rollback readiness.
- Retire old app versions only after the new client is verified on the user's actual Android 10 phone and all paired devices. Never delete the old installation first.

## Release gates (all required)
- Unit, integration, security, accessibility and regression suites pass.
- Android phone builds and runs on API 29; TV and laptop clients pass their own checks.
- Real-device wake-word, voice, camera, media and thermal tests are completed where applicable.
- Backend health, authenticated chat, speech, pairing, shared persistence, backup/restore and job delivery are verified against the actual configured production target.
- No secrets in client bundles or logs; no unsafe arbitrary tool execution.
- Upgrade/migration is proven non-destructive and rollback is rehearsed.
- User explicitly approves the final migration and retirement step.

## First implementation backlog
1. Reconcile this blueprint against the merged 5.0–10.0 code and update the capability ledger so it reflects the actual current main branch.
2. Add a machine-readable capability registry and a single versioned tool contract.
3. Add an action lifecycle envelope: `proposed`, `awaiting_approval`, `handed_off`, `executed`, `verified`, `failed`, `cancelled`.
4. Add contract tests for permission, approval, idempotency, timeouts and honest status reporting.
5. Build a read-only migration inspector before any import or app replacement work.
6. Continue the YouTube Music exact-track flow and wake-word reliability work as separate gated modules.

## Current known constraints
- A successful CI build is not physical-device verification.
- The user's Android 10 phone is the primary compatibility target.
- Existing installed JARVIS 5.0.2, owner access, paired devices, user data and live backend are protected.
- Debug APKs are verification artifacts, not safe in-place update packages.
- Offline conversational AI, reliable wake-word behavior, true casting, durable proactive automation and universal smart-home/media control must not be advertised as complete until tested.
