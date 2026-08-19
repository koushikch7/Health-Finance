package com.example.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AppDatabaseTest {

    private lateinit var db: AppDatabase

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun `re-inserting the same dedupe key does not duplicate the ledger`() = runTest {
        val dao = db.financialDao()
        val record = FinancialRecordEntity(
            title = "EMI",
            type = "EXPENSE",
            amount = 100.0,
            category = "LOAN",
            description = "test",
            accountName = "BANK",
            dedupeKey = "sms|BANK|1|abc"
        )

        assertTrue(dao.insertRecordIfNew(record) != -1L)
        assertEquals(-1L, dao.insertRecordIfNew(record))
        assertEquals(1, dao.countRecords())
    }

    @Test
    fun `deleting a thread also removes its messages`() = runTest {
        val dao = db.chatDao()
        val thread = ChatThreadEntity(id = "t1", title = "Test")
        dao.insertThread(thread)
        dao.insertMessage(ChatMessageEntity(threadId = "t1", role = "user", content = "hi"))
        dao.insertMessage(ChatMessageEntity(threadId = "t1", role = "model", content = "hello"))

        assertEquals(2, dao.getMessagesForThreadDirect("t1").size)

        dao.deleteThreadWithMessages("t1")

        assertEquals(0, dao.countThreads())
        assertEquals(0, dao.getMessagesForThreadDirect("t1").size)
    }

    @Test
    fun `messages are returned in chronological order`() = runTest {
        val dao = db.chatDao()
        dao.insertThread(ChatThreadEntity(id = "t2", title = "Ordered"))
        dao.insertMessage(ChatMessageEntity(threadId = "t2", role = "user", content = "second", timestamp = 200))
        dao.insertMessage(ChatMessageEntity(threadId = "t2", role = "user", content = "first", timestamp = 100))

        val contents = dao.getMessagesForThread("t2").first().map { it.content }
        assertEquals(listOf("first", "second"), contents)
    }

    @Test
    fun `marking an email read persists`() = runTest {
        val dao = db.emailDao()
        dao.insertEmail(
            EmailItemEntity(
                accountEmail = "a@b.com",
                provider = "GMAIL",
                sender = "S",
                subject = "Sub",
                summary = "sum",
                fullBody = "body",
                category = "PRIMARY"
            )
        )
        val stored = dao.getAllEmailsDirect().first()
        dao.markEmailRead(stored.id)

        assertTrue(dao.getAllEmailsDirect().first().isRead)
    }

    @Test
    fun `settings round trip`() = runTest {
        val dao = db.settingsDao()
        dao.saveSetting(AppSettingEntity("smtp_host", "smtp.example.com"))
        dao.saveSetting(AppSettingEntity("smtp_host", "smtp.updated.com"))

        assertEquals("smtp.updated.com", dao.getSetting("smtp_host")?.value)
    }
}

