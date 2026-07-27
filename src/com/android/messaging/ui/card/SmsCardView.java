/* Copyright (C) 2026 SPDX-License-Identifier: Apache-2.0 */
package com.android.messaging.ui.card;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.net.Uri;
import android.provider.CalendarContract;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.android.messaging.R;
import com.android.messaging.card.CardType;
import com.android.messaging.card.SmsCard;
import com.android.messaging.card.SmsCardRepository;
import com.android.messaging.util.ThreadUtil;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.chip.Chip;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** MD3 visual renderer for a persisted local SMS card placed above the message bubble. */
public final class SmsCardView extends MaterialCardView {
    private static final ExecutorService LOADER = Executors.newSingleThreadExecutor(runnable -> {
        final Thread thread = new Thread(runnable, "SmsCardViewLoader");
        thread.setPriority(Thread.MIN_PRIORITY);
        return thread;
    });

    private final LinearLayout mRoot;
    private final LinearLayout mHeader;
    private final TextView mTitle;
    private final Chip mStatus;
    private final MaterialButton mExpand;
    private final LinearLayout mDetails;
    private final LinearLayout mActions;
    private long mBoundMessageId = -1;
    @Nullable private SmsCard mCard;

    public SmsCardView(final Context context, final android.util.AttributeSet attrs) {
        super(context, attrs);
        setRadius(dp(16));
        setCardElevation(dp(1));
        setUseCompatPadding(true);
        setCardBackgroundColor(resolveSurfaceColor());
        mRoot = vertical();
        mRoot.setPadding(dp(12), dp(8), dp(12), dp(8));
        addView(mRoot, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        mHeader = new LinearLayout(context);
        mHeader.setGravity(Gravity.CENTER_VERTICAL);
        mHeader.setOrientation(LinearLayout.HORIZONTAL);
        mTitle = new TextView(context);
        mTitle.setTextSize(16);
        mTitle.setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD);
        mHeader.addView(mTitle, new LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1));
        mStatus = new Chip(context, null, com.google.android.material.R.attr.chipFilterStyle);
        mStatus.setCheckable(false);
        mStatus.setClickable(false);
        mHeader.addView(mStatus);
        mExpand = new MaterialButton(context, null,
                com.google.android.material.R.attr.materialButtonTextButtonStyle);
        mExpand.setText("⌄");
        mExpand.setMinWidth(0);
        mExpand.setMinHeight(0);
        mHeader.addView(mExpand);
        mRoot.addView(mHeader);

        mDetails = vertical();
        mDetails.setPadding(0, dp(6), 0, 0);
        mRoot.addView(mDetails);
        mActions = new LinearLayout(context);
        mActions.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        mActions.setOrientation(LinearLayout.HORIZONTAL);
        mRoot.addView(mActions);
        setVisibility(GONE);
    }

    /** Starts an off-main-thread lookup. A recycled view ignores stale async results. */
    public void bind(final long messageId) {
        mBoundMessageId = messageId;
        mCard = null;
        setVisibility(GONE);
        LOADER.execute(() -> {
            final SmsCard card = SmsCardRepository.getBestForMessage(messageId);
            ThreadUtil.getMainThreadHandler().post(() -> {
                if (mBoundMessageId != messageId) return;
                mCard = card;
                render();
            });
        });
    }

    private void render() {
        if (mCard == null || mCard.confidence < .5f) {
            setVisibility(GONE);
            requestLayout();
            return;
        }
        final JSONObject data;
        try { data = new JSONObject(mCard.cardData); }
        catch (final JSONException exception) { setVisibility(GONE); return; }
        setVisibility(VISIBLE);
        mTitle.setText(titleFor(mCard.cardType, data));
        final String status = statusFor(mCard.cardType, data);
        mStatus.setText(status);
        mStatus.setChipBackgroundColor(ColorStateList.valueOf(colorFor(mCard.cardType)));
        mStatus.setTextColor(0xffffffff);
        final boolean expanded = mCard.isExpanded || mCard.confidence >= .8f;
        mDetails.removeAllViews();
        addFields(data, expanded);
        mDetails.setVisibility(expanded ? VISIBLE : GONE);
        mExpand.setText(expanded ? "⌃" : "⌄");
        mExpand.setOnClickListener(view -> {
            if (mCard == null) return;
            final boolean next = !mDetails.isShown();
            mDetails.setVisibility(next ? VISIBLE : GONE);
            mExpand.setText(next ? "⌃" : "⌄");
            final long id = mCard.id;
            LOADER.execute(() -> SmsCardRepository.setExpanded(id, next));
            requestLayout();
        });
        buildActions(data);
        requestLayout();
    }

    private void addFields(@NonNull final JSONObject data, final boolean expanded) {
        if (!expanded) return;
        final Iterator<String> keys = data.keys();
        while (keys.hasNext()) {
            final String key = keys.next();
            if ("cardType".equals(key) || "status".equals(key) || "sourceApp".equals(key)) continue;
            final String value = data.optString(key);
            if (TextUtils.isEmpty(value)) continue;
            final LinearLayout row = new LinearLayout(getContext());
            row.setGravity(Gravity.CENTER_VERTICAL);
            final TextView label = new TextView(getContext());
            label.setText(labelFor(key));
            label.setTextColor(0xff5f6368);
            row.addView(label, new LinearLayout.LayoutParams(dp(92), LayoutParams.WRAP_CONTENT));
            final TextView text = new TextView(getContext());
            text.setText(value);
            text.setTextSize(14);
            row.addView(text, new LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1));
            if (isCoreField(key)) {
                final MaterialButton copy = actionButton("复制");
                copy.setOnClickListener(view -> copy(value));
                row.addView(copy);
            }
            mDetails.addView(row);
        }
        if (mCard != null && mCard.cardType == CardType.OTP_CARD) {
            final TextView source = new TextView(getContext());
            source.setText(data.optString("sourceApp"));
            source.setTextColor(0xff5f6368);
            mDetails.addView(source);
        }
    }

    private void buildActions(@NonNull final JSONObject data) {
        mActions.removeAllViews();
        final List<MaterialButton> buttons = new ArrayList<>();
        switch (mCard.cardType) {
            case OTP_CARD:
                buttons.add(button("复制验证码", view -> copy(data.optString("otp"))));
                break;
            case BANK_CARD:
            case RECHARGE_CARD:
                buttons.add(button("复制金额", view -> copy(data.optString("amount"))));
                buttons.add(button("复制明细", view -> copy(data.toString())));
                break;
            case EXPRESS_CARD:
                buttons.add(button("复制单号", view -> copy(data.optString("trackingNumber"))));
                buttons.add(button("查询物流", view -> browser("https://www.baidu.com/s?wd="
                        + Uri.encode(data.optString("trackingNumber") + " 物流"))));
                break;
            case TRAIN_CARD:
                buttons.add(button("添加日历", view -> addCalendar(data)));
                buttons.add(button("复制票号", view -> copy(data.optString("trainNumber"))));
                buttons.add(button("查看车次", view -> browser("https://www.12306.cn")));
                break;
            case FLIGHT_CARD:
                buttons.add(button("添加日历", view -> addCalendar(data)));
                buttons.add(button("复制航班号", view -> copy(data.optString("flightNumber"))));
                buttons.add(button("查询状态", view -> launchFlightStatus(data.optString("flightNumber"))));
                break;
            case APPOINTMENT_CARD:
                buttons.add(button("添加日历", view -> addCalendar(data)));
                if (!TextUtils.isEmpty(data.optString("location"))) {
                    buttons.add(button("复制地址", view -> copy(data.optString("location"))));
                }
                if (!TextUtils.isEmpty(data.optString("contactPhone"))) {
                    buttons.add(button("拨打电话", view -> call(data.optString("contactPhone"))));
                }
                break;
            default:
                break;
        }
        for (int i = 0; i < Math.min(3, buttons.size()); i++) mActions.addView(buttons.get(i));
    }

    private MaterialButton button(final String text, final View.OnClickListener listener) {
        final MaterialButton button = actionButton(text);
        button.setOnClickListener(listener);
        return button;
    }

    private MaterialButton actionButton(final String text) {
        final MaterialButton button = new MaterialButton(getContext(), null,
                com.google.android.material.R.attr.materialButtonTextButtonStyle);
        button.setText(text);
        button.setMinHeight(0);
        button.setMinWidth(0);
        return button;
    }

    private void copy(@Nullable final String text) {
        if (TextUtils.isEmpty(text)) return;
        final ClipboardManager clipboard = (ClipboardManager) getContext().getSystemService(
                Context.CLIPBOARD_SERVICE);
        if (clipboard != null) clipboard.setPrimaryClip(ClipData.newPlainText("SMS card", text));
    }

    private void browser(@NonNull final String url) {
        try { getContext().startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url))); }
        catch (final RuntimeException ignored) { }
    }

    private void launchFlightStatus(@NonNull final String flightNumber) {
        final Intent umetrip = getContext().getPackageManager().getLaunchIntentForPackage(
                "com.umetrip.android.msky.app");
        if (umetrip != null) {
            umetrip.putExtra(Intent.EXTRA_TEXT, flightNumber);
            try { getContext().startActivity(umetrip); return; } catch (final RuntimeException ignored) { }
        }
        browser("https://www.flightradar24.com/data/flights/" + Uri.encode(flightNumber));
    }

    private void call(@NonNull final String phone) {
        try { getContext().startActivity(new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + phone))); }
        catch (final RuntimeException ignored) { }
    }

    private void addCalendar(@NonNull final JSONObject data) {
        final Intent intent = new Intent(Intent.ACTION_INSERT).setData(CalendarContract.Events.CONTENT_URI);
        intent.putExtra(CalendarContract.Events.TITLE, titleFor(mCard.cardType, data));
        intent.putExtra(CalendarContract.Events.DESCRIPTION, data.toString());
        try { getContext().startActivity(intent); } catch (final RuntimeException ignored) { }
    }

    @NonNull private String titleFor(final CardType type, final JSONObject data) {
        switch (type) {
            case OTP_CARD: return "验证码 " + data.optString("otp");
            case BANK_CARD: return data.optString("transactionType", "交易") + "通知";
            case EXPRESS_CARD: return data.optString("company", "快递服务");
            case TRAIN_CARD: return data.optString("trainNumber", "出行提醒");
            case FLIGHT_CARD: return data.optString("flightNumber", "航班提醒");
            case APPOINTMENT_CARD: return data.optString("subject", "预约提醒");
            case RECHARGE_CARD: return data.optString("serviceType", "缴费通知");
            default: return "服务通知";
        }
    }

    @NonNull private String statusFor(final CardType type, final JSONObject data) {
        return type == CardType.EXPRESS_CARD ? data.optString("status", "IN_TRANSIT")
                : mCard.confidence >= .8f ? "已识别" : "点击展开";
    }

    private int colorFor(final CardType type) {
        switch (type) {
            case EXPRESS_CARD: return 0xffef6c00;
            case BANK_CARD: return 0xff2e7d32;
            case OTP_CARD: return 0xff6a1b9a;
            default: return 0xff1565c0;
        }
    }

    @NonNull private String labelFor(@NonNull final String key) {
        return key.replaceAll("([A-Z])", " $1").trim();
    }

    private boolean isCoreField(@NonNull final String key) {
        return "otp".equals(key) || "amount".equals(key) || "trackingNumber".equals(key)
                || "trainNumber".equals(key) || "flightNumber".equals(key);
    }

    private LinearLayout vertical() {
        final LinearLayout layout = new LinearLayout(getContext());
        layout.setOrientation(LinearLayout.VERTICAL);
        return layout;
    }

    private int dp(final int value) { return (int) (value * getResources().getDisplayMetrics().density); }
    private int resolveSurfaceColor() { return 0xfff6f3f8; }
}
