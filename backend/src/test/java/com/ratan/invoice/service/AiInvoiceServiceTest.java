package com.ratan.invoice.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ratan.invoice.dto.ai.AiEnhanceRequest;
import com.ratan.invoice.dto.ai.AiEnhanceResponse;
import com.ratan.invoice.dto.ai.AiGeneratedInvoiceResponse;
import com.ratan.invoice.dto.ai.AiPromptRequest;
import com.ratan.invoice.repository.InvoiceRepository;
import com.ratan.invoice.service.ai.AiInvoiceService;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AiInvoiceServiceTest {

    @Mock
    private InvoiceRepository invoiceRepository;

    private AiInvoiceService aiInvoiceService;

    @BeforeEach
    void setUp() {
        aiInvoiceService = new AiInvoiceService(new ObjectMapper(), invoiceRepository);
    }

    @Test
    void fallbackSmartRuleParserExtractsClientAndItemsFromPrompt() {
        String prompt = "Create invoice for Infosys for 40 hours of frontend development at 2500 INR with 18% GST due in 20 days";

        AiPromptRequest request = new AiPromptRequest(prompt, null, null, true);
        AiGeneratedInvoiceResponse response = aiInvoiceService.generateInvoice(request);

        assertNotNull(response);
        assertNotNull(response.items());
        assertFalse(response.items().isEmpty());

        assertEquals("Infosys", response.clientName());
        assertEquals(new BigDecimal("40"), response.items().get(0).quantity());
        assertEquals(new BigDecimal("2500"), response.items().get(0).rate());
        assertEquals(new BigDecimal("18.00"), response.items().get(0).gstRate());
        assertTrue(response.providerUsed().contains("Fallback"));
    }

    @Test
    void fallbackRuleEnhancerGeneratesDetailedDescription() {
        AiEnhanceRequest req = new AiEnhanceRequest("UI UX Design", "Mobile application design");
        AiEnhanceResponse res = aiInvoiceService.enhanceDescription(req);

        assertNotNull(res);
        assertTrue(res.enhancedDescription().toLowerCase().contains("design"));
        assertEquals("998314", res.hsnSacCode());
        assertEquals(new BigDecimal("18.00"), res.recommendedGstRate());
    }
}
