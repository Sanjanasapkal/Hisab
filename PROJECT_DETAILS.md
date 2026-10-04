# HISAB (हिसाब) — Complete Project Documentation & Technical Viva Guide
**Author:** Sanjana Dadaso Sapkal  
**Architecture:** 3-Tier Cloud-Synchronized System (Native Android Kotlin + Node.js/Express + MongoDB Atlas Cloud + Brevo SMTP)  
**Live Cloud Deployment:** https://hisab-zovn.onrender.com  
**GitHub Repository:** https://github.com/Sanjanasapkal/Hisab.git  

---

## 📑 Table of Contents
1. [Project Overview & 1-Minute Viva Elevator Pitch](#1-project-overview--the-1-minute-viva-elevator-pitch)
2. [Problem Statement & Motivation](#2-problem-statement--motivation)
3. [System Architecture (The 3-Tier Model)](#3-system-architecture-the-3-tier-model)
4. [Folder Structure & Component Breakdown](#4-folder-structure--component-breakdown)
5. [Database Architecture & All 8 MongoDB Collections](#5-database-architecture--all-8-mongodb-collections)
6. [Core Features & Business Logic](#6-core-features--business-logic)
7. [Security & Data Privacy Implementation](#7-security--data-privacy-implementation)
8. [Testing & Quality Assurance (73 Automated Tests)](#8-testing--quality-assurance-73-automated-tests)
9. [Over-The-Air (OTA) Version Checker & Release News](#9-over-the-air-ota-version-checker--release-news)
10. [10-Slide Presentation Information (Full Content)](#10-10-slide-presentation-information-full-content)
11. [Top 15 Frequently Asked Viva Questions & Answers](#11-top-15-frequently-asked-viva-questions--answers)

---

## 1. Project Overview & The 1-Minute Viva Elevator Pitch

> *"Sir/Madam, 'Hisab' is a full-stack, cloud-synchronized personal financial ledger engineered to track informal debt and lending—money lent to or borrowed from friends, family, and colleagues.*
> 
> *The project consists of a native Android application built with Kotlin and Material Design 3, communicating over secure HTTPS REST APIs with a Node.js/Express cloud server deployed on Render, and backed by a MongoDB Atlas distributed cloud database.*
> 
> *What makes Hisab unique is its **Audit-Preserved Accounting Period Architecture**: unlike basic debt apps that simply overwrite a balance number to zero when someone settles up, Hisab archives the active accounting period, generates a permanent settlement audit receipt, and opens a clean new cycle starting at ₹0.*
> 
> *Furthermore, Hisab implements strict multi-tenant data isolation, integer-paise financial precision, 6-digit email OTP verification, in-app password recovery, and an automated Over-The-Air version check system."*

---

## 2. Problem Statement & Motivation

1. **Fragility of Paper Diaries ("Bahi Khata"):**  
   Traditional paper notebooks are vulnerable to physical damage, water spills, misplacement, and math calculation mistakes.
2. **Disorganization in Chat Apps (WhatsApp / SMS):**  
   People often write "Sent ₹500 for dinner" in WhatsApp chats, but messaging apps lack debit/credit calculation, running balance summaries, and settlement audit trails.
3. **Complexity of Enterprise Accounting Tools:**  
   Apps like Tally and QuickBooks are built for corporate GST filing, invoices, and business taxes—far too heavy and complex for everyday personal lending between friends and peers.
4. **The Hisab Solution:**  
   A lightweight, secure, cloud-backed notebook specifically designed for tracking lending, borrowing, and settlements with total transparency.

---

## 3. System Architecture (The 3-Tier Model)

```
┌─────────────────────────────────────────────────────────────┐
│                 TIER 1: CLIENT LAYER                        │
│  Native Android App (Kotlin, Material Design 3, Coroutines) │
│  - ViewBinding for type-safe UI interaction                 │
│  - Retrofit 2 + OkHttp 3 for TLS-encrypted REST API calls   │
│  - EncryptedSharedPreferences for local JWT token storage   │
└──────────────────────────────┬──────────────────────────────┘
                               │ HTTPS / TLS (Public Internet)
                               ▼
┌─────────────────────────────────────────────────────────────┐
│                 TIER 2: BACKEND APPLICATION                 │
│  Node.js + Express.js REST API (Hosted 24/7 on Render)      │
│  - JWT Bearer Authentication Middleware                     │
│  - Multi-tenant tenant-scoping ({ userId: req.user._id })   │
│  - Input sanitization & rate limiting                       │
│  - Brevo Cloud SMTP Relay (Port 2525) for 6-digit OTPs      │
└──────────────────────────────┬──────────────────────────────┘
                               │ Mongoose ODM / TLS connection
                               ▼
┌─────────────────────────────────────────────────────────────┐
│                 TIER 3: CLOUD DATABASE                      │
│  MongoDB Atlas Cloud Replica Set (AWS Datacenter)           │
│  - 8 Normalized collections                                 │
│  - Strict schema validation & indexing                      │
│  - 24/7 high availability with automated failover           │
└─────────────────────────────────────────────────────────────┘
```

### Why this architecture?
- **Device Independence:** Your phone talks to Render cloud. The developer's laptop can be turned off completely; the backend and database run 24/7 in the cloud.
- **Global Network Flexibility:** Works across 4G/5G mobile cellular data, college Wi-Fi, home Wi-Fi, or hotspots anywhere in the world.
- **Security:** The mobile phone never connects directly to MongoDB Atlas. All database access is mediated by the Node.js server with strict authentication.

---

## 4. Folder Structure & Component Breakdown

### A. Android Client (`app/`)
- `app/src/main/java/com/example/hisab/`
  - `MainActivity.kt`: Dashboard displaying 3-card overview (Receivables, Payables, Net Balance), search bar, person list, and What's New / Logout menu.
  - `AccountDetailActivity.kt`: Detailed ledger for an individual person, real-time balance badge, transaction stream, and "Settle Hisab" modal.
  - `TransactionHistoryActivity.kt`: Historical audit trail displaying past settled periods, payment notes, and timestamps.
  - `auth/`:
    - `LoginActivity.kt`: Email & password authentication issuing JWT session.
    - `RegisterActivity.kt`: Account registration with 6-digit OTP email challenge.
    - `VerifyOtpActivity.kt`: 6-digit OTP code verification screen.
    - `ForgotPasswordActivity.kt`: In-app password recovery trigger.
    - `ResetPasswordActivity.kt`: Enters 6-digit code and new password inside the app.
  - `api/`:
    - `ApiClient.kt`: Retrofit client configured with cloud URL `https://hisab-zovn.onrender.com/`.
    - `HisabApiService.kt`: REST API interface endpoints.
    - `ApiModels.kt`: Data Transfer Objects (DTOs) for serializing network payloads.
    - `SessionManager.kt`: Manages encrypted JWT token storage.
  - `util/`:
    - `CurrencyFormatter.kt`: Formats paise into rupee currency (`₹`).
    - `StatusBarUtil.kt`: Manages edge-to-edge status bar styling.
- `app/src/main/res/`:
  - `layout/`: Modern Material 3 XML layouts with card elevations and deep green styling.
  - `drawable/`: Vector graphics including `ic_hisab_logo.xml`.
  - `values/colors.xml`: Theme palette (`primary = #183B35`, `accent = #1F6B57`, `mint = #E1EDE9`).

### B. Cloud Backend (`backend/`)
- `src/server.js`: Server entry point. Reads `.env`, connects to MongoDB Atlas, and starts Express listener.
- `src/app.js`: Configures CORS, JSON parsers, rate limiting, and mounts API routes.
- `src/config/database.js`: Connects to MongoDB Atlas with auto-reconnect handling.
- `src/controllers/`:
  - `authController.js`: Registration, JWT login, OTP verification, and in-app password reset.
  - `personController.js`: CRUD operations on contacts and ledger history.
  - `transactionController.js`: Adding debts/credits and executing period settlements.
  - `appController.js`: Handles `GET /api/app/version` and `POST /api/app/version` for OTA update checks.
- `src/models/`: The 8 Mongoose schemas for MongoDB Atlas.
- `src/services/emailService.js`: Brevo SMTP mailer with port 2525 cloud firewall bypass.
- `test/`: 73 automated unit and integration tests.

---

## 5. Database Architecture & All 8 MongoDB Collections

| # | Collection | Description | Triggered When? |
|---|---|---|---|
| 1 | **`users`** | User profiles, Bcrypt-hashed passwords, email verification status. | User completes registration. |
| 2 | **`people`** | Contacts/entities with whom you share financial balances. | User taps "Add Person". |
| 3 | **`transactions`** | Individual credit/debit records (amount in paise, reason, date, periodId). | User taps "Add Transaction". |
| 4 | **`otpchallenges`** | 5-minute single-use 6-digit registration OTP codes. | User clicks "CONTINUE WITH OTP" on Register screen. |
| 5 | **`passwordresettokens`** | 15-minute single-use SHA-256 hashed 6-digit password reset codes. | User clicks "SEND RESET CODE" on Forgot Password screen. |
| 6 | **`accountperiods`** | Historical time chapters/folders keeping past settled cycles separate from active balances. | User clicks "MARK AS SETTLED". |
| 7 | **`settlements`** | Permanent payment receipts storing amount, date, and payment note (e.g. "Paid via GPay"). | User clicks "MARK AS SETTLED". |
| 8 | **`appupdates`** | Version numbers, "What's New" release notes, and APK download URLs for in-app version checks. | When admin publishes a new app release. |

---

## 6. Core Features & Business Logic

### A. Dual-Perspective Financial Ledger
- **"You will get" (Receivables):** Money you lent to others (positive balance).
- **"You will give" (Payables):** Money you borrowed from others (negative balance).
- **Net Balance:** `Total Receivables - Total Payables`. Instant overall financial standing.

### B. Integer Paise Precision
- Standard floating-point math in computers (`0.1 + 0.2 = 0.30000000000000004`) causes rounding bugs in financial apps.
- Hisab stores all currency as integer **paise** (`₹100.50` = `10050` paise).
- Floating-point errors are mathematically impossible.

### C. The "Settle Hisab" Architecture
1. When User taps **"Settle Hisab"** with a note (e.g., *"Cleared via UPI"*):
2. Current active `AccountPeriod` is locked with its final balance and marked `isClosed: true`.
3. A `Settlement` receipt document is created with the timestamp and note.
4. A new active `AccountPeriod` is opened with **₹0 balance**.
5. **Result:** The active balance resets to zero, but 100% of historical transactions are preserved forever in the History tab.

---

## 7. Security & Data Privacy Implementation

1. **Bcrypt Password Hashing:** Passwords salted with 10 rounds of Bcrypt; zero plaintext passwords stored.
2. **Strict Multi-Tenant Isolation:** Every query enforces `{ userId: req.user._id }`. Users can never read, modify, or delete another user's financial ledger.
3. **Stateless JWT Tokens:** 7-day cryptographically signed JSON Web Tokens stored securely on the mobile device.
4. **Cloud Firewall Port 2525 Bypass:** Render free tier blocks outbound ports 25, 465, and 587. Hisab automatically routes Brevo SMTP through port 2525, bypassing firewall drops.
5. **In-App Password Reset (Zero Web URLs):** Password resets are performed 100% in-app via a 6-digit code. Emails contain zero external web links to prevent phishing.

---

## 8. Testing & Quality Assurance (73 Automated Tests)

The backend includes a comprehensive automated test suite powered by the Node.js native test runner and Supertest:
- **Suite 1: App Version & Announcement Suite (2 tests)**: OTA updates & MongoDB persistence.
- **Suite 2: Account Recovery & Password Reset Suite (9 tests)**: Rate limits, 6-digit OTP verification, anti-enumeration.
- **Suite 3: Authentication & Registration Suite (6 tests)**: Duplicate prevention, OTP lifecycle.
- **Suite 4: Email Service Delivery & Provider Suite (15 tests)**: Brevo SMTP port 2525, HTML sanitization.
- **Suite 5: Server Health & Route Handlers (3 tests)**: HTTP 200, 404, and 503 database checks.
- **Suite 6: Cloud Ledger & Strict User Data Isolation Suite (38 tests)**: Cross-user hacking simulations, settlement verification.
- **Result:** **73 passed, 0 failed, 100% success rate.**

---

## 9. Over-The-Air (OTA) Version Checker & Release News

- **Endpoint:** `GET /api/app/version`
- **How it works:**
  1. On launch, the Android app reads `BuildConfig.VERSION_CODE` (e.g. `1`).
  2. Queries MongoDB Atlas for the highest active `versionCode`.
  3. If MongoDB has `versionCode > 1`, a dialog automatically appears:  
     *"🚀 New Update Available (v1.1.0)! What's New: ... [Download Update]"*.
  4. Clicking "Download Update" launches the browser directly to download the new APK.
  5. Users can also manually check anytime via the **"What's New & Updates"** menu option!

---

## 10. 10-Slide Presentation Information (Full Content)

### Slide 1: Title Slide
- **Title:** HISAB (हिसाब)
- **Subtitle:** Modern Cloud-Synchronized Personal Finance & Ledger Management System
- **Presenter:** Sanjana Dadaso Sapkal
- **Tech Stack:** Android (Kotlin) | Node.js (Express) | MongoDB Atlas | Render Cloud

### Slide 2: Problem Statement & Motivation
- **Fragile Traditional Bookkeeping:** Paper diaries are easily lost or damaged.
- **Disorganized Digital Notes:** WhatsApp messages lack balance tracking.
- **Overcomplicated Business Tools:** Tally and QuickBooks are built for corporate taxes, not peer lending.
- **The Hisab Vision:** A clean, cloud-synced digital notebook for peer-to-peer debts.

### Slide 3: Project Objectives & Core Capabilities
- **Dual-Party Ledger:** Tracks "You will get" vs. "You will give" with live net balances.
- **Audit-Preserved Settlements:** Clears active debt to ₹0 while preserving 100% of history.
- **Cloud Synchronization:** Continuous real-time sync with MongoDB Atlas.
- **In-App Security & Recovery:** 6-digit email OTPs and encrypted JWT sessions.

### Slide 4: System Architecture & Data Flow
- **Tier 1 (Android Client):** Native Kotlin, ViewBinding, Retrofit2, Coroutines.
- **Tier 2 (Cloud Backend):** Node.js / Express.js REST API hosted on Render.
- **Tier 3 (Cloud Database):** MongoDB Atlas distributed replica set in AWS.
- **Email Gateway:** Brevo SMTP Relay over cloud-safe port 2525.

### Slide 5: Android Application UI/UX
- **Visual Branding:** Deep forest green theme (`#183B35`), custom Hisab logo, splash screen.
- **Dashboard (`MainActivity`):** 3-card overview, search bar, add person dialog.
- **Account Ledger (`AccountDetailActivity`):** Live badges, reverse-chronological transaction feed.
- **Audit Trail (`TransactionHistoryActivity`):** Filterable settled periods with payment notes.

### Slide 6: Backend & RESTful API Architecture
- **Auth & Recovery APIs:** Registration with OTP, JWT login, in-app reset code (no web links).
- **People & Contact Ledger:** Full CRUD on `/api/people` with balance summation.
- **Transactions & Settlement Engine:** `/api/transactions` and `/api/transactions/settle`.
- **OTA Version Updates & Release News:** `GET /api/app/version` metadata directly from MongoDB.

### Slide 7: Security & Data Privacy Implementation
- **Multi-Tenant Data Isolation:** Every query enforces `{ userId: req.user._id }`.
- **Cryptographic Protection:** Bcrypt password hashing (10 rounds) and SHA-256 OTP hashing.
- **Anti-Enumeration:** Generic recovery responses prevent email harvesting.
- **Cloud Firewall Hardening:** Custom port 2525 routing to bypass Render port 587 block.

### Slide 8: Testing & Quality Assurance
- **100% Passing Automated Suite:** 73/73 unit and integration tests passing.
- **Isolation Attack Verification:** Automated tests prove User B cannot read User A's data (404).
- **Financial Precision Testing:** Verified balance addition, subtraction, and settlement zeroing.
- **Resiliency Testing:** Validated mock email fallbacks and cloud database reconnection.

### Slide 9: Cloud Deployment & DevOps Pipeline
- **Continuous Deployment (CI/CD):** Git push to `main` auto-deploys to Render cloud.
- **Cloud Infrastructure:** Linux container on Render; 3-node MongoDB Atlas replica set.
- **Global Access:** Works on any Wi-Fi or mobile data without developer laptop running.
- **Brevo Cloud Mail Gateway:** Verified SPF/DKIM for reliable OTP delivery.

### Slide 10: Conclusion & Future Scope
- **Key Deliverables:** Fully functional, cloud-synced financial ledger deployed in production.
- **Instant UPI Deep Linking:** Future integration to pay via Google Pay / PhonePe directly.
- **PDF & Excel Statement Exports:** Generate formal stamped transaction statements.
- **Group Expense Splitting:** Extend from 1-on-1 to group trip bill splitting.

---

## 11. Top 15 Frequently Asked Viva Questions & Answers

1. **Q: Why did you choose Kotlin over Java for Android?**  
   *A:* Kotlin offers null-safety (preventing NullPointerExceptions), concise syntax, extension functions, and native coroutines for asynchronous background network calls.

2. **Q: Why MongoDB instead of traditional SQL for the cloud?**  
   *A:* MongoDB's document model allows flexible schema evolution, high write throughput for financial streams, seamless JSON mapping with Node.js/Express, and effortless horizontal scaling with Atlas replica sets.

3. **Q: What is the purpose of the `accountperiods` collection?**  
   *A:* It separates past settled cycles from active current debts. When a balance is settled, the active period is closed, and a new period starting at ₹0 begins, preserving 100% of history for disputes.

4. **Q: Why do you store currency in paise instead of rupees with decimals?**  
   *A:* In computer programming, floating-point arithmetic (like `0.1 + 0.2`) produces precision errors (e.g. `0.30000000000000004`). By storing exact integers (10000 paise = ₹100), rounding errors are impossible.

5. **Q: How do you prevent User A from seeing User B's transactions?**  
   *A:* Multi-tenant scoping. Every backend query automatically appends `{ userId: req.user._id }` extracted from the verified JWT token.

6. **Q: What happens if Render puts the server to sleep after 15 minutes of inactivity?**  
   *A:* Render's free tier spins down idle containers to save power. When the app sends a new request, Render automatically wakes the server up within ~30 seconds. Data in MongoDB Atlas is permanently preserved.

7. **Q: Why did email delivery fail initially on Render and how did you solve it?**  
   *A:* Render's cloud firewall blocks outbound ports 25, 465, and 587 to prevent spam. We solved this by configuring Brevo's cloud-safe port **2525**, which successfully bypasses the firewall.

8. **Q: Why are password resets done in-app instead of via web links?**  
   *A:* Security and seamless mobile UX. A 6-digit code entered inside the app eliminates browser redirects and prevents email link interception or phishing.

9. **Q: What is the purpose of the `appupdates` collection in MongoDB?**  
   *A:* Since our APK is distributed outside the Google Play Store, `appupdates` allows the app to automatically detect new releases, display "What's New" highlights, and offer a one-tap APK download.

10. **Q: How does authentication work?**  
    *A:* Users register with their email, verify a 6-digit OTP, and log in. The server issues a signed JSON Web Token (JWT) that the Android client attaches as a Bearer token in the HTTP Authorization header.

11. **Q: What happens if a user enters the wrong OTP multiple times?**  
    *A:* `otpchallenges` tracks `attempts`. After 5 incorrect attempts, the challenge is invalidated to prevent brute-force attacks.

12. **Q: What is ViewBinding and why is it used?**  
    *A:* ViewBinding generates a binding class for each XML layout, replacing `findViewById`. It provides compile-time type safety and null safety.

13. **Q: What is Retrofit?**  
    *A:* Retrofit is a type-safe HTTP client for Android and Java developed by Square. It translates API interfaces into callable Kotlin suspend functions.

14. **Q: Can Hisab work if the user is in a different city or on 4G mobile data?**  
    *A:* Yes! The backend is deployed on public cloud servers (`hisab-zovn.onrender.com`), accessible globally from any internet connection.

15. **Q: How many automated tests do you have and what do they verify?**  
    *A:* 73 automated tests verifying authentication, security isolation, financial settlement accuracy, email delivery, and OTA version updates with a 100% pass rate.
