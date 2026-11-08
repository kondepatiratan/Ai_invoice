package com.ratan.invoice.service.ai.assistant;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Locale;

/** Standard payment notes, used when no AI provider is available. */
public final class NotesTemplate {

    private static final DateTimeFormatter DISPLAY = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.ENGLISH);

    private NotesTemplate() {
    }

    public static String build(String businessName, String clientName, String dueDate, BigDecimal total) {
        StringBuilder sb = new StringBuilder();
        sb.append("Thank you for your business");
        if (clientName != null && !clientName.isBlank()) {
            sb.append(", ").append(clientName.trim());
        }
        sb.append(".\n");

        sb.append("Payment of ").append(total == null ? "the invoice amount" : BusinessSnapshot.formatInr(total));
        String due = formatDate(dueDate);
        sb.append(due == null ? " is due as per the agreed terms." : " is due by " + due + ".");
        sb.append('\n');

        sb.append("Please quote the invoice number when making the payment.\n");
        sb.append("For any questions about this invoice, please contact ")
                .append(businessName == null || businessName.isBlank() ? "us" : businessName.trim())
                .append('.');
        return sb.toString();
    }

    private static String formatDate(String isoDate) {
        if (isoDate == null || isoDate.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(isoDate.trim()).format(DISPLAY);
        } catch (DateTimeParseException ex) {
            return null;
        }
    }
}
