package com.example.hisab.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/**
 * SQLite Database Manager for the Hisab application.
 *
 * Designed with a clean relational schema supporting accounting periods and settlements.
 *
 * Why Account Periods?
 * When people settle up, simply setting a balance number to 0 or deleting rows
 * destroys financial history. In Hisab, transactions belong to an [AccountPeriod].
 * When a user taps "Settle Hisab", the current period is closed and recorded in [Settlements],
 * and a new open period is created starting at ₹0.00. This preserves the complete
 * history without affecting the active balance!
 */
class DatabaseHelper private constructor(context: Context, dbName: String = DATABASE_NAME) :
    SQLiteOpenHelper(context.applicationContext, dbName, null, DATABASE_VERSION) {

    companion object {
        const val DATABASE_NAME = "hisab.db"
        const val DATABASE_VERSION = 2

        // Table: People
        const val TABLE_PEOPLE = "people"
        const val COL_PEOPLE_ID = "id"
        const val COL_PEOPLE_REMOTE_ID = "remote_id"
        const val COL_PEOPLE_NAME = "name"
        const val COL_PEOPLE_CREATED_AT = "created_at"
        const val COL_PEOPLE_UPDATED_AT = "updated_at"
        const val COL_PEOPLE_ARCHIVED = "archived"

        // Table: Account Periods
        const val TABLE_ACCOUNT_PERIODS = "account_periods"
        const val COL_PERIODS_ID = "id"
        const val COL_PERIODS_PERSON_ID = "person_id"
        const val COL_PERIODS_STARTED_AT = "started_at"
        const val COL_PERIODS_CLOSED_AT = "closed_at"
        const val COL_PERIODS_OPENING_BALANCE_PAISE = "opening_balance_paise"
        const val COL_PERIODS_CLOSING_BALANCE_PAISE = "closing_balance_paise"
        const val COL_PERIODS_STATUS = "status"

        // Table: Transactions
        const val TABLE_TRANSACTIONS = "transactions"
        const val COL_TRANS_ID = "id"
        const val COL_TRANS_REMOTE_ID = "remote_id"
        const val COL_TRANS_PERSON_ID = "person_id"
        const val COL_TRANS_PERIOD_ID = "period_id"
        const val COL_TRANS_AMOUNT_PAISE = "amount_paise"
        const val COL_TRANS_REASON = "reason"
        const val COL_TRANS_TRANSACTION_DATE = "transaction_date"
        const val COL_TRANS_CREATED_AT = "created_at"
        const val COL_TRANS_UPDATED_AT = "updated_at"
        const val COL_TRANS_NOTES = "notes"

        // Table: Settlements
        const val TABLE_SETTLEMENTS = "settlements"
        const val COL_SETTLE_ID = "id"
        const val COL_SETTLE_PERSON_ID = "person_id"
        const val COL_SETTLE_PERIOD_ID = "period_id"
        const val COL_SETTLE_FINAL_BALANCE_PAISE = "final_balance_paise"
        const val COL_SETTLE_SETTLED_AT = "settled_at"
        const val COL_SETTLE_NOTE = "note"

        private val instances = mutableMapOf<String, DatabaseHelper>()

        /**
         * Returns DatabaseHelper instance scoped to the currently authenticated user
         * for strict local multi-user data isolation.
         */
        fun getInstance(context: Context): DatabaseHelper {
            val sessionManager = com.example.hisab.api.SessionManager.getInstance(context)
            val userId = sessionManager.getUserId()
            val dbName = if (!userId.isNullOrBlank()) "hisab_${userId}.db" else DATABASE_NAME
            return getInstance(context, dbName)
        }

        fun getInstance(context: Context, customDbName: String): DatabaseHelper {
            return synchronized(this) {
                instances.getOrPut(customDbName) {
                    DatabaseHelper(context, customDbName)
                }
            }
        }
    }

    override fun onConfigure(db: SQLiteDatabase) {
        super.onConfigure(db)
        // Enable foreign key constraints for SQLite
        db.setForeignKeyConstraintsEnabled(true)
    }

    override fun onCreate(db: SQLiteDatabase) {
        // 1. Create People table
        val createPeopleTable = """
            CREATE TABLE $TABLE_PEOPLE (
                $COL_PEOPLE_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_PEOPLE_REMOTE_ID TEXT,
                $COL_PEOPLE_NAME TEXT NOT NULL,
                $COL_PEOPLE_CREATED_AT INTEGER NOT NULL,
                $COL_PEOPLE_UPDATED_AT INTEGER NOT NULL,
                $COL_PEOPLE_ARCHIVED INTEGER NOT NULL DEFAULT 0
            );
        """.trimIndent()
        db.execSQL(createPeopleTable)

        // 2. Create Account Periods table
        val createAccountPeriodsTable = """
            CREATE TABLE $TABLE_ACCOUNT_PERIODS (
                $COL_PERIODS_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_PERIODS_PERSON_ID INTEGER NOT NULL,
                $COL_PERIODS_STARTED_AT INTEGER NOT NULL,
                $COL_PERIODS_CLOSED_AT INTEGER,
                $COL_PERIODS_OPENING_BALANCE_PAISE INTEGER NOT NULL DEFAULT 0,
                $COL_PERIODS_CLOSING_BALANCE_PAISE INTEGER,
                $COL_PERIODS_STATUS TEXT NOT NULL DEFAULT 'open',
                FOREIGN KEY ($COL_PERIODS_PERSON_ID) REFERENCES $TABLE_PEOPLE($COL_PEOPLE_ID) ON DELETE CASCADE
            );
        """.trimIndent()
        db.execSQL(createAccountPeriodsTable)

        // 3. Create Transactions table
        val createTransactionsTable = """
            CREATE TABLE $TABLE_TRANSACTIONS (
                $COL_TRANS_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_TRANS_REMOTE_ID TEXT,
                $COL_TRANS_PERSON_ID INTEGER NOT NULL,
                $COL_TRANS_PERIOD_ID INTEGER NOT NULL,
                $COL_TRANS_AMOUNT_PAISE INTEGER NOT NULL,
                $COL_TRANS_REASON TEXT NOT NULL,
                $COL_TRANS_TRANSACTION_DATE INTEGER NOT NULL,
                $COL_TRANS_CREATED_AT INTEGER NOT NULL,
                $COL_TRANS_UPDATED_AT INTEGER NOT NULL,
                $COL_TRANS_NOTES TEXT,
                FOREIGN KEY ($COL_TRANS_PERSON_ID) REFERENCES $TABLE_PEOPLE($COL_PEOPLE_ID) ON DELETE CASCADE,
                FOREIGN KEY ($COL_TRANS_PERIOD_ID) REFERENCES $TABLE_ACCOUNT_PERIODS($COL_PERIODS_ID) ON DELETE CASCADE
            );
        """.trimIndent()
        db.execSQL(createTransactionsTable)

        // 4. Create Settlements table
        val createSettlementsTable = """
            CREATE TABLE $TABLE_SETTLEMENTS (
                $COL_SETTLE_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_SETTLE_PERSON_ID INTEGER NOT NULL,
                $COL_SETTLE_PERIOD_ID INTEGER NOT NULL,
                $COL_SETTLE_FINAL_BALANCE_PAISE INTEGER NOT NULL,
                $COL_SETTLE_SETTLED_AT INTEGER NOT NULL,
                $COL_SETTLE_NOTE TEXT,
                FOREIGN KEY ($COL_SETTLE_PERSON_ID) REFERENCES $TABLE_PEOPLE($COL_PEOPLE_ID) ON DELETE CASCADE,
                FOREIGN KEY ($COL_SETTLE_PERIOD_ID) REFERENCES $TABLE_ACCOUNT_PERIODS($COL_PERIODS_ID) ON DELETE CASCADE
            );
        """.trimIndent()
        db.execSQL(createSettlementsTable)

        // 5. Create performance indexes
        db.execSQL("CREATE INDEX idx_people_remote_id ON $TABLE_PEOPLE ($COL_PEOPLE_REMOTE_ID);")
        db.execSQL("CREATE INDEX idx_account_periods_person_id ON $TABLE_ACCOUNT_PERIODS ($COL_PERIODS_PERSON_ID);")
        db.execSQL("CREATE INDEX idx_transactions_remote_id ON $TABLE_TRANSACTIONS ($COL_TRANS_REMOTE_ID);")
        db.execSQL("CREATE INDEX idx_transactions_person_id ON $TABLE_TRANSACTIONS ($COL_TRANS_PERSON_ID);")
        db.execSQL("CREATE INDEX idx_transactions_period_id ON $TABLE_TRANSACTIONS ($COL_TRANS_PERIOD_ID);")
        db.execSQL("CREATE INDEX idx_settlements_person_id ON $TABLE_SETTLEMENTS ($COL_SETTLE_PERSON_ID);")
        db.execSQL("CREATE INDEX idx_settlements_period_id ON $TABLE_SETTLEMENTS ($COL_SETTLE_PERIOD_ID);")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            try {
                db.execSQL("ALTER TABLE $TABLE_PEOPLE ADD COLUMN $COL_PEOPLE_REMOTE_ID TEXT;")
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_people_remote_id ON $TABLE_PEOPLE ($COL_PEOPLE_REMOTE_ID);")
            } catch (e: Exception) {
                // Column may already exist
            }
            try {
                db.execSQL("ALTER TABLE $TABLE_TRANSACTIONS ADD COLUMN $COL_TRANS_REMOTE_ID TEXT;")
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_transactions_remote_id ON $TABLE_TRANSACTIONS ($COL_TRANS_REMOTE_ID);")
            } catch (e: Exception) {
                // Column may already exist
            }
        }
    }
}
