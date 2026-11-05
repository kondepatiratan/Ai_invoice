package com.ratan.invoice.dto.ai;

import java.math.BigDecimal;

public record AiEnhanceResponse(
        String title,
        String enhancedDescription,
        BigDecimal suggestedRate,
        BigDecimal suggestedQuantity,
        BigDecimal recommendedGstRate,
        String hsnSacCode,
        String providerUsed
) {
}
