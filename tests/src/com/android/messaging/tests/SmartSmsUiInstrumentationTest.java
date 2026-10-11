/*
 * Copyright (C) 2026
 * SPDX-License-Identifier: Apache-2.0
 */
package com.android.messaging.tests;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.ClipData;
import android.content.ClipDescription;
import android.content.ClipboardManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.os.SystemClock;
import android.test.InstrumentationTestCase;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

/**
 * Device-side visual smoke suite for the AOSP-built Messaging package.
 *
 * Preconditions: the target Messaging app must be installed, granted its normal SMS/contact
 * permissions, and assigned the SMS role. The settings-activity cases deliberately fail if the
 * target redirects to PermissionCheckActivity, which prevents a false green result on an invalid
 * device test setup.
 */
public final class SmartSmsUiInstrumentationTest extends InstrumentationTestCase {
    private static final String TARGET_PACKAGE = "com.android.messaging";

    public void testOtpSettingsControlsAreVisible() {
        final Activity activity = launch("com.android.messaging.ui.otp.OtpSettingsActivity");
        try {
            assertVisible(activity, "otp_auto_copy_switch", "MaterialSwitch");
            assertVisible(activity, "otp_show_toast_switch", "MaterialSwitch");
            assertVisible(activity, "otp_notification_copy_action_switch", "MaterialSwitch");
            assertVisible(activity, "otp_rules_list", "RecyclerView");
            assertVisible(activity, "otp_add_rule", "FloatingActionButton");
        } finally {
            activity.finish();
        }
    }

    public void testOtpSettingsSwitchesToggleAndRestoreState() {
        final Activity activity = launch("com.android.messaging.ui.otp.OtpSettingsActivity");
        try {
            assertToggleChangesAndRestores(activity, "otp_auto_copy_switch");
            assertToggleChangesAndRestores(activity, "otp_show_toast_switch");
            assertToggleChangesAndRestores(activity, "otp_notification_copy_action_switch");
        } finally {
            activity.finish();
        }
    }

    public void testOtpNotificationCopyActionCopiesSensitiveCode() throws Exception {
        final Activity activity = launch("com.android.messaging.ui.otp.OtpSettingsActivity");
        try {
            final Context targetContext = getInstrumentation().getTargetContext();
            final ClipboardManager clipboard =
                    targetContext.getSystemService(ClipboardManager.class);
            assertNotNull("ClipboardManager is required", clipboard);

            final String messageId = "notification-action-test-" + System.currentTimeMillis();
            final String expectedCode = "827361";
            final Class<?> receiver = targetContext.getClassLoader().loadClass(
                    "com.android.messaging.receiver.OtpCopyReceiver");
            final Method createPendingIntent = receiver.getMethod("getPendingIntent",
                    Context.class, String.class, String.class);
            final PendingIntent pendingIntent = (PendingIntent) createPendingIntent.invoke(null,
                    targetContext, messageId, expectedCode);
            try {
                pendingIntent.send();
                waitUntil(() -> {
                    final ClipData clip = clipboard.getPrimaryClip();
                    return clip != null && clip.getItemCount() == 1
                            && expectedCode.contentEquals(clip.getItemAt(0).getText());
                }, "Notification copy action did not copy the OTP");

                final ClipData clip = clipboard.getPrimaryClip();
                assertNotNull(clip);
                assertNotNull("OTP clipboard description should exist", clip.getDescription());
                assertNotNull("OTP clipboard should be marked sensitive",
                        clip.getDescription().getExtras());
                assertTrue("OTP clipboard should be marked sensitive",
                        clip.getDescription().getExtras().getBoolean(
                                ClipDescription.EXTRA_IS_SENSITIVE));
            } finally {
                final ClipData clip = clipboard.getPrimaryClip();
                if (clip != null && clip.getItemCount() == 1
                        && expectedCode.contentEquals(clip.getItemAt(0).getText())) {
                    clipboard.clearPrimaryClip();
                }
            }
        } finally {
            activity.finish();
        }
    }

    public void testCardParserSwitchTogglesAndRestoresState() {
        final Activity activity = launch(
                "com.android.messaging.ui.cardsettings.SmsCardSettingsActivity");
        try {
            assertToggleChangesAndRestores(activity, "sms_card_enabled");
        } finally {
            activity.finish();
        }
    }

    public void testCategorySettingsControlsAreVisible() {
        final Activity activity = launch("com.android.messaging.ui.category.CategorySettingsActivity");
        try {
            assertVisible(activity, "category_settings_list", "RecyclerView");
            assertVisible(activity, "category_reset_button", "MaterialButton");
            assertVisible(activity, "category_add_button", "ExtendedFloatingActionButton");
        } finally {
            activity.finish();
        }
    }

    public void testCardSettingsControlsAreVisible() {
        final Activity activity = launch(
                "com.android.messaging.ui.cardsettings.SmsCardSettingsActivity");
        try {
            assertVisible(activity, "sms_card_enabled", "MaterialSwitch");
            assertVisible(activity, "sms_card_confidence", "Slider");
            assertVisible(activity, "sms_card_type_container", "LinearLayout");
            assertVisible(activity, "sms_card_history", "MaterialButton");
            assertVisible(activity, "sms_card_import", "MaterialButton");
            assertVisible(activity, "sms_card_export", "MaterialButton");
        } finally {
            activity.finish();
        }
    }

    public void testCategoryChipsAreBelowActionBarAndAboveConversationList() throws Exception {
        final Activity activity = launchConversationList();
        try {
            final Object fragment = injectConversationCategories(activity, 3, 2);
            final HorizontalScrollView strip = (HorizontalScrollView) activity.findViewById(
                    targetId("category_chip_strip"));
            final View list = activity.findViewById(android.R.id.list);
            waitUntil(() -> strip.getVisibility() == View.VISIBLE && strip.getHeight() > 0
                    && list.getHeight() > 0, "Category strip did not become visible");
            assertTrue("Chip strip must be above the conversation list", strip.getTop() < list.getTop());
            assertTrue("Chip strip must not overlap the conversation list",
                    strip.getBottom() <= list.getTop());
            assertTrue("Chip strip must remain inside the activity content below the ActionBar",
                    screenY(strip) >= screenY(activity.getWindow().getDecorView()));
        } finally {
            activity.finish();
        }
    }

    public void testCategoryChipClickFiltersAndAllChipRestoresConversations() throws Exception {
        final Activity activity = launchConversationList();
        try {
            final Object fragment = injectConversationCategories(activity, 3, 2);
            final LinearLayout container = (LinearLayout) activity.findViewById(
                    targetId("category_chip_container"));
            waitUntil(() -> container.getChildCount() >= 3, "Category chips were not rendered");
            final Object adapter = privateField(fragment, "mAdapter");
            waitUntil(() -> adapterItemCount(adapter) == 3, "All conversations were not rendered");

            final TextView expressChip = findChip(container, "快递");
            getInstrumentation().runOnMainSync(expressChip::performClick);
            waitUntil(() -> adapterItemCount(adapter) == 2,
                    "Express chip did not filter the conversation list");
            assertEquals("Express category id must become the active adapter filter", 5,
                    adapterCategoryFilter(adapter));
            final TextView selectedExpressChip = findChip(container, "快递");
            assertTrue("Clicked express chip must enter selected state",
                    selectedExpressChip instanceof CompoundButton
                            && ((CompoundButton) selectedExpressChip).isChecked());

            final TextView allChip = findChip(container, "全部");
            getInstrumentation().runOnMainSync(allChip::performClick);
            waitUntil(() -> adapterItemCount(adapter) == 3,
                    "All chip did not restore the complete conversation list");
            assertEquals("ALL must restore the default adapter filter", 1,
                    adapterCategoryFilter(adapter));
        } finally {
            activity.finish();
        }
    }

    public void testCategoryChipStripScrollsOnNarrowContent() throws Exception {
        final Activity activity = launchConversationList();
        try {
            injectConversationCategories(activity, 12, 2);
            final HorizontalScrollView strip = (HorizontalScrollView) activity.findViewById(
                    targetId("category_chip_strip"));
            final LinearLayout container = (LinearLayout) activity.findViewById(
                    targetId("category_chip_container"));
            waitUntil(() -> strip.getWidth() > 0 && container.getWidth() > strip.getWidth(),
                    "Many category chips should overflow horizontally instead of being clipped");
            getInstrumentation().runOnMainSync(() -> strip.fullScroll(View.FOCUS_RIGHT));
            assertTrue("Horizontal chip strip must scroll to reveal trailing categories",
                    strip.getScrollX() > 0);
        } finally {
            activity.finish();
        }
    }

    public void testExpandedCardFitsWidthMatrixAndActionsRemainScrollable() throws Exception {
        final Activity activity = launch("com.android.messaging.ui.otp.OtpSettingsActivity");
        try {
            final View message = inflateTargetLayout(activity, "conversation_message_view");
            final View card = message.findViewById(targetId("sms_card_view"));
            renderFlightCard(card);
            final View bubble = message.findViewById(targetId("message_content"));
            final HorizontalScrollView actions = (HorizontalScrollView) privateField(card,
                    "mActionsScroll");
            final int[] widthsDp = {320, 360, 411, 600, 720, 1024};
            boolean narrowActionOverflow = false;
            for (final int widthDp : widthsDp) {
                final int widthPx = dp(activity, widthDp);
                measureAndLayout(message, widthPx);
                assertEquals("Card must remain visible at " + widthDp + "dp", View.VISIBLE,
                        card.getVisibility());
                assertTrue("Card left edge escaped at " + widthDp + "dp", card.getLeft() >= 0);
                assertTrue("Card right edge escaped at " + widthDp + "dp",
                        card.getRight() <= message.getWidth());
                assertTrue("Card must be above the message bubble at " + widthDp + "dp",
                        card.getTop() <= bubble.getTop());
                assertEquals("Card-to-bubble gap must remain 8dp at " + widthDp + "dp",
                        dp(activity, 8), bubble.getTop() - card.getBottom());
                assertTrue("Card action viewport must fit card width at " + widthDp + "dp",
                        actions.getWidth() <= card.getWidth());
                if (widthDp == 320) {
                    narrowActionOverflow = actions.getChildAt(0).getWidth() > actions.getWidth();
                    getInstrumentation().runOnMainSync(() -> actions.fullScroll(View.FOCUS_RIGHT));
                    if (narrowActionOverflow) {
                        assertTrue("Multi-action cards must scroll rather than crop actions on narrow widths",
                                actions.getScrollX() > 0);
                    }
                }
            }
        } finally {
            activity.finish();
        }
    }

    public void testCardFitsNarrowLargeFontConfiguration() throws Exception {
        final Activity activity = launch("com.android.messaging.ui.otp.OtpSettingsActivity");
        try {
            final Configuration configuration = new Configuration(activity.getResources()
                    .getConfiguration());
            configuration.fontScale = 1.5f;
            final Context scaled = activity.createConfigurationContext(configuration);
            final int theme = targetResources().getIdentifier("BugleTheme", "style", TARGET_PACKAGE);
            assertTrue("Target Bugle theme must exist", theme != 0);
            final Context themed = new ContextThemeWrapper(scaled, theme);
            final View message = inflateTargetLayout(themed, "conversation_message_view");
            final View card = message.findViewById(targetId("sms_card_view"));
            renderFlightCard(card);
            measureAndLayout(message, dp(themed, 320));
            assertTrue("Large-font card must remain within a narrow message row",
                    card.getLeft() >= 0 && card.getRight() <= message.getWidth());
            final HorizontalScrollView actions = (HorizontalScrollView) privateField(card,
                    "mActionsScroll");
            assertTrue("Large-font action viewport must remain inside the card",
                    actions.getWidth() <= card.getWidth());
        } finally {
            activity.finish();
        }
    }

    public void testConversationLayoutsInflateAndInitialStatesAreCorrect() {
        final Activity activity = launch("com.android.messaging.ui.otp.OtpSettingsActivity");
        try {
            final View list = inflateTargetLayout(activity, "conversation_list_fragment");
            assertView(list, "category_chip_strip", "HorizontalScrollView", View.GONE);
            assertView(list, "category_chip_container", "LinearLayout", View.VISIBLE);

            final View row = inflateTargetLayout(activity, "conversation_list_item_view");
            assertView(row, "conversation_category_chip", "Chip", View.GONE);

            final View message = inflateTargetLayout(activity, "conversation_message_view");
            assertView(message, "sms_card_view", "SmsCardView", View.GONE);
        } finally {
            activity.finish();
        }
    }

    private Activity launchConversationList() {
        return launch("com.android.messaging.ui.conversationlist.ConversationListActivity");
    }

    /** Injects deterministic in-memory rows through the live fragment adapter test seam. */
    private Object injectConversationCategories(final Activity activity, final int categoryCount,
            final int expressConversationCount) throws Exception {
        final Object fragment = conversationListFragment(activity);
        final Object adapter = privateField(fragment, "mAdapter");
        final java.util.concurrent.ExecutorService executor =
                (java.util.concurrent.ExecutorService) privateField(fragment, "mCategoryExecutor");
        executor.shutdownNow();
        final MatrixCursor cursor = testConversationCursor(expressConversationCount);
        getInstrumentation().runOnMainSync(() -> {
            try {
                invoke(adapter, "submitCursor", new Class<?>[] {Cursor.class}, cursor);
                final List<Object> categories = (List<Object>) privateField(fragment, "mCategories");
                categories.clear();
                categories.add(category(1, "全部", "ALL", 0));
                categories.add(category(5, "快递", "EXPRESS", 10));
                for (int index = 0; index < categoryCount - 2; index++) {
                    categories.add(category(100 + index, "测试分类" + index + "很长的名称", "CUSTOM",
                            20 + index));
                }
                invokeDeclared(fragment, "buildCategoryChips", new Class<?>[0]);
            } catch (final Exception exception) {
                throw new RuntimeException(exception);
            }
        });
        return fragment;
    }

    private Object conversationListFragment(final Activity activity) throws Exception {
        final Object manager = invoke(activity, "getSupportFragmentManager", new Class<?>[0]);
        final long deadline = SystemClock.uptimeMillis() + 4000L;
        while (SystemClock.uptimeMillis() < deadline) {
            final List<?> fragments = (List<?>) invoke(manager, "getFragments", new Class<?>[0]);
            for (final Object fragment : fragments) {
                if (fragment != null && fragment.getClass().getName().equals(
                        "com.android.messaging.ui.conversationlist.ConversationListFragment")) {
                    return fragment;
                }
            }
            getInstrumentation().waitForIdleSync();
            SystemClock.sleep(50L);
        }
        throw new AssertionError("ConversationListFragment was not attached");
    }

    private MatrixCursor testConversationCursor(final int expressConversationCount) throws Exception {
        final Class<?> itemData = Class.forName(
                "com.android.messaging.datamodel.data.ConversationListItemData");
        final String[] projection = (String[]) itemData.getField("PROJECTION").get(null);
        final MatrixCursor cursor = new MatrixCursor(projection);
        for (int index = 0; index < 3; index++) {
            final int categoryId = index < expressConversationCount ? 5 : 1;
            final String categoryName = categoryId == 5 ? "快递" : null;
            final Object[] values = new Object[] {
                    String.valueOf(100 + index), "会话" + index, null, "测试短信", 1000L + index,
                    0, null, null, -2L, null, "95338", 1, "", 0, 0, 0, null, null, null, 0,
                    String.valueOf(100 + index), null, null, 0, null, "95338", 0, categoryId,
                    categoryName, 0xffef6c00
            };
            assertEquals("Conversation projection changed; update test row fixture", projection.length,
                    values.length);
            cursor.addRow(values);
        }
        return cursor;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private Object category(final int id, final String name, final String typeName,
            final int sortOrder) throws Exception {
        final Class<?> categoryType = Class.forName("com.android.messaging.category.SmsCategoryType");
        final Object type = Enum.valueOf((Class) categoryType, typeName);
        final Class<?> category = Class.forName("com.android.messaging.category.SmsCategory");
        final Constructor<?> constructor = category.getConstructor(int.class, String.class,
                categoryType, int.class, String.class, boolean.class, int.class, boolean.class);
        return constructor.newInstance(id, name, type, 0xff1565c0, "notifications", true,
                sortOrder, true);
    }

    private int adapterItemCount(final Object adapter) {
        try {
            return (Integer) invoke(adapter, "getItemCount", new Class<?>[0]);
        } catch (final Exception exception) {
            throw new RuntimeException(exception);
        }
    }

    private int adapterCategoryFilter(final Object adapter) {
        try {
            return (Integer) invoke(adapter, "getCategoryFilter", new Class<?>[0]);
        } catch (final Exception exception) {
            throw new RuntimeException(exception);
        }
    }

    private TextView findChip(final LinearLayout container, final String startsWith) {
        for (int index = 0; index < container.getChildCount(); index++) {
            final View child = container.getChildAt(index);
            if (child instanceof TextView && ((TextView) child).getText().toString().startsWith(
                    startsWith)) {
                return (TextView) child;
            }
        }
        throw new AssertionError("Missing chip: " + startsWith);
    }

    private void renderFlightCard(final View card) throws Exception {
        final Class<?> cardTypeClass = Class.forName("com.android.messaging.card.CardType");
        final Object flightCard = Enum.valueOf((Class) cardTypeClass, "FLIGHT_CARD");
        final Class<?> smsCardClass = Class.forName("com.android.messaging.card.SmsCard");
        final Constructor<?> constructor = smsCardClass.getConstructor(long.class, long.class,
                long.class, cardTypeClass, String.class, String.class, float.class, long.class,
                boolean.class);
        final Object data = constructor.newInstance(1L, 1L, 1L, flightCard,
                "{\"flightNumber\":\"CA1234\",\"gate\":\"A128\","
                        + "\"departureTime\":\"12月31日 23:59\","
                        + "\"route\":\"PEK-CAN\"}", "测试航班", .95f,
                System.currentTimeMillis(), true);
        final Field model = card.getClass().getDeclaredField("mCard");
        model.setAccessible(true);
        final Method render = card.getClass().getDeclaredMethod("render");
        render.setAccessible(true);
        getInstrumentation().runOnMainSync(() -> {
            try {
                model.set(card, data);
                render.invoke(card);
            } catch (final Exception exception) {
                throw new RuntimeException(exception);
            }
        });
    }

    private void measureAndLayout(final View view, final int widthPx) {
        getInstrumentation().runOnMainSync(() -> {
            view.measure(View.MeasureSpec.makeMeasureSpec(widthPx, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
            view.layout(0, 0, widthPx, view.getMeasuredHeight());
        });
    }

    private void waitUntil(final Condition condition, final String failureMessage) {
        final long deadline = SystemClock.uptimeMillis() + 4000L;
        while (SystemClock.uptimeMillis() < deadline) {
            getInstrumentation().waitForIdleSync();
            if (condition.value()) return;
            SystemClock.sleep(50L);
        }
        throw new AssertionError(failureMessage);
    }

    private interface Condition { boolean value(); }

    private static Object privateField(final Object target, final String name) throws Exception {
        final Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }

    private static Object invoke(final Object target, final String name, final Class<?>[] types,
            final Object... arguments) throws Exception {
        final Method method = target.getClass().getMethod(name, types);
        return method.invoke(target, arguments);
    }

    private static Object invokeDeclared(final Object target, final String name,
            final Class<?>[] types, final Object... arguments) throws Exception {
        final Method method = target.getClass().getDeclaredMethod(name, types);
        method.setAccessible(true);
        return method.invoke(target, arguments);
    }

    private static int screenY(final View view) {
        final int[] location = new int[2];
        view.getLocationOnScreen(location);
        return location[1];
    }

    private static int dp(final Context context, final int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    private Activity launch(final String className) {
        final Context target = getInstrumentation().getTargetContext();
        final Intent intent = new Intent().setComponent(new ComponentName(TARGET_PACKAGE, className));
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        final Activity activity = getInstrumentation().startActivitySync(intent);
        getInstrumentation().waitForIdleSync();
        assertEquals("Target must not redirect to the permission-check activity", className,
                activity.getClass().getName());
        return activity;
    }

    private View inflateTargetLayout(final Activity activity, final String layoutName) {
        return inflateTargetLayout((Context) activity, layoutName);
    }

    private View inflateTargetLayout(final Context context, final String layoutName) {
        final int id = targetResources().getIdentifier(layoutName, "layout", TARGET_PACKAGE);
        assertTrue("Missing target layout " + layoutName, id != 0);
        final View[] view = new View[1];
        getInstrumentation().runOnMainSync(() -> view[0] = LayoutInflater.from(context).inflate(
                id, null, false));
        assertNotNull(view[0]);
        return view[0];
    }

    private void assertToggleChangesAndRestores(final Activity activity, final String idName) {
        final View view = activity.findViewById(targetId(idName));
        assertNotNull("Missing toggle " + idName, view);
        assertTrue("Expected CompoundButton for " + idName, view instanceof CompoundButton);
        final CompoundButton button = (CompoundButton) view;
        final boolean original = button.isChecked();
        getInstrumentation().runOnMainSync(button::performClick);
        getInstrumentation().waitForIdleSync();
        assertEquals("Toggle did not change state for " + idName, !original, button.isChecked());
        getInstrumentation().runOnMainSync(button::performClick);
        getInstrumentation().waitForIdleSync();
        assertEquals("Toggle did not restore state for " + idName, original, button.isChecked());
    }

    private void assertVisible(final Activity activity, final String idName, final String type) {
        final View view = activity.findViewById(targetId(idName));
        assertNotNull("Missing visible control " + idName, view);
        assertEquals(type, view.getClass().getSimpleName());
        assertEquals(View.VISIBLE, view.getVisibility());
    }

    private void assertView(final View root, final String idName, final String type,
            final int visibility) {
        final View view = root.findViewById(targetId(idName));
        assertNotNull("Missing layout control " + idName, view);
        assertEquals(type, view.getClass().getSimpleName());
        assertEquals(visibility, view.getVisibility());
    }

    private int targetId(final String name) {
        final int id = targetResources().getIdentifier(name, "id", TARGET_PACKAGE);
        assertTrue("Missing target id " + name, id != 0);
        return id;
    }

    private Resources targetResources() {
        return getInstrumentation().getTargetContext().getResources();
    }
}
