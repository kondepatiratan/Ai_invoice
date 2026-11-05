package com.ratan.invoice.dto;

import com.ratan.invoice.entity.InvoiceStatus;
import jakarta.validation.constraints.NotNull;

public record InvoiceStatusRequest(
        @NotNull(message = "Status is required")
        InvoiceStatus status
) {
}
