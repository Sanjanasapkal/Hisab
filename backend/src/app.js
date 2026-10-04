const express = require('express');
const cors = require('cors');
const rateLimit = require('express-rate-limit');
const mongoose = require('mongoose');
const errorHandler = require('./middleware/errorHandler');

const app = express();

// Trust proxy for rate limiting behind reverse proxies/emulators
app.set('trust proxy', 1);

// Enable Cross-Origin Resource Sharing (CORS)
app.use(cors());

// Parse incoming JSON request bodies (limit to 1MB to prevent large payload attacks)
app.use(express.json({ limit: '1mb' }));
app.use(express.urlencoded({ extended: true }));

// Global Rate Limiting: 300 requests per 15 minutes per IP
const globalLimiter = rateLimit({
    windowMs: 15 * 60 * 1000,
    max: 300,
    standardHeaders: true,
    legacyHeaders: false,
    message: {
        success: false,
        message: 'Too many requests from this IP, please try again after 15 minutes'
    }
});
app.use(globalLimiter);

// Simple request logger for development visibility
if (process.env.NODE_ENV !== 'test') {
    app.use((req, res, next) => {
        const start = Date.now();
        res.on('finish', () => {
            const duration = Date.now() - start;
            console.log(`[HTTP] ${req.method} ${req.originalUrl} -> ${res.statusCode} (${duration}ms)`);
        });
        next();
    });
}

// ---------------- Root & Health Check Endpoints ----------------

// Base API Info
app.get('/', (req, res) => {
    res.json({
        success: true,
        message: 'Hisab Backend API is running',
        version: '1.0.0',
        timestamp: new Date().toISOString()
    });
});

// Comprehensive Health Check endpoint
app.get('/health', (req, res) => {
    const dbState = mongoose.connection.readyState;
    // 0 = disconnected, 1 = connected, 2 = connecting, 3 = disconnecting
    const dbStatusMap = {
        0: 'disconnected',
        1: 'connected',
        2: 'connecting',
        3: 'disconnecting'
    };

    const isDbReady = dbState === 1;

    res.status(isDbReady ? 200 : 503).json({
        success: isDbReady,
        status: isDbReady ? 'healthy' : 'degraded',
        database: {
            state: dbStatusMap[dbState] || 'unknown',
            name: mongoose.connection.name || null,
            host: mongoose.connection.host || null
        },
        uptimeSeconds: Math.floor(process.uptime()),
        timestamp: new Date().toISOString()
    });
});

// Web Password Reset Page (for email reset links)
app.get('/reset-password', (req, res) => {
    const token = req.query.token || '';
    res.setHeader('Content-Type', 'text/html; charset=utf-8');
    res.send(`
    <!DOCTYPE html>
    <html lang="en">
    <head>
        <meta charset="utf-8">
        <meta name="viewport" content="width=device-width, initial-scale=1.0">
        <title>Reset Password — Hisab</title>
        <style>
            * { box-sizing: border-box; }
            body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; background: #F7F8FA; margin: 0; padding: 20px; display: flex; align-items: center; justify-content: center; min-height: 100vh; color: #202522; }
            .card { background: #FFFFFF; border-radius: 16px; border: 1px solid #E6E9EC; box-shadow: 0 4px 12px rgba(0,0,0,0.06); width: 100%; max-width: 440px; overflow: hidden; }
            .header { background: #183B35; padding: 24px; text-align: center; color: #FFFFFF; }
            .header h1 { margin: 0; font-size: 22px; font-weight: 700; }
            .header p { margin: 6px 0 0; font-size: 13px; color: #A7D3C6; }
            .body { padding: 28px 24px; }
            .form-group { margin-bottom: 20px; }
            label { display: block; font-size: 13px; font-weight: 600; color: #374151; margin-bottom: 6px; }
            input[type="password"] { width: 100%; padding: 12px 14px; border: 1px solid #D1D5DB; border-radius: 8px; font-size: 15px; outline: none; transition: border-color 0.2s; }
            input[type="password"]:focus { border-color: #1F6B57; box-shadow: 0 0 0 3px rgba(31, 107, 87, 0.15); }
            button { width: 100%; background: #1F6B57; color: #FFFFFF; border: none; padding: 14px; border-radius: 8px; font-size: 15px; font-weight: 600; cursor: pointer; transition: background-color 0.2s; }
            button:hover { background: #165042; }
            button:disabled { background: #9CA3AF; cursor: not-allowed; }
            .alert { padding: 12px 14px; border-radius: 8px; font-size: 14px; margin-bottom: 20px; display: none; line-height: 1.4; }
            .alert-error { background: #FDE8E8; color: #C44747; border: 1px solid #F8B4B4; }
            .alert-success { background: #E8F5EE; color: #218653; border: 1px solid #A3E0C1; }
            .hint { font-size: 12px; color: #6B7280; margin-top: 4px; }
        </style>
    </head>
    <body>
        <div class="card">
            <div class="header">
                <h1>Hisab</h1>
                <p>Personal Finance &amp; Ledger System</p>
            </div>
            <div class="body">
                <h2 style="font-size: 17px; margin: 0 0 16px 0; color: #202522;">Create New Password</h2>
                <div id="alertBox" class="alert"></div>

                <form id="resetForm" onsubmit="handleReset(event)">
                    <input type="hidden" id="token" value="${token}">
                    
                    <div class="form-group">
                        <label for="newPassword">New Password</label>
                        <input type="password" id="newPassword" placeholder="Minimum 8 characters" required minlength="8">
                        <div class="hint">Must contain at least one letter and one number</div>
                    </div>

                    <div class="form-group">
                        <label for="confirmPassword">Confirm Password</label>
                        <input type="password" id="confirmPassword" placeholder="Re-enter new password" required>
                    </div>

                    <button type="submit" id="submitBtn">Update Password</button>
                </form>
            </div>
        </div>

        <script>
            const token = document.getElementById('token').value;
            const alertBox = document.getElementById('alertBox');
            const submitBtn = document.getElementById('submitBtn');
            const form = document.getElementById('resetForm');

            if (!token) {
                showAlert('Missing or invalid reset token. Please request a new password reset link.', 'error');
                form.style.display = 'none';
            }

            function showAlert(msg, type) {
                alertBox.textContent = msg;
                alertBox.className = 'alert alert-' + type;
                alertBox.style.display = 'block';
            }

            async function handleReset(e) {
                e.preventDefault();
                const newPassword = document.getElementById('newPassword').value;
                const confirmPassword = document.getElementById('confirmPassword').value;

                if (newPassword !== confirmPassword) {
                    showAlert('Passwords do not match.', 'error');
                    return;
                }

                if (newPassword.length < 8 || !/[a-zA-Z]/.test(newPassword) || !/[0-9]/.test(newPassword)) {
                    showAlert('Password must be at least 8 characters and include both letters and numbers.', 'error');
                    return;
                }

                submitBtn.disabled = true;
                submitBtn.textContent = 'Updating...';

                try {
                    const res = await fetch('/api/auth/reset-password', {
                        method: 'POST',
                        headers: { 'Content-Type': 'application/json' },
                        body: JSON.stringify({ token, newPassword, confirmPassword })
                    });
                    const data = await res.json();

                    if (data.success) {
                        showAlert('Success! Your password has been reset. You can now open Hisab and log in with your new password.', 'success');
                        form.style.display = 'none';
                    } else {
                        showAlert(data.message || 'Failed to reset password. Please try again.', 'error');
                        submitBtn.disabled = false;
                        submitBtn.textContent = 'Update Password';
                    }
                } catch (err) {
                    showAlert('Network error. Please try again.', 'error');
                    submitBtn.disabled = false;
                    submitBtn.textContent = 'Update Password';
                }
            }
        </script>
    </body>
    </html>
    `);
});

// API Routes
const authRoutes = require('./routes/authRoutes');
const personRoutes = require('./routes/personRoutes');
const transactionRoutes = require('./routes/transactionRoutes');
const appRoutes = require('./routes/appRoutes');

app.use('/api/auth', authRoutes);
app.use('/api/people', personRoutes);
app.use('/api/transactions', transactionRoutes);
app.use('/api/app', appRoutes);

// 404 Route Handler
app.use((req, res) => {
    res.status(404).json({
        success: false,
        message: `Endpoint not found: ${req.method} ${req.originalUrl}`
    });
});

// Centralized Error Handling
app.use(errorHandler);

module.exports = app;
