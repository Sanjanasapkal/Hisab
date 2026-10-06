const AppUpdate = require('../models/AppUpdate');

/**
 * Default fallback metadata if MongoDB does not have an active record yet.
 */
const DEFAULT_UPDATE_METADATA = {
    versionCode: 2,
    versionName: '1.1.0',
    title: 'Hisab v1.1.0 — Smart Calculations & Simplified UI',
    releaseDate: new Date('2026-10-06'),
    whatsNew: [
        '⚡ Simplified 2-way Google Pay style arrows (↗ You Gave / ↙ You Got)',
        '📅 Custom settlement date selector with calendar picker',
        '🔄 Auto-settle: balanced transactions (₹0) automatically move to History',
        '📖 Comprehensive "About Hisab & Calculation Guide" in options menu',
        '🔑 Seamless login for emails with dots (e.g. tanujamohite.286@gmail.com)'
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
