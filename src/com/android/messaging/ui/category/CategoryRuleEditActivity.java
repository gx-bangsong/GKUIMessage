/*
 * Copyright (C) 2026
 * SPDX-License-Identifier: Apache-2.0
 */
package com.android.messaging.ui.category;

import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;

import com.android.messaging.R;
import com.android.messaging.category.CategoryMatchType;
import com.android.messaging.category.CategoryReclassificationService;
import com.android.messaging.category.SmsCategory;
import com.android.messaging.category.SmsCategoryRepository;
import com.android.messaging.category.SmsCategoryRule;
import com.android.messaging.category.SmsCategoryType;
import com.android.messaging.category.SmsClassifier;
import com.android.messaging.datamodel.MessagingContentProvider;
import com.android.messaging.ui.BugleActionBarActivity;
import com.android.messaging.util.ThreadUtil;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/** Creates a custom category or edits the rules, palette color, and icon of an existing one. */
public final class CategoryRuleEditActivity extends BugleActionBarActivity {
    public static final String EXTRA_CATEGORY_ID = "category_id";
    private static final int[] PALETTE = {
            0xff1565c0, 0xff6a1b9a, 0xff2e7d32, 0xffef6c00, 0xffc62828, 0xff00695c
    };
    private static final String[] PALETTE_NAMES = {"Blue", "Purple", "Green", "Orange", "Red", "Teal"};
    private static final String[] ICONS = {"all", "person", "key", "account_balance",
            "local_shipping", "campaign", "notifications"};

    private final ExecutorService mWorker = Executors.newSingleThreadExecutor(runnable -> {
        final Thread thread = new Thread(runnable, "CategoryRuleEditor");
        thread.setPriority(Thread.MIN_PRIORITY);
        return thread;
    });

    private TextInputLayout mNameLayout;
    private TextInputEditText mName;
    private MaterialAutoCompleteTextView mIcon;
    private MaterialButton mColor;
    private LinearLayout mRules;
    private TextInputEditText mTestSms;
    private TextView mTestResult;
    @Nullable private SmsCategory mCategory;
    private int mColorValue = PALETTE[0];

    @Override
    protected void onCreate(@Nullable final Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.category_rule_edit_activity);
        final ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
            actionBar.setTitle(R.string.category_rule_edit_title);
        }
        mNameLayout = findViewById(R.id.category_edit_name_layout);
        mName = findViewById(R.id.category_edit_name);
        mIcon = findViewById(R.id.category_edit_icon);
        mColor = findViewById(R.id.category_edit_color);
        mRules = findViewById(R.id.category_rules_container);
        mTestSms = findViewById(R.id.category_test_sms);
        mTestResult = findViewById(R.id.category_test_result);
        mIcon.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, ICONS));
        mColor.setOnClickListener(view -> chooseColor());
        findViewById(R.id.category_add_rule).setOnClickListener(view -> addRule(null));
        findViewById(R.id.category_test_rules).setOnClickListener(view -> testRules());
        findViewById(R.id.category_save).setOnClickListener(view -> save());
        findViewById(R.id.category_cancel).setOnClickListener(view -> finish());
        updateColorButton();

        final int categoryId = getIntent().getIntExtra(EXTRA_CATEGORY_ID, -1);
        if (categoryId > 0) loadCategory(categoryId);
        else {
            mIcon.setText("notifications", false);
            addRule(null);
        }
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull final MenuItem item) {
        if (item.getItemId() == android.R.id.home) { finish(); return true; }
        return super.onOptionsItemSelected(item);
    }

    @Override protected void onDestroy() { mWorker.shutdownNow(); super.onDestroy(); }

    private void loadCategory(final int categoryId) {
        mWorker.execute(() -> {
            final SmsCategory category = SmsCategoryRepository.getCategory(categoryId);
            final List<SmsCategoryRule> rules = SmsCategoryRepository.getRules(categoryId);
            ThreadUtil.getMainThreadHandler().post(() -> {
                if (isFinishing() || category == null) return;
                mCategory = category;
                mName.setText(category.name);
                mIcon.setText(category.icon, false);
                mColorValue = category.color;
                updateColorButton();
                final boolean isAll = category.type == SmsCategoryType.ALL;
                mName.setEnabled(!isAll);
                mIcon.setEnabled(!isAll);
                mColor.setEnabled(!isAll);
                findViewById(R.id.category_add_rule).setEnabled(!isAll);
                mRules.removeAllViews();
                for (final SmsCategoryRule rule : rules) addRule(rule);
                if (rules.isEmpty() && !isAll) addRule(null);
            });
        });
    }

    private void chooseColor() {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.category_choose_color)
                .setItems(PALETTE_NAMES, (dialog, which) -> {
                    mColorValue = PALETTE[which];
                    updateColorButton();
                }).show();
    }

    private void updateColorButton() {
        mColor.setBackgroundTintList(ColorStateList.valueOf(mColorValue));
        mColor.setText(PALETTE_NAMES[indexOfColor(mColorValue)]);
    }

    private int indexOfColor(final int color) {
        for (int i = 0; i < PALETTE.length; i++) if (PALETTE[i] == color) return i;
        return 0;
    }

    private void addRule(@Nullable final SmsCategoryRule existing) {
        final View row = LayoutInflater.from(this).inflate(R.layout.category_rule_row, mRules, false);
        final MaterialAutoCompleteTextView type = row.findViewById(R.id.category_rule_type);
        final TextInputEditText value = row.findViewById(R.id.category_rule_value);
        final TextInputEditText weight = row.findViewById(R.id.category_rule_weight);
        type.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_list_item_1,
                CategoryMatchType.values()));
        type.setText(existing == null ? CategoryMatchType.KEYWORD.name() : existing.matchType.name(),
                false);
        value.setText(existing == null ? "" : existing.value);
        weight.setText(String.valueOf(existing == null ? 10 : existing.weight));
        value.setTypeface(Typeface.MONOSPACE);
        row.findViewById(R.id.category_rule_delete).setOnClickListener(view -> mRules.removeView(row));
        mRules.addView(row);
    }

    @NonNull
    private List<SmsCategoryRule> collectRules(final boolean validate) {
        final ArrayList<SmsCategoryRule> rules = new ArrayList<>();
        for (int index = 0; index < mRules.getChildCount(); index++) {
            final View row = mRules.getChildAt(index);
            final MaterialAutoCompleteTextView type = row.findViewById(R.id.category_rule_type);
            final TextInputEditText valueInput = row.findViewById(R.id.category_rule_value);
            final TextInputEditText weightInput = row.findViewById(R.id.category_rule_weight);
            final String value = textOf(valueInput).trim();
            if (TextUtils.isEmpty(value)) continue;
            final CategoryMatchType matchType = CategoryMatchType.valueOf(type.getText().toString());
            if (validate && matchType == CategoryMatchType.REGEX) {
                try { Pattern.compile(value); }
                catch (final PatternSyntaxException exception) {
                    valueInput.setError(exception.getDescription());
                    throw exception;
                }
            }
            int weight = 0;
            try { weight = Integer.parseInt(textOf(weightInput)); }
            catch (final NumberFormatException exception) { weightInput.setError("0-100"); }
            rules.add(new SmsCategoryRule(0, mCategory == null ? 0 : mCategory.id, matchType,
                    value, Math.max(0, Math.min(100, weight)), true));
        }
        return rules;
    }

    private void testRules() {
        final String sample = textOf(mTestSms);
        final List<SmsCategoryRule> rules;
        try { rules = collectRules(true); }
        catch (final PatternSyntaxException exception) { return; }
        final int score = SmsClassifier.scoreRules(rules, sample, null);
        if (score > 0) {
            final String name = textOf(mName).trim();
            mTestResult.setText(getString(R.string.category_test_match, name, score));
        } else {
            mTestResult.setText(R.string.category_test_no_match);
        }
    }

    private void save() {
        final String name = textOf(mName).trim();
        if (TextUtils.isEmpty(name)) { mNameLayout.setError(getString(R.string.category_name_required)); return; }
        final List<SmsCategoryRule> rules;
        try { rules = collectRules(true); }
        catch (final PatternSyntaxException exception) { return; }
        final String icon = TextUtils.isEmpty(mIcon.getText()) ? "notifications" : mIcon.getText().toString();
        mWorker.execute(() -> {
            int id;
            if (mCategory == null) {
                id = SmsCategoryRepository.insertCustomCategory(name, mColorValue, icon);
            } else {
                id = mCategory.id;
                SmsCategoryRepository.updateCategory(new SmsCategory(id, name, mCategory.type,
                        mColorValue, icon, mCategory.enabled, mCategory.sortOrder, mCategory.isBuiltIn));
            }
            SmsCategoryRepository.replaceRules(id, rules);
            MessagingContentProvider.notifyConversationListChanged();
            CategoryReclassificationService.enqueue(getApplicationContext());
            ThreadUtil.getMainThreadHandler().post(() -> { setResult(RESULT_OK); finish(); });
        });
    }

    @NonNull private static String textOf(@NonNull final TextInputEditText input) {
        return input.getText() == null ? "" : input.getText().toString();
    }
}
