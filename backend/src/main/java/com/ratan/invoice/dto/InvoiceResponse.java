package com.ratan.invoice.dto;

import com.ratan.invoice.entity.InvoiceStatus;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record InvoiceResponse(
        Long id,
        String invoiceNumber,
        LocalDate issueDate,
        LocalDate dueDate,
        InvoiceStatus status,
        BigDecimal subtotal,
        BigDecimal cgst,
        BigDecimal sgst,
        BigDecimal igst,
        BigDecimal totalAmount,
        String notes,
        LocalDateTime createdAt,
        ClientResponse client,
        List<InvoiceItemResponse> items
) {
}
