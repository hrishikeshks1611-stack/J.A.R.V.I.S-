# J.A.R.V.I.S. 16.0 — fresh owner and empty memory deployment

This is an isolated first-install path. It must not use the old owner's Redis data, restore an old backup, or call the legacy recovery/owner-transfer flow.

## Required deployment configuration

Create a **new, empty Redis database** for this instance (preferred). If a separate database is not available, use a unique prefix that has never been used by another J.A.R.V.I.S. deployment. Configure:

- `JARVIS_FRESH_INSTANCE=true`
- `JARVIS_PREFIX=jarvis:fresh:16:<unique-instance-id>:` (unique, trailing colon)
- `UPSTASH_REDIS_REST_URL` and `UPSTASH_REDIS_REST_TOKEN` for that new database
- `JARVIS_SETUP_CODE` as a high-entropy, one-time bootstrap secret supplied out-of-band (never put it in the APK, repository, URL, or logs)
- The AI provider credentials required by the selected enabled features

The server refuses to start in fresh-instance mode if `JARVIS_PREFIX` is missing or falls back to the legacy `jarvis:v1:` namespace. The unique prefix is isolation, not a substitute for a dedicated database when one is available.

## First phone becomes owner

1. Deploy the backend with the isolated empty database/namespace and the one-time `JARVIS_SETUP_CODE`.
2. Point the J.A.R.V.I.S. 16.0 client at this new backend.
3. On the user's current phone, open **Devices & Sync** and use **Make this the owner device** with the one-time setup code.
4. Verify the response role is `owner`, then verify `/api/pair` action `verify` reports the same phone as owner.
5. Confirm the new conversation history and saved-memory endpoints both return empty arrays before the first user interaction.
6. Remove/rotate `JARVIS_SETUP_CODE` after the first owner claim. Future devices use owner-issued pairing codes only.

The existing `claim` action creates an owner session only when no owner record exists. A device-only session must never be created as the first device. The ordinary `pair` action requires an owner-issued, one-time pairing code.

## Data preservation boundary

Do not point this deployment at the old Redis database, do not reuse its prefix, and do not import its backup. Do not clear or mutate the old database as part of this rollout. This produces a genuinely empty new J.A.R.V.I.S. memory while leaving the previous instance untouched.

## Release gate

Do not announce this as live until the new backend is deployed, the phone is configured to use it, the first phone is verified as `owner`, empty memory is confirmed, and a second-device pairing test succeeds. A passing unit test or APK compilation alone is not proof of these live behaviors.
