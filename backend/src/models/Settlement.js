const mongoose = require('mongoose');

const settlementSchema = new mongoose.Schema({
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
    finalBalancePaise: {
        type: Number,
        required: true,
        validate: {
            validator: Number.isInteger,
            message: '{VALUE} is not an integer paise value'
        }
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
    clientLocalId: {
        type: Number,
        default: null
    }
}, {
    timestamps: true
});

settlementSchema.index({ ownerUserId: 1, personId: 1, settledAt: -1 });

const Settlement = mongoose.model('Settlement', settlementSchema);

module.exports = Settlement;
