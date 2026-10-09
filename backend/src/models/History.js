const mongoose = require('mongoose');

/**
 * Embedded transaction snapshot within a historical accounting period.
 * Preserves the exact transaction details at the time of settlement.
 */
const historyTransactionSchema = new mongoose.Schema({
    originalTxId: {
        type: mongoose.Schema.Types.ObjectId,
        ref: 'Transaction',
        default: null
    },
    amountPaise: {
        type: Number,
        required: true,
        validate: {
            validator: function(val) {
                return Number.isInteger(val) && val !== 0;
            },
            message: '{VALUE} is not a valid non-zero integer paise amount'
        }
    },
    reason: {
        type: String,
        required: true,
        trim: true
    },
    transactionDate: {
        type: Date,
        default: Date.now
    },
    notes: {
        type: String,
        trim: true,
        default: null
    },
    clientLocalId: {
        type: Number,
        default: null
    }
}, { _id: true });

/**
 * Dedicated History document schema for MongoDB.
 * 
 * Each document captures a completely settled/closed accounting period, its
 * final balance, settlement timestamp, settlement note, and full list of
 * archived transactions. This guarantees that history records never get mixed
 * up with active, open-period transactions.
 */
const historySchema = new mongoose.Schema({
    ownerUserId: {
        type: mongoose.Schema.Types.ObjectId,
        ref: 'User',
        required: true,
        index: true
    },
    personId: {
        type: mongoose.Schema.Types.ObjectId,
        ref: 'Person',
        required: true,
        index: true
    },
    periodId: {
        type: mongoose.Schema.Types.ObjectId,
        ref: 'AccountPeriod',
        required: true,
        index: true
    },
    settlementId: {
        type: mongoose.Schema.Types.ObjectId,
        ref: 'Settlement',
        default: null
    },
    startedAt: {
        type: Date,
        required: true
    },
    closedAt: {
        type: Date,
        required: true
    },
    openingBalancePaise: {
        type: Number,
        default: 0
    },
    closingBalancePaise: {
        type: Number,
        default: 0
    },
    finalBalancePaise: {
        type: Number,
        default: 0
    },
    settledAt: {
        type: Date,
        default: Date.now,
        index: true
    },
    note: {
        type: String,
        trim: true,
        default: null
    },
    transactions: [historyTransactionSchema],
    clientLocalId: {
        type: Number,
        default: null
    }
}, {
    timestamps: true
});

historySchema.index({ ownerUserId: 1, personId: 1, settledAt: -1 });
historySchema.index({ ownerUserId: 1, periodId: 1 });

const History = mongoose.model('History', historySchema);

module.exports = History;
