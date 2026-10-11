package com.android.messaging.smartcore;

import static org.junit.Assert.assertEquals;

import java.util.Arrays;
import org.junit.Test;

/** Functional regression tests for contact-first weighted category classification. */
public class ConversationClassifierTest {
    @Test
    public void contactAlwaysWinsOverRules() {
        final CategoryDefinition all = all();
        final CategoryDefinition personal = new CategoryDefinition("PERSONAL", true, false, true,
                1, null);
        final CategoryDefinition promotion = category("PROMOTION", 2,
                new WeightedRule(RuleMatchType.KEYWORD, "优惠", 30, true));
        assertEquals("PERSONAL", ConversationClassifier.classify(true,
                Arrays.asList(all, personal, promotion), "限时优惠", "10086").categoryId);
    }

    @Test
    public void highestAccumulatedWeightWins() {
        final CategoryDefinition finance = category("FINANCE", 10,
                new WeightedRule(RuleMatchType.KEYWORD, "到账", 20, true));
        final CategoryDefinition express = category("EXPRESS", 20,
                new WeightedRule(RuleMatchType.KEYWORD, "快递", 30, true),
                new WeightedRule(RuleMatchType.KEYWORD, "派送", 20, true));
        final Classification result = ConversationClassifier.classify(false,
                Arrays.asList(all(), finance, express), "快递正在派送，到账通知", "95338");
        assertEquals("EXPRESS", result.categoryId);
        assertEquals(50, result.score);
    }

    @Test
    public void senderPrefixRuleMatchesNormalizedNumber() {
        final CategoryDefinition express = category("EXPRESS", 10,
                new WeightedRule(RuleMatchType.SENDER, "95338", 30, true));
        assertEquals("EXPRESS", ConversationClassifier.classify(false,
                Arrays.asList(all(), express), "您的包裹已更新", "+86 95338").categoryId);
    }

    @Test
    public void invalidRegexAndDisabledRulesAreIgnored() {
        final CategoryDefinition promotion = category("PROMOTION", 10,
                new WeightedRule(RuleMatchType.REGEX, "([", 100, true),
                new WeightedRule(RuleMatchType.KEYWORD, "优惠", 100, false));
        assertEquals("ALL", ConversationClassifier.classify(false,
                Arrays.asList(all(), promotion), "优惠活动", "10086").categoryId);
    }

    @Test
    public void equalScoreUsesLowerSortOrderThenCategoryId() {
        final CategoryDefinition later = category("Z_CATEGORY", 20,
                new WeightedRule(RuleMatchType.KEYWORD, "通知", 20, true));
        final CategoryDefinition earlier = category("A_CATEGORY", 10,
                new WeightedRule(RuleMatchType.KEYWORD, "通知", 20, true));
        assertEquals("A_CATEGORY", ConversationClassifier.classify(false,
                Arrays.asList(all(), later, earlier), "服务通知", "10086").categoryId);
    }

    private static CategoryDefinition all() {
        return new CategoryDefinition("ALL", true, true, false, 0, null);
    }

    private static CategoryDefinition category(final String id, final int sortOrder,
            final WeightedRule... rules) {
        return new CategoryDefinition(id, true, false, false, sortOrder, Arrays.asList(rules));
    }
}
