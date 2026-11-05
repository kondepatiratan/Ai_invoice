package com.ratan.invoice.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.ratan.invoice.dto.InvoiceItemRequest;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class GstCalculatorTest {

    private final GstCalculator calculator = new GstCalculator();

    @Test
    void splitsTaxIntoCgstAndSgstForSameState() {
        GstCalculator.Calculation result = calculator.calculate(
                List.of(item("1", "100", "18")),
                "Maharashtra",
                "  maharashtra  ");

        assertMoney("100.00", result.subtotal());
        assertMoney("9.00", result.cgst());
        assertMoney("9.00", result.sgst());
        assertMoney("0.00", result.igst());
        assertMoney("118.00", result.totalAmount());
    }

    @Test
    void appliesFullTaxAsIgstForDifferentStates() {
        GstCalculator.Calculation result = calculator.calculate(
                List.of(item("2", "50", "12")),
                "Maharashtra",
                "Gujarat");

        assertMoney("100.00", result.subtotal());
        assertMoney("0.00", result.cgst());
        assertMoney("0.00", result.sgst());
        assertMoney("12.00", result.igst());
        assertMoney("112.00", result.totalAmount());
    }

    @Test
    void calculatesMultipleItemsUsingTheirOwnGstRates() {
        GstCalculator.Calculation result = calculator.calculate(
                List.of(item("2", "10", "5"), item("1", "100", "18")),
                "Karnataka",
                "Karnataka");

        assertMoney("120.00", result.subtotal());
        assertMoney("9.50", result.cgst());
        assertMoney("9.50", result.sgst());
        assertMoney("0.00", result.igst());
        assertMoney("139.00", result.totalAmount());
        assertMoney("20.00", result.items().get(0).amount());
        assertMoney("100.00", result.items().get(1).amount());
    }

    @Test
    void roundsAmountsAndTaxHalfUpToTwoDecimalPlaces() {
        GstCalculator.Calculation result = calculator.calculate(
                List.of(item("1", "0.05", "10")),
                "Delhi",
                "Delhi");

        assertMoney("0.05", result.subtotal());
        assertMoney("0.01", result.cgst());
        assertMoney("0.00", result.sgst());
        assertMoney("0.06", result.totalAmount());
    }

    private static InvoiceItemRequest item(String quantity, String rate, String gstRate) {
        return new InvoiceItemRequest(
                "Item",
                new BigDecimal(quantity),
                new BigDecimal(rate),
                new BigDecimal(gstRate));
    }

    private static void assertMoney(String expected, BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual));
        assertEquals(2, actual.scale());
    }
}
