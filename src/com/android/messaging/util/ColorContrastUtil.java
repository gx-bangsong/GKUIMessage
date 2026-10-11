/*
 * Copyright (C) 2026
 * SPDX-License-Identifier: Apache-2.0
 */
package com.android.messaging.util;

import android.content.Context;
import android.graphics.Color;
import android.util.TypedValue;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;

/** Theme-color resolution and WCAG contrast helpers for View-based Material UI. */
public final class ColorContrastUtil {
    private ColorContrastUtil() {}

    /** Resolves a color theme attribute, returning {@code fallback} when a legacy theme lacks it. */
    @ColorInt
    public static int resolveThemeColor(@NonNull final Context context, final int attribute,
            @ColorInt final int fallback) {
        final TypedValue value = new TypedValue();
        if (!context.getTheme().resolveAttribute(attribute, value, true)) {
            return fallback;
        }
        if (value.resourceId != 0) {
            try {
                return context.getColor(value.resourceId);
            } catch (final RuntimeException exception) {
                return fallback;
            }
        }
        return value.type >= TypedValue.TYPE_FIRST_COLOR_INT
                && value.type <= TypedValue.TYPE_LAST_COLOR_INT ? value.data : fallback;
    }

    /** Returns black or white, whichever has the larger WCAG contrast against {@code background}. */
    @ColorInt
    public static int readableContentColor(@ColorInt final int background) {
        final double whiteContrast = contrastRatio(Color.WHITE, background);
        final double blackContrast = contrastRatio(Color.BLACK, background);
        return whiteContrast >= blackContrast ? Color.WHITE : Color.BLACK;
    }

    /** Computes the WCAG contrast ratio for two sRGB colors, including alpha compositing. */
    public static double contrastRatio(@ColorInt final int foreground, @ColorInt final int background) {
        final int opaqueBackground = composite(background, Color.WHITE);
        final int opaqueForeground = composite(foreground, opaqueBackground);
        final double first = relativeLuminance(opaqueForeground);
        final double second = relativeLuminance(opaqueBackground);
        return (Math.max(first, second) + .05d) / (Math.min(first, second) + .05d);
    }

    /** WCAG AA threshold for ordinary body text. */
    public static boolean hasBodyTextContrast(@ColorInt final int foreground,
            @ColorInt final int background) {
        return contrastRatio(foreground, background) >= 4.5d;
    }

    /** WCAG AA threshold for large text (18sp or 14sp bold). */
    public static boolean hasLargeTextContrast(@ColorInt final int foreground,
            @ColorInt final int background) {
        return contrastRatio(foreground, background) >= 3.0d;
    }

    @ColorInt
    private static int composite(@ColorInt final int foreground, @ColorInt final int background) {
        final float alpha = Color.alpha(foreground) / 255f;
        final int red = Math.round(Color.red(foreground) * alpha + Color.red(background) * (1f - alpha));
        final int green = Math.round(Color.green(foreground) * alpha
                + Color.green(background) * (1f - alpha));
        final int blue = Math.round(Color.blue(foreground) * alpha + Color.blue(background) * (1f - alpha));
        return Color.rgb(red, green, blue);
    }

    private static double relativeLuminance(@ColorInt final int color) {
        return .2126d * linear(Color.red(color) / 255d)
                + .7152d * linear(Color.green(color) / 255d)
                + .0722d * linear(Color.blue(color) / 255d);
    }

    private static double linear(final double component) {
        return component <= .04045d ? component / 12.92d
                : Math.pow((component + .055d) / 1.055d, 2.4d);
    }
}
