# NIVUNA LABS PRESENTATION GUIDE
## Problem Statement 01: AI Invoice Generation & Management
**Presenter**: Ratan Kondepati  
**Application Name**: InvoxAI

---

## 📋 Presentation Checklist Alignment

### 1. Problem Understanding & Solution Approach
- **The Problem**: Freelancers, agencies, and small businesses spend excessive manual time formatting invoices, computing intricate Indian GST taxes (intra-state CGST+SGST vs. inter-state IGST), chasing overdue payments, and writing line-item descriptions.
- **The Solution**: **InvoxAI** automates the entire invoice lifecycle through AI. Users describe a billing event in natural English, and the platform extracts clients, calculates compliant GST splits, generates PDF downloads, tracks payment stages, and crafts contextual payment reminders.

---

### 2. Application Architecture & Technology Stack
```
+------------------------------------------------------------------------+
|                   FRONTEND (HTML5, CSS3, JS ES6+, Bootstrap 5.3)       |
|  - Dashboard (Chart.js) | AI Prompt Studio | Invoices Table | Clients  |
+------------------------------------------------------------------------+
                                   | (REST API via JWT)
                                   v
+------------------------------------------------------------------------+
|                   BACKEND (Java 21, Spring Boot 3.5.x)                 |
|  - Spring Security (Stateless JWT)                                    |
|  - GST Calculation Engine (Intra CGST/SGST vs Inter IGST)              |
|  - PDF Generation Service (OpenPDF)                                    |
+------------------------------------------------------------------------+
         |                                              |
         v                                              v
+-----------------------------+          +-------------------------------+
|       DATABASE LAYER        |          |      AI INTEGRATION LAYER     |
| - Persistent H2 / MySQL 8.0 |          | - Primary: Google Gemini 2.5  |
| - JPA / Hibernate 6         |          | - Fallback: OpenAI ChatGPT    |
| - Automated Data Seeder     |          | - Tier 3: Local Smart Rules   |
+-----------------------------+          +-------------------------------+
```

---

### 3. Vibe Coding & AI Tools Used
- **Tools Used**: Antigravity, Google Gemini 2.5 Flash, ChatGPT (OpenAI).
- **Why Chosen**:
  - *Gemini 2.5 Flash*: Exceptional speed, low latency, and native structured JSON output formatting.
  - *OpenAI gpt-4o-mini*: Reliable secondary fallback to guarantee zero service interruption.
  - *Antigravity Agentic IDE*: Rapid iteration of backend API contracts, Spring Security integration, and front-end glassmorphism design.
- **How Collaborated with AI**:
  - Structured prompt engineering with strict JSON output schemas.
  - Co-design of the fallback failover pipeline so an API timeout or rate limit automatically routes to the secondary provider without user disruption.
- **What AI Generated vs. What Was Reviewed & Modified**:
  - *AI Generated*: Initial entity schemas, prompt templates, and HTML layout structure.
  - *Reviewed & Refactored*: Fixed Spring 6 `NoResourceFoundException` handling in `GlobalExceptionHandler`, implemented custom `BigDecimal` rounding rules for GST splits, added query token extraction in `JwtAuthFilter` for seamless browser PDF tab previews, and implemented dual intra/inter state detection based on supplier and client state codes.

---

### 4. Live Application Walkthrough (Step-by-Step Demo Script)

#### Step 1: Login & Dashboard Overview (1 Minute)
1. Open browser to `http://localhost:8080/`.
2. Click **"1-Click Demo Sign In"** to load the preloaded profile.
3. Highlight the 4 stat cards:
   - *Collected Revenue (Paid)*
   - *Pending Receivables (Sent)*
   - *Overdue Payments Alert*
   - *Active Pipeline & Client Count*
4. Point out the live **Revenue Trend Bar Chart** and **Status Donut Chart**.

#### Step 2: AI Prompt-to-Invoice Demonstration (2 Minutes)
1. Click the **AI Studio** tab in navigation.
2. Click one of the quick prompt pills or paste:
   ```text
   Invoice Wipro Limited in Bangalore for 45 hours of Spring Boot microservices backend development at ₹3000/hour and 15 hours of Docker DevOps setup at ₹2500/hour, 18% GST, due in 15 days
   ```
3. Click **"Generate Invoice with AI"**.
4. Show the extracted client info, dates, and line items.
5. Point out the **Provider Badge**: `Gemini gemini-2.5-flash (Primary)` with elapsed latency (~1.2s).
6. Click **"Apply & Save as Invoice"** — instantly saved to the database!

#### Step 3: Demonstrating the Dual-Tier Fallback Mechanism (1 Minute)
1. In the AI Studio card, toggle **"Simulate Gemini Failure (Demo Fallback Provider)"**.
2. Click **"Generate Invoice with AI"** again.
3. Show that the app **never crashes**: it automatically fell back to the secondary fallback system, and the badge reflects the fallback provider!
4. *Key Presenter Statement*: *"In real-world SaaS applications, external AI APIs can fail or hit rate limits. Our architecture provides seamless, zero-downtime failover."*

#### Step 4: Line Item AI Enhancer & Real-Time GST Calculation (1.5 Minutes)
1. Click **"New Invoice"** in the top navigation.
2. Select **"Acme Global Technologies (Karnataka)"**: Notice the green banner indicating **Intra-State Supply (CGST 50% + SGST 50%)**.
3. Now switch to **"TechNova Labs (Maharashtra)"**: Notice the banner dynamically switches to **Inter-State Supply (IGST 100%)**.
4. In the items row, type *"Kubernetes cluster"* in the description and click the **"✨ AI"** button.
5. Watch the AI auto-fill an enterprise description, SAC code `998315`, suggested rate, and 18% GST slab!

#### Step 5: PDF Export & AI Payment Reminder (1.5 Minutes)
1. Go to the **Invoices** tab.
2. Click the eye icon (👁️) to preview **INV-0001**.
3. Click **"Download Server PDF"** to demonstrate binary PDF generation streamed from Spring Boot via OpenPDF.
4. Click the three dots menu on an overdue invoice (**INV-0003**) and select **"Generate AI Reminder"**.
5. Switch between **"Friendly"**, **"Formal"**, and **"Urgent"** tones to show real-time contextual email/WhatsApp generation. Click **"Copy to Clipboard"**.

---

### 5. Challenges, Learnings & Future Enhancements

#### Challenges Overcome
1. **Model Endpoint Evolution**: Updated from legacy models to latest Google `gemini-2.5-flash` with native `responseMimeType: "application/json"`.
2. **Spring Security & Browser PDF Downloads**: Browser downloads via `<a href="..." target="_blank">` do not allow custom `Authorization` headers. Enhanced `JwtAuthFilter` to seamlessly authenticate both `Authorization: Bearer <token>` and `?token=<token>` query parameters.
3. **Indian GST Rules**: Ensuring correct tax bifurcation (CGST+SGST vs. IGST) based on place of supply, with `BigDecimal` half-up rounding.

#### Future Enhancements
- WhatsApp Business API integration for one-click invoice dispatch.
- Multi-currency support (USD, EUR, GBP) with live exchange rates.
- E-way bill generation for physical goods delivery.
- OCR invoice scanning: upload an image of a receipt to auto-generate an invoice.
