package com.ratan.invoice.service.ai.assistant;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class NotesTemplateTest {

    @Test
    void includesClientAmountAndDueDate() {
        String notes = NotesTemplate.build("Ratan Tech Solutions", "Infosys Ltd", "2026-11-23", new BigDecimal("188800"));
        assertTrue(notes.contains("Thank you for your business, Infosys Ltd."));
        assertTrue(notes.contains("₹1,88,800.00 is due by 23 November 2026."));
        assertTrue(notes.contains("contact Ratan Tech Solutions."));
    }

    @Test
    void neverInventsMissingDetails() {
        String notes = NotesTemplate.build(null, null, "not-a-date", null);
        assertTrue(notes.contains("due as per the agreed terms"));
        assertFalse(notes.contains("["));
        assertFalse(notes.contains("null"));
    }
}
