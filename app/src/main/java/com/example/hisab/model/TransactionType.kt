package com.example.hisab.model

/**
 * User-friendly transaction directions that map to the underlying signed convention.
 *
 * Underlying rule:
 * - Positive (+): The other person owes the app user.
 * - Negative (-): The app user owes the other person.
 */
enum class TransactionType(
    val title: String,
    val description: String,
    val signMultiplier: Int
) {
    THEY_OWE_ME(
        title = "They owe me (+)",
        description = "You paid or lent money to them. Increases what they owe you.",
        signMultiplier = +1
    ),
    I_OWE_THEM(
        title = "I owe them (-)",
        description = "They paid or lent money to you. Increases what you owe them.",
        signMultiplier = -1
    ),
    PAYMENT_RECEIVED(
        title = "Payment received (-)",
        description = "They returned money to you. Reduces what they owe you.",
        signMultiplier = -1
    ),
    PAYMENT_MADE(
        title = "Payment made (+)",
        description = "You returned money to them. Reduces what you owe them.",
        signMultiplier = +1
    )
}
