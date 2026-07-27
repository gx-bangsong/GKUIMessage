/*
 * Copyright (C) 2026
 * SPDX-License-Identifier: Apache-2.0
 */
package com.android.messaging.otp;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.messaging.util.LogUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/** Performs priority-ordered, bounded OTP regular-expression matching. */
public final class OtpDetector {
    public static final long MATCH_TIMEOUT_MILLIS = 500L;

    /*
     * Java's regex engine is not interruptible for every pathological expression. A bounded,
     * zero-queue pool prevents one malicious expression from blocking the SMS worker or creating
     * an unbounded number of matcher threads. A timed-out task is cancelled and its result is
     * discarded; a stuck worker is retired when it eventually returns.
     */
    private static final ThreadPoolExecutor MATCH_EXECUTOR = new ThreadPoolExecutor(
            0, 2, 10L, TimeUnit.SECONDS, new SynchronousQueue<>(), runnable -> {
                final Thread thread = new Thread(runnable, "OtpRegexMatcher");
                thread.setPriority(Thread.MIN_PRIORITY);
                return thread;
            });

    private OtpDetector() {}

    /**
     * Runs matching off the caller's UI thread and returns null on timeout, invalid pattern, or
     * no match. The 500 ms limit covers the whole ordered rule set, not every individual rule.
     */
    @Nullable
    public static OtpMatch detectWithinTimeout(@NonNull final String message,
            @NonNull final List<OtpRule> orderedRules) {
        final Future<OtpMatch> future;
        try {
            future = MATCH_EXECUTOR.submit(() -> detect(message, orderedRules));
        } catch (final RejectedExecutionException exception) {
            LogUtil.w(LogUtil.BUGLE_TAG, "OTP matching skipped: regex workers are busy");
            return null;
        }
        try {
            return future.get(MATCH_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
        } catch (final TimeoutException exception) {
            future.cancel(true);
            LogUtil.w(LogUtil.BUGLE_TAG, "OTP matching timed out");
        } catch (final InterruptedException exception) {
            future.cancel(true);
            Thread.currentThread().interrupt();
        } catch (final ExecutionException exception) {
            LogUtil.w(LogUtil.BUGLE_TAG, "OTP matching failed", exception.getCause());
        }
        return null;
    }

    /** Used by the rule editor's preview worker. */
    @Nullable
    public static OtpMatch previewWithinTimeout(@NonNull final String pattern,
            @NonNull final String message) {
        return detectWithinTimeout(message, java.util.Collections.singletonList(
                new OtpRule(-1, "preview", pattern, 0, true, false)));
    }

    @Nullable
    private static OtpMatch detect(final String message, final List<OtpRule> orderedRules) {
        final ArrayList<com.android.messaging.smartcore.OtpRule> coreRules = new ArrayList<>();
        for (final OtpRule rule : orderedRules) {
            coreRules.add(new com.android.messaging.smartcore.OtpRule(rule.id, rule.pattern,
                    rule.priority, rule.enabled));
        }
        final com.android.messaging.smartcore.OtpMatch match =
                com.android.messaging.smartcore.OtpRecognizer.detect(message, coreRules);
        if (match == null) return null;
        for (final OtpRule rule : orderedRules) {
            if (rule.id == match.ruleId) {
                return new OtpMatch(match.value, match.start, match.end, rule);
            }
        }
        return null;
    }
}
