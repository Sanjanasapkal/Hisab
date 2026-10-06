const crypto = require('crypto');
const bcrypt = require('bcryptjs');
const validator = require('validator');
const User = require('../models/User');
const OtpChallenge = require('../models/OtpChallenge');
const PasswordResetToken = require('../models/PasswordResetToken');
const emailService = require('../services/emailService');
const {
    cleanEmail,
    validateRegistration,
    validateVerifyOtp,
    validateResendOtp,
    validateLogin,
    validateForgotUsername,
    validateForgotPassword,
    validateResetPassword
} = require('../validators/authValidators');
const { generateToken } = require('../services/tokenService');

/**
 * Returns candidate email variations (e.g., both with and without dots for Gmail)
 * to ensure users like tanujamohite.286@gmail.com match existing or new records seamlessly.
 */
function getCandidateEmails(email) {
    if (!email || typeof email !== 'string') return [];
    const clean = email.trim().toLowerCase();
    const stripped = validator.normalizeEmail(clean, { gmail_remove_dots: true }) || clean;
    const preserved = validator.normalizeEmail(clean, { gmail_remove_dots: false }) || clean;
    return Array.from(new Set([clean, stripped, preserved]));
}

/**
 * Generates a cryptographically secure 6-digit numeric OTP.
 */
function generateSecureOtp() {
    return crypto.randomInt(100000, 1000000).toString();
}

/**
 * Computes a SHA-256 hash of a string.
 */
function hashSha256(text) {
    return crypto.createHash('sha256').update(text).digest('hex');
}

/**
 * POST /api/auth/register
 * 
 * Registers a new user in a pending, unverified state and sends a 6-digit email OTP.
 */
async function register(req, res, next) {
    try {
        const validation = validateRegistration(req.body);
        if (!validation.isValid) {
            return res.status(400).json({
                success: false,
                message: validation.errors[0],
                errors: validation.errors
            });
        }

        const { name, email, normalizedEmail, password } = validation.normalized;

        // 1. Check if normalized email is already taken by a verified user
        const existingByEmail = await User.findOne({ normalizedEmail });
        if (existingByEmail && existingByEmail.emailVerified) {
            return res.status(409).json({
                success: false,
                message: 'An account with this email address already exists. Please log in.'
            });
        }

        // 2. Hash password with bcrypt (cost factor 12)
        const passwordHash = await bcrypt.hash(password, 12);

        let user;

        // 3. Handle pending/unverified account reuse or new creation safely
        if (existingByEmail && !existingByEmail.emailVerified) {
            // Update unverified user record with fresh registration details
            existingByEmail.name = name;
            existingByEmail.username = name;
            existingByEmail.passwordHash = passwordHash;
            user = await existingByEmail.save();
        } else {
            // Create a brand new pending user
            user = await User.create({
                name,
                username: name,
                email,
                normalizedEmail,
                passwordHash,
                emailVerified: false
            });
        }

        // 5. Invalidate any prior active OTP challenges for this user
        await OtpChallenge.updateMany(
            { userId: user._id, purpose: 'EMAIL_VERIFICATION', used: false },
            { used: true }
        );

        // 6. Generate secure 6-digit OTP and compute SHA-256 hash
        const rawOtp = generateSecureOtp();
        const otpHash = hashSha256(rawOtp);
        const now = Date.now();

        const challengeDoc = await OtpChallenge.create({
            userId: user._id,
            email: user.normalizedEmail,
            purpose: 'EMAIL_VERIFICATION',
            otpHash,
            attempts: 0,
            maxAttempts: 5,
            expiresAt: new Date(now + 5 * 60 * 1000),      // 5 minutes expiry
            resendAvailableAt: new Date(now + 60 * 1000), // 60 seconds cooldown
            used: false
        });

        // 7. Send OTP via configured email provider or dev mock
        try {
            await emailService.sendVerificationOtp(user.email, user.username, rawOtp);
        } catch (emailError) {
            // Clean up the created challenge so user is not penalized by a resend cooldown
            await OtpChallenge.deleteOne({ _id: challengeDoc._id });
            console.error('[AUTH] Failed to send verification OTP email:', emailError.message);
            return res.status(502).json({
                success: false,
                message: 'Failed to deliver verification email. Please check your email configuration or try again shortly.'
            });
        }

        return res.status(201).json({
            success: true,
            message: 'Registration initiated! Please enter the 6-digit code sent to your email to verify your account.',
            data: {
                userId: user._id,
                email: user.email,
                username: user.username,
                expiresInSeconds: 300,
                resendCooldownSeconds: 60
            }
        });
    } catch (error) {
        // Handle race conditions where simultaneous requests hit the unique index
        if (error.code === 11000) {
            return res.status(409).json({
                success: false,
                message: 'Username or email already in use. Please try again with different credentials.'
            });
        }
        next(error);
    }
}

/**
 * POST /api/auth/verify-email-otp
 * 
 * Validates the 6-digit OTP and marks the user's email as verified.
 */
async function verifyEmailOtp(req, res, next) {
    try {
        const validation = validateVerifyOtp(req.body);
        if (!validation.isValid) {
            return res.status(400).json({
                success: false,
                message: validation.errors[0],
                errors: validation.errors
            });
        }

        const { normalizedEmail, otp } = validation.normalized;

        // 1. Find user by normalized email or candidates (handles Gmail dot differences)
        const candidates = getCandidateEmails(normalizedEmail);
        const user = await User.findOne({
            $or: [
                { normalizedEmail: { $in: candidates } },
                { email: { $in: candidates } }
            ]
        });
        if (!user) {
            return res.status(400).json({
                success: false,
                message: 'No pending registration found for this email address.'
            });
        }

        if (user.emailVerified) {
            return res.status(400).json({
                success: false,
                message: 'Account is already verified. Please proceed to login.'
            });
        }

        // 2. Find latest active OTP challenge
        const challenge = await OtpChallenge.findOne({
            userId: user._id,
            purpose: 'EMAIL_VERIFICATION',
            used: false
        }).sort({ createdAt: -1 });

        if (!challenge) {
            return res.status(400).json({
                success: false,
                message: 'No active verification code found. Please request a new one.'
            });
        }

        // 3. Check expiration
        if (challenge.expiresAt < new Date()) {
            challenge.used = true;
            await challenge.save();
            return res.status(400).json({
                success: false,
                message: 'Verification code has expired. Please request a new code.'
            });
        }

        // 4. Check attempts limit
        if (challenge.attempts >= challenge.maxAttempts) {
            challenge.used = true;
            await challenge.save();
            return res.status(429).json({
                success: false,
                message: 'Too many incorrect attempts. This code is now invalid. Please request a new code.'
            });
        }

        // 5. Compare SHA-256 hashes
        const submittedHash = hashSha256(otp);
        if (submittedHash !== challenge.otpHash) {
            challenge.attempts += 1;
            await challenge.save();

            const remainingAttempts = Math.max(0, challenge.maxAttempts - challenge.attempts);
            const warningMsg = remainingAttempts > 0
                ? `Invalid verification code. ${remainingAttempts} attempt(s) remaining.`
                : 'Invalid verification code. Attempt limit reached. Please request a new code.';

            return res.status(400).json({
                success: false,
                message: warningMsg,
                remainingAttempts
            });
        }

        // 6. Success: Mark challenge as used and verify user
        challenge.used = true;
        await challenge.save();

        user.emailVerified = true;
        user.emailVerifiedAt = new Date();
        await user.save();

        return res.status(200).json({
            success: true,
            message: 'Email verified successfully! You can now log in to Hisab.'
        });
    } catch (error) {
        next(error);
    }
}

/**
 * POST /api/auth/resend-email-otp
 * 
 * Generates and resends a fresh 6-digit OTP, respecting cooldowns.
 */
async function resendEmailOtp(req, res, next) {
    try {
        const validation = validateResendOtp(req.body);
        if (!validation.isValid) {
            return res.status(400).json({
                success: false,
                message: validation.errors[0]
            });
        }

        const { normalizedEmail } = validation;

        const candidates = getCandidateEmails(normalizedEmail);
        const user = await User.findOne({
            $or: [
                { normalizedEmail: { $in: candidates } },
                { email: { $in: candidates } }
            ]
        });

        // Generic safe response to prevent account enumeration if user does not exist
        if (!user) {
            return res.status(200).json({
                success: true,
                message: 'If an unverified account exists with that email address, a new code has been sent.'
            });
        }

        if (user.emailVerified) {
            return res.status(400).json({
                success: false,
                message: 'This account is already verified. Please log in.'
            });
        }

        // Check cooldown from latest challenge
        const latestChallenge = await OtpChallenge.findOne({
            userId: user._id,
            purpose: 'EMAIL_VERIFICATION'
        }).sort({ createdAt: -1 });

        const now = Date.now();
        if (latestChallenge && latestChallenge.resendAvailableAt > new Date(now)) {
            const waitSeconds = Math.ceil((latestChallenge.resendAvailableAt.getTime() - now) / 1000);
            return res.status(429).json({
                success: false,
                message: `Please wait ${waitSeconds} seconds before requesting another code.`,
                retryAfterSeconds: waitSeconds
            });
        }

        // Invalidate prior challenges
        await OtpChallenge.updateMany(
            { userId: user._id, purpose: 'EMAIL_VERIFICATION', used: false },
            { used: true }
        );

        // Generate and record new OTP
        const rawOtp = generateSecureOtp();
        const otpHash = hashSha256(rawOtp);

        const challengeDoc = await OtpChallenge.create({
            userId: user._id,
            email: user.normalizedEmail,
            purpose: 'EMAIL_VERIFICATION',
            otpHash,
            attempts: 0,
            maxAttempts: 5,
            expiresAt: new Date(now + 5 * 60 * 1000),      // 5 minutes expiry
            resendAvailableAt: new Date(now + 60 * 1000), // 60 seconds cooldown
            used: false
        });

        // Send OTP
        try {
            await emailService.sendVerificationOtp(user.email, user.username, rawOtp);
        } catch (emailError) {
            await OtpChallenge.deleteOne({ _id: challengeDoc._id });
            console.error('[AUTH] Failed to resend verification OTP email:', emailError.message);
            return res.status(502).json({
                success: false,
                message: 'Failed to deliver verification email. Please try again shortly.'
            });
        }

        return res.status(200).json({
            success: true,
            message: 'A new verification code has been sent to your email.',
            data: {
                expiresInSeconds: 300,
                resendCooldownSeconds: 60
            }
        });
    } catch (error) {
        next(error);
    }
}

/**
 * POST /api/auth/login
 * 
 * Authenticates user credentials and issues a secure JWT token.
 */
async function login(req, res, next) {
    try {
        const validation = validateLogin(req.body);
        if (!validation.isValid) {
            return res.status(400).json({
                success: false,
                message: validation.errors[0],
                errors: validation.errors
            });
        }

        const { normalizedIdentifier, password } = validation.normalized;

        // Allow login by normalized email (both with and without dots for backwards compatibility) or username
        let query;
        if (normalizedIdentifier.includes('@')) {
            const candidates = getCandidateEmails(normalizedIdentifier);
            query = {
                $or: [
                    { email: { $in: candidates } },
                    { normalizedEmail: { $in: candidates } }
                ]
            };
        } else {
            query = {
                $or: [
                    { username: normalizedIdentifier },
                    { email: normalizedIdentifier },
                    { normalizedEmail: normalizedIdentifier }
                ]
            };
        }

        const user = await User.findOne(query);

        // Safe generic error to avoid account enumeration
        if (!user) {
            return res.status(401).json({
                success: false,
                message: 'Invalid email or password'
            });
        }

        // Verify password against stored bcrypt hash
        const isPasswordValid = await user.comparePassword(password);
        if (!isPasswordValid) {
            return res.status(401).json({
                success: false,
                message: 'Invalid email or password'
            });
        }

        // Reject accounts that have not completed email verification
        if (!user.emailVerified) {
            return res.status(403).json({
                success: false,
                message: 'Please verify your email address before logging in.',
                requiresVerification: true,
                unverifiedEmail: user.email
            });
        }

        // Generate signed JWT token
        const token = generateToken(user);
        const displayName = user.name || user.username || (user.email ? user.email.split('@')[0] : 'User');

        return res.status(200).json({
            success: true,
            message: 'Login successful',
            data: {
                token,
                expiresIn: process.env.JWT_EXPIRES_IN || '7d',
                user: {
                    id: user._id,
                    name: displayName,
                    username: displayName,
                    email: user.email,
                    emailVerified: user.emailVerified,
                    createdAt: user.createdAt
                }
            }
        });
    } catch (error) {
        next(error);
    }
}

/**
 * POST /api/auth/logout
 * 
 * Logs out the user (client discards stored session token).
 */
async function logout(req, res, next) {
    try {
        return res.status(200).json({
            success: true,
            message: 'Logged out successfully'
        });
    } catch (error) {
        next(error);
    }
}

/**
 * GET /api/auth/me
 * 
 * Returns the currently authenticated user's profile.
 */
async function getMe(req, res, next) {
    try {
        const displayName = req.user.name || req.user.username || (req.user.email ? req.user.email.split('@')[0] : 'User');
        return res.status(200).json({
            success: true,
            data: {
                id: req.user._id,
                name: displayName,
                username: displayName,
                email: req.user.email,
                emailVerified: req.user.emailVerified,
                createdAt: req.user.createdAt,
                updatedAt: req.user.updatedAt
            }
        });
    } catch (error) {
        next(error);
    }
}

/**
 * POST /api/auth/forgot-username
 * 
 * Sends the username to the account's verified email.
 * Always returns a generic response to prevent account enumeration.
 */
async function forgotUsername(req, res, next) {
    try {
        const validation = validateForgotUsername(req.body);
        if (!validation.isValid) {
            return res.status(400).json({
                success: false,
                message: validation.errors[0]
            });
        }

        const { normalizedEmail } = validation;

        const candidates = getCandidateEmails(normalizedEmail);
        const user = await User.findOne({
            $or: [
                { normalizedEmail: { $in: candidates } },
                { email: { $in: candidates } }
            ]
        });
        if (user && user.emailVerified) {
            try {
                await emailService.sendUsernameRecovery(user.email, user.username);
            } catch (emailError) {
                console.error('[AUTH] Failed to send username recovery email:', emailError.message);
            }
        }

        // Generic response to prevent account enumeration
        return res.status(200).json({
            success: true,
            message: 'If an account is registered with that email address, an email containing your username has been sent.'
        });
    } catch (error) {
        next(error);
    }
}

/**
 * POST /api/auth/forgot-password
 * 
 * Generates an expiring single-use reset token and emails a password reset link.
 * Returns a generic response to prevent account enumeration.
 */
async function forgotPassword(req, res, next) {
    try {
        const validation = validateForgotPassword(req.body);
        if (!validation.isValid) {
            return res.status(400).json({
                success: false,
                message: validation.errors[0]
            });
        }

        const { normalizedIdentifier, isEmail } = validation;

        const candidates = isEmail ? getCandidateEmails(normalizedIdentifier) : [normalizedIdentifier];
        const query = isEmail
            ? { $or: [{ normalizedEmail: { $in: candidates } }, { email: { $in: candidates } }] }
            : { $or: [{ username: normalizedIdentifier }, { email: normalizedIdentifier }] };

        const user = await User.findOne(query);
        if (user && user.emailVerified) {
            // Invalidate any previous unused tokens for this user
            await PasswordResetToken.updateMany(
                { userId: user._id, used: false },
                { used: true }
            );

            // Generate cryptographically secure 6-digit numeric reset OTP
            const rawToken = generateSecureOtp();
            const tokenHash = hashSha256(rawToken);

            const resetDoc = await PasswordResetToken.create({
                userId: user._id,
                email: user.normalizedEmail,
                tokenHash,
                expiresAt: new Date(Date.now() + 15 * 60 * 1000), // 15 minutes expiry
                used: false
            });

            // Dynamically determine base URL from request host (works across any Wi-Fi, hotspot, or tunnel)
            const reqHost = typeof req.get === 'function' ? req.get('host') : (req.headers && req.headers.host) || null;
            const reqProtocol = req.secure || (req.headers && req.headers['x-forwarded-proto'] === 'https') ? 'https' : 'http';
            const dynamicBaseUrl = reqHost ? `${reqProtocol}://${reqHost}` : null;
            const baseUrl = (process.env.APP_BASE_URL && process.env.APP_BASE_URL !== 'auto' && !process.env.APP_BASE_URL.includes('10.86.189.171'))
                ? process.env.APP_BASE_URL
                : (dynamicBaseUrl || 'http://localhost:5000');

            const resetUrl = `${baseUrl}/reset-password?token=${rawToken}`;

            try {
                await emailService.sendPasswordResetLink(user.email, user.username, resetUrl, rawToken);
            } catch (emailError) {
                // If email delivery fails, remove the token so an un-emailed token cannot be used
                await PasswordResetToken.deleteOne({ _id: resetDoc._id });
                console.error('[AUTH] Failed to send password reset email:', emailError.message);
            }
        }

        // Generic response to prevent account enumeration
        return res.status(200).json({
            success: true,
            message: 'If an account is registered with that email address, instructions to reset your password have been sent.'
        });
    } catch (error) {
        next(error);
    }
}

/**
 * POST /api/auth/reset-password
 * 
 * Validates the reset token and updates the user's password.
 */
async function resetPassword(req, res, next) {
    try {
        const validation = validateResetPassword(req.body);
        if (!validation.isValid) {
            return res.status(400).json({
                success: false,
                message: validation.errors[0],
                errors: validation.errors
            });
        }

        const { token, newPassword, email } = validation.normalized;
        const tokenHash = hashSha256(token);

        const query = {
            tokenHash,
            used: false
        };

        if (email) {
            query.email = email;
        }

        const resetDoc = await PasswordResetToken.findOne(query);

        if (!resetDoc) {
            return res.status(400).json({
                success: false,
                message: 'Password reset link is invalid or has already been used. Please request a new one.'
            });
        }

        if (resetDoc.expiresAt < new Date()) {
            resetDoc.used = true;
            await resetDoc.save();
            return res.status(400).json({
                success: false,
                message: 'Password reset link has expired. Please request a new one.'
            });
        }

        const user = await User.findById(resetDoc.userId);
        if (!user) {
            return res.status(400).json({
                success: false,
                message: 'User account not found.'
            });
        }

        // Hash new password with bcrypt
        const passwordHash = await bcrypt.hash(newPassword, 12);
        user.passwordHash = passwordHash;
        await user.save();

        // Invalidate token
        resetDoc.used = true;
        await resetDoc.save();

        // Invalidate any other active reset tokens for this user
        await PasswordResetToken.updateMany(
            { userId: user._id, used: false },
            { used: true }
        );

        return res.status(200).json({
            success: true,
            message: 'Password has been reset successfully! You can now log in with your new password.'
        });
    } catch (error) {
        next(error);
    }
}

module.exports = {
    register,
    verifyEmailOtp,
    resendEmailOtp,
    login,
    logout,
    getMe,
    forgotUsername,
    forgotPassword,
    resetPassword
};
