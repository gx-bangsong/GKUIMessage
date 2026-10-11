/*
 * Copyright (C) 2026
 * SPDX-License-Identifier: Apache-2.0
 */
package com.android.messaging.tests;

import android.app.Activity;
import android.app.UiAutomation;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.ParcelFileDescriptor;
import android.os.SystemClock;
import android.test.InstrumentationTestCase;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Device-side theme contrast checks for the actual Material/Dynamic Color rendered views.
 * The test uses the currently selected wallpaper palette and exercises both system night modes.
 */
public final class SmartSmsThemeContrastInstrumentationTest extends InstrumentationTestCase {
    private static final String TARGET_PACKAGE = "com.android.messaging";
    private int mOriginalNightMode;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mOriginalNightMode = getInstrumentation().getTargetContext().getResources()
                .getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
    }

    @Override
    protected void tearDown() throws Exception {
        try {
            setNightMode(mOriginalNightMode == Configuration.UI_MODE_NIGHT_YES);
        } finally {
            super.tearDown();
        }
    }

    public void testReadableThemePairsInLightMode() throws Exception {
        setNightMode(false);
        assertSettingsThemeContrast();
    }

    public void testReadableThemePairsInDarkMode() throws Exception {
        setNightMode(true);
        assertSettingsThemeContrast();
    }

    public void testCategoryForegroundAlgorithmMeetsBodyTextContrast() throws Exception {
        final Class<?> util = Class.forName("com.android.messaging.util.ColorContrastUtil");
        final Method foreground = util.getMethod("readableContentColor", int.class);
        final Method contrast = util.getMethod("contrastRatio", int.class, int.class);
        final int[] categoryColors = {0xff1565c0, 0xff6a1b9a, 0xff2e7d32, 0xffef6c00,
                0xffc62828, 0xff00695c};
        for (final int background : categoryColors) {
            final int content = (Integer) foreground.invoke(null, background);
            final double ratio = (Double) contrast.invoke(null, content, background);
            assertTrue("Category Chip foreground fails WCAG AA: " + ratio, ratio >= 4.5d);
        }
    }

    public void testRenderedCardUsesReadableThemeAndStatusColors() throws Exception {
        final Activity activity = launch("com.android.messaging.ui.otp.OtpSettingsActivity");
        try {
            final View message = inflate(activity, "conversation_message_view");
            final View card = message.findViewById(targetId("sms_card_view"));
            renderExpressCard(card);

            final TextView title = (TextView) privateField(card, "mTitle");
            final TextView status = (TextView) privateField(card, "mStatus");
            final int cardBackground = ((ColorStateList) invoke(card, "getCardBackgroundColor"))
                    .getDefaultColor();
            final int statusBackground = ((ColorStateList) invoke(status,
                    "getChipBackgroundColor")).getDefaultColor();
            assertBodyContrast("Card title", title.getCurrentTextColor(), cardBackground);
            assertBodyContrast("Card status", status.getCurrentTextColor(), statusBackground);
        } finally {
            activity.finish();
        }
    }

    private void assertSettingsThemeContrast() throws Exception {
        final String[] activities = {
                "com.android.messaging.ui.otp.OtpSettingsActivity",
                "com.android.messaging.ui.category.CategorySettingsActivity",
                "com.android.messaging.ui.cardsettings.SmsCardSettingsActivity"
        };
        for (final String name : activities) {
            final Activity activity = launch(name);
            try {
                final int surface = themeColor(activity, "colorSurface", 0xfffafafa);
                final int onSurface = themeColor(activity, "colorOnSurface", 0xff1b1b1f);
                assertBodyContrast(name + " onSurface", onSurface, surface);
                final int checked = assertVisibleTextContrast(activity.getWindow().getDecorView());
                assertTrue("No visible text with a solid inherited background was checked in " + name,
                        checked > 0);
            } finally {
                activity.finish();
            }
        }
    }

    private int assertVisibleTextContrast(final View view) {
        int checked = 0;
        if (view instanceof TextView && view.getVisibility() == View.VISIBLE
                && ((TextView) view).getText().length() > 0) {
            final Integer background = solidBackground(view);
            if (background != null) {
                final TextView textView = (TextView) view;
                final float sizeSp = textView.getTextSize() / getTargetDensity();
                final boolean large = sizeSp >= 18f
                        || (sizeSp >= 14f && textView.getTypeface() != null
                        && textView.getTypeface().isBold());
                final double minimum = large ? 3d : 4.5d;
                final double ratio = contrast(textView.getCurrentTextColor(), background);
                assertTrue("Unreadable text '" + textView.getText() + "' ratio=" + ratio,
                        ratio >= minimum);
                checked++;
            }
        }
        if (view instanceof ViewGroup) {
            final ViewGroup group = (ViewGroup) view;
            for (int index = 0; index < group.getChildCount(); index++) {
                checked += assertVisibleTextContrast(group.getChildAt(index));
            }
        }
        return checked;
    }

    private Integer solidBackground(final View view) {
        View current = view;
        while (current != null) {
            final Drawable background = current.getBackground();
            if (background instanceof ColorDrawable) {
                final int color = ((ColorDrawable) background).getColor();
                if (Color.alpha(color) != 0) return color;
            } else if (background != null) {
                // A RippleDrawable, MaterialShapeDrawable, bitmap, or gradient needs component-
                // specific inspection. Do not incorrectly compare text against an ancestor.
                return null;
            }
            final Object parent = current.getParent();
            current = parent instanceof View ? (View) parent : null;
        }
        return null;
    }

    private int themeColor(final Context context, final String attributeName, final int fallback) {
        final int attribute = materialAttribute(attributeName);
        if (attribute == 0) return fallback;
        final TypedValue value = new TypedValue();
        if (!context.getTheme().resolveAttribute(attribute, value, true)) return fallback;
        if (value.resourceId != 0) return context.getColor(value.resourceId);
        return value.type >= TypedValue.TYPE_FIRST_COLOR_INT
                && value.type <= TypedValue.TYPE_LAST_COLOR_INT ? value.data : fallback;
    }

    private int materialAttribute(final String name) {
        try {
            return Class.forName("com.google.android.material.R$attr").getField(name).getInt(null);
        } catch (final ReflectiveOperationException exception) {
            return targetResources().getIdentifier(name, "attr", TARGET_PACKAGE);
        }
    }

    private void assertBodyContrast(final String name, final int foreground, final int background) {
        final double ratio = contrast(foreground, background);
        assertTrue(name + " contrast ratio=" + ratio, ratio >= 4.5d);
    }

    private double contrast(final int foreground, final int background) {
        final int opaqueBackground = composite(background, Color.WHITE);
        final int opaqueForeground = composite(foreground, opaqueBackground);
        final double first = luminance(opaqueForeground);
        final double second = luminance(opaqueBackground);
        return (Math.max(first, second) + .05d) / (Math.min(first, second) + .05d);
    }

    private int composite(final int foreground, final int background) {
        final float alpha = Color.alpha(foreground) / 255f;
        return Color.rgb(Math.round(Color.red(foreground) * alpha + Color.red(background) * (1f - alpha)),
                Math.round(Color.green(foreground) * alpha + Color.green(background) * (1f - alpha)),
                Math.round(Color.blue(foreground) * alpha + Color.blue(background) * (1f - alpha)));
    }

    private double luminance(final int color) {
        return .2126d * linear(Color.red(color) / 255d)
                + .7152d * linear(Color.green(color) / 255d)
                + .0722d * linear(Color.blue(color) / 255d);
    }

    private double linear(final double value) {
        return value <= .04045d ? value / 12.92d : Math.pow((value + .055d) / 1.055d, 2.4d);
    }

    private void setNightMode(final boolean night) throws Exception {
        final UiAutomation automation = getInstrumentation().getUiAutomation();
        final ParcelFileDescriptor descriptor = automation.executeShellCommand(
                "cmd uimode night " + (night ? "yes" : "no"));
        if (descriptor != null) descriptor.close();
        SystemClock.sleep(500L);
    }

    private Activity launch(final String className) {
        final Intent intent = new Intent().setComponent(new ComponentName(TARGET_PACKAGE, className));
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        final Activity activity = getInstrumentation().startActivitySync(intent);
        getInstrumentation().waitForIdleSync();
        assertEquals(className, activity.getClass().getName());
        return activity;
    }

    private View inflate(final Context context, final String layoutName) {
        final int layout = targetResources().getIdentifier(layoutName, "layout", TARGET_PACKAGE);
        assertTrue(layout != 0);
        final View[] result = new View[1];
        getInstrumentation().runOnMainSync(() -> result[0] = LayoutInflater.from(context).inflate(
                layout, null, false));
        return result[0];
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void renderExpressCard(final View card) throws Exception {
        final Class<?> typeClass = Class.forName("com.android.messaging.card.CardType");
        final Object type = Enum.valueOf((Class) typeClass, "EXPRESS_CARD");
        final Class<?> cardClass = Class.forName("com.android.messaging.card.SmsCard");
        final Constructor<?> constructor = cardClass.getConstructor(long.class, long.class,
                long.class, typeClass, String.class, String.class, float.class, long.class,
                boolean.class);
        final Object model = constructor.newInstance(1L, 1L, 1L, type,
                "{\"trackingNumber\":\"SF123456789012\",\"company\":\"顺丰速运\","
                        + "\"status\":\"OUT_FOR_DELIVERY\"}", "测试", .95f,
                System.currentTimeMillis(), true);
        final Field modelField = card.getClass().getDeclaredField("mCard");
        modelField.setAccessible(true);
        final Method render = card.getClass().getDeclaredMethod("render");
        render.setAccessible(true);
        getInstrumentation().runOnMainSync(() -> {
            try {
                modelField.set(card, model);
                render.invoke(card);
            } catch (final Exception exception) {
                throw new RuntimeException(exception);
            }
        });
    }

    private Object privateField(final Object target, final String fieldName) throws Exception {
        final Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.get(target);
    }

    private Object invoke(final Object target, final String methodName) throws Exception {
        return target.getClass().getMethod(methodName).invoke(target);
    }

    private int targetId(final String name) {
        final int id = targetResources().getIdentifier(name, "id", TARGET_PACKAGE);
        assertTrue(id != 0);
        return id;
    }

    private float getTargetDensity() {
        return getInstrumentation().getTargetContext().getResources().getDisplayMetrics().scaledDensity;
    }

    private Resources targetResources() {
        return getInstrumentation().getTargetContext().getResources();
    }
}
