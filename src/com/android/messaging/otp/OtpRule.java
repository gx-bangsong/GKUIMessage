/*
 * Copyright (C) 2026
 * SPDX-License-Identifier: Apache-2.0
 */
package com.android.messaging.otp;

import androidx.annotation.NonNull;

/** Immutable representation of one locally stored OTP matching rule. */
public final class OtpRule {
    public final int id;
    @NonNull public final String name;
    @NonNull public final String pattern;
    public final int priority;
    public final boolean enabled;
    public final boolean isBuiltIn;

    public OtpRule(final int id, @NonNull final String name, @NonNull final String pattern,
            final int priority, final boolean enabled, final boolean isBuiltIn) {
        this.id = id;
        this.name = name;
        this.pattern = pattern;
        this.priority = priority;
        this.enabled = enabled;
        this.isBuiltIn = isBuiltIn;
    }

    public OtpRule withEnabled(final boolean value) {
        return new OtpRule(id, name, pattern, priority, value, isBuiltIn);
    }

    public OtpRule withPriority(final int value) {
        return new OtpRule(id, name, pattern, value, enabled, isBuiltIn);
    }
}
