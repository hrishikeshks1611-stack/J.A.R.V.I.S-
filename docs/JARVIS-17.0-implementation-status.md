# J.A.R.V.I.S. 17.0 foundation

## Scope in this branch

- Android package version: 17.0.0 (version code 1700); the existing application ID is intentionally preserved so this remains an in-place upgrade path for the current Android app.
- AI provider: the server prefers the OpenAI Responses API when `OPENAI_API_KEY` is configured. `OPENAI_MODEL` is optional and defaults to `gpt-6-astra`. Gemini remains a compatibility fallback when no OpenAI key is configured.
- Tool calling: the OpenAI provider adapts the existing audited J.A.R.V.I.S. allow-list. Tool execution remains server-side and allow-listed; no arbitrary code, shell, or arbitrary URL execution is exposed.
- Android app discovery/launch: the native bridge enumerates launchable installed apps (excluding financial apps) and can launch a uniquely matched app label after the user confirms. This opens apps; it does not grant control over their private screens or data.
- Cross-app sharing: a new allow-listed tool prepares text for Android's native Sharesheet. The user chooses any compatible installed target (for example messaging, email, notes or social apps) and completes the action there. No app is silently sent content.
- Native web hand-off: the Android bridge can open validated HTTP/HTTPS links in the user's selected browser. App-specific deep links and APIs must be added per provider.
- Existing pairing, owner identity, shared history, saved memories, file tools, specialist engines, media functions and Android permissions are retained from 16.0.

## Required server configuration

Set `OPENAI_API_KEY` in the Vercel project's server-side Environment Variables and redeploy the 17.0 branch/preview to use OpenAI intelligence. Never place this key in browser JavaScript, the Android manifest, or the APK.

Optional:
- `OPENAI_MODEL`: override the default model identifier.

If `OPENAI_API_KEY` is absent, the existing Gemini provider remains active when `GEMINI_API_KEY` (or the legacy `JARVIS` key) is configured.

## Live Voice status

The existing Live Voice client is still the Gemini transcription/WebSocket implementation and remains behind its explicit free-tier confirmation gate. The OpenAI chat provider does not automatically enable Live Voice. Do not enable the Gemini gate without the owner's confirmation of the billing/free-tier restriction; a separate Realtime/WebRTC implementation and physical-device test are still required for an OpenAI-powered live voice experience.

## Android integration boundaries\n\nAndroid app-to-app interoperability is capability-based, not universal remote control. The generic launcher and Sharesheet work with many installed apps through Android intents. Deeper actions (for example reading WhatsApp chats, posting to Instagram, controlling Spotify queues, editing Google Drive files or accessing another app's private data) require that app's supported public intents/deep links, SDK/API and often OAuth consent. An AccessibilityService is not a safe or reliable substitute for official APIs and must not be used for covert automation. Banking, UPI, wallet, payment and other financial apps remain excluded.\n\n## Validation

GitHub Actions runs the Node test suite, JavaScript syntax checks, Android phone build, and Android unit tests on this branch. A successful build artifact is not proof of microphone, wake-word, contacts, call, or other hardware-dependent behaviour on the owner's physical phone. Those still require hands-on device testing.
