# InvoxAI — Intelligent GST Invoicing & Management Platform
**Nivuna Labs Assignment Project**

> A full-stack, enterprise-grade AI Invoicing & GST Management application built with **Java 21**, **Spring Boot 3.5**, **H2 / MySQL**, and a modern **HTML5 / CSS3 / JavaScript (ES6+) / Bootstrap 5.3** frontend.

---

## 🌟 Key Highlights & Innovations

1. **Dual-Tier Resilient AI Integration**:
   - **Primary Model**: Google Gemini (`gemini-2.5-flash`) via Generative Language API.
   - **Fallback Model**: OpenAI ChatGPT (`gpt-4o-mini`) with automatic failover.
   - **Tier 3 Offline Resilience**: Built-in deterministic regex & heuristic parser ensuring zero application crashes even in offline or quota-exceeded environments.
   - **Live Provider Transparency**: Every AI output displays a live badge indicating which AI provider processed the request and its execution latency.
   - **Simulate Fallback Toggle**: Built-in switch in the UI allowing presenters to simulate Gemini downtime and demonstrate ChatGPT fallback live to evaluators.

2. **Automated Prompt-to-Invoice (Natural Language)**:
   - Type plain English descriptions like *"Invoice Infosys in Bangalore for 40 hours of Spring Boot development at ₹3000/hr, 18% GST, due in 15 days"*.
   - AI extracts client metadata, line items, quantities, rates, standard SAC/HSN codes, and computes due dates.

3. **Intelligent Line Item Enhancer**:
   - One-click **AI Enhance** button on every invoice item row.
   - Expands short titles (e.g., *"API Gateway"*) into professional enterprise descriptions with recommended Indian SAC codes (e.g., `998313`) and standard GST slabs.

4. **Context-Aware AI Payment Reminders**:
   - Generates tailored email and WhatsApp reminder messages based on invoice status (`DRAFT`, `SENT`, `OVERDUE`), client name, amount, and selected tone (*Friendly*, *Formal*, *Urgent*).

5. **Indian GST Tax Engine**:
   - **Intra-State Supply**: Automatically splits GST equally into **CGST (50%)** and **SGST (50%)** when the supplier state matches the client state (e.g., Karnataka &rarr; Karnataka).
   - **Inter-State Supply**: Automatically applies full tax as **IGST (Integrated GST)** when states differ (e.g., Karnataka &rarr; Maharashtra).
   - Real-time client-side calculation + strict server-side validation using `BigDecimal` with `HALF_UP` rounding.

6. **High-Fidelity PDF Generation & Export**:
   - **Server-Side PDF**: Uses OpenPDF to stream binary PDFs (`GET /api/invoices/{id}/pdf`) with formal styling, company branding, itemized tables, and tax summaries.
   - **Client-Side Print**: Optimized `@media print` CSS for standard A4 invoice sheets.

7. **Interactive Analytics Dashboard**:
   - Real-time revenue stat cards: **Collected Revenue (Paid)**, **Pending Receivables (Sent)**, **Overdue Alerts**, and **Active Pipeline**.
   - Chart.js interactive monthly revenue trend bar chart and invoice status donut chart.

---

## 🏗️ Technology Stack

| Component | Technology | Rationale |
| :--- | :--- | :--- |
| **Backend** | Java 21, Spring Boot 3.5.x | High-throughput, robust REST API architecture, transactional data integrity |
| **Security** | Spring Security, JJWT (HMAC SHA-256) | Stateless token-based authentication with Bearer header and download query token support |
| **Primary AI** | Google Gemini 2.5 Flash | Fast response times, native JSON schema mode, low latency |
| **Fallback AI** | OpenAI ChatGPT (gpt-4o-mini) | Industry-standard fallback with JSON mode support |
| **Database** | Embedded H2 (persistent file) / MySQL 8.0 | Zero-setup immediate startup with persistent storage; seamlessly switchable to MySQL |
| **PDF Engine** | OpenPDF (Lowagie iText) | Vector-crisp printable PDF generation with precise layout control |
| **Frontend** | HTML5, CSS3, JavaScript (ES6+), Bootstrap 5.3, Chart.js | Clean, responsive Single Page Application (SPA) with glassmorphism styling |

---

## 🚀 Quick Start Guide

### 1. Prerequisites
- **Java 17+** (Java 21 installed on this environment)
- **Apache Maven 3.8+** (Maven 3.9.11 installed)
- **Modern Web Browser** (Chrome, Edge, Firefox, Brave)

### 2. Configure Secrets (optional for a local demo)
Copy `backend/secrets.properties.example` to `backend/secrets.properties` and fill in the values you need
(JWT secret, MySQL connection, AI keys). This file is git-ignored, so keys never reach GitHub.
Without it, the app runs on embedded H2 with a random JWT key and the rule-based AI fallback.

To open the H2 database console locally, set `H2_CONSOLE_ENABLED=true` (it is disabled by default).

### 3. Run the Application
Open a terminal in `D:\Ai_Invoice _genaretorer\backend`:

```powershell
mvn spring-boot:run
```

The Spring Boot backend will build and start on port **8080**.

### 4. Access the Web Application
Open your browser and navigate to:
👉 **[http://localhost:8080/](http://localhost:8080/)**

### 5. Create Your Account
Click **Register** and enter your business name, GSTIN and state. Your state decides whether invoices use
CGST + SGST (same-state clients) or IGST (other-state clients). Then add a client and create your first invoice.

---

## 📡 REST API Reference

### Authentication
- `POST /api/auth/register`: Register new user profile
- `POST /api/auth/login`: Authenticate and receive JWT token

### AI Integration
- `GET /api/ai/health`: Check active AI model and fallback readiness
- `POST /api/ai/generate-invoice`: Extract structured invoice JSON from natural language prompt
- `POST /api/ai/enhance-description`: Generate professional line-item description, SAC code, rate, and GST slab
- `POST /api/ai/reminder`: Generate contextual payment follow-up email/WhatsApp message

### Invoices & Tax
- `GET /api/invoices`: List all invoices (supports `?status=PAID|SENT|OVERDUE|DRAFT`)
- `GET /api/invoices/{id}`: Get invoice details
- `POST /api/invoices`: Create new invoice
- `PUT /api/invoices/{id}`: Update draft invoice
- `PATCH /api/invoices/{id}/status`: Transition status (`DRAFT` &rarr; `SENT` &rarr; `PAID` or `OVERDUE` &rarr; `PAID`)
- `DELETE /api/invoices/{id}`: Delete draft invoice
- `GET /api/invoices/{id}/pdf`: Download binary PDF invoice document

### Clients & Dashboard
- `GET /api/clients`: List all user clients
- `POST /api/clients`: Add a new client
- `DELETE /api/clients/{id}`: Delete client
- `GET /api/dashboard/stats`: Retrieve financial metrics, status counts, and revenue trends

---

## 🤖 AI Collaboration Log

See [AI_PROMPT_LOG.md](AI_PROMPT_LOG.md) for the tools used, key prompts, and what was reviewed, modified and debugged.

---

## 🧪 Running Unit Tests

Run the full automated test suite (covering GST math, PDF creation, and AI fallback):

```powershell
cd "D:\Ai_Invoice _genaretorer\backend"
mvn test
```

All tests execute with **0 failures and 0 errors**.
