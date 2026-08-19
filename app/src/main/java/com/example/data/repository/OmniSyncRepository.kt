package com.example.data.repository

import android.content.Context
import android.content.pm.PackageManager
import android.provider.CallLog
import android.provider.Telephony
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.data.api.GeminiClient
import com.example.data.api.SmtpClient
import com.example.data.local.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.UUID

private const val MAX_SMS_SCAN = 300
private const val MAX_CALL_SCAN = 100
private const val KEY_DEMO_SEEDED = "demo_records_seeded"

class OmniSyncRepository(private val context: Context, private val database: AppDatabase) {

    private val chatDao = database.chatDao()
    private val healthDao = database.healthDao()
    private val financialDao = database.financialDao()
    private val emailDao = database.emailDao()
    private val settingsDao = database.settingsDao()

    // --- Chat Flows ---
    val allThreads: Flow<List<ChatThreadEntity>> = chatDao.getAllThreads()
    
    fun getMessagesForThread(threadId: String): Flow<List<ChatMessageEntity>> {
        return chatDao.getMessagesForThread(threadId)
    }

    suspend fun createNewThread(title: String): ChatThreadEntity = withContext(Dispatchers.IO) {
        val thread = ChatThreadEntity(id = UUID.randomUUID().toString(), title = title)
        chatDao.insertThread(thread)
        // Insert greeting message from AI
        val initialMessage = ChatMessageEntity(
            threadId = thread.id,
            role = "model",
            content = "Hi! I am your OmniSync Collaborative AI. I have full knowledge of your wearable watch health parameters, parsed calls/SMS financial flows, and multiple synchronized emails. How can I assist you with your finances or wellness analytics today?"
        )
        chatDao.insertMessage(initialMessage)
        thread
    }

    suspend fun deleteThread(threadId: String) = withContext(Dispatchers.IO) {
        chatDao.deleteThreadWithMessages(threadId)
    }

    /** Ensures at least one thread exists; returns the thread that should be shown. */
    suspend fun ensureThreadExists(defaultTitle: String): ChatThreadEntity? = withContext(Dispatchers.IO) {
        if (chatDao.countThreads() == 0) createNewThread(defaultTitle) else null
    }

    suspend fun sendChatMessage(threadId: String, userText: String) = withContext(Dispatchers.IO) {
        val userMsg = ChatMessageEntity(threadId = threadId, role = "user", content = userText)
        chatDao.insertMessage(userMsg)

        // Gather Context / Knowledge Base
        val healthContext = gatherHealthContext()
        val financialContext = gatherFinancialContext()
        val emailContext = gatherEmailContext()

        val systemPrompt = """
            You are OmniSync AI, a highly sophisticated collaborative dashboard intelligence assistant.
            You have access to the user's unified personal database loaded into your knowledge base below.
            Use this data to answer any queries intelligently, relate health patterns to finance, 
            provide actionable tips, estimate loans/interests, track SIP progress and review daily email briefs.
            
            ========= USER DATA INBOX =========
            [WEARABLE HEALTH METRICS]
            $healthContext
            
            [FINANCIAL TRANSACTIONS & LOANS]
            $financialContext
            
            [SYNCED EMAILS HIGHLIGHTS]
            $emailContext
            ====================================
            
            Refer to the data directly to provide authentic personal analytics. Always keep suggestions practical and professional.
        """.trimIndent()

        // Fetch thread history (limit to last 15 messages for token thrift).
        // The message just inserted is dropped here because it is sent as the live `prompt`.
        val historyList = chatDao.getMessagesForThreadDirect(threadId)
        val historyPairs = historyList
            .dropLast(1)
            .takeLast(15)
            .map { it.role to it.content }

        val aiBaseUrl = getSettingValue("ai_base_url")
        val aiApiKey = getSettingValue("ai_api_key")
        val aiModel = getSettingValue("ai_model")

        val aiResponse = GeminiClient.generateContent(
            prompt = userText,
            systemInstruction = systemPrompt,
            chatHistory = historyPairs,
            customBaseUrl = aiBaseUrl,
            customApiKey = aiApiKey,
            customModel = aiModel
        )

        val aiMsg = ChatMessageEntity(threadId = threadId, role = "model", content = aiResponse)
        chatDao.insertMessage(aiMsg)
    }

    // --- Health Metrics API & Bluetooth Sync ---
    val allHealthMetrics: Flow<List<HealthMetricEntity>> = healthDao.getAllMetrics()
    val latestHealthMetric: Flow<HealthMetricEntity?> = healthDao.getLatestMetric()

    /** Outcome of a wearable sync so the UI can explain what actually happened. */
    data class HealthSyncResult(val success: Boolean, val message: String)

    /** Health Connect read permissions the app needs. */
    val healthConnectPermissions: Set<String> = setOf(
        HealthPermission.getReadPermission(StepsRecord::class),
        HealthPermission.getReadPermission(HeartRateRecord::class),
        HealthPermission.getReadPermission(SleepSessionRecord::class)
    )

    suspend fun syncBluetoothWatchMetrics(): HealthSyncResult = withContext(Dispatchers.IO) {
        when (HealthConnectClient.getSdkStatus(context)) {
            HealthConnectClient.SDK_UNAVAILABLE ->
                return@withContext seedDemoHealth("Health Connect is not supported on this device. Showing sample vitals.")
            HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED ->
                return@withContext seedDemoHealth("Please install/update the Health Connect app, then sync again.")
        }

        try {
            val client = HealthConnectClient.getOrCreate(context)
            val granted = client.permissionController.getGrantedPermissions()
            if (!granted.containsAll(healthConnectPermissions)) {
                return@withContext HealthSyncResult(
                    false,
                    "Health Connect permissions were not granted. Tap 'Grant health access' to enable real vitals."
                )
            }

            val endTime = Instant.now()
            val startTime = endTime.minus(24, ChronoUnit.HOURS)
            val range = TimeRangeFilter.between(startTime, endTime)

            val totalSteps = client.readRecords(ReadRecordsRequest(StepsRecord::class, range))
                .records.sumOf { it.count }.toInt()

            val hrSamples = client.readRecords(ReadRecordsRequest(HeartRateRecord::class, range))
                .records.flatMap { it.samples }
            val avgHr = if (hrSamples.isNotEmpty()) {
                (hrSamples.sumOf { it.beatsPerMinute } / hrSamples.size).toInt()
            } else 0

            val totalSleepMins = client.readRecords(ReadRecordsRequest(SleepSessionRecord::class, range))
                .records.sumOf { ChronoUnit.MINUTES.between(it.startTime, it.endTime) }.toInt()

            if (totalSteps == 0 && avgHr == 0 && totalSleepMins == 0) {
                return@withContext HealthSyncResult(
                    false,
                    "Health Connect returned no records for the last 24 hours. Make sure your watch app writes to Health Connect."
                )
            }

            healthDao.insertMetric(
                HealthMetricEntity(
                    heartRate = avgHr,
                    sleepMinutes = totalSleepMins,
                    // Derived from sleep duration against an 8h target — no invented numbers.
                    sleepScore = ((totalSleepMins / 480.0) * 100).toInt().coerceIn(0, 100),
                    steps = totalSteps,
                    calories = (totalSteps * 0.04).toInt(),
                    rxtype = "Health Connect"
                )
            )
            HealthSyncResult(true, "Synced $totalSteps steps and $totalSleepMins min of sleep from Health Connect.")
        } catch (e: Exception) {
            Log.e("HealthConnect", "Error reading Health Connect", e)
            HealthSyncResult(false, "Health Connect read failed: ${e.localizedMessage ?: "unknown error"}")
        }
    }

    /**
     * Inserts a clearly-labelled sample reading so the dashboard is demonstrable on devices
     * without Health Connect. Only ever inserted once.
     */
    private suspend fun seedDemoHealth(reason: String): HealthSyncResult {
        if (healthDao.getAllMetricsDirect().isEmpty()) {
            healthDao.insertMetric(
                HealthMetricEntity(
                    heartRate = 72,
                    sleepMinutes = 431,
                    sleepScore = 90,
                    steps = 8420,
                    calories = 337,
                    rxtype = "Sample data (not from a device)"
                )
            )
        }
        return HealthSyncResult(false, reason)
    }

    suspend fun clearHealthData() = withContext(Dispatchers.IO) {
        healthDao.clearAllMetrics()
    }

    // --- Financial Records Core (Messages & Calls parser) ---
    val allFinancialRecords: Flow<List<FinancialRecordEntity>> = financialDao.getAllRecords()

    suspend fun runSmsAndCallFinanceParse(): Int = withContext(Dispatchers.IO) {
        var count = 0

        if (hasPermission(android.Manifest.permission.READ_SMS)) {
            try {
                context.contentResolver.query(
                    Telephony.Sms.CONTENT_URI,
                    arrayOf(Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE),
                    null, null, "${Telephony.Sms.DATE} DESC"
                )?.use { cursor ->
                    val addressIdx = cursor.getColumnIndex(Telephony.Sms.ADDRESS)
                    val bodyIdx = cursor.getColumnIndex(Telephony.Sms.BODY)
                    val dateIdx = cursor.getColumnIndex(Telephony.Sms.DATE)
                    if (addressIdx >= 0 && bodyIdx >= 0 && dateIdx >= 0) {
                        var scanned = 0
                        while (cursor.moveToNext() && scanned < MAX_SMS_SCAN) {
                            scanned++
                            val address = cursor.getString(addressIdx) ?: continue
                            val body = cursor.getString(bodyIdx) ?: continue
                            val timestamp = cursor.getLong(dateIdx)

                            val record = FinancialMessageParser.parseSms(address, body, timestamp)
                            if (record != null && financialDao.insertRecordIfNew(record) != -1L) count++
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("OmniSyncRepo", "SMS query rejected or failed: ${e.message}")
            }
        }

        if (hasPermission(android.Manifest.permission.READ_CALL_LOG)) {
            try {
                context.contentResolver.query(
                    CallLog.Calls.CONTENT_URI,
                    arrayOf(CallLog.Calls.NUMBER, CallLog.Calls.DATE),
                    null, null, "${CallLog.Calls.DATE} DESC"
                )?.use { cursor ->
                    val numberIdx = cursor.getColumnIndex(CallLog.Calls.NUMBER)
                    val dateIdx = cursor.getColumnIndex(CallLog.Calls.DATE)
                    if (numberIdx >= 0 && dateIdx >= 0) {
                        var scanned = 0
                        while (cursor.moveToNext() && scanned < MAX_CALL_SCAN) {
                            scanned++
                            val number = cursor.getString(numberIdx) ?: continue
                            val timestamp = cursor.getLong(dateIdx)

                            val record = FinancialMessageParser.parseCallLog(number, timestamp)
                            if (record != null && financialDao.insertRecordIfNew(record) != -1L) count++
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("OmniSyncRepo", "Call Log query failed: ${e.message}")
            }
        }

        // Seed demo data only the very first time, so the dashboard is never blank on an
        // emulator / device without permissions. Re-syncs never re-add it.
        if (financialDao.countRecords() == 0 && getSettingValue(KEY_DEMO_SEEDED) != "true") {
            demoRecords().forEach { financialDao.insertRecordIfNew(it) }
            saveSetting(KEY_DEMO_SEEDED, "true")
        }

        count
    }

    private fun hasPermission(permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    private fun demoRecords(): List<FinancialRecordEntity> = listOf(
        FinancialRecordEntity(
            title = "HDFC Loan EMI Auto-Debit",
            type = "EXPENSE",
            amount = 28500.0,
            category = "LOAN",
            description = "Monthly loan repayment for apartment mortgage (Acc: XXXX8992). Interest 8.4% p.a.",
            accountName = "HDFC Mortgage Account",
            dedupeKey = "demo|loan-emi"
        ),
        FinancialRecordEntity(
            title = "Groww Nifty 50 SIP Mutual Fund",
            type = "EXPENSE",
            amount = 12000.0,
            category = "SIP",
            description = "Automated systematic investment plan deposit to index growth fund.",
            accountName = "Groww Mutual Fund",
            dedupeKey = "demo|sip"
        ),
        FinancialRecordEntity(
            title = "Salary Credited",
            type = "EARNING",
            amount = 145000.0,
            category = "GENERAL",
            description = "Monthly compensation salary credit.",
            accountName = "Citibank Primary",
            dedupeKey = "demo|salary"
        ),
        FinancialRecordEntity(
            title = "Credit Card Payment",
            type = "EXPENSE",
            amount = 8900.0,
            category = "CREDIT_CARD",
            description = "Cleared statement balance (Acc: XXXX2011).",
            accountName = "Chase Titanium Visa",
            dedupeKey = "demo|card"
        ),
        FinancialRecordEntity(
            title = "SBI Savings Interest Credit",
            type = "EARNING",
            amount = 4120.0,
            category = "INTEREST",
            description = "Quarterly cumulative savings account interest yield.",
            accountName = "SBI Savings Account",
            dedupeKey = "demo|interest"
        ),
        FinancialRecordEntity(
            title = "Housing Loan Rebate Offer",
            type = "OFFER",
            amount = 1500.0,
            category = "OFFER",
            description = "Pre-approved home renegotiation: save 0.35% off the annual lending index.",
            accountName = "ICICI Lending Desk",
            isActionable = true,
            dedupeKey = "demo|offer"
        )
    )

    suspend fun clearFinancialRecords() = withContext(Dispatchers.IO) {
        financialDao.clearAllRecords()
    }

    // --- Multi-Account Email Synchronization ---
    val allEmails: Flow<List<EmailItemEntity>> = emailDao.getAllEmails()

    suspend fun syncMultiAccountMails(): String = withContext(Dispatchers.IO) {
        val composioApiKey = getSettingValue("composio_api_key")

        if (composioApiKey.isNotEmpty()) {
            val composioMails = com.example.data.api.ComposioClient.fetchRecentGmailMails(composioApiKey).map {
                val lower = it.snippet.lowercase()
                EmailItemEntity(
                    accountEmail = it.accountEmail,
                    provider = "GMAIL",
                    sender = it.sender,
                    subject = it.subject,
                    summary = it.snippet.take(120),
                    fullBody = it.snippet,
                    category = when {
                        lower.contains("offer") || lower.contains("sale") || lower.contains("discount") -> "PROMOTIONS"
                        lower.contains("lottery") || lower.contains("you won") -> "SPAM"
                        else -> "PRIMARY"
                    }
                )
            }

            // Only replace the cache once we actually have fresh data, otherwise a failed
            // network call would leave the user staring at an empty inbox.
            if (composioMails.isNotEmpty()) {
                emailDao.clearAllMails()
                composioMails.forEach { emailDao.insertEmail(it) }
                return@withContext "Synced ${composioMails.size} emails via Composio."
            }
            return@withContext "Composio returned no emails. Check your API key and connected Gmail account."
        }

        // No key configured: show a labelled sample inbox once so the screen is explorable.
        if (emailDao.countEmails() == 0) {
            sampleEmails().forEach { emailDao.insertEmail(it) }
        }
        "No Composio API key configured — showing sample inbox. Add a key in Portals to sync real mail."
    }

    suspend fun markEmailRead(id: Long) = withContext(Dispatchers.IO) {
        emailDao.markEmailRead(id)
    }

    private fun sampleEmails(): List<EmailItemEntity> = listOf(
            EmailItemEntity(
                accountEmail = "user@gmail.com",
                provider = "GMAIL",
                sender = "Vanguard Portfolio Insights",
                subject = "Quarterly SIP Statement Analysis - May 2026",
                summary = "SIP investment grew 11.2% over last quarter. Recommended alignment to balanced mid-cap funds.",
                fullBody = "Your monthly SIP has been successfully aggregated. The net asset value has appreciated. No major modifications to portfolio allocations are suggested.",
                category = "PRIMARY",
                isRead = false
            ),
            EmailItemEntity(
                accountEmail = "user@gmail.com",
                provider = "GMAIL",
                sender = "Netflix Promo Team",
                subject = "30% off Premium plan upgrade exclusively for you!",
                summary = "Promo upgrade discount. Expiring soon.",
                fullBody = "Promo code SUMMER30 allows upgrading to Netflix Ultra HD plan at a discounted rate.",
                category = "PROMOTIONS",
                isRead = true
            ),
            EmailItemEntity(
                accountEmail = "user@gmail.com",
                provider = "GMAIL",
                sender = "Security Alert",
                subject = "Unauthorized Login Attempt Flagged: Chase Loan Suite",
                summary = "Suspicious access blocked from foreign ISP. Secure account immediately.",
                fullBody = "We blocked an unauthorized device trying to sign in to your home equity credit suite. Password reset is strongly suggested.",
                category = "PRIMARY",
                isRead = false
            ),
            EmailItemEntity(
                accountEmail = "user@outlook.com",
                provider = "OUTLOOK",
                sender = "Unknown Lottery Hub",
                subject = "CONGRATULATIONS!! You won a standard cash price of 5,000,000 USD!!!",
                summary = "Spam financial request. Claims fake prize award.",
                fullBody = "Send security deposit immediately to claim reward package.",
                category = "SPAM",
                isRead = false
            ),
            EmailItemEntity(
                accountEmail = "user@zoho.com",
                provider = "ZOHO",
                sender = "Acme Corp Office Admin",
                subject = "Loan eligibility statement and interest calculations updated",
                summary = "Corporate partner preferential loan rates: Home housing logs start at 7.15% fixed.",
                fullBody = "Your linked corporate ID qualified for the custom interest mortgage rate of 7.15% fixed per annum instead of 8.4%. Please coordinate paperwork.",
                category = "PRIMARY",
                isRead = false
            ),
            EmailItemEntity(
                accountEmail = "user@zoho.com",
                provider = "ZOHO",
                sender = "Daily Deals Desk",
                subject = "Coupons for shoes, groceries inside!!",
                summary = "Deals and catalogs.",
                fullBody = "Click link to secure 10% on coupon books.",
                category = "PROMOTIONS",
                isRead = true
            )
        )


    suspend fun clearEmails() = withContext(Dispatchers.IO) {
        emailDao.clearAllMails()
    }

    // --- Daily Overview AI Analysis & Tips ---
    suspend fun runAiWellnessAnalysis(): String = withContext(Dispatchers.IO) {
        val healthContext = gatherHealthContext()
        val prompt = """
            Analyze the following synchronized watch vital parameters and daily logs.
            Provide:
            1. An overall Sleep & Sleep Score Evaluation.
            2. Heart rate trends assessment.
            3. 3 Custom Actionable Wellness Daily Tips based directly on this data.
            Keep the response formatting extremely beautiful, structured, clean, with markdown bullets.
            
            Logs:
            $healthContext
        """.trimIndent()
        
        val aiBaseUrl = getSettingValue("ai_base_url")
        val aiApiKey = getSettingValue("ai_api_key")
        val aiModel = getSettingValue("ai_model")

        GeminiClient.generateContent(
            prompt = prompt,
            systemInstruction = "You are a professional clinical wearable health analyst. Give high-impact medical-adjacent actionable layout insights.",
            customBaseUrl = aiBaseUrl,
            customApiKey = aiApiKey,
            customModel = aiModel
        )
    }

    // --- Settings Configuration Helpers ---
    suspend fun saveSetting(key: String, value: String) {
        settingsDao.saveSetting(AppSettingEntity(key, value))
    }

    suspend fun getSettingValue(key: String): String {
        return settingsDao.getSetting(key)?.value ?: ""
    }

    fun getAllSettings(): Flow<List<AppSettingEntity>> {
        return settingsDao.getAllSettingsFlow()
    }

    // --- Outbound SMTP Summarizer ---
    suspend fun sendDailySmtpSummaryMail(): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        val config = SmtpClient.Config(
            host = getSettingValue("smtp_host").ifEmpty { "smtp.gmail.com" },
            port = getSettingValue("smtp_port").toIntOrNull() ?: 465,
            username = getSettingValue("smtp_username"),
            password = getSettingValue("smtp_password"),
            recipient = getSettingValue("smtp_recipient")
        )

        SmtpClient.validate(config)?.let { return@withContext false to it }

        val timestamp = DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm")
            .withZone(ZoneId.systemDefault())
            .format(Instant.now())

        val mailBody = """
            OMNISYNC DAILY INTELLIGENCE BRIEF
            =================================
            Generated: $timestamp

            HEALTH INSIGHTS
            ${gatherHealthContext()}

            FINANCE BREAKDOWN (Loans, Cards, SIPs)
            ${gatherFinancialContext()}

            EMAIL HIGHLIGHTS
            ${gatherEmailContext()}

            ---------------------------------
            Automated brief from OmniSync AI.
        """.trimIndent()

        val result = SmtpClient.send(config, "OmniSync Daily Summary — $timestamp", mailBody)
        result.fold(
            onSuccess = { true to it },
            onFailure = { false to "SMTP send failed: ${it.localizedMessage ?: it::class.simpleName}" }
        )
    }

    // --- Private Context Aggregators ---
    private suspend fun gatherHealthContext(): String {
        val metrics = database.healthDao().getAllMetricsDirect()
        if (metrics.isEmpty()) return "No wearable data synced yet. Ask the user to grant Health Connect access and run a sync."
        val latest = metrics.first()
        return """
            Latest Read (Sync Time: ${latest.timestamp}):
            - Steps: ${latest.steps} steps (burned approx. ${latest.calories} kcal)
            - Resting / Peak HRV: ${latest.heartRate} bpm
            - Sleep minutes: ${latest.sleepMinutes} mins
            - Wearable Sleep Quality Score: ${latest.sleepScore}/100
            - Blood Oxygen: ${latest.bloodOxygen}%
            - Sync Source: ${latest.rxtype}
        """.trimIndent()
    }

    private suspend fun gatherFinancialContext(): String {
        val records = database.financialDao().getAllRecordsDirect()
        if (records.isEmpty()) return "No parsed financial notifications found."
        val expenses = records.filter { it.type == "EXPENSE" }
        val earnings = records.filter { it.type == "EARNING" }
        val loans = records.filter { it.category == "LOAN" }
        val sips = records.filter { it.category == "SIP" }
        val offers = records.filter { it.category == "OFFER" }

        val expensesSum = expenses.sumOf { it.amount }
        val earningsSum = earnings.sumOf { it.amount }

        return """
            Total Income Identified: INR $earningsSum
            Total Structured Expenses: INR $expensesSum
            Active Loan Accounts tracked: ${loans.size} accounts (Outstanding balance EMI alerts: ${loans.joinToString { it.title + ": INR " + it.amount }})
            SIP Portfolios: ${sips.size} plans (Monthly commitments: ${sips.sumOf { it.amount }} INR)
            Dynamic Offers & Refinances: ${offers.size} options parsed
        """.trimIndent()
    }

    private suspend fun gatherEmailContext(): String {
        val mails = database.emailDao().getAllEmailsDirect()
        if (mails.isEmpty()) return "Zero current emails synchronized."
        val primary = mails.filter { it.category == "PRIMARY" }
        val promotions = mails.filter { it.category == "PROMOTIONS" }
        val spam = mails.filter { it.category == "SPAM" }

        return """
            Emails Analyzed:
            - Primary Important Mails: ${primary.size} items (Summarized: ${primary.joinToString("; ") { it.sender + " -> " + it.subject + " (" + it.summary + ")" }})
            - Segregated Promotional Advertisements: ${promotions.size} items (Isolate-filtered from workspace!)
            - Blocked / Diverted Spam Scams: ${spam.size} items (Isolate-filtered successfully!)
        """.trimIndent()
    }
}
