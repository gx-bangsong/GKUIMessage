/* SPDX-License-Identifier: Apache-2.0 */
package com.android.messaging.smartcore;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/** Contact-first conversation classifier with deterministic weighted-rule tie breaks. */
public final class ConversationClassifier {
    private ConversationClassifier() {}

    public static Classification classify(final boolean senderIsContact,
            final List<CategoryDefinition> categories, final String body, final String sender) {
        CategoryDefinition all = null;
        CategoryDefinition personal = null;
        if (categories != null) {
            for (final CategoryDefinition category : categories) {
                if (category.all) all = category;
                if (category.personal) personal = category;
            }
        }
        if (all == null) all = new CategoryDefinition("ALL", true, true, false, 0, null);
        if (senderIsContact && personal != null && personal.enabled) {
            return new Classification(personal.id, Integer.MAX_VALUE);
        }
        CategoryDefinition winner = all;
        int winnerScore = 0;
        if (categories != null) {
            for (final CategoryDefinition category : categories) {
                if (!category.enabled || category.all || category.personal) continue;
                int score = 0;
                for (final WeightedRule rule : category.rules) {
                    if (matches(rule, body, sender)) score += Math.max(0, rule.weight);
                }
                if (score > winnerScore || (score == winnerScore && score > 0
                        && compare(category, winner) < 0)) {
                    winner = category;
                    winnerScore = score;
                }
            }
        }
        return new Classification(winner.id, winnerScore);
    }

    public static boolean matches(final WeightedRule rule, final String body, final String sender) {
        if (rule == null || !rule.enabled || rule.value == null || rule.value.isEmpty()) return false;
        switch (rule.type) {
            case KEYWORD:
                return body != null && body.toLowerCase(Locale.ROOT).contains(
                        rule.value.toLowerCase(Locale.ROOT));
            case SENDER:
                return normalize(sender).startsWith(normalize(rule.value));
            case REGEX:
                if (body == null) return false;
                try {
                    return Pattern.compile(rule.value, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE)
                            .matcher(body).find();
                } catch (final PatternSyntaxException ignored) {
                    return false;
                }
            default:
                return false;
        }
    }

    private static int compare(final CategoryDefinition first, final CategoryDefinition second) {
        final int sort = Integer.compare(first.sortOrder, second.sortOrder);
        return sort != 0 ? sort : first.id.compareTo(second.id);
    }

    private static String normalize(final String sender) {
        return sender == null ? "" : sender.replaceAll("[^0-9A-Za-z+]", "");
    }
}
