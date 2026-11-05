package com.ratan.invoice.service;

import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.ratan.invoice.entity.Client;
import com.ratan.invoice.entity.Invoice;
import com.ratan.invoice.entity.InvoiceItem;
import com.ratan.invoice.entity.User;
import com.ratan.invoice.exception.ResourceNotFoundException;
import com.ratan.invoice.repository.InvoiceRepository;
import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PdfService {

    private final InvoiceRepository invoiceRepository;

    public PdfService(InvoiceRepository invoiceRepository) {
        this.invoiceRepository = invoiceRepository;
    }

    @Transactional(readOnly = true)
    public byte[] generatePdf(Long invoiceId, Long userId) {
        Invoice invoice = invoiceRepository.findByIdAndUserId(invoiceId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Invoice not found"));

        User user = invoice.getUser();
        Client client = invoice.getClient();

        Document document = new Document(PageSize.A4, 36, 36, 36, 36);
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        try {
            PdfWriter.getInstance(document, out);
            document.open();

            // Color Palette
            Color primaryColor = new Color(24, 76, 120);      // Deep Slate Blue
            Color secondaryColor = new Color(100, 116, 139);  // Slate Gray
            Color tableHeaderBg = new Color(241, 245, 249);   // Light Grayish Blue
            Color totalBg = new Color(236, 242, 255);         // Subtle Blue

            // Fonts
            Font brandFont = new Font(Font.HELVETICA, 18, Font.BOLD, primaryColor);
            Font titleFont = new Font(Font.HELVETICA, 20, Font.BOLD, primaryColor);
            Font sectionTitleFont = new Font(Font.HELVETICA, 10, Font.BOLD, primaryColor);
            Font bodyFont = new Font(Font.HELVETICA, 9, Font.NORMAL, Color.DARK_GRAY);
            Font bodyBoldFont = new Font(Font.HELVETICA, 9, Font.BOLD, Color.BLACK);
            Font headerCellFont = new Font(Font.HELVETICA, 9, Font.BOLD, primaryColor);
            Font grandTotalFont = new Font(Font.HELVETICA, 12, Font.BOLD, primaryColor);

            DateTimeFormatter dtf = DateTimeFormatter.ofPattern("dd MMM yyyy");

            // 1. Top Header Table (Logo / Business on left, INVOICE title on right)
            PdfPTable topTable = new PdfPTable(2);
            topTable.setWidthPercentage(100);
            topTable.setWidths(new float[]{60, 40});

            // Business Info Cell
            PdfPCell businessCell = new PdfPCell();
            businessCell.setBorder(PdfPCell.NO_BORDER);
            String bizName = user.getBusinessName() != null && !user.getBusinessName().isBlank()
                    ? user.getBusinessName() : user.getName();
            businessCell.addElement(new Paragraph(bizName, brandFont));
            businessCell.addElement(new Paragraph("GSTIN: " + (user.getGstin() != null ? user.getGstin() : "N/A"), bodyFont));
            businessCell.addElement(new Paragraph("State: " + (user.getState() != null ? user.getState() : "N/A"), bodyFont));
            businessCell.addElement(new Paragraph("Email: " + user.getEmail(), bodyFont));
            topTable.addCell(businessCell);

            // Invoice Title & Meta Cell
            PdfPCell metaCell = new PdfPCell();
            metaCell.setBorder(PdfPCell.NO_BORDER);
            metaCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
            Paragraph invTitle = new Paragraph("TAX INVOICE", titleFont);
            invTitle.setAlignment(Element.ALIGN_RIGHT);
            metaCell.addElement(invTitle);

            Paragraph invNum = new Paragraph(invoice.getInvoiceNumber(), new Font(Font.HELVETICA, 12, Font.BOLD, secondaryColor));
            invNum.setAlignment(Element.ALIGN_RIGHT);
            metaCell.addElement(invNum);

            Paragraph statusPara = new Paragraph("STATUS: " + invoice.getStatus().name(),
                    new Font(Font.HELVETICA, 10, Font.BOLD, getStatusColor(invoice.getStatus().name())));
            statusPara.setAlignment(Element.ALIGN_RIGHT);
            metaCell.addElement(statusPara);

            topTable.addCell(metaCell);
            document.add(topTable);

            document.add(new Paragraph(" "));

            // 2. Client & Dates Table
            PdfPTable clientDatesTable = new PdfPTable(2);
            clientDatesTable.setWidthPercentage(100);
            clientDatesTable.setWidths(new float[]{55, 45});

            // Bill To
            PdfPCell billToCell = new PdfPCell();
            billToCell.setBorder(PdfPCell.BOX);
            billToCell.setBorderColor(new Color(226, 232, 240));
            billToCell.setPadding(10);
            billToCell.setBackgroundColor(new Color(248, 250, 252));

            billToCell.addElement(new Paragraph("BILLED TO:", sectionTitleFont));
            billToCell.addElement(new Paragraph(client.getName(), bodyBoldFont));
            if (client.getEmail() != null) billToCell.addElement(new Paragraph("Email: " + client.getEmail(), bodyFont));
            if (client.getPhone() != null) billToCell.addElement(new Paragraph("Phone: " + client.getPhone(), bodyFont));
            if (client.getGstin() != null) billToCell.addElement(new Paragraph("GSTIN: " + client.getGstin(), bodyFont));
            if (client.getState() != null) billToCell.addElement(new Paragraph("State: " + client.getState(), bodyFont));
            if (client.getAddress() != null) billToCell.addElement(new Paragraph("Address: " + client.getAddress(), bodyFont));

            clientDatesTable.addCell(billToCell);

            // Invoice Dates & Place of Supply
            PdfPCell datesCell = new PdfPCell();
            datesCell.setBorder(PdfPCell.BOX);
            datesCell.setBorderColor(new Color(226, 232, 240));
            datesCell.setPadding(10);
            datesCell.setBackgroundColor(new Color(248, 250, 252));

            datesCell.addElement(new Paragraph("INVOICE DETAILS:", sectionTitleFont));
            datesCell.addElement(new Paragraph("Issue Date: " + invoice.getIssueDate().format(dtf), bodyFont));
            datesCell.addElement(new Paragraph("Due Date: " + (invoice.getDueDate() != null ? invoice.getDueDate().format(dtf) : "On Receipt"), bodyFont));
            String pos = client.getState() != null ? client.getState() : "Inter-State";
            datesCell.addElement(new Paragraph("Place of Supply: " + pos, bodyFont));

            boolean isIntraState = user.getState() != null && client.getState() != null
                    && user.getState().trim().equalsIgnoreCase(client.getState().trim());
            datesCell.addElement(new Paragraph("Tax Type: " + (isIntraState ? "Intra-State (CGST + SGST)" : "Inter-State (IGST)"), bodyFont));

            clientDatesTable.addCell(datesCell);
            document.add(clientDatesTable);

            document.add(new Paragraph(" "));

            // 3. Items Table
            PdfPTable itemsTable = new PdfPTable(6);
            itemsTable.setWidthPercentage(100);
            itemsTable.setWidths(new float[]{8, 44, 12, 12, 12, 12});

            addHeaderCell(itemsTable, "#", headerCellFont, tableHeaderBg, Element.ALIGN_CENTER);
            addHeaderCell(itemsTable, "Item & Description", headerCellFont, tableHeaderBg, Element.ALIGN_LEFT);
            addHeaderCell(itemsTable, "Qty", headerCellFont, tableHeaderBg, Element.ALIGN_RIGHT);
            addHeaderCell(itemsTable, "Rate (₹)", headerCellFont, tableHeaderBg, Element.ALIGN_RIGHT);
            addHeaderCell(itemsTable, "GST %", headerCellFont, tableHeaderBg, Element.ALIGN_RIGHT);
            addHeaderCell(itemsTable, "Amount (₹)", headerCellFont, tableHeaderBg, Element.ALIGN_RIGHT);

            int itemIndex = 1;
            for (InvoiceItem item : invoice.getItems()) {
                addBodyCell(itemsTable, String.valueOf(itemIndex++), bodyFont, Element.ALIGN_CENTER);
                addBodyCell(itemsTable, item.getDescription(), bodyFont, Element.ALIGN_LEFT);
                addBodyCell(itemsTable, item.getQuantity().stripTrailingZeros().toPlainString(), bodyFont, Element.ALIGN_RIGHT);
                addBodyCell(itemsTable, formatCurrency(item.getRate()), bodyFont, Element.ALIGN_RIGHT);
                addBodyCell(itemsTable, item.getGstRate().stripTrailingZeros().toPlainString() + "%", bodyFont, Element.ALIGN_RIGHT);
                addBodyCell(itemsTable, formatCurrency(item.getAmount()), bodyFont, Element.ALIGN_RIGHT);
            }

            document.add(itemsTable);

            // 4. Totals Summary Table
            PdfPTable totalsTable = new PdfPTable(2);
            totalsTable.setWidthPercentage(100);
            totalsTable.setWidths(new float[]{60, 40});

            // Notes / Bank Details in left cell
            PdfPCell notesCell = new PdfPCell();
            notesCell.setBorder(PdfPCell.NO_BORDER);
            notesCell.setPadding(8);
            if (invoice.getNotes() != null && !invoice.getNotes().isBlank()) {
                notesCell.addElement(new Paragraph("Notes / Payment Terms:", sectionTitleFont));
                notesCell.addElement(new Paragraph(invoice.getNotes(), bodyFont));
            }
            totalsTable.addCell(notesCell);

            // Numbers in right cell
            PdfPCell summaryCell = new PdfPCell();
            summaryCell.setBorder(PdfPCell.NO_BORDER);
            summaryCell.setPadding(0);

            PdfPTable subTotals = new PdfPTable(2);
            subTotals.setWidthPercentage(100);
            subTotals.setWidths(new float[]{60, 40});

            addRow(subTotals, "Subtotal:", formatCurrency(invoice.getSubtotal()), bodyFont, bodyBoldFont);

            if (invoice.getCgst().compareTo(BigDecimal.ZERO) > 0) {
                addRow(subTotals, "CGST:", formatCurrency(invoice.getCgst()), bodyFont, bodyFont);
            }
            if (invoice.getSgst().compareTo(BigDecimal.ZERO) > 0) {
                addRow(subTotals, "SGST:", formatCurrency(invoice.getSgst()), bodyFont, bodyFont);
            }
            if (invoice.getIgst().compareTo(BigDecimal.ZERO) > 0) {
                addRow(subTotals, "IGST:", formatCurrency(invoice.getIgst()), bodyFont, bodyFont);
            }

            // Total Amount
            PdfPCell grandTotalLabel = new PdfPCell(new Phrase("Total Amount:", grandTotalFont));
            grandTotalLabel.setBackgroundColor(totalBg);
            grandTotalLabel.setPadding(8);
            grandTotalLabel.setBorderColor(new Color(199, 210, 254));

            PdfPCell grandTotalVal = new PdfPCell(new Phrase("₹ " + formatCurrency(invoice.getTotalAmount()), grandTotalFont));
            grandTotalVal.setBackgroundColor(totalBg);
            grandTotalVal.setHorizontalAlignment(Element.ALIGN_RIGHT);
            grandTotalVal.setPadding(8);
            grandTotalVal.setBorderColor(new Color(199, 210, 254));

            subTotals.addCell(grandTotalLabel);
            subTotals.addCell(grandTotalVal);

            summaryCell.addElement(subTotals);
            totalsTable.addCell(summaryCell);

            document.add(totalsTable);

            // 5. Footer
            document.add(new Paragraph(" "));
            Paragraph footer = new Paragraph("Thank you for your business! • This is a computer generated invoice powered by AI Invoice Generator",
                    new Font(Font.HELVETICA, 8, Font.ITALIC, Color.GRAY));
            footer.setAlignment(Element.ALIGN_CENTER);
            document.add(footer);

            document.close();
            return out.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Error generating invoice PDF", e);
        }
    }

    private static void addHeaderCell(PdfPTable table, String text, Font font, Color bg, int align) {
        PdfPCell cell = new PdfPCell(new Phrase(text, font));
        cell.setBackgroundColor(bg);
        cell.setPadding(6);
        cell.setHorizontalAlignment(align);
        cell.setBorderColor(new Color(203, 213, 225));
        table.addCell(cell);
    }

    private static void addBodyCell(PdfPTable table, String text, Font font, int align) {
        PdfPCell cell = new PdfPCell(new Phrase(text, font));
        cell.setPadding(6);
        cell.setHorizontalAlignment(align);
        cell.setBorderColor(new Color(226, 232, 240));
        table.addCell(cell);
    }

    private static void addRow(PdfPTable table, String label, String value, Font labelFont, Font valFont) {
        PdfPCell lCell = new PdfPCell(new Phrase(label, labelFont));
        lCell.setPadding(4);
        lCell.setBorder(PdfPCell.NO_BORDER);
        PdfPCell vCell = new PdfPCell(new Phrase("₹ " + value, valFont));
        vCell.setPadding(4);
        vCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        vCell.setBorder(PdfPCell.NO_BORDER);
        table.addCell(lCell);
        table.addCell(vCell);
    }

    private static String formatCurrency(BigDecimal amount) {
        return amount != null ? amount.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString() : "0.00";
    }

    private static Color getStatusColor(String status) {
        return switch (status) {
            case "PAID" -> new Color(22, 101, 52);       // Green
            case "OVERDUE" -> new Color(185, 28, 28);    // Red
            case "SENT" -> new Color(29, 78, 216);       // Blue
            default -> new Color(71, 85, 105);          // Slate Gray (Draft)
        };
    }
}
