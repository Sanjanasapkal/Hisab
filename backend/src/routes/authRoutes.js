const express = require('express');
const router = express.Router();
const authController = require('../controllers/authController');
const {
    registrationLimiter,
    otpVerifyLimiter,
    otpResendLimiter,
    loginLimiter,
    passwordRecoveryLimiter
} = require('../middleware/rateLimiter');
const authenticate = require('../middleware/authMiddleware');

// Registration endpoint
router.post('/register', registrationLimiter, authController.register);

// OTP Verification endpoint
router.post('/verify-email-otp', otpVerifyLimiter, authController.verifyEmailOtp);

// OTP Resend endpoint
router.post('/resend-email-otp', otpResendLimiter, authController.resendEmailOtp);

// Login endpoint
router.post('/login', loginLimiter, authController.login);

// Logout endpoint
router.post('/logout', authController.logout);

// Authenticated User Profile
router.get('/me', authenticate, authController.getMe);

// Account Recovery Endpoints
router.post('/forgot-username', passwordRecoveryLimiter, authController.forgotUsername);
router.post('/forgot-password', passwordRecoveryLimiter, authController.forgotPassword);
router.post('/reset-password', passwordRecoveryLimiter, authController.resetPassword);

module.exports = router;
