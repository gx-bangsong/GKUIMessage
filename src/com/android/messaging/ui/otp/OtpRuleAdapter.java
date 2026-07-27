/*
 * Copyright (C) 2026
 * SPDX-License-Identifier: Apache-2.0
 */
package com.android.messaging.ui.otp;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.android.messaging.R;
import com.android.messaging.otp.OtpRule;
import com.google.android.material.materialswitch.MaterialSwitch;

import java.util.ArrayList;
import java.util.List;

/** RecyclerView adapter for priority-ordered OTP rules. */
final class OtpRuleAdapter extends RecyclerView.Adapter<OtpRuleAdapter.RuleViewHolder> {
    interface Listener {
        void onRuleClicked(@NonNull OtpRule rule);
        void onRuleEnabledChanged(@NonNull OtpRule rule, boolean enabled);
    }

    private final ArrayList<OtpRule> mRules = new ArrayList<>();
    private final Listener mListener;

    OtpRuleAdapter(final Listener listener) {
        mListener = listener;
        setHasStableIds(true);
    }

    void replaceAll(@NonNull final List<OtpRule> rules) {
        mRules.clear();
        mRules.addAll(rules);
        notifyDataSetChanged();
    }

    @NonNull
    OtpRule getRule(final int position) {
        return mRules.get(position);
    }

    void move(final int fromPosition, final int toPosition) {
        final OtpRule rule = mRules.remove(fromPosition);
        mRules.add(toPosition, rule);
        notifyItemMoved(fromPosition, toPosition);
    }

    @NonNull
    List<OtpRule> snapshot() {
        return new ArrayList<>(mRules);
    }

    @Override
    public long getItemId(final int position) {
        return mRules.get(position).id;
    }

    @NonNull
    @Override
    public RuleViewHolder onCreateViewHolder(@NonNull final ViewGroup parent, final int viewType) {
        return new RuleViewHolder(LayoutInflater.from(parent.getContext()).inflate(
                R.layout.otp_rule_list_item, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull final RuleViewHolder holder, final int position) {
        final OtpRule rule = mRules.get(position);
        holder.name.setText(rule.name);
        holder.pattern.setText(rule.pattern);
        holder.builtIn.setVisibility(rule.isBuiltIn ? View.VISIBLE : View.GONE);
        holder.enabled.setOnCheckedChangeListener(null);
        holder.enabled.setChecked(rule.enabled);
        holder.enabled.setOnCheckedChangeListener((button, checked) -> {
            final int adapterPosition = holder.getAdapterPosition();
            if (adapterPosition != RecyclerView.NO_POSITION) {
                mListener.onRuleEnabledChanged(mRules.get(adapterPosition), checked);
            }
        });
        holder.itemView.setOnClickListener(view -> mListener.onRuleClicked(rule));
    }

    @Override
    public int getItemCount() {
        return mRules.size();
    }

    static final class RuleViewHolder extends RecyclerView.ViewHolder {
        final TextView name;
        final TextView pattern;
        final TextView builtIn;
        final MaterialSwitch enabled;

        RuleViewHolder(@NonNull final View itemView) {
            super(itemView);
            name = itemView.findViewById(R.id.otp_rule_name);
            pattern = itemView.findViewById(R.id.otp_rule_pattern);
            builtIn = itemView.findViewById(R.id.otp_rule_builtin);
            enabled = itemView.findViewById(R.id.otp_rule_enabled);
        }
    }
}
