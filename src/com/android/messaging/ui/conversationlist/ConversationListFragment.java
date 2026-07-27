/*
 * Copyright (C) 2015 The Android Open Source Project
 * Copyright (C) 2024-2025 The LineageOS Project
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

import android.app.Activity;
import android.content.Context;
import android.database.Cursor;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Bundle;
import android.os.Parcelable;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewGroup.MarginLayoutParams;
import android.view.ViewPropertyAnimator;
import android.view.accessibility.AccessibilityManager;
import android.widget.AbsListView;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.core.view.ViewGroupCompat;
import androidx.fragment.app.Fragment;
import androidx.loader.app.LoaderManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.android.messaging.R;
import com.android.messaging.category.SmsCategory;
import com.android.messaging.category.SmsCategoryRepository;
import com.android.messaging.datamodel.DataModel;
import com.android.messaging.datamodel.binding.Binding;
import com.android.messaging.datamodel.binding.BindingBase;
import com.android.messaging.datamodel.data.ConversationListData;
import com.android.messaging.datamodel.data.ConversationListData.ConversationListDataListener;
import com.android.messaging.datamodel.data.ConversationListItemData;
import com.android.messaging.ui.ListEmptyView;
import com.android.messaging.ui.SnackBarInteraction;
import com.android.messaging.ui.UIIntents;
import com.android.messaging.util.AccessibilityUtil;
import com.android.messaging.util.ImeUtil;
import com.android.messaging.util.LogUtil;
import com.android.messaging.util.ThreadUtil;
import com.android.messaging.util.UiUtils;
import com.google.android.material.chip.Chip;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Shows a list of conversations.
 */
public class ConversationListFragment extends Fragment implements ConversationListDataListener,
        ConversationListItemView.HostInterface {
    private static final String BUNDLE_ARCHIVED_MODE = "archived_mode";
    private static final String BUNDLE_FORWARD_MESSAGE_MODE = "forward_message_mode";

    private MenuItem mShowBlockedMenuItem;
    private boolean mArchiveMode;
    private boolean mBlockedAvailable;
    private boolean mForwardMessageMode;

    public interface ConversationListFragmentHost {
        void onConversationClick(final ConversationListData listData,
                                        final ConversationListItemData conversationListItemData,
                                        final boolean isLongClick,
                                        final ConversationListItemView conversationView);
        void onCreateConversationClick();
        boolean isConversationSelected(final String conversationId);
        boolean isSwipeAnimatable();
        boolean isSelectionMode();
        boolean hasWindowFocus();
    }

    private ConversationListFragmentHost mHost;
    private RecyclerView mRecyclerView;
    private ExtendedFloatingActionButton mStartNewConversationButton;
    private ListEmptyView mEmptyListMessageView;
    private ConversationListAdapter mAdapter;
    private View mCategoryStrip;
    private HorizontalScrollView mCategoryScrollView;
    private LinearLayout mCategoryChipContainer;
    private final ArrayList<SmsCategory> mCategories = new ArrayList<>();
    private final ExecutorService mCategoryExecutor = Executors.newSingleThreadExecutor(runnable -> {
        final Thread thread = new Thread(runnable, "SmsCategoryChips");
        thread.setPriority(Thread.MIN_PRIORITY);
        return thread;
    });

    // Saved Instance State Data - only for temporal data which is nice to maintain but not
    // critical for correctness.
    private static final String SAVED_INSTANCE_STATE_LIST_VIEW_STATE_KEY =
            "conversationListViewState";
    private Parcelable mListState;

    final Binding<ConversationListData> mListBinding = BindingBase.createBinding(this);

    public static ConversationListFragment createArchivedConversationListFragment() {
        return createConversationListFragment(BUNDLE_ARCHIVED_MODE);
    }

    public static ConversationListFragment createForwardMessageConversationListFragment() {
        return createConversationListFragment(BUNDLE_FORWARD_MESSAGE_MODE);
    }

    public static ConversationListFragment createConversationListFragment(String modeKeyName) {
        final ConversationListFragment fragment = new ConversationListFragment();
        if (modeKeyName != null) {
            final Bundle bundle = new Bundle();
            bundle.putBoolean(modeKeyName, true);
            fragment.setArguments(bundle);
        }
        return fragment;
    }

    /**
     * {@inheritDoc} from Fragment
     */
    @Override
    public void onCreate(final Bundle bundle) {
        super.onCreate(bundle);
        mListBinding.getData().init(LoaderManager.getInstance(this), mListBinding);
        mAdapter = new ConversationListAdapter(this);
    }

    @Override
    public void onResume() {
        super.onResume();

        mHost = (ConversationListFragmentHost) getActivity();
        setScrolledToNewestConversationIfNeeded();

        updateUi();
        loadCategories();
    }

    public void setScrolledToNewestConversationIfNeeded() {
        if (!mArchiveMode
                && !mForwardMessageMode
                && isScrolledToFirstConversation()
                && mHost.hasWindowFocus()) {
            mListBinding.getData().setScrolledToNewestConversation(true);
        }
    }

    private boolean isScrolledToFirstConversation() {
        int firstItemPosition = ((LinearLayoutManager) mRecyclerView.getLayoutManager())
                .findFirstCompletelyVisibleItemPosition();
        return firstItemPosition == 0;
    }

    /**
     * {@inheritDoc} from Fragment
     */
    @Override
    public void onDestroy() {
        super.onDestroy();
        mListBinding.unbind();
        mCategoryExecutor.shutdownNow();
        mHost = null;
    }

    /**
     * {@inheritDoc} from Fragment
     */
    @Override
    public View onCreateView(final LayoutInflater inflater, final ViewGroup container,
            final Bundle savedInstanceState) {
        final ViewGroup rootView = (ViewGroup) inflater.inflate(R.layout.conversation_list_fragment,
                container, false);
        mRecyclerView = rootView.findViewById(android.R.id.list);
        mEmptyListMessageView = rootView.findViewById(R.id.no_conversations_view);
        mCategoryStrip = rootView.findViewById(R.id.category_chip_strip);
        mCategoryScrollView = rootView.findViewById(R.id.category_chip_scroll);
        mCategoryChipContainer = rootView.findViewById(R.id.category_chip_container);
        mEmptyListMessageView.setImageHint(R.drawable.ic_oobe_conv_list);
        // The default behavior for default layout param generation by LinearLayoutManager is to
        // provide width and height of WRAP_CONTENT, but this is not desirable for
        // ConversationListFragment; the view in each row should be a width of MATCH_PARENT so that
        // the entire row is tappable.
        final Activity activity = getActivity();
        final LinearLayoutManager manager = new LinearLayoutManager(activity) {
            @Override
            public RecyclerView.LayoutParams generateDefaultLayoutParams() {
                return new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT);
            }
        };
        mRecyclerView.setLayoutManager(manager);
        mRecyclerView.setHasFixedSize(true);
        mRecyclerView.setAdapter(mAdapter);
        mAdapter.registerAdapterDataObserver(new RecyclerView.AdapterDataObserver() {
            @Override public void onChanged() { updateEmptyListUi(mAdapter.getItemCount() == 0); }
            @Override public void onItemRangeInserted(final int start, final int count) {
                updateEmptyListUi(mAdapter.getItemCount() == 0);
            }
            @Override public void onItemRangeRemoved(final int start, final int count) {
                updateEmptyListUi(mAdapter.getItemCount() == 0);
            }
        });
        mRecyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            int mCurrentState = AbsListView.OnScrollListener.SCROLL_STATE_IDLE;

            @Override
            public void onScrolled(@NonNull final RecyclerView recyclerView, final int dx,
                                   final int dy) {
                if (mCurrentState == AbsListView.OnScrollListener.SCROLL_STATE_TOUCH_SCROLL
                        || mCurrentState == AbsListView.OnScrollListener.SCROLL_STATE_FLING) {
                    ImeUtil.get().hideImeKeyboard(getActivity(), mRecyclerView);
                }

                if (isScrolledToFirstConversation()) {
                    setScrolledToNewestConversationIfNeeded();
                } else {
                    mListBinding.getData().setScrolledToNewestConversation(false);
                }
            }

            @Override
            public void onScrollStateChanged(@NonNull final RecyclerView recyclerView,
                                             final int newState) {
                mCurrentState = newState;
            }
        });
        mRecyclerView.addOnItemTouchListener(new ConversationListSwipeHelper(mRecyclerView));

        if (savedInstanceState != null) {
            mListState = savedInstanceState.getParcelable(SAVED_INSTANCE_STATE_LIST_VIEW_STATE_KEY,
                    Parcelable.class);
        }

        mStartNewConversationButton = rootView.findViewById(R.id.start_new_conversation_button);
        if (mArchiveMode || mForwardMessageMode) {
            mStartNewConversationButton.setVisibility(View.GONE);
        } else {
            mStartNewConversationButton.setVisibility(View.VISIBLE);
            mStartNewConversationButton.setOnClickListener(clickView ->
                    mHost.onCreateConversationClick());
        }

        if (mArchiveMode || mForwardMessageMode) {
            mCategoryStrip.setVisibility(View.GONE);
        }

        // The root view has a non-null background, which by default is deemed by the framework
        // to be a "transition group," where all child views are animated together during an
        // activity transition. However, we want each individual items in the recycler view to
        // show explode animation themselves, so we explicitly tag the root view to be a non-group.
        ViewGroupCompat.setTransitionGroup(rootView, false);

        setHasOptionsMenu(true);
        return rootView;
    }

    @Override
    public void onAttach(@NonNull final Context context) {
        super.onAttach(context);
        LogUtil.v(LogUtil.BUGLE_TAG, "Attaching List");
        final Bundle arguments = getArguments();
        if (arguments != null) {
            mArchiveMode = arguments.getBoolean(BUNDLE_ARCHIVED_MODE, false);
            mForwardMessageMode = arguments.getBoolean(BUNDLE_FORWARD_MESSAGE_MODE, false);
        }
        mListBinding.bind(DataModel.get().createConversationListData(context, this, mArchiveMode));
    }


    @Override
    public void onSaveInstanceState(@NonNull final Bundle outState) {
        super.onSaveInstanceState(outState);
        if (mListState != null) {
            outState.putParcelable(SAVED_INSTANCE_STATE_LIST_VIEW_STATE_KEY, mListState);
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        mListState = mRecyclerView.getLayoutManager().onSaveInstanceState();
        mListBinding.getData().setScrolledToNewestConversation(false);
    }

    @Override
    public void onConversationListCursorUpdated(final ConversationListData data,
            final Cursor cursor) {
        mListBinding.ensureBound(data);
        final boolean hadNoRows = mAdapter.getItemCount() == 0;
        mAdapter.submitCursor(cursor);
        updateEmptyListUi(cursor == null || cursor.getCount() == 0);
        buildCategoryChips();
        if (mListState != null && cursor != null && hadNoRows) {
            mRecyclerView.post(() -> mRecyclerView.getLayoutManager().onRestoreInstanceState(
                    mListState));
        }
    }

    private void loadCategories() {
        if (mArchiveMode || mForwardMessageMode || !isAdded()) {
            return;
        }
        mCategoryExecutor.execute(() -> {
            final List<SmsCategory> categories = SmsCategoryRepository.getCategories(false);
            ThreadUtil.getMainThreadHandler().post(() -> {
                if (!isAdded()) {
                    return;
                }
                mCategories.clear();
                mCategories.addAll(categories);
                buildCategoryChips();
            });
        });
    }

    private void buildCategoryChips() {
        if (mCategoryChipContainer == null || mArchiveMode || mForwardMessageMode) {
            return;
        }
        mCategoryChipContainer.removeAllViews();
        if (mCategories.size() <= 1) {
            mCategoryStrip.setVisibility(View.GONE);
            return;
        }
        mCategoryStrip.setVisibility(View.VISIBLE);
        final Map<Integer, Integer> unreadCounts = new HashMap<>();
        int allUnread = 0;
        for (final ConversationListItemData item : mAdapter.getAllConversations()) {
            if (!item.getIsRead()) {
                allUnread++;
                unreadCounts.put(item.getCategoryId(), unreadCounts.getOrDefault(item.getCategoryId(), 0)
                        + 1);
            }
        }
        boolean selectedStillAvailable = false;
        for (final SmsCategory category : mCategories) {
            if (category.id == mAdapter.getCategoryFilter()) {
                selectedStillAvailable = true;
            }
        }
        if (!selectedStillAvailable) {
            mAdapter.setCategoryFilter(SmsCategoryRepository.ALL_CATEGORY_ID);
        }
        for (final SmsCategory category : mCategories) {
            final int unread = category.id == SmsCategoryRepository.ALL_CATEGORY_ID ? allUnread
                    : unreadCounts.getOrDefault(category.id, 0);
            final Chip chip = createCategoryChip(category, unread);
            mCategoryChipContainer.addView(chip);
        }
    }

    private Chip createCategoryChip(final SmsCategory category, final int unread) {
        final Chip chip = new Chip(requireContext(), null,
                com.google.android.material.R.attr.chipFilterStyle);
        final int horizontalMargin = (int) (8 * getResources().getDisplayMetrics().density);
        final LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMargins(horizontalMargin, 0, 0, 0);
        chip.setLayoutParams(params);
        chip.setCheckable(true);
        chip.setChecked(category.id == mAdapter.getCategoryFilter());
        chip.setChipIconResource(iconForCategory(category.icon));
        chip.setChipIconVisible(true);
        chip.setText(unread > 0 ? getString(R.string.category_chip_unread_count, category.name, unread)
                : category.name);
        final int selectedColor = category.color;
        chip.setChipBackgroundColor(new ColorStateList(new int[][] {
                new int[] {android.R.attr.state_checked}, new int[] {}},
                new int[] {selectedColor, Color.TRANSPARENT}));
        chip.setChipStrokeColor(ColorStateList.valueOf(selectedColor));
        chip.setChipStrokeWidth(1f);
        chip.setOnClickListener(view -> {
            mAdapter.setCategoryFilter(category.id);
            buildCategoryChips();
            mCategoryScrollView.post(() -> mCategoryScrollView.smoothScrollTo(
                    Math.max(0, chip.getLeft() - horizontalMargin), 0));
        });
        return chip;
    }

    private int iconForCategory(final String icon) {
        switch (icon) {
            case "person":
                return R.drawable.ic_person_light;
            case "key":
            case "account_balance":
            case "local_shipping":
            case "campaign":
            case "notifications":
                return R.drawable.ic_info_light;
            case "all":
            default:
                return R.drawable.ic_message;
        }
    }

    @Override
    public void setBlockedParticipantsAvailable(final boolean blockedAvailable) {
        mBlockedAvailable = blockedAvailable;
        if (mShowBlockedMenuItem != null) {
            mShowBlockedMenuItem.setVisible(blockedAvailable);
        }
    }

    public void updateUi() {
        mAdapter.notifyItemRangeChanged(0, mAdapter.getItemCount());
    }

    @Override
    public void onPrepareOptionsMenu(@NonNull final Menu menu) {
        super.onPrepareOptionsMenu(menu);
        final MenuItem startNewConversationMenuItem =
                menu.findItem(R.id.action_start_new_conversation);
        if (startNewConversationMenuItem != null) {
            // It is recommended for the Floating Action button functionality to be duplicated as a
            // menu
            AccessibilityManager accessibilityManager = (AccessibilityManager)
                    requireActivity().getSystemService(Context.ACCESSIBILITY_SERVICE);
            startNewConversationMenuItem.setVisible(accessibilityManager
                    .isTouchExplorationEnabled());
        }

        final MenuItem archive = menu.findItem(R.id.action_show_archived);
        if (archive != null) {
            archive.setVisible(true);
        }
    }

    @Override
    public void onCreateOptionsMenu(@NonNull final Menu menu,
                                    @NonNull final MenuInflater inflater) {
        if (!isAdded()) {
            // Guard against being called before we're added to the activity
            return;
        }

        mShowBlockedMenuItem = menu.findItem(R.id.action_show_blocked_contacts);
        if (mShowBlockedMenuItem != null) {
            mShowBlockedMenuItem.setVisible(mBlockedAvailable);
        }
    }

    /**
     * {@inheritDoc} from ConversationListItemView.HostInterface
     */
    @Override
    public void onConversationClicked(final ConversationListItemData conversationListItemData,
            final boolean isLongClick, final ConversationListItemView conversationView) {
        final ConversationListData listData = mListBinding.getData();
        mHost.onConversationClick(listData, conversationListItemData, isLongClick,
                conversationView);
    }

    /**
     * {@inheritDoc} from ConversationListItemView.HostInterface
     */
    @Override
    public boolean isConversationSelected(final String conversationId) {
        return mHost.isConversationSelected(conversationId);
    }

    @Override
    public boolean isSwipeAnimatable() {
        return mHost.isSwipeAnimatable();
    }

    // Show and hide empty list UI as needed with appropriate text based on view specifics
    private void updateEmptyListUi(final boolean isEmpty) {
        if (isEmpty) {
            int emptyListText;
            if (!mListBinding.getData().getHasFirstSyncCompleted()) {
                emptyListText = R.string.conversation_list_first_sync_text;
            } else if (mArchiveMode) {
                emptyListText = R.string.archived_conversation_list_empty_text;
            } else {
                emptyListText = R.string.conversation_list_empty_text;
            }
            mEmptyListMessageView.setTextHint(emptyListText);
            mEmptyListMessageView.setVisibility(View.VISIBLE);
            mEmptyListMessageView.setIsImageVisible(true);
            mEmptyListMessageView.setIsVerticallyCentered(true);
        } else {
            mEmptyListMessageView.setVisibility(View.GONE);
        }
    }

    @Override
    public List<SnackBarInteraction> getSnackBarInteractions() {
        final List<SnackBarInteraction> interactions = new ArrayList<>(1);
        final SnackBarInteraction fabInteraction =
                new SnackBarInteraction.BasicSnackBarInteraction(mStartNewConversationButton);
        interactions.add(fabInteraction);
        return interactions;
    }

    private ViewPropertyAnimator getNormalizedFabAnimator() {
        return mStartNewConversationButton.animate()
                .setInterpolator(UiUtils.DEFAULT_INTERPOLATOR)
                .setDuration(getActivity().getResources().getInteger(
                        R.integer.fab_animation_duration_ms));
    }

    public void dismissFab() {
        // To prevent clicking while animating.
        mStartNewConversationButton.setEnabled(false);
        final MarginLayoutParams lp =
                (MarginLayoutParams) mStartNewConversationButton.getLayoutParams();
        final float fabWidthWithLeftRightMargin = mStartNewConversationButton.getWidth()
                + lp.leftMargin + lp.rightMargin;
        final int direction = AccessibilityUtil.isLayoutRtl(mStartNewConversationButton) ? -1 : 1;
        getNormalizedFabAnimator().translationX(direction * fabWidthWithLeftRightMargin);
    }

    public void showFab() {
        getNormalizedFabAnimator().translationX(0).withEndAction(() -> {
            // Re-enable clicks after the animation.
            mStartNewConversationButton.setEnabled(true);
        });
    }

    @Override
    public void startFullScreenPhotoViewer(
            final Uri initialPhoto, final Rect initialPhotoBounds, final Uri photosUri) {
        UIIntents.get().launchFullScreenPhotoViewer(
                getActivity(), initialPhoto, initialPhotoBounds, photosUri);
    }

    @Override
    public void startFullScreenVideoViewer(final Uri videoUri) {
        UIIntents.get().launchFullScreenVideoViewer(getActivity(), videoUri);
    }

    @Override
    public boolean isSelectionMode() {
        return mHost != null && mHost.isSelectionMode();
    }
}
