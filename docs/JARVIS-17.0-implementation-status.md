# J.A.R.V.I.S. 17.0 foundation

## Scope in this branch

- Android package version: 17.0.0 (version code 1700); the existing application ID is intentionally preserved so this remains an in-place upgrade path for the current Android app.
- AI provider: the server prefers the OpenAI Responses API when `OPENAI_API_KEY` is configured. `OPENAI_MODEL` is optional and defaults to `gpt-6-astra`. Gemini remains a compatibility fallback when no OpenAI key is configured.
- Tool calling: the OpenAI provider adapts the existing audited J.A.R.V.I.S. allow-list. Tool execution remains server-side and allow-listed; no arbitrary code, shell, or arbitrary URL execution is exposed.
- Android app launch: the model can prepare a request to open a named non-financial app. The UI presents an explicit action button and the native Android bridge performs the app hand-off only after the user taps it.
- Existing pairing, owner identity, shared history, saved memories, file tools, specialist engines, media functions and Android permissions are retained from 16.0.

## Required server configuration

Set `OPENAI_API_KEY` in the Vercel project's server-side Environment Variables and redeploy the 17.0 branch/preview to use OpenAI intelligence. Never place this key in browser JavaScript, the Android manifest, or the APK.

Optional:
- `OPENAI_MODEL`: override the default model identifier.

If `OPENAI_API_KEY` is absent, the existing Gemini provider remains active when `GEMINI_API_KEY` (or the legacy `JARVIS` key) is configured.

## Live Voice status

The existing Live Voice client is still the Gemini transcription/WebSocket implementation and remains behind its explicit free-tier confirmation gate. The OpenAI chat provider does not automatically enable Live Voice. Do not enable the Gemini gate without the owner's confirmation of the billing/free-tier restriction; a separate Realtime/WebRTC implementation and physical-device test are still required for an OpenAI-powered live voice experience.

## Validation

GitHub Actions runs the Node test suite, JavaScript syntax checks, Android phone build, and Android unit tests on this branch. A successful build artifact is not proof of microphone, wake-word, contacts, call, or other hardware-dependent behaviour on the owner's physical phone. Those still require hands-on device testing.
