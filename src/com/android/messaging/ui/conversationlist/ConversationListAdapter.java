/*
 * Copyright (C) 2015 The Android Open Source Project
 * Copyright (C) 2024-2026 The LineageOS Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.messaging.ui.conversationlist;

import android.content.Context;
import android.database.Cursor;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.android.messaging.R;
import com.android.messaging.category.SmsCategoryRepository;
import com.android.messaging.datamodel.data.ConversationListItemData;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Snapshot-backed adapter. Category selection submits a filtered list through DiffUtil so that
 * the conversation list changes smoothly instead of invalidating every row.
 */
public final class ConversationListAdapter extends
        ListAdapter<ConversationListItemData, ConversationListAdapter.ConversationListViewHolder> {
    private static final DiffUtil.ItemCallback<ConversationListItemData> DIFF_CALLBACK =
            new DiffUtil.ItemCallback<>() {
                @Override
                public boolean areItemsTheSame(@NonNull final ConversationListItemData oldItem,
                        @NonNull final ConversationListItemData newItem) {
                    return oldItem.getConversationId().equals(newItem.getConversationId());
                }

                @Override
                public boolean areContentsTheSame(@NonNull final ConversationListItemData oldItem,
                        @NonNull final ConversationListItemData newItem) {
                    return oldItem.getTimestamp() == newItem.getTimestamp()
                            && oldItem.getIsRead() == newItem.getIsRead()
                            && oldItem.getCategoryId() == newItem.getCategoryId()
                            && oldItem.getCategoryColor() == newItem.getCategoryColor()
                            && same(oldItem.getCategoryName(), newItem.getCategoryName())
                            && same(oldItem.getSnippetText(), newItem.getSnippetText())
                            && same(oldItem.getName(), newItem.getName());
                }
            };

    private final ConversationListItemView.HostInterface mHostInterface;
    private final ArrayList<ConversationListItemData> mAllConversations = new ArrayList<>();
    private int mSelectedCategoryId = SmsCategoryRepository.ALL_CATEGORY_ID;

    public ConversationListAdapter(final ConversationListItemView.HostInterface hostInterface) {
        super(DIFF_CALLBACK);
        mHostInterface = hostInterface;
        setHasStableIds(true);
    }

    /** Copies the Loader-owned cursor into stable rows before asynchronous DiffUtil processing. */
    public void submitCursor(final Cursor cursor) {
        mAllConversations.clear();
        if (cursor != null) {
            final int originalPosition = cursor.getPosition();
            while (cursor.moveToNext()) {
                final ConversationListItemData item = new ConversationListItemData();
                item.bind(cursor);
                mAllConversations.add(item);
            }
            cursor.moveToPosition(originalPosition);
        }
        submitFilteredList();
    }

    public void setCategoryFilter(final int categoryId) {
        if (mSelectedCategoryId == categoryId) {
            return;
        }
        mSelectedCategoryId = categoryId;
        submitFilteredList();
    }

    public int getCategoryFilter() {
        return mSelectedCategoryId;
    }

    @NonNull
    public List<ConversationListItemData> getAllConversations() {
        return Collections.unmodifiableList(new ArrayList<>(mAllConversations));
    }

    private void submitFilteredList() {
        final ArrayList<ConversationListItemData> filtered = new ArrayList<>();
        for (final ConversationListItemData item : mAllConversations) {
            if (mSelectedCategoryId == SmsCategoryRepository.ALL_CATEGORY_ID
                    || item.getCategoryId() == mSelectedCategoryId) {
                filtered.add(item);
            }
        }
        submitList(filtered);
    }

    @Override
    public long getItemId(final int position) {
        try {
            return Long.parseLong(getItem(position).getConversationId());
        } catch (final NumberFormatException exception) {
            return RecyclerView.NO_ID;
        }
    }

    @NonNull
    @Override
    public ConversationListViewHolder onCreateViewHolder(@NonNull final ViewGroup parent,
            final int viewType) {
        final ConversationListItemView itemView = (ConversationListItemView)
                LayoutInflater.from(parent.getContext()).inflate(R.layout.conversation_list_item_view,
                        parent, false);
        return new ConversationListViewHolder(itemView);
    }

    @Override
    public void onBindViewHolder(@NonNull final ConversationListViewHolder holder,
            final int position) {
        holder.mView.bind(getItem(position), mHostInterface,
                mSelectedCategoryId == SmsCategoryRepository.ALL_CATEGORY_ID);
    }

    private static boolean same(final Object first, final Object second) {
        return first == second || (first != null && first.equals(second));
    }

    /** ViewHolder that holds a ConversationListItemView. */
    public static final class ConversationListViewHolder extends RecyclerView.ViewHolder {
        final ConversationListItemView mView;

        ConversationListViewHolder(@NonNull final ConversationListItemView itemView) {
            super(itemView);
            mView = itemView;
        }
    }
}
