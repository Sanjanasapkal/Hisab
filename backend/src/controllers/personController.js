const Person = require('../models/Person');
const AccountPeriod = require('../models/AccountPeriod');
const Transaction = require('../models/Transaction');
const Settlement = require('../models/Settlement');

/**
 * GET /api/people
 * 
 * Retrieves all active people for the authenticated user, calculates each person's
 * current balance, and returns the unified dashboard financial overview.
 */
async function getPeople(req, res, next) {
    try {
        const people = await Person.find({
            ownerUserId: req.userId,
            archived: false
        }).sort({ name: 1 });

        let totalOthersOweMe = 0;
        let totalIOweOthers = 0;

        const peopleWithBalances = await Promise.all(people.map(async (person) => {
            // Find active open period
            let openPeriod = await AccountPeriod.findOne({
                ownerUserId: req.userId,
                personId: person._id,
                status: 'open'
            });

            // Ensure an open period always exists
            if (!openPeriod) {
                openPeriod = await AccountPeriod.create({
                    ownerUserId: req.userId,
                    personId: person._id,
                    status: 'open',
                    openingBalancePaise: 0
                });
            }

            // Sum transactions for open period
            const transResult = await Transaction.aggregate([
                { $match: { ownerUserId: req.userId, periodId: openPeriod._id } },
                { $group: { _id: null, total: { $sum: '$amountPaise' } } }
            ]);

            const transactionSum = transResult.length > 0 ? transResult[0].total : 0;
            const currentBalancePaise = (openPeriod.openingBalancePaise || 0) + transactionSum;

            if (currentBalancePaise > 0) {
                totalOthersOweMe += currentBalancePaise;
            } else if (currentBalancePaise < 0) {
                totalIOweOthers += Math.abs(currentBalancePaise);
            }

            return {
                id: person._id,
                name: person.name,
                createdAt: person.createdAt,
                updatedAt: person.updatedAt,
                archived: person.archived,
                currentBalancePaise: currentBalancePaise,
                clientLocalId: person.clientLocalId
            };
        }));

        const netBalance = totalOthersOweMe - totalIOweOthers;

        return res.status(200).json({
            success: true,
            summary: {
                totalOthersOweMe,
                totalIOweOthers,
                netBalance,
                activePeopleCount: peopleWithBalances.length
            },
            data: peopleWithBalances
        });
    } catch (error) {
        next(error);
    }
}

/**
 * GET /api/people/:id
 * 
 * Retrieves details for a specific person, including active period transactions.
 */
async function getPersonById(req, res, next) {
    try {
        const person = await Person.findOne({
            _id: req.params.id,
            ownerUserId: req.userId,
            archived: false
        });

        if (!person) {
            return res.status(404).json({
                success: false,
                message: 'Person not found'
            });
        }

        // Get open period
        let openPeriod = await AccountPeriod.findOne({
            ownerUserId: req.userId,
            personId: person._id,
            status: 'open'
        });

        if (!openPeriod) {
            openPeriod = await AccountPeriod.create({
                ownerUserId: req.userId,
                personId: person._id,
                status: 'open',
                openingBalancePaise: 0
            });
        }

        // Get transactions for active open period
        const transactions = await Transaction.find({
            ownerUserId: req.userId,
            periodId: openPeriod._id
        }).sort({ transactionDate: -1, createdAt: -1 });

        const transactionSum = transactions.reduce((acc, t) => acc + t.amountPaise, 0);
        const currentBalancePaise = (openPeriod.openingBalancePaise || 0) + transactionSum;

        return res.status(200).json({
            success: true,
            data: {
                person: {
                    id: person._id,
                    name: person.name,
                    createdAt: person.createdAt,
                    updatedAt: person.updatedAt,
                    currentBalancePaise,
                    clientLocalId: person.clientLocalId
                },
                openPeriod: {
                    id: openPeriod._id,
                    startedAt: openPeriod.startedAt,
                    openingBalancePaise: openPeriod.openingBalancePaise
                },
                transactions
            }
        });
    } catch (error) {
        next(error);
    }
}

/**
 * POST /api/people
 * 
 * Adds a new person and creates their initial open accounting period.
 */
async function createPerson(req, res, next) {
    try {
        const name = (req.body.name || '').trim();
        const clientLocalId = req.body.clientLocalId || null;

        if (!name) {
            return res.status(400).json({
                success: false,
                message: 'Person name is required'
            });
        }

        // Case-insensitive duplicate check scoped strictly to ownerUserId
        const existing = await Person.findOne({
            ownerUserId: req.userId,
            name: new RegExp('^' + name.replace(/[.*+?^${}()|[\]\\]/g, '\\$&') + '$', 'i'),
            archived: false
        });

        if (existing) {
            return res.status(409).json({
                success: false,
                message: `A person with the name "${name}" already exists`
            });
        }

        const person = await Person.create({
            ownerUserId: req.userId,
            name,
            clientLocalId
        });

        // Initialize active accounting period
        const openPeriod = await AccountPeriod.create({
            ownerUserId: req.userId,
            personId: person._id,
            status: 'open',
            openingBalancePaise: 0
        });

        return res.status(201).json({
            success: true,
            message: 'Person added successfully',
            data: {
                id: person._id,
                name: person.name,
                createdAt: person.createdAt,
                updatedAt: person.updatedAt,
                currentBalancePaise: 0,
                openPeriodId: openPeriod._id,
                clientLocalId: person.clientLocalId
            }
        });
    } catch (error) {
        next(error);
    }
}

/**
 * PATCH /api/people/:id
 * 
 * Updates a person's display name.
 */
async function updatePerson(req, res, next) {
    try {
        const name = (req.body.name || '').trim();
        if (!name) {
            return res.status(400).json({
                success: false,
                message: 'Name cannot be empty'
            });
        }

        const person = await Person.findOne({
            _id: req.params.id,
            ownerUserId: req.userId,
            archived: false
        });

        if (!person) {
            return res.status(404).json({
                success: false,
                message: 'Person not found'
            });
        }

        // Check duplicate name for this user (excluding this person)
        const duplicate = await Person.findOne({
            ownerUserId: req.userId,
            _id: { $ne: person._id },
            name: new RegExp('^' + name.replace(/[.*+?^${}()|[\]\\]/g, '\\$&') + '$', 'i'),
            archived: false
        });

        if (duplicate) {
            return res.status(409).json({
                success: false,
                message: `Another person with the name "${name}" already exists`
            });
        }

        person.name = name;
        await person.save();

        return res.status(200).json({
            success: true,
            message: 'Person updated successfully',
            data: person
        });
    } catch (error) {
        next(error);
    }
}

/**
 * DELETE /api/people/:id
 * 
 * Safely archives a person and their active records.
 */
async function deletePerson(req, res, next) {
    try {
        const person = await Person.findOneAndUpdate(
            { _id: req.params.id, ownerUserId: req.userId, archived: false },
            { archived: true },
            { new: true }
        );

        if (!person) {
            return res.status(404).json({
                success: false,
                message: 'Person not found'
            });
        }

        return res.status(200).json({
            success: true,
            message: 'Person deleted successfully'
        });
    } catch (error) {
        next(error);
    }
}

/**
 * GET /api/people/:id/history
 * 
 * Retrieves full accounting history across all periods for a person.
 */
async function getPersonHistory(req, res, next) {
    try {
        const person = await Person.findOne({
            _id: req.params.id,
            ownerUserId: req.userId,
            archived: false
        });

        if (!person) {
            return res.status(404).json({
                success: false,
                message: 'Person not found'
            });
        }

        // Load all periods, newest first
        const periods = await AccountPeriod.find({
            ownerUserId: req.userId,
            personId: person._id
        }).sort({ startedAt: -1, createdAt: -1 });

        const historyItems = await Promise.all(periods.map(async (period) => {
            let settlement = null;
            if (period.status === 'closed') {
                settlement = await Settlement.findOne({
                    ownerUserId: req.userId,
                    periodId: period._id
                });
            }

            const transactions = await Transaction.find({
                ownerUserId: req.userId,
                periodId: period._id
            }).sort({ transactionDate: -1, createdAt: -1 });

            const txSum = transactions.reduce((acc, t) => acc + t.amountPaise, 0);
            const calculatedBalancePaise = period.closingBalancePaise !== null
                ? period.closingBalancePaise
                : (period.openingBalancePaise || 0) + txSum;

            return {
                period,
                settlement,
                transactions,
                calculatedBalancePaise
            };
        }));

        return res.status(200).json({
            success: true,
            data: {
                person,
                history: historyItems,
                transactions: historyItems.flatMap(h => h.transactions)
            }
        });
    } catch (error) {
        next(error);
    }
}

module.exports = {
    getPeople,
    getPersonById,
    createPerson,
    updatePerson,
    deletePerson,
    getPersonHistory
};
