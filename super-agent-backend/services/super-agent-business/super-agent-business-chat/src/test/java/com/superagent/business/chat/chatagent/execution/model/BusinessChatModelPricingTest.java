package com.superagent.business.chat.chatagent.execution.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class BusinessChatModelPricingTest {

    @Test
    void shouldQuoteDeepSeekV4FlashWithOfficialUsdPrice() {
        BusinessChatModelPricing.PriceQuote quote = BusinessChatModelPricing.quote(
                BusinessChatModelProvider.DEEPSEEK,
                "https://api.deepseek.com",
                "deepseek-v4-flash",
                "STREAM",
                10_000,
                2_000);

        assertThat(quote.currency()).isEqualTo("USD");
        assertThat(quote.inputTokenUnitPrice()).isEqualByComparingTo("0.14");
        assertThat(quote.outputTokenUnitPrice()).isEqualByComparingTo("0.28");
        assertThat(quote.priceUnitTokens()).isEqualTo(1_000_000);
        assertThat(quote.estimatedCost()).isEqualByComparingTo("0.00196000");
    }

    @Test
    void shouldQuoteDashScopeQwenPlusByInputTierAndCallType() {
        BusinessChatModelPricing.PriceQuote streamQuote = BusinessChatModelPricing.quote(
                BusinessChatModelProvider.DASHSCOPE,
                "https://dashscope.aliyuncs.com/compatible-mode",
                "qwen-plus",
                "STREAM",
                150_000,
                10_000);
        BusinessChatModelPricing.PriceQuote nonStreamQuote = BusinessChatModelPricing.quote(
                BusinessChatModelProvider.DASHSCOPE,
                "https://dashscope.aliyuncs.com/compatible-mode",
                "qwen-plus",
                "NON_STREAM",
                150_000,
                10_000);

        assertThat(streamQuote.currency()).isEqualTo("CNY");
        assertThat(streamQuote.inputTokenUnitPrice()).isEqualByComparingTo("2.4");
        assertThat(streamQuote.outputTokenUnitPrice()).isEqualByComparingTo("24");
        assertThat(streamQuote.estimatedCost()).isEqualByComparingTo("0.60000000");
        assertThat(nonStreamQuote.outputTokenUnitPrice()).isEqualByComparingTo("20");
        assertThat(nonStreamQuote.estimatedCost()).isEqualByComparingTo("0.56000000");
    }

    @Test
    void shouldRejectModelWithoutConfiguredPrice() {
        assertThatThrownBy(() -> BusinessChatModelPricing.configUnitPrice(
                BusinessChatModelProvider.ZHIPU,
                "https://open.bigmodel.cn/api/paas/v4",
                "glm-4-flash"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("model pricing is not configured");
    }
}
