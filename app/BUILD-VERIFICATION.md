# Android build verification

The Android project has two Gradle product flavors: `phone` and `tv`. The CI compile workflow builds both with:

```sh
gradle --no-daemon assemblePhoneDebug assembleTvDebug
```

These are compile-verification outputs only. They are not approved update APKs and must not be published as releases.

The installed Build 39 package was signed with a different certificate from later debug builds. Without the original Build 39 private signing keystore, Android will reject a replacement APK as an in-place update. Keep the existing phone and TV installations untouched until signing continuity is recovered and verified.

A successful CI build does not count as physical validation. Test phone permissions, Android 10 behavior, Android TV navigation, and WebView bridges on actual hardware before claiming device compatibility.

## Verified 6.0.0 to 6.0.1 update test

A dedicated Android 10 emulator test was run against the archived JARVIS 6.0.0 phone APK from workflow run 43410843193 and a freshly built 6.0.1 phone APK from the repair branch.

- The archived 6.0.0 APK installed successfully on the Android 10 emulator.
- A marker file was written into the installed app's private data directory before the update attempt.
- Android rejected the 6.0.1 replacement with `INSTALL_FAILED_UPDATE_INCOMPATIBLE`.
- The test therefore did not reach the data-preservation check; no claim is made that an update or migration succeeded.
- The 6.0.1 APK itself built successfully, but it is not approved for an in-place update over the archived 6.0.0 build.
- Do not publish this build with `JARVIS_UPDATE_COMPATIBLE=true`.

Run: https://github.com/hrishikeshks1611-stack/J.A.R.V.I.S-/actions/runs/43452996308

This was an automated emulator test, not a test on the user's physical phone. Keep the existing phone and TV installations untouched until a genuinely compatible update has been independently verified.
