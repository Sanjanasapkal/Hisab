const nodemailer = require('nodemailer');

/**
 * Global testing overrides (injectable for unit tests without network calls).
 */
let _transporterOverride = null;
let _fetchOverride = null;

/**
 * Helper to mask email addresses for safe logging (never leak full PII in logs).
 * Example: user@example.com -> u***r@example.com
 */
function maskEmail(email) {
    if (!email || typeof email !== 'string') return '***';
    const parts = email.split('@');
    if (parts.length !== 2) return '***';
    const [name, domain] = parts;
    if (name.length <= 2) {
        return `${name[0]}*@${domain}`;
    }
    return `${name[0]}${'*'.repeat(Math.min(name.length - 2, 4))}${name[name.length - 1]}@${domain}`;
}

/**
 * Helper to mask sensitive tokens/secrets for safe logging.
 * Example: re_123456789abc -> re_***abc
 */
function maskSecret(secret) {
    if (!secret || typeof secret !== 'string') return '***';
    if (secret.length <= 6) return '***';
    return `${secret.slice(0, 3)}***${secret.slice(-3)}`;
}

/**
 * Escapes HTML characters to prevent template injection attacks (XSS).
 */
function escapeHtml(str) {
    if (str === null || str === undefined) return '';
    return String(str)
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;')
        .replace(/'/g, '&#039;');
}

/**
 * Determines which email provider is currently configured based on environment variables.
 * Returns: 'resend' | 'smtp' | 'mock' | 'none'
 */
function getProvider() {
    // 1. Resend API Key takes priority if explicitly set
    if (process.env.RESEND_API_KEY && process.env.RESEND_API_KEY.trim() !== '') {
        return 'resend';
    }

    // 2. Standard SMTP configuration
    if (
        process.env.SMTP_HOST && process.env.SMTP_HOST.trim() !== '' &&
        process.env.SMTP_USER && process.env.SMTP_USER.trim() !== '' &&
        process.env.SMTP_PASS && process.env.SMTP_PASS.trim() !== ''
    ) {
        return 'smtp';
    }

    // 3. Explicit Mock Mode (ONLY permitted outside of production)
    const mockRequested = process.env.ENABLE_MOCK_EMAIL === 'true' || process.env.ALLOW_DEV_MOCK_EMAIL === 'true';
    if (mockRequested && process.env.NODE_ENV !== 'production') {
        return 'mock';
    }

    return 'none';
}

/**
 * Checks if real email service configuration (or explicitly enabled dev mock) is ready.
 */
function isConfigured() {
    return getProvider() !== 'none';
}

/**
 * Validates email configuration at startup and prints clear diagnostics.
 * In production: Throws a fatal Error if credentials are missing or mock mode is requested.
 * In development: Warns clearly if no provider is configured, or reports active provider.
 */
function verifyEmailServiceConfig() {
    const isProduction = process.env.NODE_ENV === 'production';
    const mockRequested = process.env.ENABLE_MOCK_EMAIL === 'true' || process.env.ALLOW_DEV_MOCK_EMAIL === 'true';

    // 1. Production security checks: Mock mode is strictly forbidden in production
    if (isProduction && mockRequested) {
        throw new Error(
            'FATAL: ENABLE_MOCK_EMAIL is set to "true", but email mock mode is strictly prohibited in production. ' +
            'You must configure a real email provider (SMTP or Resend) in your production environment.'
        );
    }

    const provider = getProvider();

    // 2. Production missing credentials check
    if (isProduction && provider === 'none') {
        throw new Error(
            'FATAL: No email provider is configured in production! ' +
            'Real email delivery is required for verification OTPs and password resets. ' +
            'Please configure SMTP credentials (SMTP_HOST, SMTP_PORT, SMTP_USER, SMTP_PASS, EMAIL_FROM) ' +
            'or a Resend API key (RESEND_API_KEY, EMAIL_FROM) in your environment variables.'
        );
    }

    // 3. Validate specific provider settings
    if (provider === 'resend') {
        const apiKey = process.env.RESEND_API_KEY.trim();
        const fromAddress = process.env.EMAIL_FROM || 'Hisab Support <onboarding@resend.dev>';
        console.log(`📧 [EMAIL SERVICE] Active Provider: Resend REST API (Key: ${maskSecret(apiKey)})`);
        console.log(`   Sender Address: ${fromAddress}`);
        return { configured: true, provider: 'resend', sender: fromAddress };
    }

    if (provider === 'smtp') {
        const host = process.env.SMTP_HOST.trim();
        const port = parseInt(process.env.SMTP_PORT || '587', 10);
        const user = process.env.SMTP_USER.trim();
        const fromAddress = process.env.EMAIL_FROM || `"Hisab Support" <${user}>`;

        if (isNaN(port) || port <= 0 || port > 65535) {
            throw new Error(`Invalid SMTP_PORT: "${process.env.SMTP_PORT}". Must be a valid port number (e.g., 587 or 465).`);
        }

        console.log(`📧 [EMAIL SERVICE] Active Provider: SMTP Relay (${host}:${port}, User: ${maskEmail(user)})`);
        console.log(`   Sender Address: ${fromAddress}`);
        return { configured: true, provider: 'smtp', host, port, sender: fromAddress };
    }

    if (provider === 'mock') {
        console.warn('⚠️  [EMAIL SERVICE] EXPLICIT MOCK MODE ENABLED (ENABLE_MOCK_EMAIL=true)');
        console.warn('   Emails and OTP codes will be printed to this dev terminal for local offline testing.');
        console.warn('   To send real emails instead, configure Brevo, Gmail, or Resend in backend/.env.');
        return { configured: true, provider: 'mock' };
    }

    // 4. Incomplete or missing configuration in development
    const hasPartialSmtp = Boolean(process.env.SMTP_HOST || process.env.SMTP_USER || process.env.SMTP_PASS);
    if (hasPartialSmtp) {
        const missing = [];
        if (!process.env.SMTP_HOST) missing.push('SMTP_HOST');
        if (!process.env.SMTP_USER) missing.push('SMTP_USER');
        if (!process.env.SMTP_PASS) missing.push('SMTP_PASS');

        const errorMsg = `Incomplete SMTP configuration. Missing required fields: ${missing.join(', ')}`;
        console.error(`❌ [EMAIL SERVICE CONFIG ERROR] ${errorMsg}`);
        console.error('   Please fill in all SMTP variables in backend/.env or set ENABLE_MOCK_EMAIL=true.');
        return { configured: false, provider: 'none', error: errorMsg };
    }

    console.warn('⚠️  [EMAIL SERVICE] NO EMAIL PROVIDER CONFIGURED');
    console.warn('   Real email delivery is currently inactive.');
    console.warn('   - To send real emails: Configure Brevo, Gmail, or Resend in backend/.env.');
    console.warn('   - To test locally with console mock: Set ENABLE_MOCK_EMAIL=true in backend/.env.');
    console.warn('   (Attempts to register or reset passwords will return a 502 configuration error until configured.)');

    return { configured: false, provider: 'none', error: 'No email provider credentials configured' };
}

/**
 * Creates or retrieves a Nodemailer SMTP transporter.
 */
function createTransporter(portOverride = null) {
    if (_transporterOverride) {
        return _transporterOverride;
    }

    const host = process.env.SMTP_HOST;
    let port = portOverride || parseInt(process.env.SMTP_PORT || '587', 10);

    // Render & cloud hosts block outbound port 587 on their free tier.
    // Brevo officially supports port 2525 as the cloud-safe alternative.
    const isCloudEnv = process.env.NODE_ENV === 'production' || Boolean(process.env.RENDER);
    if (!portOverride && host && (host.includes('brevo.com') || host.includes('sendinblue')) && port === 587 && isCloudEnv) {
        port = 2525;
    }

    const secure = process.env.SMTP_SECURE === 'true' || port === 465;

    return nodemailer.createTransport({
        host,
        port,
        secure,
        auth: {
            user: process.env.SMTP_USER,
            pass: process.env.SMTP_PASS
        },
        connectionTimeout: 15000, // 15s connection timeout
        greetingTimeout: 15000,   // 15s greeting timeout
        socketTimeout: 20000      // 20s socket timeout
    });
}

/**
 * Delivers email via Resend REST API (using native fetch, zero extra dependencies).
 */
async function sendViaResend({ to, subject, html, text }) {
    const apiKey = process.env.RESEND_API_KEY ? process.env.RESEND_API_KEY.trim() : '';
    if (!apiKey) {
        throw new Error('RESEND_API_KEY is missing or empty.');
    }

    const fromAddress = process.env.EMAIL_FROM || 'Hisab Support <onboarding@resend.dev>';
    const fetchFn = _fetchOverride || globalThis.fetch;

    const response = await fetchFn('https://api.resend.com/emails', {
        method: 'POST',
        headers: {
            'Authorization': `Bearer ${apiKey}`,
            'Content-Type': 'application/json'
        },
        body: JSON.stringify({
            from: fromAddress,
            to: Array.isArray(to) ? to : [to],
            subject,
            html,
            text
        })
    });

    let data;
    try {
        data = await response.json();
    } catch (parseErr) {
        data = null;
    }

    if (!response.ok || !data || !data.id || data.error) {
        const errorDetail = (data && (data.message || data.error?.message)) || `HTTP ${response.status} ${response.statusText}`;
        throw new Error(`Resend provider delivery failed: ${errorDetail}`);
    }

    console.log(`[EMAIL] Dispatched via Resend to ${maskEmail(to)} (MessageId: ${data.id})`);
    return {
        delivered: true,
        mock: false,
        provider: 'resend',
        messageId: data.id
    };
}

/**
 * Delivers email via Nodemailer SMTP and verifies delivery acceptance.
 */
async function sendViaSmtp({ to, subject, html, text }) {
    const fromAddress = process.env.EMAIL_FROM || `"Hisab Support" <${process.env.SMTP_USER}>`;
    const host = process.env.SMTP_HOST || '';
    const configuredPort = parseInt(process.env.SMTP_PORT || '587', 10);
    const isBrevo = host.includes('brevo.com') || host.includes('sendinblue');

    try {
        const transporter = createTransporter();
        const info = await transporter.sendMail({
            from: fromAddress,
            to,
            subject,
            text,
            html
        });

        // Verification of provider acceptance: Do not claim sent unless accepted
        if (Array.isArray(info.rejected) && info.rejected.length > 0 && info.rejected.includes(to)) {
            throw new Error(`SMTP server rejected delivery to recipient: ${maskEmail(to)}`);
        }

        if (Array.isArray(info.accepted) && info.accepted.length === 0) {
            throw new Error(`SMTP server did not accept recipient: ${maskEmail(to)}`);
        }

        console.log(`[EMAIL] Dispatched via SMTP to ${maskEmail(to)} (MessageId: ${info.messageId || 'unknown'})`);
        return {
            delivered: true,
            mock: false,
            provider: 'smtp',
            messageId: info.messageId,
            accepted: info.accepted
        };
    } catch (primaryErr) {
        // If port 587 was blocked by cloud host firewall (e.g. Render free tier), retry on cloud-safe port 2525
        if (!_transporterOverride && isBrevo && configuredPort !== 2525) {
            try {
                console.log(`[EMAIL] Primary SMTP connection failed (${primaryErr.message}). Retrying via cloud-safe port 2525...`);
                const fallbackTransporter = createTransporter(2525);
                const fallbackInfo = await fallbackTransporter.sendMail({
                    from: fromAddress,
                    to,
                    subject,
                    text,
                    html
                });

                console.log(`[EMAIL] Dispatched via SMTP port 2525 to ${maskEmail(to)} (MessageId: ${fallbackInfo.messageId || 'unknown'})`);
                return {
                    delivered: true,
                    mock: false,
                    provider: 'smtp',
                    messageId: fallbackInfo.messageId,
                    accepted: fallbackInfo.accepted
                };
            } catch (fallbackErr) {
                console.error(`[EMAIL] Fallback port 2525 also failed: ${fallbackErr.message}`);
            }
        }
        throw primaryErr;
    }
}

/**
 * Core unified email delivery dispatcher.
 */
async function deliverEmail({ to, subject, html, text, devMeta }) {
    const provider = getProvider();

    // 1. Explicit Mock Mode (strictly outside production)
    if (provider === 'mock') {
        if (process.env.NODE_ENV === 'production') {
            throw new Error('FATAL: Mock email delivery is strictly forbidden in production mode.');
        }

        console.log('\n=============================================================');
        console.log(`📬 [HISAB DEV EMAIL MOCK] ${devMeta?.title || 'Notification'}`);
        console.log(`   To:       ${to} ${devMeta?.username ? `(${devMeta.username})` : ''}`);
        if (devMeta?.otp) {
            console.log(`   OTP CODE: [ ${devMeta.otp} ]`);
            console.log('   Notice:   Expires in 5 minutes (single-use)');
        }
        if (devMeta?.resetUrl) {
            console.log(`   RESET URL: ${devMeta.resetUrl}`);
            console.log('   Notice:    Expires in 15 minutes (single-use)');
        }
        console.log('   Provider: Explicit Mock Mode (ENABLE_MOCK_EMAIL=true)');
        console.log('=============================================================\n');

        return {
            delivered: true,
            mock: true,
            provider: 'mock',
            messageId: `mock-${Date.now()}`
        };
    }

    // 2. Resend REST API
    if (provider === 'resend') {
        return await sendViaResend({ to, subject, html, text });
    }

    // 3. Nodemailer SMTP
    if (provider === 'smtp') {
        return await sendViaSmtp({ to, subject, html, text });
    }

    // 4. No provider configured
    throw new Error(
        'Email service is not configured. Real email provider credentials (SMTP or Resend) are required. ' +
        'In local development, you may set ENABLE_MOCK_EMAIL=true in backend/.env to use offline console mock.'
    );
}

// -----------------------------------------------------------------------------
// High-Level Service Methods
// -----------------------------------------------------------------------------

/**
 * Sends a 6-digit Email Verification OTP during user registration.
 */
async function sendVerificationOtp(email, username, otp) {
    if (!email || typeof email !== 'string') {
        throw new Error('Recipient email is required.');
    }
    if (!otp || !/^\d{6}$/.test(String(otp))) {
        throw new Error('Valid 6-digit OTP code is required.');
    }

    const safeUsername = escapeHtml(username || 'Hisab User');
    const safeOtp = escapeHtml(otp);

    const htmlContent = `
    <!DOCTYPE html>
    <html lang="en">
    <head>
        <meta charset="utf-8">
        <meta name="viewport" content="width=device-width, initial-scale=1.0">
        <title>Hisab Verification Code</title>
        <style>
            body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif; background-color: #F7F8FA; margin: 0; padding: 24px; color: #202522; }
            .container { max-width: 520px; margin: 0 auto; background: #ffffff; border-radius: 14px; overflow: hidden; border: 1px solid #E6E9EC; box-shadow: 0 4px 12px rgba(0,0,0,0.04); }
            .header { background: #183B35; padding: 28px 24px; text-align: center; }
            .header h1 { color: #ffffff; margin: 0; font-size: 24px; font-weight: 700; letter-spacing: -0.5px; }
            .header p { color: #A7D3C6; margin: 6px 0 0 0; font-size: 14px; }
            .content { padding: 32px 24px; }
            .greeting { font-size: 16px; margin-top: 0; margin-bottom: 12px; }
            .otp-box { background: #E1EDE9; border: 2px dashed #1F6B57; border-radius: 10px; padding: 18px; text-align: center; margin: 24px 0; }
            .otp-code { font-size: 38px; font-weight: 800; letter-spacing: 8px; color: #1F6B57; margin: 0; font-family: 'Courier New', Courier, monospace; }
            .badge-box { background: #F3F4F6; border-radius: 8px; padding: 14px 16px; margin: 20px 0; }
            .badge-item { font-size: 13px; color: #4B5563; line-height: 1.6; margin: 4px 0; }
            .footer { background: #F7F8FA; padding: 18px 24px; text-align: center; font-size: 12px; color: #9CA3AF; border-top: 1px solid #E6E9EC; }
        </style>
    </head>
    <body>
        <div class="container">
            <div class="header">
                <h1>Hisab</h1>
                <p>Personal Finance &amp; Digital Ledger System</p>
            </div>
            <div class="content">
                <h2 style="font-size: 18px; margin-top: 0; color: #183B35;">Verify Your Email Address</h2>
                <p class="greeting">Hello <strong>${safeUsername}</strong>,</p>
                <p style="color: #4B5563; line-height: 1.5;">Thank you for registering with Hisab. Please use the six-digit verification code below to activate your account:</p>
                
                <div class="otp-box">
                    <div class="otp-code">${safeOtp}</div>
                </div>
                
                <div class="badge-box">
                    <div class="badge-item">⏰ <strong>Expires in 5 minutes:</strong> This code can only be used once.</div>
                    <div class="badge-item">🔒 <strong>Keep it confidential:</strong> Hisab support will never ask for your code.</div>
                </div>
                
                <p style="font-size: 13px; color: #6B7280; line-height: 1.5; margin-top: 24px;">
                    If you did not attempt to register an account with Hisab, you can safely ignore this email.
                </p>
            </div>
            <div class="footer">
                &copy; ${new Date().getFullYear()} Hisab Project. Secure Ledger Management.
            </div>
        </div>
    </body>
    </html>
    `;

    const textContent = `Hello ${username || 'User'},\n\nYour Hisab verification code is: ${otp}\n\nThis code will expire in 5 minutes and can only be used once.\n\nNever share this code with anyone. Hisab support will never ask for your code.\n\nIf you did not request this, please ignore this email.\n\nHisab Support`;

    return await deliverEmail({
        to: email,
        subject: `${otp} is your Hisab verification code`,
        html: htmlContent,
        text: textContent,
        devMeta: {
            title: 'Email Verification OTP Delivery',
            username,
            otp
        }
    });
}

/**
 * Sends a Username Recovery Email to a verified account.
 */
async function sendUsernameRecovery(email, username) {
    if (!email || typeof email !== 'string') {
        throw new Error('Recipient email is required.');
    }
    if (!username) {
        throw new Error('Username is required.');
    }

    const safeUsername = escapeHtml(username);

    const htmlContent = `
    <!DOCTYPE html>
    <html lang="en">
    <head>
        <meta charset="utf-8">
        <meta name="viewport" content="width=device-width, initial-scale=1.0">
        <title>Hisab Username Recovery</title>
        <style>
            body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif; background-color: #F7F8FA; margin: 0; padding: 24px; color: #202522; }
            .container { max-width: 520px; margin: 0 auto; background: #ffffff; border-radius: 14px; overflow: hidden; border: 1px solid #E6E9EC; box-shadow: 0 4px 12px rgba(0,0,0,0.04); }
            .header { background: #183B35; padding: 28px 24px; text-align: center; }
            .header h1 { color: #ffffff; margin: 0; font-size: 24px; font-weight: 700; }
            .content { padding: 32px 24px; }
            .username-box { background: #E1EDE9; border: 1px solid #1F6B57; border-radius: 8px; padding: 18px; text-align: center; margin: 24px 0; }
            .username-text { font-size: 24px; font-weight: 700; color: #1F6B57; margin: 0; font-family: monospace; }
            .footer { background: #F7F8FA; padding: 18px 24px; text-align: center; font-size: 12px; color: #9CA3AF; border-top: 1px solid #E6E9EC; }
        </style>
    </head>
    <body>
        <div class="container">
            <div class="header">
                <h1>Hisab</h1>
            </div>
            <div class="content">
                <h2 style="font-size: 18px; margin-top: 0; color: #183B35;">Username Recovery</h2>
                <p>Hello,</p>
                <p style="color: #4B5563; line-height: 1.5;">We received a request to recover the username associated with your Hisab account.</p>
                
                <div class="username-box">
                    <p style="margin: 0 0 6px 0; font-size: 13px; color: #6B7280;">Your Hisab username is:</p>
                    <div class="username-text">${safeUsername}</div>
                </div>
                
                <p style="font-size: 13px; color: #6B7280; line-height: 1.5;">
                    You can use this username along with your password to log in to the Hisab Android application.
                </p>
                <p style="font-size: 13px; color: #6B7280; line-height: 1.5;">
                    If you did not request this information, you can safely ignore this email.
                </p>
                <p style="margin-top: 24px; color: #183B35; font-weight: 600;">Hisab Support Team</p>
            </div>
            <div class="footer">
                &copy; ${new Date().getFullYear()} Hisab Project. Secure Ledger Management.
            </div>
        </div>
    </body>
    </html>
    `;

    const textContent = `Hello,\n\nWe received a request to recover the username for your Hisab account.\n\nYour username is: ${username}\n\nIf you did not request this, you can safely ignore this email.\n\nHisab Support`;

    return await deliverEmail({
        to: email,
        subject: 'Your Hisab username recovery',
        html: htmlContent,
        text: textContent,
        devMeta: {
            title: 'Username Recovery Delivery',
            username
        }
    });
}

/**
 * Sends a Password Reset Link Email.
 */
async function sendPasswordResetLink(email, username, resetUrl, resetCode) {
    if (!email || typeof email !== 'string') {
        throw new Error('Recipient email is required.');
    }
    if (!resetUrl || typeof resetUrl !== 'string') {
        throw new Error('Reset URL is required.');
    }

    const code = resetCode || (resetUrl.match(/[?&]token=([^&]+)/)?.[1]) || '';
    const safeUsername = escapeHtml(username || 'Hisab User');
    const safeResetUrl = escapeHtml(resetUrl);
    const safeCode = escapeHtml(code);

    const htmlContent = `
    <!DOCTYPE html>
    <html lang="en">
    <head>
        <meta charset="utf-8">
        <meta name="viewport" content="width=device-width, initial-scale=1.0">
        <title>Reset Your Hisab Password</title>
        <style>
            body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif; background-color: #F7F8FA; margin: 0; padding: 24px; color: #202522; }
            .container { max-width: 520px; margin: 0 auto; background: #ffffff; border-radius: 14px; overflow: hidden; border: 1px solid #E6E9EC; box-shadow: 0 4px 12px rgba(0,0,0,0.04); }
            .header { background: #183B35; padding: 28px 24px; text-align: center; }
            .header h1 { color: #ffffff; margin: 0; font-size: 24px; font-weight: 700; }
            .header p { color: #A7D3C6; margin: 6px 0 0 0; font-size: 14px; }
            .content { padding: 32px 24px; }
            .otp-box { background: #E1EDE9; border: 2px dashed #1F6B57; border-radius: 10px; padding: 18px; text-align: center; margin: 20px 0; }
            .otp-code { font-size: 34px; font-weight: 800; letter-spacing: 6px; color: #1F6B57; margin: 0; font-family: 'Courier New', Courier, monospace; }
            .btn-reset { display: inline-block; background-color: #1F6B57; color: #ffffff !important; text-decoration: none; padding: 14px 28px; border-radius: 8px; font-weight: 700; font-size: 15px; margin: 16px 0; text-align: center; }
            .notice-box { background: #F3F4F6; border-radius: 8px; padding: 14px 16px; margin: 20px 0; font-size: 13px; color: #4B5563; line-height: 1.5; }
            .footer { background: #F7F8FA; padding: 18px 24px; text-align: center; font-size: 12px; color: #9CA3AF; border-top: 1px solid #E6E9EC; }
        </style>
    </head>
    <body>
        <div class="container">
            <div class="header">
                <h1>Hisab</h1>
                <p>Personal Finance &amp; Digital Ledger System</p>
            </div>
            <div class="content">
                <h2 style="font-size: 18px; margin-top: 0; color: #183B35;">Reset Password</h2>
                <p>Hello <strong>${safeUsername}</strong>,</p>
                <p style="color: #4B5563; line-height: 1.5;">We received a request to reset the password for your Hisab account.</p>
                
                ${safeCode ? `
                <p style="font-size: 14px; font-weight: 600; color: #183B35; margin-bottom: 6px;">Your 6-Digit Password Reset Code:</p>
                <div class="otp-box">
                    <div class="otp-code">${safeCode}</div>
                </div>
                <p style="font-size: 13px; color: #4B5563; line-height: 1.5; margin-top: -8px; margin-bottom: 24px;">
                    📱 <strong>In the Hisab mobile app:</strong> Enter this 6-digit code on the Reset Password screen along with your new password.
                </p>
                ` : ''}

                <div style="text-align: center; margin: 20px 0;">
                    <a href="${safeResetUrl}" class="btn-reset" target="_blank" rel="noopener noreferrer">Reset Password</a>
                </div>
                
                <p style="font-size: 12px; color: #6B7280; line-height: 1.5;">
                    Or open this link directly in your browser:<br>
                    <a href="${safeResetUrl}" style="color: #1F6B57; word-break: break-all;">${safeResetUrl}</a>
                </p>
                
                <div class="notice-box">
                    ⏰ <strong>This code/link expires in 15 minutes</strong> and can only be used once.<br>
                    🔒 If you did not request a password reset, you can safely ignore this email. Your existing password will remain unchanged.
                </div>
            </div>
            <div class="footer">
                &copy; ${new Date().getFullYear()} Hisab Support Team.
            </div>
        </div>
    </body>
    </html>
    `;

    const textContent = `Hello ${username || 'User'},\n\nWe received a request to reset the password for your Hisab account.\n\nYOUR PASSWORD RESET CODE: ${code}\n\nEnter this code in the Hisab mobile app to reset your password.\n\nAlternatively, open this link in your browser:\n${resetUrl}\n\nThis code expires in 15 minutes and can only be used once.\n\nIf you did not request this, please ignore this email.\n\nHisab Support`;

    return await deliverEmail({
        to: email,
        subject: `${code ? `${code} is your ` : ''}Hisab password reset code`,
        html: htmlContent,
        text: textContent,
        devMeta: {
            title: 'Password Reset Link Delivery',
            username,
            resetUrl,
            otp: code
        }
    });
}

// -----------------------------------------------------------------------------
// Testing Injection Helpers
// -----------------------------------------------------------------------------
function _setTransporter(transporter) {
    _transporterOverride = transporter;
}

function _setFetch(fetchFn) {
    _fetchOverride = fetchFn;
}

function _resetOverrides() {
    _transporterOverride = null;
    _fetchOverride = null;
}

module.exports = {
    verifyEmailServiceConfig,
    isConfigured,
    getProvider,
    sendVerificationOtp,
    sendUsernameRecovery,
    sendPasswordResetLink,
    escapeHtml,
    maskEmail,
    maskSecret,
    _setTransporter,
    _setFetch,
    _resetOverrides
};
