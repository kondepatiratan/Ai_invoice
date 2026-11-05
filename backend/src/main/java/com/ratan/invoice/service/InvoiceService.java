package com.ratan.invoice.service;

import com.ratan.invoice.dto.ClientResponse;
import com.ratan.invoice.dto.InvoiceItemRequest;
import com.ratan.invoice.dto.InvoiceItemResponse;
import com.ratan.invoice.dto.InvoiceRequest;
import com.ratan.invoice.dto.InvoiceResponse;
import com.ratan.invoice.entity.Client;
import com.ratan.invoice.entity.Invoice;
import com.ratan.invoice.entity.InvoiceItem;
import com.ratan.invoice.entity.InvoiceStatus;
import com.ratan.invoice.entity.User;
import com.ratan.invoice.exception.InvalidRequestException;
import com.ratan.invoice.exception.ResourceNotFoundException;
import com.ratan.invoice.repository.ClientRepository;
import com.ratan.invoice.repository.InvoiceRepository;
import com.ratan.invoice.repository.UserRepository;
import com.ratan.invoice.security.SecurityUtils;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InvoiceService {

    private final InvoiceRepository invoiceRepository;
    private final ClientRepository clientRepository;
    private final UserRepository userRepository;
    private final GstCalculator gstCalculator;

    public InvoiceService(
            InvoiceRepository invoiceRepository,
            ClientRepository clientRepository,
            UserRepository userRepository,
            GstCalculator gstCalculator) {
        this.invoiceRepository = invoiceRepository;
        this.clientRepository = clientRepository;
        this.userRepository = userRepository;
        this.gstCalculator = gstCalculator;
    }

    @Transactional
    public InvoiceResponse create(InvoiceRequest request) {
        User owner = lockCurrentOwner();
        Client client = findOwnedClient(request.clientId(), owner.getId());
        Invoice invoice = new Invoice();
        invoice.setUser(owner);
        invoice.setClient(client);
        owner.setInvoiceSequence(owner.getInvoiceSequence() + 1);
        invoice.setInvoiceNumber(String.format(Locale.ROOT, "INV-%04d", owner.getInvoiceSequence()));
        invoice.setStatus(InvoiceStatus.DRAFT);
        apply(invoice, request, owner, client);
        return toResponse(invoiceRepository.save(invoice));
    }

    @Transactional
    public List<InvoiceResponse> list(InvoiceStatus status) {
        Long userId = SecurityUtils.getCurrentUser().getId();
        List<Invoice> invoices = invoiceRepository.findAllByUserIdOrderByCreatedAtDescIdDesc(userId);
        updateFetchedOverdueInvoices(invoices);
        return invoices.stream()
                .filter(invoice -> status == null || invoice.getStatus() == status)
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public InvoiceResponse get(Long id) {
        Invoice invoice = findOwned(id);
        updateOverdue(invoice);
        return toResponse(invoice);
    }

    @Transactional
    public InvoiceResponse update(Long id, InvoiceRequest request) {
        Invoice invoice = findOwned(id);
        if (invoice.getStatus() != InvoiceStatus.DRAFT) {
            throw new InvalidRequestException("Only DRAFT invoices can be updated");
        }
        Client client = findOwnedClient(request.clientId(), invoice.getUser().getId());
        apply(invoice, request, invoice.getUser(), client);
        return toResponse(invoiceRepository.save(invoice));
    }

    @Transactional
    public InvoiceResponse updateStatus(Long id, InvoiceStatus newStatus) {
        Invoice invoice = findOwned(id);
        updateOverdue(invoice);
        InvoiceStatus currentStatus = invoice.getStatus();
        boolean allowed = currentStatus == InvoiceStatus.DRAFT && newStatus == InvoiceStatus.SENT
                || currentStatus == InvoiceStatus.SENT && newStatus == InvoiceStatus.PAID
                || currentStatus == InvoiceStatus.OVERDUE && newStatus == InvoiceStatus.PAID;
        if (!allowed) {
            throw new InvalidRequestException(
                    "Invalid invoice status transition from " + currentStatus + " to " + newStatus);
        }
        invoice.setStatus(newStatus);
        return toResponse(invoiceRepository.save(invoice));
    }

    @Transactional
    public void delete(Long id) {
        Invoice invoice = findOwned(id);
        if (invoice.getStatus() != InvoiceStatus.DRAFT) {
            throw new InvalidRequestException("Only DRAFT invoices can be deleted");
        }
        invoiceRepository.delete(invoice);
    }

    @Scheduled(cron = "0 0 0 * * *")
    @Transactional
    public void markOverdueInvoices() {
        invoiceRepository.markSentInvoicesOverdue(LocalDate.now());
    }

    private void apply(Invoice invoice, InvoiceRequest request, User owner, Client client) {
        invoice.setClient(client);
        invoice.setIssueDate(request.issueDate());
        invoice.setDueDate(request.dueDate());
        invoice.setNotes(request.notes());
        invoice.getItems().clear();

        GstCalculator.Calculation calculation =
                gstCalculator.calculate(request.items(), owner.getState(), client.getState());
        for (int i = 0; i < request.items().size(); i++) {
            InvoiceItemRequest requestItem = request.items().get(i);
            InvoiceItem item = new InvoiceItem();
            item.setInvoice(invoice);
            item.setDescription(requestItem.description().trim());
            item.setQuantity(requestItem.quantity());
            item.setRate(requestItem.rate());
            item.setGstRate(requestItem.gstRate());
            item.setAmount(calculation.items().get(i).amount());
            invoice.getItems().add(item);
        }
        invoice.setSubtotal(calculation.subtotal());
        invoice.setCgst(calculation.cgst());
        invoice.setSgst(calculation.sgst());
        invoice.setIgst(calculation.igst());
        invoice.setTotalAmount(calculation.totalAmount());
    }

    private User lockCurrentOwner() {
        Long userId = SecurityUtils.getCurrentUser().getId();
        return userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }

    private Client findOwnedClient(Long clientId, Long userId) {
        return clientRepository.findByIdAndUserId(clientId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Client not found"));
    }

    private Invoice findOwned(Long invoiceId) {
        Long userId = SecurityUtils.getCurrentUser().getId();
        return invoiceRepository.findByIdAndUserId(invoiceId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Invoice not found"));
    }

    private void updateFetchedOverdueInvoices(List<Invoice> invoices) {
        LocalDate today = LocalDate.now();
        invoices.stream()
                .filter(invoice -> invoice.getStatus() == InvoiceStatus.SENT
                        && invoice.getDueDate() != null
                        && invoice.getDueDate().isBefore(today))
                .forEach(invoice -> invoice.setStatus(InvoiceStatus.OVERDUE));
    }

    private void updateOverdue(Invoice invoice) {
        if (invoice.getStatus() == InvoiceStatus.SENT
                && invoice.getDueDate() != null
                && invoice.getDueDate().isBefore(LocalDate.now())) {
            invoice.setStatus(InvoiceStatus.OVERDUE);
        }
    }

    private InvoiceResponse toResponse(Invoice invoice) {
        Client client = invoice.getClient();
        ClientResponse clientResponse = new ClientResponse(
                client.getId(),
                client.getName(),
                client.getEmail(),
                client.getPhone(),
                client.getGstin(),
                client.getState(),
                client.getAddress(),
                client.getCreatedAt());
        List<InvoiceItemResponse> items = invoice.getItems().stream()
                .map(item -> new InvoiceItemResponse(
                        item.getId(),
                        item.getDescription(),
                        item.getQuantity(),
                        item.getRate(),
                        item.getGstRate(),
                        item.getAmount()))
                .toList();
        return new InvoiceResponse(
                invoice.getId(),
                invoice.getInvoiceNumber(),
                invoice.getIssueDate(),
                invoice.getDueDate(),
                invoice.getStatus(),
                invoice.getSubtotal(),
                invoice.getCgst(),
                invoice.getSgst(),
                invoice.getIgst(),
                invoice.getTotalAmount(),
                invoice.getNotes(),
                invoice.getCreatedAt(),
                clientResponse,
                items);
    }
}
