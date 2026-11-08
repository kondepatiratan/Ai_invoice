package com.ratan.invoice.dto.ai;

import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;

/** What the invoice form currently shows, so the AI can write matching payment notes. */
public record AiNotesRequest(
        @Size(max = 150) String clientName,
        String dueDate,
        BigDecimal totalAmount,
        @Size(max = 20) List<String> itemDescriptions
) {
}
