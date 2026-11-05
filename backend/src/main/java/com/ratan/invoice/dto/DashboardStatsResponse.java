package com.ratan.invoice.dto;

import java.math.BigDecimal;
import java.util.List;

public record DashboardStatsResponse(
        long totalInvoices,
        BigDecimal totalRevenue,
        BigDecimal pendingRevenue,
        BigDecimal overdueRevenue,
        long draftCount,
        long sentCount,
        long paidCount,
        long overdueCount,
        long totalClients,
        List<InvoiceResponse> recentInvoices,
        List<MonthlyRevenueDto> monthlyTrends
) {
    public record MonthlyRevenueDto(
            String month,
            BigDecimal revenue,
            long count
    ) {
    }
}
