package com.example.hisab.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import com.example.hisab.model.AccountPeriod
import com.example.hisab.model.DashboardSummary
import com.example.hisab.model.Person

/**
 * Repository responsible for managing Person records and dashboard aggregations.
 *
 * Uses parameterized queries and ContentValues to guard against SQL injection.
 * Encapsulates all SQLite interactions away from the UI Activities.
 */
class PersonRepository(context: Context) {

    private val dbHelper = DatabaseHelper.getInstance(context)

    /**
     * Adds a new person to the local database.
     * Automatically initializes their first active [AccountPeriod] with an opening balance of 0.
     *
     * @param name Person's name (must be non-empty).
     * @return Result pair of (newPersonId, errorMessage). If successful, errorMessage is null.
     */
    fun addPerson(name: String, remoteId: String? = null): Pair<Long, String?> {
        val trimmedName = name.trim()
        if (trimmedName.isEmpty()) {
            return Pair(-1L, "Name cannot be empty")
        }

        val db = dbHelper.writableDatabase

        // Prevent accidental duplicate names (case-insensitive)
        val cursor = db.rawQuery(
            "SELECT ${DatabaseHelper.COL_PEOPLE_ID} FROM ${DatabaseHelper.TABLE_PEOPLE} " +
                    "WHERE LOWER(${DatabaseHelper.COL_PEOPLE_NAME}) = LOWER(?) AND ${DatabaseHelper.COL_PEOPLE_ARCHIVED} = 0",
            arrayOf(trimmedName)
        )
        val exists = cursor.moveToFirst()
        cursor.close()

        if (exists) {
            return Pair(-1L, "A person with the name \"$trimmedName\" already exists")
        }

        db.beginTransaction()
        return try {
            val now = System.currentTimeMillis()
            val personValues = ContentValues().apply {
                put(DatabaseHelper.COL_PEOPLE_NAME, trimmedName)
                put(DatabaseHelper.COL_PEOPLE_CREATED_AT, now)
                put(DatabaseHelper.COL_PEOPLE_UPDATED_AT, now)
                put(DatabaseHelper.COL_PEOPLE_ARCHIVED, 0)
                if (remoteId != null) {
                    put(DatabaseHelper.COL_PEOPLE_REMOTE_ID, remoteId)
                }
            }
            val personId = db.insertOrThrow(DatabaseHelper.TABLE_PEOPLE, null, personValues)

            // Create their initial open accounting period
            val periodValues = ContentValues().apply {
                put(DatabaseHelper.COL_PERIODS_PERSON_ID, personId)
                put(DatabaseHelper.COL_PERIODS_STARTED_AT, now)
                put(DatabaseHelper.COL_PERIODS_OPENING_BALANCE_PAISE, 0L)
                put(DatabaseHelper.COL_PERIODS_STATUS, AccountPeriod.STATUS_OPEN)
            }
            db.insertOrThrow(DatabaseHelper.TABLE_ACCOUNT_PERIODS, null, periodValues)

            db.setTransactionSuccessful()
            Pair(personId, null)
        } catch (e: Exception) {
            Pair(-1L, "Failed to save person: ${e.localizedMessage}")
        } finally {
            db.endTransaction()
        }
    }

    /**
     * Retrieves all non-archived people, calculating each person's current balance
     * based on transactions in their active open accounting period.
     */
    fun getAllPeople(): List<Person> {
        val db = dbHelper.readableDatabase
        val people = mutableListOf<Person>()

        // Query people joined with their current open period's transaction sum
        val query = """
            SELECT 
                p.${DatabaseHelper.COL_PEOPLE_ID} AS person_id,
                p.${DatabaseHelper.COL_PEOPLE_REMOTE_ID} AS person_remote_id,
                p.${DatabaseHelper.COL_PEOPLE_NAME} AS person_name,
                p.${DatabaseHelper.COL_PEOPLE_CREATED_AT} AS person_created,
                p.${DatabaseHelper.COL_PEOPLE_UPDATED_AT} AS person_updated,
                p.${DatabaseHelper.COL_PEOPLE_ARCHIVED} AS person_archived,
                ap.${DatabaseHelper.COL_PERIODS_OPENING_BALANCE_PAISE} AS opening_balance,
                COALESCE(SUM(t.${DatabaseHelper.COL_TRANS_AMOUNT_PAISE}), 0) AS transaction_sum
            FROM ${DatabaseHelper.TABLE_PEOPLE} p
            LEFT JOIN ${DatabaseHelper.TABLE_ACCOUNT_PERIODS} ap 
                ON p.${DatabaseHelper.COL_PEOPLE_ID} = ap.${DatabaseHelper.COL_PERIODS_PERSON_ID} 
                AND ap.${DatabaseHelper.COL_PERIODS_STATUS} = '${AccountPeriod.STATUS_OPEN}'
            LEFT JOIN ${DatabaseHelper.TABLE_TRANSACTIONS} t 
                ON ap.${DatabaseHelper.COL_PERIODS_ID} = t.${DatabaseHelper.COL_TRANS_PERIOD_ID}
            WHERE p.${DatabaseHelper.COL_PEOPLE_ARCHIVED} = 0
            GROUP BY p.${DatabaseHelper.COL_PEOPLE_ID}
            ORDER BY p.${DatabaseHelper.COL_PEOPLE_NAME} COLLATE NOCASE ASC
        """.trimIndent()

        val cursor: Cursor = db.rawQuery(query, null)
        cursor.use {
            val idIdx = it.getColumnIndexOrThrow("person_id")
            val remoteIdIdx = it.getColumnIndex("person_remote_id")
            val nameIdx = it.getColumnIndexOrThrow("person_name")
            val createdIdx = it.getColumnIndexOrThrow("person_created")
            val updatedIdx = it.getColumnIndexOrThrow("person_updated")
            val archivedIdx = it.getColumnIndexOrThrow("person_archived")
            val openingIdx = it.getColumnIndexOrThrow("opening_balance")
            val sumIdx = it.getColumnIndexOrThrow("transaction_sum")

            while (it.moveToNext()) {
                val opening = if (it.isNull(openingIdx)) 0L else it.getLong(openingIdx)
                val sum = it.getLong(sumIdx)
                val currentBalance = opening + sum
                val remoteId = if (remoteIdIdx >= 0 && !it.isNull(remoteIdIdx)) it.getString(remoteIdIdx) else null

                people.add(
                    Person(
                        id = it.getLong(idIdx),
                        name = it.getString(nameIdx),
                        createdAt = it.getLong(createdIdx),
                        updatedAt = it.getLong(updatedIdx),
                        archived = it.getInt(archivedIdx) == 1,
                        currentBalancePaise = currentBalance,
                        remoteId = remoteId
                    )
                )
            }
        }
        return people
    }

    /**
     * Retrieves a single person by their ID, including their current open-period balance.
     */
    fun getPersonById(personId: Long): Person? {
        val db = dbHelper.readableDatabase
        val query = """
            SELECT 
                p.${DatabaseHelper.COL_PEOPLE_ID} AS person_id,
                p.${DatabaseHelper.COL_PEOPLE_REMOTE_ID} AS person_remote_id,
                p.${DatabaseHelper.COL_PEOPLE_NAME} AS person_name,
                p.${DatabaseHelper.COL_PEOPLE_CREATED_AT} AS person_created,
                p.${DatabaseHelper.COL_PEOPLE_UPDATED_AT} AS person_updated,
                p.${DatabaseHelper.COL_PEOPLE_ARCHIVED} AS person_archived,
                ap.${DatabaseHelper.COL_PERIODS_OPENING_BALANCE_PAISE} AS opening_balance,
                COALESCE(SUM(t.${DatabaseHelper.COL_TRANS_AMOUNT_PAISE}), 0) AS transaction_sum
            FROM ${DatabaseHelper.TABLE_PEOPLE} p
            LEFT JOIN ${DatabaseHelper.TABLE_ACCOUNT_PERIODS} ap 
                ON p.${DatabaseHelper.COL_PEOPLE_ID} = ap.${DatabaseHelper.COL_PERIODS_PERSON_ID} 
                AND ap.${DatabaseHelper.COL_PERIODS_STATUS} = '${AccountPeriod.STATUS_OPEN}'
            LEFT JOIN ${DatabaseHelper.TABLE_TRANSACTIONS} t 
                ON ap.${DatabaseHelper.COL_PERIODS_ID} = t.${DatabaseHelper.COL_TRANS_PERIOD_ID}
            WHERE p.${DatabaseHelper.COL_PEOPLE_ID} = ?
            GROUP BY p.${DatabaseHelper.COL_PEOPLE_ID}
        """.trimIndent()

        val cursor = db.rawQuery(query, arrayOf(personId.toString()))
        return cursor.use {
            if (it.moveToFirst()) {
                val idIdx = it.getColumnIndexOrThrow("person_id")
                val remoteIdIdx = it.getColumnIndex("person_remote_id")
                val nameIdx = it.getColumnIndexOrThrow("person_name")
                val createdIdx = it.getColumnIndexOrThrow("person_created")
                val updatedIdx = it.getColumnIndexOrThrow("person_updated")
                val archivedIdx = it.getColumnIndexOrThrow("person_archived")
                val openingIdx = it.getColumnIndexOrThrow("opening_balance")
                val sumIdx = it.getColumnIndexOrThrow("transaction_sum")

                val opening = if (it.isNull(openingIdx)) 0L else it.getLong(openingIdx)
                val sum = it.getLong(sumIdx)
                val remoteId = if (remoteIdIdx >= 0 && !it.isNull(remoteIdIdx)) it.getString(remoteIdIdx) else null

                Person(
                    id = it.getLong(idIdx),
                    name = it.getString(nameIdx),
                    createdAt = it.getLong(createdIdx),
                    updatedAt = it.getLong(updatedIdx),
                    archived = it.getInt(archivedIdx) == 1,
                    currentBalancePaise = opening + sum,
                    remoteId = remoteId
                )
            } else {
                null
            }
        }
    }

    /**
     * Looks up a local Person by their remote cloud ID (MongoDB _id).
     */
    fun getPersonByRemoteId(remoteId: String): Person? {
        val db = dbHelper.readableDatabase
        val cursor = db.query(
            DatabaseHelper.TABLE_PEOPLE,
            arrayOf(DatabaseHelper.COL_PEOPLE_ID),
            "${DatabaseHelper.COL_PEOPLE_REMOTE_ID} = ?",
            arrayOf(remoteId),
            null, null, null, "1"
        )
        val id = cursor.use {
            if (it.moveToFirst()) it.getLong(0) else null
        }
        return id?.let { getPersonById(it) }
    }

    /**
     * Looks up a local Person by name.
     */
    fun getPersonByName(name: String): Person? {
        val db = dbHelper.readableDatabase
        val cursor = db.query(
            DatabaseHelper.TABLE_PEOPLE,
            arrayOf(DatabaseHelper.COL_PEOPLE_ID),
            "LOWER(${DatabaseHelper.COL_PEOPLE_NAME}) = LOWER(?) AND ${DatabaseHelper.COL_PEOPLE_ARCHIVED} = 0",
            arrayOf(name.trim()),
            null, null, null, "1"
        )
        val id = cursor.use {
            if (it.moveToFirst()) it.getLong(0) else null
        }
        return id?.let { getPersonById(it) }
    }

    /**
     * Updates a local Person's remote cloud ID.
     */
    fun updatePersonRemoteId(personId: Long, remoteId: String) {
        val db = dbHelper.writableDatabase
        val values = ContentValues().apply {
            put(DatabaseHelper.COL_PEOPLE_REMOTE_ID, remoteId)
            put(DatabaseHelper.COL_PEOPLE_UPDATED_AT, System.currentTimeMillis())
        }
        db.update(
            DatabaseHelper.TABLE_PEOPLE,
            values,
            "${DatabaseHelper.COL_PEOPLE_ID} = ?",
            arrayOf(personId.toString())
        )
    }

    /**
     * Returns local people that have not yet been synced to MongoDB Atlas (remote_id is NULL).
     */
    fun getUnsyncedPeople(): List<Person> {
        val all = getAllPeople()
        return all.filter { it.remoteId.isNullOrBlank() }
    }

    /**
     * Calculates the dashboard summary amounts across all people.
     */
    fun getDashboardSummary(): DashboardSummary {
        val people = getAllPeople()
        var totalOthersOweMe = 0L
        var totalIOweOthers = 0L

        for (person in people) {
            val balance = person.currentBalancePaise
            if (balance > 0) {
                totalOthersOweMe += balance
            } else if (balance < 0) {
                totalIOweOthers += kotlin.math.abs(balance)
            }
        }

        val netBalance = totalOthersOweMe - totalIOweOthers
        return DashboardSummary(
            totalOthersOweMe = totalOthersOweMe,
            totalIOweOthers = totalIOweOthers,
            netBalance = netBalance,
            activePeopleCount = people.size
        )
    }

    /**
     * Updates an existing person's name.
     */
    fun updatePersonName(id: Long, newName: String): Pair<Boolean, String?> {
        val trimmed = newName.trim()
        if (trimmed.isEmpty()) {
            return Pair(false, "Name cannot be empty")
        }

        val db = dbHelper.writableDatabase
        // Check for duplicate name for other users
        val cursor = db.rawQuery(
            "SELECT ${DatabaseHelper.COL_PEOPLE_ID} FROM ${DatabaseHelper.TABLE_PEOPLE} " +
                    "WHERE LOWER(${DatabaseHelper.COL_PEOPLE_NAME}) = LOWER(?) AND ${DatabaseHelper.COL_PEOPLE_ID} != ? AND ${DatabaseHelper.COL_PEOPLE_ARCHIVED} = 0",
            arrayOf(trimmed, id.toString())
        )
        val duplicate = cursor.moveToFirst()
        cursor.close()

        if (duplicate) {
            return Pair(false, "Another person with the name \"$trimmed\" already exists")
        }

        val values = ContentValues().apply {
            put(DatabaseHelper.COL_PEOPLE_NAME, trimmed)
            put(DatabaseHelper.COL_PEOPLE_UPDATED_AT, System.currentTimeMillis())
        }
        val rows = db.update(
            DatabaseHelper.TABLE_PEOPLE,
            values,
            "${DatabaseHelper.COL_PEOPLE_ID} = ?",
            arrayOf(id.toString())
        )
        return Pair(rows > 0, if (rows > 0) null else "Person not found")
    }

    /**
     * Deletes a person and cascades to all their periods, transactions, and settlements.
     */
    fun deletePerson(id: Long): Boolean {
        val db = dbHelper.writableDatabase
        val rows = db.delete(
            DatabaseHelper.TABLE_PEOPLE,
            "${DatabaseHelper.COL_PEOPLE_ID} = ?",
            arrayOf(id.toString())
        )
        return rows > 0
    }
}
