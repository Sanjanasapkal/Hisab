const validator = require('validator');

/**
 * Validates registration input payload.
 */
function validateRegistration(data = {}) {
    const errors = [];
    // User display name (optional, defaults to part before @ in email)
    const rawName = (data.name || data.username || '').trim();
    const email = (data.email || '').trim();
    const password = data.password || '';
    const confirmPassword = data.confirmPassword;

    // 1. Email validation
    if (!email) {
        errors.push('Email address is required');
    } else if (!validator.isEmail(email)) {
        errors.push('Please enter a valid email address');
    }

    // 2. Password validation
    if (!password) {
        errors.push('Password is required');
    } else if (password.length < 8) {
        errors.push('Password must be at least 8 characters long');
    } else if (password.length > 100) {
        errors.push('Password cannot exceed 100 characters');
    } else if (!/[a-zA-Z]/.test(password) || !/[0-9]/.test(password)) {
        errors.push('Password must contain at least one letter and one number');
    }

    // 3. Confirm Password (if present)
    if (confirmPassword !== undefined && confirmPassword !== null) {
        if (password !== confirmPassword) {
            errors.push('Password and confirm password do not match');
        }
    }

    const fallbackName = email ? email.split('@')[0] : 'User';
    const name = rawName || fallbackName;

    return {
        isValid: errors.length === 0,
        errors,
        normalized: {
            name,
            username: name,
            email: validator.normalizeEmail(email) || email.toLowerCase(),
            normalizedEmail: (validator.normalizeEmail(email) || email.toLowerCase()).trim(),
            password
        }
    };
}

/**
 * Validates OTP verification payload.
 */
function validateVerifyOtp(data = {}) {
    const errors = [];
    const email = (data.email || '').trim();
    const otp = (data.otp || '').trim();

    if (!email || !validator.isEmail(email)) {
        errors.push('Valid email address is required');
    }

    if (!otp) {
        errors.push('Verification code is required');
    } else if (!/^\d{6}$/.test(otp)) {
        errors.push('Verification code must be exactly 6 digits');
    }

    return {
        isValid: errors.length === 0,
        errors,
        normalized: {
            email: validator.normalizeEmail(email) || email.toLowerCase(),
            normalizedEmail: (validator.normalizeEmail(email) || email.toLowerCase()).trim(),
            otp
        }
    };
}

/**
 * Validates Resend OTP payload.
 */
function validateResendOtp(data = {}) {
    const errors = [];
    const email = (data.email || '').trim();

    if (!email || !validator.isEmail(email)) {
        errors.push('Valid email address is required');
    }

    return {
        isValid: errors.length === 0,
        errors,
        normalizedEmail: (validator.normalizeEmail(email) || email.toLowerCase()).trim()
    };
}

/**
 * Validates Login payload.
 */
function validateLogin(data = {}) {
    const errors = [];
    const identifier = (data.email || data.username || data.identifier || '').trim();
    const password = data.password || '';

    if (!identifier) {
        errors.push('Email address is required');
    }

    if (!password) {
        errors.push('Password is required');
    }

    return {
        isValid: errors.length === 0,
        errors,
        normalized: {
            identifier,
            normalizedIdentifier: identifier.toLowerCase(),
            password
        }
    };
}

/**
 * Validates Forgot Username payload.
 */
function validateForgotUsername(data = {}) {
    const errors = [];
    const email = (data.email || '').trim();

    if (!email) {
        errors.push('Email address is required');
    } else if (!validator.isEmail(email)) {
        errors.push('Please enter a valid email address');
    }

    return {
        isValid: errors.length === 0,
        errors,
        normalizedEmail: (validator.normalizeEmail(email) || email.toLowerCase()).trim()
    };
}

/**
 * Validates Forgot Password payload.
 * Accepts username, identifier, or email.
 */
function validateForgotPassword(data = {}) {
    const errors = [];
    const input = (data.identifier || data.username || data.email || '').trim();

    if (!input) {
        errors.push('Username or email is required');
    }

    const isEmail = validator.isEmail(input);
    const normalizedIdentifier = isEmail
        ? (validator.normalizeEmail(input) || input.toLowerCase()).trim()
        : input.toLowerCase().trim();

    return {
        isValid: errors.length === 0,
        errors,
        isEmail,
        normalizedIdentifier,
        normalizedEmail: isEmail ? normalizedIdentifier : null
    };
}

/**
 * Validates Reset Password payload.
 */
function validateResetPassword(data = {}) {
    const errors = [];
    const token = (data.token || '').trim();
    const email = (data.email || '').trim().toLowerCase();
    const newPassword = data.newPassword || '';
    const confirmPassword = data.confirmPassword;

    if (!token) {
        errors.push('Reset token is required');
    }

    if (!newPassword) {
        errors.push('New password is required');
    } else if (newPassword.length < 8) {
        errors.push('Password must be at least 8 characters long');
    } else if (newPassword.length > 100) {
        errors.push('Password cannot exceed 100 characters');
    } else if (!/[a-zA-Z]/.test(newPassword) || !/[0-9]/.test(newPassword)) {
        errors.push('Password must contain at least one letter and one number');
    }

    if (confirmPassword !== undefined && confirmPassword !== null) {
        if (newPassword !== confirmPassword) {
            errors.push('Password and confirm password do not match');
        }
    }

    return {
        isValid: errors.length === 0,
        errors,
        normalized: {
            token,
            email,
            newPassword
        }
    };
}

module.exports = {
    validateRegistration,
    validateVerifyOtp,
    validateResendOtp,
    validateLogin,
    validateForgotUsername,
    validateForgotPassword,
    validateResetPassword
};
