/*
 * Copyright (C) 2026
 * SPDX-License-Identifier: Apache-2.0
 */
package com.android.messaging.category;

import androidx.annotation.NonNull;

/** The selected category and accumulated matching rule weight. */
public final class SmsClassificationResult {
    @NonNull public final SmsCategory category;
    public final int score;

    public SmsClassificationResult(@NonNull final SmsCategory category, final int score) {
        this.category = category;
        this.score = score;
    }
}
