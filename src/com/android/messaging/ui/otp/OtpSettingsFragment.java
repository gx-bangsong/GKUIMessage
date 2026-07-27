/*
 * Copyright (C) 2026
 * SPDX-License-Identifier: Apache-2.0
 */
package com.android.messaging.ui.otp;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.android.messaging.R;
import com.android.messaging.otp.OtpAutoCopyManager;
import com.android.messaging.otp.OtpRule;
import com.android.messaging.otp.OtpRuleRepository;
import com.android.messaging.util.BuglePrefs;
import com.android.messaging.util.ThreadUtil;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.materialswitch.MaterialSwitch;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * MD3 settings screen for OTP copying. It is launched from the application's Preference screen;
 * switches use the same Bugle preferences file and rules use the local otp_rules table.
 */
public final class OtpSettingsFragment extends Fragment implements OtpRuleAdapter.Listener {
    private final ExecutorService mDatabaseExecutor = Executors.newSingleThreadExecutor(runnable -> {
        final Thread thread = new Thread(runnable, "OtpRulesSettings");
        thread.setPriority(Thread.MIN_PRIORITY);
        return thread;
    });

    private OtpRuleAdapter mAdapter;
    private SharedPreferences mPreferences;

    @Nullable
    @Override
    public View onCreateView(@NonNull final LayoutInflater inflater,
            @Nullable final ViewGroup container, @Nullable final Bundle savedInstanceState) {
        return inflater.inflate(R.layout.otp_settings_fragment, container, false);
    }

    @Override
    public void onViewCreated(@NonNull final View view, @Nullable final Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        mPreferences = requireContext().getSharedPreferences(BuglePrefs.SHARED_PREFERENCES_NAME,
                Context.MODE_PRIVATE);

        final MaterialSwitch autoCopy = view.findViewById(R.id.otp_auto_copy_switch);
        final MaterialSwitch showToast = view.findViewById(R.id.otp_show_toast_switch);
        autoCopy.setChecked(mPreferences.getBoolean(OtpAutoCopyManager.PREF_AUTO_COPY_ENABLED,
                true));
        showToast.setChecked(mPreferences.getBoolean(OtpAutoCopyManager.PREF_SHOW_TOAST, true));
        autoCopy.setOnCheckedChangeListener((button, checked) -> mPreferences.edit().putBoolean(
                OtpAutoCopyManager.PREF_AUTO_COPY_ENABLED, checked).apply());
        showToast.setOnCheckedChangeListener((button, checked) -> mPreferences.edit().putBoolean(
                OtpAutoCopyManager.PREF_SHOW_TOAST, checked).apply());

        final RecyclerView rules = view.findViewById(R.id.otp_rules_list);
        rules.setLayoutManager(new LinearLayoutManager(requireContext()));
        mAdapter = new OtpRuleAdapter(this);
        rules.setAdapter(mAdapter);
        new ItemTouchHelper(new RuleTouchCallback()).attachToRecyclerView(rules);

        final FloatingActionButton addRule = view.findViewById(R.id.otp_add_rule);
        addRule.setOnClickListener(clicked -> startActivity(new Intent(requireContext(),
                OtpRuleEditActivity.class)));
    }

    @Override
    public void onResume() {
        super.onResume();
        loadRules();
    }

    @Override
    public void onDestroy() {
        mDatabaseExecutor.shutdownNow();
        super.onDestroy();
    }

    @Override
    public void onRuleClicked(@NonNull final OtpRule rule) {
        final Intent intent = new Intent(requireContext(), OtpRuleEditActivity.class);
        intent.putExtra(OtpRuleEditActivity.EXTRA_RULE_ID, rule.id);
        startActivity(intent);
    }

    @Override
    public void onRuleEnabledChanged(@NonNull final OtpRule rule, final boolean enabled) {
        mDatabaseExecutor.execute(() -> OtpRuleRepository.setEnabled(rule.id, enabled));
    }

    private void loadRules() {
        if (mAdapter == null) {
            return;
        }
        mDatabaseExecutor.execute(() -> {
            final List<OtpRule> loadedRules = OtpRuleRepository.getAllRules();
            ThreadUtil.getMainThreadHandler().post(() -> {
                if (isAdded() && mAdapter != null) {
                    mAdapter.replaceAll(loadedRules);
                }
            });
        });
    }

    private final class RuleTouchCallback extends ItemTouchHelper.SimpleCallback {
        RuleTouchCallback() {
            super(ItemTouchHelper.UP | ItemTouchHelper.DOWN, ItemTouchHelper.LEFT);
        }

        @Override
        public boolean onMove(@NonNull final RecyclerView recyclerView,
                @NonNull final RecyclerView.ViewHolder source,
                @NonNull final RecyclerView.ViewHolder target) {
            final int from = source.getAdapterPosition();
            final int to = target.getAdapterPosition();
            if (from == RecyclerView.NO_POSITION || to == RecyclerView.NO_POSITION) {
                return false;
            }
            mAdapter.move(from, to);
            final List<OtpRule> orderedRules = mAdapter.snapshot();
            mDatabaseExecutor.execute(() -> OtpRuleRepository.updatePriorities(orderedRules));
            return true;
        }

        @Override
        public void onSwiped(@NonNull final RecyclerView.ViewHolder viewHolder,
                final int direction) {
            final int position = viewHolder.getAdapterPosition();
            if (position == RecyclerView.NO_POSITION) {
                return;
            }
            final OtpRule rule = mAdapter.getRule(position);
            if (rule.isBuiltIn) {
                mAdapter.notifyItemChanged(position);
                Toast.makeText(requireContext(), R.string.otp_builtin_rule_not_deletable,
                        Toast.LENGTH_SHORT).show();
                return;
            }
            mDatabaseExecutor.execute(() -> {
                OtpRuleRepository.deleteCustomRule(rule.id);
                ThreadUtil.getMainThreadHandler().post(OtpSettingsFragment.this::loadRules);
            });
        }
    }
}
