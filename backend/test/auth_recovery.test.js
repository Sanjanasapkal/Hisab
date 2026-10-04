const test = require('node:test');
const assert = require('node:assert/strict');
const http = require('node:http');
const crypto = require('crypto');
const bcrypt = require('bcryptjs');
require('dotenv').config();
const { connectDatabase, disconnectDatabase } = require('../src/config/database');
const app = require('../src/app');
const User = require('../src/models/User');
const PasswordResetToken = require('../src/models/PasswordResetToken');

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

test('Account Recovery - Forgot Username & Password Reset Suite', async (t) => {
    await connectDatabase();

    const originalPassword = 'OriginalPassword123';
    const originalHash = await bcrypt.hash(originalPassword, 10);

    const testEmail = 'recovery_user@example.com';
    const testUsername = 'recovery_user_hisab';

    await User.deleteMany({
        $or: [{ normalizedEmail: testEmail }, { normalizedUsername: testUsername }]
    });
    await PasswordResetToken.deleteMany({ email: testEmail });

    const user = await User.create({
        username: testUsername,
        normalizedUsername: testUsername.toLowerCase(),
        email: testEmail,
        normalizedEmail: testEmail.toLowerCase(),
        passwordHash: originalHash,
        emailVerified: true,
        emailVerifiedAt: new Date()
    });

    await t.test('1. POST /api/auth/forgot-username returns generic 200 without exposing username', async () => {
        const res = await apiRequest(app, {
            method: 'POST',
            path: '/api/auth/forgot-username',
            body: { email: testEmail }
        });

        assert.equal(res.status, 200);
        assert.equal(res.body.success, true);
        assert.match(res.body.message, /If an account is registered/i);
        // Security check: username must NOT be leaked in JSON body
        assert.equal(res.body.username, undefined);
    });

    await t.test('2. POST /api/auth/forgot-username with unknown email still returns generic 200', async () => {
        const res = await apiRequest(app, {
            method: 'POST',
            path: '/api/auth/forgot-username',
            body: { email: 'nonexistent_account@example.com' }
        });

        assert.equal(res.status, 200);
        assert.equal(res.body.success, true);
        assert.match(res.body.message, /If an account is registered/i);
    });

    let generatedRawToken = null;

    await t.test('3. POST /api/auth/forgot-password creates SHA-256 hashed reset token', async () => {
        const res = await apiRequest(app, {
            method: 'POST',
            path: '/api/auth/forgot-password',
            body: { email: testEmail }
        });

        assert.equal(res.status, 200);
        assert.equal(res.body.success, true);
        assert.match(res.body.message, /instructions to reset your password have been sent/i);

        // Verify that token exists in DB with SHA-256 hash (64 hex characters)
        const tokenDoc = await PasswordResetToken.findOne({ userId: user._id, used: false });
        assert.ok(tokenDoc);
        assert.equal(tokenDoc.tokenHash.length, 64);
        assert.equal(tokenDoc.used, false);
    });

    await t.test('3b. POST /api/auth/forgot-password works by submitting username directly', async () => {
        const res = await apiRequest(app, {
            method: 'POST',
            path: '/api/auth/forgot-password',
            body: { username: testUsername }
        });

        assert.equal(res.status, 200);
        assert.equal(res.body.success, true);
        assert.match(res.body.message, /instructions to reset your password have been sent/i);

        // Verify token was generated and tied to user's registered email
        const tokenDoc = await PasswordResetToken.findOne({ userId: user._id, used: false });
        assert.ok(tokenDoc);
        assert.equal(tokenDoc.email, testEmail);
    });

    await t.test('4. POST /api/auth/reset-password rejects invalid token', async () => {
        const res = await apiRequest(app, {
            method: 'POST',
            path: '/api/auth/reset-password',
            body: {
                token: 'completely_invalid_token_123',
                newPassword: 'BrandNewPassword123'
            }
        });

        assert.equal(res.status, 400);
        assert.equal(res.body.success, false);
        assert.match(res.body.message, /invalid or has already been used/i);
    });

    await t.test('5. POST /api/auth/reset-password updates password and invalidates token', async () => {
        // Create a specific known token for deterministic testing
        const rawToken = crypto.randomBytes(32).toString('hex');
        const tokenHash = crypto.createHash('sha256').update(rawToken).digest('hex');

        await PasswordResetToken.create({
            userId: user._id,
            email: user.normalizedEmail,
            tokenHash,
            expiresAt: new Date(Date.now() + 15 * 60 * 1000),
            used: false
        });

        const newPassword = 'BrandNewPassword123';
        const res = await apiRequest(app, {
            method: 'POST',
            path: '/api/auth/reset-password',
            body: {
                token: rawToken,
                newPassword: newPassword,
                confirmPassword: newPassword
            }
        });

        assert.equal(res.status, 200);
        assert.equal(res.body.success, true);
        assert.match(res.body.message, /Password has been reset successfully/i);

        // Verify token is now marked used in database
        const usedTokenDoc = await PasswordResetToken.findOne({ tokenHash });
        assert.equal(usedTokenDoc.used, true);

        // Verify user can log in with new password
        const loginRes = await apiRequest(app, {
            method: 'POST',
            path: '/api/auth/login',
            body: { username: testUsername, password: newPassword }
        });
        assert.equal(loginRes.status, 200);
        assert.ok(loginRes.body.data.token);

        // Verify old password fails
        const oldLoginRes = await apiRequest(app, {
            method: 'POST',
            path: '/api/auth/login',
            body: { username: testUsername, password: originalPassword }
        });
        assert.equal(oldLoginRes.status, 401);
    });

    await t.test('6. POST /api/auth/reset-password rejects reusing the same token', async () => {
        // Using the same token from step 5 must now be rejected
        const rawToken = crypto.randomBytes(32).toString('hex');
        const tokenHash = crypto.createHash('sha256').update(rawToken).digest('hex');

        await PasswordResetToken.create({
            userId: user._id,
            email: user.normalizedEmail,
            tokenHash,
            expiresAt: new Date(Date.now() + 15 * 60 * 1000),
            used: true // already used!
        });

        const res = await apiRequest(app, {
            method: 'POST',
            path: '/api/auth/reset-password',
            body: {
                token: rawToken,
                newPassword: 'AnotherPassword123'
            }
        });

        assert.equal(res.status, 400);
        assert.equal(res.body.success, false);
        assert.match(res.body.message, /invalid or has already been used/i);
    });

    await t.test('7. GET /reset-password serves HTML web reset page', async () => {
        const res = await apiRequest(app, {
            method: 'GET',
            path: '/reset-password?token=sample_token_for_test'
        });

        assert.equal(res.status, 200);
        assert.match(res.headers['content-type'], /text\/html/);
        assert.match(res.body, /Reset Password — Hisab/);
        assert.match(res.body, /Update Password/);
    });

    await t.test('8. POST /api/auth/reset-password validates 6-digit OTP matching email address', async () => {
        const sixDigitCode = '654321';
        const codeHash = crypto.createHash('sha256').update(sixDigitCode).digest('hex');

        await PasswordResetToken.create({
            userId: user._id,
            email: user.normalizedEmail,
            tokenHash: codeHash,
            expiresAt: new Date(Date.now() + 15 * 60 * 1000),
            used: false
        });

        // 8a. Rejects if email does not match
        const mismatchedRes = await apiRequest(app, {
            method: 'POST',
            path: '/api/auth/reset-password',
            body: {
                token: sixDigitCode,
                email: 'wrongemail@example.com',
                newPassword: 'CorrectPassword123'
            }
        });
        assert.equal(mismatchedRes.status, 400);
        assert.equal(mismatchedRes.body.success, false);

        // 8b. Accepts when email matches the 6-digit code
        const matchedRes = await apiRequest(app, {
            method: 'POST',
            path: '/api/auth/reset-password',
            body: {
                token: sixDigitCode,
                email: testEmail,
                newPassword: 'CorrectPassword123',
                confirmPassword: 'CorrectPassword123'
            }
        });
        assert.equal(matchedRes.status, 200);
        assert.equal(matchedRes.body.success, true);
        assert.match(matchedRes.body.message, /Password has been reset successfully/i);
    });

    // Cleanup
    await User.deleteMany({
        $or: [{ normalizedEmail: testEmail }, { normalizedUsername: testUsername }]
    });
    await PasswordResetToken.deleteMany({ email: testEmail });

    await disconnectDatabase();
});
