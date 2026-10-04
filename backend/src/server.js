require('dotenv').config();
const app = require('./app');
const { connectDatabase, disconnectDatabase } = require('./config/database');
const { verifyEmailServiceConfig } = require('./services/emailService');

const PORT = process.env.PORT || 5000;
const HOST = '0.0.0.0'; // Listen on all network interfaces for emulator/device access

async function startServer() {
    console.log('====================================================');
    console.log('🚀 Starting Hisab Backend Server...');
    console.log(`📌 Environment: ${process.env.NODE_ENV || 'development'}`);
    console.log('====================================================');

    // Validate email configuration at startup
    verifyEmailServiceConfig();

    // Attempt MongoDB Atlas connection
    await connectDatabase();

    // Start HTTP server
    const server = app.listen(PORT, HOST, () => {
        console.log(`\n📡 Server listening on http://${HOST}:${PORT}`);
        console.log(`   - Localhost (Dev machine): http://localhost:${PORT}`);
        console.log(`   - Android Emulator URL:   http://10.0.2.2:${PORT}`);
        console.log(`   - Health check:           http://localhost:${PORT}/health\n`);
    });

    // Graceful Shutdown on termination signals
    const handleShutdown = async (signal) => {
        console.log(`\n🛑 Received ${signal}. Gracefully shutting down...`);
        server.close(async () => {
            console.log('HTTP server closed.');
            await disconnectDatabase();
            console.log('Exiting process.');
            process.exit(0);
        });

        // Force exit after 10s timeout if hung
        setTimeout(() => {
            console.error('Forcefully exiting after timeout.');
            process.exit(1);
        }, 10000);
    };

    process.on('SIGTERM', () => handleShutdown('SIGTERM'));
    process.on('SIGINT', () => handleShutdown('SIGINT'));
}

startServer().catch((err) => {
    console.error('Fatal error during startup:', err);
    process.exit(1);
});

