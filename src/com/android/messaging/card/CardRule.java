/* Copyright (C) 2026 SPDX-License-Identifier: Apache-2.0 */
package com.android.messaging.card;

import androidx.annotation.NonNull;

import java.util.List;

/** Deserialized schema-compatible card parsing rule. */
final class CardRule {
    final CardType type;
    @NonNull final List<String> keywords;
    @NonNull final List<String> senderPrefixes;
    @NonNull final List<Field> fields;

    CardRule(final CardType type, @NonNull final List<String> keywords,
            @NonNull final List<String> senderPrefixes, @NonNull final List<Field> fields) {
        this.type = type;
        this.keywords = keywords;
        this.senderPrefixes = senderPrefixes;
        this.fields = fields;
    }

    static final class Field {
        @NonNull final String name;
        final boolean required;
        final float weight;
        @NonNull final List<String> patterns;

        Field(@NonNull final String name, final boolean required, final float weight,
                @NonNull final List<String> patterns) {
            this.name = name;
            this.required = required;
            this.weight = weight;
            this.patterns = patterns;
        }
    }
}
