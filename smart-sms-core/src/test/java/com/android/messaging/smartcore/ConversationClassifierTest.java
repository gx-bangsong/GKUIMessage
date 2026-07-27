package com.android.messaging.smartcore;

import static org.junit.Assert.assertEquals;

import java.util.Arrays;
import org.junit.Test;

public class ConversationClassifierTest {
    @Test
    public void contactAlwaysWinsOverRules() {
        final CategoryDefinition all = new CategoryDefinition("ALL", true, true, false, 0, null);
        final CategoryDefinition personal = new CategoryDefinition("PERSONAL", true, false, true,
                1, null);
        final CategoryDefinition promotion = new CategoryDefinition("PROMOTION", true, false,
                false, 2, Arrays.asList(new WeightedRule(RuleMatchType.KEYWORD, "优惠", 30, true)));
        assertEquals("PERSONAL", ConversationClassifier.classify(true,
                Arrays.asList(all, personal, promotion), "限时优惠", "10086").categoryId);
    }

    @Test
    public void highestAccumulatedWeightWins() {
        final CategoryDefinition all = new CategoryDefinition("ALL", true, true, false, 0, null);
        final CategoryDefinition finance = new CategoryDefinition("FINANCE", true, false, false,
                10, Arrays.asList(new WeightedRule(RuleMatchType.KEYWORD, "到账", 20, true)));
        final CategoryDefinition express = new CategoryDefinition("EXPRESS", true, false, false,
                20, Arrays.asList(new WeightedRule(RuleMatchType.KEYWORD, "快递", 30, true),
                        new WeightedRule(RuleMatchType.KEYWORD, "派送", 20, true)));
        assertEquals("EXPRESS", ConversationClassifier.classify(false,
                Arrays.asList(all, finance, express), "快递正在派送，到账通知", "95338").categoryId);
    }
}
