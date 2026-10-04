const express = require('express');
const router = express.Router();
const appController = require('../controllers/appController');

// Public route to check for latest version & release news
router.get('/version', appController.getLatestVersion);
router.post('/version', appController.publishVersion);

module.exports = router;
