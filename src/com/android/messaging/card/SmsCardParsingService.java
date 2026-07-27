/* Copyright (C) 2026 SPDX-License-Identifier: Apache-2.0 */
package com.android.messaging.card;

import android.content.Context;
import android.content.Intent;

import androidx.annotation.NonNull;
import androidx.core.app.JobIntentService;

import com.android.messaging.Factory;
import com.android.messaging.otp.OtpDetectionCache;
import com.android.messaging.datamodel.DatabaseWrapper;
import com.android.messaging.datamodel.MessagingContentProvider;

import java.util.List;

/** Queue-backed, process-safe local parser for one newly delivered SMS. */
public final class SmsCardParsingService extends JobIntentService {
    private static final int JOB_ID = 1003;
    private static final String EXTRA_MESSAGE_ID = "message_id";
    private static final String EXTRA_THREAD_ID = "thread_id";
    private static final String EXTRA_CONVERSATION_ID = "conversation_id";
    private static final String EXTRA_BODY = "body";
    private static final String EXTRA_SENDER = "sender";

    public static void enqueue(@NonNull final Context context, final long messageId, final long threadId,
            @NonNull final String conversationId, @NonNull final String body,
            @NonNull final String sender) {
        if (!SmsCardSettings.isEnabled(context)) return;
        final Intent work = new Intent(context, SmsCardParsingService.class);
        work.putExtra(EXTRA_MESSAGE_ID, messageId);
        work.putExtra(EXTRA_THREAD_ID, threadId);
        work.putExtra(EXTRA_CONVERSATION_ID, conversationId);
        work.putExtra(EXTRA_BODY, body);
        work.putExtra(EXTRA_SENDER, sender);
        enqueueWork(context, SmsCardParsingService.class, JOB_ID, work);
    }

    @Override
    protected void onHandleWork(@NonNull final Intent intent) {
        final Context context = getApplicationContext();
        if (!SmsCardSettings.isEnabled(context)) return;
        final String body = intent.getStringExtra(EXTRA_BODY);
        if (body == null || body.isEmpty()) return;
        // Module 1's maximum matcher window is 500 ms. Give its evidence handoff a short chance
        // before parsing assets, without ever invoking OtpDetector from this module.
        if (OtpDetectionCache.get(body) == null && (body.contains("验证码") || body.contains("校验码")
                || body.toUpperCase(java.util.Locale.ROOT).contains("OTP"))) {
            try { Thread.sleep(550L); } catch (final InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
        }
        final long messageId = intent.getLongExtra(EXTRA_MESSAGE_ID, -1L);
        if (messageId < 0) return;
        final long threadId = intent.getLongExtra(EXTRA_THREAD_ID, -1L);
        final String sender = intent.getStringExtra(EXTRA_SENDER);
        final DatabaseWrapper database = Factory.get().getDataModel().getDatabase();
        final List<SmsCardParser.ParsedCard> cards = SmsCardParser.parse(context, body, sender);
        for (final SmsCardParser.ParsedCard card : cards) {
            SmsCardRepository.upsert(database, messageId, threadId, card, body);
        }
        if (!cards.isEmpty()) {
            final String conversationId = intent.getStringExtra(EXTRA_CONVERSATION_ID);
            if (conversationId != null) MessagingContentProvider.notifyMessagesChanged(conversationId);
            else MessagingContentProvider.notifyAllMessagesChanged();
        }
    }
}
