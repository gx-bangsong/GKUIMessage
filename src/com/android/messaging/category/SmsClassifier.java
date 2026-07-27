/*
 * Copyright (C) 2026
 * SPDX-License-Identifier: Apache-2.0
 */
package com.android.messaging.category;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.ContactsContract.PhoneLookup;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.messaging.datamodel.DatabaseWrapper;
import com.android.messaging.smartcore.CategoryDefinition;
import com.android.messaging.smartcore.Classification;
import com.android.messaging.smartcore.ConversationClassifier;
import com.android.messaging.smartcore.RuleMatchType;
import com.android.messaging.smartcore.WeightedRule;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Local, deterministic SMS classifier. Contact messages are PERSONAL before any configurable
 * rule. Rule scores are summed by category, then the category with the highest score wins.
 */
public final class SmsClassifier {
    private SmsClassifier() {}

    @NonNull
    public static SmsClassificationResult classifyAndCache(@NonNull final Context context,
            @NonNull final DatabaseWrapper database, @NonNull final String conversationId,
            @Nullable final String messageBody, @Nullable final String sender) {
        final SmsClassificationResult result = classify(context, messageBody, sender);
        SmsCategoryRepository.cacheConversationCategory(database, conversationId, result.category.id);
        return result;
    }

    @NonNull
    public static SmsClassificationResult classify(@NonNull final Context context,
            @Nullable final String messageBody, @Nullable final String sender) {
        final List<SmsCategory> categories = SmsCategoryRepository.getCategories(false);
        return classify(categories, SmsCategoryRepository.getEnabledRulesByCategory(), context,
                messageBody, sender);
    }

    /** Exposed for the rule editor's local, unsaved-rule test result. */
    public static int scoreRules(@NonNull final List<SmsCategoryRule> rules,
            @Nullable final String messageBody, @Nullable final String sender) {
        int score = 0;
        for (final SmsCategoryRule rule : rules) {
            final WeightedRule coreRule = toCoreRule(rule);
            if (ConversationClassifier.matches(coreRule, messageBody, sender)) {
                score += Math.max(0, rule.weight);
            }
        }
        return score;
    }

    @NonNull
    private static SmsClassificationResult classify(@NonNull final List<SmsCategory> categories,
            @NonNull final Map<Integer, List<SmsCategoryRule>> rulesByCategory,
            @NonNull final Context context, @Nullable final String messageBody,
            @Nullable final String sender) {
        SmsCategory all = null;
        final HashMap<String, SmsCategory> appCategories = new HashMap<>();
        final ArrayList<CategoryDefinition> coreCategories = new ArrayList<>();
        for (final SmsCategory category : categories) {
            if (category.type == SmsCategoryType.ALL) all = category;
            appCategories.put(String.valueOf(category.id), category);
            final List<SmsCategoryRule> rules = rulesByCategory.get(category.id);
            final ArrayList<WeightedRule> coreRules = new ArrayList<>();
            if (rules != null) {
                for (final SmsCategoryRule rule : rules) coreRules.add(toCoreRule(rule));
            }
            coreCategories.add(new CategoryDefinition(String.valueOf(category.id), category.enabled,
                    category.type == SmsCategoryType.ALL,
                    category.type == SmsCategoryType.PERSONAL, category.sortOrder, coreRules));
        }
        if (all == null) {
            all = new SmsCategory(SmsCategoryRepository.ALL_CATEGORY_ID, "全部", SmsCategoryType.ALL,
                    0xff5f6368, "all", true, 0, true);
            appCategories.put(String.valueOf(all.id), all);
            coreCategories.add(new CategoryDefinition(String.valueOf(all.id), true, true, false, 0,
                    null));
        }
        final Classification result = ConversationClassifier.classify(isKnownContact(context, sender),
                coreCategories, messageBody, sender);
        final SmsCategory selected = appCategories.get(result.categoryId);
        return new SmsClassificationResult(selected == null ? all : selected, result.score);
    }

    @NonNull
    private static WeightedRule toCoreRule(@NonNull final SmsCategoryRule rule) {
        return new WeightedRule(RuleMatchType.valueOf(rule.matchType.name()), rule.value, rule.weight,
                rule.enabled);
    }

    private static boolean isKnownContact(@NonNull final Context context, @Nullable final String sender) {
        if (TextUtils.isEmpty(sender)) {
            return false;
        }
        final Uri lookupUri = Uri.withAppendedPath(PhoneLookup.CONTENT_FILTER_URI,
                Uri.encode(sender));
        try (Cursor cursor = context.getContentResolver().query(lookupUri,
                new String[] {PhoneLookup._ID}, null, null, null)) {
            return cursor != null && cursor.moveToFirst();
        } catch (final SecurityException exception) {
            // Permission loss should gracefully fall back to rule-based classification.
            return false;
        }
    }
}
