const mongoose = require('mongoose');

const personSchema = new mongoose.Schema({
    ownerUserId: {
        type: mongoose.Schema.Types.ObjectId,
        ref: 'User',
        required: true,
        index: true
    },
    name: {
        type: String,
        required: [true, 'Person name is required'],
        trim: true,
        minlength: [1, 'Name cannot be empty'],
        maxlength: [100, 'Name cannot exceed 100 characters']
    },
    archived: {
        type: Boolean,
        default: false,
        index: true
    },
    clientLocalId: {
        type: Number,
        default: null
    }
}, {
    timestamps: true
});

// Ensure fast lookup for user's people
personSchema.index({ ownerUserId: 1, name: 1, archived: 1 });

const Person = mongoose.model('Person', personSchema);

module.exports = Person;
