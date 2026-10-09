const https = require('https');
const AppUpdate = require('../models/AppUpdate');

/**
 * Default fallback metadata if MongoDB does not have an active record yet.
 */
const DEFAULT_UPDATE_METADATA = {
    versionCode: 4,
    versionName: '1.1.2',
    title: 'Hisab v1.1.2 — In-App Updates & Stability Improvements',
    releaseDate: new Date('2026-10-09'),
    whatsNew: [
        '🚀 Seamless In-App Updates: Download and install new updates directly within Hisab without leaving the app',
        '✨ Enhanced Ledger Performance: Smoother account balance calculations and instant settlement sync',
        '🔒 Cloud Backup & History: Secure, isolated accounting records with zero data overlap',
        '🛠️ General Bug Fixes: Core stability improvements and responsive UI refinements'
    ],
    downloadUrl: 'https://hisab-zovn.onrender.com/api/app/download',
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

/**
 * GET /api/app/download
 * Streams the APK binary directly from the server with proper attachment headers.
 * Users are never redirected to GitHub or third-party web pages.
 */
async function downloadApk(req, res, next) {
    try {
        const rawUrl = 'https://raw.githubusercontent.com/Sanjanasapkal/Hisab/main/Hisab.apk';

        https.get(rawUrl, (upstream) => {
            if (upstream.statusCode >= 300 && upstream.statusCode < 400 && upstream.headers.location) {
                https.get(upstream.headers.location, (redirected) => {
                    res.setHeader('Content-Type', 'application/vnd.android.package-archive');
                    res.setHeader('Content-Disposition', 'attachment; filename="Hisab.apk"');
                    if (redirected.headers['content-length']) {
                        res.setHeader('Content-Length', redirected.headers['content-length']);
                    }
                    redirected.pipe(res);
                }).on('error', next);
            } else {
                res.setHeader('Content-Type', 'application/vnd.android.package-archive');
                res.setHeader('Content-Disposition', 'attachment; filename="Hisab.apk"');
                if (upstream.headers['content-length']) {
                    res.setHeader('Content-Length', upstream.headers['content-length']);
                }
                upstream.pipe(res);
            }
        }).on('error', next);
    } catch (error) {
        next(error);
    }
}

module.exports = {
    getLatestVersion,
    publishVersion,
    downloadApk
};
