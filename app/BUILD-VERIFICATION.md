# Android build verification

The Android project has two Gradle product flavors: `phone` and `tv`. The CI compile workflow builds both with:

```sh
gradle --no-daemon assemblePhoneDebug assembleTvDebug
```

These are compile-verification outputs only. They are not approved update APKs and must not be published as releases.

The installed Build 39 package was signed with a different certificate from later debug builds. Without the original Build 39 private signing keystore, Android will reject a replacement APK as an in-place update. Keep the existing phone and TV installations untouched until signing continuity is recovered and verified.

A successful CI build does not count as physical validation. Test phone permissions, Android 10 behavior, Android TV navigation, and WebView bridges on actual hardware before claiming device compatibility.
