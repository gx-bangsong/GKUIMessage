/* Copyright (C) 2026 SPDX-License-Identifier: Apache-2.0 */
package com.android.messaging.otp;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Short-lived in-process handoff from module 1 to the card renderer. It stores only an OTP value
 * keyed by the exact delivered body for two minutes and never runs a second OTP expression.
 */
public final class OtpDetectionCache {
    private static final long TTL_MILLIS = 120_000L;
    private static final ConcurrentHashMap<String, Entry> ENTRIES = new ConcurrentHashMap<>();

    private OtpDetectionCache() {}

    public static void record(@NonNull final String body, @NonNull final String code) {
        ENTRIES.put(body, new Entry(code, System.currentTimeMillis()));
    }

    @Nullable
    public static String get(@NonNull final String body) {
        final Entry entry = ENTRIES.get(body);
        if (entry == null) return null;
        if (System.currentTimeMillis() - entry.createdAt > TTL_MILLIS) {
            ENTRIES.remove(body, entry);
            return null;
        }
        return entry.code;
    }

    private static final class Entry {
        final String code;
        final long createdAt;
        Entry(final String code, final long createdAt) { this.code = code; this.createdAt = createdAt; }
    }
}
