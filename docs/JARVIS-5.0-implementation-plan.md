# J.A.R.V.I.S. continuation release acceptance plan

## Preserve without exception
- One shared brain across paired Android phone, Android TV, and installed laptop PWA.
- Gemini TTS model/voice remains Algieba with en-GB British-butler delivery whenever online speech is available.
- Keep the animated 3D arc-reactor/HUD identity, attachments, local clock, permissioned location, existing app launches, and the current device pairing.
- Keep Gemini and Upstash credentials server-side. Never log or expose secret values.
- No new APK installation, package replacement, production switch, or PWA publish without the release gates below.

## Included in the development continuation

### Shared foundation
- Shared Redis conversation, atomic completed-turn appends, pairing/device management, owner transfer and recovery, user-approved memory lifecycle, backup/restore, Mission Control, local briefing/routines, and owner-only security audit.
- Memory remains explicit opt-in. Full backup restore is destructive and owner-only.

### Voice and model tools
- Algieba remains the primary online TTS voice. Speech cache/cooldown are bounded reliability measures, not a quota bypass.
- Android local British-English TTS is only a fallback and can sound different.
- Gemini tool-calling is allow-listed, bounded, and confirmation-gated for Clock/Calendar actions. No arbitrary code or shell execution.
- Chat remains JSON request/response; do not claim token streaming. Speech audio retains the streaming path.

### Productivity and devices
- Office Engine exports PDF, DOCX, XLSX, PPTX, CSV, TXT, and Markdown with bounded schema validation and a 3 MB cap.
- CSV formula-like values are escaped as text. XLSX formula support remains restricted.
- Generated files are delivered inline as base64 in this branch; durable artifact history and multi-instance one-time download storage are not included.
- Android app launches, photo/camera, Clock/Calendar handoffs, Android TV layout, and YouTube Music URL/app handoff are platform-dependent.
- Cast is a shortcut to Android OS settings, not screen mirroring.

## Deferred capabilities
- True MediaProjection casting and receiver discovery.
- Continuous wake-word/background microphone service. The Vosk prototype is on a separate branch and has not passed its Android 10 emulator smoke test.
- Offline conversational language model.
- Recurring reminders and background notifications.
- Calendar/weather-connected proactive briefings.
- Smart-home and universal TV/media integrations.
- Long-running external-account agent orchestration.

## Automated acceptance
- Run `node --check` on server/API/library JavaScript.
- Run all `node --test tests/*.test.mjs` regression tests after installing declared runtime dependencies.
- Compile Android phone and TV variants. A successful compile is not a device test.
- Check the deployed API's health and paired-session gates without reading or changing user conversation data.
- Review accessibility, keyboard/TV focus, CORS, session authorization, input bounds, file output safety, data deletion/restore, and backward compatibility.

## Release gates
1. Vercel must accept a production deployment and the deployed API must have working Gemini/Upstash configuration.
2. The frontend and backend must be verified compatible. Do not point the laptop PWA at a preview backend.
3. Android in-place update signing must match the installed Build 39 certificate. The original private keystore is not currently available; do not publish debug APKs as updates.
4. Physical phone, Android TV, and installed PWA checks remain outstanding.
5. If any gate fails, leave production, Pages, and installed phone/TV applications untouched. Keep changes in the review branch and report the exact blocker.
