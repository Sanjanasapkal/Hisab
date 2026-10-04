const test = require('node:test');
const assert = require('node:assert/strict');
const emailService = require('../src/services/emailService');
const authController = require('../src/controllers/authController');
const User = require('../src/models/User');
const OtpChallenge = require('../src/models/OtpChallenge');
const PasswordResetToken = require('../src/models/PasswordResetToken');

// Helper to create mock Express response object
function createMockRes() {
    const res = {
        statusCode: 200,
        body: null,
        status(code) {
            this.statusCode = code;
            return this;
        },
        json(data) {
            this.body = data;
            return this;
        }
    };
    return res;
}

test('Authentication Flow - Email Provider Error Handling', async (t) => {
    // Save original model methods and emailService methods
    const originalSendOtp = emailService.sendVerificationOtp;
    const originalSendReset = emailService.sendPasswordResetLink;
    const originalFindOneUser = User.findOne;
    const originalCreateOtp = OtpChallenge.create;
    const originalDeleteOneOtp = OtpChallenge.deleteOne;
    const originalUpdateManyOtp = OtpChallenge.updateMany;
    const originalCreateReset = PasswordResetToken.create;
    const originalDeleteOneReset = PasswordResetToken.deleteOne;
    const originalUpdateManyReset = PasswordResetToken.updateMany;

    t.afterEach(() => {
        emailService.sendVerificationOtp = originalSendOtp;
        emailService.sendPasswordResetLink = originalSendReset;
        User.findOne = originalFindOneUser;
        OtpChallenge.create = originalCreateOtp;
        OtpChallenge.deleteOne = originalDeleteOneOtp;
        OtpChallenge.updateMany = originalUpdateManyOtp;
        PasswordResetToken.create = originalCreateReset;
        PasswordResetToken.deleteOne = originalDeleteOneReset;
        PasswordResetToken.updateMany = originalUpdateManyReset;
    });

    await t.test('1. register: Returns 502 and rolls back OtpChallenge when email delivery fails', async () => {
        // Mock user not existing
        User.findOne = async () => null;
        User.create = async (userData) => ({
            _id: 'mock_user_id_123',
            ...userData
        });

        // Mock OtpChallenge creation and deletion
        let createdChallengeId = null;
        let deletedChallengeId = null;

        OtpChallenge.updateMany = async () => ({ modifiedCount: 0 });
        OtpChallenge.create = async (challengeData) => {
            createdChallengeId = 'mock_challenge_id_456';
            return {
                _id: createdChallengeId,
                ...challengeData
            };
        };
        OtpChallenge.deleteOne = async (filter) => {
            deletedChallengeId = filter._id;
            return { deletedCount: 1 };
        };

        // Mock emailService throwing provider rejection
        emailService.sendVerificationOtp = async () => {
            throw new Error('SMTP server rejected delivery to recipient: u**r@example.com');
        };

        const req = {
            body: {
                username: 'alice_hisab',
                email: 'alice@example.com',
                password: 'Password123!',
                confirmPassword: 'Password123!'
            }
        };
        const res = createMockRes();
        let nextCalledWith = null;

        await authController.register(req, res, (err) => { nextCalledWith = err; });

        // Assert that email failure returns 502 with clear message and did not unhandled-crash
        assert.equal(res.statusCode, 502);
        assert.equal(res.body.success, false);
        assert.match(res.body.message, /Failed to deliver verification email/i);
        assert.equal(nextCalledWith, null);

        // Assert that the created OTP challenge was rolled back so the user can retry without cooldown
        assert.equal(deletedChallengeId, createdChallengeId);
    });

    await t.test('2. resendVerificationOtp: Returns 502 and rolls back challenge on email provider failure', async () => {
        User.findOne = async () => ({
            _id: 'mock_user_id_123',
            email: 'alice@example.com',
            normalizedEmail: 'alice@example.com',
            username: 'alice_hisab',
            emailVerified: false
        });

        let deletedChallengeId = null;
        OtpChallenge.findOne = () => ({
            sort: () => null // no prior cooldown blocking
        });
        OtpChallenge.updateMany = async () => ({ modifiedCount: 0 });
        OtpChallenge.create = async (challengeData) => ({
            _id: 'mock_challenge_to_delete_789',
            ...challengeData
        });
        OtpChallenge.deleteOne = async (filter) => {
            deletedChallengeId = filter._id;
            return { deletedCount: 1 };
        };

        // Mock emailService failure
        emailService.sendVerificationOtp = async () => {
            throw new Error('Resend provider delivery failed: 422 unverified domain');
        };

        const req = {
            body: {
                email: 'alice@example.com'
            }
        };
        const res = createMockRes();

        await authController.resendEmailOtp(req, res, () => {});

        assert.equal(res.statusCode, 502);
        assert.equal(res.body.success, false);
        assert.match(res.body.message, /Failed to deliver verification email/i);
        assert.equal(deletedChallengeId, 'mock_challenge_to_delete_789');
    });

    await t.test('3. forgotPassword: Deletes reset token if email delivery fails', async () => {
        User.findOne = async () => ({
            _id: 'mock_user_id_123',
            email: 'alice@example.com',
            normalizedEmail: 'alice@example.com',
            username: 'alice_hisab',
            emailVerified: true
        });

        let deletedTokenId = null;
        PasswordResetToken.updateMany = async () => ({ modifiedCount: 0 });
        PasswordResetToken.create = async (tokenData) => ({
            _id: 'mock_reset_token_id_999',
            ...tokenData
        });
        PasswordResetToken.deleteOne = async (filter) => {
            deletedTokenId = filter._id;
            return { deletedCount: 1 };
        };

        emailService.sendPasswordResetLink = async () => {
            throw new Error('SMTP connection timed out');
        };

        const req = {
            body: {
                email: 'alice@example.com'
            }
        };
        const res = createMockRes();

        await authController.forgotPassword(req, res, () => {});

        // Generic response returned for enumeration safety
        assert.equal(res.statusCode, 200);
        assert.equal(res.body.success, true);
        assert.match(res.body.message, /instructions to reset your password have been sent/i);

        // Undelivered token is safely deleted from DB
        assert.equal(deletedTokenId, 'mock_reset_token_id_999');
    });
});
