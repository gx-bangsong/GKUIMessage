/* SPDX-License-Identifier: Apache-2.0 */
package com.android.messaging.smartcore;

/** Winner from weighted category evaluation. */
public final class Classification {
    public final String categoryId;
    public final int score;

    public Classification(final String categoryId, final int score) {
        this.categoryId = categoryId;
        this.score = score;
    }
}
