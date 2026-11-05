package com.ratan.invoice.dto.ai;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record AiGeneratedInvoiceResponse(
        String clientName,
        String clientEmail,
        String clientPhone,
        String clientGstin,
        String clientState,
        String clientAddress,
        LocalDate issueDate,
        LocalDate dueDate,
        String notes,
        List<AiItemResponse> items,
        String providerUsed,
        String modelUsed,
        String rawResponse
) {
    public record AiItemResponse(
            String description,
            BigDecimal quantity,
            BigDecimal rate,
            BigDecimal gstRate,
            String hsnSacCode
    ) {
    }
}
