/* Copyright (C) 2026 SPDX-License-Identifier: Apache-2.0 */
package com.android.messaging.card;

import androidx.annotation.NonNull;

/** Persisted local representation of a parsed service SMS. */
public final class SmsCard {
    public final long id;
    public final long messageId;
    public final long threadId;
    @NonNull public final CardType cardType;
    @NonNull public final String cardData;
    @NonNull public final String rawText;
    public final float confidence;
    public final long parsedAt;
    public final boolean isExpanded;

    public SmsCard(final long id, final long messageId, final long threadId,
            @NonNull final CardType cardType, @NonNull final String cardData,
            @NonNull final String rawText, final float confidence, final long parsedAt,
            final boolean isExpanded) {
        this.id = id;
        this.messageId = messageId;
        this.threadId = threadId;
        this.cardType = cardType;
        this.cardData = cardData;
        this.rawText = rawText;
        this.confidence = confidence;
        this.parsedAt = parsedAt;
        this.isExpanded = isExpanded;
    }
}
