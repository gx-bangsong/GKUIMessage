/* SPDX-License-Identifier: Apache-2.0 */
package com.android.messaging.smartcore;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/** Pure Java trigger, extraction, and confidence engine for service SMS rules. */
public final class ServiceSmsParser {
    private ServiceSmsParser() {}

    public static List<ParsedServiceCard> parse(final List<ServiceCardRule> rules,
            final String body, final String sender, final float minimumConfidence) {
        final ArrayList<ParsedServiceCard> cards = new ArrayList<>();
        if (rules == null || body == null) return cards;
        for (final ServiceCardRule rule : rules) {
            if (!isTriggered(rule, body, sender)) continue;
            final ParsedServiceCard card = parseRule(rule, body);
            if (card != null && card.confidence >= bounded(minimumConfidence)) cards.add(card);
        }
        return cards;
    }

    public static boolean isTriggered(final ServiceCardRule rule, final String body,
            final String sender) {
        if (rule == null || body == null) return false;
        final String lower = body.toLowerCase(Locale.ROOT);
        for (final String keyword : rule.keywords) {
            if (keyword != null && lower.contains(keyword.toLowerCase(Locale.ROOT))) return true;
        }
        final String normalized = normalize(sender);
        for (final String prefix : rule.senderPrefixes) {
            if (prefix != null && normalized.startsWith(normalize(prefix))) return true;
        }
        return false;
    }

    private static ParsedServiceCard parseRule(final ServiceCardRule rule, final String body) {
        final Map<String, String> fields = new LinkedHashMap<>();
        float matchedWeight = 0f;
        float requiredWeight = 0f;
        for (final ServiceCardField field : rule.fields) {
            if (field.required) requiredWeight += field.weight;
            final String value = findFirst(field.patterns, body);
            if (value != null) {
                fields.put(field.name, value);
                matchedWeight += field.weight;
            }
        }
        if (requiredWeight <= 0f) requiredWeight = 1f;
        return new ParsedServiceCard(rule.cardType, fields,
                Math.min(1f, Math.max(0f, matchedWeight / requiredWeight)));
    }

    private static String findFirst(final List<String> patterns, final String body) {
        for (final String expression : patterns) {
            try {
                final Matcher matcher = Pattern.compile(expression,
                        Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE).matcher(body);
                if (!matcher.find()) continue;
                for (int group = 1; group <= matcher.groupCount(); group++) {
                    if (matcher.group(group) != null) return matcher.group(group).trim();
                }
                return matcher.group().trim();
            } catch (final PatternSyntaxException ignored) {
                // Imported malformed expressions are ignored instead of stopping the parser.
            }
        }
        return null;
    }

    private static float bounded(final float value) { return Math.max(0f, Math.min(1f, value)); }
    private static String normalize(final String sender) {
        return sender == null ? "" : sender.replaceAll("[^0-9A-Za-z+]", "");
    }
}
