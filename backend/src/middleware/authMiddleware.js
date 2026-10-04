const { verifyToken } = require('../services/tokenService');
const User = require('../models/User');

/**
 * Authentication Middleware.
 * 
 * Verifies JWT token from Authorization header and scopes req.userId to the verified user.
 */
async function authenticate(req, res, next) {
    try {
        const authHeader = req.headers.authorization;

        if (!authHeader || !authHeader.startsWith('Bearer ')) {
            return res.status(401).json({
                success: false,
                message: 'Authorization required. Please provide a valid Bearer token.'
            });
        }

        const token = authHeader.split(' ')[1];
        if (!token) {
            return res.status(401).json({
                success: false,
                message: 'Token missing from authorization header.'
            });
        }

        // Verify token signature and expiration
        const decoded = verifyToken(token);

        // Fetch user from MongoDB to ensure user is active and email is verified
        const user = await User.findById(decoded.userId);
        if (!user) {
            return res.status(401).json({
                success: false,
                message: 'User account associated with this session no longer exists.'
            });
        }

        if (!user.emailVerified) {
            return res.status(403).json({
                success: false,
                message: 'Your email address has not been verified yet.'
            });
        }

        // Attach verified user and userId to request
        req.userId = user._id;
        req.user = user;

        next();
    } catch (error) {
        if (error.name === 'TokenExpiredError') {
            return res.status(401).json({
                success: false,
                message: 'Session has expired. Please log in again.'
            });
        }

        if (error.name === 'JsonWebTokenError') {
            return res.status(401).json({
                success: false,
                message: 'Invalid authorization token.'
            });
        }

        next(error);
    }
}

module.exports = authenticate;
