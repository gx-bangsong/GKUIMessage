package com.android.messaging.smartcore;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.List;
import org.junit.Test;

public class ServiceSmsParserTest {
    @Test
    public void extractsExpressTrackingAndPickupCode() {
        final ServiceCardRule rule = new ServiceCardRule("EXPRESS_CARD", Arrays.asList("快递"),
                Arrays.asList("95338"), Arrays.asList(
                        new ServiceCardField("trackingNumber", true, .6f,
                                Arrays.asList("运单号[：:]?\\s*([A-Z0-9]{8,24})")),
                        new ServiceCardField("pickupCode", false, .2f,
                                Arrays.asList("取件码[：:]?\\s*([A-Z0-9]{4,10})"))));
        final List<ParsedServiceCard> cards = ServiceSmsParser.parse(Arrays.asList(rule),
                "顺丰快递：运单号SF123456789012，取件码A1234", "95338", .5f);
        assertEquals(1, cards.size());
        assertEquals("SF123456789012", cards.get(0).fields.get("trackingNumber"));
        assertEquals("A1234", cards.get(0).fields.get("pickupCode"));
        assertTrue(cards.get(0).confidence >= .5f);
    }
}
