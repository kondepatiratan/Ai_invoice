package com.ratan.invoice.dto.ai;

public record AiReminderResponse(
        String subject,
        String messageBody,
        String channelSuggestion,
        String providerUsed
) {
}
