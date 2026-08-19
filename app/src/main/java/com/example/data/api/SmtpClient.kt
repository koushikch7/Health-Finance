package com.example.data.api

import android.util.Base64
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.Socket
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory

/**
 * Minimal SMTP client implemented on top of the JDK socket APIs so that the app can actually
 * deliver the daily summary mail without pulling in a mail framework.
 *
 * Supports implicit TLS (port 465) and STARTTLS (ports 587/25) with AUTH LOGIN.
 */
object SmtpClient {

    data class Config(
        val host: String,
        val port: Int,
        val username: String,
        val password: String,
        val recipient: String
    )

    fun validate(config: Config): String? = when {
        config.host.isBlank() -> "SMTP host is not configured."
        config.port !in 1..65535 -> "SMTP port '${config.port}' is invalid."
        config.username.isBlank() -> "SMTP sender email is not configured."
        config.password.isBlank() || config.password.all { it == '•' } ->
            "SMTP password/app-token is not configured."
        !config.recipient.contains("@") -> "Recipient address '${config.recipient}' is invalid."
        else -> null
    }

    suspend fun send(config: Config, subject: String, body: String): Result<String> =
        withContext(Dispatchers.IO) {
            validate(config)?.let { return@withContext Result.failure(IllegalArgumentException(it)) }

            var socket: Socket? = null
            try {
                socket = if (config.port == 465) {
                    (SSLSocketFactory.getDefault() as SSLSocketFactory)
                        .createSocket(config.host, config.port) as SSLSocket
                } else {
                    Socket(config.host, config.port)
                }
                socket.soTimeout = 30_000

                var session = Session(socket)
                session.expect("220")
                session.command("EHLO omnisync", "250")

                if (config.port != 465) {
                    session.command("STARTTLS", "220")
                    val tls = (SSLSocketFactory.getDefault() as SSLSocketFactory)
                        .createSocket(socket, config.host, config.port, true) as SSLSocket
                    tls.startHandshake()
                    socket = tls
                    session = Session(tls)
                    session.command("EHLO omnisync", "250")
                }

                session.command("AUTH LOGIN", "334")
                session.command(config.username.base64(), "334")
                session.command(config.password.base64(), "235")

                session.command("MAIL FROM:<${config.username}>", "250")
                session.command("RCPT TO:<${config.recipient}>", "250")
                session.command("DATA", "354")

                val message = buildString {
                    append("From: OmniSync AI <${config.username}>\r\n")
                    append("To: <${config.recipient}>\r\n")
                    append("Subject: $subject\r\n")
                    append("MIME-Version: 1.0\r\n")
                    append("Content-Type: text/plain; charset=UTF-8\r\n")
                    append("\r\n")
                    // Dot-stuffing keeps a line starting with "." from terminating the message.
                    append(body.replace("\r\n", "\n").lines().joinToString("\r\n") {
                        if (it.startsWith(".")) ".$it" else it
                    })
                    append("\r\n.")
                }
                session.command(message, "250")
                session.commandIgnoringReply("QUIT")

                Result.success("Daily summary delivered to ${config.recipient}.")
            } catch (e: Exception) {
                Log.e("SmtpClient", "SMTP dispatch failed", e)
                Result.failure(e)
            } finally {
                runCatching { socket?.close() }
            }
        }

    private fun String.base64(): String =
        Base64.encodeToString(toByteArray(Charsets.UTF_8), Base64.NO_WRAP)

    private class Session(socket: Socket) {
        private val reader = BufferedReader(InputStreamReader(socket.getInputStream(), Charsets.UTF_8))
        private val writer = OutputStreamWriter(socket.getOutputStream(), Charsets.UTF_8)

        fun expect(code: String): String {
            val response = readResponse()
            if (!response.startsWith(code)) {
                throw IllegalStateException("SMTP server replied: $response")
            }
            return response
        }

        fun command(payload: String, expectedCode: String) {
            writer.write("$payload\r\n")
            writer.flush()
            expect(expectedCode)
        }

        fun commandIgnoringReply(payload: String) {
            runCatching {
                writer.write("$payload\r\n")
                writer.flush()
            }
        }

        private fun readResponse(): String {
            val builder = StringBuilder()
            while (true) {
                val line = reader.readLine() ?: break
                builder.append(line)
                // Multi-line replies use "250-", the final line uses "250 ".
                if (line.length < 4 || line[3] != '-') break
                builder.append('\n')
            }
            return builder.toString()
        }
    }
}

