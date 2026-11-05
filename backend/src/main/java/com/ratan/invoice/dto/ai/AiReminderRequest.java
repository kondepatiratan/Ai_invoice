package com.ratan.invoice.dto.ai;

import jakarta.validation.constraints.NotNull;

public record AiReminderRequest(
        @NotNull(message = "Invoice ID is required")
        Long invoiceId,
        String tone
) {
}
