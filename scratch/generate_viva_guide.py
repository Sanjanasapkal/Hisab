import os
import docx
from docx import Document
from docx.shared import Inches, Pt, RGBColor
from docx.enum.text import WD_ALIGN_PARAGRAPH
from docx.enum.table import WD_TABLE_ALIGNMENT, WD_ALIGN_VERTICAL
from docx.oxml import OxmlElement, parse_xml
from docx.oxml.ns import nsdecls, qn

def set_cell_background(cell, hex_color):
    tcPr = cell._tc.get_or_add_tcPr()
    shd = parse_xml(f'<w:shd {nsdecls("w")} w:fill="{hex_color}"/>')
    tcPr.append(shd)

def set_cell_margins(cell, top=100, bottom=100, left=150, right=150):
    tcPr = cell._tc.get_or_add_tcPr()
    tcMar = OxmlElement('w:tcMar')
    for m, val in [('top', top), ('bottom', bottom), ('left', left), ('right', right)]:
        node = OxmlElement(f'w:{m}')
        node.set(qn('w:w'), str(val))
        node.set(qn('w:type'), 'dxa')
        tcMar.append(node)
    tcPr.append(tcMar)

def create_document():
    doc = Document()

    # Page Margins (1 inch all around)
    sections = doc.sections
    for section in sections:
        section.top_margin = Inches(1.0)
        section.bottom_margin = Inches(1.0)
        section.left_margin = Inches(1.0)
        section.right_margin = Inches(1.0)

    # Palette
    COLOR_PRIMARY = RGBColor(13, 59, 46)     # Deep Emerald (#0D3B2E)
    COLOR_SECONDARY = RGBColor(26, 117, 92)  # Medium Green (#1A755C)
    COLOR_DARK = RGBColor(33, 37, 41)        # Charcoal Text (#212529)
    COLOR_GRAY = RGBColor(108, 117, 125)     # Subtitle (#6C757D)
    HEX_HEADER_BG = "0D3B2E"
    HEX_ROW_ALT = "F8F9FA"
    HEX_CARD_BG = "E8F5E9"

    # Title
    title_p = doc.add_paragraph()
    title_p.paragraph_format.space_before = Pt(0)
    title_p.paragraph_format.space_after = Pt(4)
    title_p.alignment = WD_ALIGN_PARAGRAPH.CENTER
    title_run = title_p.add_run("HISAB – PERSONAL MONEY NOTEBOOK")
    title_run.font.name = "Arial"
    title_run.font.size = Pt(24)
    title_run.font.bold = True
    title_run.font.color.rgb = COLOR_PRIMARY

    sub_p = doc.add_paragraph()
    sub_p.paragraph_format.space_before = Pt(0)
    sub_p.paragraph_format.space_after = Pt(20)
    sub_p.alignment = WD_ALIGN_PARAGRAPH.CENTER
    sub_run = sub_p.add_run("Complete Viva Examination & Technical Project Defense Guide\nNative Android (Kotlin) + Node.js/Express + MongoDB Atlas Cloud")
    sub_run.font.name = "Arial"
    sub_run.font.size = Pt(12)
    sub_run.font.italic = True
    sub_run.font.color.rgb = COLOR_GRAY

    # Callout Box for Student Note
    callout_tbl = doc.add_table(rows=1, cols=1)
    callout_tbl.alignment = WD_TABLE_ALIGNMENT.CENTER
    cell = callout_tbl.cell(0, 0)
    set_cell_background(cell, "E0F2F1")
    set_cell_margins(cell, top=140, bottom=140, left=200, right=200)
    cp = cell.paragraphs[0]
    cp.paragraph_format.space_before = Pt(0)
    cp.paragraph_format.space_after = Pt(0)
    c_run = cp.add_run("💡 How to Use This Document for Your Viva:\n"
                       "This guide is written from absolute scratch in beginner-friendly, crystal-clear language. "
                       "It explains every single folder, database table/document, networking call, and architectural design decision. "
                       "Read this like a story—by the end, you will understand the system better than the examiner!")
    c_run.font.name = "Arial"
    c_run.font.size = Pt(10.5)
    c_run.font.color.rgb = COLOR_PRIMARY

    doc.add_paragraph().paragraph_format.space_after = Pt(12)

    # Helper function for adding headings
    def add_h1(text):
        h = doc.add_paragraph()
        h.paragraph_format.space_before = Pt(18)
        h.paragraph_format.space_after = Pt(6)
        h.paragraph_format.keep_with_next = True
        run = h.add_run(text)
        run.font.name = "Arial"
        run.font.size = Pt(16)
        run.font.bold = True
        run.font.color.rgb = COLOR_PRIMARY
        return h

    def add_h2(text):
        h = doc.add_paragraph()
        h.paragraph_format.space_before = Pt(12)
        h.paragraph_format.space_after = Pt(4)
        h.paragraph_format.keep_with_next = True
        run = h.add_run(text)
        run.font.name = "Arial"
        run.font.size = Pt(13)
        run.font.bold = True
        run.font.color.rgb = COLOR_SECONDARY
        return h

    def add_p(text, bold_prefix="", italic=False):
        p = doc.add_paragraph()
        p.paragraph_format.space_before = Pt(0)
        p.paragraph_format.space_after = Pt(5)
        p.paragraph_format.line_spacing = 1.15
        if bold_prefix:
            r_bold = p.add_run(bold_prefix)
            r_bold.font.name = "Arial"
            r_bold.font.size = Pt(10.5)
            r_bold.font.bold = True
            r_bold.font.color.rgb = COLOR_DARK
        run = p.add_run(text)
        run.font.name = "Arial"
        run.font.size = Pt(10.5)
        run.font.italic = italic
        run.font.color.rgb = COLOR_DARK
        return p

    def add_bullet(text, bold_prefix=""):
        p = doc.add_paragraph(style='List Bullet')
        p.paragraph_format.space_before = Pt(0)
        p.paragraph_format.space_after = Pt(3)
        p.paragraph_format.line_spacing = 1.15
        if bold_prefix:
            r_bold = p.add_run(bold_prefix)
            r_bold.font.name = "Arial"
            r_bold.font.size = Pt(10)
            r_bold.font.bold = True
            r_bold.font.color.rgb = COLOR_DARK
        run = p.add_run(text)
        run.font.name = "Arial"
        run.font.size = Pt(10)
        run.font.color.rgb = COLOR_DARK
        return p

    # =========================================================================
    # SECTION 1: PROJECT OVERVIEW & ELEVATOR PITCH
    # =========================================================================
    add_h1("1. Project Overview & The 1-Minute Viva Pitch")
    
    add_p("When the examiner asks: 'Tell me about your project in simple words', use this exact 1-minute pitch:")
    
    pitch_tbl = doc.add_table(rows=1, cols=1)
    pitch_tbl.alignment = WD_TABLE_ALIGNMENT.CENTER
    p_cell = pitch_tbl.cell(0, 0)
    set_cell_background(p_cell, "F1F8E9")
    set_cell_margins(p_cell, top=140, bottom=140, left=180, right=180)
    pitch_p = p_cell.paragraphs[0]
    pitch_p.paragraph_format.space_before = Pt(0)
    pitch_p.paragraph_format.space_after = Pt(0)
    pitch_run = pitch_p.add_run(
        "\"Sir/Madam, 'Hisab' is a full-stack, offline-first personal financial ledger designed to track informal debts—money lent to or borrowed from friends, family, and colleagues.\n\n"
        "The project consists of a native Android application built using Kotlin and XML, connected to a scalable Node.js/Express REST API and a MongoDB Atlas cloud database.\n\n"
        "What makes Hisab unique is its Accounting Period Architecture: unlike basic debt apps that simply erase records or overwrite balance numbers to zero when someone settles up, Hisab closes the accounting period, records a permanent immutable settlement audit trail, and opens a fresh accounting period starting at zero. "
        "Furthermore, Hisab implements an Offline-First Bi-directional Sync engine with SQLite and MongoDB, complete multi-user data isolation, and integer-paise precision to eliminate floating-point rounding errors.\""
    )
    pitch_run.font.name = "Arial"
    pitch_run.font.size = Pt(10.5)
    pitch_run.font.color.rgb = COLOR_PRIMARY

    doc.add_paragraph().paragraph_format.space_after = Pt(6)

    # =========================================================================
    # SECTION 2: HIGH-LEVEL ARCHITECTURE
    # =========================================================================
    add_h1("2. High-Level Architecture (The 3-Tier Model)")
    add_p("The project follows the industry-standard 3-Tier Enterprise Architecture:")

    add_bullet(" Client: Native Android Application written in Kotlin with Material Design XML. It features an embedded SQLite database for zero-latency local operations, ViewBinding for type-safe UI, and Retrofit 2 for HTTP communication.", "Tier 1 — ")
    add_bullet(" Server: Node.js with Express.js REST API. It handles business logic, JWT authentication, user input validation, password security with bcrypt, and transactional integrity.", "Tier 2 — ")
    add_bullet(" Database: MongoDB Atlas Cloud Cluster. A distributed NoSQL document store that permanently preserves user profiles, contacts, transactions, accounting periods, and settlement audit logs.", "Tier 3 — ")

    add_h2("Why this 3-Tier Architecture?")
    add_bullet("Decoupling & Scalability: The backend API is completely independent of Android. In the future, an iOS app or React web dashboard can be built that plugs into the exact same backend without changing a single line of backend code.", "1. ")
    add_bullet("Offline-First Reliability: A phone can lose Wi-Fi or mobile data at any moment. By keeping an SQLite database on the phone, the user can record transactions offline in a basement or flight. When connectivity returns, SyncManager pushes changes to MongoDB Atlas.", "2. ")
    add_bullet("Security: The mobile phone NEVER connects directly to MongoDB Atlas. Direct database connections from phones expose database passwords and IP addresses to hackers. Instead, the phone talks to our Express server via secure HTTPS/REST API with JWT tokens.", "3. ")

    # =========================================================================
    # SECTION 3: PROJECT FOLDER STRUCTURE
    # =========================================================================
    add_h1("3. Detailed Folder Structure – Why Every Folder Exists")
    add_p("The repository is cleanly split into two independent root directories: 'app/' (the Android client) and 'backend/' (the cloud server).")

    add_h2("A. The Backend Folder (Node.js & Express)")
    
    backend_table_data = [
        ("File / Folder", "Purpose & Role in the Project"),
        ("backend/src/server.js", "Entry point of the server. Reads .env, connects to MongoDB Atlas, and starts the HTTP server on port 5000 listening on 0.0.0.0."),
        ("backend/src/app.js", "Configures Express application middleware (express.json, cors, morgan logging) and binds route handlers."),
        ("backend/src/config/db.js", "Database connection module. Uses Mongoose to connect to the MongoDB Atlas cluster URI with retry handling."),
        ("backend/src/models/", "Mongoose Schemas defining the structure of documents in MongoDB Atlas: User.js, Person.js, Transaction.js, AccountPeriod.js, Settlement.js."),
        ("backend/src/routes/", "URL endpoints definition. Maps HTTP requests (GET, POST, PATCH, DELETE) to specific controller functions."),
        ("backend/src/controllers/", "Core business logic. Validates inputs, queries Mongoose models, computes balances, handles settlements, and returns JSON responses."),
        ("backend/src/middleware/authMiddleware.js", "Security gatekeeper. Intercepts incoming requests, extracts the JWT Bearer token from headers, verifies it, and attaches req.userId."),
        ("backend/test/", "Automated integration test suites using Node.js native test runner (41 tests verifying auth, ledger, and isolation)."),
        ("backend/.env", "Secrets & configuration file (PORT=5000, MONGO_URI, JWT_SECRET, OTP settings). Never committed to public GitHub.")
    ]
    
    t_be = doc.add_table(rows=len(backend_table_data), cols=2)
    t_be.alignment = WD_TABLE_ALIGNMENT.CENTER
    for i, row in enumerate(backend_table_data):
        c0, c1 = t_be.cell(i, 0), t_be.cell(i, 1)
        c0.text, c1.text = row[0], row[1]
        set_cell_margins(c0, 60, 60, 100, 100)
        set_cell_margins(c1, 60, 60, 100, 100)
        if i == 0:
            set_cell_background(c0, HEX_HEADER_BG)
            set_cell_background(c1, HEX_HEADER_BG)
            c0.paragraphs[0].runs[0].font.bold = True
            c0.paragraphs[0].runs[0].font.color.rgb = RGBColor(255, 255, 255)
            c1.paragraphs[0].runs[0].font.bold = True
            c1.paragraphs[0].runs[0].font.color.rgb = RGBColor(255, 255, 255)
        elif i % 2 == 1:
            set_cell_background(c0, HEX_ROW_ALT)
            set_cell_background(c1, HEX_ROW_ALT)

    doc.add_paragraph().paragraph_format.space_after = Pt(8)

    add_h2("B. The Android Folder (Native Kotlin)")
    
    android_table_data = [
        ("Package / Folder", "Purpose & Architectural Responsibility"),
        ("com.example.hisab.auth", "Activities handling user onboarding: LoginActivity, RegisterActivity, VerifyOtpActivity, ForgotPasswordActivity, etc."),
        ("com.example.hisab.data", "Data access layer. Contains DatabaseHelper (SQLite tables & schema), PersonRepository, TransactionRepository, and SyncManager."),
        ("com.example.hisab.api", "Networking layer. HisabApiService (Retrofit interface), ApiClient (OkHttp client with AuthInterceptor), SessionManager (EncryptedSharedPreferences), ApiModels (DTOs)."),
        ("com.example.hisab.adapter", "RecyclerView adapters: PersonAdapter (dashboard contact list), TransactionAdapter (person entries), HistoryAdapter (past closed periods)."),
        ("com.example.hisab.model", "Kotlin data classes: Person, Transaction, AccountPeriod, Settlement, DashboardSummary."),
        ("com.example.hisab.util", "Utility helpers: CurrencyFormatter (converts signed integer paise to ₹Rupees format), DateFormatter, StatusBarUtil (handles edge-to-edge window insets)."),
        ("app/src/main/res/layout/", "XML UI screen definitions using ConstraintLayout, CoordinatorLayout, MaterialCardView, and MaterialButton."),
        ("app/src/main/res/xml/network_security_config.xml", "Allows cleartext HTTP traffic to local subnet IPs (192.168.x.x and 10.0.2.2) so phone can talk to dev PC.")
    ]

    t_and = doc.add_table(rows=len(android_table_data), cols=2)
    t_and.alignment = WD_TABLE_ALIGNMENT.CENTER
    for i, row in enumerate(android_table_data):
        c0, c1 = t_and.cell(i, 0), t_and.cell(i, 1)
        c0.text, c1.text = row[0], row[1]
        set_cell_margins(c0, 60, 60, 100, 100)
        set_cell_margins(c1, 60, 60, 100, 100)
        if i == 0:
            set_cell_background(c0, HEX_HEADER_BG)
            set_cell_background(c1, HEX_HEADER_BG)
            c0.paragraphs[0].runs[0].font.bold = True
            c0.paragraphs[0].runs[0].font.color.rgb = RGBColor(255, 255, 255)
            c1.paragraphs[0].runs[0].font.bold = True
            c1.paragraphs[0].runs[0].font.color.rgb = RGBColor(255, 255, 255)
        elif i % 2 == 1:
            set_cell_background(c0, HEX_ROW_ALT)
            set_cell_background(c1, HEX_ROW_ALT)

    doc.add_paragraph().paragraph_format.space_after = Pt(10)

    # =========================================================================
    # SECTION 4: HOW DATA IS STORED IN BOTH DATABASES
    # =========================================================================
    add_h1("4. The Two Databases: What is Stored in Each Document/Table?")
    
    add_p("A very common viva question is: 'What database are you using, and what are the tables/collections?' In Hisab, we use BOTH a local relational database (SQLite) and a cloud NoSQL database (MongoDB Atlas).")

    add_h2("A. MongoDB Atlas Cloud Collections (NoSQL Documents)")
    add_bullet(" users: Stores registered account holders. Fields: _id, username, email, passwordHash (hashed with bcrypt), isVerified (boolean), emailOtp, otpExpiresAt, createdAt.", "1. ")
    add_bullet(" people: Stores the contacts created by each user. Fields: _id, ownerUserId (references User), name (e.g. 'Sakshi'), archived (boolean for soft delete), clientLocalId, createdAt.", "2. ")
    add_bullet(" accountperiods: Represents accounting cycles. Fields: _id, ownerUserId, personId (references Person), startedAt, closedAt, openingBalancePaise (usually 0), closingBalancePaise, status ('open' or 'closed').", "3. ")
    add_bullet(" transactions: Individual lending/borrowing records. Fields: _id, ownerUserId, personId, periodId (references open AccountPeriod), amountPaise (signed integer), reason (e.g. 'Dinner'), transactionDate, notes, createdAt.", "4. ")
    add_bullet(" settlements: Audit receipts created when an account is settled. Fields: _id, ownerUserId, personId, periodId (the period that was closed), finalBalancePaise (the balance cleared), settledAt, note (e.g. 'Paid via cash'), clientLocalId.", "5. ")

    add_h2("B. Local SQLite Database Tables (On the Physical Phone)")
    add_bullet("TABLE_PEOPLE (people): id, remote_id (MongoDB _id), name, created_at, updated_at, archived.", "• ")
    add_bullet("TABLE_ACCOUNT_PERIODS (account_periods): id, person_id, started_at, closed_at, opening_balance_paise, closing_balance_paise, status ('open'/'closed').", "• ")
    add_bullet("TABLE_TRANSACTIONS (transactions): id, remote_id (MongoDB _id), person_id, period_id, amount_paise, reason, transaction_date, notes.", "• ")
    add_bullet("TABLE_SETTLEMENTS (settlements): id, person_id, period_id, final_balance_paise, settled_at, note.", "• ")

    add_h2("C. Critical Design Question: Why Store Money in Integer Paise Instead of Rupees?")
    add_p("If the examiner asks: 'Why is your amount stored as amountPaise (Long) instead of float or double in rupees?' Answer this:", bold_prefix="Viva Tip: ")
    add_p("In computer science, floating-point numbers (float/double) cannot precisely represent decimal fractions in base-2 binary arithmetic. For example, in JavaScript or Java, '0.1 + 0.2' equals '0.30000000000000004' instead of '0.3'. In financial applications, this causes pennies to appear or disappear due to rounding errors! By storing all amounts in integer paise (1 Rupee = 100 paise), ₹50.50 is stored as integer '5050'. Integer arithmetic has ZERO precision loss. When displaying to the user, CurrencyFormatter divides by 100 and formats it with the ₹ symbol.")

    # =========================================================================
    # SECTION 5: STEP-BY-STEP DATA JOURNEY (TRACE OF A REQUEST)
    # =========================================================================
    add_h1("5. Step-by-Step Data Journey: 'How the App Talks to the Backend & MongoDB'")

    add_h2("Scenario 1: Adding a Transaction (Lending ₹100 to Sakshi)")
    add_bullet("User Action: The user types '100' and 'Lunch at cafe' in AddTransactionActivity and taps 'Save'.", "Step 1: ")
    add_bullet("Local Validation: CurrencyFormatter.parseRupeesToPaise('100') converts ₹100 into integer 10000 paise. Reason is checked for non-empty string.", "Step 2: ")
    add_bullet("Immediate SQLite Write: TransactionRepository.addTransaction() starts an SQLite transaction (`db.beginTransaction()`), finds Sakshi's active open period, and inserts a row into TABLE_TRANSACTIONS. The UI updates INSTANTLY with 0ms latency!", "Step 3: ")
    add_bullet("Cloud Synchronization Triggered: A coroutine launches SyncManager.sync(applicationContext).", "Step 4: ")
    add_bullet("HTTP Request Built: Retrofit 2 serializes a CreateTransactionRequest into JSON: {\"personId\": \"6abd...\", \"amountPaise\": 10000, \"reason\": \"Lunch at cafe\"}.", "Step 5: ")
    add_bullet("Network Interceptor: OkHttp AuthInterceptor automatically injects the HTTP Header: 'Authorization: Bearer <JWT_TOKEN>'.", "Step 6: ")
    add_bullet("Network Transmission: The phone sends an HTTP POST to http://192.168.10.129:5000/api/transactions over Wi-Fi.", "Step 7: ")
    add_bullet("Express Routing & Middleware: backend/src/server.js passes the request to authMiddleware.js. The middleware verifies the JWT signature. If valid, it extracts the authenticated user's ID into req.userId.", "Step 8: ")
    add_bullet("Controller Business Logic: transactionController.addTransaction validates that the person belongs to req.userId, finds the active open AccountPeriod for Sakshi in MongoDB Atlas, and creates a new Transaction document using Mongoose.", "Step 9: ")
    add_bullet("MongoDB Atlas Cloud Storage: MongoDB writes the document into the 'transactions' collection on Cluster0 and returns a generated _id (e.g. '67a8b9c...').", "Step 10: ")
    add_bullet("Client ID Reconciliation: Express responds with HTTP 201 Created and the new transaction object. The Android SyncManager receives the response and updates the SQLite row's remote_id with MongoDB's _id. Both databases are now perfectly in sync!", "Step 11: ")

    add_h2("Scenario 2: Settling an Account ('Settle Hisab')")
    add_p("When Sakshi pays back the ₹100 and the user taps 'Settle Hisab':")
    add_bullet("Local Period Closed: TransactionRepository.settleHisab() computes the closing balance (+₹100), marks the SQLite open period as 'closed', inserts a Settlement record, and starts a brand new open period with opening balance = 0.", "1. ")
    add_bullet("Cloud API Call: PersonDetailActivity calls ApiClient.getService().settleHisab(SettleRequest(personId, note)).", "2. ")
    add_bullet("MongoDB Period Closed: The backend finds Sakshi's open AccountPeriod in MongoDB, sets its status to 'closed', sets closingBalancePaise = 10000, and saves it.", "3. ")
    add_bullet("MongoDB Settlement Created: A new document is written into the MongoDB 'settlements' collection containing finalBalancePaise, settledAt timestamp, and user note.", "4. ")
    add_bullet("New Open Period in Cloud: A new AccountPeriod document is created in MongoDB with status: 'open' and openingBalancePaise: 0. Both databases now have zero active balance while preserving 100% of historical transactions!", "5. ")

    # =========================================================================
    # SECTION 6: KEY ARCHITECTURAL & UI HIGHLIGHTS
    # =========================================================================
    add_h1("6. Key Engineering Innovations & Design Decisions")

    add_h2("1. The Accounting Period Architecture")
    add_p("Why didn't we just set the person's balance to 0 in the database when they settle up?")
    add_p("In traditional naive accounting apps, when a friend pays you back, people either delete all transactions or set the balance column to 0. This completely destroys financial history! You can never look back and see what they borrowed last month. "
          "In Hisab, transactions belong to an 'AccountPeriod'. Settling closes the period like a financial quarter, saves a permanent settlement receipt, and starts Period 2 at ₹0.00. The History screen allows users to inspect every previous settled period!")

    add_h2("2. Multi-User Local Database Isolation")
    add_p("What happens if User A logs out and User B logs in on the SAME phone?")
    add_p("DatabaseHelper dynamically scopes SQLite databases using the user's ID: 'hisab_{userId}.db'. User A's data is stored in 'hisab_userA.db' and User B's data is stored in 'hisab_userB.db'. Even if two users share the same Android phone, they can NEVER see each other's financial records!")

    add_h2("3. Edge-to-Edge Status Bar Integration (Android 15 Target SDK 36)")
    add_p("In Android 15, the OS enforces edge-to-edge drawing, which can cause status bar icons (clock, battery, Wi-Fi) to overlap top toolbars. We created StatusBarUtil.kt using WindowInsetsCompat to dynamically query the physical device's status bar and camera punch-hole height, sliding toolbar content down while seamlessly extending the brand's dark emerald background behind the system bars.")

    # =========================================================================
    # SECTION 7: TOP 15 VIVA QUESTIONS & MODEL ANSWERS
    # =========================================================================
    add_h1("7. Top 15 Viva Questions & Ready-to-Answer Responses")

    viva_qas = [
        ("Q1: What is the tech stack of your project?",
         "Frontend: Native Android (Kotlin), Material Design XML, SQLite database, Retrofit 2 REST client.\n"
         "Backend: Node.js, Express.js framework, Mongoose ODM.\n"
         "Database: MongoDB Atlas Cloud (Multi-region replica set) and local SQLite on the device."),

        ("Q2: Why did you choose Kotlin instead of Java for Android?",
         "Kotlin provides modern features like null-safety (eliminating NullPointerExceptions), concise data classes, extension functions, and native Coroutines for asynchronous non-blocking background operations."),

        ("Q3: What is the difference between SQLite and MongoDB in your project?",
         "SQLite is an embedded relational SQL database that runs locally inside the Android phone for instant offline access.\n"
         "MongoDB Atlas is a cloud-hosted NoSQL document database that securely stores all users' data centrally, allowing cross-device synchronization and permanent cloud backup."),

        ("Q4: How do you authenticate users securely?",
         "We use JSON Web Tokens (JWT). When a user logs in, the backend verifies their password using bcrypt and generates a signed JWT token. The phone saves this token in EncryptedSharedPreferences. Every subsequent HTTP request includes the token in the 'Authorization: Bearer <token>' header. The server verifies this token using a secret key before allowing access."),

        ("Q5: Why did you use bcrypt for passwords?",
         "Plain text passwords can be stolen in a database breach. Bcrypt is a cryptographic hashing algorithm with a salt and configurable work factor (cost). It produces a one-way hash that cannot be decrypted, protecting users even if the database is compromised."),

        ("Q6: How does offline mode work?",
         "Every time the user creates a person, transaction, or settlement, it is written immediately to SQLite on the phone. The user never experiences loading spinners or network delays. When network connectivity is available, SyncManager automatically pushes unsynced items to MongoDB Atlas and pulls any remote updates."),

        ("Q7: How does your app prevent duplicate records during synchronization?",
         "Every local SQLite record has a 'remote_id' column that stores the corresponding MongoDB '_id'. Before pushing or pulling, SyncManager checks if a record with that remote_id already exists locally or on Atlas. If it does, it updates the record instead of creating a duplicate."),

        ("Q8: What is ViewBinding in Android and why use it?",
         "ViewBinding generates a binding class for each XML layout. It replaces old 'findViewById' and provides compile-time type safety and null safety, preventing runtime crashes caused by invalid view IDs."),

        ("Q9: What happens when an account is settled?",
         "Instead of deleting records or zeroing out rows, the system closes the current active AccountPeriod, saves a Settlement document with the final balance and timestamp, and opens a new AccountPeriod starting at 0 paise. This preserves 100% of financial audit history."),

        ("Q10: Why did you store money in integer paise rather than rupees as float?",
         "Binary floating-point arithmetic (float/double) suffers from rounding precision errors (e.g. 0.1 + 0.2 = 0.30000000000000004). In banking and financial apps, using integer paise (₹1 = 100 paise) guarantees exact integer arithmetic with zero rounding discrepancies."),

        ("Q11: What is the signed amount convention used in your transactions?",
         "A positive amount (+paise) means the other person owes me money. A negative amount (-paise) means I owe the other person money. Calculating the net balance is as simple as running a SQL SUM(amount_paise) query."),

        ("Q12: How do you protect user data from other users?",
         "Through strict tenant isolation. In MongoDB, every document has an 'ownerUserId' foreign key. The backend queries always include '{ ownerUserId: req.userId }'. On the phone, each user gets an independent SQLite database file: 'hisab_{userId}.db'."),

        ("Q13: How does the phone communicate with the backend during development?",
         "The backend server listens on 0.0.0.0:5000 on the local development PC. The physical Android phone connects to the same Wi-Fi network and communicates with the backend via the PC's LAN IP (http://192.168.x.x:5000). Android's network_security_config.xml permits cleartext HTTP for local IP ranges."),

        ("Q14: What is Mongoose and why did you use it with MongoDB?",
         "Mongoose is an Object Data Modeling (ODM) library for MongoDB and Node.js. It provides schema validation, type casting, default values, and query building, making MongoDB interactions structured and robust."),

        ("Q15: What was the recent bug you fixed regarding status bar overlap and button clipping?",
         "On Android 15 (Target SDK 36), edge-to-edge layout is mandatory, causing the system status bar to overlap top toolbars. We created StatusBarUtil using WindowInsetsCompat to dynamically slide toolbars below the status bar and camera punch-hole. We also fixed the action buttons by removing all-caps (which caused text wrapping) and allowing dynamic height with proper internal padding so text never clips.")
    ]

    for q, a in viva_qas:
        add_h2(q)
        add_p(a)

    # Save document
    output_path = r"c:\Users\HP\AndroidStudioProjects\Hisab\Hisab_Project_Viva_Complete_Guide.docx"
    doc.save(output_path)
    print(f"Document successfully created at: {output_path}")

if __name__ == "__main__":
    create_document()
