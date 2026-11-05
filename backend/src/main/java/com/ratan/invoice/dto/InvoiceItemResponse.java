package com.ratan.invoice.dto;

import java.math.BigDecimal;

public record InvoiceItemResponse(
        Long id,
        String description,
        BigDecimal quantity,
        BigDecimal rate,
        BigDecimal gstRate,
        BigDecimal amount
) {
}
