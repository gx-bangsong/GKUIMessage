/*
 * Copyright (C) 2026
 * SPDX-License-Identifier: Apache-2.0
 */
package com.android.messaging.ui.category;

import android.os.Bundle;

import androidx.appcompat.app.ActionBar;
import androidx.fragment.app.FragmentTransaction;

import com.android.messaging.R;
import com.android.messaging.ui.BugleActionBarActivity;

/** Settings entry point for SMS category ordering and enablement. */
public final class CategorySettingsActivity extends BugleActionBarActivity {
    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        final ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
            actionBar.setTitle(R.string.category_settings_title);
        }
        if (savedInstanceState == null) {
            final FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
            transaction.replace(android.R.id.content, new CategorySettingsFragment());
            transaction.commit();
        }
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }
}
