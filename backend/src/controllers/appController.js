const AppUpdate = require('../models/AppUpdate');

/**
 * Default fallback metadata if MongoDB does not have an active record yet.
 */
const DEFAULT_UPDATE_METADATA = {
    versionCode: 3,
    versionName: '1.1.1',
    title: 'Hisab v1.1.1 — History Document Isolation & Registration Fix',
    releaseDate: new Date('2026-10-09'),
    whatsNew: [
        '✨ Fixed registration validation: accounts now create reliably without errors',
        '🛡️ Isolated MongoDB History Documents: settled accounting periods stay strictly in History',
        '⚡ Active period protection: updating or syncing never pollutes the current account ledger',
        '🔄 Auto-healing: cleans up any historical transactions that were previously placed in active period'
    ],
    downloadUrl: 'https://github.com/Sanjanasapkal/Hisab/raw/main/Hisab.apk',
    isMandatory: false,
    active: true
};

/**
 * GET /api/app/version
 * Returns the latest active app version & release notes from MongoDB.
 */
async function getLatestVersion(req, res, next) {
    try {
        let latest = await AppUpdate.findOne({ active: true }).sort({ versionCode: -1 });

        if (!latest) {
            // Seed default record into MongoDB if empty
            try {
                latest = await AppUpdate.create(DEFAULT_UPDATE_METADATA);
            } catch (seedErr) {
                // In case of parallel create or read-only replica
                latest = DEFAULT_UPDATE_METADATA;
            }
        }

        return res.status(200).json({
            success: true,
            data: {
                versionCode: latest.versionCode,
                versionName: latest.versionName,
                title: latest.title,
                releaseDate: latest.releaseDate,
                whatsNew: latest.whatsNew,
                downloadUrl: latest.downloadUrl,
                isMandatory: latest.isMandatory
            }
        });
    } catch (error) {
        next(error);
    }
}

/**
 * POST /api/app/version
 * Admin endpoint to publish a new app version announcement in MongoDB.
 */
async function publishVersion(req, res, next) {
    try {
        const { versionCode, versionName, title, whatsNew, downloadUrl, isMandatory } = req.body;
        if (!versionCode || !versionName) {
            return res.status(400).json({
                success: false,
                message: 'versionCode (number) and versionName (string) are required.'
            });
        }

        const update = await AppUpdate.findOneAndUpdate(
            { versionCode },
            {
                versionCode,
                versionName,
                title: title || `Hisab v${versionName}`,
                whatsNew: Array.isArray(whatsNew) ? whatsNew : [whatsNew].filter(Boolean),
                downloadUrl: downloadUrl || 'https://github.com/Sanjanasapkal/Hisab/raw/main/Hisab.apk',
                isMandatory: Boolean(isMandatory),
                active: true,
                releaseDate: new Date()
            },
            { upsert: true, new: true }
        );

        return res.status(201).json({
            success: true,
            message: 'App version published successfully to MongoDB',
            data: update
        });
    } catch (error) {
        next(error);
    }
}

module.exports = {
    getLatestVersion,
    publishVersion
};
