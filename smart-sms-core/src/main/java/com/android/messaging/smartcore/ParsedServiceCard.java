/* SPDX-License-Identifier: Apache-2.0 */
package com.android.messaging.smartcore;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Extracted service-card data and normalized confidence. */
public final class ParsedServiceCard {
    public final String cardType;
    public final Map<String, String> fields;
    public final float confidence;

    public ParsedServiceCard(final String cardType, final Map<String, String> fields,
            final float confidence) {
        this.cardType = cardType;
        this.fields = Collections.unmodifiableMap(new LinkedHashMap<>(fields));
        this.confidence = confidence;
    }
}
