package com.example.data.api

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SmtpClientValidationTest {

    private fun config(
        host: String = "smtp.gmail.com",
        port: Int = 465,
        username: String = "user@gmail.com",
        password: String = "app-password",
        recipient: String = "user@gmail.com"
    ) = SmtpClient.Config(host, port, username, password, recipient)

    @Test
    fun `a fully configured account passes validation`() {
        assertNull(SmtpClient.validate(config()))
    }

    @Test
    fun `blank host is rejected`() {
        assertNotNull(SmtpClient.validate(config(host = "")))
    }

    @Test
    fun `out of range port is rejected`() {
        assertNotNull(SmtpClient.validate(config(port = 0)))
        assertNotNull(SmtpClient.validate(config(port = 70000)))
    }

    @Test
    fun `masked placeholder password is rejected`() {
        val error = SmtpClient.validate(config(password = "••••••••"))
        assertNotNull(error)
        assertTrue(error!!.contains("password", ignoreCase = true))
    }

    @Test
    fun `malformed recipient is rejected`() {
        assertNotNull(SmtpClient.validate(config(recipient = "not-an-email")))
    }
}

