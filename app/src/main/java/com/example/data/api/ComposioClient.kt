package com.example.data.api

import android.util.Log
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit
import org.json.JSONArray

object ComposioClient {
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    // Example action to fetch emails using Composio's pre-built integration
    suspend fun fetchRecentGmailMails(apiKey: String): List<EmailData> = withContext(Dispatchers.IO) {
        if (apiKey.isEmpty()) return@withContext emptyList()
        
        // Composio endpoint for tool execution
        val url = "https://backend.composio.dev/api/v1/actions/execute"
        
        // Using Gmail integration action, passing an empty payload to just fetch top messages
        // or a query parameter depending on Composio's exact action signature.
        val requestJson = JSONObject().apply {
            put("action", "GMAIL_GET_MESSAGES")
            put("params", JSONObject().apply {
                put("maxResults", 5)
            })
        }
        
        val mediaType = "application/json; charset=utf-8".toMediaType()
        val body = requestJson.toString().toRequestBody(mediaType)

        val request = Request.Builder()
            .url(url)
            .addHeader("x-api-key", apiKey)
            .post(body)
            .build()
            
        val results = mutableListOf<EmailData>()

        try {
            client.newCall(request).execute().use { response ->
                val responseStr = response.body?.string() ?: ""
                if (response.isSuccessful) {
                    val root = JSONObject(responseStr)
                    val data = root.optJSONObject("data") ?: return@use
                    val messages = data.optJSONArray("messages") ?: return@use
                    
                    for (i in 0 until messages.length()) {
                        val msg = messages.optJSONObject(i) ?: continue
                        results.add(
                            EmailData(
                                sender = msg.optString("sender", "Unknown Sender"),
                                subject = msg.optString("subject", "No Subject"),
                                snippet = msg.optString("snippet", "")
                            )
                        )
                    }
                } else {
                    Log.e("ComposioClient", "Error fetching emails: $responseStr")
                }
            }
        } catch (e: Exception) {
            Log.e("ComposioClient", "Network exception", e)
        }
        
        return@withContext results
    }
}

data class EmailData(
    val sender: String,
    val subject: String,
    val snippet: String
)
