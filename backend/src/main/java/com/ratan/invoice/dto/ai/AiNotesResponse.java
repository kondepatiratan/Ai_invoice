package com.ratan.invoice.dto.ai;

public record AiNotesResponse(
        String notes,
        String providerUsed,
        boolean fallbackUsed,
        long responseTimeMs
) {
}
