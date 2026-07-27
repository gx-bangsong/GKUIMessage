/* Copyright (C) 2026 SPDX-License-Identifier: Apache-2.0 */
package com.android.messaging.card;

import android.content.Context;

import androidx.annotation.NonNull;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/** Imports only schema-valid local JSON card rules; no content is uploaded or remotely fetched. */
public final class SmsCardRuleStore {
    private SmsCardRuleStore() {}

    public static void importRule(@NonNull final Context context, @NonNull final InputStream input,
            @NonNull final String filename) throws IOException, org.json.JSONException {
        final String json = read(input);
        CardRuleLoader.validate(json);
        final File directory = new File(context.getFilesDir(), "card_rules");
        if (!directory.exists() && !directory.mkdirs()) throw new IOException("Cannot create rule dir");
        try (FileOutputStream output = new FileOutputStream(new File(directory,
                filename.replaceAll("[^A-Za-z0-9_.-]", "_")))) {
            output.write(json.getBytes(StandardCharsets.UTF_8));
        }
    }

    @NonNull
    public static String exportRules(@NonNull final Context context) throws IOException {
        final StringBuilder output = new StringBuilder();
        final String[] names = {"bank_rules.json", "express_rules.json", "train_rules.json",
                "flight_rules.json", "appointment_rules.json", "recharge_rules.json"};
        for (final String name : names) {
            try (InputStream input = context.getAssets().open("card_rules/" + name)) {
                output.append(read(input)).append('\n');
            }
        }
        return output.toString();
    }

    @NonNull private static String read(@NonNull final InputStream input) throws IOException {
        final ByteArrayOutputStream output = new ByteArrayOutputStream();
        final byte[] buffer = new byte[4096]; int count;
        while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
        return output.toString(StandardCharsets.UTF_8.name());
    }
}
