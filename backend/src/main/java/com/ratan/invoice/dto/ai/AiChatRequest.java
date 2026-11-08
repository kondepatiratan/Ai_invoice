package com.ratan.invoice.dto.ai;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

public record AiChatRequest(
        @NotBlank(message = "Please type a question")
        @Size(max = 500, message = "Questions can be at most 500 characters")
        String question,
        List<ChatTurn> history,
        Boolean simulateFallback
) {

    /** One earlier message in the conversation, so follow-up questions make sense. */
    public record ChatTurn(String role, String text) {
    }
}
