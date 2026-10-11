/*
 * Copyright (C) 2026
 * SPDX-License-Identifier: Apache-2.0
 */
package com.android.messaging.receiver;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.messaging.otp.OtpAutoCopyManager;

/** Handles the private "copy verification code" action attached to a message notification. */
public final class OtpCopyReceiver extends BroadcastReceiver {
    private static final String ACTION_COPY_OTP = "com.android.messaging.action.COPY_OTP";
    private static final String EXTRA_OTP = "com.android.messaging.extra.OTP";

    /** Creates an immutable, message-specific PendingIntent for a notification action. */
    @Keep
    @NonNull
    public static PendingIntent getPendingIntent(@NonNull final Context context,
            @NonNull final String messageId, @NonNull final String otp) {
        final Intent intent = new Intent(context, OtpCopyReceiver.class)
                .setAction(ACTION_COPY_OTP)
                .setData(new Uri.Builder().scheme("messaging").authority("otp-copy")
                        .appendPath(messageId).build())
                .putExtra(EXTRA_OTP, otp);
        return PendingIntent.getBroadcast(context, messageId.hashCode(), intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    @Override
    public void onReceive(@NonNull final Context context, @Nullable final Intent intent) {
        if (intent == null || !ACTION_COPY_OTP.equals(intent.getAction())) {
            return;
        }
        OtpAutoCopyManager.copyFromNotification(context, intent.getStringExtra(EXTRA_OTP));
    }
}
