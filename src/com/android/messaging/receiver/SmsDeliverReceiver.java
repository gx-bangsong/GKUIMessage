/*
 * Copyright (C) 2015 The Android Open Source Project
 * Copyright (C) 2024 The LineageOS Project
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

package com.android.messaging.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.provider.Telephony.Sms;

import com.android.messaging.otp.OtpAutoCopyManager;
import com.android.messaging.util.ThreadUtil;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Class that receives incoming SMS messages on KLP+ Devices.
 */
public final class SmsDeliverReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(final Context context, final Intent intent) {
        if (Sms.Intents.SMS_DELIVER_ACTION.equals(intent.getAction())) {
            // Keep the ordered SMS broadcast alive only for the bounded receive-time OTP task.
            // Normal delivery is deliberately started immediately and is never blocked by regex.
            final PendingResult pendingResult = goAsync();
            final AtomicBoolean finished = new AtomicBoolean();
            final Runnable finishBroadcast = () -> {
                if (finished.compareAndSet(false, true)) {
                    pendingResult.finish();
                }
            };
            // The 750 ms guard is independent of the 500 ms regex limit. It guarantees that a
            // database failure cannot keep an ordered SMS broadcast alive until the ANR limit.
            ThreadUtil.getMainThreadHandler().postDelayed(finishBroadcast, 750L);
            OtpAutoCopyManager.processIncomingSms(context, SmsReceiver.getIncomingSmsBody(intent),
                    finishBroadcast);
            SmsReceiver.deliverSmsIntent(context, intent);
        }
    }
}
