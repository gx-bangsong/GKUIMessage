/* SPDX-License-Identifier: Apache-2.0 */
package com.android.messaging.smartcore;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/** Deterministic priority-ordered OTP recognition with malformed-rule isolation. */
public final class OtpRecognizer {
    private OtpRecognizer() {}

    public static OtpMatch detect(final String message, final List<OtpRule> inputRules) {
        if (message == null || inputRules == null) return null;
        final ArrayList<OtpRule> rules = new ArrayList<>(inputRules);
        rules.sort(Comparator.comparingInt((OtpRule rule) -> rule.priority).thenComparingInt(
                rule -> rule.id));
        for (final OtpRule rule : rules) {
            if (!rule.enabled || rule.pattern == null) continue;
            try {
                final Matcher matcher = Pattern.compile(rule.pattern,
                        Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE).matcher(message);
                if (!matcher.find()) continue;
                final int group = matcher.groupCount() > 0 && matcher.group(1) != null ? 1 : 0;
                final String value = matcher.group(group);
                if (value != null && !value.isEmpty()) {
                    return new OtpMatch(rule.id, value, matcher.start(group), matcher.end(group));
                }
            } catch (final PatternSyntaxException ignored) {
                // A user-editable malformed rule is deliberately ignored.
            }
        }
        return null;
    }
}
