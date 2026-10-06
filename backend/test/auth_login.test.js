const test = require('node:test');
const assert = require('node:assert/strict');
const http = require('node:http');
const bcrypt = require('bcryptjs');
require('dotenv').config();
const { connectDatabase, disconnectDatabase } = require('../src/config/database');
const app = require('../src/app');
const User = require('../src/models/User');

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
                        const parsed = JSON.parse(data);
                        resolve({ status: res.statusCode, headers: res.headers, body: parsed });
                    } catch (e) {
                        resolve({ status: res.statusCode, headers: res.headers, body: data });
                    }
                });
            });
            req.on('error', (err) => {
                server.close();
                reject(err);
            });
            if (bodyStr) {
                req.write(bodyStr);
            }
            req.end();
        });
    });
}

test('Authentication - Login & Session Suite', async (t) => {
    await connectDatabase();

    const testPassword = 'CorrectPassword123';
    const passwordHash = await bcrypt.hash(testPassword, 10);

    // Setup: 1 verified user and 1 unverified user
    const verifiedUserEmail = 'verified_login@example.com';
    const verifiedUsername = 'verified_login_user';

    const unverifiedUserEmail = 'unverified_login@example.com';
    const unverifiedUsername = 'unverified_login_user';

    await User.deleteMany({
        normalizedEmail: { $in: [verifiedUserEmail, unverifiedUserEmail] }
    });

    const verifiedUser = await User.create({
        username: verifiedUsername,
        normalizedUsername: verifiedUsername.toLowerCase(),
        email: verifiedUserEmail,
        normalizedEmail: verifiedUserEmail.toLowerCase(),
        passwordHash,
        emailVerified: true,
        emailVerifiedAt: new Date()
    });

    await User.create({
        username: unverifiedUsername,
        normalizedUsername: unverifiedUsername.toLowerCase(),
        email: unverifiedUserEmail,
        normalizedEmail: unverifiedUserEmail.toLowerCase(),
        passwordHash,
        emailVerified: false
    });

    let authToken = null;

    await t.test('1. Reject login with missing fields', async () => {
        const res = await apiRequest(app, {
            method: 'POST',
            path: '/api/auth/login',
            body: { username: '' }
        });
        assert.equal(res.status, 400);
        assert.equal(res.body.success, false);
    });

    await t.test('2. Reject login with non-existent username', async () => {
        const res = await apiRequest(app, {
            method: 'POST',
            path: '/api/auth/login',
            body: { username: 'non_existent_user_999', password: testPassword }
        });
        assert.equal(res.status, 401);
        assert.equal(res.body.success, false);
        assert.equal(res.body.message, 'Invalid email or password');
    });

    await t.test('3. Reject login with incorrect password', async () => {
        const res = await apiRequest(app, {
            method: 'POST',
            path: '/api/auth/login',
            body: { username: verifiedUsername, password: 'WrongPassword999' }
        });
        assert.equal(res.status, 401);
        assert.equal(res.body.success, false);
        assert.equal(res.body.message, 'Invalid email or password');
    });

    await t.test('4. Reject login for unverified account', async () => {
        const res = await apiRequest(app, {
            method: 'POST',
            path: '/api/auth/login',
            body: { username: unverifiedUsername, password: testPassword }
        });
        assert.equal(res.status, 403);
        assert.equal(res.body.success, false);
        assert.equal(res.body.requiresVerification, true);
        assert.match(res.body.message, /verify your email/i);
    });

    await t.test('5. Successful login with verified username returns JWT token', async () => {
        const res = await apiRequest(app, {
            method: 'POST',
            path: '/api/auth/login',
            body: { username: verifiedUsername, password: testPassword }
        });

        assert.equal(res.status, 200);
        assert.equal(res.body.success, true);
        assert.ok(res.body.data.token);
        assert.equal(res.body.data.user.username, verifiedUsername);
        assert.equal(res.body.data.user.emailVerified, true);
        assert.equal(res.body.data.user.passwordHash, undefined); // Never expose hash!

        authToken = res.body.data.token;
    });

    await t.test('6. Successful login with email address returns JWT token', async () => {
        const res = await apiRequest(app, {
            method: 'POST',
            path: '/api/auth/login',
            body: { identifier: verifiedUserEmail, password: testPassword }
        });

        assert.equal(res.status, 200);
        assert.equal(res.body.success, true);
        assert.ok(res.body.data.token);
    });

    await t.test('6b. Successful login with dotted email (e.g. tanujamohite.286@gmail.com) and stripped version', async () => {
        const dottedEmail = 'tanujamohite.286@gmail.com';
        const strippedEmail = 'tanujamohite286@gmail.com';
        await User.deleteMany({ email: { $in: [dottedEmail, strippedEmail] } });

        await User.create({
            username: 'Tanuja',
            email: dottedEmail,
            normalizedEmail: strippedEmail,
            passwordHash,
            emailVerified: true,
            emailVerifiedAt: new Date()
        });

        // 1. Can log in with the exact dotted email
        const res1 = await apiRequest(app, {
            method: 'POST',
            path: '/api/auth/login',
            body: { email: dottedEmail, password: testPassword }
        });
        assert.equal(res1.status, 200);
        assert.equal(res1.body.success, true);
        assert.ok(res1.body.data.token);

        // 2. Can also log in with the stripped version
        const res2 = await apiRequest(app, {
            method: 'POST',
            path: '/api/auth/login',
            body: { email: strippedEmail, password: testPassword }
        });
        assert.equal(res2.status, 200);
        assert.equal(res2.body.success, true);
        assert.ok(res2.body.data.token);

        await User.deleteMany({ email: { $in: [dottedEmail, strippedEmail] } });
    });

    await t.test('7. Access GET /api/auth/me with valid Bearer token', async () => {
        const res = await apiRequest(app, {
            method: 'GET',
            path: '/api/auth/me',
            headers: {
                Authorization: `Bearer ${authToken}`
            }
        });

        assert.equal(res.status, 200);
        assert.equal(res.body.success, true);
        assert.equal(res.body.data.id, verifiedUser._id.toString());
        assert.equal(res.body.data.username, verifiedUsername);
    });

    await t.test('8. Access GET /api/auth/me without token returns 401', async () => {
        const res = await apiRequest(app, {
            method: 'GET',
            path: '/api/auth/me'
        });

        assert.equal(res.status, 401);
        assert.equal(res.body.success, false);
        assert.match(res.body.message, /Authorization required/i);
    });

    await t.test('9. Access GET /api/auth/me with tampered token returns 401', async () => {
        const res = await apiRequest(app, {
            method: 'GET',
            path: '/api/auth/me',
            headers: {
                Authorization: 'Bearer invalid.tampered.token123'
            }
        });

        assert.equal(res.status, 401);
        assert.equal(res.body.success, false);
        assert.match(res.body.message, /Invalid authorization token/i);
    });

    await t.test('10. POST /api/auth/logout succeeds', async () => {
        const res = await apiRequest(app, {
            method: 'POST',
            path: '/api/auth/logout',
            headers: {
                Authorization: `Bearer ${authToken}`
            }
        });

        assert.equal(res.status, 200);
        assert.equal(res.body.success, true);
    });

    // Cleanup
    await User.deleteMany({
        normalizedEmail: { $in: [verifiedUserEmail, unverifiedUserEmail] }
    });

    await disconnectDatabase();
});
