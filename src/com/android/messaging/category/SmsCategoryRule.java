/*
 * Copyright (C) 2026
 * SPDX-License-Identifier: Apache-2.0
 */
package com.android.messaging.category;

import androidx.annotation.NonNull;

/** One keyword, Java-regex, or sender-prefix rule belonging to a category. */
public final class SmsCategoryRule {
    public final int id;
    public final int categoryId;
    @NonNull public final CategoryMatchType matchType;
    @NonNull public final String value;
    public final int weight;
    public final boolean enabled;

    public SmsCategoryRule(final int id, final int categoryId,
            @NonNull final CategoryMatchType matchType, @NonNull final String value,
            final int weight, final boolean enabled) {
        this.id = id;
        this.categoryId = categoryId;
        this.matchType = matchType;
        this.value = value;
        this.weight = weight;
        this.enabled = enabled;
    }
}
