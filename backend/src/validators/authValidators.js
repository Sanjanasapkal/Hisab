const validator = require('validator');

/**
 * Normalizes an email address without stripping dots from Gmail user handles.
 * Standard validator.normalizeEmail strips dots by default, which broke accounts
 * like tanujamohite.286@gmail.com when logging in.
 */
function cleanEmail(email) {
    if (!email || typeof email !== 'string') return '';
    const trimmed = email.trim().toLowerCase();
    const normalized = validator.normalizeEmail(trimmed, {
        gmail_remove_dots: false,
        all_lowercase: true,
        gmail_remove_subaddress: false
    });
    return normalized || trimmed;
}

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
    const preservedEmail = cleanEmail(email);
    const canonicalEmail = (validator.normalizeEmail(email, { gmail_remove_dots: true }) || preservedEmail).trim();

    return {
        isValid: errors.length === 0,
        errors,
        normalized: {
            name,
            username: name,
            email: preservedEmail,
            normalizedEmail: canonicalEmail,
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

    const normalizedEmailVal = cleanEmail(email);

    return {
        isValid: errors.length === 0,
        errors,
        normalized: {
            email: normalizedEmailVal,
            normalizedEmail: normalizedEmailVal,
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
        normalizedEmail: cleanEmail(email)
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
            normalizedIdentifier: identifier.includes('@') ? cleanEmail(identifier) : identifier.toLowerCase(),
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
        normalizedEmail: cleanEmail(email)
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
        ? cleanEmail(input)
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
            email: cleanEmail(email),
            newPassword
        }
    };
}

module.exports = {
    cleanEmail,
    validateRegistration,
    validateVerifyOtp,
    validateResendOtp,
    validateLogin,
    validateForgotUsername,
    validateForgotPassword,
    validateResetPassword
};
