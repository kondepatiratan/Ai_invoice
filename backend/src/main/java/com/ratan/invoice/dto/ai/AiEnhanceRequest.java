package com.ratan.invoice.dto.ai;

import jakarta.validation.constraints.NotBlank;

public record AiEnhanceRequest(
        @NotBlank(message = "Title is required")
        String title,
        String context
) {
}
