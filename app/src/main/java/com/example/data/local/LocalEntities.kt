package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "chat_threads")
data class ChatThreadEntity(
    @PrimaryKey val id: String, // UUID
    val title: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "chat_messages")
data class ChatMessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val threadId: String,
    val role: String, // "user" or "model"
    val content: String,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "health_metrics")
data class HealthMetricEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val heartRate: Int,
    val sleepMinutes: Int,
    val sleepScore: Int,
    val steps: Int,
    val calories: Int,
    val rxtype: String = "Automated Active Sync", // e.g. "Galaxy Watch"
    val bloodOxygen: Int = 98 // extra health metadata
)

@Entity(tableName = "financial_records")
data class FinancialRecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val title: String,
    val type: String, // "EXPENSE", "EARNING", "LOAN", "SIP", "OFFER"
    val amount: Double,
    val category: String, // "LOAN", "SIP", "CREDIT_CARD", "INTEREST", "BANK_TRANSFER", "OFFER", "GENERAL"
    val description: String,
    val accountName: String,
    val isActionable: Boolean = false
)

@Entity(tableName = "email_items")
data class EmailItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val accountEmail: String,
    val provider: String, // "GMAIL", "OUTLOOK", "ZOHO"
    val sender: String,
    val subject: String,
    val summary: String,
    val fullBody: String,
    val category: String, // "PRIMARY", "PROMOTIONS", "SPAM"
    val timestamp: Long = System.currentTimeMillis(),
    val isRead: Boolean = false
)

@Entity(tableName = "app_settings")
data class AppSettingEntity(
    @PrimaryKey val key: String,
    val value: String
)
