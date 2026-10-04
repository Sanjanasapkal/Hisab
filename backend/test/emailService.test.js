const test = require('node:test');
const assert = require('node:assert/strict');
const emailService = require('../src/services/emailService');

// Save original environment variables to restore after each test
const originalEnv = { ...process.env };

function resetEnv() {
    process.env = { ...originalEnv };
    delete process.env.SMTP_HOST;
    delete process.env.SMTP_PORT;
    delete process.env.SMTP_SECURE;
    delete process.env.SMTP_USER;
    delete process.env.SMTP_PASS;
    delete process.env.EMAIL_FROM;
    delete process.env.RESEND_API_KEY;
    delete process.env.ENABLE_MOCK_EMAIL;
    delete process.env.ALLOW_DEV_MOCK_EMAIL;
    process.env.NODE_ENV = 'development';
    emailService._resetOverrides();
}

test('Email Service - Provider Configuration & Startup Validation', async (t) => {
    t.beforeEach(() => {
        resetEnv();
    });

    t.afterEach(() => {
        resetEnv();
    });

    await t.test('1. Production: Throws fatal error if ENABLE_MOCK_EMAIL is true', () => {
        process.env.NODE_ENV = 'production';
        process.env.ENABLE_MOCK_EMAIL = 'true';

        assert.throws(
            () => emailService.verifyEmailServiceConfig(),
            /email mock mode is strictly prohibited in production/i
        );
    });

    await t.test('2. Production: Throws fatal error if no email provider is configured', () => {
        process.env.NODE_ENV = 'production';
        process.env.ENABLE_MOCK_EMAIL = 'false';

        assert.throws(
            () => emailService.verifyEmailServiceConfig(),
            /no email provider is configured in production/i
        );
    });

    await t.test('3. Detects SMTP provider when valid SMTP variables are present', () => {
        process.env.SMTP_HOST = 'smtp.brevo.com';
        process.env.SMTP_PORT = '587';
        process.env.SMTP_USER = 'user@example.com';
        process.env.SMTP_PASS = 'secretpass';
        process.env.EMAIL_FROM = 'Hisab <support@hisab.app>';

        assert.equal(emailService.getProvider(), 'smtp');
        const config = emailService.verifyEmailServiceConfig();
        assert.equal(config.configured, true);
        assert.equal(config.provider, 'smtp');
        assert.equal(config.host, 'smtp.brevo.com');
        assert.equal(config.port, 587);
    });

    await t.test('4. Detects Resend provider when RESEND_API_KEY is present', () => {
        process.env.RESEND_API_KEY = 're_123456789abcdef';
        process.env.EMAIL_FROM = 'Hisab <onboarding@resend.dev>';

        assert.equal(emailService.getProvider(), 'resend');
        const config = emailService.verifyEmailServiceConfig();
        assert.equal(config.configured, true);
        assert.equal(config.provider, 'resend');
    });

    await t.test('5. Rejects invalid SMTP port configuration', () => {
        process.env.SMTP_HOST = 'smtp.example.com';
        process.env.SMTP_PORT = 'invalid_port';
        process.env.SMTP_USER = 'user@example.com';
        process.env.SMTP_PASS = 'secret';

        assert.throws(
            () => emailService.verifyEmailServiceConfig(),
            /Invalid SMTP_PORT/i
        );
    });

    await t.test('6. Reports incomplete SMTP configuration in development', () => {
        process.env.SMTP_HOST = 'smtp.example.com';
        // Missing SMTP_USER and SMTP_PASS

        const config = emailService.verifyEmailServiceConfig();
        assert.equal(config.configured, false);
        assert.match(config.error, /Missing required fields/i);
    });

    await t.test('7. Explicit mock mode is recognized in development', () => {
        process.env.ENABLE_MOCK_EMAIL = 'true';

        assert.equal(emailService.getProvider(), 'mock');
        const config = emailService.verifyEmailServiceConfig();
        assert.equal(config.configured, true);
        assert.equal(config.provider, 'mock');
    });

    await t.test('8. Rejects sending when no provider configured and mock mode is false', async () => {
        process.env.ENABLE_MOCK_EMAIL = 'false';

        await assert.rejects(
            async () => {
                await emailService.sendVerificationOtp('user@example.com', 'testuser', '123456');
            },
            /Email service is not configured/i
        );
    });
});

test('Email Service - Delivery Acceptance & Provider Error Handling', async (t) => {
    t.beforeEach(() => {
        resetEnv();
    });

    t.afterEach(() => {
        resetEnv();
    });

    await t.test('1. Explicit mock mode returns mock delivery result in dev', async () => {
        process.env.ENABLE_MOCK_EMAIL = 'true';

        const result = await emailService.sendVerificationOtp('mock_user@example.com', 'MockUser', '654321');
        assert.equal(result.delivered, true);
        assert.equal(result.mock, true);
        assert.equal(result.provider, 'mock');
        assert.ok(result.messageId);
    });

    await t.test('2. SMTP: Confirms delivery when provider accepts recipient', async () => {
        process.env.SMTP_HOST = 'smtp.test.com';
        process.env.SMTP_USER = 'smtp_user';
        process.env.SMTP_PASS = 'smtp_pass';

        // Mock transporter returning accepted recipient
        const mockTransporter = {
            sendMail: async (mailOptions) => {
                assert.equal(mailOptions.to, 'recipient@example.com');
                assert.match(mailOptions.subject, /123456/);
                assert.match(mailOptions.html, /123456/);
                return {
                    messageId: '<test-msg-id-123@smtp.test.com>',
                    accepted: ['recipient@example.com'],
                    rejected: []
                };
            }
        };

        emailService._setTransporter(mockTransporter);

        const result = await emailService.sendVerificationOtp('recipient@example.com', 'Alice', '123456');
        assert.equal(result.delivered, true);
        assert.equal(result.mock, false);
        assert.equal(result.provider, 'smtp');
        assert.equal(result.messageId, '<test-msg-id-123@smtp.test.com>');
        assert.deepEqual(result.accepted, ['recipient@example.com']);
    });

    await t.test('3. SMTP: Throws error and does not claim sent if provider rejects recipient', async () => {
        process.env.SMTP_HOST = 'smtp.test.com';
        process.env.SMTP_USER = 'smtp_user';
        process.env.SMTP_PASS = 'smtp_pass';

        // Mock transporter returning rejected recipient
        const mockTransporter = {
            sendMail: async () => {
                return {
                    messageId: '<rejected-id@smtp.test.com>',
                    accepted: [],
                    rejected: ['recipient@example.com']
                };
            }
        };

        emailService._setTransporter(mockTransporter);

        await assert.rejects(
            async () => {
                await emailService.sendVerificationOtp('recipient@example.com', 'Alice', '123456');
            },
            /SMTP server rejected delivery to recipient/i
        );
    });

    await t.test('4. SMTP: Throws error if provider accepts 0 recipients', async () => {
        process.env.SMTP_HOST = 'smtp.test.com';
        process.env.SMTP_USER = 'smtp_user';
        process.env.SMTP_PASS = 'smtp_pass';

        const mockTransporter = {
            sendMail: async () => {
                return {
                    messageId: '<none-accepted@smtp.test.com>',
                    accepted: [],
                    rejected: []
                };
            }
        };

        emailService._setTransporter(mockTransporter);

        await assert.rejects(
            async () => {
                await emailService.sendVerificationOtp('recipient@example.com', 'Alice', '123456');
            },
            /SMTP server did not accept recipient/i
        );
    });

    await t.test('5. SMTP: Propagates network/auth failure cleanly', async () => {
        process.env.SMTP_HOST = 'smtp.test.com';
        process.env.SMTP_USER = 'smtp_user';
        process.env.SMTP_PASS = 'smtp_pass';

        const mockTransporter = {
            sendMail: async () => {
                const err = new Error('Invalid login: 535 Authentication credentials invalid');
                err.code = 'EAUTH';
                throw err;
            }
        };

        emailService._setTransporter(mockTransporter);

        await assert.rejects(
            async () => {
                await emailService.sendVerificationOtp('recipient@example.com', 'Alice', '123456');
            },
            /Invalid login/i
        );
    });

    await t.test('6. Resend: Confirms delivery when Resend API returns ID', async () => {
        process.env.RESEND_API_KEY = 're_test_key_123';

        emailService._setFetch(async (url, options) => {
            assert.equal(url, 'https://api.resend.com/emails');
            assert.equal(options.method, 'POST');
            const body = JSON.parse(options.body);
            assert.deepEqual(body.to, ['resend_user@example.com']);
            assert.match(body.html, /888999/);

            return {
                ok: true,
                status: 200,
                statusText: 'OK',
                json: async () => ({ id: 'resend_message_id_999' })
            };
        });

        const result = await emailService.sendVerificationOtp('resend_user@example.com', 'Bob', '888999');
        assert.equal(result.delivered, true);
        assert.equal(result.mock, false);
        assert.equal(result.provider, 'resend');
        assert.equal(result.messageId, 'resend_message_id_999');
    });

    await t.test('7. Resend: Throws error when API returns rejection/validation error', async () => {
        process.env.RESEND_API_KEY = 're_test_key_123';

        emailService._setFetch(async () => {
            return {
                ok: false,
                status: 422,
                statusText: 'Unprocessable Entity',
                json: async () => ({
                    statusCode: 422,
                    name: 'validation_error',
                    message: 'The from email domain is unverified.'
                })
            };
        });

        await assert.rejects(
            async () => {
                await emailService.sendVerificationOtp('resend_user@example.com', 'Bob', '888999');
            },
            /Resend provider delivery failed.*The from email domain is unverified/i
        );
    });
});

test('Email Service - Templates & Input Sanitization', async (t) => {
    t.beforeEach(() => {
        resetEnv();
    });

    t.afterEach(() => {
        resetEnv();
    });

    await t.test('1. Validates 6-digit numeric OTP format strictly', async () => {
        process.env.ENABLE_MOCK_EMAIL = 'true';

        // Too short
        await assert.rejects(
            async () => emailService.sendVerificationOtp('test@example.com', 'user', '12345'),
            /Valid 6-digit OTP code is required/i
        );

        // Alpha characters
        await assert.rejects(
            async () => emailService.sendVerificationOtp('test@example.com', 'user', '12A456'),
            /Valid 6-digit OTP code is required/i
        );

        // Valid 6 digits
        const res = await emailService.sendVerificationOtp('test@example.com', 'user', '123456');
        assert.equal(res.delivered, true);
    });

    await t.test('2. Prevents HTML injection in OTP username template', async () => {
        let sentHtml = '';
        process.env.SMTP_HOST = 'smtp.test.com';
        process.env.SMTP_USER = 'user';
        process.env.SMTP_PASS = 'pass';

        emailService._setTransporter({
            sendMail: async (opts) => {
                sentHtml = opts.html;
                return { accepted: ['victim@example.com'], rejected: [], messageId: '123' };
            }
        });

        const maliciousUsername = '<script>alert("hacked")</script><b>Bold</b>';
        await emailService.sendVerificationOtp('victim@example.com', maliciousUsername, '654321');

        assert.ok(!sentHtml.includes('<script>alert("hacked")</script>'));
        assert.ok(sentHtml.includes('&lt;script&gt;alert(&quot;hacked&quot;)&lt;/script&gt;'));
    });

    await t.test('3. Username recovery sends username and handles escaping', async () => {
        let sentHtml = '';
        process.env.ENABLE_MOCK_EMAIL = 'true';

        const result = await emailService.sendUsernameRecovery('recover@example.com', 'cool_user_123');
        assert.equal(result.delivered, true);
    });

    await t.test('4. Password reset sends valid reset link', async () => {
        let sentHtml = '';
        process.env.SMTP_HOST = 'smtp.test.com';
        process.env.SMTP_USER = 'user';
        process.env.SMTP_PASS = 'pass';

        emailService._setTransporter({
            sendMail: async (opts) => {
                sentHtml = opts.html;
                return { accepted: ['reset@example.com'], rejected: [], messageId: '123' };
            }
        });

        const testUrl = 'http://localhost:5000/reset-password?token=abcdef1234567890';
        const result = await emailService.sendPasswordResetLink('reset@example.com', 'JohnDoe', testUrl);

        assert.equal(result.delivered, true);
        assert.ok(sentHtml.includes('abcdef1234567890'));
        assert.ok(sentHtml.includes('Password Reset Code'));
        assert.ok(sentHtml.includes('15 minutes'));
        assert.ok(sentHtml.includes('reset@example.com'));
        // Verify web URL is NOT included in the email
        assert.equal(sentHtml.includes(testUrl), false);
    });

    await t.test('5. Safe masking functions mask PII and secrets properly', () => {
        assert.equal(emailService.maskEmail('user@example.com'), 'u**r@example.com');
        assert.equal(emailService.maskEmail('ab@domain.com'), 'a*@domain.com');
        assert.equal(emailService.maskSecret('re_1234567890abcdef'), 're_***def');
        assert.equal(emailService.maskSecret('short'), '***');
    });
});
