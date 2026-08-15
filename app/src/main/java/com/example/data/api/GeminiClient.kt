package com.example.data.api

import android.util.Log
import com.example.BuildConfig
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

object GeminiClient {
    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private const val GEMINI_BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models/"

    suspend fun generateContent(
        prompt: String,
        systemInstruction: String? = null,
        chatHistory: List<Pair<String, String>> = emptyList(), // role to text
        model: String = "gemini-3.1-flash-lite-preview",
        customBaseUrl: String = "",
        customApiKey: String = "",
        customModel: String = ""
    ): String = withContext(Dispatchers.IO) {
        
        // --- CUSTOM OPENAI COMPATIBLE ENDPOINT LOGIC ---
        if (customBaseUrl.isNotEmpty()) {
            try {
                val url = if (customBaseUrl.endsWith("/")) customBaseUrl + "chat/completions" else "$customBaseUrl/chat/completions"
                val requestJson = JSONObject()
                requestJson.put("model", if (customModel.isNotEmpty()) customModel else "gpt-3.5-turbo")
                
                val messagesArray = JSONArray()
                
                if (!systemInstruction.isNullOrEmpty()) {
                    val sysMsg = JSONObject().apply {
                        put("role", "system")
                        put("content", systemInstruction)
                    }
                    messagesArray.put(sysMsg)
                }
                
                for (turn in chatHistory) {
                    val role = if (turn.first.equals("user", ignoreCase = true)) "user" else "assistant"
                    val msgObj = JSONObject().apply {
                        put("role", role)
                        put("content", turn.second)
                    }
                    messagesArray.put(msgObj)
                }
                
                messagesArray.put(JSONObject().apply {
                    put("role", "user")
                    put("content", prompt)
                })
                
                requestJson.put("messages", messagesArray)
                
                val body = requestJson.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
                
                val requestBuilder = Request.Builder().url(url).post(body)
                if (customApiKey.isNotEmpty()) {
                    requestBuilder.addHeader("Authorization", "Bearer $customApiKey")
                }
                
                client.newCall(requestBuilder.build()).execute().use { response ->
                    val responseStr = response.body?.string() ?: ""
                    if (!response.isSuccessful) {
                        return@withContext "Custom AI API Error: HTTP ${response.code}\n$responseStr"
                    }
                    val json = JSONObject(responseStr)
                    val choices = json.optJSONArray("choices")
                    if (choices != null && choices.length() > 0) {
                        val message = choices.getJSONObject(0).optJSONObject("message")
                        return@withContext message?.optString("content", "Empty content") ?: "Parse failed"
                    }
                    return@withContext "Custom AI parsing failed: $responseStr"
                }
            } catch (e: Exception) {
                return@withContext "Custom AI Network Error: ${e.localizedMessage}"
            }
        }

        // --- DEFAULT GEMINI LOGIC ---
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            return@withContext "Error: Gemini API Key is missing. Configure it in Secrets panel or set a Custom API in Settings."
        }

        val url = "$GEMINI_BASE_URL$model:generateContent?key=$apiKey"
        val requestJson = JSONObject()

        val contentsArray = JSONArray()

        for (turn in chatHistory) {
            val contentObj = JSONObject()
            val role = if (turn.first.equals("user", ignoreCase = true)) "user" else "model"
            contentObj.put("role", role)
            
            val partsArray = JSONArray()
            val partObj = JSONObject()
            partObj.put("text", turn.second)
            partsArray.put(partObj)
            
            contentObj.put("parts", partsArray)
            contentsArray.put(contentObj)
        }

        val currentContentObj = JSONObject()
        currentContentObj.put("role", "user")
        val currentPartsArray = JSONArray()
        val currentPartObj = JSONObject()
        currentPartObj.put("text", prompt)
        currentPartsArray.put(currentPartObj)
        currentContentObj.put("parts", currentPartsArray)
        contentsArray.put(currentContentObj)

        requestJson.put("contents", contentsArray)

        if (!systemInstruction.isNullOrEmpty()) {
            val systemInstructionObj = JSONObject()
            val partsArray = JSONArray()
            val partObj = JSONObject()
            partObj.put("text", systemInstruction)
            partsArray.put(partObj)
            systemInstructionObj.put("parts", partsArray)
            requestJson.put("systemInstruction", systemInstructionObj)
        }

        val mediaType = "application/json; charset=utf-8".toMediaType()
        val body = requestJson.toString().toRequestBody(mediaType)

        val request = Request.Builder()
            .url(url)
            .post(body)
            .build()

        try {
            client.newCall(request).execute().use { response ->
                val responseStr = response.body?.string() ?: ""
                if (!response.isSuccessful) {
                    Log.e("GeminiClient", "API Error: $responseStr")
                    return@withContext "Error details: HTTP ${response.code}\n$responseStr"
                }

                val responseJson = JSONObject(responseStr)
                val candidates = responseJson.optJSONArray("candidates")
                if (candidates != null && candidates.length() > 0) {
                    val candidate = candidates.getJSONObject(0)
                    val contentObj = candidate.optJSONObject("content")
                    val parts = contentObj?.optJSONArray("parts")
                    if (parts != null && parts.length() > 0) {
                        return@withContext parts.getJSONObject(0).optString("text", "No text field in response")
                    }
                }
                "Response parsing failed. Raw response: $responseStr"
            }
        } catch (e: Exception) {
            Log.e("GeminiClient", "Network exception", e)
            "Network Error: ${e.localizedMessage ?: "Unknown error"}"
        }
    }
}
