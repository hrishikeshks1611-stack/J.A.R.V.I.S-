# J.A.R.V.I.S. 7.0 — build and acceptance plan

## Product rule
JARVIS 7.0 is a separate Android application. It must not replace, update, uninstall, or overwrite JARVIS 6.0.0 during development or installation. The old installation remains available until the owner confirms 7.0 works properly and chooses to remove 6.0.0.

## Independent app identity
- Phone application ID: `com.hrishikesh.jarvis.seven`
- Android TV application ID: `com.hrishikesh.jarvis.seven.tv`
- Version: `7.0` / version code `700`
- Existing 6.0.0 package identity remains unchanged.
- Shared account and cloud memory are intentional; local app data and installation identity remain separate.

## Carry-forward feature inventory
- Core assistant, AI backend, Alfred-style voice and conversation
- Shared memory, backup, device pairing and owner recovery
- Office engine: PDF, DOCX, XLSX, PPTX, CSV, TXT and Markdown workflows
- Tutor engine: stepwise teaching, worked examples, quizzes, marking and revision planning
- Medical assistant: first aid, emergency red flags and safe medical information
- Mentalist engine: contextual observation, social cues, critical thinking and respectful communication
- Emotional availability behaviour
- YouTube Music integration and media controls
- Camera and microphone
- Contact calling and permission handling
- Moving 3D ARC reactor / futuristic HUD

## Android organiser and wake-word acceptance
- Wake-word settings persist across app restarts and device restarts where Android permits.
- Explicit microphone permission flow and visible service state.
- Verify foreground microphone service behaviour on the target Android version and OEM battery restrictions.
- Alarm creation opens the system Clock confirmation UI; verify a saved alarm and its label on-device.
- Clock screen can be opened directly.
- Calendar event creation opens the system Calendar editor; verify event save on-device.
- Reminder creation must provide a user-confirmed reminder attached to a calendar event or a supported Android reminder provider; do not report success before it is saved.
- Check notification permission and reminder notifications.

## Required regression tests before release
1. Build and package identity checks for phone and TV.
2. Confirm 7.0 installs alongside 6.0.0 without replacing it.
3. Confirm both apps can be launched independently.
4. Sign in / pair to the same owner account and confirm cloud memory continuity.
5. Run the full Node engine and API test suite.
6. Verify Office, Tutor, Medical, Mentalist and emotional availability in the app.
7. Physical-device tests: microphone, wake-word, background operation, camera, contact calling, YouTube Music/media controls, alarms, clock, calendar and reminders.
8. Verify Android permissions are requested only when needed and denied permissions fail safely.
9. Verify data isolation: deleting 6.0.0 after acceptance must not delete the shared cloud account or 7.0's local data.

## Release gate
A successful CI build is not proof of device functionality. Do not call 7.0 complete or distribute it as a release until the above tests are passed and the owner approves the result.
