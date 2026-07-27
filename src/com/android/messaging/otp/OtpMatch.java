/*
 * Copyright (C) 2026
 * SPDX-License-Identifier: Apache-2.0
 */
package com.android.messaging.otp;

import androidx.annotation.NonNull;

/** A matched OTP and its range in the source message. */
public final class OtpMatch {
    @NonNull public final String value;
    public final int start;
    public final int end;
    @NonNull public final OtpRule rule;

    OtpMatch(@NonNull final String value, final int start, final int end,
            @NonNull final OtpRule rule) {
        this.value = value;
        this.start = start;
        this.end = end;
        this.rule = rule;
    }
}
