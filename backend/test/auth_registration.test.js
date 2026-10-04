const test = require('node:test');
const assert = require('node:assert/strict');
const http = require('node:http');
require('dotenv').config();
const { connectDatabase, disconnectDatabase } = require('../src/config/database');
const app = require('../src/app');
const User = require('../src/models/User');
const OtpChallenge = require('../src/models/OtpChallenge');

// HTTP request test helper
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

test('Authentication - Registration & OTP Verification Suite', async (t) => {
    // Connect to database before running tests
    const connected = await connectDatabase();
    assert.equal(connected, true, 'Database must be connected for auth tests');

    // Clean up test documents before test run
    const testEmail = 'testuser_hisab@example.com';
    const testUsername = 'testuser_hisab';

    await User.deleteMany({
        $or: [
            { normalizedEmail: testEmail },
            { normalizedUsername: testUsername }
        ]
    });
    await OtpChallenge.deleteMany({ email: testEmail });

    await t.test('1. Reject registration with missing or invalid fields', async () => {
        // Missing email
        let res = await apiRequest(app, {
            method: 'POST',
            path: '/api/auth/register',
            body: { password: 'Password123' }
        });
        assert.equal(res.status, 400);
        assert.equal(res.body.success, false);
        assert.match(res.body.message, /Email address is required/);

        // Invalid email
        res = await apiRequest(app, {
            method: 'POST',
            path: '/api/auth/register',
            body: { username: 'testuser', email: 'not-an-email', password: 'Password123' }
        });
        assert.equal(res.status, 400);
        assert.equal(res.body.success, false);
        assert.match(res.body.message, /valid email address/);

        // Weak password (no digits)
        res = await apiRequest(app, {
            method: 'POST',
            path: '/api/auth/register',
            body: { username: 'testuser', email: 'valid@example.com', password: 'onlyletters' }
        });
        assert.equal(res.status, 400);
        assert.equal(res.body.success, false);
        assert.match(res.body.message, /Password must contain at least one letter and one number/);

        // Password mismatch
        res = await apiRequest(app, {
            method: 'POST',
            path: '/api/auth/register',
            body: {
                username: 'testuser',
                email: 'valid@example.com',
                password: 'Password123',
                confirmPassword: 'Password456'
            }
        });
        assert.equal(res.status, 400);
        assert.equal(res.body.success, false);
        assert.match(res.body.message, /do not match/);
    });

    await t.test('2. Successful registration creates unverified user and OTP challenge', async () => {
        const res = await apiRequest(app, {
            method: 'POST',
            path: '/api/auth/register',
            body: {
                username: testUsername,
                email: testEmail,
                password: 'SecurePassword123',
                confirmPassword: 'SecurePassword123'
            }
        });

        assert.equal(res.status, 201);
        assert.equal(res.body.success, true);
        assert.ok(res.body.data.userId);

        // Verify user exists in database but is unverified
        const user = await User.findById(res.body.data.userId);
        assert.ok(user);
        assert.equal(user.emailVerified, false);
        assert.equal(user.emailVerifiedAt, null);

        // Verify OTP challenge was created with SHA-256 hash (never plaintext)
        const challenge = await OtpChallenge.findOne({ userId: user._id, purpose: 'EMAIL_VERIFICATION' });
        assert.ok(challenge);
        assert.equal(challenge.used, false);
        assert.equal(challenge.attempts, 0);
        assert.equal(challenge.otpHash.length, 64); // SHA-256 hex string length is 64 chars
    });

    await t.test('3. Reject incorrect OTP and track attempt count', async () => {
        const res = await apiRequest(app, {
            method: 'POST',
            path: '/api/auth/verify-email-otp',
            body: {
                email: testEmail,
                otp: '000000' // Deliberately incorrect
            }
        });

        assert.equal(res.status, 400);
        assert.equal(res.body.success, false);
        assert.match(res.body.message, /Invalid verification code/);
        assert.equal(res.body.remainingAttempts, 4);

        // Check in database that attempts incremented
        const challenge = await OtpChallenge.findOne({ email: testEmail, used: false });
        assert.equal(challenge.attempts, 1);
    });

    await t.test('4. Correct OTP verifies user account', async () => {
        // Retrieve challenge and verify using our known hash logic
        const crypto = require('crypto');
        const rawKnownOtp = '654321';
        const knownHash = crypto.createHash('sha256').update(rawKnownOtp).digest('hex');

        // Update the challenge with a known OTP hash for testing verification
        await OtpChallenge.findOneAndUpdate(
            { email: testEmail, used: false },
            { otpHash: knownHash }
        );

        const res = await apiRequest(app, {
            method: 'POST',
            path: '/api/auth/verify-email-otp',
            body: {
                email: testEmail,
                otp: rawKnownOtp
            }
        });

        assert.equal(res.status, 200);
        assert.equal(res.body.success, true);
        assert.match(res.body.message, /Email verified successfully/);

        // Verify in database that emailVerified is now true
        const verifiedUser = await User.findOne({ normalizedEmail: testEmail });
        assert.equal(verifiedUser.emailVerified, true);
        assert.ok(verifiedUser.emailVerifiedAt instanceof Date);

        // Challenge should now be marked as used
        const challenge = await OtpChallenge.findOne({ email: testEmail, otpHash: knownHash });
        assert.equal(challenge.used, true);
    });

    await t.test('5. Reject duplicate registration for already-verified email', async () => {
        const res = await apiRequest(app, {
            method: 'POST',
            path: '/api/auth/register',
            body: {
                username: 'another_user',
                email: testEmail, // same verified email
                password: 'Password999'
            }
        });

        assert.equal(res.status, 409);
        assert.equal(res.body.success, false);
        assert.match(res.body.message, /already exists/);
    });

    await t.test('6. Allows multiple users with the same display name as long as emails differ', async () => {
        const res = await apiRequest(app, {
            method: 'POST',
            path: '/api/auth/register',
            body: {
                username: testUsername, // same display name
                email: 'different_email@example.com',
                password: 'Password999'
            }
        });

        assert.equal(res.status, 201);
        assert.equal(res.body.success, true);

        await User.deleteMany({ normalizedEmail: 'different_email@example.com' });
        await OtpChallenge.deleteMany({ email: 'different_email@example.com' });
    });

    // Cleanup test data
    await User.deleteMany({
        $or: [
            { normalizedEmail: testEmail },
            { normalizedUsername: testUsername }
        ]
    });
    await OtpChallenge.deleteMany({ email: testEmail });

    // Disconnect cleanly
    await disconnectDatabase();
});
