package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.Percent
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

@Composable
fun FinancialScreen(viewModel: OmniSyncViewModel) {
    val records by viewModel.financialRecords.collectAsState()
    val isSyncingFinance by viewModel.isSyncingFinance.collectAsState()

    val expenses = records.filter { it.type == "EXPENSE" }
    val earnings = records.filter { it.type == "EARNING" }
    val loans = records.filter { it.category == "LOAN" }
    val sips = records.filter { it.category == "SIP" }
    val offers = records.filter { it.category == "OFFER" }

    val totalExpenses = expenses.sumOf { it.amount }
    val totalEarnings = earnings.sumOf { it.amount }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("financial_screen")
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(bottom = 24.dp, top = 8.dp)
    ) {
        // --- Header Core ---
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Unified Ledger Portfolio",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Black
                    )
                    Text(
                        text = "Real-time parsing of credit cards, SIP, loans & interests",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }
                
                Button(
                    onClick = { viewModel.triggerFinanceSync() },
                    shape = RoundedCornerShape(12.dp),
                    enabled = !isSyncingFinance
                ) {
                    if (isSyncingFinance) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 1.5.dp)
                    } else {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Re-parse", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }
        }

        // --- Ledger Summary Cards ---
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                FinanceMetricCard(
                    modifier = Modifier.weight(1f),
                    title = "Total Earnings",
                    amount = "INR $totalEarnings",
                    label = "Salaries & Interests",
                    color = Color(0xFF4CAF50),
                    icon = Icons.AutoMirrored.Filled.TrendingUp
                )
                FinanceMetricCard(
                    modifier = Modifier.weight(1f),
                    title = "Total Expenses",
                    amount = "INR $totalExpenses",
                    label = "Outflow & Auto-Debits",
                    color = Color(0xFFFF9800),
                    icon = Icons.AutoMirrored.Filled.TrendingDown
                )
            }
        }

        // --- Leverage Refinance Banner (Loans & Interests) ---
        if (offers.isNotEmpty()) {
            item {
                Text("AI Refinancing Opportunities", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            items(offers) { offer ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Outlined.Percent, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(offer.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Black)
                            Text(offer.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Actionable Contact: ${offer.accountName}",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }

        // --- Section breakdown of Loans & Mutual Funds (SIP) ---
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Card(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.HomeWork, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Mortgage Loans", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        Text("Active Accounts: ${loans.size}", style = MaterialTheme.typography.bodySmall)
                        Text("Monthly EMIs: ${loans.sumOf { it.amount }} INR", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Black)
                    }
                }

                Card(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.AutoMirrored.Filled.TrendingUp, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color(0xFF4CAF50))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Mutual Fund SIPs", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        Text("Active Plans: ${sips.size}", style = MaterialTheme.typography.bodySmall)
                        Text("Committed: ${sips.sumOf { it.amount }} INR", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Black)
                    }
                }
            }
        }

        // --- Records Streams List ---
        item {
            Text("Parsed SMS Ledger Transactions", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }

        val parsedTransactions = records.filter { it.type != "OFFER" }
        if (parsedTransactions.isEmpty()) {
            item {
                Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    Text("No transactions detected. Trigger re-parsing of active messages.", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
                }
            }
        } else {
            items(parsedTransactions) { record ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(
                                            if (record.type == "EARNING") Color(0xFFE8F5E9) else Color(0xFFFFF3E0)
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = if (record.type == "EARNING") Icons.Filled.ArrowUpward else Icons.Filled.ArrowDownward,
                                        contentDescription = null,
                                        tint = if (record.type == "EARNING") Color(0xFF4BCA56) else Color(0xFFFF9F1C),
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(record.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                                    Text(record.accountName, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
                                }
                            }
                            Text(
                                "INR ${record.amount}",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Black,
                                color = if (record.type == "EARNING") Color(0xFF4CAF50) else Color(0xFFFF9800)
                            )
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = record.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun FinanceMetricCard(modifier: Modifier = Modifier, title: String, amount: String, label: String, color: Color, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.08f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Icon(imageVector = icon, contentDescription = null, tint = color, modifier = Modifier.size(22.dp))
            Spacer(modifier = Modifier.height(8.dp))
            Text(title, style = MaterialTheme.typography.labelSmall, color = color, fontWeight = FontWeight.Bold)
            Text(amount, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
        }
    }
}
