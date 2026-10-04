const rateLimit = require('express-rate-limit');

/**
 * Limit registration requests to prevent mass spam account creation.
 */
const registrationLimiter = rateLimit({
    windowMs: 60 * 60 * 1000, // 1 hour
    max: 15, // Max 15 registrations per hour per IP
    standardHeaders: true,
    legacyHeaders: false,
    message: {
        success: false,
        message: 'Too many registration attempts from this IP address. Please try again after an hour.'
    }
});

/**
 * Limit OTP verification attempts to prevent brute-forcing 6-digit codes.
 */
const otpVerifyLimiter = rateLimit({
    windowMs: 15 * 60 * 1000, // 15 minutes
    max: 25, // 25 attempts per 15 minutes
    standardHeaders: true,
    legacyHeaders: false,
    message: {
        success: false,
        message: 'Too many verification attempts from this IP address. Please try again after 15 minutes.'
    }
});

/**
 * Limit OTP resend requests to prevent email provider abuse and inbox bombing.
 */
const otpResendLimiter = rateLimit({
    windowMs: 15 * 60 * 1000, // 15 minutes
    max: 6, // 6 resends per 15 minutes
    standardHeaders: true,
    legacyHeaders: false,
    message: {
        success: false,
        message: 'Too many OTP resend requests. Please wait before requesting another code.'
    }
});

/**
 * Limit login attempts.
 */
const loginLimiter = rateLimit({
    windowMs: 15 * 60 * 1000,
    max: 15,
    standardHeaders: true,
    legacyHeaders: false,
    message: {
        success: false,
        message: 'Too many login attempts. Please try again after 15 minutes.'
    }
});

/**
 * Limit password recovery attempts (forgot username / forgot password / reset password).
 */
const passwordRecoveryLimiter = rateLimit({
    windowMs: 15 * 60 * 1000,
    max: 10,
    standardHeaders: true,
    legacyHeaders: false,
    message: {
        success: false,
        message: 'Too many recovery requests from this IP address. Please try again after 15 minutes.'
    }
});

module.exports = {
    registrationLimiter,
    otpVerifyLimiter,
    otpResendLimiter,
    loginLimiter,
    passwordRecoveryLimiter
};
