package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.MailOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.viewmodel.OmniSyncViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: OmniSyncViewModel) {
    val smtpHost by viewModel.smtpHost.collectAsState()
    val smtpPort by viewModel.smtpPort.collectAsState()
    val smtpUsername by viewModel.smtpUsername.collectAsState()
    val smtpPassword by viewModel.smtpPassword.collectAsState()
    val smtpRecipient by viewModel.smtpRecipient.collectAsState()

    val smtpResult by viewModel.smtpResult.collectAsState()
    val isDriveConnected by viewModel.googleDriveConnected.collectAsState()
    val isNextcloudConnected by viewModel.nextcloudConnected.collectAsState()

    val aiBaseUrl by viewModel.aiBaseUrl.collectAsState()
    val aiApiKey by viewModel.aiApiKey.collectAsState()
    val aiModel by viewModel.aiModel.collectAsState()
    val composioApiKey by viewModel.composioApiKey.collectAsState()

    var tempHost by remember(smtpHost) { mutableStateOf(smtpHost) }
    var tempPort by remember(smtpPort) { mutableStateOf(smtpPort) }
    var tempUser by remember(smtpUsername) { mutableStateOf(smtpUsername) }
    var tempPass by remember(smtpPassword) { mutableStateOf(smtpPassword) }
    var tempRecipient by remember(smtpRecipient) { mutableStateOf(smtpRecipient) }

    var tempAiBaseUrl by remember(aiBaseUrl) { mutableStateOf(aiBaseUrl) }
    var tempAiApiKey by remember(aiApiKey) { mutableStateOf(aiApiKey) }
    var tempAiModel by remember(aiModel) { mutableStateOf(aiModel) }
    var tempComposio by remember(composioApiKey) { mutableStateOf(composioApiKey) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("settings_screen")
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(bottom = 24.dp, top = 8.dp)
    ) {
        // --- Header Core ---
        item {
            Column {
                Text(
                    text = "Configurations & Cloud Linkages",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Black
                )
                Text(
                    text = "OAuth synchronization portals & SMTP dispatches",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
            }
        }

        // --- SMTP Credentials Group ---
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.MailOutline, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Outbound SMTP Server Config", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = tempHost,
                        onValueChange = { tempHost = it },
                        label = { Text("SMTP Host Server") },
                        modifier = Modifier.fillMaxWidth().testTag("smtp_host_input"),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = tempPort,
                        onValueChange = { tempPort = it },
                        label = { Text("SMTP Port Number") },
                        modifier = Modifier.fillMaxWidth().testTag("smtp_port_input"),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = tempUser,
                        onValueChange = { tempUser = it },
                        label = { Text("SMTP Sender Email") },
                        modifier = Modifier.fillMaxWidth().testTag("smtp_user_input"),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = tempPass,
                        onValueChange = { tempPass = it },
                        label = { Text("SMTP Password / Credential Token") },
                        modifier = Modifier.fillMaxWidth().testTag("smtp_pass_input"),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = tempRecipient,
                        onValueChange = { tempRecipient = it },
                        label = { Text("Target recipient Address") },
                        modifier = Modifier.fillMaxWidth().testTag("smtp_recipient_input"),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(14.dp))

                    Button(
                        onClick = {
                            viewModel.updateSmtpConfig(tempHost, tempPort, tempUser, tempPass, tempRecipient)
                        },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth().testTag("smtp_save_btn")
                    ) {
                        Text("Save Server Configuration", fontWeight = FontWeight.Bold)
                    }

                    if (smtpResult.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = smtpResult,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }

    // --- Custom AI & Composio Configuration ---
    item {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.CloudDownload, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("AI & Composio Integrations", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.height(12.dp))
                
                Text(
                    "Override default Gemini with Custom OpenAI-compatible endpoints (e.g., Local LM Studio). Leave blank to use default Gemini.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = tempAiBaseUrl,
                    onValueChange = { tempAiBaseUrl = it },
                    label = { Text("Custom AI Base URL (v1/chat/completions)") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = tempAiApiKey,
                    onValueChange = { tempAiApiKey = it },
                    label = { Text("Custom AI API Key") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                
                OutlinedTextField(
                    value = tempAiModel,
                    onValueChange = { tempAiModel = it },
                    label = { Text("Custom AI Model Name") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(16.dp))
                
                Divider()
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    "Composio API (For Real Email Sync & Dispatch)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = tempComposio,
                    onValueChange = { tempComposio = it },
                    label = { Text("Composio API Key") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(14.dp))

                Button(
                    onClick = {
                        viewModel.updateAiAndComposioConfig(tempAiBaseUrl, tempAiApiKey, tempAiModel, tempComposio)
                    },
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Save Integrations", fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    // --- Cloud Storage Linkages (GDrive & Nextcloud) ---
    item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.CloudDownload, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Cloud Knowledge Backups", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Backup your unified synchronized knowledge base to primary business clouds.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    // GDrive Toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFF34A853).copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.CloudQueue, contentDescription = null, tint = Color(0xFF34A853))
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text("Google Drive Folder sync", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                                Text(
                                    text = if (isDriveConnected) "Linked via Google Identity OAuth" else "Not Linked",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isDriveConnected) Color(0xFF34A853) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                        Switch(
                            checked = isDriveConnected,
                            onCheckedChange = { viewModel.toggleGoogleDrive() },
                            modifier = Modifier.testTag("gdrive_toggle_switch")
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Divider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                    Spacer(modifier = Modifier.height(12.dp))

                    // Nextcloud Toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFF0082C9).copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Dns, contentDescription = null, tint = Color(0xFF0082C9))
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text("Nextcloud WebDAV Sync", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                                Text(
                                    text = if (isNextcloudConnected) "Connected via active protocol URL" else "Disconnected",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isNextcloudConnected) Color(0xFF0082C9) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                        Switch(
                            checked = isNextcloudConnected,
                            onCheckedChange = { viewModel.toggleNextcloud() },
                            modifier = Modifier.testTag("nextcloud_toggle_switch")
                        )
                    }
                }
            }
        }
    }
}
