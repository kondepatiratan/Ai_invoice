package com.ratan.invoice.service.ai.assistant;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * A read-only summary of one business's invoices and clients.
 * Every number here is calculated by code, so the AI only has to explain it, never compute it.
 */
public record BusinessSnapshot(
        String businessName,
        String businessState,
        LocalDate today,
        Totals totals,
        List<ClientSummary> clients,
        List<InvoiceSummary> invoices) {

    /** Only the newest invoices are sent to the AI, to keep the prompt small. */
    public static final int MAX_INVOICES_IN_PROMPT = 150;

    public record Totals(
            long draftCount,
            long sentCount,
            long paidCount,
            long overdueCount,
            BigDecimal collected,
            BigDecimal pending,
            BigDecimal overdue) {

        public long totalCount() {
            return draftCount + sentCount + paidCount + overdueCount;
        }

        /** Money still to be received: sent (not yet due) plus overdue. */
        public BigDecimal outstanding() {
            return pending.add(overdue);
        }
    }

    public record ClientSummary(
            String name,
            String state,
            long invoiceCount,
            BigDecimal billed,
            BigDecimal paid,
            BigDecimal outstanding,
            BigDecimal overdue) {
    }

    public record InvoiceSummary(
            String number,
            String clientName,
            LocalDate issueDate,
            LocalDate dueDate,
            String status,
            BigDecimal total,
            long daysOverdue) {
    }

    /** A client's basic details, so clients without invoices are included too. */
    public record ClientInfo(String name, String state) {
    }

    /** An invoice as stored, before the snapshot works out overdue days. */
    public record InvoiceInfo(
            String number,
            String clientName,
            LocalDate issueDate,
            LocalDate dueDate,
            String status,
            BigDecimal total) {
    }

    /**
     * Builds the snapshot from plain invoice and client data.
     * Invoices should be newest first.
     */
    public static BusinessSnapshot build(
            String businessName,
            String businessState,
            LocalDate today,
            List<ClientInfo> clientInfos,
            List<InvoiceInfo> invoiceInfos) {

        long draft = 0;
        long sent = 0;
        long paid = 0;
        long overdueCount = 0;
        BigDecimal collected = BigDecimal.ZERO;
        BigDecimal pending = BigDecimal.ZERO;
        BigDecimal overdue = BigDecimal.ZERO;

        Map<String, MutableClient> byClient = new LinkedHashMap<>();
        for (ClientInfo info : clientInfos) {
            byClient.putIfAbsent(key(info.name()), new MutableClient(info.name(), info.state()));
        }

        List<InvoiceSummary> invoices = new ArrayList<>();
        for (InvoiceInfo inv : invoiceInfos) {
            BigDecimal total = inv.total() == null ? BigDecimal.ZERO : inv.total();
            String status = inv.status() == null ? "DRAFT" : inv.status();
            MutableClient client = byClient.computeIfAbsent(
                    key(inv.clientName()), k -> new MutableClient(inv.clientName(), null));

            long daysOverdue = 0;
            if ("OVERDUE".equals(status) && inv.dueDate() != null && inv.dueDate().isBefore(today)) {
                daysOverdue = ChronoUnit.DAYS.between(inv.dueDate(), today);
            }

            switch (status) {
                case "PAID" -> {
                    paid++;
                    collected = collected.add(total);
                    client.paid = client.paid.add(total);
                    client.billed = client.billed.add(total);
                }
                case "SENT" -> {
                    sent++;
                    pending = pending.add(total);
                    client.outstanding = client.outstanding.add(total);
                    client.billed = client.billed.add(total);
                }
                case "OVERDUE" -> {
                    overdueCount++;
                    overdue = overdue.add(total);
                    client.outstanding = client.outstanding.add(total);
                    client.overdue = client.overdue.add(total);
                    client.billed = client.billed.add(total);
                }
                default -> draft++;
            }
            client.invoiceCount++;

            invoices.add(new InvoiceSummary(
                    inv.number(), inv.clientName(), inv.issueDate(), inv.dueDate(), status, total, daysOverdue));
        }

        List<ClientSummary> clients = byClient.values().stream()
                .map(c -> new ClientSummary(c.name, c.state, c.invoiceCount, c.billed, c.paid, c.outstanding, c.overdue))
                .sorted(Comparator.comparing(ClientSummary::billed).reversed()
                        .thenComparing(ClientSummary::name, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)))
                .toList();

        Totals totals = new Totals(draft, sent, paid, overdueCount, collected, pending, overdue);
        return new BusinessSnapshot(businessName, businessState, today, totals, clients, List.copyOf(invoices));
    }

    /** The data block given to the AI. Plain text keeps it short and easy for the model to read. */
    public String toPromptText() {
        StringBuilder sb = new StringBuilder();
        sb.append("Business: ").append(orDash(businessName))
                .append(" (state: ").append(orDash(businessState)).append(")\n");
        sb.append("Today: ").append(today).append(". All amounts are in Indian Rupees and include GST.\n\n");

        sb.append("TOTALS\n");
        sb.append("- Collected (paid invoices): ").append(formatInr(totals.collected()))
                .append(" from ").append(totals.paidCount()).append(" invoices\n");
        sb.append("- Pending (sent, not yet due): ").append(formatInr(totals.pending()))
                .append(" from ").append(totals.sentCount()).append(" invoices\n");
        sb.append("- Overdue (past due date, unpaid): ").append(formatInr(totals.overdue()))
                .append(" from ").append(totals.overdueCount()).append(" invoices\n");
        sb.append("- Total outstanding (pending + overdue): ").append(formatInr(totals.outstanding())).append('\n');
        sb.append("- Drafts (not sent, nobody owes this yet): ").append(totals.draftCount()).append('\n');
        sb.append("- Clients: ").append(clients.size()).append(", invoices: ").append(totals.totalCount()).append("\n\n");

        sb.append("CLIENTS (name | state | invoices | billed | paid | outstanding | overdue)\n");
        if (clients.isEmpty()) {
            sb.append("- none\n");
        }
        for (ClientSummary c : clients) {
            sb.append("- ").append(orDash(c.name())).append(" | ").append(orDash(c.state()))
                    .append(" | ").append(c.invoiceCount())
                    .append(" | ").append(formatInr(c.billed()))
                    .append(" | ").append(formatInr(c.paid()))
                    .append(" | ").append(formatInr(c.outstanding()))
                    .append(" | ").append(formatInr(c.overdue())).append('\n');
        }

        sb.append("\nINVOICES, newest first (number | client | issued | due | status | total | days overdue)\n");
        if (invoices.isEmpty()) {
            sb.append("- none\n");
        }
        invoices.stream().limit(MAX_INVOICES_IN_PROMPT).forEach(i -> sb.append("- ")
                .append(i.number()).append(" | ").append(orDash(i.clientName()))
                .append(" | ").append(orDash(i.issueDate())).append(" | ").append(orDash(i.dueDate()))
                .append(" | ").append(i.status()).append(" | ").append(formatInr(i.total()))
                .append(" | ").append(i.daysOverdue()).append('\n'));
        if (invoices.size() > MAX_INVOICES_IN_PROMPT) {
            sb.append("(").append(invoices.size() - MAX_INVOICES_IN_PROMPT)
                    .append(" older invoices not listed; the totals above include them)\n");
        }
        return sb.toString();
    }

    /** Formats an amount the Indian way, e.g. 188800 -> "₹1,88,800.00". */
    public static String formatInr(BigDecimal amount) {
        BigDecimal value = (amount == null ? BigDecimal.ZERO : amount).setScale(2, RoundingMode.HALF_UP);
        boolean negative = value.signum() < 0;
        String plain = value.abs().toPlainString();
        String whole = plain.substring(0, plain.indexOf('.'));
        String paise = plain.substring(plain.indexOf('.'));

        StringBuilder grouped = new StringBuilder();
        if (whole.length() <= 3) {
            grouped.append(whole);
        } else {
            String lastThree = whole.substring(whole.length() - 3);
            String rest = whole.substring(0, whole.length() - 3);
            StringBuilder restGrouped = new StringBuilder();
            for (int i = rest.length(); i > 0; i -= 2) {
                String part = rest.substring(Math.max(0, i - 2), i);
                restGrouped.insert(0, restGrouped.length() == 0 ? part : part + ",");
            }
            grouped.append(restGrouped).append(',').append(lastThree);
        }
        return (negative ? "-₹" : "₹") + grouped + paise;
    }

    private static String key(String name) {
        return name == null ? "" : name.trim().toLowerCase(Locale.ROOT);
    }

    private static String orDash(Object value) {
        return value == null || value.toString().isBlank() ? "-" : value.toString();
    }

    private static final class MutableClient {
        private final String name;
        private final String state;
        private long invoiceCount;
        private BigDecimal billed = BigDecimal.ZERO;
        private BigDecimal paid = BigDecimal.ZERO;
        private BigDecimal outstanding = BigDecimal.ZERO;
        private BigDecimal overdue = BigDecimal.ZERO;

        private MutableClient(String name, String state) {
            this.name = name;
            this.state = state;
        }
    }
}
