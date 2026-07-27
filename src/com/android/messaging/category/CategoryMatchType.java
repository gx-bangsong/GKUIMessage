/*
 * Copyright (C) 2026
 * SPDX-License-Identifier: Apache-2.0
 */
package com.android.messaging.category;

/** How a category rule is evaluated against a received SMS. */
public enum CategoryMatchType {
    KEYWORD,
    REGEX,
    SENDER
}
