const test = require('node:test');
const assert = require('node:assert/strict');
const http = require('node:http');
const bcrypt = require('bcryptjs');
require('dotenv').config();
const { connectDatabase, disconnectDatabase } = require('../src/config/database');
const app = require('../src/app');
const User = require('../src/models/User');
const Person = require('../src/models/Person');
const AccountPeriod = require('../src/models/AccountPeriod');
const Transaction = require('../src/models/Transaction');
const Settlement = require('../src/models/Settlement');
const { generateToken } = require('../src/services/tokenService');

function apiRequest(app, options) {
    return new Promise((resolve, reject) => {
        const server = http.createServer(app);
        server.listen(0, () => {
            const port = server.address().port;
            const bodyStr = options.body ? JSON.stringify(options.body) : null;
            const headers = {
                'Content-Type': 'application/json',
                ...(bodyStr && { 'Content-Length': Buffer.byteLength(bodyStr) }),
                ...options.headers
            };

            const req = http.request({
                port,
                host: '127.0.0.1',
                method: options.method,
                path: options.path,
                headers
            }, (res) => {
                let data = '';
                res.on('data', chunk => data += chunk);
                res.on('end', () => {
                    server.close();
                    try {
                        const parsed = JSON.parse(data);
                        resolve({ status: res.statusCode, headers: res.headers, body: parsed });
                    } catch (e) {
                        resolve({ status: res.statusCode, headers: res.headers, body: data });
                    }
                });
            });
            req.on('error', (err) => {
                server.close();
                reject(err);
            });
            if (bodyStr) {
                req.write(bodyStr);
            }
            req.end();
        });
    });
}

test('Cloud Ledger & Strict User Data Isolation Suite', async (t) => {
    await connectDatabase();

    const passwordHash = await bcrypt.hash('SecretPass123', 10);

    // Setup 2 isolated users
    const emailA = 'user_a_ledger@example.com';
    const emailB = 'user_b_ledger@example.com';

    await User.deleteMany({ normalizedEmail: { $in: [emailA, emailB] } });

    const userA = await User.create({
        username: 'usera_ledger',
        normalizedUsername: 'usera_ledger',
        email: emailA,
        normalizedEmail: emailA,
        passwordHash,
        emailVerified: true
    });

    const userB = await User.create({
        username: 'userb_ledger',
        normalizedUsername: 'userb_ledger',
        email: emailB,
        normalizedEmail: emailB,
        passwordHash,
        emailVerified: true
    });

    const tokenA = generateToken(userA);
    const tokenB = generateToken(userB);

    // Clean up any existing records for these users
    await Person.deleteMany({ ownerUserId: { $in: [userA._id, userB._id] } });
    await AccountPeriod.deleteMany({ ownerUserId: { $in: [userA._id, userB._id] } });
    await Transaction.deleteMany({ ownerUserId: { $in: [userA._id, userB._id] } });
    await Settlement.deleteMany({ ownerUserId: { $in: [userA._id, userB._id] } });

    let personAId = null;
    let transactionAId = null;

    await t.test('1. User A creates a person (Sakshi)', async () => {
        const res = await apiRequest(app, {
            method: 'POST',
            path: '/api/people',
            headers: { Authorization: `Bearer ${tokenA}` },
            body: { name: 'Sakshi' }
        });

        assert.equal(res.status, 201);
        assert.equal(res.body.success, true);
        assert.equal(res.body.data.name, 'Sakshi');
        assert.equal(res.body.data.currentBalancePaise, 0);

        personAId = res.body.data.id;
    });

    await t.test('2. User A records transactions for Sakshi (Lent ₹100, Lunch ₹90)', async () => {
        // Transaction 1: +₹100 (10000 paise)
        const res1 = await apiRequest(app, {
            method: 'POST',
            path: '/api/transactions',
            headers: { Authorization: `Bearer ${tokenA}` },
            body: {
                personId: personAId,
                amountPaise: 10000,
                reason: 'Money lent for books'
            }
        });
        assert.equal(res1.status, 201);
        assert.equal(res1.body.data.amountPaise, 10000);
        transactionAId = res1.body.data._id;

        // Transaction 2: +₹90 (9000 paise)
        const res2 = await apiRequest(app, {
            method: 'POST',
            path: '/api/transactions',
            headers: { Authorization: `Bearer ${tokenA}` },
            body: {
                personId: personAId,
                amountPaise: 9000,
                reason: 'Lunch bill split'
            }
        });
        assert.equal(res2.status, 201);
        assert.equal(res2.body.data.amountPaise, 9000);
    });

    await t.test('3. User A checks dashboard metrics and balance (Sakshi owes ₹190)', async () => {
        const res = await apiRequest(app, {
            method: 'GET',
            path: '/api/people',
            headers: { Authorization: `Bearer ${tokenA}` }
        });

        assert.equal(res.status, 200);
        assert.equal(res.body.summary.totalOthersOweMe, 19000); // 10000 + 9000 = 19000 paise
        assert.equal(res.body.summary.totalIOweOthers, 0);
        assert.equal(res.body.summary.netBalance, 19000);
        assert.equal(res.body.data[0].currentBalancePaise, 19000);
    });

    // ---------------- STRICT USER DATA ISOLATION TESTS ----------------

    await t.test('4. SECURITY: User B cannot see User A\'s people in list', async () => {
        const res = await apiRequest(app, {
            method: 'GET',
            path: '/api/people',
            headers: { Authorization: `Bearer ${tokenB}` }
        });

        assert.equal(res.status, 200);
        assert.equal(res.body.data.length, 0, 'User B must not see any of User A\'s people');
        assert.equal(res.body.summary.netBalance, 0);
    });

    await t.test('5. SECURITY: User B cannot fetch User A\'s person by ID', async () => {
        const res = await apiRequest(app, {
            method: 'GET',
            path: `/api/people/${personAId}`,
            headers: { Authorization: `Bearer ${tokenB}` }
        });

        assert.equal(res.status, 404, 'Must return 404 for records owned by another user');
    });

    await t.test('6. SECURITY: User B cannot edit User A\'s person name', async () => {
        const res = await apiRequest(app, {
            method: 'PATCH',
            path: `/api/people/${personAId}`,
            headers: { Authorization: `Bearer ${tokenB}` },
            body: { name: 'Hacked Name' }
        });

        assert.equal(res.status, 404);

        // Verify name was not modified in database
        const personInDb = await Person.findById(personAId);
        assert.equal(personInDb.name, 'Sakshi');
    });

    await t.test('7. SECURITY: User B cannot delete User A\'s person', async () => {
        const res = await apiRequest(app, {
            method: 'DELETE',
            path: `/api/people/${personAId}`,
            headers: { Authorization: `Bearer ${tokenB}` }
        });

        assert.equal(res.status, 404);

        const personInDb = await Person.findById(personAId);
        assert.equal(personInDb.archived, false);
    });

    await t.test('8. SECURITY: User B cannot add transactions to User A\'s person', async () => {
        const res = await apiRequest(app, {
            method: 'POST',
            path: '/api/transactions',
            headers: { Authorization: `Bearer ${tokenB}` },
            body: {
                personId: personAId,
                amountPaise: 50000,
                reason: 'Malicious transaction attempt'
            }
        });

        assert.equal(res.status, 404);
    });

    await t.test('9. SECURITY: User B cannot delete User A\'s transaction', async () => {
        const res = await apiRequest(app, {
            method: 'DELETE',
            path: `/api/transactions/${transactionAId}`,
            headers: { Authorization: `Bearer ${tokenB}` }
        });

        assert.equal(res.status, 404);

        const txInDb = await Transaction.findById(transactionAId);
        assert.ok(txInDb, 'Transaction must still exist');
    });

    await t.test('10. SECURITY: User B cannot settle User A\'s person', async () => {
        const res = await apiRequest(app, {
            method: 'POST',
            path: '/api/transactions/settle',
            headers: { Authorization: `Bearer ${tokenB}` },
            body: {
                personId: personAId,
                note: 'Malicious settlement attempt'
            }
        });

        assert.equal(res.status, 404);
    });

    // ---------------- SETTLEMENT & HISTORICAL AUDIT TRAIL ----------------

    await t.test('11. User A settles account with Sakshi (balance resets to 0, history preserved)', async () => {
        const settleRes = await apiRequest(app, {
            method: 'POST',
            path: '/api/transactions/settle',
            headers: { Authorization: `Bearer ${tokenA}` },
            body: {
                personId: personAId,
                note: 'Settled via Google Pay UPI'
            }
        });

        assert.equal(settleRes.status, 200);
        assert.equal(settleRes.body.success, true);
        assert.equal(settleRes.body.data.finalBalancePaise, 19000);
        assert.equal(settleRes.body.data.closedPeriod.status, 'closed');
        assert.equal(settleRes.body.data.newPeriod.status, 'open');
        assert.equal(settleRes.body.data.newPeriod.openingBalancePaise, 0);

        // Verify active balance is now 0 for Sakshi
        const personRes = await apiRequest(app, {
            method: 'GET',
            path: `/api/people/${personAId}`,
            headers: { Authorization: `Bearer ${tokenA}` }
        });
        assert.equal(personRes.status, 200);
        assert.equal(personRes.body.data.person.currentBalancePaise, 0);
        assert.equal(personRes.body.data.transactions.length, 0); // No active transactions in new period

        // Verify history endpoint retains the closed period, transactions, and settlement note
        const historyRes = await apiRequest(app, {
            method: 'GET',
            path: `/api/people/${personAId}/history`,
            headers: { Authorization: `Bearer ${tokenA}` }
        });
        assert.equal(historyRes.status, 200);
        assert.equal(historyRes.body.data.history.length, 2); // 1 open period, 1 closed period

        const closedHistoryItem = historyRes.body.data.history.find(h => h.period.status === 'closed');
        assert.ok(closedHistoryItem);
        assert.equal(closedHistoryItem.transactions.length, 2); // The 2 transactions are PRESERVED!
        assert.equal(closedHistoryItem.settlement.note, 'Settled via Google Pay UPI');
        assert.equal(closedHistoryItem.calculatedBalancePaise, 19000);
    });

    // Cleanup
    await Person.deleteMany({ ownerUserId: { $in: [userA._id, userB._id] } });
    await AccountPeriod.deleteMany({ ownerUserId: { $in: [userA._id, userB._id] } });
    await Transaction.deleteMany({ ownerUserId: { $in: [userA._id, userB._id] } });
    await Settlement.deleteMany({ ownerUserId: { $in: [userA._id, userB._id] } });
    await User.deleteMany({ normalizedEmail: { $in: [emailA, emailB] } });

    await disconnectDatabase();
});
