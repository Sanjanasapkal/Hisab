const mongoose = require('mongoose');

const appUpdateSchema = new mongoose.Schema({
    versionCode: {
        type: Number,
        required: true,
        unique: true,
        index: true
    },
    versionName: {
        type: String,
        required: true,
        trim: true
    },
    title: {
        type: String,
        default: 'New Update Available'
    },
    releaseDate: {
        type: Date,
        default: Date.now
    },
    whatsNew: {
        type: [String],
        default: []
    },
    downloadUrl: {
        type: String,
        default: 'https://github.com/Sanjanasapkal/Hisab/raw/main/Hisab.apk'
    },
    isMandatory: {
        type: Boolean,
        default: false
    },
    active: {
        type: Boolean,
        default: true
    }
}, {
    timestamps: true
});

module.exports = mongoose.model('AppUpdate', appUpdateSchema);
