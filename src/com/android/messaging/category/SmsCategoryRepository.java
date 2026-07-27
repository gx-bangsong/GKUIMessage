/*
 * Copyright (C) 2026
 * SPDX-License-Identifier: Apache-2.0
 */
package com.android.messaging.category;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.messaging.datamodel.DataModel;
import com.android.messaging.datamodel.DatabaseHelper;
import com.android.messaging.datamodel.DatabaseHelper.CategoryColumns;
import com.android.messaging.datamodel.DatabaseHelper.CategoryRuleColumns;
import com.android.messaging.datamodel.DatabaseHelper.ConversationColumns;
import com.android.messaging.datamodel.DatabaseWrapper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** DAO for local categories and category rules. Call all database methods off the UI thread. */
public final class SmsCategoryRepository {
    public static final int ALL_CATEGORY_ID = 1;
    private static final SmsCategory[] BUILT_IN_CATEGORIES = new SmsCategory[] {
            new SmsCategory(1, "全部", SmsCategoryType.ALL, 0xff5f6368, "all", true, 0, true),
            new SmsCategory(2, "私信", SmsCategoryType.PERSONAL, 0xff1565c0, "person", true, 10,
                    true),
            new SmsCategory(3, "验证码", SmsCategoryType.VERIFICATION, 0xff6a1b9a, "key", true,
                    20, true),
            new SmsCategory(4, "金融", SmsCategoryType.FINANCE, 0xff2e7d32, "account_balance",
                    true, 30, true),
            new SmsCategory(5, "快递", SmsCategoryType.EXPRESS, 0xffef6c00, "local_shipping",
                    true, 40, true),
            new SmsCategory(6, "推广", SmsCategoryType.PROMOTION, 0xffc62828, "campaign", true,
                    50, true),
            new SmsCategory(7, "通知", SmsCategoryType.NOTIFICATION, 0xff00695c,
                    "notifications", true, 60, true),
    };

    private static final Object[][] BUILT_IN_RULES = new Object[][] {
            {3, CategoryMatchType.KEYWORD, "验证码", 100},
            {3, CategoryMatchType.KEYWORD, "校验码", 100},
            {3, CategoryMatchType.KEYWORD, "动态码", 90},
            {3, CategoryMatchType.KEYWORD, "OTP", 90},
            {4, CategoryMatchType.KEYWORD, "银行", 30},
            {4, CategoryMatchType.KEYWORD, "消费", 25},
            {4, CategoryMatchType.KEYWORD, "到账", 25},
            {4, CategoryMatchType.KEYWORD, "转账", 25},
            {4, CategoryMatchType.KEYWORD, "账单", 25},
            {4, CategoryMatchType.KEYWORD, "还款", 25},
            {4, CategoryMatchType.KEYWORD, "余额", 20},
            {5, CategoryMatchType.KEYWORD, "快递", 35},
            {5, CategoryMatchType.KEYWORD, "物流", 35},
            {5, CategoryMatchType.KEYWORD, "派送", 30},
            {5, CategoryMatchType.KEYWORD, "揽收", 30},
            {5, CategoryMatchType.KEYWORD, "取件", 30},
            {6, CategoryMatchType.KEYWORD, "退订", 35},
            {6, CategoryMatchType.KEYWORD, "优惠", 25},
            {6, CategoryMatchType.KEYWORD, "促销", 30},
            {6, CategoryMatchType.KEYWORD, "限时", 20},
            {6, CategoryMatchType.KEYWORD, "点击领取", 30},
            {7, CategoryMatchType.KEYWORD, "通知", 20},
            {7, CategoryMatchType.KEYWORD, "提醒", 20},
            {7, CategoryMatchType.KEYWORD, "成功", 10},
            {7, CategoryMatchType.KEYWORD, "服务", 10},
    };

    private SmsCategoryRepository() {}

    /** Called while creating/upgrading the raw SQLite database. Safe to call repeatedly. */
    public static void seedBuiltInData(@NonNull final SQLiteDatabase database) {
        for (final SmsCategory category : BUILT_IN_CATEGORIES) {
            database.insertWithOnConflict(DatabaseHelper.SMS_CATEGORIES_TABLE, null,
                    categoryValues(category, true), SQLiteDatabase.CONFLICT_IGNORE);
        }
        for (final Object[] rule : BUILT_IN_RULES) {
            final int categoryId = (Integer) rule[0];
            final CategoryMatchType matchType = (CategoryMatchType) rule[1];
            final String value = (String) rule[2];
            final int weight = (Integer) rule[3];
            final Cursor existing = database.query(DatabaseHelper.SMS_CATEGORY_RULES_TABLE,
                    new String[] {CategoryRuleColumns._ID}, CategoryRuleColumns.CATEGORY_ID
                            + "=? AND " + CategoryRuleColumns.MATCH_TYPE + "=? AND "
                            + CategoryRuleColumns.VALUE + "=?", new String[] {
                                    String.valueOf(categoryId), matchType.name(), value},
                    null, null, null);
            try {
                if (existing.moveToFirst()) {
                    continue;
                }
            } finally {
                existing.close();
            }
            database.insert(DatabaseHelper.SMS_CATEGORY_RULES_TABLE, null,
                    ruleValues(new SmsCategoryRule(0, categoryId, matchType, value, weight, true),
                            false));
        }
    }

    @NonNull
    public static List<SmsCategory> getCategories(final boolean includeDisabled) {
        final DatabaseWrapper database = database();
        ensureBuiltInData(database);
        final String selection = includeDisabled ? null : CategoryColumns.ENABLED + "=1";
        return queryCategories(database, selection);
    }

    @Nullable
    public static SmsCategory getCategory(final int categoryId) {
        final DatabaseWrapper database = database();
        ensureBuiltInData(database);
        final List<SmsCategory> categories = queryCategories(database,
                CategoryColumns._ID + "=" + categoryId);
        return categories.isEmpty() ? null : categories.get(0);
    }

    @NonNull
    public static Map<Integer, List<SmsCategoryRule>> getEnabledRulesByCategory() {
        final DatabaseWrapper database = database();
        ensureBuiltInData(database);
        final LinkedHashMap<Integer, List<SmsCategoryRule>> rules = new LinkedHashMap<>();
        final Cursor cursor = database.query(DatabaseHelper.SMS_CATEGORY_RULES_TABLE, null,
                CategoryRuleColumns.ENABLED + "=1", null, null, null,
                CategoryRuleColumns.CATEGORY_ID + " ASC, " + CategoryRuleColumns._ID + " ASC");
        try {
            while (cursor.moveToNext()) {
                final SmsCategoryRule rule = ruleFromCursor(cursor);
                List<SmsCategoryRule> categoryRules = rules.get(rule.categoryId);
                if (categoryRules == null) {
                    categoryRules = new ArrayList<>();
                    rules.put(rule.categoryId, categoryRules);
                }
                categoryRules.add(rule);
            }
        } finally {
            cursor.close();
        }
        return rules;
    }

    @NonNull
    public static List<SmsCategoryRule> getRules(final int categoryId) {
        final DatabaseWrapper database = database();
        ensureBuiltInData(database);
        final ArrayList<SmsCategoryRule> rules = new ArrayList<>();
        final Cursor cursor = database.query(DatabaseHelper.SMS_CATEGORY_RULES_TABLE, null,
                CategoryRuleColumns.CATEGORY_ID + "=?", new String[] {String.valueOf(categoryId)},
                null, null, CategoryRuleColumns._ID + " ASC");
        try {
            while (cursor.moveToNext()) {
                rules.add(ruleFromCursor(cursor));
            }
        } finally {
            cursor.close();
        }
        return rules;
    }

    public static int insertCustomCategory(@NonNull final String name, final int color,
            @NonNull final String icon) {
        final DatabaseWrapper database = database();
        ensureBuiltInData(database);
        final int order = getMaxSortOrder(database) + 10;
        final long id = database.insert(DatabaseHelper.SMS_CATEGORIES_TABLE, null,
                categoryValues(new SmsCategory(0, name, SmsCategoryType.CUSTOM, color, icon,
                        true, order, false), false));
        return (int) id;
    }

    public static void updateCategory(@NonNull final SmsCategory category) {
        if (category.type == SmsCategoryType.ALL) {
            return;
        }
        database().update(DatabaseHelper.SMS_CATEGORIES_TABLE, categoryValues(category, false),
                CategoryColumns._ID + "=?", new String[] {String.valueOf(category.id)});
    }

    public static void setCategoryEnabled(final int categoryId, final boolean enabled) {
        if (categoryId == ALL_CATEGORY_ID) {
            return;
        }
        final ContentValues values = new ContentValues();
        values.put(CategoryColumns.ENABLED, enabled ? 1 : 0);
        database().update(DatabaseHelper.SMS_CATEGORIES_TABLE, values, CategoryColumns._ID + "=?",
                new String[] {String.valueOf(categoryId)});
    }

    public static void updateSortOrders(@NonNull final List<SmsCategory> categories) {
        final DatabaseWrapper database = database();
        database.beginTransaction();
        try {
            int order = 0;
            for (final SmsCategory category : categories) {
                final ContentValues values = new ContentValues();
                values.put(CategoryColumns.SORT_ORDER, order);
                database.update(DatabaseHelper.SMS_CATEGORIES_TABLE, values,
                        CategoryColumns._ID + "=?", new String[] {String.valueOf(category.id)});
                order += 10;
            }
            database.setTransactionSuccessful();
        } finally {
            database.endTransaction();
        }
    }

    public static void deleteCustomCategory(final int categoryId) {
        database().delete(DatabaseHelper.SMS_CATEGORIES_TABLE,
                CategoryColumns._ID + "=? AND " + CategoryColumns.IS_BUILT_IN + "=0",
                new String[] {String.valueOf(categoryId)});
    }

    public static void replaceRules(final int categoryId, @NonNull final List<SmsCategoryRule> rules) {
        final DatabaseWrapper database = database();
        database.beginTransaction();
        try {
            database.delete(DatabaseHelper.SMS_CATEGORY_RULES_TABLE,
                    CategoryRuleColumns.CATEGORY_ID + "=?", new String[] {String.valueOf(categoryId)});
            for (final SmsCategoryRule rule : rules) {
                database.insert(DatabaseHelper.SMS_CATEGORY_RULES_TABLE, null,
                        ruleValues(new SmsCategoryRule(0, categoryId, rule.matchType, rule.value,
                                rule.weight, rule.enabled), false));
            }
            database.setTransactionSuccessful();
        } finally {
            database.endTransaction();
        }
    }

    /** Restores categories and built-in rules, and removes all custom category/rule changes. */
    public static void resetToDefaults() {
        final DatabaseWrapper database = database();
        database.beginTransaction();
        try {
            database.delete(DatabaseHelper.SMS_CATEGORY_RULES_TABLE, null, null);
            database.delete(DatabaseHelper.SMS_CATEGORIES_TABLE,
                    CategoryColumns.IS_BUILT_IN + "=0", null);
            for (final SmsCategory category : BUILT_IN_CATEGORIES) {
                database.update(DatabaseHelper.SMS_CATEGORIES_TABLE, categoryValues(category, false),
                        CategoryColumns._ID + "=?", new String[] {String.valueOf(category.id)});
            }
            seedBuiltInData(database.getDatabase());
            database.setTransactionSuccessful();
        } finally {
            database.endTransaction();
        }
    }

    public static void cacheConversationCategory(@NonNull final DatabaseWrapper database,
            @NonNull final String conversationId, final int categoryId) {
        final ContentValues values = new ContentValues();
        values.put(ConversationColumns.CATEGORY_ID, categoryId);
        values.put(ConversationColumns.CLASSIFIED_AT, System.currentTimeMillis());
        database.update(DatabaseHelper.CONVERSATIONS_TABLE, values, ConversationColumns._ID + "=?",
                new String[] {conversationId});
    }

    private static int getMaxSortOrder(final DatabaseWrapper database) {
        final Cursor cursor = database.rawQuery("SELECT MAX(" + CategoryColumns.SORT_ORDER + ") FROM "
                + DatabaseHelper.SMS_CATEGORIES_TABLE, null);
        try {
            return cursor.moveToFirst() && !cursor.isNull(0) ? cursor.getInt(0) : 0;
        } finally {
            cursor.close();
        }
    }

    @NonNull
    private static List<SmsCategory> queryCategories(final DatabaseWrapper database,
            @Nullable final String selection) {
        final ArrayList<SmsCategory> categories = new ArrayList<>();
        final Cursor cursor = database.query(DatabaseHelper.SMS_CATEGORIES_TABLE, null, selection,
                null, null, null, CategoryColumns.SORT_ORDER + " ASC, " + CategoryColumns._ID
                        + " ASC");
        try {
            while (cursor.moveToNext()) {
                categories.add(categoryFromCursor(cursor));
            }
        } finally {
            cursor.close();
        }
        return categories;
    }

    private static DatabaseWrapper database() {
        return DataModel.get().getDatabase();
    }

    private static void ensureBuiltInData(final DatabaseWrapper database) {
        seedBuiltInData(database.getDatabase());
    }

    private static ContentValues categoryValues(final SmsCategory category, final boolean includeId) {
        final ContentValues values = new ContentValues();
        if (includeId) {
            values.put(CategoryColumns._ID, category.id);
        }
        values.put(CategoryColumns.NAME, category.name);
        values.put(CategoryColumns.TYPE, category.type.name());
        values.put(CategoryColumns.COLOR, category.color);
        values.put(CategoryColumns.ICON, category.icon);
        values.put(CategoryColumns.ENABLED, category.enabled ? 1 : 0);
        values.put(CategoryColumns.SORT_ORDER, category.sortOrder);
        values.put(CategoryColumns.IS_BUILT_IN, category.isBuiltIn ? 1 : 0);
        return values;
    }

    private static ContentValues ruleValues(final SmsCategoryRule rule, final boolean includeId) {
        final ContentValues values = new ContentValues();
        if (includeId) {
            values.put(CategoryRuleColumns._ID, rule.id);
        }
        values.put(CategoryRuleColumns.CATEGORY_ID, rule.categoryId);
        values.put(CategoryRuleColumns.MATCH_TYPE, rule.matchType.name());
        values.put(CategoryRuleColumns.VALUE, rule.value);
        values.put(CategoryRuleColumns.WEIGHT, rule.weight);
        values.put(CategoryRuleColumns.ENABLED, rule.enabled ? 1 : 0);
        return values;
    }

    private static SmsCategory categoryFromCursor(final Cursor cursor) {
        return new SmsCategory(cursor.getInt(cursor.getColumnIndexOrThrow(CategoryColumns._ID)),
                cursor.getString(cursor.getColumnIndexOrThrow(CategoryColumns.NAME)),
                SmsCategoryType.valueOf(cursor.getString(cursor.getColumnIndexOrThrow(
                        CategoryColumns.TYPE))),
                cursor.getInt(cursor.getColumnIndexOrThrow(CategoryColumns.COLOR)),
                cursor.getString(cursor.getColumnIndexOrThrow(CategoryColumns.ICON)),
                cursor.getInt(cursor.getColumnIndexOrThrow(CategoryColumns.ENABLED)) != 0,
                cursor.getInt(cursor.getColumnIndexOrThrow(CategoryColumns.SORT_ORDER)),
                cursor.getInt(cursor.getColumnIndexOrThrow(CategoryColumns.IS_BUILT_IN)) != 0);
    }

    private static SmsCategoryRule ruleFromCursor(final Cursor cursor) {
        return new SmsCategoryRule(cursor.getInt(cursor.getColumnIndexOrThrow(CategoryRuleColumns._ID)),
                cursor.getInt(cursor.getColumnIndexOrThrow(CategoryRuleColumns.CATEGORY_ID)),
                CategoryMatchType.valueOf(cursor.getString(cursor.getColumnIndexOrThrow(
                        CategoryRuleColumns.MATCH_TYPE))),
                cursor.getString(cursor.getColumnIndexOrThrow(CategoryRuleColumns.VALUE)),
                cursor.getInt(cursor.getColumnIndexOrThrow(CategoryRuleColumns.WEIGHT)),
                cursor.getInt(cursor.getColumnIndexOrThrow(CategoryRuleColumns.ENABLED)) != 0);
    }
}
