package com.example.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FinancialMessageParserTest {

    @Test
    fun `extracts amount with currency prefix and thousands separators`() {
        assertEquals(1234.56, FinancialMessageParser.extractAmount("INR 1,234.56 debited"))
        assertEquals(2500.0, FinancialMessageParser.extractAmount("Rs.2500 spent on card"))
        assertEquals(99.99, FinancialMessageParser.extractAmount("You paid $99.99"))
    }

    @Test
    fun `extracts amount with trailing currency`() {
        assertEquals(4500.0, FinancialMessageParser.extractAmount("4,500 INR credited"))
    }

    @Test
    fun `returns null when no amount is present`() {
        assertNull(FinancialMessageParser.extractAmount("Your OTP is 123456 for login"))
    }

    @Test
    fun `debit sms maps to an expense`() {
        val record = FinancialMessageParser.parseSms(
            address = "HDFCBK",
            body = "INR 28,500.00 debited towards your Loan EMI for A/c XXXX8992",
            timestamp = 1_000L
        )
        assertNotNull(record)
        assertEquals("EXPENSE", record!!.type)
        assertEquals("LOAN", record.category)
        assertEquals(28500.0, record.amount, 0.001)
    }

    @Test
    fun `credit sms maps to an earning and detects interest`() {
        val record = FinancialMessageParser.parseSms(
            address = "SBIINB",
            body = "Rs.4120 credited as savings interest to your account",
            timestamp = 2_000L
        )
        assertNotNull(record)
        assertEquals("EARNING", record!!.type)
        assertEquals("INTEREST", record.category)
        assertEquals(4120.0, record.amount, 0.001)
    }

    @Test
    fun `sip debit is categorised as SIP`() {
        val record = FinancialMessageParser.parseSms(
            "GROWW",
            "Rs 12000 debited for your monthly SIP installment",
            3_000L
        )
        assertEquals("SIP", record!!.category)
    }

    @Test
    fun `offer sms is actionable even without an amount`() {
        val record = FinancialMessageParser.parseSms(
            "ICICIB",
            "You have a pre-approved home loan offer waiting",
            4_000L
        )
        assertNotNull(record)
        assertEquals("OFFER", record!!.type)
        assertTrue(record.isActionable)
        assertEquals(0.0, record.amount, 0.001)
    }

    @Test
    fun `non financial sms is ignored`() {
        assertNull(FinancialMessageParser.parseSms("MOM", "Dinner at 8?", 5_000L))
    }

    @Test
    fun `transactional sms without an amount is ignored rather than invented`() {
        assertNull(
            FinancialMessageParser.parseSms("BANK", "Your card was debited recently", 6_000L)
        )
    }

    @Test
    fun `bank helpline calls become actionable offers and others are ignored`() {
        assertNotNull(FinancialMessageParser.parseCallLog("1800-123-4567", 7_000L))
        assertNull(FinancialMessageParser.parseCallLog("+919812345678", 7_000L))
    }

    @Test
    fun `dedupe key is stable for the same message and differs across messages`() {
        val first = FinancialMessageParser.parseSms("HDFCBK", "INR 100 debited", 10L)!!
        val repeat = FinancialMessageParser.parseSms("HDFCBK", "INR 100 debited", 10L)!!
        val other = FinancialMessageParser.parseSms("HDFCBK", "INR 200 debited", 10L)!!

        assertEquals(first.dedupeKey, repeat.dedupeKey)
        assertTrue(first.dedupeKey != other.dedupeKey)
    }
}

