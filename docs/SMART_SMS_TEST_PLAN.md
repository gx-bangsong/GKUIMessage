# Smart SMS test plan

This plan separates portable CI checks from device-side UI checks. The split is intentional: the
rule engine can run in an ordinary GitHub Actions runner, while the AOSP Messaging user interface
requires a system-built package with SMS role and privileged permissions.

## 1. Automated GitHub Actions tests

Run with:

```bash
gradle :smart-sms-core:testDebugUnitTest :smart-sms-core:assembleDebug
```

| ID | Test class / case | Expected result |
|---|---|---|
| OTP-01 | `OtpRecognizerTest.lowerPriorityRuleDoesNotOverrideFirstPriorityMatch` | The numerically smaller priority rule wins. |
| OTP-02 | `OtpRecognizerTest.disabledRuleIsSkippedInFavorOfEnabledRule` | Disabled rules cannot produce a code. |
| OTP-03 | `OtpRecognizerTest.malformedRuleDoesNotPreventLaterValidRule` | A malformed custom expression is isolated. |
| OTP-04 | `OtpRecognizerTest.patternWithoutCaptureGroupReturnsWholeMatch` | Whole-match fallback supports valid patterns without group 1. |
| OTP-05 | `OtpRecognizerTest.noMatchingRuleReturnsNull` | Ordinary SMS content does not create an OTP result. |
| CAT-01 | `ConversationClassifierTest.contactAlwaysWinsOverRules` | Known contacts always map to `PERSONAL`. |
| CAT-02 | `ConversationClassifierTest.highestAccumulatedWeightWins` | All matching category weights are accumulated and the highest total wins. |
| CAT-03 | `ConversationClassifierTest.senderPrefixRuleMatchesNormalizedNumber` | A `+86` prefixed sender still matches a domestic short-code prefix. |
| CAT-04 | `ConversationClassifierTest.invalidRegexAndDisabledRulesAreIgnored` | Invalid and disabled rules cannot affect classification. |
| CAT-05 | `ConversationClassifierTest.equalScoreUsesLowerSortOrderThenCategoryId` | Equal scores resolve deterministically. |
| CARD-01 | `ServiceSmsParserTest.extractsExpressTrackingAndPickupCode` | Tracking number and pickup code are extracted from a delivery SMS. |
| CARD-02 | `ServiceSmsParserTest.senderPrefixTriggersRuleWithoutKeyword` | Sender-prefix triggers work without a keyword. |
| CARD-03 | `ServiceSmsParserTest.requiredFieldMissingFallsBelowMinimumConfidence` | Cards with insufficient required fields are suppressed. |
| CARD-04 | `ServiceSmsParserTest.malformedFieldPatternDoesNotBlockOtherFields` | One malformed field expression does not block valid extraction. |
| CARD-05 | `ServiceSmsParserTest.confidenceThresholdIsRespected` | User-selected minimum confidence is enforced. |
| UI-C-01 | `UiLayoutContractTest.otpSettingsExposesRequiredControls` | OTP switches, rule list, and FAB remain declared in the actual layout. |
| UI-C-02 | `UiLayoutContractTest.categorySettingsExposesManagementControls` | Category list, reset action, and add action remain declared. |
| UI-C-03 | `UiLayoutContractTest.cardSettingsExposesParserAndCacheControls` | Card parser, confidence, history, cache, import/export controls remain declared. |
| UI-C-04 | `UiLayoutContractTest.conversationListHasHiddenCategoryStripUntilMultipleCategoriesExist` | Category strip starts hidden until more than ALL is available. |
| UI-C-04B | `UiLayoutContractTest.categoryStripIsDeclaredBeforeConversationListContainer` | Chips are structurally declared between the ActionBar content boundary and the list container. |
| UI-C-05 | `UiLayoutContractTest.conversationRowCategoryChipAndMessageCardStartHidden` | RecyclerView reuse cannot show a stale category chip/card before binding. |
| UI-C-06 | `UiLayoutContractTest.applicationPreferencesNavigateToAllSmartSmsSettings` | Application preferences still target OTP, category, and card activities. |
| UI-C-07 | `UiLayoutContractTest.otpNotificationCopyReceiverIsPrivate` | The notification clipboard action can only reach the app's non-exported receiver. |

`UiLayoutContractTest` parses the **actual project XML files** from Gradle. It is a structural UI
regression test, not a screenshot pixel test.

## Conversation-list hierarchy note

The current AOSP Messaging branch does **not** expose a search bar in
`ConversationListActivity`; its expected hierarchy is therefore:

```text
ActionBar / Toolbar
  → category_chip_strip
  → RecyclerView (@android:id/list)
```

`UI-C-04B` and `UI-D-05` enforce this order. If a search control is added later, update those
tests to assert `ActionBar → search bar → category_chip_strip → RecyclerView` rather than assuming
a search view that does not exist in this source branch.

## 2. AOSP device-side UI smoke tests

Build and run only in a matching AOSP/LineageOS tree after building the target package:

```bash
source build/envsetup.sh
lunch lineage_hotdog-userdebug
m messaging messaging_smart_sms_ui_tests
atest messaging_smart_sms_ui_tests
```

Before running, ensure Messaging is the default SMS app and has its SMS/contact runtime
permissions. The suite deliberately fails if an activity redirects to `PermissionCheckActivity`.

| ID | Instrumentation case | Expected result |
|---|---|---|
| UI-D-01 | `testOtpSettingsControlsAreVisible` | OTP automatic-copy, Toast, notification-copy switches, rules list, and FAB render visibly. |
| UI-D-01B | `testOtpSettingsSwitchesToggleAndRestoreState` | All OTP switches accept interaction and restore their stored state. |
| UI-D-01C | `testOtpNotificationCopyActionCopiesSensitiveCode` | The notification PendingIntent copies the OTP and marks the clipboard content sensitive. |
| UI-D-01D | `testCardParserSwitchTogglesAndRestoresState` | The card parser master switch accepts interaction and restores state. |
| UI-D-02 | `testCategorySettingsControlsAreVisible` | Category RecyclerView, reset action, and extended add FAB render visibly. |
| UI-D-03 | `testCardSettingsControlsAreVisible` | Card enable switch, confidence Slider, type container, history/import/export controls render visibly. |
| UI-D-04 | `testConversationLayoutsInflateAndInitialStatesAreCorrect` | Conversation layouts inflate in the target app and hidden-before-bind views start hidden. |
| UI-D-05 | `testCategoryChipsAreBelowActionBarAndAboveConversationList` | The dynamic chip strip is visibly between the app ActionBar content boundary and RecyclerView, with no overlap. |
| UI-D-06 | `testCategoryChipClickFiltersAndAllChipRestoresConversations` | A real chip click filters injected live adapter data; ALL restores every row. |
| UI-D-07 | `testCategoryChipStripScrollsOnNarrowContent` | Many long category chips overflow into a functioning horizontal scroll region instead of clipping. |
| UI-D-08 | `testExpandedCardFitsWidthMatrixAndActionsRemainScrollable` | A card is measured at 320/360/411/600/720/1024dp; it stays above the bubble with an 8dp gap, stays inside bounds, and action overflow scrolls. |
| UI-D-09 | `testCardFitsNarrowLargeFontConfiguration` | A 1.5x font-scale card remains within a 320dp message row and retains an action viewport. |

The instrumentation class is:

```text
tests/src/com/android/messaging/tests/SmartSmsUiInstrumentationTest.java
```

and its Soong target is:

```text
messaging_smart_sms_ui_tests
```

## 3. Manual end-to-end device checklist

These cases need a physical SIM/device or a controlled SMS injector and are not faked by unit
tests:

1. Receive an OTP SMS and verify a single automatic clipboard copy, Toast behavior, and 60-second
   clipboard ownership cleanup.
2. Disable OTP auto-copy, receive another OTP, verify the notification has a "Copy code" action,
   tap it, and confirm that the code is copied. Also verify disabling the notification-action switch
   removes that action.
3. Add, reorder, disable, and delete category rules; verify category Chips and unread counts update
   after the background reclassification notification completes.
4. Receive finance, express, train, flight, appointment, and recharge examples; verify only cards
   meeting the configured confidence threshold render.
5. Verify a 0.5–0.79 confidence card starts collapsed, a >=0.8 card starts expanded, and expand
   state persists after leaving and reopening the conversation.
6. Repeat category and card checks at 320/360/411/600/720/1024dp widths, portrait and landscape,
   1.0x/1.3x/1.5x font scale, RTL, split-screen, and any target foldable/tablet posture.
7. Exercise copy, browser, dialer, calendar, and flight-status actions and ensure missing external
   apps fall back safely to browser intents.
8. Import an invalid JSON rule and confirm it is rejected; import a valid rule and confirm it only
   affects local parsing.
9. Verify no network request, external `ContentProvider`, or telemetry is emitted while parsing.

## 4. Theme, dynamic color, and contrast checks

The application now calls Material `DynamicColors.applyToActivitiesIfAvailable()` during
`BugleApplication.onCreate()`. Android 12+ therefore receives the current wallpaper-derived
Material palette before activities are created; earlier releases retain the existing DayNight
fallback.

| ID | Instrumentation case | Expected result |
|---|---|---|
| THEME-01 | `testReadableThemePairsInLightMode` | `colorOnSurface` meets the 4.5:1 body-text threshold against `colorSurface` in light mode. |
| THEME-02 | `testReadableThemePairsInDarkMode` | The same theme pair remains readable in dark mode. |
| THEME-03 | `testCategoryForegroundAlgorithmMeetsBodyTextContrast` | Every built-in category color receives its best black/white foreground with >=4.5:1 contrast. |
| THEME-04 | `testRenderedCardUsesReadableThemeAndStatusColors` | Actual rendered card title and status Chip satisfy the body-text threshold. |

Implementation:

```text
tests/src/com/android/messaging/tests/SmartSmsThemeContrastInstrumentationTest.java
```

The test forces both system night modes through `UiAutomation`, launches real target activities,
checks resolved Material theme attributes, and traverses visible text that has a solid inherited
background. It also renders an actual `SmsCardView` through the target app and checks its title and
status Chip foreground/background pairs.

The test uses the WCAG AA thresholds: >=4.5:1 for normal body text and >=3.0:1 for large or bold
text. It does not claim that a single currently selected wallpaper proves every possible OEM dynamic
palette. The manual matrix below covers additional wallpaper palettes and contrast settings.

## 5. Screenshot-testing recommendation

The core CI performs behavior, layout-contract, and contrast-math checks. For visual golden-image
regressions in a full AOSP environment, prefer an on-device screenshot harness over a JVM-only
renderer because this app uses AOSP-private View libraries and Android system dynamic colors.

Recommended screenshot matrix:

```text
Themes:         light, dark, high-contrast where supported
Wallpapers:     at least 3 materially different dynamic palettes
Widths:         320, 360, 411, 600, 720, 1024dp
Font scale:     1.0x, 1.3x, 1.5x
States:         ALL / filtered category / card collapsed / card expanded
```

Store a small set of approved golden images; run the narrow-phone light/dark smoke cases per PR and
the extended wallpaper/device matrix nightly or before release. Do not generate a full Cartesian
product on every change.
