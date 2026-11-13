# J.A.R.V.I.S. — Fresh Build

Clean-room Android application baseline with a new application ID
(com.hrishikesh.jarvis.fresh), a new version line, no imported app data,
and no dependency on the previous JARVIS source tree.

## Implemented and CI-verified
- Native Android launch screen with ARC-reactor artwork and animated rotation.
- Foreground microphone service started and stopped explicitly by the user.
- Four wake phrases: “Hey JARVIS”, “Wake up, JARVIS”, “Hello JARVIS”,
  and “JARVIS, are you there?”
- Command continuation after a wake phrase for app launch, camera intent,
  and music search.
- Unit tests for wake phrase matching and command parsing.
- CI builds and verifies a debug APK.

## Important runtime limitations
- Wake recognition uses Android's installed speech-recognition provider.
  Offline recognition depends on that provider and is not guaranteed.
- The user must grant microphone permission and explicitly start the
  foreground listening service. Android may still restrict microphone use
  under device-specific battery/background policies.
- Camera currently hands off to the phone's camera app; it is not yet
  an in-app camera preview/capture workflow.
- Music currently opens a search in the selected handler; it does not
  guarantee automatic playback or full background media control.
- Algieba is selected only if the installed TTS engine exposes that voice.
  Otherwise Android's configured UK English voice is used.
- Physical-device wake detection and launch behavior still require testing
  on the owner's Android 10 phone.

## Remaining implementation
Offline wake-word engine; full voice diagnostics and guaranteed voice choice;
in-app camera capture; active media-session controls; encrypted owner pairing
and optional sync; fresh local memory store; true 3D ARC-reactor HUD and
functional holographic panels; broader Android 10 and current-version testing.

## Data policy
No old conversations, databases, or device backups are imported. The app starts
with an empty local state.
