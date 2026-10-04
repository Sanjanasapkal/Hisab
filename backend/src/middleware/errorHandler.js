/**
 * Centralized Error Handling Middleware for Hisab API.
 * 
 * Ensures consistent JSON responses across all controllers and catches
 * unexpected exceptions cleanly without exposing sensitive stack traces in production.
 */
function errorHandler(err, req, res, next) {
    console.error(`[ERROR] ${req.method} ${req.originalUrl}:`, err);

    // Mongoose Validation Error (e.g. required field missing, regex mismatch)
    if (err.name === 'ValidationError') {
        const errors = Object.values(err.errors).map(val => val.message);
        return res.status(400).json({
            success: false,
            message: 'Validation failed',
            errors: errors
        });
    }

    // Mongoose Duplicate Key Error (e.g. duplicate username or email)
    if (err.code === 11000) {
        const field = Object.keys(err.keyValue)[0];
        return res.status(409).json({
            success: false,
            message: `A record with this ${field} already exists`,
            errors: [`Duplicate value for field '${field}'`]
        });
    }

    // JWT Authentication Errors
    if (err.name === 'JsonWebTokenError') {
        return res.status(401).json({
            success: false,
            message: 'Invalid authorization token'
        });
    }

    if (err.name === 'TokenExpiredError') {
        return res.status(401).json({
            success: false,
            message: 'Authorization token has expired. Please log in again.'
        });
    }

    // Default Server Error
    const statusCode = err.statusCode || 500;
    const message = err.message || 'Internal Server Error';

    res.status(statusCode).json({
        success: false,
        message: message,
        ...(process.env.NODE_ENV === 'development' && { stack: err.stack })
    });
}

module.exports = errorHandler;
