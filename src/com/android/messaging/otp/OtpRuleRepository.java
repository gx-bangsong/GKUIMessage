/*
 * Copyright (C) 2026
 * SPDX-License-Identifier: Apache-2.0
 */
package com.android.messaging.otp;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.messaging.datamodel.DataModel;
import com.android.messaging.datamodel.DatabaseHelper;
import com.android.messaging.datamodel.DatabaseWrapper;

import java.util.ArrayList;
import java.util.List;

/**
 * Data access object for {@code otp_rules}. All methods except {@link #seedBuiltInRules} must be
 * invoked off the main thread because they use Messaging's database wrapper.
 */
public final class OtpRuleRepository {
    private static final OtpRule[] BUILT_IN_RULES = new OtpRule[] {
            new OtpRule(1, "通用验证码",
                    "(\\d{4,8})(?=.*(?:验证码|校验码|动态码|OTP|code))",
                    10, true, true),
            new OtpRule(2, "纯数字短码", "(?<![.\\d])\\b(\\d{4,6})\\b(?![.\\d])",
                    20, true, true),
            new OtpRule(3, "字母数字混合", "([A-Z0-9]{6,8})(?=.*(?:验证码|code))",
                    30, true, true),
    };

    private OtpRuleRepository() {}

    /** Seeds missing built-ins without changing a user's enabled state or edited expression. */
    public static void seedBuiltInRules(@NonNull final SQLiteDatabase database) {
        for (final OtpRule rule : BUILT_IN_RULES) {
            final ContentValues values = valuesFor(rule, true /* includeId */);
            database.insertWithOnConflict(DatabaseHelper.OTP_RULES_TABLE, null, values,
                    SQLiteDatabase.CONFLICT_IGNORE);
        }
    }

    @NonNull
    public static List<OtpRule> getAllRules() {
        final DatabaseWrapper database = database();
        ensureBuiltIns(database);
        final ArrayList<OtpRule> rules = new ArrayList<>();
        final Cursor cursor = database.query(DatabaseHelper.OTP_RULES_TABLE, null, null, null,
                null, null, DatabaseHelper.OtpRuleColumns.PRIORITY + " ASC, "
                        + DatabaseHelper.OtpRuleColumns._ID + " ASC");
        try {
            while (cursor.moveToNext()) {
                rules.add(fromCursor(cursor));
            }
        } finally {
            cursor.close();
        }
        return rules;
    }

    @NonNull
    public static List<OtpRule> getEnabledRules() {
        final DatabaseWrapper database = database();
        ensureBuiltIns(database);
        final ArrayList<OtpRule> rules = new ArrayList<>();
        final Cursor cursor = database.query(DatabaseHelper.OTP_RULES_TABLE, null,
                DatabaseHelper.OtpRuleColumns.ENABLED + "=1", null, null, null,
                DatabaseHelper.OtpRuleColumns.PRIORITY + " ASC, "
                        + DatabaseHelper.OtpRuleColumns._ID + " ASC");
        try {
            while (cursor.moveToNext()) {
                rules.add(fromCursor(cursor));
            }
        } finally {
            cursor.close();
        }
        return rules;
    }

    @Nullable
    public static OtpRule getRule(final int id) {
        final DatabaseWrapper database = database();
        ensureBuiltIns(database);
        final Cursor cursor = database.query(DatabaseHelper.OTP_RULES_TABLE, null,
                DatabaseHelper.OtpRuleColumns._ID + "=?", new String[] {String.valueOf(id)},
                null, null, null);
        try {
            return cursor.moveToFirst() ? fromCursor(cursor) : null;
        } finally {
            cursor.close();
        }
    }

    public static long insert(@NonNull final String name, @NonNull final String pattern,
            final boolean enabled) {
        final DatabaseWrapper database = database();
        ensureBuiltIns(database);
        final int priority = (int) database.queryNumEntries(DatabaseHelper.OTP_RULES_TABLE,
                null, null) + 10;
        final ContentValues values = valuesFor(new OtpRule(0, name, pattern, priority, enabled,
                false), false /* includeId */);
        return database.insert(DatabaseHelper.OTP_RULES_TABLE, null, values);
    }

    public static void update(@NonNull final OtpRule rule) {
        final ContentValues values = valuesFor(rule, false /* includeId */);
        database().update(DatabaseHelper.OTP_RULES_TABLE, values,
                DatabaseHelper.OtpRuleColumns._ID + "=?",
                new String[] {String.valueOf(rule.id)});
    }

    public static void setEnabled(final int id, final boolean enabled) {
        final ContentValues values = new ContentValues();
        values.put(DatabaseHelper.OtpRuleColumns.ENABLED, enabled ? 1 : 0);
        database().update(DatabaseHelper.OTP_RULES_TABLE, values,
                DatabaseHelper.OtpRuleColumns._ID + "=?", new String[] {String.valueOf(id)});
    }

    /** Rewrites each position so a smaller value always has a higher matching priority. */
    public static void updatePriorities(@NonNull final List<OtpRule> rules) {
        final DatabaseWrapper database = database();
        database.beginTransaction();
        try {
            for (int index = 0; index < rules.size(); index++) {
                final ContentValues values = new ContentValues();
                values.put(DatabaseHelper.OtpRuleColumns.PRIORITY, (index + 1) * 10);
                database.update(DatabaseHelper.OTP_RULES_TABLE, values,
                        DatabaseHelper.OtpRuleColumns._ID + "=?",
                        new String[] {String.valueOf(rules.get(index).id)});
            }
            database.setTransactionSuccessful();
        } finally {
            database.endTransaction();
        }
    }

    /** Built-in rules are intentionally not deletable. Returns whether a row was deleted. */
    public static boolean deleteCustomRule(final int id) {
        return database().delete(DatabaseHelper.OTP_RULES_TABLE,
                DatabaseHelper.OtpRuleColumns._ID + "=? AND "
                        + DatabaseHelper.OtpRuleColumns.IS_BUILT_IN + "=0",
                new String[] {String.valueOf(id)}) > 0;
    }

    private static DatabaseWrapper database() {
        return DataModel.get().getDatabase();
    }

    private static void ensureBuiltIns(final DatabaseWrapper database) {
        seedBuiltInRules(database.getDatabase());
    }

    private static ContentValues valuesFor(final OtpRule rule, final boolean includeId) {
        final ContentValues values = new ContentValues();
        if (includeId) {
            values.put(DatabaseHelper.OtpRuleColumns._ID, rule.id);
        }
        values.put(DatabaseHelper.OtpRuleColumns.NAME, rule.name);
        values.put(DatabaseHelper.OtpRuleColumns.PATTERN, rule.pattern);
        values.put(DatabaseHelper.OtpRuleColumns.PRIORITY, rule.priority);
        values.put(DatabaseHelper.OtpRuleColumns.ENABLED, rule.enabled ? 1 : 0);
        values.put(DatabaseHelper.OtpRuleColumns.IS_BUILT_IN, rule.isBuiltIn ? 1 : 0);
        return values;
    }

    private static OtpRule fromCursor(final Cursor cursor) {
        return new OtpRule(cursor.getInt(cursor.getColumnIndexOrThrow(
                        DatabaseHelper.OtpRuleColumns._ID)),
                cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.OtpRuleColumns.NAME)),
                cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.OtpRuleColumns.PATTERN)),
                cursor.getInt(cursor.getColumnIndexOrThrow(DatabaseHelper.OtpRuleColumns.PRIORITY)),
                cursor.getInt(cursor.getColumnIndexOrThrow(DatabaseHelper.OtpRuleColumns.ENABLED)) != 0,
                cursor.getInt(cursor.getColumnIndexOrThrow(
                        DatabaseHelper.OtpRuleColumns.IS_BUILT_IN)) != 0);
    }
}
