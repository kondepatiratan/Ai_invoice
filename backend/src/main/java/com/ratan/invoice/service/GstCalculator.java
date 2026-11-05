package com.ratan.invoice.service;

import com.ratan.invoice.dto.InvoiceItemRequest;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Component;

@Component
public class GstCalculator {

    private static final BigDecimal HUNDRED = new BigDecimal("100");
    private static final int SCALE = 2;

    public Calculation calculate(
            List<InvoiceItemRequest> items,
            String sellerState,
            String clientState) {
        boolean intrastate = normalizeState(sellerState).equalsIgnoreCase(normalizeState(clientState));
        BigDecimal subtotal = BigDecimal.ZERO.setScale(SCALE);
        BigDecimal cgst = BigDecimal.ZERO.setScale(SCALE);
        BigDecimal sgst = BigDecimal.ZERO.setScale(SCALE);
        BigDecimal igst = BigDecimal.ZERO.setScale(SCALE);
        var itemCalculations = new java.util.ArrayList<ItemCalculation>(items.size());

        for (InvoiceItemRequest item : items) {
            BigDecimal amount = item.quantity().multiply(item.rate()).setScale(SCALE, RoundingMode.HALF_UP);
            BigDecimal tax = amount.multiply(item.gstRate())
                    .divide(HUNDRED, SCALE, RoundingMode.HALF_UP);
            BigDecimal itemCgst = BigDecimal.ZERO.setScale(SCALE);
            BigDecimal itemSgst = BigDecimal.ZERO.setScale(SCALE);
            BigDecimal itemIgst = BigDecimal.ZERO.setScale(SCALE);

            if (intrastate) {
                itemCgst = tax.divide(BigDecimal.valueOf(2), SCALE, RoundingMode.HALF_UP);
                itemSgst = tax.subtract(itemCgst).setScale(SCALE, RoundingMode.HALF_UP);
                cgst = cgst.add(itemCgst);
                sgst = sgst.add(itemSgst);
            } else {
                itemIgst = tax;
                igst = igst.add(itemIgst);
            }

            subtotal = subtotal.add(amount);
            itemCalculations.add(new ItemCalculation(amount, itemCgst, itemSgst, itemIgst));
        }

        BigDecimal totalAmount = subtotal.add(cgst).add(sgst).add(igst).setScale(SCALE, RoundingMode.HALF_UP);
        return new Calculation(
                subtotal.setScale(SCALE, RoundingMode.HALF_UP),
                cgst.setScale(SCALE, RoundingMode.HALF_UP),
                sgst.setScale(SCALE, RoundingMode.HALF_UP),
                igst.setScale(SCALE, RoundingMode.HALF_UP),
                totalAmount,
                List.copyOf(itemCalculations));
    }

    private String normalizeState(String state) {
        return Objects.toString(state, "").trim().replaceAll("\\s+", " ");
    }

    public record Calculation(
            BigDecimal subtotal,
            BigDecimal cgst,
            BigDecimal sgst,
            BigDecimal igst,
            BigDecimal totalAmount,
            List<ItemCalculation> items
    ) {
    }

    public record ItemCalculation(
            BigDecimal amount,
            BigDecimal cgst,
            BigDecimal sgst,
            BigDecimal igst
    ) {
    }
}
