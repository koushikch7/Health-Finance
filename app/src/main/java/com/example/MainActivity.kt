package com.example

import android.Manifest
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.room.Room
import com.example.data.local.AppDatabase
import com.example.data.repository.OmniSyncRepository
import com.example.ui.screens.*
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.*

class MainActivity : ComponentActivity() {

    private lateinit var database: AppDatabase
    private lateinit var repository: OmniSyncRepository

    // Request permissions on startup for absolute compliance
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val smsGranted = permissions[Manifest.permission.READ_SMS] ?: false
        val callsGranted = permissions[Manifest.permission.READ_CALL_LOG] ?: false
        val btGranted = permissions[Manifest.permission.BLUETOOTH_CONNECT] ?: false

        if (smsGranted || callsGranted || btGranted) {
            Toast.makeText(
                this,
                "Permissions linked successfully! Active sync enabled.",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Initialize SQLite Room DB
        database = Room.databaseBuilder(
            applicationContext,
            AppDatabase::class.java,
            "omnisync_intelligence_db"
        )
            .fallbackToDestructiveMigration()
            .build()

        repository = OmniSyncRepository(applicationContext, database)

        // Launch permissions prompt
        permissionLauncher.launch(
            arrayOf(
                Manifest.permission.READ_SMS,
                Manifest.permission.RECEIVE_SMS,
                Manifest.permission.READ_CALL_LOG,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_SCAN
            )
        )

        setContent {
            MyApplicationTheme {
                // Initialize ViewModel with custom factory surving state lifecycle
                val viewModelFactory = OmniSyncViewModelFactory(application, repository)
                val viewModel: OmniSyncViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
                    factory = viewModelFactory
                )

                val selectedScreen by viewModel.selectedScreen.collectAsState()

                Scaffold(
                    modifier = Modifier.fillMaxSize().testTag("main_app_scaffold"),
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
                                icon = { Icon(Icons.Default.ForwardToInbox, contentDescription = "Emails") },
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
                                Screen.Health -> HealthScreen(viewModel)
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
