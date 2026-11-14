# J.A.R.V.I.S. — Fresh Android Build

Application ID: `com.hrishikesh.jarvis.eight.release`. This uses the J.A.R.V.I.S 8.0 package identity for an in-place upgrade path. Local encrypted memory is retained only when Android accepts the update as the same installed app.

## Implemented in source
- ARC-reactor launcher artwork and a rotating OpenGL ES 2.0 3D reactor with torus rings and a luminous core.
- Functional HUD-style panels grouping voice/system, device/music launch, and private-memory controls.
- User-started foreground wake listener for “Hey JARVIS”, “Wake up, JARVIS”, “Hello JARVIS”, and “JARVIS, are you there?”
- On Android 12+ the listener prefers Android's on-device speech recognizer when one is available; otherwise it uses the installed Android recognition provider and requests offline recognition.
- Command continuation after a wake phrase for app launch, camera, music search, and media controls.
- Native Camera2 preview with supported output-size selection, front/rear switching, and JPEG capture saved in the app's private Pictures folder.
- Strict Alfred voice gate: only an explicitly identified male en-GB Algieba/UK male voice is permitted; otherwise all spoken output is muted (no engine-default or female fallback).
- Fresh local conversation history encrypted with AES-GCM; the AES key is held by Android Keystore.
- UI to review and clear local memory.
- Voice commands for app launch, camera, and music search.

## Runtime limits
- Wake listening must be started by the user and is shown as a foreground notification. Android 10 does not expose the modern on-device recognizer API; offline behavior there depends on the installed speech provider honoring Android's offline preference.
- This is speech-recognition-based wake listening, not a dedicated low-power always-on hotword DSP. Battery use and recognition reliability depend on the phone and recognition provider.
- Android may restrict background microphone operation under battery-management policies. The service can be stopped by the system or user.
- Direct control of another app's media session is intentionally excluded from this sideload build because it requires notification-listener access, a sensitive capability that can trigger Play Protect's internet-sideloading block in supported markets.
- Music search opens YouTube Music search; it does not guarantee automatic track playback.
- App launch visibility depends on Android package visibility and installed launchable activities.
- Algieba depends on the installed TTS engine.
- Photos are stored in app-specific external Pictures storage and are not automatically published to the public gallery.
- Build/test success is not a substitute for physical-device validation of microphone, camera, media sessions, and background behavior.

## Still to build
- Dedicated low-power offline hotword engine for dependable always-on wake detection, including Android 10.
- Full Office authoring: multi-slide presentations, structured/formula-enabled spreadsheets, richer document layouts, multi-page PDF export, and PDF attachment extraction.
- A genuinely conversational AI Education engine with subject explanations, worked solutions, and fact-checked learning support; current study pack generation is an offline organiser.
- Owner-controlled encrypted pairing and optional multi-device sync, including a Windows companion.
- Expanded device commands, setup diagnostics, and broader Android compatibility.
- Physical-device validation on the owner's Android 10 phone.

## Data policy
The encrypted conversation database starts empty on a fresh install. Android app backup is disabled.


## Voice guarantee and current limits
- JARVIS will not intentionally fall back to a female or unclassified TTS voice. If the phone's installed TTS engine does not expose a qualifying UK male voice, JARVIS remains silent and reports that the Alfred voice is unavailable. The exact Alfred timbre and phrasing cannot be guaranteed by Android TTS alone.
- Basic offline Office export is included: text-based DOCX, single-slide PPTX, one-column XLSX, and a simple single-page PDF. These are starter templates, not a full-featured or AI-designed Office suite.
- Attachment text import supports TXT, CSV, Markdown, DOCX, PPTX, and XLSX. PDF text extraction is not implemented.
- The study tool builds a revision outline and self-test prompts from material supplied by the user. It is not an AI tutor and does not independently verify subject facts.
- Launcher icon and splash use a circular multi-ring ARC reactor design, with no triangular centrepiece.


## Alfred conversational persona (1.0.13)

## Security-focused sideload build (1.0.15)
- Removed the notification-listener service and third-party media-session control implementation from the APK.
- Removed the unused INTERNET permission; music links are handed to the user's browser/music app.
- The build workflow now fails if notification-listener declarations or media-session APIs return to the app source.
- This change targets the sensitive notification-listener capability identified in Google's Play Protect sideload warning guidance. It cannot guarantee a clean Play Protect verdict; Google performs its own APK classification and device-side checks.
- Offline conversational response layer is active for greetings, identity, thanks, philosophical prompts, relationship topics, opinions, follow-up requests, and unsupported action requests.
- Replies use a courteous professional register, dry British-style wit, and address the owner as Sir.
- Responses can use recent encrypted conversation history for follow-up context.
- Unsupported actions are explicitly identified; the persona does not claim that an unimplemented task has been completed.
- This is a deterministic on-device persona layer, not unrestricted generative AI. A genuinely open-ended conversational model requires a separately configured inference service or on-device model.
