const test = require('node:test');
const assert = require('node:assert/strict');
const http = require('node:http');
require('dotenv').config();
const { connectDatabase, disconnectDatabase } = require('../src/config/database');
const app = require('../src/app');
const AppUpdate = require('../src/models/AppUpdate');

function apiRequest(app, options) {
    return new Promise((resolve, reject) => {
        const server = http.createServer(app);
        server.listen(0, () => {
            const port = server.address().port;
            const bodyStr = options.body ? JSON.stringify(options.body) : null;
            const headers = {
                'Content-Type': 'application/json',
                ...(bodyStr && { 'Content-Length': Buffer.byteLength(bodyStr) }),
                ...options.headers
            };

            const req = http.request({
                port,
                host: '127.0.0.1',
                method: options.method,
                path: options.path,
                headers
            }, (res) => {
                let data = '';
                res.on('data', chunk => data += chunk);
                res.on('end', () => {
                    server.close();
                    try {
                        const parsed = data ? JSON.parse(data) : {};
                        resolve({ status: res.statusCode, body: parsed, headers: res.headers });
                    } catch (e) {
                        resolve({ status: res.statusCode, raw: data, headers: res.headers });
                    }
                });
            });

            req.on('error', (err) => {
                server.close();
                reject(err);
            });

            if (bodyStr) req.write(bodyStr);
            req.end();
        });
    });
}

test('App Version & Announcement Suite', async (t) => {
    await connectDatabase();

    await t.test('1. GET /api/app/version returns valid version metadata', async () => {
        const res = await apiRequest(app, {
            method: 'GET',
            path: '/api/app/version'
        });

        assert.equal(res.status, 200);
        assert.equal(res.body.success, true);
        assert.ok(typeof res.body.data.versionCode === 'number');
        assert.ok(typeof res.body.data.versionName === 'string');
        assert.ok(Array.isArray(res.body.data.whatsNew));
        assert.ok(res.body.data.downloadUrl);
    });

    await t.test('2. POST /api/app/version creates/updates new version in MongoDB', async () => {
        const res = await apiRequest(app, {
            method: 'POST',
            path: '/api/app/version',
            body: {
                versionCode: 99,
                versionName: '9.9.0',
                title: 'Hisab Future Update',
                whatsNew: ['Test Feature A', 'Test Feature B'],
                downloadUrl: 'https://example.com/test-hisab.apk'
            }
        });

        assert.equal(res.status, 201);
        assert.equal(res.body.success, true);
        assert.equal(res.body.data.versionCode, 99);

        // Fetch to confirm it is now returned as latest
        const latestRes = await apiRequest(app, {
            method: 'GET',
            path: '/api/app/version'
        });

        assert.equal(latestRes.status, 200);
        assert.equal(latestRes.body.data.versionCode, 99);
        assert.equal(latestRes.body.data.versionName, '9.9.0');

        // Cleanup test version
        await AppUpdate.deleteOne({ versionCode: 99 });
    });

    await disconnectDatabase();
});
