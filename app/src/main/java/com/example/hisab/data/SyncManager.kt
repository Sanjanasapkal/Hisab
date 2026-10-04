package com.example.hisab.data

import android.content.Context
import com.example.hisab.api.ApiClient
import com.example.hisab.api.CreatePersonRequest
import com.example.hisab.api.CreateTransactionRequest
import com.example.hisab.api.SessionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/**
 * Result data holder for cloud ledger synchronization.
 */
data class SyncResult(
    val success: Boolean,
    val message: String,
    val peopleSynced: Int = 0,
    val transactionsSynced: Int = 0
)

/**
 * SyncManager orchestrates bi-directional synchronization between local SQLite
 * and the Node.js / MongoDB Atlas cloud database.
 * 
 * Rules:
 * - Offline-first: Reads/writes are always instant on local SQLite.
 * - Multi-user isolation: Every user has an independent local database file (hisab_{userId}.db).
 * - Bi-directional:
 *   1. Pulls remote people and transactions from Atlas into SQLite.
 *   2. Pushes offline-created local people and transactions to Atlas.
 *   3. Avoids duplicates by matching MongoDB `_id` against SQLite `remote_id`.
 */
object SyncManager {

    private val syncMutex = Mutex()

    suspend fun sync(context: Context): SyncResult = withContext(Dispatchers.IO) {
        syncMutex.withLock {
            val sessionManager = SessionManager.getInstance(context)
            if (!sessionManager.isLoggedIn()) {
                return@withLock SyncResult(false, "Please log in to sync with cloud.")
            }

            val apiService = ApiClient.getService(context)
            val personRepo = PersonRepository(context)
            val txRepo = TransactionRepository(context)

            var peopleCount = 0
            var txCount = 0

            try {
                // =========================================================================
                // Step 1: Pull Remote People from MongoDB Atlas
                // =========================================================================
                val remotePeopleResp = apiService.getPeople()
                if (remotePeopleResp.isSuccessful && remotePeopleResp.body()?.success == true) {
                    val remoteList = remotePeopleResp.body()?.data.orEmpty()
                    val remoteIdSet = remoteList.map { it.id }.toSet()

                    for (remotePerson in remoteList) {
                        val existingByRemoteId = personRepo.getPersonByRemoteId(remotePerson.id)
                        if (existingByRemoteId == null) {
                            // Check if a person with the same name exists locally
                            val existingByName = personRepo.getPersonByName(remotePerson.name)
                            if (existingByName != null) {
                                personRepo.updatePersonRemoteId(existingByName.id, remotePerson.id)
                            } else {
                                personRepo.addPerson(remotePerson.name, remoteId = remotePerson.id)
                            }
                        } else if (existingByRemoteId.name != remotePerson.name) {
                            personRepo.updatePersonName(existingByRemoteId.id, remotePerson.name)
                        }
                        peopleCount++
                    }

                    // Bi-directional deletion: If a local person has a remoteId, but that remoteId is
                    // no longer in Atlas's active list (i.e. was deleted/archived on Atlas), remove it locally:
                    val localPeople = personRepo.getAllPeople()
                    for (localP in localPeople) {
                        val rId = localP.remoteId
                        if (!rId.isNullOrBlank() && !remoteIdSet.contains(rId)) {
                            personRepo.deletePerson(localP.id)
                        }
                    }
                }

            // =========================================================================
            // Step 2: Push Local Unsynced People (Created Offline) to Atlas
            // =========================================================================
            val unsyncedPeople = personRepo.getUnsyncedPeople()
            for (localPerson in unsyncedPeople) {
                try {
                    val createResp = apiService.createPerson(
                        CreatePersonRequest(name = localPerson.name, clientLocalId = localPerson.id)
                    )
                    if (createResp.isSuccessful && createResp.body()?.success == true) {
                        val remoteId = createResp.body()?.data?.id
                        if (!remoteId.isNullOrBlank()) {
                            personRepo.updatePersonRemoteId(localPerson.id, remoteId)
                            peopleCount++
                        }
                    }
                } catch (e: Exception) {
                    // Log and continue to allow partial sync
                }
            }

            // =========================================================================
            // Step 3: Pull Remote Transactions for all people with remoteId
            // =========================================================================
            val allLocalPeople = personRepo.getAllPeople()
            for (person in allLocalPeople) {
                val remotePersonId = person.remoteId ?: continue

                try {
                    val historyResp = apiService.getPersonHistory(remotePersonId)
                    if (historyResp.isSuccessful && historyResp.body()?.success == true) {
                        val transactions = historyResp.body()?.data?.transactions.orEmpty()
                        for (remoteTx in transactions) {
                            val existingTx = txRepo.getTransactionByRemoteId(remoteTx.id)
                            if (existingTx == null) {
                                val txDate = parseIsoDate(remoteTx.transactionDate)
                                txRepo.addTransaction(
                                    personId = person.id,
                                    amountPaise = remoteTx.amountPaise,
                                    reason = remoteTx.reason,
                                    transactionDate = txDate,
                                    notes = remoteTx.notes,
                                    remoteId = remoteTx.id
                                )
                                txCount++
                            }
                        }
                    }
                } catch (e: Exception) {
                    // Continue to next person
                }
            }

            // =========================================================================
            // Step 4: Push Local Unsynced Transactions (Created Offline) to Atlas
            // =========================================================================
            val unsyncedTx = txRepo.getUnsyncedTransactions()
            for (tx in unsyncedTx) {
                val person = personRepo.getPersonById(tx.personId) ?: continue
                val remotePersonId = person.remoteId ?: continue

                try {
                    val addTxResp = apiService.addTransaction(
                        CreateTransactionRequest(
                            personId = remotePersonId,
                            amountPaise = tx.amountPaise,
                            reason = tx.reason,
                            transactionDate = tx.transactionDate,
                            notes = tx.notes,
                            clientLocalId = tx.id
                        )
                    )
                    if (addTxResp.isSuccessful && addTxResp.body()?.success == true) {
                        val remoteTxId = addTxResp.body()?.data?.id
                        if (!remoteTxId.isNullOrBlank()) {
                            txRepo.updateTransactionRemoteId(tx.id, remoteTxId)
                            txCount++
                        }
                    }
                } catch (e: Exception) {
                    // Continue to next transaction
                }
            }

            SyncResult(
                success = true,
                message = "Synced $peopleCount contacts and $txCount transactions with cloud.",
                peopleSynced = peopleCount,
                transactionsSynced = txCount
            )
        } catch (e: Exception) {
            SyncResult(
                success = false,
                message = "Sync failed: ${e.localizedMessage ?: "Network error"}",
                peopleSynced = peopleCount,
                transactionsSynced = txCount
            )
        }
    }
}

    private fun parseIsoDate(isoString: String?): Long {
        if (isoString.isNullOrBlank()) return System.currentTimeMillis()
        return try {
            val format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }
            format.parse(isoString)?.time ?: System.currentTimeMillis()
        } catch (e: Exception) {
            try {
                val formatSec = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
                    timeZone = TimeZone.getTimeZone("UTC")
                }
                formatSec.parse(isoString)?.time ?: System.currentTimeMillis()
            } catch (e2: Exception) {
                System.currentTimeMillis()
            }
        }
    }
}
