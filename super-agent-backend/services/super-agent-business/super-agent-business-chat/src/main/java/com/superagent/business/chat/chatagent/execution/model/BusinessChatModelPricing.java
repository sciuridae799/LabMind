package com.superagent.business.chat.chatagent.execution.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import org.springframework.util.StringUtils;

public final class BusinessChatModelPricing {

    private static final int PRICE_UNIT_TOKENS = 1_000_000;

    private static final String CNY = "CNY";

    private static final String USD = "USD";

    private static final String STREAM_CALL = "STREAM";

    private BusinessChatModelPricing() {
    }

    public static PriceQuote quote(
            BusinessChatModelProvider provider,
            String baseUrl,
            String modelName,
            String callType,
            int inputTokens,
            int outputTokens) {
        UnitPrice unitPrice = unitPrice(provider, baseUrl, modelName, callType, inputTokens);
        BigDecimal unitTokens = BigDecimal.valueOf(PRICE_UNIT_TOKENS);
        BigDecimal inputCost = BigDecimal.valueOf(inputTokens)
                .multiply(unitPrice.inputTokenUnitPrice())
                .divide(unitTokens, 8, RoundingMode.HALF_UP);
        BigDecimal outputCost = BigDecimal.valueOf(outputTokens)
                .multiply(unitPrice.outputTokenUnitPrice())
                .divide(unitTokens, 8, RoundingMode.HALF_UP);
        return new PriceQuote(
                unitPrice.inputTokenUnitPrice(),
                unitPrice.outputTokenUnitPrice(),
                PRICE_UNIT_TOKENS,
                unitPrice.currency(),
                inputCost.add(outputCost));
    }

    public static UnitPrice configUnitPrice(
            BusinessChatModelProvider provider,
            String baseUrl,
            String modelName) {
        return unitPrice(provider, baseUrl, modelName, STREAM_CALL, 1);
    }

    private static UnitPrice unitPrice(
            BusinessChatModelProvider provider,
            String baseUrl,
            String modelName,
            String callType,
            int inputTokens) {
        if (provider == BusinessChatModelProvider.DEEPSEEK) {
            return deepSeekPrice(modelName);
        }
        if (provider == BusinessChatModelProvider.DASHSCOPE) {
            return dashScopePrice(baseUrl, modelName, callType, inputTokens);
        }
        if (provider == BusinessChatModelProvider.ZHIPU) {
            return zhipuPrice(modelName);
        }
        throw unsupported(provider, modelName);
    }

    private static UnitPrice deepSeekPrice(String modelName) {
        String normalizedModel = normalizeModelName(modelName);
        return switch (normalizedModel) {
            case "deepseek-v4-flash", "deepseek-chat", "deepseek-reasoner" ->
                    price("0.14", "0.28", USD);
            case "deepseek-v4-pro" -> price("0.435", "0.87", USD);
            default -> throw unsupported(BusinessChatModelProvider.DEEPSEEK, modelName);
        };
    }

    private static UnitPrice dashScopePrice(
            String baseUrl,
            String modelName,
            String callType,
            int inputTokens) {
        String normalizedModel = normalizeModelName(modelName);
        boolean international = isDashScopeInternational(baseUrl);
        boolean streamCall = STREAM_CALL.equals(callType);
        return switch (normalizedModel) {
            case "qwen-plus", "qwen-plus-latest" -> qwenPlusPrice(international, streamCall, inputTokens);
            case "qwen-turbo", "qwen-turbo-latest" -> qwenTurboPrice(international, streamCall);
            case "qwen-max", "qwen-max-latest" -> international
                    ? price("11.743", "46.971", CNY)
                    : price("11.743", "46.971", CNY);
            case "qwen3-235b-a22b", "qwen3-32b" -> international
                    ? price("1.688", streamCall ? "16.88" : "6.752", CNY)
                    : price("2", streamCall ? "20" : "8", CNY);
            default -> throw unsupported(BusinessChatModelProvider.DASHSCOPE, modelName);
        };
    }

    private static UnitPrice qwenPlusPrice(boolean international, boolean streamCall, int inputTokens) {
        if (international) {
            return inputTokens <= 256_000
                    ? price("3.5232", streamCall ? "35.2284" : "10.5684", CNY)
                    : price("10.5684", streamCall ? "105.6852" : "31.7052", CNY);
        }
        if (inputTokens <= 128_000) {
            return price("0.8", streamCall ? "8" : "2", CNY);
        }
        if (inputTokens <= 256_000) {
            return price("2.4", streamCall ? "24" : "20", CNY);
        }
        return price("4.8", streamCall ? "64" : "48", CNY);
    }

    private static UnitPrice qwenTurboPrice(boolean international, boolean streamCall) {
        if (international) {
            return price("0.367", streamCall ? "3.67" : "1.468", CNY);
        }
        return price("0.3", streamCall ? "3" : "0.6", CNY);
    }

    private static UnitPrice zhipuPrice(String modelName) {
        String normalizedModel = normalizeModelName(modelName);
        return switch (normalizedModel) {
            case "glm-5.1" -> price("6", "24", CNY);
            case "glm-5" -> price("4", "18", CNY);
            default -> throw unsupported(BusinessChatModelProvider.ZHIPU, modelName);
        };
    }

    private static boolean isDashScopeInternational(String baseUrl) {
        String normalizedBaseUrl = StringUtils.hasText(baseUrl) ? baseUrl.toLowerCase() : "";
        return normalizedBaseUrl.contains("dashscope-intl")
                || normalizedBaseUrl.contains("dashscope-us")
                || normalizedBaseUrl.contains("cn-hongkong");
    }

    private static UnitPrice price(String inputTokenUnitPrice, String outputTokenUnitPrice, String currency) {
        return new UnitPrice(
                new BigDecimal(inputTokenUnitPrice),
                new BigDecimal(outputTokenUnitPrice),
                PRICE_UNIT_TOKENS,
                currency);
    }

    private static String normalizeModelName(String modelName) {
        if (!StringUtils.hasText(modelName)) {
            throw new IllegalArgumentException("modelName must not be blank");
        }
        return modelName.strip().toLowerCase();
    }

    private static IllegalArgumentException unsupported(BusinessChatModelProvider provider, String modelName) {
        return new IllegalArgumentException("model pricing is not configured: provider="
                + provider.getValue() + ", modelName=" + modelName);
    }

    public record UnitPrice(
            BigDecimal inputTokenUnitPrice,
            BigDecimal outputTokenUnitPrice,
            int priceUnitTokens,
            String currency) {
    }

    public record PriceQuote(
            BigDecimal inputTokenUnitPrice,
            BigDecimal outputTokenUnitPrice,
            int priceUnitTokens,
            String currency,
            BigDecimal estimatedCost) {
    }
}
