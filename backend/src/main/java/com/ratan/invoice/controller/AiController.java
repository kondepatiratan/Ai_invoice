package com.ratan.invoice.controller;

import com.ratan.invoice.dto.ai.AiChatRequest;
import com.ratan.invoice.dto.ai.AiChatResponse;
import com.ratan.invoice.dto.ai.AiEnhanceRequest;
import com.ratan.invoice.dto.ai.AiNotesRequest;
import com.ratan.invoice.dto.ai.AiNotesResponse;
import com.ratan.invoice.dto.ai.AiEnhanceResponse;
import com.ratan.invoice.dto.ai.AiGeneratedInvoiceResponse;
import com.ratan.invoice.dto.ai.AiPromptRequest;
import com.ratan.invoice.dto.ai.AiReminderRequest;
import com.ratan.invoice.dto.ai.AiReminderResponse;
import com.ratan.invoice.dto.ai.AiStatusResponse;
import com.ratan.invoice.security.SecurityUtils;
import com.ratan.invoice.service.ai.AiInvoiceService;
import com.ratan.invoice.service.ai.assistant.AiAssistantService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ai")
public class AiController {

    private final AiInvoiceService aiInvoiceService;
    private final AiAssistantService aiAssistantService;

    public AiController(AiInvoiceService aiInvoiceService, AiAssistantService aiAssistantService) {
        this.aiInvoiceService = aiInvoiceService;
        this.aiAssistantService = aiAssistantService;
    }

    @GetMapping("/health")
    public AiStatusResponse getHealth() {
        return aiInvoiceService.getStatus();
    }

    @GetMapping("/status")
    public AiStatusResponse getStatus() {
        return aiInvoiceService.getStatus();
    }

    @PostMapping("/generate-invoice")
    public AiGeneratedInvoiceResponse generateInvoice(@Valid @RequestBody AiPromptRequest request) {
        return aiInvoiceService.generateInvoice(request);
    }

    @PostMapping("/enhance-description")
    public AiEnhanceResponse enhanceDescription(@Valid @RequestBody AiEnhanceRequest request) {
        return aiInvoiceService.enhanceDescription(request);
    }

    /** "Ask your invoices": read-only questions about the logged-in business's data. */
    @PostMapping("/chat")
    public AiChatResponse chat(@Valid @RequestBody AiChatRequest request) {
        return aiAssistantService.ask(request);
    }

    /** Drafts payment terms and notes for the invoice form. */
    @PostMapping("/write-notes")
    public AiNotesResponse writeNotes(@Valid @RequestBody AiNotesRequest request) {
        return aiAssistantService.writeNotes(request);
    }

    @PostMapping("/reminder")
    public AiReminderResponse generateReminder(@Valid @RequestBody AiReminderRequest request) {
        Long userId = SecurityUtils.getCurrentUser().getId();
        return aiInvoiceService.generatePaymentReminder(request, userId);
    }
}
