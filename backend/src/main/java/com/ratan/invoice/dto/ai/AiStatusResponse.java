package com.ratan.invoice.dto.ai;

public record AiStatusResponse(
        boolean geminiConfigured,
        String geminiModel,
        boolean openaiConfigured,
        String openaiModel,
        boolean localFallbackAvailable,
        String activePrimary
) {
}
