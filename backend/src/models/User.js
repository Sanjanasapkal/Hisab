const mongoose = require('mongoose');
const bcrypt = require('bcryptjs');

const userSchema = new mongoose.Schema({
    name: {
        type: String,
        trim: true,
        default: ''
    },
    // Optional legacy username support without unique constraint
    username: {
        type: String,
        trim: true,
        default: ''
    },
    email: {
        type: String,
        required: [true, 'Email address is required'],
        trim: true,
        lowercase: true,
        match: [/^\S+@\S+\.\S+$/, 'Please provide a valid email address']
    },
    normalizedEmail: {
        type: String,
        required: true,
        unique: true,
        lowercase: true,
        trim: true,
        index: true
    },
    passwordHash: {
        type: String,
        required: [true, 'Password is required']
    },
    emailVerified: {
        type: Boolean,
        default: false,
        index: true
    },
    emailVerifiedAt: {
        type: Date,
        default: null
    },
    createdAt: {
        type: Date,
        default: Date.now
    },
    updatedAt: {
        type: Date,
        default: Date.now
    }
}, {
    timestamps: true
});

// Instance method to compare plaintext password with stored bcrypt hash
userSchema.methods.comparePassword = async function(plainPassword) {
    if (!this.passwordHash) return false;
    return bcrypt.compare(plainPassword, this.passwordHash);
};

// Transform output to strip sensitive fields (passwordHash, __v) when converted to JSON
userSchema.set('toJSON', {
    transform: function(doc, ret) {
        delete ret.passwordHash;
        delete ret.__v;
        return ret;
    }
});

const User = mongoose.model('User', userSchema);

// Safely drop legacy unique username index from MongoDB Atlas if it exists
User.collection.dropIndex('normalizedUsername_1').catch(() => {});

module.exports = User;
