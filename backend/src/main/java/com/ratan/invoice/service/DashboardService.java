package com.ratan.invoice.service;

import com.ratan.invoice.dto.DashboardStatsResponse;
import com.ratan.invoice.dto.InvoiceResponse;
import com.ratan.invoice.entity.Invoice;
import com.ratan.invoice.entity.InvoiceStatus;
import com.ratan.invoice.repository.ClientRepository;
import com.ratan.invoice.repository.InvoiceRepository;
import com.ratan.invoice.security.SecurityUtils;
import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DashboardService {

    private final InvoiceRepository invoiceRepository;
    private final ClientRepository clientRepository;
    private final InvoiceService invoiceService;

    public DashboardService(
            InvoiceRepository invoiceRepository,
            ClientRepository clientRepository,
            InvoiceService invoiceService) {
        this.invoiceRepository = invoiceRepository;
        this.clientRepository = clientRepository;
        this.invoiceService = invoiceService;
    }

    @Transactional(readOnly = true)
    public DashboardStatsResponse getStats() {
        Long userId = SecurityUtils.getCurrentUser().getId();
        List<Invoice> invoices = invoiceRepository.findAllByUserIdOrderByCreatedAtDescIdDesc(userId);
        long totalClients = clientRepository.findAllByUserIdOrderByNameAsc(userId).size();

        long totalInvoices = invoices.size();
        BigDecimal totalRevenue = BigDecimal.ZERO;
        BigDecimal pendingRevenue = BigDecimal.ZERO;
        BigDecimal overdueRevenue = BigDecimal.ZERO;

        long draftCount = 0;
        long sentCount = 0;
        long paidCount = 0;
        long overdueCount = 0;

        Map<String, DashboardStatsResponse.MonthlyRevenueDto> monthlyMap = new LinkedHashMap<>();
        DateTimeFormatter monthFmt = DateTimeFormatter.ofPattern("MMM yyyy");

        for (Invoice inv : invoices) {
            BigDecimal total = inv.getTotalAmount() != null ? inv.getTotalAmount() : BigDecimal.ZERO;
            InvoiceStatus status = inv.getStatus();

            switch (status) {
                case PAID -> {
                    paidCount++;
                    totalRevenue = totalRevenue.add(total);
                }
                case SENT -> {
                    sentCount++;
                    pendingRevenue = pendingRevenue.add(total);
                }
                case OVERDUE -> {
                    overdueCount++;
                    overdueRevenue = overdueRevenue.add(total);
                }
                case DRAFT -> draftCount++;
            }

            if (inv.getIssueDate() != null) {
                String mKey = inv.getIssueDate().format(monthFmt);
                DashboardStatsResponse.MonthlyRevenueDto existing = monthlyMap.get(mKey);
                if (existing == null) {
                    monthlyMap.put(mKey, new DashboardStatsResponse.MonthlyRevenueDto(mKey, total, 1));
                } else {
                    monthlyMap.put(mKey, new DashboardStatsResponse.MonthlyRevenueDto(
                            mKey, existing.revenue().add(total), existing.count() + 1));
                }
            }
        }

        List<InvoiceResponse> allInvoices = invoiceService.list(null);
        List<InvoiceResponse> recentInvoices = allInvoices.stream().limit(5).toList();

        List<DashboardStatsResponse.MonthlyRevenueDto> monthlyTrends = new ArrayList<>(monthlyMap.values());
        Collections.reverse(monthlyTrends);

        return new DashboardStatsResponse(
                totalInvoices,
                totalRevenue,
                pendingRevenue,
                overdueRevenue,
                draftCount,
                sentCount,
                paidCount,
                overdueCount,
                totalClients,
                recentInvoices,
                monthlyTrends
        );
    }
}
