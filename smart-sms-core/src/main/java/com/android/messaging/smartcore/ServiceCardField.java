/* SPDX-License-Identifier: Apache-2.0 */
package com.android.messaging.smartcore;

import java.util.Collections;
import java.util.List;

/** One field extraction definition for a service-SMS card rule. */
public final class ServiceCardField {
    public final String name;
    public final boolean required;
    public final float weight;
    public final List<String> patterns;

    public ServiceCardField(final String name, final boolean required, final float weight,
            final List<String> patterns) {
        this.name = name;
        this.required = required;
        this.weight = weight;
        this.patterns = patterns == null ? Collections.emptyList() : patterns;
    }
}
