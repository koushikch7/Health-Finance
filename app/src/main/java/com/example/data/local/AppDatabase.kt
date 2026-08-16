package com.example.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatDao {
    @Query("SELECT * FROM chat_threads ORDER BY createdAt DESC")
    fun getAllThreads(): Flow<List<ChatThreadEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertThread(thread: ChatThreadEntity)

    @Query("DELETE FROM chat_threads WHERE id = :threadId")
    suspend fun deleteThread(threadId: String)

    @Query("SELECT * FROM chat_messages WHERE threadId = :threadId ORDER BY timestamp ASC")
    fun getMessagesForThread(threadId: String): Flow<List<ChatMessageEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: ChatMessageEntity)

    @Query("DELETE FROM chat_messages WHERE threadId = :threadId")
    suspend fun deleteMessagesForThread(threadId: String)
}

@Dao
interface HealthDao {
    @Query("SELECT * FROM health_metrics ORDER BY timestamp DESC")
    fun getAllMetrics(): Flow<List<HealthMetricEntity>>

    @Query("SELECT * FROM health_metrics ORDER BY timestamp DESC LIMIT 1")
    fun getLatestMetric(): Flow<HealthMetricEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMetric(metric: HealthMetricEntity)

    @Query("DELETE FROM health_metrics")
    suspend fun clearAllMetrics()

    @Query("SELECT * FROM health_metrics ORDER BY timestamp DESC")
    suspend fun getAllMetricsDirect(): List<HealthMetricEntity>
}

@Dao
interface FinancialDao {
    @Query("SELECT * FROM financial_records ORDER BY timestamp DESC")
    fun getAllRecords(): Flow<List<FinancialRecordEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecord(record: FinancialRecordEntity)

    @Query("DELETE FROM financial_records WHERE id = :id")
    suspend fun deleteRecord(id: Long)

    @Query("DELETE FROM financial_records")
    suspend fun clearAllRecords()

    @Query("SELECT * FROM financial_records ORDER BY timestamp DESC")
    suspend fun getAllRecordsDirect(): List<FinancialRecordEntity>
}

@Dao
interface EmailDao {
    @Query("SELECT * FROM email_items ORDER BY timestamp DESC")
    fun getAllEmails(): Flow<List<EmailItemEntity>>

    @Query("SELECT * FROM email_items WHERE category = :category ORDER BY timestamp DESC")
    fun getEmailsByCategory(category: String): Flow<List<EmailItemEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEmail(email: EmailItemEntity)

    @Update
    suspend fun updateEmail(email: EmailItemEntity)

    @Query("DELETE FROM email_items WHERE id = :id")
    suspend fun deleteEmail(id: Long)

    @Query("DELETE FROM email_items")
    suspend fun clearAllMails()

    @Query("SELECT * FROM email_items ORDER BY timestamp DESC")
    suspend fun getAllEmailsDirect(): List<EmailItemEntity>
}

@Dao
interface SettingsDao {
    @Query("SELECT * FROM app_settings WHERE `key` = :key LIMIT 1")
    suspend fun getSetting(key: String): AppSettingEntity?

    @Query("SELECT * FROM app_settings")
    fun getAllSettingsFlow(): Flow<List<AppSettingEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveSetting(setting: AppSettingEntity)
}

@Database(
    entities = [
        ChatThreadEntity::class,
        ChatMessageEntity::class,
        HealthMetricEntity::class,
        FinancialRecordEntity::class,
        EmailItemEntity::class,
        AppSettingEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun chatDao(): ChatDao
    abstract fun healthDao(): HealthDao
    abstract fun financialDao(): FinancialDao
    abstract fun emailDao(): EmailDao
    abstract fun settingsDao(): SettingsDao
}
