package com.ratan.invoice.dto.ai;

public record AiChatResponse(
        String answer,
        String providerUsed,
        boolean fallbackUsed,
        long responseTimeMs
) {
}
