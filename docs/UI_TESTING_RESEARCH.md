# UI testing research and decision record

## Requirements being addressed

The Smart SMS additions must remain readable and usable under:

- light and dark DayNight themes;
- Android 12+ wallpaper-derived dynamic color;
- accessibility contrast settings where the platform supports them;
- narrow phones, tablets, landscape, split-screen, and large font scale;
- category filtering and expanded/collapsed service cards.

## Evaluated approaches

### 1. Android Accessibility Test Framework / Espresso AccessibilityChecks

Android's official Accessibility Test Framework, exposed through Espresso `AccessibilityChecks`,
automatically detects issues including text contrast, touch target size, labels, and traversal
problems. It is the preferred check when an AOSP device test has Espresso dependencies available.

Decision: keep the current dependency-free device test module functional on all matching AOSP trees,
and add `SmartSmsThemeContrastInstrumentationTest` with the same WCAG contrast math for mandatory
Smart SMS surfaces. When the ROM test environment provides Espresso/ATF, enable root-view checks as
an additional non-suppressing gate.

References:

- https://developer.android.com/training/testing/espresso/accessibility-checking
- https://developer.android.com/guide/topics/ui/accessibility

### 2. On-device Instrumentation layout tests

Instrumentation is the most accurate option for this application because it renders the actual
system Messaging package, platform resources, default-SMS permission state, Material Components,
and current system dynamic palette.

Decision: `messaging_smart_sms_ui_tests` is the authoritative behavior/layout suite. It injects
in-memory category rows into the live adapter, clicks actual Chips, measures real cards, and runs a
light/dark contrast test on device.

### 3. Screenshot / golden image tests

Official Android guidance notes that screenshot tests validate colors, margins, sizes, and fonts,
but recommends selecting representative configurations rather than testing every combination.

For a conventional public Gradle View project, Paparazzi can snapshot View hierarchies and
Roborazzi can parameterize Robolectric configurations such as night mode and device qualifiers.
This system app uses AOSP-private UI libraries and real dynamic color, so a JVM renderer may differ
from the system image. An on-device screenshot harness is preferred for release visual baselines.

Decision: use the device-side smoke tests for every ROM validation and add a screenshot baseline
harness only in the complete AOSP tree. Maintain a compact representative matrix rather than adding
all screenshots to the portable `smart-sms-core` CI job.

References:

- https://developer.android.com/training/testing/ui-tests/screenshot
- https://developer.android.com/agents/skills/testing/testing-setup/skill
- https://cashapp.github.io/paparazzi/accessibility/
- https://github.com/takahirom/roborazzi

### 4. Material dynamic-color token discipline

Material guidance states that dynamic color uses paired foreground/background roles, for example
`onPrimary` on `primary` and `onPrimaryContainer` on `primaryContainer`. Arbitrary hardcoded
foreground colors on arbitrary category colors are unsafe.

Decision: app surfaces resolve `colorSurface`, `colorOnSurface`, and `colorOnSurfaceVariant` from
the active theme. Category and status Chips retain their semantic background colors but choose the
black/white foreground with the best WCAG contrast. The app calls
`DynamicColors.applyToActivitiesIfAvailable()` before Activity creation.

References:

- https://developer.android.com/develop/ui/compose/designsystems/material3
- https://developer.android.com/design/ui/mobile/guides/styles/color
- https://developer.android.com/reference/com/google/android/material/color/ColorContrast
