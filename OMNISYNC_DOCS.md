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
*   Integrated with the **Android Health Connect API**, pulling metrics from Samsung Health, Google Fit, Fitbit, etc.
*   **Permissions**: Requested at runtime via `PermissionController.createRequestPermissionResultContract()` (Health Connect uses its own contract, not the standard Android one). The "Grant" button on the Vitals screen triggers it.
*   **No fabricated vitals.** If Health Connect is unavailable, un-permitted, or has no records, the app reports exactly that via a snackbar. A single clearly-labelled `"Sample data (not from a device)"` row is seeded only so the dashboard isn't blank.
*   `sleepScore` is derived from sleep duration against an 8h target rather than randomised.

### 3.2. Financial Ledger Analytics (Native Content Providers)
*   **Mechanism**: `ContentResolver` queries against `Telephony.Sms.CONTENT_URI` and `CallLog.Calls.CONTENT_URI`, guarded by runtime permission checks (the providers are never queried without permission).
*   **Parsing Logic**: Lives in `FinancialMessageParser`, a pure/JVM-testable object. It regex-matches amounts (prefix and suffix currency forms, thousands separators) and categorises into LOAN / SIP / CREDIT_CARD / INTEREST / OFFER.
*   **No invented amounts**: a transactional SMS with no parsable amount is skipped rather than assigned a random figure.
*   **Idempotent**: every record carries a `dedupeKey` backed by a unique index, so re-syncing never duplicates the ledger.

### 3.3. Email Integration (Composio API)
*   Routes through the **Composio API** when an API key is configured in Portals.
*   The local cache is only cleared once fresh data has arrived, so a failed network call can't wipe the inbox.
*   Without a key, a labelled sample inbox is seeded once. Opening a mail marks it read.

### 3.4. AI Chatbot & Insight Generation
*   Aggregates local data (Health + Finance + Emails) into a system prompt.
*   Thread history is read directly (last 15 messages) and the live message is excluded from history so it isn't sent twice.
*   **AI Providers**:
    1.  **Custom OpenAI-Compatible API** (Base URL + key + model — LM Studio, Ollama, Groq, …).
    2.  **Fallback**: Google Gemini via the `GEMINI_API_KEY` secret.

### 3.5. SMTP Daily Summary
*   Implemented in `SmtpClient` on top of JDK sockets — no extra dependency.
*   Supports implicit TLS (465) and STARTTLS (587/25) with AUTH LOGIN, plus dot-stuffing for message bodies.
*   Configuration is validated before dialling out, and the result (success or the actual error) is surfaced in the UI.

## 4. Local Database Schema (Room, version 2)
*   `chat_threads` & `chat_messages`: Persists chatbot history. Thread deletion is transactional (`deleteThreadWithMessages`) so messages are never orphaned; `threadId` is indexed.
*   `health_metrics`: Caches pulled Health Connect data.
*   `financial_records`: Parsed expenses, earnings, and loans. `dedupeKey` has a **unique index** and inserts use `OnConflictStrategy.IGNORE` to keep re-syncs idempotent.
*   `email_items`: Cached emails, indexed by `category`.
*   `app_settings`: Key-Value store for API keys (Composio, Custom AI Base URL, etc.).
*   Obtained via `AppDatabase.getInstance(context)` — a singleton, so the DB is not re-opened on every Activity recreation.

## 5. Testing
Run with `./gradlew :app:testDebugUnitTest`. 24 tests currently pass:
*   `FinancialMessageParserTest` (11) — amount extraction, categorisation, debit/credit/offer mapping, rejection of non-financial and amount-less messages, dedupe-key stability.
*   `AppDatabaseTest` (5) — Room/Robolectric: dedupe enforcement, cascading thread delete, message ordering, mark-as-read, settings upsert.
*   `SmtpClientValidationTest` (5) — config validation rules.
*   Plus the existing Robolectric/screenshot/sample tests (3).

## 6. Cloud & Backend Integration (Future Proofing)
*   **Firebase vs. Self-Hosted**: For a local-first app, **Room** is efficient and free.
*   **Supabase / Qdrant**: Options if multi-device sync or vector search (RAG) is needed later.
*   **Google Drive / Nextcloud**: The Settings toggles currently persist intent only — no OAuth sync is implemented yet, and the UI says so explicitly.

## 7. Known Issues & Setup Requirements
1.  **Health Connect App**: Must be installed (pre-installed on Android 14+) with Samsung Health / Google Fit linked. Permissions are requested from the Vitals screen.
2.  **SMS/Call Permissions**: Google Play restricts `READ_SMS`, so distribute via APK sideloading or F-Droid.
3.  **Composio Config**: Generate a Composio API key and set it in the "Portals" tab to sync real mail.
4.  **SMTP**: Gmail requires an **App Password** (not your account password) with 2FA enabled.
5.  **Release signing**: Only configured when a keystore exists at `KEYSTORE_PATH` (or `my-upload-key.jks`); debug builds use the standard auto-generated debug key.

## 8. Build Requirements
*   Gradle **9.3.1+** (required by AGP 9.1.1) — set in `gradle/wrapper/gradle-wrapper.properties`.
*   JDK 11+ on `PATH`.
*   `GEMINI_API_KEY` supplied via `.env` (see `.env.example`).

## 9. Git Version Control
This project originated in the Google AI Studio environment.
*   **To push to Git**: Use the AI Studio UI -> Settings/Menu -> "Export to GitHub". It does not auto-sync in the background.

## 10. Deployment Flow (Local Testing)
1. `./gradlew :app:assembleDebug` → `app/build/outputs/apk/debug/app-debug.apk`.
2. Install and grant runtime permissions (SMS, Calls), then grant Health Connect access from the Vitals tab.
3. Configure API keys in the Portals tab.
4. Run each sync — every outcome is reported in a snackbar.
