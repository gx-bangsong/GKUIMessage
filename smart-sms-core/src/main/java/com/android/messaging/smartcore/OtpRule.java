/* SPDX-License-Identifier: Apache-2.0 */
package com.android.messaging.smartcore;

/** Platform-independent OTP rule for the shared recognizer. */
public final class OtpRule {
    public final int id;
    public final String pattern;
    public final int priority;
    public final boolean enabled;

    public OtpRule(final int id, final String pattern, final int priority, final boolean enabled) {
        this.id = id;
        this.pattern = pattern;
        this.priority = priority;
        this.enabled = enabled;
    }
}
