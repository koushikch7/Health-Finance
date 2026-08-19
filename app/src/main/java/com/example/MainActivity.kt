package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.health.connect.client.PermissionController
import com.example.data.local.AppDatabase
import com.example.data.repository.OmniSyncRepository
import com.example.ui.screens.*
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.*

class MainActivity : ComponentActivity() {

    private lateinit var database: AppDatabase
    private lateinit var repository: OmniSyncRepository
    private var viewModelRef: OmniSyncViewModel? = null

    // Runtime permissions for the SMS / call-log ledger parser.
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.values.any { it }) {
            // Re-run the parser now that we can actually read the providers.
            viewModelRef?.triggerFinanceSync()
        }
    }

    // Health Connect uses its own permission contract rather than the standard Android one.
    private val healthPermissionLauncher = registerForActivityResult(
        PermissionController.createRequestPermissionResultContract()
    ) { granted ->
        if (granted.isNotEmpty()) viewModelRef?.triggerWatchSync()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Initialize SQLite Room DB (singleton — avoids re-opening on every recreate)
        database = AppDatabase.getInstance(applicationContext)

        repository = OmniSyncRepository(applicationContext, database)

        // Only ask for what is still missing, so returning users are not re-prompted.
        val required = arrayOf(
            Manifest.permission.READ_SMS,
            Manifest.permission.RECEIVE_SMS,
            Manifest.permission.READ_CALL_LOG
        )
        val missing = required.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) permissionLauncher.launch(missing.toTypedArray())

        setContent {
            MyApplicationTheme {
                // Initialize ViewModel with custom factory surving state lifecycle
                val viewModelFactory = OmniSyncViewModelFactory(application, repository)
                val viewModel: OmniSyncViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
                    factory = viewModelFactory
                )

                // Permission callbacks fire outside composition, so keep a handle to the VM.
                SideEffect { viewModelRef = viewModel }

                val selectedScreen by viewModel.selectedScreen.collectAsState()
                val statusMessage by viewModel.statusMessage.collectAsState()
                val snackbarHostState = remember { SnackbarHostState() }

                // Surface every sync result (success or failure) instead of failing silently.
                LaunchedEffect(statusMessage) {
                    statusMessage?.let {
                        snackbarHostState.showSnackbar(it)
                        viewModel.consumeStatusMessage()
                    }
                }

                Scaffold(
                    modifier = Modifier.fillMaxSize().testTag("main_app_scaffold"),
                    snackbarHost = { SnackbarHost(snackbarHostState) },
                    topBar = {
                        @OptIn(ExperimentalMaterial3Api::class)
                        CenterAlignedTopAppBar(
                            title = {
                                Text(
                                    text = when (selectedScreen) {
                                        Screen.Dashboard -> "OMNISYNC AI"
                                        Screen.Chatbot -> "INTELLIGENT BOT"
                                        Screen.Emails -> "EMAIL SEGREGINATOR"
                                        Screen.Financials -> "LEDGER ANALYTICS"
                                        Screen.Health -> "WATCH METRICS"
                                        Screen.Settings -> "CREDENTIAL PORTALS"
                                    },
                                    fontWeight = FontWeight.Black,
                                    style = MaterialTheme.typography.titleMedium
                                )
                            },
                            colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                                containerColor = MaterialTheme.colorScheme.surface
                            )
                        )
                    },
                    bottomBar = {
                        NavigationBar(
                            modifier = Modifier.testTag("app_navigation_bar"),
                            containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp)
                        ) {
                            NavigationBarItem(
                                selected = selectedScreen == Screen.Dashboard,
                                onClick = { viewModel.navigateTo(Screen.Dashboard) },
                                icon = { Icon(Icons.Default.Dashboard, contentDescription = "Dashboard") },
                                label = { Text("Hub", fontWeight = FontWeight.Bold) },
                                modifier = Modifier.testTag("nav_hub_item")
                            )
                            NavigationBarItem(
                                selected = selectedScreen == Screen.Health,
                                onClick = { viewModel.navigateTo(Screen.Health) },
                                icon = { Icon(Icons.Default.Watch, contentDescription = "Health") },
                                label = { Text("Vitals", fontWeight = FontWeight.Bold) },
                                modifier = Modifier.testTag("nav_vitals_item")
                            )
                            NavigationBarItem(
                                selected = selectedScreen == Screen.Financials,
                                onClick = { viewModel.navigateTo(Screen.Financials) },
                                icon = { Icon(Icons.Default.AccountBalanceWallet, contentDescription = "Finance") },
                                label = { Text("Ledger", fontWeight = FontWeight.Bold) },
                                modifier = Modifier.testTag("nav_ledger_item")
                            )
                            NavigationBarItem(
                                selected = selectedScreen == Screen.Emails,
                                onClick = { viewModel.navigateTo(Screen.Emails) },
                                icon = { Icon(Icons.AutoMirrored.Filled.ForwardToInbox, contentDescription = "Emails") },
                                label = { Text("Mails", fontWeight = FontWeight.Bold) },
                                modifier = Modifier.testTag("nav_mails_item")
                            )
                            NavigationBarItem(
                                selected = selectedScreen == Screen.Chatbot,
                                onClick = { viewModel.navigateTo(Screen.Chatbot) },
                                icon = { Icon(Icons.Default.SmartToy, contentDescription = "Chatbot") },
                                label = { Text("Chat", fontWeight = FontWeight.Bold) },
                                modifier = Modifier.testTag("nav_chat_item")
                            )
                            NavigationBarItem(
                                selected = selectedScreen == Screen.Settings,
                                onClick = { viewModel.navigateTo(Screen.Settings) },
                                icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
                                label = { Text("Portals", fontWeight = FontWeight.Bold) },
                                modifier = Modifier.testTag("nav_portals_item")
                            )
                        }
                    }
                ) { innerPadding ->
                    // Animated Navigation crossfade transition
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    ) {
                        AnimatedContent(
                            targetState = selectedScreen,
                            transitionSpec = {
                                fadeIn() togetherWith fadeOut()
                            },
                            label = "screen_navigation_fade"
                        ) { targetScreen ->
                            when (targetScreen) {
                                Screen.Dashboard -> DashboardScreen(viewModel)
                                Screen.Health -> HealthScreen(
                                    viewModel = viewModel,
                                    onRequestHealthPermissions = {
                                        healthPermissionLauncher.launch(repository.healthConnectPermissions)
                                    }
                                )
                                Screen.Financials -> FinancialScreen(viewModel)
                                Screen.Emails -> EmailScreen(viewModel)
                                Screen.Chatbot -> ChatbotScreen(viewModel)
                                Screen.Settings -> SettingsScreen(viewModel)
                            }
                        }
                    }
                }
            }
        }
    }
}
