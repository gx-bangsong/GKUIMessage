/*
 * Copyright (C) 2026
 * SPDX-License-Identifier: Apache-2.0
 */
package com.android.messaging.ui.otp;

import android.graphics.Typeface;
import android.os.Bundle;
import android.text.Editable;
import android.text.SpannableString;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.style.BackgroundColorSpan;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.core.content.ContextCompat;

import com.android.messaging.R;
import com.android.messaging.otp.OtpDetector;
import com.android.messaging.otp.OtpMatch;
import com.android.messaging.otp.OtpRule;
import com.android.messaging.otp.OtpRuleRepository;
import com.android.messaging.ui.BugleActionBarActivity;
import com.android.messaging.util.ThreadUtil;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/** Creates or edits one OTP rule and previews a bounded match against test SMS text. */
public final class OtpRuleEditActivity extends BugleActionBarActivity {
    public static final String EXTRA_RULE_ID = "otp_rule_id";

    private final ExecutorService mWorker = Executors.newSingleThreadExecutor(runnable -> {
        final Thread thread = new Thread(runnable, "OtpRuleEditor");
        thread.setPriority(Thread.MIN_PRIORITY);
        return thread;
    });

    private TextInputLayout mNameLayout;
    private TextInputLayout mPatternLayout;
    private TextInputEditText mName;
    private TextInputEditText mPattern;
    private TextInputEditText mTestMessage;
    private TextView mPreviewState;
    private TextView mPreviewMessage;
    @Nullable private OtpRule mExistingRule;
    private int mPreviewRequest;

    @Override
    protected void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.otp_rule_edit_activity);
        final ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
            actionBar.setTitle(R.string.otp_rule_edit_title);
        }

        mNameLayout = findViewById(R.id.otp_rule_name_layout);
        mPatternLayout = findViewById(R.id.otp_rule_pattern_layout);
        mName = findViewById(R.id.otp_rule_name_input);
        mPattern = findViewById(R.id.otp_rule_pattern_input);
        mTestMessage = findViewById(R.id.otp_rule_test_message_input);
        mPreviewState = findViewById(R.id.otp_rule_preview_state);
        mPreviewMessage = findViewById(R.id.otp_rule_preview_message);
        mPattern.setTypeface(Typeface.MONOSPACE);

        final TextWatcher previewWatcher = new TextWatcher() {
            @Override public void beforeTextChanged(final CharSequence s, final int start,
                    final int count, final int after) {}
            @Override public void onTextChanged(final CharSequence s, final int start,
                    final int before, final int count) { requestPreview(); }
            @Override public void afterTextChanged(final Editable s) {}
        };
        mPattern.addTextChangedListener(previewWatcher);
        mTestMessage.addTextChangedListener(previewWatcher);

        final MaterialButton save = findViewById(R.id.otp_rule_save);
        final MaterialButton cancel = findViewById(R.id.otp_rule_cancel);
        save.setOnClickListener(view -> save());
        cancel.setOnClickListener(view -> finish());

        final int id = getIntent().getIntExtra(EXTRA_RULE_ID, -1);
        if (id > 0) {
            loadRule(id);
        } else {
            requestPreview();
        }
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull final MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onDestroy() {
        mWorker.shutdownNow();
        super.onDestroy();
    }

    private void loadRule(final int id) {
        mWorker.execute(() -> {
            final OtpRule rule = OtpRuleRepository.getRule(id);
            ThreadUtil.getMainThreadHandler().post(() -> {
                if (isFinishing() || rule == null) {
                    return;
                }
                mExistingRule = rule;
                mName.setText(rule.name);
                mPattern.setText(rule.pattern);
                requestPreview();
            });
        });
    }

    private void requestPreview() {
        final String expression = textOf(mPattern);
        final String sample = textOf(mTestMessage);
        final int request = ++mPreviewRequest;
        if (TextUtils.isEmpty(expression) || TextUtils.isEmpty(sample)) {
            mPreviewState.setText(R.string.otp_preview_enter_test_message);
            mPreviewMessage.setText("");
            return;
        }
        mPreviewState.setText(R.string.otp_preview_checking);
        mWorker.execute(() -> {
            final OtpMatch match = OtpDetector.previewWithinTimeout(expression, sample);
            ThreadUtil.getMainThreadHandler().post(() -> {
                if (isFinishing() || request != mPreviewRequest) {
                    return;
                }
                showPreview(sample, match);
            });
        });
    }

    private void showPreview(@NonNull final String sample, @Nullable final OtpMatch match) {
        if (match == null) {
            mPreviewState.setText(R.string.otp_preview_no_match);
            mPreviewMessage.setText(sample);
            return;
        }
        mPreviewState.setText(getString(R.string.otp_preview_matched, match.value));
        final SpannableString highlighted = new SpannableString(sample);
        final int accent = ContextCompat.getColor(this, R.color.lineage_accent);
        highlighted.setSpan(new BackgroundColorSpan((accent & 0x00ffffff) | 0x44000000),
                match.start, match.end, SpannableString.SPAN_EXCLUSIVE_EXCLUSIVE);
        mPreviewMessage.setText(highlighted);
    }

    private void save() {
        final String name = textOf(mName).trim();
        final String pattern = textOf(mPattern);
        mNameLayout.setError(null);
        mPatternLayout.setError(null);
        if (TextUtils.isEmpty(name)) {
            mNameLayout.setError(getString(R.string.otp_rule_name_required));
            return;
        }
        if (TextUtils.isEmpty(pattern)) {
            mPatternLayout.setError(getString(R.string.otp_rule_pattern_required));
            return;
        }
        try {
            Pattern.compile(pattern);
        } catch (final PatternSyntaxException exception) {
            mPatternLayout.setError(exception.getDescription());
            return;
        }
        mWorker.execute(() -> {
            if (mExistingRule == null) {
                OtpRuleRepository.insert(name, pattern, true);
            } else {
                OtpRuleRepository.update(new OtpRule(mExistingRule.id, name, pattern,
                        mExistingRule.priority, mExistingRule.enabled, mExistingRule.isBuiltIn));
            }
            ThreadUtil.getMainThreadHandler().post(() -> {
                setResult(RESULT_OK);
                finish();
            });
        });
    }

    @NonNull
    private static String textOf(@NonNull final TextInputEditText text) {
        return text.getText() == null ? "" : text.getText().toString();
    }
}
