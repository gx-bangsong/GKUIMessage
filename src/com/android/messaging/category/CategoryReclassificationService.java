/*
 * Copyright (C) 2026
 * SPDX-License-Identifier: Apache-2.0
 */
package com.android.messaging.category;

import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;

import androidx.annotation.NonNull;
import androidx.core.app.JobIntentService;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import com.android.messaging.Factory;
import com.android.messaging.R;
import com.android.messaging.datamodel.DatabaseHelper;
import com.android.messaging.datamodel.DatabaseHelper.ConversationColumns;
import com.android.messaging.datamodel.DatabaseHelper.MessageColumns;
import com.android.messaging.datamodel.DatabaseHelper.ParticipantColumns;
import com.android.messaging.datamodel.DatabaseWrapper;
import com.android.messaging.datamodel.MessagingContentProvider;
import com.android.messaging.util.NotificationsUtil;

/** Reclassifies the latest SMS of every local conversation after a rule configuration change. */
public final class CategoryReclassificationService extends JobIntentService {
    private static final int JOB_ID = 1002;
    private static final int NOTIFICATION_ID = 2102;

    public static void enqueue(@NonNull final Context context) {
        enqueueWork(context, CategoryReclassificationService.class, JOB_ID,
                new Intent(context, CategoryReclassificationService.class));
    }

    @Override
    protected void onHandleWork(@NonNull final Intent intent) {
        final Context context = getApplicationContext();
        final DatabaseWrapper database = Factory.get().getDataModel().getDatabase();
        final String sql = "SELECT c." + ConversationColumns._ID + ", c."
                + ConversationColumns.SNIPPET_TEXT + ", p." + ParticipantColumns.NORMALIZED_DESTINATION
                + " FROM " + DatabaseHelper.CONVERSATIONS_TABLE + " c LEFT JOIN "
                + DatabaseHelper.MESSAGES_TABLE + " m ON c." + ConversationColumns.LATEST_MESSAGE_ID
                + "=m." + MessageColumns._ID + " LEFT JOIN " + DatabaseHelper.PARTICIPANTS_TABLE
                + " p ON m." + MessageColumns.SENDER_PARTICIPANT_ID + "=p."
                + ParticipantColumns._ID + " WHERE c." + ConversationColumns.SORT_TIMESTAMP + ">0";
        try (Cursor cursor = database.rawQuery(sql, null)) {
            final int total = cursor.getCount();
            int current = 0;
            showProgress(context, current, total);
            while (cursor.moveToNext()) {
                final String conversationId = cursor.getString(0);
                final String body = cursor.getString(1);
                final String sender = cursor.getString(2);
                SmsClassifier.classifyAndCache(context, database, conversationId, body, sender);
                current++;
                if (current == total || current % 10 == 0) {
                    showProgress(context, current, total);
                }
            }
        } finally {
            NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID);
        }
        MessagingContentProvider.notifyConversationListChanged();
    }

    private static void showProgress(@NonNull final Context context, final int current,
            final int total) {
        final NotificationCompat.Builder builder = new NotificationCompat.Builder(context,
                NotificationsUtil.DEFAULT_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_sms_light)
                .setContentTitle(context.getString(R.string.category_reclassification_title))
                .setContentText(context.getString(R.string.category_reclassification_progress,
                        current, total))
                .setOnlyAlertOnce(true)
                .setOngoing(true)
                .setProgress(total, current, total == 0);
        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, builder.build());
    }
}
