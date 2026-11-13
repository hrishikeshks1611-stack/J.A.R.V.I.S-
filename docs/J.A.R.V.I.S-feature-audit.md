# J.A.R.V.I.S — feature inventory and release gates

This inventory is based on the current Android shell, the current published web client source, and its API routes. Source presence is not proof of device-level operation.

## Preserved capabilities in source

- **Identity and interface:** Android launcher label is exactly `J.A.R.V.I.S`. The launcher icon points to the blue ARC-reactor vector with a downward-pointing triangle inside a circular ring. The web interface contains the animated reactor core and HUD.
- **Alfred:** Alfred remains a personality/voice mode inside J.A.R.V.I.S. It is not the app name.
- **Fast local responses:** simple greetings, local time/date, assistant identity, common website opening, web-search links, and thanks can be answered locally without waiting for the AI API. General knowledge and complex requests still need the online AI service.
- **AI conversation:** the web client calls the authenticated J.A.R.V.I.S chat API, which uses the configured Gemini model fallback chain and the shared personality/tool layer.
- **Voice and wake word:** Android exposes microphone capture and a foreground Vosk-based offline wake-word service. Wake-phrase parsing has unit tests. The service downloads its speech model once and performs recognition on-device.
- **Speech output:** Alfred voice generation is routed through the configured Gemini TTS path. The web client deliberately fails silent rather than switching to an unverified voice when that service is unavailable.
- **Music:** the web client searches YouTube Music for a selected track ID, then the Android bridge opens that exact track and requests media-session playback. A sent play command is not proof that audio actually started; the UI must retain that distinction.
- **Calls and contacts:** native bridge methods exist for contact lookup and call handoff, with Android permissions.
- **Clock and calendar:** native bridge methods exist for alarm requests and calendar-event creation. These are OS handoffs, not a replacement clock/calendar database.
- **Apps and device controls:** native bridge methods exist for launching supported apps, opening Cast settings, location settings, and speech settings.
- **Camera and files:** native camera/photo capture, file selection, attachment transfer, and file saving are implemented in the Android bridge.
- **Office:** the web client has a file-generation path for supported document formats through the file-generation API.
- **Memory and continuity:** the web client includes device pairing/sync, shared conversation history, explicitly saved memories, export/restore backup flows, and owner security-audit UI. These depend on the configured backend and must be tested against it.
- **Mission Control:** local device diagnostics and local-only Focus/Good Night/Normal routines are present. These routines do not claim to run background jobs.
- **Cast:** the current control opens Android Cast settings. It does not itself mirror the phone screen.

## Not present as separate engines in the inspected current client/API

The inspected current web client and chat API do not contain separate Tutor, Medical, Mentalist, or Emotional Assistant engines. Do not label these as working or expose them as completed until they are implemented and tested.

## Required release verification

1. Unit tests pass, including wake-phrase parsing.
2. Phone and TV APKs compile.
3. The built APK reports the exact launcher label `J.A.R.V.I.S`, contains the ARC launcher resource, and uses the independent package IDs defined in `app/build.gradle`.
4. Android 10 emulator test installs the phone APK, launches `MainActivity`, and verifies the launcher identity and WebView host.
5. Physical Android 10/Vivo testing verifies wake-word start/stop, microphone readiness, false triggers, battery/heat, calls/contact permissions, alarms/calendar handoff, camera, and media-session playback.
6. Backend-dependent tests verify authenticated chat, TTS, music search, Office file generation, pairing, memory, and backup/restore.
7. Do not mark a feature complete solely because its method or button exists in source. Record the exact test evidence and distinguish requested, handed-off, executed, and independently verified actions.

## Known limitations to keep visible

- The animated reactor in the current HTML is a layered CSS animation; it is not yet a true WebGL/3D-rendered model.
- A successful emulator launch is not a physical-device test.
- Actual YouTube Music playback is not confirmed until Android exposes a media session and playback is independently observed.
- Wake-word reliability, battery impact, and all native OS handoffs still need physical testing on the target phone.
