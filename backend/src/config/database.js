const mongoose = require('mongoose');

/**
 * Connects to MongoDB Atlas using Mongoose.
 * 
 * Provides clear console feedback and error guidance for beginners.
 */
async function connectDatabase() {
    const mongoUri = process.env.MONGODB_URI;

    if (!mongoUri || mongoUri.trim() === '') {
        console.warn('---------------------------------------------------------');
        console.warn('⚠️  MONGODB_URI is not set in backend/.env!');
        console.warn('   Please copy backend/.env.example to backend/.env and');
        console.warn('   paste your MongoDB Atlas connection string.');
        console.warn('---------------------------------------------------------');
        return false;
    }

    try {
        // Set Mongoose connection options
        mongoose.set('strictQuery', true);

        // Event listeners for connection monitoring
        mongoose.connection.on('connected', () => {
            console.log('✅ Successfully connected to MongoDB Atlas database:', mongoose.connection.name);
        });

        mongoose.connection.on('error', (err) => {
            console.error('❌ MongoDB Atlas connection error:', err.message);
        });

        mongoose.connection.on('disconnected', () => {
            console.warn('⚠️  MongoDB Atlas disconnected.');
        });

        await mongoose.connect(mongoUri, {
            serverSelectionTimeoutMS: 5000, // Timeout after 5s instead of hanging indefinitely
        });

        return true;
    } catch (error) {
        console.error('❌ Failed to establish connection to MongoDB Atlas:');
        console.error('   Error details:', error.message);
        console.error('   Troubleshooting tips:');
        console.error('   1. Verify your IP is added to the MongoDB Atlas Network Access allowlist.');
        console.error('   2. Verify your database username and password in MONGODB_URI.');
        console.error('   3. Ensure your internet connection is active.');
        return false;
    }
}

/**
 * Closes the database connection gracefully.
 */
async function disconnectDatabase() {
    try {
        await mongoose.disconnect();
        console.log('MongoDB connection closed.');
    } catch (err) {
        console.error('Error during database disconnect:', err.message);
    }
}

module.exports = {
    connectDatabase,
    disconnectDatabase,
    mongoose,
};
