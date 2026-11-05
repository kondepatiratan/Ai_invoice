# AI Collaboration & Prompt Log
**Project**: AI Invoice Generation & Management (InvoxAI)  
**Presenter**: Ratan Kondepati  
**Institution/Program**: Nivuna Labs

---

## 1. AI Tools Utilized & Selection Rationale
- **Antigravity AI Agent**: Selected as the primary autonomous development assistant for end-to-end full-stack pair programming, iterative bug identification, and rapid scaffolding.
- **Google Gemini 2.5 Flash API**: Chosen as the primary generative AI model for natural language invoice extraction due to sub-second latency, Indian billing contextual awareness, and native JSON mode (`responseMimeType: "application/json"`).
- **OpenAI ChatGPT API (gpt-4o-mini)**: Chosen as the resilient secondary fallback model to ensure 100% operational continuity during rate limits or service interruptions.

---

## 2. Prompts Used & Collaboration Process

### Prompt Iteration 1: Natural Language Invoice Extraction Schema
- **Objective**: Convert loose user billing prompts into strict, parseable JSON for Indian invoicing.
- **System Instruction**:
  ```text
  You are an intelligent billing and invoice generation AI assistant for India.
  Analyze the user's prompt and extract structured invoice details:
  1. Determine client info: clientName, clientEmail, clientPhone, clientGstin, clientState, clientAddress.
  2. Determine issueDate (default to today) and dueDate (default to 15 days from today if not specified).
  3. Extract list of invoice items: description, quantity, rate (INR), gstRate (0, 5, 12, 18, 28), and hsnSacCode.
  4. Generate professional notes with payment terms.
  Output MUST be JSON matching the specified schema.
  ```

### Prompt Iteration 2: Fallback Failover Architecture Design
- **Objective**: Seamlessly switch providers without crashing the application.
- **AI Solution**: Developed a 3-tier cascade:
  1. Primary: Google Gemini REST endpoint.
  2. Secondary: OpenAI ChatGPT endpoint.
  3. Tertiary: Local NLP heuristic regex parser for offline/demo reliability.

### Prompt Iteration 3: Contextual Payment Reminders
- **Objective**: Craft realistic polite, formal, or urgent payment reminders.
- **Prompt**:
  ```text
  Write a professional payment reminder message with tone '{tone}' for Invoice {invNumber}.
  Client: {clientName}. Amount: {amount}. Due Date: {dueDate}. Status: {status}.
  Return JSON: {"subject":"...", "messageBody":"...", "channelSuggestion":"Email / WhatsApp"}
  ```

---

## 3. What AI Generated vs. What Was Reviewed, Modified & Debugged

| Area | What AI Generated | What Was Reviewed & Modified by Developer |
| :--- | :--- | :--- |
| **Spring Security** | Default stateless JWT filter | Added query parameter `?token=` token extraction to allow native browser tab opening of `/api/invoices/{id}/pdf`. |
| **Exception Handling** | Broad exception handler | Fixed `NoResourceFoundException` in Spring Boot 3.5 to prevent static resource 500 errors when loading frontend files. |
| **GST Engine** | Basic subtotal & tax sum | Refactored into separate intra-state (`CGST 50% + SGST 50%`) vs. inter-state (`IGST 100%`) calculations with exact `BigDecimal` half-up rounding. |
| **AI Fallback** | Try-catch block | Added `simulateFallback` parameter to allow intentional failover testing during live presentations. |
| **Database** | MySQL-only configuration | Configured hybrid persistent file H2 by default with seamless MySQL switchability for zero-friction evaluation. |
| **Frontend UI** | Basic HTML table | Transformed into full glassmorphism dashboard with Chart.js analytics, dynamic line item rows, live tax notices, and `@media print` A4 printable styling. |
