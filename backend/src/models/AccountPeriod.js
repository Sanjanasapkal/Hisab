const mongoose = require('mongoose');

const accountPeriodSchema = new mongoose.Schema({
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
    startedAt: {
        type: Date,
        default: Date.now
    },
    closedAt: {
        type: Date,
        default: null
    },
    openingBalancePaise: {
        type: Number,
        default: 0,
        validate: {
            validator: Number.isInteger,
            message: '{VALUE} is not an integer paise value'
        }
    },
    closingBalancePaise: {
        type: Number,
        default: null,
        validate: {
            validator: function(val) {
                return val === null || Number.isInteger(val);
            },
            message: '{VALUE} is not an integer paise value'
        }
    },
    status: {
        type: String,
        enum: ['open', 'closed'],
        default: 'open',
        index: true
    },
    clientLocalId: {
        type: Number,
        default: null
    }
}, {
    timestamps: true
});

accountPeriodSchema.index({ ownerUserId: 1, personId: 1, status: 1 });

const AccountPeriod = mongoose.model('AccountPeriod', accountPeriodSchema);

module.exports = AccountPeriod;
