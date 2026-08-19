package com.example.data.repository

import com.example.data.local.FinancialRecordEntity

/**
 * Pure, side-effect free parsing of raw device signals (SMS bodies / call log entries) into
 * ledger records. Kept separate from [OmniSyncRepository] so that the rules can be unit tested
 * on the JVM without Android dependencies.
 */
object FinancialMessageParser {

    private val DEBIT_KEYWORDS = listOf("debited", "debit", "spent", "sent to", "paid", "withdrawn")
    private val CREDIT_KEYWORDS = listOf("credited", "credit", "received", "deposited")
    private val OFFER_KEYWORDS = listOf("offer", "pre-approved", "preapproved", "cashback", "discount")

    // Matches "INR 1,234.56", "Rs.1234", "$1,200.00", "1234.56 INR"
    private val AMOUNT_REGEX = Regex(
        "(?:inr|rs|usd|eur|₹|\\$)\\.?\\s*([0-9][0-9,]*(?:\\.[0-9]{1,2})?)|([0-9][0-9,]*(?:\\.[0-9]{1,2})?)\\s*(?:inr|rs|usd|eur)"
    )

    /** Numeric amount contained in [text], or `null` when the text carries no parsable amount. */
    fun extractAmount(text: String): Double? {
        val match = AMOUNT_REGEX.find(text.lowercase()) ?: return null
        val raw = match.groupValues.drop(1).firstOrNull { it.isNotBlank() } ?: return null
        return raw.replace(",", "").toDoubleOrNull()
    }

    fun categorise(text: String): String {
        val cleaned = text.lowercase()
        return when {
            cleaned.contains("emi") || cleaned.contains("loan") -> "LOAN"
            cleaned.contains("sip") || cleaned.contains("mutual fund") || cleaned.contains("mutual") -> "SIP"
            cleaned.contains("credit card") || cleaned.contains("card ending") ||
                cleaned.contains("visa") || cleaned.contains("mastercard") -> "CREDIT_CARD"
            cleaned.contains("interest") -> "INTEREST"
            else -> "GENERAL"
        }
    }

    /**
     * Converts a transactional SMS into a ledger record. Returns `null` for non-financial messages
     * and for messages where no amount could be extracted (we never fabricate figures).
     */
    fun parseSms(address: String, body: String, timestamp: Long): FinancialRecordEntity? {
        val cleaned = body.lowercase()
        val amount = extractAmount(cleaned)
        val key = dedupeKey("sms", address, timestamp, body)

        return when {
            DEBIT_KEYWORDS.any { cleaned.contains(it) } && amount != null -> FinancialRecordEntity(
                timestamp = timestamp,
                title = "Debit · $address",
                type = "EXPENSE",
                amount = amount,
                category = categorise(cleaned),
                description = body.trim(),
                accountName = address,
                dedupeKey = key
            )

            CREDIT_KEYWORDS.any { cleaned.contains(it) } && amount != null -> FinancialRecordEntity(
                timestamp = timestamp,
                title = "Credit · $address",
                type = "EARNING",
                amount = amount,
                category = if (cleaned.contains("interest")) "INTEREST" else "GENERAL",
                description = body.trim(),
                accountName = address,
                dedupeKey = key
            )

            OFFER_KEYWORDS.any { cleaned.contains(it) } -> FinancialRecordEntity(
                timestamp = timestamp,
                title = "Offer · $address",
                type = "OFFER",
                amount = amount ?: 0.0,
                category = "OFFER",
                description = body.trim(),
                accountName = address,
                isActionable = true,
                dedupeKey = key
            )

            else -> null
        }
    }

    /** Flags calls from banking/finance helplines as actionable follow-ups. */
    fun parseCallLog(number: String, timestamp: Long): FinancialRecordEntity? {
        val cleanNo = number.replace(Regex("[^0-9+]"), "")
        val isFinancialDesk = cleanNo.startsWith("+1800") || cleanNo.startsWith("1800") ||
            cleanNo.startsWith("+91180") || cleanNo.contains("80092")
        if (!isFinancialDesk) return null

        return FinancialRecordEntity(
            timestamp = timestamp,
            title = "Financial desk call · $number",
            type = "OFFER",
            amount = 0.0,
            category = "OFFER",
            description = "Call from a banking/lending helpline. Possible EMI or refinancing follow-up.",
            accountName = number,
            isActionable = true,
            dedupeKey = dedupeKey("call", number, timestamp, "")
        )
    }

    fun dedupeKey(source: String, address: String, timestamp: Long, body: String): String =
        "$source|$address|$timestamp|${body.trim().hashCode()}"
}


