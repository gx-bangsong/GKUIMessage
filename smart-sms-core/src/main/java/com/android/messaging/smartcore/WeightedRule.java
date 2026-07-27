/* SPDX-License-Identifier: Apache-2.0 */
package com.android.messaging.smartcore;

/** A weighted text or sender matcher. */
public final class WeightedRule {
    public final RuleMatchType type;
    public final String value;
    public final int weight;
    public final boolean enabled;

    public WeightedRule(final RuleMatchType type, final String value, final int weight,
            final boolean enabled) {
        this.type = type;
        this.value = value;
        this.weight = weight;
        this.enabled = enabled;
    }
}
