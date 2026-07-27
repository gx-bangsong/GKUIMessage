/* SPDX-License-Identifier: Apache-2.0 */
package com.android.messaging.smartcore;

import java.util.Collections;
import java.util.List;

/** Platform-neutral category definition used by the weighted conversation classifier. */
public final class CategoryDefinition {
    public final String id;
    public final boolean enabled;
    public final boolean all;
    public final boolean personal;
    public final int sortOrder;
    public final List<WeightedRule> rules;

    public CategoryDefinition(final String id, final boolean enabled, final boolean all,
            final boolean personal, final int sortOrder, final List<WeightedRule> rules) {
        this.id = id;
        this.enabled = enabled;
        this.all = all;
        this.personal = personal;
        this.sortOrder = sortOrder;
        this.rules = rules == null ? Collections.emptyList() : rules;
    }
}
