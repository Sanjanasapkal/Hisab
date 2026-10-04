# Hisab (हिसाब) — Personal Finance & Digital Ledger App

[![Kotlin](https://img.shields.io/badge/Kotlin-1.9.0-purple.svg)](https://kotlinlang.org)
[![Android](https://img.shields.io/badge/Platform-Android%20(API%2026%2B)-green.svg)](https://developer.android.com)
[![Node.js](https://img.shields.io/badge/Backend-Node.js%20%7C%20Express-brightgreen.svg)](https://nodejs.org)
[![Database](https://img.shields.io/badge/Database-MongoDB%20Atlas%20%2B%20SQLite-forestgreen.svg)](https://www.mongodb.com/atlas)
[![Email](https://img.shields.io/badge/Email%20Service-Brevo%20SMTP-blue.svg)](https://www.brevo.com)

**Hisab** is a full-stack, enterprise-grade digital ledger and personal debt tracking application designed to help individuals and small businesses keep accurate records of money lent (*You will get*) and borrowed (*You will give*). 

Built with an **offline-first architecture**, it ensures seamless instant bookkeeping on local SQLite, backed by **automatic real-time cloud synchronization** with MongoDB Atlas.

---

## ✨ Key Features

- 🔐 **Modern Email Authentication**:
  - Pure email and password login (no username collision or verification hassle).
  - 6-digit numeric OTP email verification delivered via **Brevo SMTP relay**.
  - Secure in-app password reset with token hashing and expiration limits.
  - Industry-standard **JWT (JSON Web Tokens)** with encrypted Android keystore session storage.

- ☁️ **Automatic Real-Time Cloud Sync**:
  - Full bi-directional synchronization between local **SQLite** and **MongoDB Atlas**.
  - Automatically syncs whenever a contact or transaction is added, edited, settled, or deleted.
  - Offline-first: Full offline functionality with automated queue resolution on network reconnect.

- 💰 **Exact Financial Calculations**:
  - Uses exact integer **paise** storage to prevent floating-point rounding errors.
  - Dual perspective: tracks "You will get" (lent) and "You will give" (borrowed) alongside a live Net Balance.

- 📜 **Period-Based Settlement Accounting**:
  - Accounts can be marked as settled with custom notes (e.g., *Paid via UPI* or *Cash*).
  - Preserves complete immutable transaction history across settled periods for audit trails.

- 👥 **Multi-User Isolation**:
  - Strict data isolation on both SQLite (independent local user databases) and MongoDB Atlas.
  - Zero cross-tenant data leakage.

---

## 🛠️ Technology Stack

| Layer | Technologies |
| :--- | :--- |
| **Android Client** | Kotlin, Material Design 3, View Binding, Coroutines & Flow, Retrofit2, OkHttp3, EncryptedSharedPreferences |
| **Local Database** | SQLite (Offline-first data layer) |
| **Backend API** | Node.js, Express.js, Mongoose, Bcrypt.js, Express Rate Limiting |
| **Cloud Database** | MongoDB Atlas (Distributed multi-tenant cluster) |
| **Email Service** | Brevo SMTP Relay (Transactional OTP & password recovery emails) |

---

## 📁 Project Architecture

```text
Hisab/
├── app/                          # Native Android Application (Kotlin)
│   ├── src/main/java/com/example/hisab/
│   │   ├── api/                  # Retrofit API clients & SessionManager
│   │   ├── auth/                 # Login, Register, OTP verification & Password reset
│   │   ├── data/                 # SQLite DatabaseHelper, Repositories, SyncManager
│   │   ├── model/                # Data models (Person, Transaction, Settlement)
│   │   └── util/                 # Currency formatters, Status bar utilities
│   └── src/main/res/             # Material 3 XML layouts, drawables, and themes
│
└── backend/                      # Cloud Backend Service (Node.js + Express)
    ├── src/
    │   ├── config/               # Database connection (MongoDB Atlas)
    │   ├── controllers/          # Auth, People, and Transaction controllers
    │   ├── models/               # Mongoose schemas (User, Person, Transaction, OtpChallenge)
    │   ├── routes/               # Express REST API routes
    │   ├── services/             # Email delivery service & Token service
    │   └── validators/           # Strict payload validation & sanitization
    ├── test/                     # 70 automated integration & security test suites
    └── server.js                 # Server entry point
```

---

## 🚀 Getting Started

### 1. Backend Setup
1. Open a terminal in the `backend/` folder:
   ```bash
   cd backend
   npm install
   ```
2. Copy `.env.example` to `.env` and configure your credentials:
   ```bash
   cp .env.example .env
   ```
3. Run tests to verify the setup:
   ```bash
   npm test
   ```
4. Start the server:
   ```bash
   npm start
   ```

### 2. Android App Setup
1. Open the project in **Android Studio** (Hedgehog or newer recommended).
2. Connect an Android device or start an Android Emulator.
3. If connecting to a local backend over Wi-Fi, tap the **Gear icon (⚙️)** on the Login screen and enter your PC's IP (e.g., `http://192.168.1.100:5000/`).
4. Click **Run ▶️** to build and install the APK.

---

## 🔒 Security Measures
- Passwords securely hashed with **Bcrypt** (Salt rounds = 12).
- Email verification codes expire within **5 minutes** (single-use enforcement).
- IP rate limiting protects authentication endpoints against brute-force attacks.
- JWT tokens signed with secure secrets and stored locally in hardware-backed **EncryptedSharedPreferences**.
- Sensitive environment variables and API keys strictly excluded via `.gitignore`.

---

## 👩‍💻 Author
- **Sakshi Santosh Shinde**
