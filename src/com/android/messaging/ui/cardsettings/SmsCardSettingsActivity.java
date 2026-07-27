/* Copyright (C) 2026 SPDX-License-Identifier: Apache-2.0 */
package com.android.messaging.ui.cardsettings;

import android.os.Bundle;
import androidx.appcompat.app.ActionBar;
import androidx.fragment.app.FragmentTransaction;
import com.android.messaging.R;
import com.android.messaging.ui.BugleActionBarActivity;

public final class SmsCardSettingsActivity extends BugleActionBarActivity {
 @Override protected void onCreate(final Bundle state) {
  super.onCreate(state); final ActionBar bar=getSupportActionBar();
  if (bar != null) { bar.setDisplayHomeAsUpEnabled(true); bar.setTitle(R.string.sms_card_settings_title); }
  if (state == null) { final FragmentTransaction tx=getSupportFragmentManager().beginTransaction();
   tx.replace(android.R.id.content,new SmsCardSettingsFragment()); tx.commit(); }
 }
 @Override public boolean onSupportNavigateUp() { finish(); return true; }
}
