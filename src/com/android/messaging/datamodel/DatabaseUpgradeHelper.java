/*
 * Copyright (C) 2015 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.messaging.datamodel;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;

import com.android.messaging.Factory;
import com.android.messaging.category.SmsCategoryRepository;
import com.android.messaging.otp.OtpRuleRepository;
import com.android.messaging.util.Assert;
import com.android.messaging.util.LogUtil;

public class DatabaseUpgradeHelper {
    private static final String TAG = LogUtil.BUGLE_DATABASE_TAG;

    public void doOnUpgrade(final SQLiteDatabase db, final int oldVersion, final int newVersion) {
        Assert.isTrue(newVersion >= oldVersion);
        if (oldVersion == newVersion) {
            return;
        }

        LogUtil.i(TAG, "Database upgrade started from version " + oldVersion + " to " + newVersion);
        try {
            doUpgradeWithExceptions(db, oldVersion, newVersion);
            LogUtil.i(TAG, "Finished database upgrade");
        } catch (final Exception ex) {
            LogUtil.e(TAG, "Failed to perform db upgrade from version " +
                    oldVersion + " to version " + newVersion, ex);
            DatabaseHelper.rebuildTables(db);
        }
    }

    public void doUpgradeWithExceptions(final SQLiteDatabase db, final int oldVersion,
            final int newVersion) throws Exception {
        int currentVersion = oldVersion;
        if (currentVersion < 2) {
            currentVersion = upgradeToVersion2(db);
        }
        if (currentVersion < 3) {
            currentVersion = upgradeToVersion3(db);
        }
        if (currentVersion < 4) {
            currentVersion = upgradeToVersion4(db);
        }
        if (currentVersion < 5) {
            currentVersion = upgradeToVersion5(db);
        }
        // Rebuild all the views
        final Context context = Factory.get().getApplicationContext();
        DatabaseHelper.dropAllViews(db);
        DatabaseHelper.rebuildAllViews(new DatabaseWrapper(context, db));
        // Finally, check if we have arrived at the final version.
        checkAndUpdateVersionAtReleaseEnd(currentVersion, Integer.MAX_VALUE, newVersion);
    }

    private int upgradeToVersion2(final SQLiteDatabase db) {
        db.execSQL("ALTER TABLE " + DatabaseHelper.CONVERSATIONS_TABLE + " ADD COLUMN " +
                DatabaseHelper.ConversationColumns.IS_ENTERPRISE + " INT DEFAULT(0)");
        LogUtil.i(TAG, "Ugraded database to version 2");
        return 2;
    }

    private int upgradeToVersion3(final SQLiteDatabase db) {
        db.execSQL("CREATE TABLE " + DatabaseHelper.OTP_RULES_TABLE + "("
                + DatabaseHelper.OtpRuleColumns._ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + DatabaseHelper.OtpRuleColumns.NAME + " TEXT NOT NULL, "
                + DatabaseHelper.OtpRuleColumns.PATTERN + " TEXT NOT NULL, "
                + DatabaseHelper.OtpRuleColumns.PRIORITY + " INTEGER NOT NULL, "
                + DatabaseHelper.OtpRuleColumns.ENABLED + " INTEGER NOT NULL DEFAULT 1, "
                + DatabaseHelper.OtpRuleColumns.IS_BUILT_IN + " INTEGER NOT NULL DEFAULT 0);");
        db.execSQL("CREATE INDEX index_" + DatabaseHelper.OTP_RULES_TABLE + "_priority ON "
                + DatabaseHelper.OTP_RULES_TABLE + "(" + DatabaseHelper.OtpRuleColumns.PRIORITY
                + ", " + DatabaseHelper.OtpRuleColumns._ID + ")");
        OtpRuleRepository.seedBuiltInRules(db);
        LogUtil.i(TAG, "Upgraded database to version 3");
        return 3;
    }

    private int upgradeToVersion4(final SQLiteDatabase db) {
        db.execSQL("ALTER TABLE " + DatabaseHelper.CONVERSATIONS_TABLE + " ADD COLUMN "
                + DatabaseHelper.ConversationColumns.CATEGORY_ID + " INT DEFAULT(1)");
        db.execSQL("ALTER TABLE " + DatabaseHelper.CONVERSATIONS_TABLE + " ADD COLUMN "
                + DatabaseHelper.ConversationColumns.CLASSIFIED_AT + " INT DEFAULT(0)");
        db.execSQL("CREATE TABLE " + DatabaseHelper.SMS_CATEGORIES_TABLE + "("
                + DatabaseHelper.CategoryColumns._ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + DatabaseHelper.CategoryColumns.NAME + " TEXT NOT NULL, "
                + DatabaseHelper.CategoryColumns.TYPE + " TEXT NOT NULL, "
                + DatabaseHelper.CategoryColumns.COLOR + " INTEGER NOT NULL, "
                + DatabaseHelper.CategoryColumns.ICON + " TEXT NOT NULL, "
                + DatabaseHelper.CategoryColumns.ENABLED + " INTEGER NOT NULL DEFAULT 1, "
                + DatabaseHelper.CategoryColumns.SORT_ORDER + " INTEGER NOT NULL, "
                + DatabaseHelper.CategoryColumns.IS_BUILT_IN + " INTEGER NOT NULL DEFAULT 0)");
        db.execSQL("CREATE TABLE " + DatabaseHelper.SMS_CATEGORY_RULES_TABLE + "("
                + DatabaseHelper.CategoryRuleColumns._ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + DatabaseHelper.CategoryRuleColumns.CATEGORY_ID + " INTEGER NOT NULL, "
                + DatabaseHelper.CategoryRuleColumns.MATCH_TYPE + " TEXT NOT NULL, "
                + DatabaseHelper.CategoryRuleColumns.VALUE + " TEXT NOT NULL, "
                + DatabaseHelper.CategoryRuleColumns.WEIGHT + " INTEGER NOT NULL DEFAULT 0, "
                + DatabaseHelper.CategoryRuleColumns.ENABLED + " INTEGER NOT NULL DEFAULT 1, "
                + "FOREIGN KEY(" + DatabaseHelper.CategoryRuleColumns.CATEGORY_ID + ") REFERENCES "
                + DatabaseHelper.SMS_CATEGORIES_TABLE + "(" + DatabaseHelper.CategoryColumns._ID
                + ") ON DELETE CASCADE)");
        db.execSQL("CREATE INDEX index_" + DatabaseHelper.SMS_CATEGORIES_TABLE + "_sort ON "
                + DatabaseHelper.SMS_CATEGORIES_TABLE + "(" + DatabaseHelper.CategoryColumns.SORT_ORDER
                + ", " + DatabaseHelper.CategoryColumns._ID + ")");
        db.execSQL("CREATE INDEX index_" + DatabaseHelper.SMS_CATEGORY_RULES_TABLE + "_category ON "
                + DatabaseHelper.SMS_CATEGORY_RULES_TABLE + "("
                + DatabaseHelper.CategoryRuleColumns.CATEGORY_ID + ")");
        SmsCategoryRepository.seedBuiltInData(db);
        LogUtil.i(TAG, "Upgraded database to version 4");
        return 4;
    }

    private int upgradeToVersion5(final SQLiteDatabase db) {
        db.execSQL("CREATE TABLE " + DatabaseHelper.SMS_CARDS_TABLE + "("
                + DatabaseHelper.SmsCardColumns._ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + DatabaseHelper.SmsCardColumns.MESSAGE_ID + " INTEGER NOT NULL, "
                + DatabaseHelper.SmsCardColumns.THREAD_ID + " INTEGER NOT NULL, "
                + DatabaseHelper.SmsCardColumns.CARD_TYPE + " TEXT NOT NULL, "
                + DatabaseHelper.SmsCardColumns.CARD_DATA + " TEXT NOT NULL, "
                + DatabaseHelper.SmsCardColumns.RAW_TEXT + " TEXT NOT NULL, "
                + DatabaseHelper.SmsCardColumns.CONFIDENCE + " REAL NOT NULL, "
                + DatabaseHelper.SmsCardColumns.PARSED_AT + " INTEGER NOT NULL, "
                + DatabaseHelper.SmsCardColumns.IS_EXPANDED + " INTEGER NOT NULL DEFAULT 0, "
                + "UNIQUE(" + DatabaseHelper.SmsCardColumns.MESSAGE_ID + ", "
                + DatabaseHelper.SmsCardColumns.CARD_TYPE + "))");
        db.execSQL("CREATE INDEX index_" + DatabaseHelper.SMS_CARDS_TABLE + "_message ON "
                + DatabaseHelper.SMS_CARDS_TABLE + "(" + DatabaseHelper.SmsCardColumns.MESSAGE_ID
                + ", " + DatabaseHelper.SmsCardColumns.CONFIDENCE + ")");
        LogUtil.i(TAG, "Upgraded database to version 5");
        return 5;
    }

    /**
     * Checks db version correctness at the end of each milestone release. If target database
     * version lies beyond the version range that the current release may handle, we snap the
     * current version to the end of the release, so that we may go on to the next release' upgrade
     * path. Otherwise, if target version is within reach of the current release, but we are not
     * at the target version, then throw an exception to force a table rebuild.
     */
    private int checkAndUpdateVersionAtReleaseEnd(final int currentVersion,
            final int maxVersionForRelease, final int targetVersion) throws Exception {
        if (maxVersionForRelease < targetVersion) {
            // Target version is beyond the current release. Snap to max version for the
            // current release so we can go on to the upgrade path for the next release.
            return maxVersionForRelease;
        }

        // If we are here, this means the current release' upgrade handler should upgrade to
        // target version...
        if (currentVersion != targetVersion) {
            // No more upgrade handlers. So we can't possibly upgrade to the final version.
            throw new Exception("Missing upgrade handler from version " +
                    currentVersion + " to version " + targetVersion);
        }
        // Upgrade succeeded.
        return targetVersion;
    }

    public void onDowngrade(final SQLiteDatabase db, final int oldVersion, final int newVersion) {
        DatabaseHelper.rebuildTables(db);
        LogUtil.e(TAG, "Database downgrade requested for version " +
                oldVersion + " version " + newVersion + ", forcing db rebuild!");
    }
}
