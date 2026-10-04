const express = require('express');
const router = express.Router();
const transactionController = require('../controllers/transactionController');
const authenticate = require('../middleware/authMiddleware');

// All transaction routes require authentication
router.use(authenticate);

router.post('/', transactionController.addTransaction);
router.post('/settle', transactionController.settleHisab);
router.delete('/:id', transactionController.deleteTransaction);

module.exports = router;
