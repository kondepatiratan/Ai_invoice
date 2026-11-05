package com.ratan.invoice.service;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import com.ratan.invoice.entity.Client;
import com.ratan.invoice.entity.Invoice;
import com.ratan.invoice.entity.InvoiceItem;
import com.ratan.invoice.entity.InvoiceStatus;
import com.ratan.invoice.entity.User;
import com.ratan.invoice.repository.InvoiceRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PdfServiceTest {

    @Mock
    private InvoiceRepository invoiceRepository;

    @InjectMocks
    private PdfService pdfService;

    @Test
    void generatesValidPdfByteArrayForInvoice() {
        User user = new User();
        user.setId(1L);
        user.setName("Ratan Kondepati");
        user.setEmail("ratan@test.com");
        user.setBusinessName("Test Solutions");
        user.setState("Karnataka");

        Client client = new Client();
        client.setId(2L);
        client.setName("Client Alpha");
        client.setEmail("alpha@test.com");
        client.setState("Karnataka");

        Invoice invoice = new Invoice();
        invoice.setId(10L);
        invoice.setInvoiceNumber("INV-0001");
        invoice.setStatus(InvoiceStatus.PAID);
        invoice.setIssueDate(LocalDate.now());
        invoice.setDueDate(LocalDate.now().plusDays(15));
        invoice.setUser(user);
        invoice.setClient(client);
        invoice.setSubtotal(new BigDecimal("1000.00"));
        invoice.setCgst(new BigDecimal("90.00"));
        invoice.setSgst(new BigDecimal("90.00"));
        invoice.setTotalAmount(new BigDecimal("1180.00"));

        InvoiceItem item = new InvoiceItem();
        item.setDescription("Cloud Architecture Consulting");
        item.setQuantity(BigDecimal.ONE);
        item.setRate(new BigDecimal("1000.00"));
        item.setGstRate(new BigDecimal("18.00"));
        item.setAmount(new BigDecimal("1000.00"));
        invoice.getItems().add(item);

        when(invoiceRepository.findByIdAndUserId(10L, 1L)).thenReturn(Optional.of(invoice));

        byte[] pdfBytes = pdfService.generatePdf(10L, 1L);

        assertNotNull(pdfBytes);
        assertTrue(pdfBytes.length > 500, "PDF byte array should contain generated content");
        String header = new String(pdfBytes, 0, 8);
        assertTrue(header.startsWith("%PDF-"), "Generated file must start with %PDF header");
    }
}
