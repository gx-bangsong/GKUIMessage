/* Copyright (C) 2026 SPDX-License-Identifier: Apache-2.0 */
package com.android.messaging.card;

import android.content.ContentValues;
import android.database.Cursor;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.messaging.datamodel.DataModel;
import com.android.messaging.datamodel.DatabaseHelper;
import com.android.messaging.datamodel.DatabaseHelper.SmsCardColumns;
import com.android.messaging.datamodel.DatabaseWrapper;

import java.util.ArrayList;
import java.util.List;

/** Local-only database DAO for parsed cards. Every method must run off the UI thread. */
public final class SmsCardRepository {
    private SmsCardRepository() {}

    public static void upsert(@NonNull final DatabaseWrapper database, final long messageId,
            final long threadId, @NonNull final SmsCardParser.ParsedCard parsed,
            @NonNull final String rawText) {
        final ContentValues values = new ContentValues();
        values.put(SmsCardColumns.MESSAGE_ID, messageId);
        values.put(SmsCardColumns.THREAD_ID, threadId);
        values.put(SmsCardColumns.CARD_TYPE, parsed.type.name());
        values.put(SmsCardColumns.CARD_DATA, parsed.data);
        values.put(SmsCardColumns.RAW_TEXT, rawText);
        values.put(SmsCardColumns.CONFIDENCE, parsed.confidence);
        values.put(SmsCardColumns.PARSED_AT, System.currentTimeMillis());
        values.put(SmsCardColumns.IS_EXPANDED, parsed.confidence >= .8f ? 1 : 0);
        database.getDatabase().insertWithOnConflict(DatabaseHelper.SMS_CARDS_TABLE, null, values,
                android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE);
    }

    @Nullable
    public static SmsCard getBestForMessage(final long messageId) {
        final DatabaseWrapper database = DataModel.get().getDatabase();
        final Cursor cursor = database.query(DatabaseHelper.SMS_CARDS_TABLE, null,
                SmsCardColumns.MESSAGE_ID + "=?", new String[] {String.valueOf(messageId)},
                null, null, SmsCardColumns.CONFIDENCE + " DESC, " + SmsCardColumns._ID + " DESC", "1");
        try { return cursor.moveToFirst() ? fromCursor(cursor) : null; }
        finally { cursor.close(); }
    }

    @NonNull
    public static List<SmsCard> getHistory() {
        final DatabaseWrapper database = DataModel.get().getDatabase();
        final ArrayList<SmsCard> cards = new ArrayList<>();
        final Cursor cursor = database.query(DatabaseHelper.SMS_CARDS_TABLE, null, null, null,
                null, null, SmsCardColumns.PARSED_AT + " DESC", "200");
        try { while (cursor.moveToNext()) cards.add(fromCursor(cursor)); }
        finally { cursor.close(); }
        return cards;
    }

    public static void setExpanded(final long cardId, final boolean expanded) {
        final ContentValues values = new ContentValues();
        values.put(SmsCardColumns.IS_EXPANDED, expanded ? 1 : 0);
        DataModel.get().getDatabase().update(DatabaseHelper.SMS_CARDS_TABLE, values,
                SmsCardColumns._ID + "=?", new String[] {String.valueOf(cardId)});
    }

    public static void clearAll() {
        DataModel.get().getDatabase().delete(DatabaseHelper.SMS_CARDS_TABLE, null, null);
    }

    private static SmsCard fromCursor(final Cursor cursor) {
        return new SmsCard(cursor.getLong(cursor.getColumnIndexOrThrow(SmsCardColumns._ID)),
                cursor.getLong(cursor.getColumnIndexOrThrow(SmsCardColumns.MESSAGE_ID)),
                cursor.getLong(cursor.getColumnIndexOrThrow(SmsCardColumns.THREAD_ID)),
                CardType.valueOf(cursor.getString(cursor.getColumnIndexOrThrow(SmsCardColumns.CARD_TYPE))),
                cursor.getString(cursor.getColumnIndexOrThrow(SmsCardColumns.CARD_DATA)),
                cursor.getString(cursor.getColumnIndexOrThrow(SmsCardColumns.RAW_TEXT)),
                cursor.getFloat(cursor.getColumnIndexOrThrow(SmsCardColumns.CONFIDENCE)),
                cursor.getLong(cursor.getColumnIndexOrThrow(SmsCardColumns.PARSED_AT)),
                cursor.getInt(cursor.getColumnIndexOrThrow(SmsCardColumns.IS_EXPANDED)) != 0);
    }
}
