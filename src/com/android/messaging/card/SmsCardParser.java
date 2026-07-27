/* Copyright (C) 2026 SPDX-License-Identifier: Apache-2.0 */
package com.android.messaging.card;

import android.content.Context;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.messaging.otp.OtpDetectionCache;
import com.android.messaging.smartcore.ParsedServiceCard;
import com.android.messaging.smartcore.ServiceCardField;
import com.android.messaging.smartcore.ServiceCardRule;
import com.android.messaging.smartcore.ServiceSmsParser;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/** Purely local asset-rule parser for Chinese service SMS cards. */
public final class SmsCardParser {
    private static final Map<String, String> EXPRESS_COMPANIES = new HashMap<>();
    static {
        EXPRESS_COMPANIES.put("95338", "顺丰速运");
        EXPRESS_COMPANIES.put("95543", "申通快递");
        EXPRESS_COMPANIES.put("95559", "圆通速递");
        EXPRESS_COMPANIES.put("95706", "韵达快递");
        EXPRESS_COMPANIES.put("95353", "京东物流");
        EXPRESS_COMPANIES.put("4001182211", "菜鸟驿站");
    }

    private SmsCardParser() {}

    @NonNull
    public static List<ParsedCard> parse(@NonNull final Context context, @NonNull final String body,
            @Nullable final String sender) {
        final ArrayList<ParsedCard> cards = new ArrayList<>();
        final String otp = OtpDetectionCache.get(body);
        if (otp != null && SmsCardSettings.isTypeEnabled(context, CardType.OTP_CARD)) {
            cards.add(createOtpCard(body, otp));
        }
        for (final CardRule rule : CardRuleLoader.load(context)) {
            if (!SmsCardSettings.isTypeEnabled(context, rule.type)) continue;
            final ParsedCard card = parseRule(rule, body, sender,
                    SmsCardSettings.minimumConfidence(context));
            if (card != null) cards.add(card);
        }
        return cards;
    }

    @Nullable
    private static ParsedCard parseRule(@NonNull final CardRule rule, @NonNull final String body,
            @Nullable final String sender, final float minimumConfidence) {
        final ArrayList<ServiceCardField> fields = new ArrayList<>();
        for (final CardRule.Field field : rule.fields) {
            fields.add(new ServiceCardField(field.name, field.required, field.weight, field.patterns));
        }
        final ServiceCardRule coreRule = new ServiceCardRule(rule.type.name(), rule.keywords,
                rule.senderPrefixes, fields);
        final List<ParsedServiceCard> parsed = ServiceSmsParser.parse(
                java.util.Collections.singletonList(coreRule), body, sender, minimumConfidence);
        if (parsed.isEmpty()) return null;
        final ParsedServiceCard coreCard = parsed.get(0);
        final JSONObject data = new JSONObject();
        try {
            for (final Map.Entry<String, String> entry : coreCard.fields.entrySet()) {
                data.put(entry.getKey(), entry.getValue());
            }
            enrich(rule.type, data, body, sender);
            data.put("cardType", rule.type.name());
            return new ParsedCard(rule.type, data.toString(), coreCard.confidence);
        } catch (final JSONException ignored) {
            return null;
        }
    }

    @NonNull
    private static ParsedCard createOtpCard(@NonNull final String body, @NonNull final String otp) {
        final JSONObject data = new JSONObject();
        try {
            data.put("otp", otp);
            data.put("sourceApp", inferOtpSource(body));
            final String expiry = findFirst(java.util.Collections.singletonList(
                    "(?:有效期|请在|分钟内|秒内)[^0-9]{0,8}(\\d{1,3})\\s*(分钟|秒)"), body);
            if (expiry != null) data.put("expiry", expiry);
            data.put("cardType", CardType.OTP_CARD.name());
        } catch (final JSONException ignored) { }
        return new ParsedCard(CardType.OTP_CARD, data.toString(), 1f);
    }

    private static void enrich(@NonNull final CardType type, @NonNull final JSONObject data,
            @NonNull final String body, @Nullable final String sender) throws JSONException {
        switch (type) {
            case BANK_CARD:
                data.put("currency", "CNY");
                data.put("transactionType", firstKeyword(body,
                        new String[] {"消费", "到账", "转账", "还款", "扣款", "入账", "交易"}));
                break;
            case EXPRESS_CARD:
                data.put("company", inferCompany(body, sender));
                data.put("status", inferExpressStatus(body));
                break;
            case FLIGHT_CARD:
                data.put("airline", inferAirline(data.optString("flightNumber")));
                break;
            default:
                break;
        }
    }

    @Nullable
    private static String findFirst(@NonNull final List<String> patterns, @NonNull final String body) {
        for (final String expression : patterns) {
            try {
                final Matcher matcher = Pattern.compile(expression,
                        Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE).matcher(body);
                if (!matcher.find()) continue;
                for (int index = 1; index <= matcher.groupCount(); index++) {
                    if (matcher.group(index) != null) return matcher.group(index).trim();
                }
                return matcher.group().trim();
            } catch (final PatternSyntaxException ignored) {
                // Imports are validated before persistence; this guards against damaged files.
            }
        }
        return null;
    }

    @NonNull
    private static String inferCompany(@NonNull final String body, @Nullable final String sender) {
        final String normalized = sender == null ? "" : sender.replaceAll("[^0-9]", "");
        for (final Map.Entry<String, String> entry : EXPRESS_COMPANIES.entrySet()) {
            if (normalized.startsWith(entry.getKey()) || body.contains(entry.getValue())) return entry.getValue();
        }
        return "快递服务";
    }

    @NonNull
    private static String inferExpressStatus(@NonNull final String body) {
        if (body.contains("已签收") || body.contains("签收")) return "DELIVERED";
        if (body.contains("派送")) return "OUT_FOR_DELIVERY";
        if (body.contains("取件") || body.contains("驿站") || body.contains("丰巢")) return "PENDING_PICKUP";
        if (body.contains("揽收")) return "COLLECTED";
        return "IN_TRANSIT";
    }

    @NonNull
    private static String inferOtpSource(@NonNull final String body) {
        if (body.contains("支付宝")) return "可能来自支付宝";
        if (body.contains("微信")) return "可能来自微信";
        if (body.contains("淘宝")) return "可能来自淘宝";
        if (body.contains("银行")) return "可能来自银行服务";
        return "服务验证码";
    }

    @NonNull
    private static String inferAirline(@Nullable final String number) {
        if (TextUtils.isEmpty(number)) return "航空公司";
        final String prefix = number.replaceAll("[0-9]", "").toUpperCase(Locale.ROOT);
        if ("CA".equals(prefix)) return "中国国际航空";
        if ("MU".equals(prefix)) return "中国东方航空";
        if ("CZ".equals(prefix)) return "中国南方航空";
        return "航空公司";
    }

    @NonNull
    private static String firstKeyword(@NonNull final String body, @NonNull final String[] values) {
        for (final String value : values) if (body.contains(value)) return value;
        return "交易";
    }

    public static final class ParsedCard {
        @NonNull public final CardType type;
        @NonNull public final String data;
        public final float confidence;
        ParsedCard(@NonNull final CardType type, @NonNull final String data, final float confidence) {
            this.type = type; this.data = data; this.confidence = confidence;
        }
    }
}
