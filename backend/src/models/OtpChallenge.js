const mongoose = require('mongoose');

const otpChallengeSchema = new mongoose.Schema({
    userId: {
        type: mongoose.Schema.Types.ObjectId,
        ref: 'User',
        required: true,
        index: true
    },
    email: {
        type: String,
        required: true,
        lowercase: true,
        trim: true,
        index: true
    },
    purpose: {
        type: String,
        enum: ['EMAIL_VERIFICATION', 'PASSWORD_RESET'],
        default: 'EMAIL_VERIFICATION',
        required: true
    },
    otpHash: {
        type: String,
        required: true
    },
    attempts: {
        type: Number,
        default: 0
    },
    maxAttempts: {
        type: Number,
        default: 5
    },
    expiresAt: {
        type: Date,
        required: true
    },
    resendAvailableAt: {
        type: Date,
        required: true
    },
    used: {
        type: Boolean,
        default: false
    }
}, {
    timestamps: true
});

// TTL Index: Automatically purge challenge documents 1 hour after expiration
otpChallengeSchema.index({ expiresAt: 1 }, { expireAfterSeconds: 3600 });

// Compound index for querying active challenges for a user & purpose
otpChallengeSchema.index({ userId: 1, purpose: 1, used: 1 });

const OtpChallenge = mongoose.model('OtpChallenge', otpChallengeSchema);

module.exports = OtpChallenge;
