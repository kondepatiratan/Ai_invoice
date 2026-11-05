package com.ratan.invoice.dto.ai;

import jakarta.validation.constraints.NotBlank;

public record AiPromptRequest(
        @NotBlank(message = "Prompt cannot be blank")
        String prompt,
        String apiKey,
        String fallbackApiKey,
        Boolean simulateFallback
) {
}
