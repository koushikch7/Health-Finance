package com.example.data.local

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Verifies that upgrading an existing v1 install keeps the user's data.
 * A destructive fallback here would silently delete chat history, which the
 * user can never regenerate from the device.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AppDatabaseMigrationTest {

    private val dbName = "migration_test_db"
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(dbName)
    }

    @After
    fun tearDown() {
        context.deleteDatabase(dbName)
    }

    /** Builds the exact v1 schema and seeds it with representative user data. */
    private fun createV1Database() {
        val config = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(dbName)
            .callback(object : SupportSQLiteOpenHelper.Callback(1) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS `chat_threads` (" +
                            "`id` TEXT NOT NULL, `title` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, " +
                            "PRIMARY KEY(`id`))"
                    )
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS `chat_messages` (" +
                            "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `threadId` TEXT NOT NULL, " +
                            "`role` TEXT NOT NULL, `content` TEXT NOT NULL, `timestamp` INTEGER NOT NULL)"
                    )
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS `health_metrics` (" +
                            "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `timestamp` INTEGER NOT NULL, " +
                            "`heartRate` INTEGER NOT NULL, `sleepMinutes` INTEGER NOT NULL, " +
                            "`sleepScore` INTEGER NOT NULL, `steps` INTEGER NOT NULL, " +
                            "`calories` INTEGER NOT NULL, `rxtype` TEXT NOT NULL, `bloodOxygen` INTEGER NOT NULL)"
                    )
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS `financial_records` (" +
                            "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `timestamp` INTEGER NOT NULL, " +
                            "`title` TEXT NOT NULL, `type` TEXT NOT NULL, `amount` REAL NOT NULL, " +
                            "`category` TEXT NOT NULL, `description` TEXT NOT NULL, " +
                            "`accountName` TEXT NOT NULL, `isActionable` INTEGER NOT NULL)"
                    )
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS `email_items` (" +
                            "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `accountEmail` TEXT NOT NULL, " +
                            "`provider` TEXT NOT NULL, `sender` TEXT NOT NULL, `subject` TEXT NOT NULL, " +
                            "`summary` TEXT NOT NULL, `fullBody` TEXT NOT NULL, `category` TEXT NOT NULL, " +
                            "`timestamp` INTEGER NOT NULL, `isRead` INTEGER NOT NULL)"
                    )
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS `app_settings` (" +
                            "`key` TEXT NOT NULL, `value` TEXT NOT NULL, PRIMARY KEY(`key`))"
                    )

                    db.execSQL("INSERT INTO chat_threads VALUES ('thread-1', 'My Analysis', 100)")
                    db.execSQL("INSERT INTO chat_messages VALUES (1, 'thread-1', 'user', 'How are my loans?', 101)")
                    db.execSQL("INSERT INTO chat_messages VALUES (2, 'thread-1', 'model', 'Your EMI is on track.', 102)")
                    db.execSQL(
                        "INSERT INTO financial_records VALUES " +
                            "(1, 200, 'HDFC EMI', 'EXPENSE', 28500.0, 'LOAN', 'monthly emi', 'HDFC', 0)"
                    )
                    db.execSQL(
                        "INSERT INTO financial_records VALUES " +
                            "(2, 201, 'Salary', 'EARNING', 145000.0, 'GENERAL', 'pay', 'Citi', 0)"
                    )
                    db.execSQL("INSERT INTO app_settings VALUES ('smtp_host', 'smtp.example.com')")
                }

                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
            })
            .build()

        FrameworkSQLiteOpenHelperFactory().create(config).writableDatabase.close()
    }

    private fun openV2(): AppDatabase = Room.databaseBuilder(context, AppDatabase::class.java, dbName)
        .addMigrations(AppDatabase.MIGRATION_1_2)
        .build()

    @Test
    fun `migration preserves chat history, ledger and settings`() = runTest {
        createV1Database()

        val db = openV2()
        try {
            // Chat history survives — this is the data the user can never get back.
            assertEquals(1, db.chatDao().countThreads())
            val messages = db.chatDao().getMessagesForThreadDirect("thread-1")
            assertEquals(2, messages.size)
            assertEquals("How are my loans?", messages[0].content)

            // Ledger rows survive and each receives a distinct backfilled key.
            val records = db.financialDao().getAllRecordsDirect()
            assertEquals(2, records.size)
            assertEquals(2, records.map { it.dedupeKey }.toSet().size)
            assertTrue(records.all { it.dedupeKey.startsWith("legacy|") })
            assertEquals(28500.0, records.first { it.title == "HDFC EMI" }.amount, 0.001)

            // Settings survive.
            assertEquals("smtp.example.com", db.settingsDao().getSetting("smtp_host")?.value)
        } finally {
            db.close()
        }
    }

    @Test
    fun `dedupe constraint is active after migrating`() = runTest {
        createV1Database()

        val db = openV2()
        try {
            val duplicate = FinancialRecordEntity(
                title = "dupe",
                type = "EXPENSE",
                amount = 1.0,
                category = "GENERAL",
                description = "d",
                accountName = "a",
                dedupeKey = "legacy|1" // already used by the migrated row
            )
            assertEquals(-1L, db.financialDao().insertRecordIfNew(duplicate))
            assertEquals(2, db.financialDao().countRecords())
        } finally {
            db.close()
        }
    }
}


