/*
 * Copyright (C) 2026
 * SPDX-License-Identifier: Apache-2.0
 */
package com.android.messaging.otp;

import android.content.ClipData;
import android.content.ClipDescription;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Handler;
import android.os.PersistableBundle;
import android.text.TextUtils;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.messaging.R;
import com.android.messaging.util.BuglePrefs;
import com.android.messaging.util.LogUtil;
import com.android.messaging.util.ThreadUtil;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Coordinates receive-time detection, clipboard ownership, feedback, and 60-second cleanup. */
public final class OtpAutoCopyManager {
    public static final String PREF_AUTO_COPY_ENABLED = "otp_auto_copy_enabled";
    public static final String PREF_SHOW_TOAST = "otp_show_copy_toast";
    private static final String CLIP_LABEL = "Messaging OTP";
    private static final long CLEAR_CLIP_DELAY_MILLIS = 60_000L;

    private static final ExecutorService IO_EXECUTOR = Executors.newSingleThreadExecutor(runnable -> {
        final Thread thread = new Thread(runnable, "OtpAutoCopyIo");
        thread.setPriority(Thread.MIN_PRIORITY);
        return thread;
    });
    private static final Handler MAIN_HANDLER = ThreadUtil.getMainThreadHandler();

    private static ClipboardManager sClipboard;
    private static Runnable sClearClipboardRunnable;
    @Nullable private static String sOwnedOtp;
    private static boolean sListenerInstalled;

    private OtpAutoCopyManager() {}

    /**
     * Handles one newly delivered SMS only. This method never scans the telephony provider or
     * historical messages. Completion is always posted to the main thread.
     */
    public static void processIncomingSms(@NonNull final Context context, @Nullable final String body,
            @Nullable final Runnable completion) {
        final Context applicationContext = context.getApplicationContext();
        if (TextUtils.isEmpty(body) || !applicationContext.getSharedPreferences(
                BuglePrefs.SHARED_PREFERENCES_NAME, Context.MODE_PRIVATE).getBoolean(
                PREF_AUTO_COPY_ENABLED, true)) {
            finish(completion);
            return;
        }
        IO_EXECUTOR.execute(() -> {
            try {
                final List<OtpRule> rules = OtpRuleRepository.getEnabledRules();
                final OtpMatch match = OtpDetector.detectWithinTimeout(body, rules);
                if (match != null) {
                    // Module 3 only consumes this evidence to render an OTP card. It never runs
                    // OTP matching itself and it never performs a duplicate clipboard copy.
                    OtpDetectionCache.record(body, match.value);
                    MAIN_HANDLER.post(() -> copyAndNotify(applicationContext, match.value));
                }
            } catch (final RuntimeException exception) {
                // The receiving broadcast and normal SMS persistence must remain reliable.
                LogUtil.w(LogUtil.BUGLE_TAG, "Unable to process incoming OTP", exception);
            } finally {
                finish(completion);
            }
        });
    }

    private static void finish(@Nullable final Runnable completion) {
        if (completion != null) {
            MAIN_HANDLER.post(completion);
        }
    }

    private static void copyAndNotify(final Context context, @NonNull final String otp) {
        final ClipboardManager clipboard = (ClipboardManager) context.getSystemService(
                Context.CLIPBOARD_SERVICE);
        if (clipboard == null) {
            return;
        }
        sClipboard = clipboard;
        installClipboardListener();

        final ClipData clip = ClipData.newPlainText(CLIP_LABEL, otp);
        final PersistableBundle extras = new PersistableBundle();
        extras.putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true);
        clip.getDescription().setExtras(extras);
        sOwnedOtp = otp;
        clipboard.setPrimaryClip(clip);
        scheduleClipboardClear();

        if (context.getSharedPreferences(BuglePrefs.SHARED_PREFERENCES_NAME,
                Context.MODE_PRIVATE).getBoolean(PREF_SHOW_TOAST, true)) {
            // Toast.LENGTH_SHORT is approximately two seconds on the platform.
            Toast.makeText(context, context.getString(R.string.otp_copied_to_clipboard, otp),
                    Toast.LENGTH_SHORT).show();
        }
    }

    private static void installClipboardListener() {
        if (sListenerInstalled || sClipboard == null) {
            return;
        }
        sClipboard.addPrimaryClipChangedListener(() -> {
            if (!isOurCurrentClip()) {
                sOwnedOtp = null;
                if (sClearClipboardRunnable != null) {
                    MAIN_HANDLER.removeCallbacks(sClearClipboardRunnable);
                }
            }
        });
        sListenerInstalled = true;
    }

    private static void scheduleClipboardClear() {
        if (sClearClipboardRunnable != null) {
            MAIN_HANDLER.removeCallbacks(sClearClipboardRunnable);
        }
        final String copiedOtp = sOwnedOtp;
        sClearClipboardRunnable = () -> {
            if (copiedOtp != null && copiedOtp.equals(sOwnedOtp) && isOurCurrentClip()) {
                sClipboard.clearPrimaryClip();
            }
            sOwnedOtp = null;
        };
        MAIN_HANDLER.postDelayed(sClearClipboardRunnable, CLEAR_CLIP_DELAY_MILLIS);
    }

    private static boolean isOurCurrentClip() {
        if (sClipboard == null || sOwnedOtp == null || !sClipboard.hasPrimaryClip()) {
            return false;
        }
        final ClipDescription description = sClipboard.getPrimaryClipDescription();
        final ClipData clip = sClipboard.getPrimaryClip();
        if (description == null || clip == null || clip.getItemCount() != 1
                || !TextUtils.equals(CLIP_LABEL, description.getLabel())) {
            return false;
        }
        final CharSequence text = clip.getItemAt(0).getText();
        return TextUtils.equals(sOwnedOtp, text);
    }
}
