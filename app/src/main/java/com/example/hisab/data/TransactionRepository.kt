package com.example.hisab.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import com.example.hisab.api.RemoteTransactionDto
import com.example.hisab.model.AccountPeriod
import com.example.hisab.model.PeriodHistoryItem
import com.example.hisab.model.Settlement
import com.example.hisab.model.Transaction

/**
 * Repository handling transaction entries, accounting periods, and the settlement process.
 *
 * Implements strict transaction safety using SQLite transactions (`beginTransaction()`).
 */
class TransactionRepository(context: Context) {

    private val dbHelper = DatabaseHelper.getInstance(context)

    /**
     * Gets the currently active open period for a person.
     * If for any reason no open period exists, one is automatically created.
     */
    fun getOrCreateOpenPeriod(personId: Long): AccountPeriod {
        val db = dbHelper.writableDatabase
        val cursor = db.query(
            DatabaseHelper.TABLE_ACCOUNT_PERIODS,
            null,
            "${DatabaseHelper.COL_PERIODS_PERSON_ID} = ? AND ${DatabaseHelper.COL_PERIODS_STATUS} = ?",
            arrayOf(personId.toString(), AccountPeriod.STATUS_OPEN),
            null,
            null,
            "${DatabaseHelper.COL_PERIODS_ID} DESC",
            "1"
        )

        cursor.use {
            if (it.moveToFirst()) {
                return cursorToAccountPeriod(it)
            }
        }

        // Create new open period
        val now = System.currentTimeMillis()
        val values = ContentValues().apply {
            put(DatabaseHelper.COL_PERIODS_PERSON_ID, personId)
            put(DatabaseHelper.COL_PERIODS_STARTED_AT, now)
            put(DatabaseHelper.COL_PERIODS_OPENING_BALANCE_PAISE, 0L)
            put(DatabaseHelper.COL_PERIODS_STATUS, AccountPeriod.STATUS_OPEN)
        }
        val newId = db.insertOrThrow(DatabaseHelper.TABLE_ACCOUNT_PERIODS, null, values)
        return AccountPeriod(
            id = newId,
            personId = personId,
            startedAt = now,
            openingBalancePaise = 0L,
            status = AccountPeriod.STATUS_OPEN
        )
    }

    /**
     * Records a new transaction against the person's current open accounting period.
     *
     * @param personId ID of the person.
     * @param amountPaise Signed amount in paise (+ if they owe me, - if I owe them).
     * @param reason Short description.
     * @param transactionDate When transaction took place.
     * @param notes Optional notes.
     * @return Pair of (transactionId, errorMessage).
     */
    fun addTransaction(
        personId: Long,
        amountPaise: Long,
        reason: String,
        transactionDate: Long = System.currentTimeMillis(),
        notes: String? = null,
        remoteId: String? = null
    ): Pair<Long, String?> {
        val trimmedReason = reason.trim()
        if (trimmedReason.isEmpty()) {
            return Pair(-1L, "Reason cannot be empty")
        }
        if (amountPaise == 0L) {
            return Pair(-1L, "Amount cannot be zero")
        }

        val db = dbHelper.writableDatabase
        db.beginTransaction()
        return try {
            val openPeriod = getOrCreateOpenPeriod(personId)
            val now = System.currentTimeMillis()

            val values = ContentValues().apply {
                put(DatabaseHelper.COL_TRANS_PERSON_ID, personId)
                put(DatabaseHelper.COL_TRANS_PERIOD_ID, openPeriod.id)
                put(DatabaseHelper.COL_TRANS_AMOUNT_PAISE, amountPaise)
                put(DatabaseHelper.COL_TRANS_REASON, trimmedReason)
                put(DatabaseHelper.COL_TRANS_TRANSACTION_DATE, transactionDate)
                put(DatabaseHelper.COL_TRANS_CREATED_AT, now)
                put(DatabaseHelper.COL_TRANS_UPDATED_AT, now)
                put(DatabaseHelper.COL_TRANS_NOTES, notes?.trim()?.ifEmpty { null })
                if (remoteId != null) {
                    put(DatabaseHelper.COL_TRANS_REMOTE_ID, remoteId)
                }
            }

            val transId = db.insertOrThrow(DatabaseHelper.TABLE_TRANSACTIONS, null, values)

            // Auto-settle: If adding this transaction brings the open period balance to ₹0.00,
            // automatically close this period into History so the active ledger displays 0 transactions.
            val updatedBalance = calculatePeriodBalance(openPeriod)
            if (updatedBalance == 0L) {
                // 1. Close current period
                val closeValues = ContentValues().apply {
                    put(DatabaseHelper.COL_PERIODS_CLOSED_AT, transactionDate)
                    put(DatabaseHelper.COL_PERIODS_CLOSING_BALANCE_PAISE, 0L)
                    put(DatabaseHelper.COL_PERIODS_STATUS, AccountPeriod.STATUS_CLOSED)
                }
                db.update(
                    DatabaseHelper.TABLE_ACCOUNT_PERIODS,
                    closeValues,
                    "${DatabaseHelper.COL_PERIODS_ID} = ?",
                    arrayOf(openPeriod.id.toString())
                )

                // 2. Insert settlement record
                val settleValues = ContentValues().apply {
                    put(DatabaseHelper.COL_SETTLE_PERSON_ID, personId)
                    put(DatabaseHelper.COL_SETTLE_PERIOD_ID, openPeriod.id)
                    put(DatabaseHelper.COL_SETTLE_FINAL_BALANCE_PAISE, 0L)
                    put(DatabaseHelper.COL_SETTLE_SETTLED_AT, transactionDate)
                    put(DatabaseHelper.COL_SETTLE_NOTE, "Settled (Balance cleared to ₹0.00)")
                }
                db.insertOrThrow(DatabaseHelper.TABLE_SETTLEMENTS, null, settleValues)

                // 3. Open brand new period starting at 0
                val newPeriodValues = ContentValues().apply {
                    put(DatabaseHelper.COL_PERIODS_PERSON_ID, personId)
                    put(DatabaseHelper.COL_PERIODS_STARTED_AT, transactionDate)
                    put(DatabaseHelper.COL_PERIODS_OPENING_BALANCE_PAISE, 0L)
                    put(DatabaseHelper.COL_PERIODS_STATUS, AccountPeriod.STATUS_OPEN)
                }
                db.insertOrThrow(DatabaseHelper.TABLE_ACCOUNT_PERIODS, null, newPeriodValues)
            }

            db.setTransactionSuccessful()
            Pair(transId, null)
        } catch (e: Exception) {
            Pair(-1L, "Failed to save transaction: ${e.localizedMessage}")
        } finally {
            db.endTransaction()
        }
    }

    /**
     * Retrieves all transactions belonging to the person's current open period,
     * sorted newest first.
     */
    fun getOpenPeriodTransactions(personId: Long): List<Transaction> {
        val openPeriod = getOrCreateOpenPeriod(personId)
        return getTransactionsForPeriod(openPeriod.id)
    }

    /**
     * Retrieves transactions for a specific period ID, sorted newest first.
     */
    fun getTransactionsForPeriod(periodId: Long): List<Transaction> {
        val db = dbHelper.readableDatabase
        val list = mutableListOf<Transaction>()

        val cursor = db.query(
            DatabaseHelper.TABLE_TRANSACTIONS,
            null,
            "${DatabaseHelper.COL_TRANS_PERIOD_ID} = ?",
            arrayOf(periodId.toString()),
            null,
            null,
            "${DatabaseHelper.COL_TRANS_TRANSACTION_DATE} DESC, ${DatabaseHelper.COL_TRANS_ID} DESC"
        )

        cursor.use {
            while (it.moveToNext()) {
                list.add(cursorToTransaction(it))
            }
        }
        return list
    }

    /**
     * Deletes a transaction by ID.
     */
    fun deleteTransaction(transactionId: Long): Boolean {
        val db = dbHelper.writableDatabase
        val rows = db.delete(
            DatabaseHelper.TABLE_TRANSACTIONS,
            "${DatabaseHelper.COL_TRANS_ID} = ?",
            arrayOf(transactionId.toString())
        )
        return rows > 0
    }

    /**
     * Calculates the balance for a specific period.
     * Balance = opening_balance_paise + sum(amount_paise)
     */
    fun calculatePeriodBalance(period: AccountPeriod): Long {
        val db = dbHelper.readableDatabase
        val cursor = db.rawQuery(
            "SELECT COALESCE(SUM(${DatabaseHelper.COL_TRANS_AMOUNT_PAISE}), 0) " +
                    "FROM ${DatabaseHelper.TABLE_TRANSACTIONS} WHERE ${DatabaseHelper.COL_TRANS_PERIOD_ID} = ?",
            arrayOf(period.id.toString())
        )
        val sum = cursor.use {
            if (it.moveToFirst()) it.getLong(0) else 0L
        }
        return period.openingBalancePaise + sum
    }

    /**
     * Settles the account for a person:
     * 1. Calculates the final balance of the open period.
     * 2. Closes the period and stamps closed_at and closing_balance_paise with the given settlement date.
     * 3. Creates a Settlement record with final balance, timestamp, and optional note.
     * 4. Opens a fresh new period starting at 0.
     *
     * Executed inside an atomic transaction.
     */
    fun settleHisab(
        personId: Long,
        note: String? = null,
        settledAt: Long = System.currentTimeMillis()
    ): Pair<Boolean, String?> {
        val db = dbHelper.writableDatabase
        db.beginTransaction()
        return try {
            val openPeriod = getOrCreateOpenPeriod(personId)
            val finalBalance = calculatePeriodBalance(openPeriod)

            // 1. Close current period
            val closeValues = ContentValues().apply {
                put(DatabaseHelper.COL_PERIODS_CLOSED_AT, settledAt)
                put(DatabaseHelper.COL_PERIODS_CLOSING_BALANCE_PAISE, finalBalance)
                put(DatabaseHelper.COL_PERIODS_STATUS, AccountPeriod.STATUS_CLOSED)
            }
            db.update(
                DatabaseHelper.TABLE_ACCOUNT_PERIODS,
                closeValues,
                "${DatabaseHelper.COL_PERIODS_ID} = ?",
                arrayOf(openPeriod.id.toString())
            )

            // 2. Insert settlement record
            val settleValues = ContentValues().apply {
                put(DatabaseHelper.COL_SETTLE_PERSON_ID, personId)
                put(DatabaseHelper.COL_SETTLE_PERIOD_ID, openPeriod.id)
                put(DatabaseHelper.COL_SETTLE_FINAL_BALANCE_PAISE, finalBalance)
                put(DatabaseHelper.COL_SETTLE_SETTLED_AT, settledAt)
                put(DatabaseHelper.COL_SETTLE_NOTE, note?.trim()?.ifEmpty { null })
            }
            db.insertOrThrow(DatabaseHelper.TABLE_SETTLEMENTS, null, settleValues)

            // 3. Start a brand new open period
            val newPeriodValues = ContentValues().apply {
                put(DatabaseHelper.COL_PERIODS_PERSON_ID, personId)
                put(DatabaseHelper.COL_PERIODS_STARTED_AT, settledAt)
                put(DatabaseHelper.COL_PERIODS_OPENING_BALANCE_PAISE, 0L)
                put(DatabaseHelper.COL_PERIODS_STATUS, AccountPeriod.STATUS_OPEN)
            }
            db.insertOrThrow(DatabaseHelper.TABLE_ACCOUNT_PERIODS, null, newPeriodValues)

            db.setTransactionSuccessful()
            Pair(true, null)
        } catch (e: Exception) {
            Pair(false, "Settlement failed: ${e.localizedMessage}")
        } finally {
            db.endTransaction()
        }
    }

    /**
     * Checks if the active open period has transactions that balance out to ₹0.00.
     * If so, automatically closes the period and moves the transactions to History.
     *
     * @return true if an auto-settlement occurred.
     */
    fun checkAndAutoSettleZeroBalance(
        personId: Long,
        timestamp: Long = System.currentTimeMillis()
    ): Boolean {
        val db = dbHelper.writableDatabase
        db.beginTransaction()
        return try {
            val openPeriod = getOrCreateOpenPeriod(personId)
            val transactions = getTransactionsForPeriod(openPeriod.id)
            if (transactions.isNotEmpty()) {
                val balance = calculatePeriodBalance(openPeriod)
                if (balance == 0L) {
                    val settleTimestamp = transactions.firstOrNull()?.transactionDate ?: timestamp

                    // 1. Close current period
                    val closeValues = ContentValues().apply {
                        put(DatabaseHelper.COL_PERIODS_CLOSED_AT, settleTimestamp)
                        put(DatabaseHelper.COL_PERIODS_CLOSING_BALANCE_PAISE, 0L)
                        put(DatabaseHelper.COL_PERIODS_STATUS, AccountPeriod.STATUS_CLOSED)
                    }
                    db.update(
                        DatabaseHelper.TABLE_ACCOUNT_PERIODS,
                        closeValues,
                        "${DatabaseHelper.COL_PERIODS_ID} = ?",
                        arrayOf(openPeriod.id.toString())
                    )

                    // 2. Insert settlement record
                    val settleValues = ContentValues().apply {
                        put(DatabaseHelper.COL_SETTLE_PERSON_ID, personId)
                        put(DatabaseHelper.COL_SETTLE_PERIOD_ID, openPeriod.id)
                        put(DatabaseHelper.COL_SETTLE_FINAL_BALANCE_PAISE, 0L)
                        put(DatabaseHelper.COL_SETTLE_SETTLED_AT, settleTimestamp)
                        put(DatabaseHelper.COL_SETTLE_NOTE, "Settled (Balance cleared to ₹0.00)")
                    }
                    db.insertOrThrow(DatabaseHelper.TABLE_SETTLEMENTS, null, settleValues)

                    // 3. Start a brand new open period
                    val newPeriodValues = ContentValues().apply {
                        put(DatabaseHelper.COL_PERIODS_PERSON_ID, personId)
                        put(DatabaseHelper.COL_PERIODS_STARTED_AT, settleTimestamp)
                        put(DatabaseHelper.COL_PERIODS_OPENING_BALANCE_PAISE, 0L)
                        put(DatabaseHelper.COL_PERIODS_STATUS, AccountPeriod.STATUS_OPEN)
                    }
                    db.insertOrThrow(DatabaseHelper.TABLE_ACCOUNT_PERIODS, null, newPeriodValues)

                    db.setTransactionSuccessful()
                    true
                } else {
                    false
                }
            } else {
                false
            }
        } catch (e: Exception) {
            false
        } finally {
            db.endTransaction()
        }
    }

    /**
     * Loads the complete history for a person, including all past periods,
     * settlements, and their transactions.
     */
    fun getPersonHistory(personId: Long): List<PeriodHistoryItem> {
        val db = dbHelper.readableDatabase
        val history = mutableListOf<PeriodHistoryItem>()

        // 1. Load all periods for this person, newest first
        val periodCursor = db.query(
            DatabaseHelper.TABLE_ACCOUNT_PERIODS,
            null,
            "${DatabaseHelper.COL_PERIODS_PERSON_ID} = ?",
            arrayOf(personId.toString()),
            null,
            null,
            "${DatabaseHelper.COL_PERIODS_STARTED_AT} DESC, ${DatabaseHelper.COL_PERIODS_ID} DESC"
        )

        val periods = mutableListOf<AccountPeriod>()
        periodCursor.use {
            while (it.moveToNext()) {
                periods.add(cursorToAccountPeriod(it))
            }
        }

        // 2. For each period, load its settlement (if closed) and transactions
        for (period in periods) {
            var settlement: Settlement? = null
            if (period.status == AccountPeriod.STATUS_CLOSED) {
                val settleCursor = db.query(
                    DatabaseHelper.TABLE_SETTLEMENTS,
                    null,
                    "${DatabaseHelper.COL_SETTLE_PERIOD_ID} = ?",
                    arrayOf(period.id.toString()),
                    null,
                    null,
                    null,
                    "1"
                )
                settleCursor.use {
                    if (it.moveToFirst()) {
                        settlement = cursorToSettlement(it)
                    }
                }
            }

            val transactions = getTransactionsForPeriod(period.id)
            val balance = period.closingBalancePaise ?: calculatePeriodBalance(period)

            history.add(
                PeriodHistoryItem(
                    period = period,
                    settlement = settlement,
                    transactions = transactions,
                    calculatedBalancePaise = balance
                )
            )
        }

        return history
    }

    private fun cursorToAccountPeriod(c: Cursor): AccountPeriod {
        val closedAtIdx = c.getColumnIndexOrThrow(DatabaseHelper.COL_PERIODS_CLOSED_AT)
        val closingBalIdx = c.getColumnIndexOrThrow(DatabaseHelper.COL_PERIODS_CLOSING_BALANCE_PAISE)

        return AccountPeriod(
            id = c.getLong(c.getColumnIndexOrThrow(DatabaseHelper.COL_PERIODS_ID)),
            personId = c.getLong(c.getColumnIndexOrThrow(DatabaseHelper.COL_PERIODS_PERSON_ID)),
            startedAt = c.getLong(c.getColumnIndexOrThrow(DatabaseHelper.COL_PERIODS_STARTED_AT)),
            closedAt = if (c.isNull(closedAtIdx)) null else c.getLong(closedAtIdx),
            openingBalancePaise = c.getLong(c.getColumnIndexOrThrow(DatabaseHelper.COL_PERIODS_OPENING_BALANCE_PAISE)),
            closingBalancePaise = if (c.isNull(closingBalIdx)) null else c.getLong(closingBalIdx),
            status = c.getString(c.getColumnIndexOrThrow(DatabaseHelper.COL_PERIODS_STATUS))
        )
    }

    /**
     * Looks up a local Transaction by its primary key ID.
     */
    fun getTransactionById(transactionId: Long): Transaction? {
        val db = dbHelper.readableDatabase
        val cursor = db.query(
            DatabaseHelper.TABLE_TRANSACTIONS,
            null,
            "${DatabaseHelper.COL_TRANS_ID} = ?",
            arrayOf(transactionId.toString()),
            null, null, null, "1"
        )
        return cursor.use {
            if (it.moveToFirst()) cursorToTransaction(it) else null
        }
    }

    /**
     * Looks up a local Transaction by its remote cloud ID (MongoDB _id).
     */
    fun getTransactionByRemoteId(remoteId: String): Transaction? {
        val db = dbHelper.readableDatabase
        val cursor = db.query(
            DatabaseHelper.TABLE_TRANSACTIONS,
            null,
            "${DatabaseHelper.COL_TRANS_REMOTE_ID} = ?",
            arrayOf(remoteId),
            null, null, null, "1"
        )
        return cursor.use {
            if (it.moveToFirst()) cursorToTransaction(it) else null
        }
    }

    /**
     * Updates a local Transaction's remote cloud ID.
     */
    fun updateTransactionRemoteId(transactionId: Long, remoteId: String) {
        val db = dbHelper.writableDatabase
        val values = ContentValues().apply {
            put(DatabaseHelper.COL_TRANS_REMOTE_ID, remoteId)
            put(DatabaseHelper.COL_TRANS_UPDATED_AT, System.currentTimeMillis())
        }
        db.update(
            DatabaseHelper.TABLE_TRANSACTIONS,
            values,
            "${DatabaseHelper.COL_TRANS_ID} = ?",
            arrayOf(transactionId.toString())
        )
    }

    /**
     * Returns local transactions that have not yet been synced to MongoDB Atlas.
     */
    fun getUnsyncedTransactions(): List<Transaction> {
        val db = dbHelper.readableDatabase
        val list = mutableListOf<Transaction>()
        val cursor = db.query(
            DatabaseHelper.TABLE_TRANSACTIONS,
            null,
            "${DatabaseHelper.COL_TRANS_REMOTE_ID} IS NULL OR ${DatabaseHelper.COL_TRANS_REMOTE_ID} = ''",
            null, null, null, null
        )
        cursor.use {
            while (it.moveToNext()) {
                list.add(cursorToTransaction(it))
            }
        }
        return list
    }

    /**
     * Synchronizes a historical closed accounting period and its settlement from MongoDB Atlas into SQLite.
     * Ensures that transactions in this historical period belong STRICTLY to this closed period
     * and NEVER pollute the active open period.
     *
     * Healing feature: If any of these transactions were previously mistakenly inserted into the
     * active open period (e.g. from an earlier sync bug), they are automatically moved to this closed period!
     */
    fun syncRemoteClosedPeriod(
        personId: Long,
        startedAt: Long,
        closedAt: Long,
        openingBalancePaise: Long,
        closingBalancePaise: Long,
        settledAt: Long,
        note: String?,
        transactions: List<RemoteTransactionDto>
    ): Long {
        val db = dbHelper.writableDatabase
        db.beginTransaction()
        try {
            var closedPeriodId: Long = -1L

            // 1A. Check if any transaction in this closed period is already associated with an existing closed period in SQLite
            for (remoteTx in transactions) {
                val existing = getTransactionByRemoteId(remoteTx.id)
                if (existing != null) {
                    val pCursor = db.query(
                        DatabaseHelper.TABLE_ACCOUNT_PERIODS,
                        null,
                        "${DatabaseHelper.COL_PERIODS_ID} = ? AND ${DatabaseHelper.COL_PERIODS_STATUS} = ?",
                        arrayOf(existing.periodId.toString(), AccountPeriod.STATUS_CLOSED),
                        null, null, null, "1"
                    )
                    pCursor.use {
                        if (it.moveToFirst()) {
                            closedPeriodId = existing.periodId
                        }
                    }
                    if (closedPeriodId != -1L) break
                }
            }

            // 1B. Check if a settlement exists matching settledAt (within 60s)
            if (closedPeriodId == -1L) {
                val settleCursor = db.query(
                    DatabaseHelper.TABLE_SETTLEMENTS,
                    null,
                    "${DatabaseHelper.COL_SETTLE_PERSON_ID} = ? AND ABS(${DatabaseHelper.COL_SETTLE_SETTLED_AT} - ?) < 60000",
                    arrayOf(personId.toString(), settledAt.toString()),
                    null, null, null, "1"
                )
                settleCursor.use {
                    if (it.moveToFirst()) {
                        closedPeriodId = it.getLong(it.getColumnIndexOrThrow(DatabaseHelper.COL_SETTLE_PERIOD_ID))
                    }
                }
            }

            // 1C. Check if an account period exists matching closedAt (within 60s)
            if (closedPeriodId == -1L) {
                val periodCursor = db.query(
                    DatabaseHelper.TABLE_ACCOUNT_PERIODS,
                    null,
                    "${DatabaseHelper.COL_PERIODS_PERSON_ID} = ? AND ${DatabaseHelper.COL_PERIODS_STATUS} = ? AND ABS(${DatabaseHelper.COL_PERIODS_CLOSED_AT} - ?) < 60000",
                    arrayOf(personId.toString(), AccountPeriod.STATUS_CLOSED, closedAt.toString()),
                    null, null, null, "1"
                )
                periodCursor.use {
                    if (it.moveToFirst()) {
                        closedPeriodId = it.getLong(it.getColumnIndexOrThrow(DatabaseHelper.COL_PERIODS_ID))
                    }
                }
            }

            // 1D. If not found, create new closed period & settlement
            if (closedPeriodId == -1L) {
                val periodValues = ContentValues().apply {
                    put(DatabaseHelper.COL_PERIODS_PERSON_ID, personId)
                    put(DatabaseHelper.COL_PERIODS_STARTED_AT, startedAt)
                    put(DatabaseHelper.COL_PERIODS_CLOSED_AT, closedAt)
                    put(DatabaseHelper.COL_PERIODS_OPENING_BALANCE_PAISE, openingBalancePaise)
                    put(DatabaseHelper.COL_PERIODS_CLOSING_BALANCE_PAISE, closingBalancePaise)
                    put(DatabaseHelper.COL_PERIODS_STATUS, AccountPeriod.STATUS_CLOSED)
                }
                closedPeriodId = db.insertOrThrow(DatabaseHelper.TABLE_ACCOUNT_PERIODS, null, periodValues)

                val settleValues = ContentValues().apply {
                    put(DatabaseHelper.COL_SETTLE_PERSON_ID, personId)
                    put(DatabaseHelper.COL_SETTLE_PERIOD_ID, closedPeriodId)
                    put(DatabaseHelper.COL_SETTLE_FINAL_BALANCE_PAISE, closingBalancePaise)
                    put(DatabaseHelper.COL_SETTLE_SETTLED_AT, settledAt)
                    put(DatabaseHelper.COL_SETTLE_NOTE, note)
                }
                db.insertOrThrow(DatabaseHelper.TABLE_SETTLEMENTS, null, settleValues)
            }

            // 2. Reconcile transactions: ensure each transaction belongs to closedPeriodId!
            for (remoteTx in transactions) {
                val existingTx = getTransactionByRemoteId(remoteTx.id)
                if (existingTx != null) {
                    // HEALING STEP: Move this transaction to its proper closed period!
                    if (existingTx.periodId != closedPeriodId) {
                        val updateValues = ContentValues().apply {
                            put(DatabaseHelper.COL_TRANS_PERIOD_ID, closedPeriodId)
                            put(DatabaseHelper.COL_TRANS_UPDATED_AT, System.currentTimeMillis())
                        }
                        db.update(
                            DatabaseHelper.TABLE_TRANSACTIONS,
                            updateValues,
                            "${DatabaseHelper.COL_TRANS_ID} = ?",
                            arrayOf(existingTx.id.toString())
                        )
                    }
                } else {
                    val txDate = parseIsoDate(remoteTx.transactionDate)
                    val txValues = ContentValues().apply {
                        put(DatabaseHelper.COL_TRANS_PERSON_ID, personId)
                        put(DatabaseHelper.COL_TRANS_PERIOD_ID, closedPeriodId)
                        put(DatabaseHelper.COL_TRANS_AMOUNT_PAISE, remoteTx.amountPaise)
                        put(DatabaseHelper.COL_TRANS_REASON, remoteTx.reason.trim())
                        put(DatabaseHelper.COL_TRANS_TRANSACTION_DATE, txDate)
                        put(DatabaseHelper.COL_TRANS_CREATED_AT, txDate)
                        put(DatabaseHelper.COL_TRANS_UPDATED_AT, txDate)
                        put(DatabaseHelper.COL_TRANS_NOTES, remoteTx.notes?.trim()?.ifEmpty { null })
                        put(DatabaseHelper.COL_TRANS_REMOTE_ID, remoteTx.id)
                    }
                    db.insertOrThrow(DatabaseHelper.TABLE_TRANSACTIONS, null, txValues)
                }
            }

            db.setTransactionSuccessful()
            return closedPeriodId
        } catch (e: Exception) {
            return -1L
        } finally {
            db.endTransaction()
        }
    }

    /**
     * Synchronizes an active transaction that belongs strictly to the open period.
     */
    fun syncRemoteOpenTransaction(
        personId: Long,
        amountPaise: Long,
        reason: String,
        transactionDate: Long,
        notes: String?,
        remoteId: String
    ): Long {
        val existingTx = getTransactionByRemoteId(remoteId)
        val openPeriod = getOrCreateOpenPeriod(personId)

        if (existingTx != null) {
            // Transaction already exists locally; NEVER pull an existing transaction into the open period!
            return existingTx.id
        }

        // Insert new into open period
        val db = dbHelper.writableDatabase
        val now = System.currentTimeMillis()
        val values = ContentValues().apply {
            put(DatabaseHelper.COL_TRANS_PERSON_ID, personId)
            put(DatabaseHelper.COL_TRANS_PERIOD_ID, openPeriod.id)
            put(DatabaseHelper.COL_TRANS_AMOUNT_PAISE, amountPaise)
            put(DatabaseHelper.COL_TRANS_REASON, reason.trim())
            put(DatabaseHelper.COL_TRANS_TRANSACTION_DATE, transactionDate)
            put(DatabaseHelper.COL_TRANS_CREATED_AT, now)
            put(DatabaseHelper.COL_TRANS_UPDATED_AT, now)
            put(DatabaseHelper.COL_TRANS_NOTES, notes?.trim()?.ifEmpty { null })
            put(DatabaseHelper.COL_TRANS_REMOTE_ID, remoteId)
        }
        return db.insertOrThrow(DatabaseHelper.TABLE_TRANSACTIONS, null, values)
    }

    /**
     * Moves a transaction to a specific period ID.
     */
    fun moveTransactionToPeriod(transactionId: Long, targetPeriodId: Long) {
        val db = dbHelper.writableDatabase
        val values = ContentValues().apply {
            put(DatabaseHelper.COL_TRANS_PERIOD_ID, targetPeriodId)
            put(DatabaseHelper.COL_TRANS_UPDATED_AT, System.currentTimeMillis())
        }
        db.update(
            DatabaseHelper.TABLE_TRANSACTIONS,
            values,
            "${DatabaseHelper.COL_TRANS_ID} = ?",
            arrayOf(transactionId.toString())
        )
    }

    private fun parseIsoDate(isoString: String?): Long {
        if (isoString.isNullOrBlank()) return System.currentTimeMillis()
        return try {
            val format = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.US).apply {
                timeZone = java.util.TimeZone.getTimeZone("UTC")
            }
            format.parse(isoString)?.time ?: System.currentTimeMillis()
        } catch (e: Exception) {
            try {
                val formatSec = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US).apply {
                    timeZone = java.util.TimeZone.getTimeZone("UTC")
                }
                formatSec.parse(isoString)?.time ?: System.currentTimeMillis()
            } catch (e2: Exception) {
                System.currentTimeMillis()
            }
        }
    }

    private fun cursorToTransaction(c: Cursor): Transaction {
        val notesIdx = c.getColumnIndexOrThrow(DatabaseHelper.COL_TRANS_NOTES)
        val remoteIdIdx = c.getColumnIndex(DatabaseHelper.COL_TRANS_REMOTE_ID)
        val remoteId = if (remoteIdIdx >= 0 && !c.isNull(remoteIdIdx)) c.getString(remoteIdIdx) else null

        return Transaction(
            id = c.getLong(c.getColumnIndexOrThrow(DatabaseHelper.COL_TRANS_ID)),
            personId = c.getLong(c.getColumnIndexOrThrow(DatabaseHelper.COL_TRANS_PERSON_ID)),
            periodId = c.getLong(c.getColumnIndexOrThrow(DatabaseHelper.COL_TRANS_PERIOD_ID)),
            amountPaise = c.getLong(c.getColumnIndexOrThrow(DatabaseHelper.COL_TRANS_AMOUNT_PAISE)),
            reason = c.getString(c.getColumnIndexOrThrow(DatabaseHelper.COL_TRANS_REASON)),
            transactionDate = c.getLong(c.getColumnIndexOrThrow(DatabaseHelper.COL_TRANS_TRANSACTION_DATE)),
            createdAt = c.getLong(c.getColumnIndexOrThrow(DatabaseHelper.COL_TRANS_CREATED_AT)),
            updatedAt = c.getLong(c.getColumnIndexOrThrow(DatabaseHelper.COL_TRANS_UPDATED_AT)),
            notes = if (c.isNull(notesIdx)) null else c.getString(notesIdx),
            remoteId = remoteId
        )
    }

    private fun cursorToSettlement(c: Cursor): Settlement {
        val noteIdx = c.getColumnIndexOrThrow(DatabaseHelper.COL_SETTLE_NOTE)
        return Settlement(
            id = c.getLong(c.getColumnIndexOrThrow(DatabaseHelper.COL_SETTLE_ID)),
            personId = c.getLong(c.getColumnIndexOrThrow(DatabaseHelper.COL_SETTLE_PERSON_ID)),
            periodId = c.getLong(c.getColumnIndexOrThrow(DatabaseHelper.COL_SETTLE_PERIOD_ID)),
            finalBalancePaise = c.getLong(c.getColumnIndexOrThrow(DatabaseHelper.COL_SETTLE_FINAL_BALANCE_PAISE)),
            settledAt = c.getLong(c.getColumnIndexOrThrow(DatabaseHelper.COL_SETTLE_SETTLED_AT)),
            note = if (c.isNull(noteIdx)) null else c.getString(noteIdx)
        )
    }
}
