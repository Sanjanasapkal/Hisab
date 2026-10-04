const jwt = require('jsonwebtoken');

const JWT_SECRET = process.env.JWT_SECRET || 'hisab_fallback_secret_for_development_mode_only_12345';
const JWT_EXPIRES_IN = process.env.JWT_EXPIRES_IN || '7d';

/**
 * Generates a signed JWT token for an authenticated user.
 */
function generateToken(user) {
    const displayName = user.name || user.username || (user.email ? user.email.split('@')[0] : 'User');
    const payload = {
        userId: user._id.toString(),
        name: displayName,
        username: displayName,
        email: user.email
    };

    return jwt.sign(payload, JWT_SECRET, {
        expiresIn: JWT_EXPIRES_IN,
        algorithm: 'HS256'
    });
}

/**
 * Verifies and decodes a JWT token.
 * Throws JsonWebTokenError or TokenExpiredError if invalid.
 */
function verifyToken(token) {
    return jwt.verify(token, JWT_SECRET, {
        algorithms: ['HS256']
    });
}

module.exports = {
    generateToken,
    verifyToken
};
