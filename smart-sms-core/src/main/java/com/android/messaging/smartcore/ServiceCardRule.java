/* SPDX-License-Identifier: Apache-2.0 */
package com.android.messaging.smartcore;

import java.util.Collections;
import java.util.List;

/** A trigger and field extraction rule for one local service-SMS card type. */
public final class ServiceCardRule {
    public final String cardType;
    public final List<String> keywords;
    public final List<String> senderPrefixes;
    public final List<ServiceCardField> fields;

    public ServiceCardRule(final String cardType, final List<String> keywords,
            final List<String> senderPrefixes, final List<ServiceCardField> fields) {
        this.cardType = cardType;
        this.keywords = keywords == null ? Collections.emptyList() : keywords;
        this.senderPrefixes = senderPrefixes == null ? Collections.emptyList() : senderPrefixes;
        this.fields = fields == null ? Collections.emptyList() : fields;
    }
}
