# AI Collaboration & Prompt Log
**Project**: AI Invoice Generation & Management (InvoxAI)  
**Presenter**: Ratan Kondepati  
**Institution/Program**: Nivuna Labs

---

## 1. AI Tools Utilized & Selection Rationale
- **Antigravity AI Agent**: Selected as the primary autonomous development assistant for end-to-end full-stack pair programming, iterative bug identification, and rapid scaffolding.
- **Cursor (agent mode)**: Used to scaffold the Spring Boot project, entities, repositories and JWT authentication inside the codebase, reviewing every change as a diff before accepting it.
- **Claude (chat)**: Used for planning (schema, API design, GST rules), reading stack traces to find root causes, and a final code review of the repository.
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

---

## 4. Debugging Log: Issues Found and How They Were Fixed

| # | Issue | Root cause | Fix | Learning |
| :--- | :--- | :--- | :--- | :--- |
| 1 | `git add` failed: "does not have a commit checked out" | The AI-generated project contained its own nested `.git` folder | Removed the inner repository so the whole project is tracked in one repo; renamed the folder to `backend` | Check the folder structure AI generates before committing |
| 2 | App failed to start: "Access denied for user 'your_username'@'localhost'" | Generated `application.properties` still had placeholder credentials | Replaced them with environment variables (`${DB_USERNAME}`, `${DB_PASSWORD}`) | Read the last "Caused by" line of a stack trace; never hardcode credentials |
| 3 | App failed to start: "Could not resolve placeholder 'JWT_SECRET'" | `$env:` variables only last for one terminal session | Load secrets from a git-ignored `secrets.properties` via `spring.config.import`, with a committed `.example` template | Secrets stay out of Git without retyping them every run |
| 4 | Hardcoded default JWT secret committed to a public repo | AI added a default value so the app would start without configuration | Removed the default; if `JWT_SECRET` is missing, a random key is generated at startup with a warning | A committed secret lets anyone forge login tokens |
| 5 | H2 database console open to everyone | Console was enabled with `web-allow-others=true` and frame protection disabled | Console disabled by default (`H2_CONSOLE_ENABLED`), frame options set to same-origin | Dev tools must be off by default before deployment |
| 6 | CORS allowed any origin with credentials | `*` origin pattern | Allowed origins now come from `CORS_ALLOWED_ORIGINS` | Least privilege for cross-origin access |
| 7 | Dashboard could count a past-due invoice as "pending" | Stats were calculated before overdue invoices were updated | Run the overdue update first, then calculate | Order of operations matters for derived data |
| 8 | Revenue chart showed made-up numbers when there was no data | AI added placeholder values as a chart fallback | Chart now shows only real data; label changed to "Billed" since drafts are included | Never display fabricated data in a financial app |
| 9 | Deleting a client with invoices returned a misleading "duplicate" error | Database foreign-key error was mapped to the generic 409 handler | Check for invoices first and return a clear message | Error messages should explain the real problem |
| 10 | The frontend existed twice (`frontend/` and `static/`) | AI copied the UI into the backend to serve it from Spring Boot | Removed the unused copy; Spring Boot serves `static/` | Duplicate code drifts out of sync |
