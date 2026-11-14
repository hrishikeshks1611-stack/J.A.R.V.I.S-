# J.A.R.V.I.S 15.0 — Separate Install, Shared Identity, Safe Cutover

## Intended setup

- J.A.R.V.I.S 14.0 remains installed and unchanged as the protected original.
- J.A.R.V.I.S 15.0 installs side-by-side under its own Android application ID: `com.hrishikesh.jarvis.fifteen.release`.
- Phone and Windows clients pair to the same J.A.R.V.I.S backend identity.
- Shared conversation history and user-approved saved memories are server-side. Pairing a new client grants access to that shared data without copying the old app's private Android storage.
- The old 14.0 session remains valid until the user explicitly approves cutover.

## Safe pairing and cutover sequence

1. Keep 14.0 installed and signed in. Do not clear its app data or revoke its session.
2. Build and install the 15.0 phone variant. Confirm Android lists it as a separate app/package and that 14.0 still opens.
3. In 14.0, open **Devices & Sync** and create a one-time pairing code.
4. In 15.0, open **Devices & Sync** and pair with that code.
5. Verify 15.0 can load the shared conversation history and saved memories. Verify the device list shows both installations with distinct device IDs.
6. Test normal chat and memory read/write from 15.0. Confirm changes appear in the shared backend from the other paired client.
7. Only after the user approves ownership cutover, use the existing **Transfer owner here** action for the 15.0 device.
8. Re-open **Devices & Sync** in 15.0 and call/perform role verification. It must explicitly report **OWNER**. If it does not, stop; do not revoke anything.
9. From 15.0's owner device list, revoke the old 14.0 device session. Confirm the API reports success and 14.0 is no longer listed as paired.
10. Confirm 14.0 still remains installed and its local data has not been intentionally cleared. Its cloud-backed actions should now fail authentication until it is paired again.

## Important boundaries

- Revocation removes the server-side session and device record. It does not uninstall the app or wipe its private local storage.
- Shared conversation and memory sync does not transfer Android-local alarms, reminders, contacts permissions, camera permissions, microphone permissions, wake-word model files, media notification access, or other OS grants.
- Re-authorise and validate device-local capabilities on 15.0 independently before revoking 14.0.
- Do not revoke 14.0 merely because pairing succeeded. Verify 15.0 ownership and all required local capabilities first.
- If pairing, sync, owner transfer, or verification fails, leave 14.0 paired and operational. Do not improvise a destructive migration.
- The current backend's pairing code is one-time and expires after 10 minutes.
- Windows pairing should be verified as its own device session; it must not be assumed to inherit Android-only permissions.

## Release checks

- [ ] 14.0 and 15.0 install side-by-side.
- [ ] Both apps open and retain their own local data.
- [ ] Both have distinct server-side device IDs.
- [ ] 15.0 reads shared history and saved memories.
- [ ] 15.0 writes shared history and approved memories correctly.
- [ ] Device list identifies each app clearly.
- [ ] Ownership transfer is verified from 15.0.
- [ ] Revoking 14.0 removes only its server session/device record.
- [ ] 14.0 remains installed with local data intact.
- [ ] 15.0 local permissions and native features are independently validated.
