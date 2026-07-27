# Gradle dual-build support

This repository remains an AOSP/LineageOS system application. **`Android.bp` is required for
ROM builds** and remains the authoritative production build definition.

Two Gradle targets are intentionally supplied:

| Target | Intended use | AOSP-private dependencies required? |
|---|---|---|
| `:smart-sms-core` | CI, parser/classifier unit tests, and standalone AAR builds | No |
| `:messagingGradle` | Android Studio navigation and experimental full Messaging APK work | Yes |

## `:smart-sms-core`: portable feature logic

`smart-sms-core` contains the platform-independent logic for:

- priority-ordered OTP recognition;
- contact-first, weighted SMS conversation classification;
- service-SMS trigger matching, field extraction, and confidence scoring.

The Android application delegates its OTP, category, and card parsing rule decisions to this
module. `Android.bp` compiles the same Java sources into the system app, while Gradle builds the
module as a standard Android Library without any proprietary AOSP jar.

Use JDK 17 and a locally installed Gradle 8.7 or newer:

```bash
gradle :smart-sms-core:testDebugUnitTest
gradle :smart-sms-core:assembleDebug
```

The generated artifact is:

```text
smart-sms-core/build/outputs/aar/smart-sms-core-debug.aar
```

## GitHub Actions

`.github/workflows/assemble-debug.yml` runs the two commands above on pull requests, pushes to
`lineage-23.2` or `arena/**`, and manual dispatches. It only needs JDK 17, Gradle 8.7, plus Android API 35; it
does **not** need Secrets, AOSP jars, a device tree, or the `hotdog` target. Successful runs upload
the debug AAR for 14 days.

This is the recommended CI signal for modules 1–3. The production system app must still be
validated with `m messaging` in a matching LineageOS/AOSP source tree.

## `:messagingGradle`: optional full-app companion

The `messagingGradle` Android Studio companion reads the canonical `src`, `res`, `assets`, and
`AndroidManifest.xml` files without duplicating sources. A full `:messagingGradle:assembleDebug`
requires AOSP-private Java and resource dependencies such as `libchips`, `libphotoviewer`, and
`com.android.vcard`; a few exported JARs alone may not include all resources needed by the
complete AOSP UI.

Therefore this target is not the default CI gate. Prefer the Soong build below for a real system
APK:

```bash
source build/envsetup.sh
lunch lineage_hotdog-userdebug
m messaging
```

`hotdog` affects the final ROM product, not the portable Smart SMS rule engine. For generic
feature-logic development and GitHub Actions, `:smart-sms-core` is sufficient and device-neutral.
