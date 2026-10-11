package com.android.messaging.smartcore;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.util.Arrays;
import org.junit.Test;

/** Functional regression tests for priority-based OTP extraction. */
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
    public void disabledRuleIsSkippedInFavorOfEnabledRule() {
        final OtpMatch match = OtpRecognizer.detect("验证码：765432", Arrays.asList(
                new OtpRule(1, "验证码[：:](\\d{6})", 1, false),
                new OtpRule(2, "验证码[：:](\\d{6})", 2, true)));
        assertEquals(2, match.ruleId);
        assertEquals("765432", match.value);
    }

    @Test
    public void malformedRuleDoesNotPreventLaterValidRule() {
        final OtpMatch match = OtpRecognizer.detect("验证码 246810", Arrays.asList(
                new OtpRule(1, "([", 1, true),
                new OtpRule(2, "验证码\\s*(\\d{6})", 2, true)));
        assertEquals(2, match.ruleId);
        assertEquals("246810", match.value);
    }

    @Test
    public void patternWithoutCaptureGroupReturnsWholeMatch() {
        final OtpMatch match = OtpRecognizer.detect("OTP=ABC123", Arrays.asList(
                new OtpRule(1, "ABC123", 1, true)));
        assertEquals("ABC123", match.value);
        assertEquals(4, match.start);
        assertEquals(10, match.end);
    }

    @Test
    public void noMatchingRuleReturnsNull() {
        assertNull(OtpRecognizer.detect("这不是验证码短信", Arrays.asList(
                new OtpRule(1, "验证码[：:](\\d{6})", 1, true))));
    }
}
