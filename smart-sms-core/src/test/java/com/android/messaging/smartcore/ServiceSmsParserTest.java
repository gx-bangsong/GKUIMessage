package com.android.messaging.smartcore;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.List;
import org.junit.Test;

/** Functional regression tests for local service-SMS card extraction and confidence. */
public class ServiceSmsParserTest {
    @Test
    public void extractsExpressTrackingAndPickupCode() {
        final ServiceCardRule rule = expressRule();
        final List<ParsedServiceCard> cards = ServiceSmsParser.parse(Arrays.asList(rule),
                "顺丰快递：运单号SF123456789012，取件码A1234", "95338", .5f);
        assertEquals(1, cards.size());
        assertEquals("SF123456789012", cards.get(0).fields.get("trackingNumber"));
        assertEquals("A1234", cards.get(0).fields.get("pickupCode"));
        assertTrue(cards.get(0).confidence >= .5f);
    }

    @Test
    public void senderPrefixTriggersRuleWithoutKeyword() {
        final ServiceCardRule rule = new ServiceCardRule("EXPRESS_CARD", Arrays.asList("快递"),
                Arrays.asList("95338"), Arrays.asList(new ServiceCardField("trackingNumber", true,
                        1f, Arrays.asList("单号[：:](\\w{8,24})"))));
        final List<ParsedServiceCard> cards = ServiceSmsParser.parse(Arrays.asList(rule),
                "单号:SF123456789012", "95338", .5f);
        assertEquals(1, cards.size());
    }

    @Test
    public void requiredFieldMissingFallsBelowMinimumConfidence() {
        final ServiceCardRule rule = expressRule();
        final List<ParsedServiceCard> cards = ServiceSmsParser.parse(Arrays.asList(rule),
                "顺丰快递正在派送，请保持电话畅通", "95338", .5f);
        assertEquals(0, cards.size());
    }

    @Test
    public void malformedFieldPatternDoesNotBlockOtherFields() {
        final ServiceCardRule rule = new ServiceCardRule("EXPRESS_CARD", Arrays.asList("快递"),
                Arrays.asList(), Arrays.asList(
                        new ServiceCardField("bad", false, .1f, Arrays.asList("([")),
                        new ServiceCardField("trackingNumber", true, 1f,
                                Arrays.asList("运单号[：:]?(\\w{8,24})"))));
        final List<ParsedServiceCard> cards = ServiceSmsParser.parse(Arrays.asList(rule),
                "快递运单号SF123456789012", "", .5f);
        assertEquals(1, cards.size());
        assertEquals("SF123456789012", cards.get(0).fields.get("trackingNumber"));
    }

    @Test
    public void confidenceThresholdIsRespected() {
        final ServiceCardRule rule = new ServiceCardRule("EXPRESS_CARD", Arrays.asList("快递"),
                Arrays.asList(), Arrays.asList(
                        new ServiceCardField("trackingNumber", true, .6f,
                                Arrays.asList("运单号[：:]?(\w{8,24})")),
                        new ServiceCardField("pickupCode", true, .4f,
                                Arrays.asList("取件码[：:]?(\w{4,10})"))));
        final List<ParsedServiceCard> cards = ServiceSmsParser.parse(Arrays.asList(rule),
                "快递运单号SF123456789012", "95338", .9f);
        assertEquals(0, cards.size());
    }

    private static ServiceCardRule expressRule() {
        return new ServiceCardRule("EXPRESS_CARD", Arrays.asList("快递"),
                Arrays.asList("95338"), Arrays.asList(
                        new ServiceCardField("trackingNumber", true, .6f,
                                Arrays.asList("运单号[：:]?\\s*([A-Z0-9]{8,24})")),
                        new ServiceCardField("pickupCode", false, .2f,
                                Arrays.asList("取件码[：:]?\\s*([A-Z0-9]{4,10})"))));
    }
}
