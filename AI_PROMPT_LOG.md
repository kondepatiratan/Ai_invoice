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
| 11 | AI Studio tab showed a blank page | View lookup turned `ai-studio` into `viewAi-studio`, but the section id is `viewAiStudio`, so every view stayed hidden | Convert hyphenated view names to PascalCase before the lookup | Test every screen manually; this bug was found during the full demo run-through |
| 12 | A new account with zero invoices showed a status chart split into four equal parts | AI-generated chart code used `[1, 1, 1, 1]` as placeholder data when every count was 0 | Show a single grey "No invoices yet" ring with tooltips off | Placeholder values in charts look like real data; found by testing with a fresh account |
| 13 | Invoice action menus (Send, Mark as Paid, Delete) were cut off and needed scrolling | The dropdown opened inside the table's scrollable `.table-responsive` wrapper, which clips anything outside it | Position the menus with Popper's `fixed` strategy so they float above the table | Check UI on a real screen; layout bugs don't show up in API tests |
| 14 | Clicking the action arrow on a PAID invoice showed nothing | The menu only had options for DRAFT, SENT and OVERDUE, so for PAID it opened empty and looked broken | Show "Paid invoices are locked" in the menu; verified every status in a headless browser | Empty states need a message, otherwise users think the app is broken |
| 15 | AI "Apply & Save" silently created clients with invented data (fake phone, fake GSTIN `29AAAAA0000A1Z5`, state defaulting to Karnataka) | AI-generated frontend filled every missing field with a placeholder so the save would succeed | A new client now opens the client form pre-filled with only the details the AI actually found; the owner completes email and state, then the invoice continues | Never invent tax data; a wrong default state silently changes CGST+SGST into IGST |
| 16 | If creating that client failed, the invoice was assigned to the first client in the list | Error handler fell back to `state.clients[0]` | Stop and show the error instead of guessing a client | A silent fallback that bills the wrong customer is worse than an error |
| 17 | Registration offered only 6 states and the client form 12, so most businesses couldn't pick their real state | Hard-coded short lists in the HTML | One list of all 36 states and union territories fills every state dropdown; state is now required in the API | The state decides the tax type, so it must always be correct and present |
| 18 | Client names, emails and item descriptions were inserted into the page as raw HTML (cross-site scripting) | Template strings put user and AI text into `innerHTML` without escaping | Escape every user/AI value with `escapeHtml`; verified a client named `<img onerror=...>` no longer runs code | AI output and user input are untrusted data, never HTML |
| 19 | The invoice form's tax preview could show IGST for a same-state client (the saved invoice was still correct) | The login response didn't include the business's state, so the browser fell back to a default | Login now returns the business state and GSTIN; old sessions without a state are asked to sign in again | Frontend previews must use the same data as the backend calculation |
| 20 | Clicking Save quickly 3 times created 3 identical invoices | The Save button stayed active while the request was in progress | Disable the button until the save finishes; tested: 3 rapid clicks now send 1 request instead of 3 | Double-submit protection is needed for any action that creates records |
