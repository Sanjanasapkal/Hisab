const Transaction = require('../models/Transaction');
const Person = require('../models/Person');
const AccountPeriod = require('../models/AccountPeriod');
const Settlement = require('../models/Settlement');

/**
 * POST /api/transactions
 * 
 * Records a new financial transaction against a person's current open accounting period.
 * Strictly verifies that the referenced person is owned by the authenticated user.
 */
async function addTransaction(req, res, next) {
    try {
        const { personId, amountPaise, reason, transactionDate, notes, clientLocalId } = req.body;

        // 1. Verify referenced person exists and is owned by authenticated user
        const person = await Person.findOne({
            _id: personId,
            ownerUserId: req.userId,
            archived: false
        });

        if (!person) {
            return res.status(404).json({
                success: false,
                message: 'Referenced person not found or access denied'
            });
        }

        // 2. Validate amount in integer paise
        if (typeof amountPaise !== 'number' || !Number.isInteger(amountPaise) || amountPaise === 0) {
            return res.status(400).json({
                success: false,
                message: 'Amount must be a non-zero integer in paise'
            });
        }

        // 3. Validate reason
        const trimmedReason = (reason || '').trim();
        if (!trimmedReason) {
            return res.status(400).json({
                success: false,
                message: 'Reason is required'
            });
        }

        // 4. Get or create active open period
        let openPeriod = await AccountPeriod.findOne({
            ownerUserId: req.userId,
            personId: person._id,
            status: 'open'
        });

        if (!openPeriod) {
            openPeriod = await AccountPeriod.create({
                ownerUserId: req.userId,
                personId: person._id,
                status: 'open',
                openingBalancePaise: 0
            });
        }

        // 5. Create transaction
        const txDate = transactionDate ? new Date(transactionDate) : new Date();
        const transaction = await Transaction.create({
            ownerUserId: req.userId,
            personId: person._id,
            periodId: openPeriod._id,
            amountPaise,
            reason: trimmedReason,
            transactionDate: txDate,
            notes: notes ? String(notes).trim() : null,
            clientLocalId: clientLocalId || null
        });

        // 6. Check if this transaction brings the open period balance to ₹0.00
        // If balance reaches 0, auto-close the period into history so active period stays clean!
        const allPeriodTx = await Transaction.find({
            ownerUserId: req.userId,
            periodId: openPeriod._id
        });
        const periodTotal = (openPeriod.openingBalancePaise || 0) + allPeriodTx.reduce((sum, t) => sum + t.amountPaise, 0);

        if (allPeriodTx.length > 0 && periodTotal === 0) {
            openPeriod.status = 'closed';
            openPeriod.closedAt = txDate;
            openPeriod.closingBalancePaise = 0;
            await openPeriod.save();

            await Settlement.create({
                ownerUserId: req.userId,
                personId: person._id,
                periodId: openPeriod._id,
                finalBalancePaise: 0,
                settledAt: txDate,
                note: 'Settled (Balance cleared to ₹0.00)'
            });

            await AccountPeriod.create({
                ownerUserId: req.userId,
                personId: person._id,
                startedAt: txDate,
                openingBalancePaise: 0,
                status: 'open'
            });
        }

        return res.status(201).json({
            success: true,
            message: 'Transaction saved successfully',
            data: transaction
        });
    } catch (error) {
        next(error);
    }
}

/**
 * DELETE /api/transactions/:id
 * 
 * Deletes an individual transaction.
 * Strictly verifies ownership.
 */
async function deleteTransaction(req, res, next) {
    try {
        const transaction = await Transaction.findOneAndDelete({
            _id: req.params.id,
            ownerUserId: req.userId
        });

        if (!transaction) {
            return res.status(404).json({
                success: false,
                message: 'Transaction not found or access denied'
            });
        }

        return res.status(200).json({
            success: true,
            message: 'Transaction deleted successfully'
        });
    } catch (error) {
        next(error);
    }
}

/**
 * POST /api/transactions/settle
 * 
 * Settles an account for a person:
 * 1. Calculates final balance of open period.
 * 2. Closes open period and stamps closing balance.
 * 3. Records a Settlement event.
 * 4. Creates a new fresh open period starting at 0 paise.
 */
async function settleHisab(req, res, next) {
    try {
        const { personId, note, clientLocalId, settledAt } = req.body;

        const person = await Person.findOne({
            _id: personId,
            ownerUserId: req.userId,
            archived: false
        });

        if (!person) {
            return res.status(404).json({
                success: false,
                message: 'Person not found or access denied'
            });
        }

        // Find active open period
        let openPeriod = await AccountPeriod.findOne({
            ownerUserId: req.userId,
            personId: person._id,
            status: 'open'
        });

        if (!openPeriod) {
            openPeriod = await AccountPeriod.create({
                ownerUserId: req.userId,
                personId: person._id,
                status: 'open',
                openingBalancePaise: 0
            });
        }

        // Calculate final balance
        const transactions = await Transaction.find({
            ownerUserId: req.userId,
            periodId: openPeriod._id
        });

        const txSum = transactions.reduce((acc, t) => acc + t.amountPaise, 0);
        const finalBalance = (openPeriod.openingBalancePaise || 0) + txSum;
        const settleDate = settledAt ? new Date(settledAt) : new Date();

        // 1. Close current period
        openPeriod.status = 'closed';
        openPeriod.closedAt = settleDate;
        openPeriod.closingBalancePaise = finalBalance;
        await openPeriod.save();

        // 2. Insert settlement record
        const settlement = await Settlement.create({
            ownerUserId: req.userId,
            personId: person._id,
            periodId: openPeriod._id,
            finalBalancePaise: finalBalance,
            settledAt: settleDate,
            note: note ? String(note).trim() : null,
            clientLocalId: clientLocalId || null
        });

        // 3. Open brand new period starting at 0 paise
        const newOpenPeriod = await AccountPeriod.create({
            ownerUserId: req.userId,
            personId: person._id,
            startedAt: settleDate,
            openingBalancePaise: 0,
            status: 'open'
        });

        return res.status(200).json({
            success: true,
            message: 'Account settled successfully',
            data: {
                closedPeriod: openPeriod,
                settlement,
                newPeriod: newOpenPeriod,
                finalBalancePaise: finalBalance
            }
        });
    } catch (error) {
        next(error);
    }
}

module.exports = {
    addTransaction,
    deleteTransaction,
    settleHisab
};
