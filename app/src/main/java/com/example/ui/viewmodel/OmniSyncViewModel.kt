package com.example.ui.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.local.AppDatabase
import com.example.data.local.ChatMessageEntity
import com.example.data.local.ChatThreadEntity
import com.example.data.local.EmailItemEntity
import com.example.data.local.FinancialRecordEntity
import com.example.data.local.HealthMetricEntity
import com.example.data.repository.OmniSyncRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class OmniSyncViewModel(application: Application, private val repository: OmniSyncRepository) : AndroidViewModel(application) {

    // --- Screen State ---
    private val _selectedScreen = MutableStateFlow(Screen.Dashboard)
    val selectedScreen = _selectedScreen.asStateFlow()

    fun navigateTo(screen: Screen) {
        _selectedScreen.value = screen
    }

    // --- Health UI State ---
    val healthMetrics: StateFlow<List<HealthMetricEntity>> = repository.allHealthMetrics
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val latestHealthMetric: StateFlow<HealthMetricEntity?> = repository.latestHealthMetric
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val _isSyncingWatch = MutableStateFlow(false)
    val isSyncingWatch = _isSyncingWatch.asStateFlow()

    private val _aiWellnessAnalysis = MutableStateFlow("")
    val aiWellnessAnalysis = _aiWellnessAnalysis.asStateFlow()

    private val _generatingWellnessAnalysis = MutableStateFlow(false)
    val generatingWellnessAnalysis = _generatingWellnessAnalysis.asStateFlow()

    fun triggerWatchSync() {
        viewModelScope.launch {
            _isSyncingWatch.value = true
            repository.syncBluetoothWatchMetrics()
            _isSyncingWatch.value = false
            generateWellnessInsights()
        }
    }

    fun generateWellnessInsights() {
        viewModelScope.launch {
            _generatingWellnessAnalysis.value = true
            val insights = repository.runAiWellnessAnalysis()
            _aiWellnessAnalysis.value = insights
            _generatingWellnessAnalysis.value = false
        }
    }

    // --- Finance UI State ---
    val financialRecords: StateFlow<List<FinancialRecordEntity>> = repository.allFinancialRecords
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _isSyncingFinance = MutableStateFlow(false)
    val isSyncingFinance = _isSyncingFinance.asStateFlow()

    fun triggerFinanceSync() {
        viewModelScope.launch {
            _isSyncingFinance.value = true
            repository.runSmsAndCallFinanceParse()
            _isSyncingFinance.value = false
        }
    }

    // --- Email UI State ---
    val emails: StateFlow<List<EmailItemEntity>> = repository.allEmails
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _emailCategoryFilter = MutableStateFlow("PRIMARY")
    val emailCategoryFilter = _emailCategoryFilter.asStateFlow()

    private val _isSyncingMails = MutableStateFlow(false)
    val isSyncingMails = _isSyncingMails.asStateFlow()

    fun setEmailCategoryFilter(category: String) {
        _emailCategoryFilter.value = category
    }

    fun triggerEmailsSync() {
        viewModelScope.launch {
            _isSyncingMails.value = true
            repository.syncMultiAccountMails()
            _isSyncingMails.value = false
        }
    }

    // --- Chatbot UI State ---
    val chatThreads: StateFlow<List<ChatThreadEntity>> = repository.allThreads
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _activeThreadId = MutableStateFlow<String?>(null)
    val activeThreadId = _activeThreadId.asStateFlow()

    private val _activeMessages = MutableStateFlow<List<ChatMessageEntity>>(emptyList())
    val activeMessages = _activeMessages.asStateFlow()

    private val _inputText = MutableStateFlow("")
    val inputText = _inputText.asStateFlow()

    private val _isGeneratingAILink = MutableStateFlow(false)
    val isGeneratingAILink = _isGeneratingAILink.asStateFlow()

    fun updateInputText(text: String) {
        _inputText.value = text
    }

    private var messageCollectionJob: Job? = null

    fun selectThread(threadId: String) {
        _activeThreadId.value = threadId
        messageCollectionJob?.cancel()
        messageCollectionJob = viewModelScope.launch {
            repository.getMessagesForThread(threadId).collect { msgs ->
                _activeMessages.value = msgs
            }
        }
    }

    fun createNewThreadClick(title: String = "Sync Analysis Discuss") {
        viewModelScope.launch {
            val thread = repository.createNewThread(title)
            selectThread(thread.id)
        }
    }

    fun deleteThreadClick(threadId: String) {
        val nextThread = chatThreads.value.firstOrNull { it.id != threadId }
        viewModelScope.launch {
            repository.deleteThread(threadId)
            if (nextThread != null) {
                selectThread(nextThread.id)
            } else {
                _activeThreadId.value = null
                _activeMessages.value = emptyList()
            }
        }
    }

    fun sendTextMessage() {
        val query = _inputText.value.trim()
        val threadId = _activeThreadId.value
        if (query.isEmpty() || threadId == null) return

        _inputText.value = ""
        viewModelScope.launch {
            _isGeneratingAILink.value = true
            repository.sendChatMessage(threadId, query)
            _isGeneratingAILink.value = false
        }
    }

    // --- SMTP & Cloud Integration UI State ---
    private val _smtpResult = MutableStateFlow("")
    val smtpResult = _smtpResult.asStateFlow()

    private val _googleDriveConnected = MutableStateFlow(false)
    val googleDriveConnected = _googleDriveConnected.asStateFlow()

    private val _nextcloudConnected = MutableStateFlow(false)
    val nextcloudConnected = _nextcloudConnected.asStateFlow()

    var smtpHost = MutableStateFlow("smtp.gmail.com")
        private set
    var smtpPort = MutableStateFlow("465")
        private set
    var smtpUsername = MutableStateFlow("")
        private set
    var smtpPassword = MutableStateFlow("")
        private set
    var smtpRecipient = MutableStateFlow("")
        private set

    var aiBaseUrl = MutableStateFlow("")
        private set
    var aiApiKey = MutableStateFlow("")
        private set
    var aiModel = MutableStateFlow("")
        private set
    var composioApiKey = MutableStateFlow("")
        private set

    init {
        viewModelScope.launch {
            smtpHost.value = repository.getSettingValue("smtp_host").ifEmpty { "smtp.gmail.com" }
            smtpPort.value = repository.getSettingValue("smtp_port").ifEmpty { "465" }
            smtpUsername.value = repository.getSettingValue("smtp_username")
            smtpPassword.value = repository.getSettingValue("smtp_password")
            smtpRecipient.value = repository.getSettingValue("smtp_recipient")

            aiBaseUrl.value = repository.getSettingValue("ai_base_url")
            aiApiKey.value = repository.getSettingValue("ai_api_key")
            aiModel.value = repository.getSettingValue("ai_model")
            composioApiKey.value = repository.getSettingValue("composio_api_key")

            _googleDriveConnected.value = repository.getSettingValue("gdrive_connected").toBoolean()
            _nextcloudConnected.value = repository.getSettingValue("nextcloud_connected").toBoolean()

            // Pre-seed data in parallel
            launch { repository.syncBluetoothWatchMetrics() }
            launch { repository.runSmsAndCallFinanceParse() }
            launch { repository.syncMultiAccountMails() }
            launch { generateWellnessInsights() }
        }

        // Manage active thread in a separate coroutine so it isn't blocked by data loading
        viewModelScope.launch {
            repository.allThreads.collect { threads ->
                if (threads.isNotEmpty() && _activeThreadId.value == null) {
                    selectThread(threads.first().id)
                } else if (threads.isEmpty() && _activeThreadId.value == null) {
                    createNewThreadClick("Inaugural AI Chat Integration")
                }
            }
        }
    }

    fun updateSmtpConfig(host: String, port: String, user: String, pass: String, recipient: String) {
        viewModelScope.launch {
            smtpHost.value = host
            smtpPort.value = port
            smtpUsername.value = user
            smtpPassword.value = pass
            smtpRecipient.value = recipient

            repository.saveSetting("smtp_host", host)
            repository.saveSetting("smtp_port", port)
            repository.saveSetting("smtp_username", user)
            repository.saveSetting("smtp_password", pass)
            repository.saveSetting("smtp_recipient", recipient)
            _smtpResult.value = "SMTP Configuration Saved successfully!"
        }
    }

    fun updateAiAndComposioConfig(baseUrl: String, apiKey: String, model: String, composioKey: String) {
        viewModelScope.launch {
            aiBaseUrl.value = baseUrl
            aiApiKey.value = apiKey
            aiModel.value = model
            composioApiKey.value = composioKey

            repository.saveSetting("ai_base_url", baseUrl)
            repository.saveSetting("ai_api_key", apiKey)
            repository.saveSetting("ai_model", model)
            repository.saveSetting("composio_api_key", composioKey)
            _smtpResult.value = "AI & Composio Config Saved!"
        }
    }

    fun triggerSmtpOverviewSend() {
        viewModelScope.launch {
            _smtpResult.value = "Initiating SMTP email handshake..."
            val outcome = repository.sendDailySmtpSummaryMail()
            _smtpResult.value = outcome.second
        }
    }

    fun toggleGoogleDrive() {
        viewModelScope.launch {
            val nextState = !_googleDriveConnected.value
            _googleDriveConnected.value = nextState
            repository.saveSetting("gdrive_connected", nextState.toString())
        }
    }

    fun toggleNextcloud() {
        viewModelScope.launch {
            val nextState = !_nextcloudConnected.value
            _nextcloudConnected.value = nextState
            repository.saveSetting("nextcloud_connected", nextState.toString())
        }
    }
}

enum class Screen {
    Dashboard,
    Chatbot,
    Emails,
    Financials,
    Health,
    Settings
}

class OmniSyncViewModelFactory(
    private val application: Application,
    private val repository: OmniSyncRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(OmniSyncViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return OmniSyncViewModel(application, repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
