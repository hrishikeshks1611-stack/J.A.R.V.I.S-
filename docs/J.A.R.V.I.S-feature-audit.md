# J.A.R.V.I.S — feature inventory and release gates

This inventory follows the actual J.A.R.V.I.S 7.0 source carried into this clean-name Android build, including its server-side engine modules and machine-readable capability catalogue. Source presence and passing backend tests are not proof of physical-device operation.

## Preserved intelligence and assistant features

- **J.A.R.V.I.S identity:** Android launcher label is exactly `J.A.R.V.I.S`. Alfred is a personality mode, never the app name.
- **Alfred:** selectable refined British butler personality mode inside J.A.R.V.I.S, with the configured Algieba British voice path. If voice generation is unavailable, the client is designed to fail silent rather than switch to an unverified voice.
- **Fast local responses:** simple greetings, local time/date, assistant identity, common website opening, web-search links, and thanks are handled locally. General knowledge and complex questions still need the online AI service.
- **Tutor / Education:** detects learning and exam requests; supports step-by-step teaching, worked examples, quizzes, exam-style answers, grading/explanations, study plans and revision planning.
- **Medical:** cautious health education and first-aid guidance; detects common emergency red flags and immediately directs the user to India's 112 or Kerala's 108 ambulance service. It does not diagnose, prescribe, or call emergency services.
- **Mentalist:** analyses social situations using observations, context and alternative explanations; explicitly avoids claims of mind-reading, reliable lie detection, or coercive manipulation.
- **Emotional availability:** always included in chat routing; attentive listening and support without pretending to be human or encouraging dependence.
- **Office:** generates PDF, DOCX, XLSX, PPTX, CSV, TXT and Markdown files through the authenticated file-generation API.
- **General AI and tools:** authenticated chat, bounded tool execution, shared history, saved memories, pairing/device revocation, and backup/restore.

## Android and device features with source implementations

- **ARC reactor:** the launcher resource is the blue circular reactor with an inverted triangle. The main HUD has an animated reactor core. The Android build bundles the exact HTML UI from the branch and renders it locally with the approved HTTPS base origin for API calls and storage.
- **Microphone and voice input:** native microphone capture and speech recognition bridge methods are present.
- **Wake word:** Vosk offline foreground service and phrase parser are present. Supported phrases in the current parser: “Hey JARVIS,” “Hello JARVIS,” “Wake up JARVIS,” and “JARVIS, are you there?” The capability catalogue marks persistent offline wake-word as experimental/prototype; real-device reliability and background behavior remain unverified.
- **Music:** exact YouTube Music track search and native direct-track handoff are present. Android media-session play control is partial: a sent command is not proof that audio actually started.
- **Calls and contacts:** native contact lookup and call handoff are present; Android Contacts/Phone permissions and a real device test are required.
- **Clock and calendar:** alarm and calendar handoff methods are present. These are partial OS handoffs, not proof that a persistent reminder was saved.
- **Reminders:** durable reminders/schedules remain planned; do not claim recurring or background reminders are implemented.
- **Apps and device controls:** native bridge methods exist for launching supported apps and opening Cast, location, and speech settings. Cast opens Android's Cast controls; it does not itself mirror the screen.
- **Camera and files:** native camera/photo capture, file selection, attachment transfer, and file saving are present.
- **Mission Control:** local device diagnostics and local-only Focus/Good Night/Normal routines are present; these do not claim to run background jobs.

## Capability status that must remain visible

- **Implemented in source/backend:** Tutor, Education, Medical, emergency guidance, Mentalist, Emotional availability, Office file generation, Alfred personality, general chat, shared memory/pairing/backup, contact calling, camera capture, microphone dictation, online British voice.
- **Partial or prototype:** offline wake word, exact-track music handoff/media-session playback, alarm/calendar handoff, app launching, local offline voice fallback.
- **Planned, not to be advertised as working:** durable reminders/schedules, calendar/weather briefings, smart-home control, offline conversational model, true screen mirroring, holographic command station.
- **Physical-device testing still required:** wake-word start/stop and false triggers, battery/heat, microphone readiness, call/contact permissions, alarms/calendar handoff, camera, and actual YouTube Music audio playback on the target Android phone.
- **Backend verification required:** authenticated chat routing for Tutor/Medical/Mentalist/Emotional, Office file generation, pairing, memory and backup/restore.

## Release gates

1. Android and backend unit tests pass.
2. Phone and TV APKs compile.
3. The APK reports the exact launcher label `J.A.R.V.I.S`, contains the ARC launcher resource, and uses the independent package IDs in `app/build.gradle`.
4. Android 10 emulator test installs the phone APK, launches `MainActivity`, and verifies the launcher identity and WebView.
5. Backend tests cover engine routing, emergency bypass, file generation, music search, shared memory, pairing and backup.
6. Physical Android testing records actual results; never mark a feature complete solely because a button or bridge method exists.
7. Distinguish a suggestion, prepared action, OS handoff, executed action and independently verified result. Never report a handoff as completed.
