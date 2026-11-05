package com.ratan.invoice.service;

import com.ratan.invoice.entity.Client;
import com.ratan.invoice.entity.Invoice;
import com.ratan.invoice.entity.InvoiceItem;
import com.ratan.invoice.entity.InvoiceStatus;
import com.ratan.invoice.entity.User;
import com.ratan.invoice.repository.ClientRepository;
import com.ratan.invoice.repository.InvoiceRepository;
import com.ratan.invoice.repository.UserRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class DataInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    private final UserRepository userRepository;
    private final ClientRepository clientRepository;
    private final InvoiceRepository invoiceRepository;
    private final PasswordEncoder passwordEncoder;
    private final GstCalculator gstCalculator;

    public DataInitializer(
            UserRepository userRepository,
            ClientRepository clientRepository,
            InvoiceRepository invoiceRepository,
            PasswordEncoder passwordEncoder,
            GstCalculator gstCalculator) {
        this.userRepository = userRepository;
        this.clientRepository = clientRepository;
        this.invoiceRepository = invoiceRepository;
        this.passwordEncoder = passwordEncoder;
        this.gstCalculator = gstCalculator;
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (userRepository.count() == 0) {
            log.info("No users found. Seeding initial demo user, clients, and invoices for Nivuna Labs presentation...");
            seedDemoData();
        }
    }

    @Transactional
    public void seedDemoData() {
        if (userRepository.existsByEmail("demo@invoice.ai")) {
            log.info("Demo user already exists.");
            return;
        }

        // 1. Create Demo User
        User user = new User();
        user.setName("Ratan Kondepati");
        user.setEmail("demo@invoice.ai");
        user.setPassword(passwordEncoder.encode("password123"));
        user.setBusinessName("Nivuna Cloud Innovations Pvt Ltd");
        user.setGstin("29ABCDE1234F1Z5");
        user.setState("Karnataka");
        user.setInvoiceSequence(4);
        user = userRepository.save(user);

        // 2. Create Clients
        Client c1 = new Client();
        c1.setUser(user);
        c1.setName("Acme Global Technologies");
        c1.setEmail("accounts@acmeglobal.com");
        c1.setPhone("+91 98450 11223");
        c1.setState("Karnataka");
        c1.setGstin("29AAACG1234F1Z1");
        c1.setAddress("Level 4, Brigade Gateway, Rajajinagar, Bengaluru");
        c1 = clientRepository.save(c1);

        Client c2 = new Client();
        c2.setUser(user);
        c2.setName("TechNova Labs Ltd");
        c2.setEmail("finance@technovalabs.io");
        c2.setPhone("+91 97310 99887");
        c2.setState("Maharashtra");
        c2.setGstin("27AAACT5678M1Z2");
        c2.setAddress("Tower B, BKC Complex, Bandra East, Mumbai");
        c2 = clientRepository.save(c2);

        Client c3 = new Client();
        c3.setUser(user);
        c3.setName("Zenith Digital Media");
        c3.setEmail("billing@zenithmedia.in");
        c3.setPhone("+91 99100 44556");
        c3.setState("Delhi");
        c3.setGstin("07AAACZ9012D1Z3");
        c3.setAddress("Barakhamba Road, Connaught Place, New Delhi");
        c3 = clientRepository.save(c3);

        LocalDate today = LocalDate.now();

        // 3. Create Sample Invoices
        // Invoice 1: PAID (Intra-state, CGST+SGST)
        createInvoice(user, c1, "INV-0001", InvoiceStatus.PAID,
                today.minusDays(25), today.minusDays(10),
                "Payment received via NEFT Ref #TXN9872134.",
                List.of(
                        new ItemSeed("Enterprise Cloud Architecture Consulting", new BigDecimal("40"), new BigDecimal("3000.00"), new BigDecimal("18.00")),
                        new ItemSeed("Security Audit & Pen Testing Report", new BigDecimal("1"), new BigDecimal("45000.00"), new BigDecimal("18.00"))
                ));

        // Invoice 2: SENT (Inter-state, IGST)
        createInvoice(user, c2, "INV-0002", InvoiceStatus.SENT,
                today.minusDays(10), today.plusDays(15),
                "Payment due within 15 days from issue date.",
                List.of(
                        new ItemSeed("Full-Stack Spring Boot & React Dashboard", new BigDecimal("60"), new BigDecimal("2500.00"), new BigDecimal("18.00")),
                        new ItemSeed("DevOps & CI/CD Pipeline Automation", new BigDecimal("20"), new BigDecimal("2800.00"), new BigDecimal("18.00"))
                ));

        // Invoice 3: OVERDUE (Inter-state, IGST)
        createInvoice(user, c3, "INV-0003", InvoiceStatus.OVERDUE,
                today.minusDays(30), today.minusDays(5),
                "Urgent: Payment is overdue. Please remit at earliest.",
                List.of(
                        new ItemSeed("Generative AI Workflow & Multi-Model Integration", new BigDecimal("30"), new BigDecimal("3500.00"), new BigDecimal("18.00"))
                ));

        // Invoice 4: DRAFT (Intra-state, CGST+SGST)
        createInvoice(user, c1, "INV-0004", InvoiceStatus.DRAFT,
                today, today.plusDays(15),
                "Draft invoice prepared for review.",
                List.of(
                        new ItemSeed("UI/UX Interactive Design System & Figma Tokens", new BigDecimal("1"), new BigDecimal("35000.00"), new BigDecimal("18.00"))
                ));

        log.info("Demo data initialized successfully with user demo@invoice.ai / password123");
    }

    private void createInvoice(
            User user, Client client, String invNum, InvoiceStatus status,
            LocalDate issueDate, LocalDate dueDate, String notes,
            List<ItemSeed> items) {

        Invoice invoice = new Invoice();
        invoice.setUser(user);
        invoice.setClient(client);
        invoice.setInvoiceNumber(invNum);
        invoice.setStatus(status);
        invoice.setIssueDate(issueDate);
        invoice.setDueDate(dueDate);
        invoice.setNotes(notes);

        List<com.ratan.invoice.dto.InvoiceItemRequest> reqItems = items.stream()
                .map(i -> new com.ratan.invoice.dto.InvoiceItemRequest(i.desc, i.qty, i.rate, i.gst))
                .toList();

        GstCalculator.Calculation calc = gstCalculator.calculate(reqItems, user.getState(), client.getState());

        for (int i = 0; i < items.size(); i++) {
            ItemSeed seed = items.get(i);
            InvoiceItem item = new InvoiceItem();
            item.setInvoice(invoice);
            item.setDescription(seed.desc);
            item.setQuantity(seed.qty);
            item.setRate(seed.rate);
            item.setGstRate(seed.gst);
            item.setAmount(calc.items().get(i).amount());
            invoice.getItems().add(item);
        }

        invoice.setSubtotal(calc.subtotal());
        invoice.setCgst(calc.cgst());
        invoice.setSgst(calc.sgst());
        invoice.setIgst(calc.igst());
        invoice.setTotalAmount(calc.totalAmount());

        invoiceRepository.save(invoice);
    }

    private record ItemSeed(String desc, BigDecimal qty, BigDecimal rate, BigDecimal gst) {}
}
