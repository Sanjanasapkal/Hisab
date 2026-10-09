const mongoose = require('mongoose');

const transactionSchema = new mongoose.Schema({
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
    amountPaise: {
        type: Number,
        required: [true, 'Amount is required'],
        validate: {
            validator: function(val) {
                return Number.isInteger(val) && val !== 0;
            },
            message: 'Amount must be a non-zero integer in paise'
        }
    },
    reason: {
        type: String,
        required: [true, 'Reason is required'],
        trim: true,
        minlength: [1, 'Reason cannot be empty'],
        maxlength: [200, 'Reason cannot exceed 200 characters']
    },
    transactionDate: {
        type: Date,
        default: Date.now,
        index: true
    },
    notes: {
        type: String,
        trim: true,
        default: null
    },
    clientLocalId: {
        type: Number,
        default: null
    },
    isSettled: {
        type: Boolean,
        default: false,
        index: true
    }
}, {
    timestamps: true
});

transactionSchema.index({ ownerUserId: 1, personId: 1, transactionDate: -1 });
transactionSchema.index({ ownerUserId: 1, periodId: 1 });

const Transaction = mongoose.model('Transaction', transactionSchema);

module.exports = Transaction;
