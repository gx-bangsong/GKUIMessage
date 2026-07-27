/* SPDX-License-Identifier: Apache-2.0 */
package com.android.messaging.smartcore;

/** Result of one OTP regular-expression match. */
public final class OtpMatch {
    public final int ruleId;
    public final String value;
    public final int start;
    public final int end;

    public OtpMatch(final int ruleId, final String value, final int start, final int end) {
        this.ruleId = ruleId;
        this.value = value;
        this.start = start;
        this.end = end;
    }
}
