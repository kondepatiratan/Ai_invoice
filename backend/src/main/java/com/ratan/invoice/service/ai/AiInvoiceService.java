package com.ratan.invoice.service.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ratan.invoice.dto.ai.AiEnhanceRequest;
import com.ratan.invoice.dto.ai.AiEnhanceResponse;
import com.ratan.invoice.dto.ai.AiGeneratedInvoiceResponse;
import com.ratan.invoice.dto.ai.AiPromptRequest;
import com.ratan.invoice.dto.ai.AiReminderRequest;
import com.ratan.invoice.dto.ai.AiReminderResponse;
import com.ratan.invoice.dto.ai.AiStatusResponse;
import com.ratan.invoice.entity.Invoice;
import com.ratan.invoice.exception.ResourceNotFoundException;
import com.ratan.invoice.repository.InvoiceRepository;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class AiInvoiceService {

    private static final Logger log = LoggerFactory.getLogger(AiInvoiceService.class);

    private final ObjectMapper objectMapper;
    private final InvoiceRepository invoiceRepository;
    private final HttpClient httpClient;

    @Value("${ai.gemini.api-key:}")
    private String defaultGeminiApiKey;

    @Value("${ai.gemini.model:gemini-2.5-flash}")
    private String geminiModel;

    @Value("${ai.gemini.url:https://generativelanguage.googleapis.com/v1beta/models}")
    private String geminiBaseUrl;

    @Value("${ai.openai.api-key:}")
    private String defaultOpenaiApiKey;

    @Value("${ai.openai.model:gpt-4o-mini}")
    private String openaiModel;

    @Value("${ai.openai.url:https://api.openai.com/v1/chat/completions}")
    private String openaiUrl;

    public AiInvoiceService(ObjectMapper objectMapper, InvoiceRepository invoiceRepository) {
        this.objectMapper = objectMapper;
        this.invoiceRepository = invoiceRepository;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    public AiStatusResponse getStatus() {
        boolean geminiConfigured = defaultGeminiApiKey != null && !defaultGeminiApiKey.isBlank();
        boolean openaiConfigured = defaultOpenaiApiKey != null && !defaultOpenaiApiKey.isBlank();
        String primary = geminiConfigured ? "Gemini (" + geminiModel + ")"
                : (openaiConfigured ? "ChatGPT (" + openaiModel + ")" : "Smart Fallback Engine");
        return new AiStatusResponse(
                geminiConfigured,
                geminiModel,
                openaiConfigured,
                openaiModel,
                true,
                primary
        );
    }

    public AiGeneratedInvoiceResponse generateInvoice(AiPromptRequest request) {
        String prompt = request.prompt();
        boolean simulateFallback = Boolean.TRUE.equals(request.simulateFallback());

        String geminiKey = hasText(request.apiKey()) ? request.apiKey() : defaultGeminiApiKey;
        String openaiKey = hasText(request.fallbackApiKey()) ? request.fallbackApiKey() : defaultOpenaiApiKey;

        // Step 1: Try Primary Provider (Google Gemini) unless simulateFallback is requested
        if (!simulateFallback && hasText(geminiKey)) {
            try {
                log.info("Attempting invoice generation via primary provider: Google Gemini ({})", geminiModel);
                AiGeneratedInvoiceResponse response = callGeminiGenerateInvoice(prompt, geminiKey);
                if (response != null && response.items() != null && !response.items().isEmpty()) {
                    return response;
                }
            } catch (Exception ex) {
                log.warn("Primary AI provider (Gemini) failed: {}. Falling back to ChatGPT (OpenAI)...", ex.getMessage());
            }
        } else if (simulateFallback) {
            log.info("Simulate Fallback mode active: bypassing Gemini and attempting ChatGPT fallback");
        }

        // Step 2: Try Fallback Provider (OpenAI ChatGPT)
        if (hasText(openaiKey)) {
            try {
                log.info("Attempting invoice generation via fallback provider: OpenAI ({})", openaiModel);
                AiGeneratedInvoiceResponse response = callOpenAiGenerateInvoice(prompt, openaiKey);
                if (response != null && response.items() != null && !response.items().isEmpty()) {
                    return response;
                }
            } catch (Exception ex) {
                log.warn("Fallback AI provider (OpenAI) failed: {}. Falling back to local smart rules...", ex.getMessage());
            }
        }

        // Step 3: Resilient Offline/Rule-based Fallback (Guaranteed to return working invoice data)
        log.info("Using smart rule-based parser fallback for prompt: {}", prompt);
        return parsePromptWithSmartRules(prompt);
    }

    public AiEnhanceResponse enhanceDescription(AiEnhanceRequest request) {
        String title = request.title();
        String context = request.context() != null ? request.context() : "";

        String prompt = String.format(
                "You are an Indian GST and invoicing expert. Given the item title '%s' and context '%s', generate:\n"
                        + "1. A polished, professional line-item description for an invoice.\n"
                        + "2. Suggested unit rate in INR (numeric).\n"
                        + "3. Suggested quantity (default 1).\n"
                        + "4. Recommended Indian GST rate percentage (one of 0, 5, 12, 18, 28).\n"
                        + "5. Recommended 6-digit SAC or HSN code.\n"
                        + "Return ONLY a JSON object: "
                        + "{\"title\":\"%s\", \"enhancedDescription\":\"...\", \"suggestedRate\":1500, \"suggestedQuantity\":1, \"recommendedGstRate\":18, \"hsnSacCode\":\"998314\"}",
                title, context, title);

        // Try Gemini
        if (hasText(defaultGeminiApiKey)) {
            try {
                String jsonText = executeGeminiRaw(prompt, defaultGeminiApiKey);
                JsonNode root = parseJsonFlexible(jsonText);
                return new AiEnhanceResponse(
                        root.path("title").asText(title),
                        root.path("enhancedDescription").asText(title),
                        new BigDecimal(root.path("suggestedRate").asText("1000")),
                        new BigDecimal(root.path("suggestedQuantity").asText("1")),
                        new BigDecimal(root.path("recommendedGstRate").asText("18")),
                        root.path("hsnSacCode").asText("998314"),
                        "Gemini " + geminiModel + " (Primary)"
                );
            } catch (Exception ex) {
                log.warn("Gemini enhance failed: {}", ex.getMessage());
            }
        }

        // Try OpenAI
        if (hasText(defaultOpenaiApiKey)) {
            try {
                String jsonText = executeOpenAiRaw(prompt, defaultOpenaiApiKey);
                JsonNode root = parseJsonFlexible(jsonText);
                return new AiEnhanceResponse(
                        root.path("title").asText(title),
                        root.path("enhancedDescription").asText(title),
                        new BigDecimal(root.path("suggestedRate").asText("1000")),
                        new BigDecimal(root.path("suggestedQuantity").asText("1")),
                        new BigDecimal(root.path("recommendedGstRate").asText("18")),
                        root.path("hsnSacCode").asText("998314"),
                        "ChatGPT " + openaiModel + " (Fallback)"
                );
            } catch (Exception ex) {
                log.warn("OpenAI enhance failed: {}", ex.getMessage());
            }
        }

        // Local Smart fallback
        return ruleBasedEnhance(title);
    }

    public AiReminderResponse generatePaymentReminder(AiReminderRequest request, Long userId) {
        Invoice invoice = invoiceRepository.findByIdAndUserId(request.invoiceId(), userId)
                .orElseThrow(() -> new ResourceNotFoundException("Invoice not found"));

        String tone = (request.tone() != null && !request.tone().isBlank()) ? request.tone() : "polite";
        String clientName = invoice.getClient().getName();
        String invNumber = invoice.getInvoiceNumber();
        String totalAmount = "₹" + invoice.getTotalAmount().toPlainString();
        String dueDate = invoice.getDueDate() != null ? invoice.getDueDate().toString() : "Due on receipt";
        String status = invoice.getStatus().name();

        String prompt = String.format(
                "Write a professional payment reminder message with tone '%s' for Invoice %s. Client: %s. Amount: %s. Due Date: %s. Status: %s.\n"
                        + "Return ONLY a JSON object: {\"subject\":\"...\", \"messageBody\":\"...\", \"channelSuggestion\":\"Email / WhatsApp\"}",
                tone, invNumber, clientName, totalAmount, dueDate, status);

        // Try Gemini
        if (hasText(defaultGeminiApiKey)) {
            try {
                String jsonText = executeGeminiRaw(prompt, defaultGeminiApiKey);
                JsonNode root = parseJsonFlexible(jsonText);
                return new AiReminderResponse(
                        root.path("subject").asText("Payment Reminder: Invoice " + invNumber),
                        root.path("messageBody").asText(),
                        root.path("channelSuggestion").asText("Email"),
                        "Gemini " + geminiModel + " (Primary)"
                );
            } catch (Exception ex) {
                log.warn("Gemini reminder generation failed: {}", ex.getMessage());
            }
        }

        // Try OpenAI
        if (hasText(defaultOpenaiApiKey)) {
            try {
                String jsonText = executeOpenAiRaw(prompt, defaultOpenaiApiKey);
                JsonNode root = parseJsonFlexible(jsonText);
                return new AiReminderResponse(
                        root.path("subject").asText("Payment Reminder: Invoice " + invNumber),
                        root.path("messageBody").asText(),
                        root.path("channelSuggestion").asText("Email"),
                        "ChatGPT " + openaiModel + " (Fallback)"
                );
            } catch (Exception ex) {
                log.warn("OpenAI reminder generation failed: {}", ex.getMessage());
            }
        }

        // Rule-based template fallback
        String subject = String.format(Locale.ROOT, "Reminder: Payment for Invoice %s (%s)", invNumber, clientName);
        String body = String.format(
                "Dear %s Team,\n\n"
                        + "Hope this message finds you well.\n"
                        + "This is a reminder regarding Invoice %s for %s, which was due on %s (Current status: %s).\n\n"
                        + "Kindly review and arrange for payment at your earliest convenience. If you have already processed the payment, please disregard this note.\n\n"
                        + "Thank you for your business!\nBest regards,\nAccounts Team",
                clientName, invNumber, totalAmount, dueDate, status);

        return new AiReminderResponse(subject, body, "Email", "Rule-based Template Engine (Offline Fallback)");
    }

    // -------------------------------------------------------------
    // Helper Methods: Google Gemini API
    // -------------------------------------------------------------
    private AiGeneratedInvoiceResponse callGeminiGenerateInvoice(String prompt, String apiKey) throws Exception {
        String systemInstruction = getInvoicePromptInstruction();
        String fullUserPrompt = systemInstruction + "\n\nUser Invoice Request: " + prompt;

        String endpoint = String.format("%s/%s:generateContent", geminiBaseUrl, geminiModel);

        Map<String, Object> requestBody = Map.of(
                "contents", List.of(
                        Map.of("parts", List.of(Map.of("text", fullUserPrompt)))
                ),
                "generationConfig", Map.of(
                        "temperature", 0.2,
                        "responseMimeType", "application/json"
                )
        );

        String jsonPayload = objectMapper.writeValueAsString(requestBody);

        HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(endpoint))
                .header("Content-Type", "application/json")
                // Key in a header, not the URL, so it never ends up in request logs
                .header("x-goog-api-key", apiKey)
                .timeout(Duration.ofSeconds(15))
                .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                .build();

        HttpResponse<String> httpResponse = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());

        if (httpResponse.statusCode() >= 200 && httpResponse.statusCode() < 300) {
            JsonNode root = objectMapper.readTree(httpResponse.body());
            JsonNode candidate = root.path("candidates").path(0);
            String text = candidate.path("content").path("parts").path(0).path("text").asText();
            return parseInvoiceJsonNode(parseJsonFlexible(text), "Gemini " + geminiModel + " (Primary)", geminiModel, text);
        } else {
            throw new RuntimeException("Gemini API HTTP " + httpResponse.statusCode() + ": " + httpResponse.body());
        }
    }

    private String executeGeminiRaw(String prompt, String apiKey) throws Exception {
        String endpoint = String.format("%s/%s:generateContent", geminiBaseUrl, geminiModel);
        Map<String, Object> requestBody = Map.of(
                "contents", List.of(Map.of("parts", List.of(Map.of("text", prompt)))),
                "generationConfig", Map.of("temperature", 0.2, "responseMimeType", "application/json")
        );
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(endpoint))
                .header("Content-Type", "application/json")
                .header("x-goog-api-key", apiKey)
                .timeout(Duration.ofSeconds(12))
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(requestBody)))
                .build();
        HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() >= 200 && resp.statusCode() < 300) {
            JsonNode root = objectMapper.readTree(resp.body());
            return root.path("candidates").path(0).path("content").path("parts").path(0).path("text").asText();
        }
        throw new RuntimeException("Gemini returned " + resp.statusCode() + ": " + resp.body());
    }

    // -------------------------------------------------------------
    // Helper Methods: OpenAI / ChatGPT API
    // -------------------------------------------------------------
    private AiGeneratedInvoiceResponse callOpenAiGenerateInvoice(String prompt, String apiKey) throws Exception {
        String systemInstruction = getInvoicePromptInstruction();

        Map<String, Object> requestBody = Map.of(
                "model", openaiModel,
                "messages", List.of(
                        Map.of("role", "system", "content", systemInstruction),
                        Map.of("role", "user", "content", prompt)
                ),
                "temperature", 0.2,
                "response_format", Map.of("type", "json_object")
        );

        String jsonPayload = objectMapper.writeValueAsString(requestBody);

        HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(openaiUrl))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .timeout(Duration.ofSeconds(15))
                .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                .build();

        HttpResponse<String> httpResponse = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());

        if (httpResponse.statusCode() >= 200 && httpResponse.statusCode() < 300) {
            JsonNode root = objectMapper.readTree(httpResponse.body());
            String text = root.path("choices").path(0).path("message").path("content").asText();
            return parseInvoiceJsonNode(parseJsonFlexible(text), "ChatGPT " + openaiModel + " (Fallback)", openaiModel, text);
        } else {
            throw new RuntimeException("OpenAI API HTTP " + httpResponse.statusCode() + ": " + httpResponse.body());
        }
    }

    private String executeOpenAiRaw(String prompt, String apiKey) throws Exception {
        Map<String, Object> requestBody = Map.of(
                "model", openaiModel,
                "messages", List.of(
                        Map.of("role", "system", "content", "You are an Indian GST and invoicing expert. Always respond in JSON format."),
                        Map.of("role", "user", "content", prompt)
                ),
                "temperature", 0.2,
                "response_format", Map.of("type", "json_object")
        );
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(openaiUrl))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .timeout(Duration.ofSeconds(12))
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(requestBody)))
                .build();
        HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() >= 200 && resp.statusCode() < 300) {
            JsonNode root = objectMapper.readTree(resp.body());
            return root.path("choices").path(0).path("message").path("content").asText();
        }
        throw new RuntimeException("OpenAI returned " + resp.statusCode() + ": " + resp.body());
    }

    // -------------------------------------------------------------
    // Prompt Instructions & Schema
    // -------------------------------------------------------------
    private String getInvoicePromptInstruction() {
        return "You are an intelligent billing and invoice generation AI assistant for India.\n"
                + "Analyze the user's prompt and extract structured invoice details.\n"
                + "Rules:\n"
                + "1. Determine client information: clientName, clientEmail, clientPhone, clientGstin, clientState (e.g. Karnataka, Maharashtra), clientAddress.\n"
                + "   Only use details written in the user's request. If any of these is not given, return an empty string \"\".\n"
                + "   Never invent an email, phone number, GSTIN or address: the owner fills them in before saving.\n"
                + "2. Determine issueDate (default to today " + LocalDate.now() + " if unspecified).\n"
                + "3. Determine dueDate (if 'due in X days' is mentioned, add X days to today, otherwise default to 15 days from today).\n"
                + "4. Extract list of invoice items: description (clear, professional), quantity (number), rate (unit price in INR), gstRate (standard Indian GST slabs: 0, 5, 12, 18, 28; default 18 for software/consulting services), and hsnSacCode (e.g. 998314 for IT).\n"
                + "5. Generate a short professional notes section with payment terms. Never invent bank account numbers, IFSC codes, UPI IDs or late fees.\n"
                + "Output MUST be a JSON object with this exact structure:\n"
                + "{\n"
                + "  \"clientName\": \"string\",\n"
                + "  \"clientEmail\": \"string\",\n"
                + "  \"clientPhone\": \"string\",\n"
                + "  \"clientGstin\": \"string\",\n"
                + "  \"clientState\": \"string\",\n"
                + "  \"clientAddress\": \"string\",\n"
                + "  \"issueDate\": \"YYYY-MM-DD\",\n"
                + "  \"dueDate\": \"YYYY-MM-DD\",\n"
                + "  \"notes\": \"string\",\n"
                + "  \"items\": [\n"
                + "    {\n"
                + "      \"description\": \"string\",\n"
                + "      \"quantity\": 1,\n"
                + "      \"rate\": 1000,\n"
                + "      \"gstRate\": 18,\n"
                + "      \"hsnSacCode\": \"998314\"\n"
                + "    }\n"
                + "  ]\n"
                + "}";
    }

    private JsonNode parseJsonFlexible(String raw) throws Exception {
        String clean = raw.trim();
        if (clean.startsWith("```json")) {
            clean = clean.substring(7);
        } else if (clean.startsWith("```")) {
            clean = clean.substring(3);
        }
        if (clean.endsWith("```")) {
            clean = clean.substring(0, clean.length() - 3);
        }
        return objectMapper.readTree(clean.trim());
    }

    private AiGeneratedInvoiceResponse parseInvoiceJsonNode(JsonNode root, String providerUsed, String modelUsed, String raw) {
        String clientName = root.path("clientName").asText("Valued Client");
        String clientEmail = root.path("clientEmail").asText("");
        String clientPhone = root.path("clientPhone").asText("");
        String clientGstin = root.path("clientGstin").asText("");
        String clientState = root.path("clientState").asText("");
        String clientAddress = root.path("clientAddress").asText("");

        LocalDate today = LocalDate.now();
        LocalDate issueDate = today;
        if (root.hasNonNull("issueDate")) {
            try {
                issueDate = LocalDate.parse(root.path("issueDate").asText());
            } catch (Exception ignored) {}
        }

        LocalDate dueDate = issueDate.plusDays(15);
        if (root.hasNonNull("dueDate")) {
            try {
                dueDate = LocalDate.parse(root.path("dueDate").asText());
            } catch (Exception ignored) {}
        }

        String notes = root.path("notes").asText("Payment terms: Net 15 days. Please include invoice number on your transfer.");

        List<AiGeneratedInvoiceResponse.AiItemResponse> items = new ArrayList<>();
        JsonNode itemsNode = root.path("items");
        if (itemsNode.isArray()) {
            for (JsonNode item : itemsNode) {
                String desc = item.path("description").asText("Professional Service");
                BigDecimal qty = new BigDecimal(item.path("quantity").asText("1"));
                BigDecimal rate = new BigDecimal(item.path("rate").asText("1000"));
                BigDecimal gstRate = new BigDecimal(item.path("gstRate").asText("18"));
                String hsn = item.path("hsnSacCode").asText("998314");
                items.add(new AiGeneratedInvoiceResponse.AiItemResponse(desc, qty, rate, gstRate, hsn));
            }
        }

        if (items.isEmpty()) {
            items.add(new AiGeneratedInvoiceResponse.AiItemResponse(
                    "Professional Consulting & Development",
                    BigDecimal.ONE,
                    new BigDecimal("1000.00"),
                    new BigDecimal("18.00"),
                    "998314"
            ));
        }

        return new AiGeneratedInvoiceResponse(
                clientName,
                clientEmail,
                clientPhone,
                clientGstin,
                clientState,
                clientAddress,
                issueDate,
                dueDate,
                notes,
                items,
                providerUsed,
                modelUsed,
                raw
        );
    }

    // -------------------------------------------------------------
    // Local Smart Rules Fallback Parser (Robust & Resilient)
    // -------------------------------------------------------------
    private AiGeneratedInvoiceResponse parsePromptWithSmartRules(String prompt) {
        String lower = prompt.toLowerCase(Locale.ROOT);

        // 1. Extract Client Name
        String clientName = "Acme Technologies";
        Matcher clientMatcher = Pattern.compile("(?:(?:create\\s+)?(?:invoice|bill)\\s+(?:for|to)\\s+|for\\s+|to\\s+)([A-Za-z0-9&\\s]+?)(?:\\s+for|\\s+with|\\s+due|\\s*[:;,]|\\s*\\d|$)")
                .matcher(prompt);
        if (clientMatcher.find()) {
            String candidate = clientMatcher.group(1).trim();
            candidate = candidate.replaceAll("^(?i)(?:for|to|client)\\s+", "").trim();
            if (candidate.length() > 2 && !candidate.equalsIgnoreCase("the") && !candidate.equalsIgnoreCase("a")) {
                clientName = candidate;
            }
        }

        // 2. Extract State (Detect common Indian states). Left empty if not mentioned,
        // because the state decides CGST+SGST vs IGST, so the owner must confirm it.
        String clientState = "";
        String[] states = {"Karnataka", "Maharashtra", "Delhi", "Tamil Nadu", "Telangana", "Gujarat", "Uttar Pradesh", "West Bengal", "Kerala", "Rajasthan"};
        for (String state : states) {
            if (lower.contains(state.toLowerCase(Locale.ROOT))) {
                clientState = state;
                break;
            }
        }

        // 3. Extract Due Date / Days
        LocalDate today = LocalDate.now();
        LocalDate dueDate = today.plusDays(15);
        Matcher daysMatcher = Pattern.compile("due\\s+in\\s+(\\d+)\\s+days?").matcher(lower);
        if (daysMatcher.find()) {
            try {
                int days = Integer.parseInt(daysMatcher.group(1));
                dueDate = today.plusDays(days);
            } catch (Exception ignored) {}
        }

        // 4. Extract GST rate
        BigDecimal gstRate = new BigDecimal("18.00");
        Matcher gstMatcher = Pattern.compile("(\\d+)\\s*%\\s*gst|gst\\s*(\\d+)\\s*%?").matcher(lower);
        if (gstMatcher.find()) {
            String rateStr = gstMatcher.group(1) != null ? gstMatcher.group(1) : gstMatcher.group(2);
            try {
                gstRate = new BigDecimal(rateStr).setScale(2);
            } catch (Exception ignored) {}
        }

        // 5. Extract Items and Rates
        List<AiGeneratedInvoiceResponse.AiItemResponse> items = new ArrayList<>();

        // Regex for patterns like: "40 hours of frontend development at 2500" or "10 licenses @ 500"
        Pattern itemPattern = Pattern.compile("(\\d+(?:\\.\\d+)?)\\s*(?:hours?|hrs?|units?|items?|days?|x)?\\s*(?:of|for)?\\s*([a-zA-Z0-9\\s\\-_/]+?)\\s*(?:@|at|rate|price|for|costing)?\\s*(?:₹|rs\\.?|inr|usd|\\$)?\\s*(\\d+(?:\\.\\d+)?)", Pattern.CASE_INSENSITIVE);
        Matcher itemMatcher = itemPattern.matcher(prompt);

        while (itemMatcher.find()) {
            try {
                BigDecimal qty = new BigDecimal(itemMatcher.group(1));
                String desc = itemMatcher.group(2).trim();
                BigDecimal rate = new BigDecimal(itemMatcher.group(3));

                if (!desc.isBlank() && desc.length() > 2) {
                    // Clean description
                    desc = desc.replaceAll("^(?:of|for|at)\\s+", "").trim();
                    items.add(new AiGeneratedInvoiceResponse.AiItemResponse(
                            capitalize(desc),
                            qty,
                            rate,
                            gstRate,
                            "998314"
                    ));
                }
            } catch (Exception ignored) {}
        }

        if (items.isEmpty()) {
            // Fallback generic item based on prompt
            String desc = prompt.length() > 60 ? prompt.substring(0, 57) + "..." : prompt;
            items.add(new AiGeneratedInvoiceResponse.AiItemResponse(
                    "Professional Services: " + desc,
                    BigDecimal.ONE,
                    new BigDecimal("5000.00"),
                    gstRate,
                    "998314"
            ));
        }

        return new AiGeneratedInvoiceResponse(
                clientName,
                "", // email, phone, GSTIN and address are never invented;
                "", // the owner enters the real ones in the client form
                "",
                clientState,
                "",
                today,
                dueDate,
                "Auto-extracted via AI Smart Fallback Engine. Payment due by " + dueDate.format(DateTimeFormatter.ofPattern("dd-MMM-yyyy")),
                items,
                "Smart Rules Engine (Offline Fallback)",
                "Local NLP Heuristics",
                prompt
        );
    }

    private AiEnhanceResponse ruleBasedEnhance(String title) {
        String lower = title.toLowerCase(Locale.ROOT);
        String desc;
        String sac = "998314";
        BigDecimal rate = new BigDecimal("2500.00");
        BigDecimal gst = new BigDecimal("18.00");

        if (lower.contains("design") || lower.contains("ui") || lower.contains("ux")) {
            desc = "Comprehensive UI/UX design, interactive Figma prototypes, design system components, and cross-device responsive layouts.";
            sac = "998314";
            rate = new BigDecimal("3500.00");
        } else if (lower.contains("backend") || lower.contains("java") || lower.contains("spring") || lower.contains("api")) {
            desc = "Enterprise backend architecture, high-performance Spring Boot REST APIs, database persistence tuning, and security implementation.";
            sac = "998313";
            rate = new BigDecimal("4000.00");
        } else if (lower.contains("test") || lower.contains("qa")) {
            desc = "End-to-end automated testing suite, regression coverage, API contract verification, and CI/CD quality gates.";
            sac = "998316";
            rate = new BigDecimal("2000.00");
        } else if (lower.contains("cloud") || lower.contains("devops") || lower.contains("aws") || lower.contains("docker")) {
            desc = "Cloud infrastructure orchestration, containerization, Kubernetes manifests, and automated blue-green deployment pipelines.";
            sac = "998315";
            rate = new BigDecimal("4500.00");
        } else {
            desc = "Professional services delivery: " + title + " including planning, implementation, quality assurance, and documentation.";
        }

        return new AiEnhanceResponse(
                title,
                desc,
                rate,
                BigDecimal.ONE,
                gst,
                sac,
                "Smart Rules Dictionary (Offline Fallback)"
        );
    }

    private static boolean hasText(String str) {
        return str != null && !str.isBlank();
    }

    private static String capitalize(String text) {
        if (text == null || text.isBlank()) return text;
        return Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }
}
