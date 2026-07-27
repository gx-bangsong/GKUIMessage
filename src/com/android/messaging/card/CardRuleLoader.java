/* Copyright (C) 2026 SPDX-License-Identifier: Apache-2.0 */
package com.android.messaging.card;

import android.content.Context;
import android.content.res.AssetManager;

import androidx.annotation.NonNull;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/** Loads built-in asset rules and optional user-imported local JSON rules. */
final class CardRuleLoader {
    private static final String[] BUILT_IN_FILES = {
            "bank_rules.json", "express_rules.json", "train_rules.json", "flight_rules.json",
            "appointment_rules.json", "recharge_rules.json"
    };

    private CardRuleLoader() {}

    @NonNull
    static List<CardRule> load(@NonNull final Context context) {
        final ArrayList<CardRule> rules = new ArrayList<>();
        final AssetManager assets = context.getAssets();
        for (final String name : BUILT_IN_FILES) {
            try (InputStream input = assets.open("card_rules/" + name)) {
                rules.add(parse(read(input)));
            } catch (final IOException | JSONException ignored) {
                // One damaged optional rule must not stop other local parsing rules.
            }
        }
        final File customDirectory = new File(context.getFilesDir(), "card_rules");
        final File[] customFiles = customDirectory.listFiles((dir, name) -> name.endsWith(".json"));
        if (customFiles != null) {
            for (final File file : customFiles) {
                try (InputStream input = new FileInputStream(file)) {
                    rules.add(parse(read(input)));
                } catch (final IOException | JSONException ignored) {
                    // Invalid imports are rejected at import time; guard again in case files change.
                }
            }
        }
        return rules;
    }

    /** Minimal local validation matching the distributed JSON Schema's required semantics. */
    static void validate(@NonNull final String json) throws JSONException {
        parse(json);
    }

    @NonNull
    private static CardRule parse(@NonNull final String json) throws JSONException {
        final JSONObject root = new JSONObject(json);
        if (root.optInt("version", 0) < 1 || !root.has("$schema")) {
            throw new JSONException("Missing $schema or supported version");
        }
        final CardType type = CardType.valueOf(root.getString("cardType"));
        final JSONObject triggers = root.getJSONObject("triggers");
        final List<String> keywords = toStrings(triggers.optJSONArray("keywords"));
        final List<String> senderPrefixes = toStrings(triggers.optJSONArray("senderPrefixes"));
        if (keywords.isEmpty() && senderPrefixes.isEmpty()) throw new JSONException("Missing trigger");
        final JSONArray fieldsJson = root.getJSONArray("fields");
        final ArrayList<CardRule.Field> fields = new ArrayList<>();
        for (int i = 0; i < fieldsJson.length(); i++) {
            final JSONObject field = fieldsJson.getJSONObject(i);
            final String name = field.getString("name");
            final boolean required = field.optBoolean("required", false);
            final float weight = (float) field.optDouble("weight", required ? 1d : .1d);
            if (weight < 0 || weight > 1) throw new JSONException("Invalid field weight");
            final List<String> patterns = toStrings(field.getJSONArray("patterns"));
            if (patterns.isEmpty()) throw new JSONException("Missing field pattern");
            for (final String pattern : patterns) {
                try { Pattern.compile(pattern); }
                catch (final PatternSyntaxException exception) {
                    throw new JSONException("Invalid Java regex for " + name + ": "
                            + exception.getDescription());
                }
            }
            fields.add(new CardRule.Field(name, required, weight, patterns));
        }
        return new CardRule(type, keywords, senderPrefixes, fields);
    }

    @NonNull
    private static List<String> toStrings(final JSONArray values) throws JSONException {
        if (values == null) return new ArrayList<>();
        final ArrayList<String> result = new ArrayList<>();
        for (int i = 0; i < values.length(); i++) result.add(values.getString(i));
        return result;
    }

    @NonNull
    private static String read(@NonNull final InputStream input) throws IOException {
        final ByteArrayOutputStream output = new ByteArrayOutputStream();
        final byte[] buffer = new byte[4096];
        int read;
        while ((read = input.read(buffer)) != -1) output.write(buffer, 0, read);
        return output.toString(StandardCharsets.UTF_8.name());
    }
}
