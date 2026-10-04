const express = require('express');
const router = express.Router();
const personController = require('../controllers/personController');
const authenticate = require('../middleware/authMiddleware');

// All people routes require authentication
router.use(authenticate);

router.get('/', personController.getPeople);
router.get('/:id', personController.getPersonById);
router.get('/:id/history', personController.getPersonHistory);
router.post('/', personController.createPerson);
router.patch('/:id', personController.updatePerson);
router.delete('/:id', personController.deletePerson);

module.exports = router;
