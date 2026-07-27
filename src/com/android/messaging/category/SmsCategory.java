/*
 * Copyright (C) 2026
 * SPDX-License-Identifier: Apache-2.0
 */
package com.android.messaging.category;

import androidx.annotation.NonNull;

/** One local SMS conversation category. */
public final class SmsCategory {
    public final int id;
    @NonNull public final String name;
    @NonNull public final SmsCategoryType type;
    public final int color;
    @NonNull public final String icon;
    public final boolean enabled;
    public final int sortOrder;
    public final boolean isBuiltIn;

    public SmsCategory(final int id, @NonNull final String name, @NonNull final SmsCategoryType type,
            final int color, @NonNull final String icon, final boolean enabled,
            final int sortOrder, final boolean isBuiltIn) {
        this.id = id;
        this.name = name;
        this.type = type;
        this.color = color;
        this.icon = icon;
        this.enabled = enabled;
        this.sortOrder = sortOrder;
        this.isBuiltIn = isBuiltIn;
    }

    public SmsCategory withEnabled(final boolean value) {
        return new SmsCategory(id, name, type, color, icon, value, sortOrder, isBuiltIn);
    }

    public SmsCategory withSortOrder(final int value) {
        return new SmsCategory(id, name, type, color, icon, enabled, value, isBuiltIn);
    }
}
