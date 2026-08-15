# OmniSync AI - Technical Architecture & Documentation

## 1. Project Overview
OmniSync AI is a local-first, privacy-focused Android application designed to aggregate and analyze personal data (Health, Finances, and Communications) into a unified dashboard. It leverages local intelligence and user-configured AI endpoints to provide actionable insights without unnecessarily exposing data to third-party tracking.

## 2. Core Architecture
The app follows a modern Android architecture:
*   **UI Layer**: Jetpack Compose (Material Design 3). Single Activity (`MainActivity.kt`) hosting multiple Compose screens navigated via `AnimatedContent`.
*   **State Management**: `ViewModel` (`OmniSyncViewModel.kt`) utilizing `StateFlow` and Coroutines for asynchronous, non-blocking UI updates.
*   **Data/Repository Layer**: `OmniSyncRepository.kt` acts as the single source of truth, orchestrating data fetches from local databases, Android native providers (SMS/Calls, Health Connect), and external APIs (Composio, Custom AI).
*   **Persistence Layer**: Room Database (SQLite) with DAO patterns for storing Chat History, Cached Health Metrics, Parsed Financial Records, Settings, and Emails.

## 3. Feature Modules & Real Integrations

### 3.1. Health & Wearable Metrics (Android Health Connect)
*   **Previous State**: Simulated BLE scanning and mock data generation.
*   **Current State**: Integrated with **Android Health Connect API**. This acts as a universal hub, pulling metrics directly from Samsung Health, Google Fit, Fitbit, etc., natively on the device.
*   **Permissions**: Requires specialized Health Connect read permissions (`READ_HEART_RATE`, `READ_STEPS`, `READ_SLEEP`).

### 3.2. Financial Ledger Analytics (Native Content Providers)
*   **Mechanism**: Uses Android `ContentResolver` to query `Telephony.Sms.CONTENT_URI` and `CallLog.Calls.CONTENT_URI`.
*   **Parsing Logic**: Scans for transactional keywords (debit, credit, EMI, SIP) and regex-matches amounts to categorize financial flows locally. No bank login required.

### 3.3. Email Integration (Composio API)
*   **Previous State**: Simulated local mock emails.
*   **Current State**: Designed to route through **Composio API**. By providing a Composio API Key, the app can interact with authenticated tools (Gmail, Outlook) to read emails and send SMTP summaries.

### 3.4. AI Chatbot & Insight Generation
*   **Primary Logic**: Aggregates local data (Health + Finance + Emails) into a structured prompt, injecting it as a system prompt to the AI.
*   **AI Providers**: 
    1.  **Custom OpenAI-Compatible API**: Allows the user to set a Base URL (e.g., Local LLM, LM Studio, Ollama, Groq) and API Key.
    2.  **Fallback**: Google Gemini AI (via AI Studio injected secrets).

## 4. Local Database Schema (Room)
*   `chat_threads` & `chat_messages`: Persists chatbot history for context retrieval.
*   `health_metrics`: Caches pulled Health Connect data.
*   `financial_records`: Stores parsed expenses, earnings, and loans.
*   `email_items`: Caches fetched emails from Composio.
*   `app_settings`: Key-Value store for API keys (Composio, Custom AI Base URL, etc.).

## 5. Cloud & Backend Integration (Future Proofing)
*   **Firebase vs. Self-Hosted**: Firebase can become costly at scale. For a local-first app, **Room Database** is highly efficient and costs nothing.
*   **Supabase / Qdrant**: If multi-device sync or vector-search (RAG) is needed in the future, Supabase (Postgres) and Qdrant (Vector DB) are excellent open-source, self-hostable alternatives to Firebase. Currently, the app remains 100% offline-first for primary storage.
*   **Google Drive**: Target for JSON/SQLite backup exports via OAuth.

## 6. Known Issues & Setup Requirements
1.  **Health Connect App**: The user must have the "Health Connect" app installed on their Android device (pre-installed on Android 14+) and have linked their Samsung Health / Google Fit to it.
2.  **SMS/Call Permissions**: Google Play restricts `READ_SMS`. This app must be distributed via APK (sideloading) or F-Droid for these permissions to function without restriction.
3.  **Composio Config**: Users must generate a Composio API key and configure it in the "Portals" settings tab.

## 7. Git Version Control
This project exists within the Google AI Studio environment. 
*   **To push to Git**: Use the AI Studio UI -> Settings/Menu -> "Export to GitHub" to push this codebase directly to your repository. It does not auto-sync in the background.

## 8. Deployment Flow (Local Testing)
1. Configure API keys in Settings.
2. Grant all runtime permissions (SMS, Calls, Health).
3. Test sync flows.
4. Export APK for local Android installation.
