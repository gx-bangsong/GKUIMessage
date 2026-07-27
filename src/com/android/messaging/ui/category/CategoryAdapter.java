/*
 * Copyright (C) 2026
 * SPDX-License-Identifier: Apache-2.0
 */
package com.android.messaging.ui.category;

import android.content.res.ColorStateList;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.android.messaging.R;
import com.android.messaging.category.SmsCategory;
import com.android.messaging.category.SmsCategoryType;
import com.google.android.material.materialswitch.MaterialSwitch;

import java.util.ArrayList;
import java.util.List;

/** Category settings list adapter with stable ids for ItemTouchHelper reorder animations. */
final class CategoryAdapter extends RecyclerView.Adapter<CategoryAdapter.Holder> {
    interface Listener {
        void onCategoryClicked(@NonNull SmsCategory category);
        void onCategoryEnabledChanged(@NonNull SmsCategory category, boolean enabled);
    }

    private final ArrayList<SmsCategory> mCategories = new ArrayList<>();
    private final Listener mListener;

    CategoryAdapter(@NonNull final Listener listener) {
        mListener = listener;
        setHasStableIds(true);
    }

    void replaceAll(@NonNull final List<SmsCategory> categories) {
        mCategories.clear();
        mCategories.addAll(categories);
        notifyDataSetChanged();
    }

    @NonNull SmsCategory get(final int position) { return mCategories.get(position); }
    void move(final int from, final int to) {
        final SmsCategory category = mCategories.remove(from);
        mCategories.add(to, category);
        notifyItemMoved(from, to);
    }
    @NonNull List<SmsCategory> snapshot() { return new ArrayList<>(mCategories); }

    @Override public long getItemId(final int position) { return mCategories.get(position).id; }
    @Override public int getItemCount() { return mCategories.size(); }

    @NonNull @Override
    public Holder onCreateViewHolder(@NonNull final ViewGroup parent, final int viewType) {
        return new Holder(LayoutInflater.from(parent.getContext()).inflate(
                R.layout.category_list_item, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull final Holder holder, final int position) {
        final SmsCategory category = mCategories.get(position);
        holder.color.setBackgroundTintList(ColorStateList.valueOf(category.color));
        holder.name.setText(category.name);
        holder.description.setText(category.type == SmsCategoryType.ALL
                ? R.string.category_all_always_shown
                : category.isBuiltIn ? R.string.category_builtin : R.string.category_custom);
        holder.enabled.setOnCheckedChangeListener(null);
        holder.enabled.setChecked(category.enabled);
        holder.enabled.setEnabled(category.type != SmsCategoryType.ALL);
        holder.enabled.setOnCheckedChangeListener((button, checked) -> {
            final int current = holder.getAdapterPosition();
            if (current != RecyclerView.NO_POSITION) {
                mListener.onCategoryEnabledChanged(mCategories.get(current), checked);
            }
        });
        holder.itemView.setOnClickListener(view -> mListener.onCategoryClicked(category));
    }

    static final class Holder extends RecyclerView.ViewHolder {
        final View color;
        final TextView name;
        final TextView description;
        final MaterialSwitch enabled;
        Holder(@NonNull final View view) {
            super(view);
            color = view.findViewById(R.id.category_color);
            name = view.findViewById(R.id.category_name);
            description = view.findViewById(R.id.category_description);
            enabled = view.findViewById(R.id.category_enabled);
        }
    }
}
