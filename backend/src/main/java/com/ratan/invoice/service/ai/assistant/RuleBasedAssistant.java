package com.ratan.invoice.service.ai.assistant;

import static com.ratan.invoice.service.ai.assistant.BusinessSnapshot.formatInr;

import com.ratan.invoice.service.ai.assistant.BusinessSnapshot.ClientSummary;
import com.ratan.invoice.service.ai.assistant.BusinessSnapshot.InvoiceSummary;
import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Answers common questions from the snapshot without any AI.
 * Used when no AI provider is configured or every provider fails, so the assistant always answers.
 */
public final class RuleBasedAssistant {

    private static final int MAX_LIST = 5;

    private RuleBasedAssistant() {
    }

    public static String answer(String question, BusinessSnapshot data) {
        String q = question == null ? "" : question.toLowerCase(Locale.ROOT);

        if (data.totals().totalCount() == 0) {
            return "You don't have any invoices yet. Add a client and create your first invoice, "
                    + "and I'll be able to answer questions about payments and clients.";
        }

        Optional<ClientSummary> client = findMentionedClient(q, data.clients());
        if (client.isPresent()) {
            return describeClient(client.get());
        }
        if (containsAny(q, "overdue", "late", "past due", "delayed")) {
            return describeOverdue(data);
        }
        if (containsAny(q, "owe", "outstanding", "pending", "receivable", "unpaid", "due")) {
            return describeOutstanding(data);
        }
        if (containsAny(q, "top", "best", "biggest", "largest", "most")) {
            return describeTopClients(data);
        }
        if (containsAny(q, "draft")) {
            return describeDrafts(data);
        }
        if (containsAny(q, "revenue", "earn", "received", "collected", "paid", "income", "made")) {
            return describeRevenue(data);
        }
        if (containsAny(q, "how many", "count", "number of", "summary", "overview", "status")) {
            return describeSummary(data);
        }
        return "I can answer questions about your invoices and clients, for example:\n"
                + "- Which invoices are overdue?\n"
                + "- How much money is still outstanding?\n"
                + "- Who are my top clients?\n"
                + "- How much does a specific client owe me?\n"
                + "- How much have I collected so far?";
    }

    private static Optional<ClientSummary> findMentionedClient(String q, List<ClientSummary> clients) {
        // Prefer the longest matching name, so "Infosys BPM" wins over "Infosys"
        return clients.stream()
                .filter(c -> c.name() != null && !c.name().isBlank())
                .filter(c -> q.contains(c.name().toLowerCase(Locale.ROOT)))
                .max(Comparator.comparingInt(c -> c.name().length()));
    }

    private static String describeClient(ClientSummary c) {
        if (c.invoiceCount() == 0) {
            return c.name() + " has no invoices yet.";
        }
        StringBuilder sb = new StringBuilder();
        sb.append(c.name()).append(" has ").append(c.invoiceCount())
                .append(c.invoiceCount() == 1 ? " invoice" : " invoices")
                .append(". Billed: ").append(formatInr(c.billed()))
                .append(", paid: ").append(formatInr(c.paid()))
                .append(", still outstanding: ").append(formatInr(c.outstanding())).append('.');
        if (c.overdue().signum() > 0) {
            sb.append(" Of that, ").append(formatInr(c.overdue())).append(" is overdue, so a reminder would help.");
        }
        return sb.toString();
    }

    private static String describeOverdue(BusinessSnapshot data) {
        List<InvoiceSummary> overdue = data.invoices().stream()
                .filter(i -> "OVERDUE".equals(i.status()))
                .sorted(Comparator.comparingLong(InvoiceSummary::daysOverdue).reversed())
                .toList();
        if (overdue.isEmpty()) {
            return "Good news: no invoices are overdue right now.";
        }
        StringBuilder sb = new StringBuilder();
        sb.append(overdue.size()).append(overdue.size() == 1 ? " invoice is" : " invoices are")
                .append(" overdue, totalling ").append(formatInr(data.totals().overdue())).append(":\n");
        overdue.stream().limit(MAX_LIST).forEach(i -> sb.append("- ").append(i.number())
                .append(" for ").append(i.clientName()).append(": ").append(formatInr(i.total()))
                .append(", ").append(i.daysOverdue()).append(i.daysOverdue() == 1 ? " day" : " days").append(" late\n"));
        sb.append("You can send a reminder from the Invoices page: open the menu on the invoice and choose Generate AI Reminder.");
        return sb.toString();
    }

    private static String describeOutstanding(BusinessSnapshot data) {
        BigDecimal outstanding = data.totals().outstanding();
        if (outstanding.signum() == 0) {
            return "Nothing is outstanding: every sent invoice has been paid.";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("Clients owe you ").append(formatInr(outstanding)).append(" in total: ")
                .append(formatInr(data.totals().pending())).append(" not yet due and ")
                .append(formatInr(data.totals().overdue())).append(" overdue.\n");
        data.clients().stream()
                .filter(c -> c.outstanding().signum() > 0)
                .sorted(Comparator.comparing(ClientSummary::outstanding).reversed())
                .limit(MAX_LIST)
                .forEach(c -> sb.append("- ").append(c.name()).append(": ").append(formatInr(c.outstanding()))
                        .append(c.overdue().signum() > 0 ? " (" + formatInr(c.overdue()) + " overdue)" : "")
                        .append('\n'));
        return sb.toString().trim();
    }

    private static String describeTopClients(BusinessSnapshot data) {
        List<ClientSummary> top = data.clients().stream()
                .filter(c -> c.billed().signum() > 0)
                .limit(MAX_LIST)
                .toList();
        if (top.isEmpty()) {
            return "None of your invoices have been sent yet, so there's no billing per client to compare.";
        }
        StringBuilder sb = new StringBuilder("Your top clients by amount billed (sent and paid invoices):\n");
        for (int i = 0; i < top.size(); i++) {
            ClientSummary c = top.get(i);
            sb.append(i + 1).append(". ").append(c.name()).append(": ").append(formatInr(c.billed()))
                    .append(" billed, ").append(formatInr(c.paid())).append(" paid\n");
        }
        return sb.toString().trim();
    }

    private static String describeDrafts(BusinessSnapshot data) {
        List<InvoiceSummary> drafts = data.invoices().stream()
                .filter(i -> "DRAFT".equals(i.status()))
                .toList();
        if (drafts.isEmpty()) {
            return "You have no draft invoices.";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("You have ").append(drafts.size()).append(drafts.size() == 1 ? " draft" : " drafts")
                .append(" waiting to be sent:\n");
        drafts.stream().limit(MAX_LIST).forEach(i -> sb.append("- ").append(i.number())
                .append(" for ").append(i.clientName()).append(": ").append(formatInr(i.total())).append('\n'));
        return sb.toString().trim();
    }

    private static String describeRevenue(BusinessSnapshot data) {
        return "You've collected " + formatInr(data.totals().collected()) + " from "
                + data.totals().paidCount() + (data.totals().paidCount() == 1 ? " paid invoice" : " paid invoices")
                + ". Another " + formatInr(data.totals().outstanding()) + " is still to be received.";
    }

    private static String describeSummary(BusinessSnapshot data) {
        BusinessSnapshot.Totals t = data.totals();
        return "You have " + t.totalCount() + " invoices for " + data.clients().size() + " clients: "
                + t.paidCount() + " paid, " + t.sentCount() + " sent, " + t.overdueCount() + " overdue and "
                + t.draftCount() + (t.draftCount() == 1 ? " draft" : " drafts") + ". Collected " + formatInr(t.collected())
                + ", outstanding " + formatInr(t.outstanding()) + ".";
    }

    private static boolean containsAny(String text, String... words) {
        for (String word : words) {
            if (text.contains(word)) {
                return true;
            }
        }
        return false;
    }
}
