# J.A.R.V.I.S 15.0 — Master Software Roadmap

**Status:** Approved development direction; implementation is in progress, not feature-complete.  
**Baseline:** `jarvis-14.0`  
**Development branch:** `jarvis-15.0`  
**Scope:** Software-only assistant for Android and Windows. Physical suit, robotics, and hardware control are out of scope.

## Mission

Evolve J.A.R.V.I.S into a context-aware, memory-enabled, permissioned autonomous AI that understands goals, plans work, uses approved tools, verifies outcomes, and communicates through voice and a cinematic interface.

**15.0 is an additive upgrade. Existing 14.0 capabilities are compatibility requirements, not optional features.**

## Non-negotiable preservation contract

Do not remove, silently replace, or regress:

- Voice conversation, personality modes, wake phrases, microphone permissions, speech-model setup, and tap-to-speak fallback.
- Existing speech generation, voice selection, and pronunciation behavior.
- Music playback, YouTube Music search/playback, and general YouTube search.
- Contacts and confirmation-based phone calling.
- Camera launch, capture, selfie, and latest-photo actions.
- Alarms, reminders, reboot rescheduling, and calendar event confirmation.
- Shared conversation history and saved-memory management.
- Device pairing, Android/Windows access, and cross-device sync.
- PDF, DOCX, XLSX, PPTX, CSV, TXT, and Markdown file creation/export.
- Education, medical-safety, and people-reading assistance modules.
- Security audit, owner recovery/transfer, backups, and confirmation gates.
- Existing 3D ARC reactor and current interface controls.

### Compatibility rules

1. Preserve Android application identity and existing user data.
2. Never ship a second app as an upgrade workaround.
3. Never modify the installed phone app or distribute an APK until update compatibility is independently verified.
4. Keep consequential actions behind explicit confirmation.
5. Never claim an action completed without a successful tool result or device-side evidence.
6. Preserve offline fallbacks, tap-to-speak, and clear error reporting.
7. Build new capabilities as modules; use feature flags where practical.
8. Maintain regression tests for every preserved capability.

## Existing 14.0 baseline discovered

The branch already contains a Gemini-backed server-side chat endpoint, a server-side allow-listed tool registry, shared conversation history, explicit saved memories, a bounded multi-step tool loop, personality/capability/tutor/medical/mentalist modules, Android bridges for calls/camera/media/alarms/reminders/calendar/wake-word services, a web app with an animated 3D reactor and mission control, security and backup flows, and Node tests covering agent behavior, permissions, memory, native bridges, voice routing, files, and UI contracts.

The current agent already has a bounded tool loop. 15.0 should strengthen goal planning, task state, recovery, and verification rather than replacing this foundation.

## Target architecture

### Intelligence layer
- Keep model credentials and provider calls server-side.
- Add a provider/model adapter so provider changes do not leak into app code.
- Use structured task intent and response contracts.
- Improve context selection and summarisation without inventing memories.
- Preserve attachments and specialist modules.
- Add timeouts, retry budgets, rate-limit handling, and clear failure states.

### Memory layer
- Retain user-controlled saved memories and shared conversation history.
- Add bounded retrieval by relevance, recency, and task context.
- Separate durable user-approved facts, temporary task state, and conversation history.
- Provide review, edit, and delete controls.
- Never treat retrieved memory or external tool output as executable instructions.

### Autonomous task engine
1. Understand the requested outcome.
2. Ask only for essential missing information.
3. Create a bounded plan with steps and dependencies.
4. Classify steps as read-only, reversible, or consequential.
5. Execute only through allow-listed tools and available integrations.
6. Require confirmation before consequential actions.
7. Capture evidence and verify each completed step.
8. Retry recoverable failures within a strict budget; otherwise explain the blocker.
9. Report completed, pending, and failed steps accurately.

Start with research, organising information, drafting, file preparation, and other reversible tasks. Sending messages, purchases, deletion, account changes, and other external consequences remain confirmation-gated.

### Voice layer
- Preserve wake-word and tap-to-speak paths.
- Improve recognition confidence, model download/install diagnostics, lifecycle handling, and recovery.
- Support natural follow-ups and interruption where supported.
- Preserve current voice selection and pronunciation.
- Never claim always-on listening when platform lifecycle or permissions prevent it.

### Visual experience
- Preserve the animated 3D ARC reactor as the central identity.
- Add modular blue HUD panels for conversation, memory, active tasks, progress, and system status.
- Distinguish listening, thinking, speaking, approval, success, and failure states.
- Keep controls readable on phone and laptop.
- Respect reduced-motion settings and low-memory devices.
- The interface must work on ordinary screens; physical holographic projection is not part of this software milestone.

### Cross-device experience
- Maintain one authenticated assistant identity across Android and Windows.
- Keep shared conversation and memory consistent.
- Show which device is executing a local action.
- Explicitly identify unavailable device-only actions.
- Preserve pairing, owner recovery, and session security.

## Delivery phases and exit criteria

### Phase 0 — Baseline and preservation
Freeze the 14.0 feature inventory; run Node tests and Android compile/unit tests; record unverified physical-device behaviors; establish a separate 15.0 branch and rollback point.  
**Exit:** baseline is green or known failures are documented; installed phone app remains untouched.

### Phase 1 — Conversational intelligence
Improve context selection and summaries; add structured intent extraction; clarify only essential ambiguity; preserve personality and specialist routing; test multi-turn context and tool-result grounding.  
**Exit:** complex requests and follow-ups work without losing existing commands.

### Phase 2 — Persistent contextual memory
Add relevant retrieval and safe summaries; separate durable memory from temporary task state; preserve review, correction, and deletion controls.  
**Exit:** context resumes across sessions while user control remains intact.

### Phase 3 — Autonomous execution
Add task/step records, dependencies, bounded planning, retries, cancellation, progress, evidence-based verification, and confirmation gates. Expand tools only through a reviewed allow-list.  
**Exit:** representative multi-step tasks complete audibly and safely; unsafe or unavailable actions fail closed.

### Phase 4 — Voice refinement
Verify model download, installation, activation, and recovery on supported Android versions; verify wake-word foreground/background lifecycle; preserve tap-to-speak and visible microphone status.  
**Exit:** automated tests pass and real-device validation is recorded separately.

### Phase 5 — Holographic-style software interface
Add task and memory panels around the existing reactor; add audio, task, and approval states; optimise for the 4 GB RAM Windows laptop and Android phone.  
**Exit:** interface remains responsive, readable, and functional with reduced motion.

### Phase 6 — Cross-device hardening and release
Test pairing, sync, reconnect, owner recovery, and local-only actions. Run the full preservation suite. Verify package identity, versioning, and update compatibility before any APK distribution.  
**Exit:** automated checks pass, device tests are documented, and update compatibility is proven—not assumed.

## Verification snapshot from the initial audit

- Node suite: **135 passed, 0 failed** in the isolated audit workspace after installing declared dependencies.
- Dependency installation audit: **0 reported vulnerabilities**.
- Android Gradle build/unit tests: **not run** in the audit workspace because Gradle is not installed there.
- Wake-word model download/activation and physical camera, alarm/reminder, and device behavior: **not newly verified** by this audit.
- No APK was built, installed, or distributed.
- No changes were made to the installed phone app or the 14.0 branch.

## First implementation backlog

1. Add the 15.0 branch to quality and Android compile/unit-test CI triggers.
2. Add agent task-state schemas and tests without changing current tool semantics.
3. Add memory retrieval/summarisation behind existing memory APIs.
4. Add a permission-aware task planner and progress event contract.
5. Extend the existing HUD with task and memory panels, retaining the ARC reactor.
6. Establish device-level evidence for wake word, alarms/reminders, camera, and voice before release.

## Definition of done

15.0 is ready only when advanced conversational intelligence, autonomous multi-step execution, and the ARC-reactor holographic-style interface are delivered while every 14.0 preservation requirement remains functional, tested, permissioned, and accurately represented.
