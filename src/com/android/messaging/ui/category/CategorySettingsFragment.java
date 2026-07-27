/*
 * Copyright (C) 2026
 * SPDX-License-Identifier: Apache-2.0
 */
package com.android.messaging.ui.category;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.android.messaging.R;
import com.android.messaging.category.CategoryReclassificationService;
import com.android.messaging.category.SmsCategory;
import com.android.messaging.category.SmsCategoryRepository;
import com.android.messaging.datamodel.MessagingContentProvider;
import com.android.messaging.util.ThreadUtil;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Manages local category order, availability, custom categories, and default reset. */
public final class CategorySettingsFragment extends Fragment implements CategoryAdapter.Listener {
    private final ExecutorService mWorker = Executors.newSingleThreadExecutor(runnable -> {
        final Thread thread = new Thread(runnable, "CategorySettings");
        thread.setPriority(Thread.MIN_PRIORITY);
        return thread;
    });
    private CategoryAdapter mAdapter;
    private Context mApplicationContext;

    @Nullable @Override
    public View onCreateView(@NonNull final LayoutInflater inflater,
            @Nullable final ViewGroup container, @Nullable final Bundle state) {
        return inflater.inflate(R.layout.category_settings_fragment, container, false);
    }

    @Override
    public void onViewCreated(@NonNull final View view, @Nullable final Bundle state) {
        super.onViewCreated(view, state);
        mApplicationContext = requireContext().getApplicationContext();
        final RecyclerView list = view.findViewById(R.id.category_settings_list);
        list.setLayoutManager(new LinearLayoutManager(requireContext()));
        mAdapter = new CategoryAdapter(this);
        list.setAdapter(mAdapter);
        new ItemTouchHelper(new TouchCallback()).attachToRecyclerView(list);
        final ExtendedFloatingActionButton add = view.findViewById(R.id.category_add_button);
        add.setOnClickListener(clicked -> startActivity(new Intent(requireContext(),
                CategoryRuleEditActivity.class)));
        view.findViewById(R.id.category_reset_button).setOnClickListener(clicked -> confirmReset());
    }

    @Override public void onResume() { super.onResume(); loadCategories(); }
    @Override public void onDestroy() { mWorker.shutdownNow(); super.onDestroy(); }

    @Override
    public void onCategoryClicked(@NonNull final SmsCategory category) {
        final Intent intent = new Intent(requireContext(), CategoryRuleEditActivity.class);
        intent.putExtra(CategoryRuleEditActivity.EXTRA_CATEGORY_ID, category.id);
        startActivity(intent);
    }

    @Override
    public void onCategoryEnabledChanged(@NonNull final SmsCategory category, final boolean enabled) {
        mWorker.execute(() -> {
            SmsCategoryRepository.setCategoryEnabled(category.id, enabled);
            reclassify();
        });
    }

    private void loadCategories() {
        if (mAdapter == null) return;
        mWorker.execute(() -> {
            final List<SmsCategory> categories = SmsCategoryRepository.getCategories(true);
            ThreadUtil.getMainThreadHandler().post(() -> {
                if (isAdded() && mAdapter != null) mAdapter.replaceAll(categories);
            });
        });
    }

    private void confirmReset() {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.category_reset_title)
                .setMessage(R.string.category_reset_message)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.category_reset_action, (dialog, which) ->
                        mWorker.execute(() -> {
                            SmsCategoryRepository.resetToDefaults();
                            reclassify();
                            ThreadUtil.getMainThreadHandler().post(this::loadCategories);
                        }))
                .show();
    }

    private void reclassify() {
        MessagingContentProvider.notifyConversationListChanged();
        if (mApplicationContext != null) {
            CategoryReclassificationService.enqueue(mApplicationContext);
        }
    }

    private final class TouchCallback extends ItemTouchHelper.SimpleCallback {
        TouchCallback() { super(ItemTouchHelper.UP | ItemTouchHelper.DOWN, ItemTouchHelper.LEFT); }
        @Override public boolean onMove(@NonNull final RecyclerView list,
                @NonNull final RecyclerView.ViewHolder source,
                @NonNull final RecyclerView.ViewHolder target) {
            final int from = source.getAdapterPosition();
            final int to = target.getAdapterPosition();
            if (from == RecyclerView.NO_POSITION || to == RecyclerView.NO_POSITION
                    || mAdapter.get(from).id == SmsCategoryRepository.ALL_CATEGORY_ID
                    || mAdapter.get(to).id == SmsCategoryRepository.ALL_CATEGORY_ID) return false;
            mAdapter.move(from, to);
            final List<SmsCategory> order = mAdapter.snapshot();
            mWorker.execute(() -> {
                SmsCategoryRepository.updateSortOrders(order);
                reclassify();
            });
            return true;
        }
        @Override public void onSwiped(@NonNull final RecyclerView.ViewHolder holder,
                final int direction) {
            final int position = holder.getAdapterPosition();
            if (position == RecyclerView.NO_POSITION) return;
            final SmsCategory category = mAdapter.get(position);
            if (category.isBuiltIn) {
                mAdapter.notifyItemChanged(position);
                return;
            }
            mWorker.execute(() -> {
                SmsCategoryRepository.deleteCustomCategory(category.id);
                reclassify();
                ThreadUtil.getMainThreadHandler().post(CategorySettingsFragment.this::loadCategories);
            });
        }
    }
}
