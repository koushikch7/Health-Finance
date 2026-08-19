package com.example.data.local

import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
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

    @Query("SELECT * FROM chat_messages WHERE threadId = :threadId ORDER BY timestamp ASC")
    suspend fun getMessagesForThreadDirect(threadId: String): List<ChatMessageEntity>

    @Query("SELECT COUNT(*) FROM chat_threads")
    suspend fun countThreads(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: ChatMessageEntity)

    @Query("DELETE FROM chat_messages WHERE threadId = :threadId")
    suspend fun deleteMessagesForThread(threadId: String)

    @Transaction
    suspend fun deleteThreadWithMessages(threadId: String) {
        deleteMessagesForThread(threadId)
        deleteThread(threadId)
    }
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

    /** Ignores rows whose [FinancialRecordEntity.dedupeKey] already exists, keeping re-syncs idempotent. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertRecordIfNew(record: FinancialRecordEntity): Long

    @Query("SELECT COUNT(*) FROM financial_records")
    suspend fun countRecords(): Int

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

    @Query("UPDATE email_items SET isRead = 1 WHERE id = :id")
    suspend fun markEmailRead(id: Long)

    @Query("SELECT COUNT(*) FROM email_items")
    suspend fun countEmails(): Int

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
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun chatDao(): ChatDao
    abstract fun healthDao(): HealthDao
    abstract fun financialDao(): FinancialDao
    abstract fun emailDao(): EmailDao
    abstract fun settingsDao(): SettingsDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        /**
         * v1 -> v2 adds `financial_records.dedupeKey` (unique) plus lookup indices.
         *
         * Chat history is authored by the user and cannot be regenerated from the device,
         * so this migrates in place rather than dropping tables. The ledger table is
         * rebuilt because SQLite cannot add a NOT NULL column and backfill it atomically;
         * existing rows are carried over with a synthetic key derived from their id.
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `financial_records_new` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `timestamp` INTEGER NOT NULL,
                        `title` TEXT NOT NULL,
                        `type` TEXT NOT NULL,
                        `amount` REAL NOT NULL,
                        `category` TEXT NOT NULL,
                        `description` TEXT NOT NULL,
                        `accountName` TEXT NOT NULL,
                        `isActionable` INTEGER NOT NULL,
                        `dedupeKey` TEXT NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT INTO `financial_records_new`
                        (id, timestamp, title, type, amount, category, description, accountName, isActionable, dedupeKey)
                    SELECT id, timestamp, title, type, amount, category, description, accountName, isActionable,
                           'legacy|' || id
                    FROM `financial_records`
                    """.trimIndent()
                )
                db.execSQL("DROP TABLE `financial_records`")
                db.execSQL("ALTER TABLE `financial_records_new` RENAME TO `financial_records`")

                // Index names must match Room's generated convention or validation fails.
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_financial_records_dedupeKey` ON `financial_records` (`dedupeKey`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_chat_messages_threadId` ON `chat_messages` (`threadId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_email_items_category` ON `email_items` (`category`)")
            }
        }

        fun getInstance(context: android.content.Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "omnisync_intelligence_db"
                )
                    .addMigrations(MIGRATION_1_2)
                    .build()
                    .also { INSTANCE = it }
            }
    }
}
