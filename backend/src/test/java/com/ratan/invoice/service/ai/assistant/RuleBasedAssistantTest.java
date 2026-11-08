package com.ratan.invoice.service.ai.assistant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ratan.invoice.service.ai.assistant.BusinessSnapshot.ClientInfo;
import com.ratan.invoice.service.ai.assistant.BusinessSnapshot.InvoiceInfo;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class RuleBasedAssistantTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 8);

    private final BusinessSnapshot snapshot = BusinessSnapshot.build(
            "Ratan Tech Solutions",
            "Andhra Pradesh",
            TODAY,
            List.of(new ClientInfo("Infosys Ltd", "Karnataka"),
                    new ClientInfo("Sai Traders", "Andhra Pradesh"),
                    new ClientInfo("TechNova Labs", "Maharashtra")),
            List.of(invoice("INV-0004", "Infosys Ltd", "DRAFT", "188800.00", 22),
                    invoice("INV-0003", "TechNova Labs", "OVERDUE", "100300.00", -14),
                    invoice("INV-0002", "Sai Traders", "SENT", "53100.00", 9),
                    invoice("INV-0001", "Infosys Ltd", "PAID", "141600.00", -19)));

    @Test
    void calculatesTotalsAndIgnoresDraftsForMoneyOwed() {
        BusinessSnapshot.Totals totals = snapshot.totals();
        assertEquals(new BigDecimal("141600.00"), totals.collected());
        assertEquals(new BigDecimal("53100.00"), totals.pending());
        assertEquals(new BigDecimal("100300.00"), totals.overdue());
        assertEquals(new BigDecimal("153400.00"), totals.outstanding());
        assertEquals(1, totals.draftCount());
    }

    @Test
    void listsOverdueInvoicesWithDaysLate() {
        String answer = RuleBasedAssistant.answer("Which invoices are overdue?", snapshot);
        assertTrue(answer.contains("INV-0003"));
        assertTrue(answer.contains("14 days late"));
        assertTrue(answer.contains("₹1,00,300.00"));
    }

    @Test
    void answersForAClientNamedInTheQuestion() {
        String answer = RuleBasedAssistant.answer("How much does techNova labs owe me?", snapshot);
        assertTrue(answer.startsWith("TechNova Labs"));
        assertTrue(answer.contains("still outstanding: ₹1,00,300.00"));
    }

    @Test
    void outstandingExcludesDrafts() {
        String answer = RuleBasedAssistant.answer("How much is outstanding?", snapshot);
        assertTrue(answer.contains("₹1,53,400.00"));
        assertFalse(answer.contains("INV-0004"));
    }

    @Test
    void collectedQuestionReportsPaidInvoices() {
        String answer = RuleBasedAssistant.answer("How much have I collected?", snapshot);
        assertTrue(answer.startsWith("You've collected ₹1,41,600.00"));
    }

    @Test
    void formatsAmountsWithIndianGrouping() {
        assertEquals("₹1,88,800.00", BusinessSnapshot.formatInr(new BigDecimal("188800")));
        assertEquals("₹1,23,45,678.50", BusinessSnapshot.formatInr(new BigDecimal("12345678.5")));
        assertEquals("₹999.00", BusinessSnapshot.formatInr(new BigDecimal("999")));
    }

    @Test
    void explainsWhenThereAreNoInvoices() {
        BusinessSnapshot empty = BusinessSnapshot.build("Empty Co", "Goa", TODAY, List.of(), List.of());
        assertTrue(RuleBasedAssistant.answer("Which invoices are overdue?", empty).startsWith("You don't have any invoices yet"));
    }

    private static InvoiceInfo invoice(String number, String client, String status, String total, int dueInDays) {
        return new InvoiceInfo(number, client, TODAY.minusDays(15), TODAY.plusDays(dueInDays), status, new BigDecimal(total));
    }
}
