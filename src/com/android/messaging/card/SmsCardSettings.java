/* Copyright (C) 2026 SPDX-License-Identifier: Apache-2.0 */
package com.android.messaging.card;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;

import com.android.messaging.util.BuglePrefs;

/** Preference keys and bounded accessors for completely local card parsing. */
public final class SmsCardSettings {
    public static final String ENABLED = "sms_cards_enabled";
    public static final String MIN_CONFIDENCE = "sms_cards_min_confidence";
    public static final String EXTERNAL_ACCESS = "sms_cards_external_access";
    private static final String TYPE_PREFIX = "sms_cards_type_";

    private SmsCardSettings() {}

    public static boolean isEnabled(@NonNull final Context context) {
        return prefs(context).getBoolean(ENABLED, true);
    }

    public static float minimumConfidence(@NonNull final Context context) {
        final float value = prefs(context).getFloat(MIN_CONFIDENCE, .5f);
        return Math.max(0f, Math.min(1f, value));
    }

    public static boolean isTypeEnabled(@NonNull final Context context, @NonNull final CardType type) {
        return prefs(context).getBoolean(TYPE_PREFIX + type.name(), true);
    }

    public static void setTypeEnabled(@NonNull final Context context, @NonNull final CardType type,
            final boolean enabled) {
        prefs(context).edit().putBoolean(TYPE_PREFIX + type.name(), enabled).apply();
    }

    private static SharedPreferences prefs(final Context context) {
        return context.getApplicationContext().getSharedPreferences(
                BuglePrefs.SHARED_PREFERENCES_NAME, Context.MODE_PRIVATE);
    }
}
