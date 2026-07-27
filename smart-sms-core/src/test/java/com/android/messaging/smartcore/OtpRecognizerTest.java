package com.android.messaging.smartcore;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.util.Arrays;
import org.junit.Test;

public class OtpRecognizerTest {
    @Test
    public void lowerPriorityRuleDoesNotOverrideFirstPriorityMatch() {
        final OtpMatch match = OtpRecognizer.detect("您的验证码是123456", Arrays.asList(
                new OtpRule(20, "(\\d{6})", 20, true),
                new OtpRule(10, "验证码是(\\d{6})", 10, true)));
        assertEquals(10, match.ruleId);
        assertEquals("123456", match.value);
    }

    @Test
    public void malformedRuleIsIgnored() {
        assertNull(OtpRecognizer.detect("nothing", Arrays.asList(
                new OtpRule(1, "([", 1, true))));
    }
}
