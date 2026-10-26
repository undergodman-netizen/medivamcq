# MEDIVA: Medical & MCQ Learning Platform Architecture & Context

MEDIVA is a high-yield, production-grade, dual-mode medical and general MCQ learning engine designed for active recall, integrated text-to-speech, simulated exam testing, and real-time AI mentoring. This document serves as the high-fidelity source of truth for the system architecture, file taxonomy, data integration channels, and visual rendering mechanics.

---

## 1. High-Level System Architecture

MEDIVA utilizes the modern Model-View-ViewModel (MVVM) structural pattern paired with Jetpack Compose, offering absolute separation of concerns, reactive data flows, and highly-optimized asynchronous thread management.

```text
               ┌────────────────────────────────────────────────────────┐
               │                      COMPOSE UI                        │
               │  - Scaffold / TopAppBar / Navigation Host / Dialogs    │
               │  - LazyColumn, BoxWithConstraints, & Grid Layouts     │
               │  - Selectable Text Containers (Zero Click-Conflict)    │
               │  - Double-Tap Bypassed Clickables (Zero Latency)       │
               │  - Vertical-Drag Gestured Fullscreen Photo Dialog      │
               └───────────────────────────┬────────────────────────────┘
                                           │
                                     (AppState)
                                           │
                                           ▼
               ┌────────────────────────────────────────────────────────┐
               │                    VIEW MODEL LAYER                    │
               │  - StateFlow Unidirectional Flow State Machine         │
               │  - Coroutine Exception Handlers (Session Safeties)     │
               │  - Interactive AI Dynamic Clinician Chat & History     │
               │  - Profile Preferences & Custom Theme Configurations   │
               └───────────────────────────┬────────────────────────────┘
                                           │
                         ┌──────────────────┼──────────────────┐
                         │                  │                  │
                         ▼                  ▼                  ▼
           ┌─────────────┴──────────┐ ┌─────┴──────────┐ ┌─────┴──────────┐
           │       TTS ENGINE       │ │   REPOSITORIES │ │ DATABASE LAYER │
           │ - TtsManager           │ │ - McqRepo      │ │ - SQLite DB    │
           │                        │ │ - AuthRepo     │ │ - Bookmarks    │
           │                        │ │ - SettingsRepo │ │ - Progress DAO │
           │                        │ │ - SyncRepo     │ │ - Room (KSP)   │
           └────────────────────────┘ └────────────────┘ └────────────────┘
```

### 1.1 Unidirectional State Flow (UDF)
The application state is managed reactively through lifecycle-aware flows:
- **AppState Mutability**: Exposed as read-only `StateFlow<AppState>` in `McqViewModel.kt` and consumed reactively in screens via `collectAsStateWithLifecycle()`.
- **Synchronized Transaction Controllers**: Updates to active user statuses, streak metrics, selected quiz indices, and preferences are applied through asynchronous thread configurations using `Dispatchers.IO` to prevent frame render drops.
- **Manual Dependency Injection**: Managed cleanly via `AppContainer.kt` initialized in `MyApplication.kt`. This avoids bulky dependency-injection framework overhead while preserving scalability and unit-testability.

---

## 2. Comprehensive Directory Taxonomy & Class Definitions

### 2.1 Core Screens and Application Layer (`app/src/main/java/com/example/`)
* **`MyApplication.kt`**: Base custom Application class that instantiates the manual DI `AppContainer` upon startup.
* **`AppContainer.kt`**: Dependency-injection registry initializing Room SQLite databases, repositories, sync managers, and AI service singletons.
* **`MainActivity.kt`**: Main launch Activity that configures WindowInsets (Edge-to-Edge support), Compose render chains, and lifecycle boundaries (e.g., pausing TTS engine and committing active session status on app backgrounding).
* **`MainAppLayout.kt`**: Core scaffold layouts configuring navigation hosts, status top bars, custom sheets, and common screen transition structures.
* **`BrainLaunchScreen.kt`**: The default launch gateway (`Screen.Intro` route) and landing hub, allowing quick session jumps, focus customized config adjustments, and stats summaries.
* **`StandardMcqSessionScreen.kt`**: The central quiz player supporting gesture-controlled sheets, active countdown timers, and an interactive AI follow-up chat box.
* **`CustomModuleScreen.kt`**: Customizable session creation engine where users can build tailored mock exams based on specific subjects, topics, sources, and settings.
* **`FlashcardScreen.kt`**: Feature screen dedicated to quick-review flashcards and memorization.
* **`AiMilestonesScreen.kt`**: Interface for displaying AI-generated performance milestones.
* **`AiViewModel.kt`**: Manages the conversational flow and state of the AI mentor interactions.
* **`BookmarksScreen.kt`**: Dedicated interface storing flagged or difficult questions for offline retrieval and quick review.
* **`UserStatisticsScreen.kt`** & **`StatsViewModel.kt`**: Performance matrix displaying completion ratios, subject-wise accuracy indices, progress graphs, and dynamic visual status rings.
* **`AuthScreen.kt`** & **`AuthViewModel.kt`**: Login portal validating Google Identity (OAuth2 client ID) and standard backend authentication setups.
* **`ProfileScreen.kt`** & **`ProfileViewModel.kt`**: Dynamic dashboard handling settings, notification actions, in-app updates, database management, and voice speed/pitch adjustments.
* **`JsonUploadScreen.kt`**: Import dashboard containing tools to parse JSON, verify schemas, view database statistics, and download clinical bank backups.

### 2.2 Shared Utilities and Component Packages (`app/src/main/java/com/example/ / utils/ / ui/`)
* **`HtmlText.kt`** & **`utils/HtmlUtils.kt`**: Processes custom rich-text HTML tags and inline elements dynamically inside Compose layouts.
* **`JsonStreamParser.kt`**: Memory-optimized, token-by-token JSON parser using `android.util.JsonReader` for robust chunked ingestion of huge medical question banks. It decodes embedded base64 assets (images/audio) directly into local file system directories to prevent memory bloat.
* **`McqModels.kt`** & **`AiState.kt`**: Typified models representing question structures, session settings, and asynchronous state wrappers (`Idle`, `Loading`, `Success`, `Error`).
* **`McqUiComponents.kt`**: Reusable interactive cards, dashboards, category rows, and dialog components with zero-latency gesture handling.
* **`ui/theme/`**: Centralized themes (`Theme.kt`, `Color.kt`, `Type.kt`) supporting Light, Dark, and System modes with customized dynamic M3 token mappings.
* **`ui/components/SharedUI.kt`**: Common interactive UI blocks and reusable application widgets.
* **`ui/designsystem/DesignSystem.kt`**: Core design constraints, tokens, and overarching design logic.
* **`ui/components/GlassModifiers.kt`**: Glassmorphism translucent overlays and custom design tokens.
* **`utils/ZipExportUtils.kt`**: Database backup utility implementing gzip compression channels to handle memory-optimized exports.
* **`utils/DateUtils.kt` & `utils/SubjectUtils.kt`**: Helper functions for date formatting, subject categorizations, and generic text transformations.

### 2.3 Repositories & Data Services (`app/src/main/java/com/example/data/ / local/`)
* **`local/RoomDb.kt`**: Room Database configurations, TypeConverters, schemas, migration flows, and DAO definitions (`McqDao`, `BookmarkDao`, `ProgressDao`).
* **`data/McqRepository.kt`**: Local SQLite abstraction coordinating Room transactions, search filtering, and bookmark history.
* **`data/SettingsRepository.kt`**: Storage manager preserving user options, custom Gemini keys, API preferences, and sync parameters.
* **`data/FirestoreSyncManager.kt`**: Manages cloud syncing protocols with Firebase Firestore. It uses safe partitioning blocks and range queries to limit network usage.
* **`data/SyncWorker.kt`**: Periodic background sync engine instantiated via WorkManager to run under active network connections.
* **`data/SyncRepository.kt`**: Abstraction layer orchestrating cloud backup states and sync scheduling.
* **`data/StatsRepository.kt`**: Calculates aggregated progress metrics and tracks session completions.
* **`data/AnalyticsService.kt`**: Records analytical events for active learning time and milestones.
* **`data/FileRepository.kt`**: Handles JSON import flows and local file read/write operations.

### 2.4 Text-To-Speech Engine (`app/src/main/java/com/example/`)
* **`TtsManager.kt`**: Master media manager that coordinates Text-To-Speech (TTS) via Android's native `TextToSpeech` engine. Strips markdown annotations and cleans HTML tags for clear speech synthesis.

---

## 3. SQLite Relational Database Architecture

All question banks and progress logs are persisted locally inside Room's SQLite database (`mcq_database`).

```text
   mcq_questions (Main Bank)            bookmarks (Flagged Questions)
 ┌─────────────────────────┐          ┌────────────────────────────┐
 │ id (INTEGER - PK Auto)  │          │ question (TEXT - PK)       │
 │ subject (TEXT - Indexed)│          │ subject (TEXT)             │
 │ topic (TEXT - Indexed)  ├─────────►│ topic (TEXT)               │
 │ question (TEXT)         │ (On bookmark)│ options (TEXT array)   │
 │ options (TEXT array)    │          │ correctAnswer (TEXT)       │
 │ correctAnswer (TEXT)    │          │ explanation (TEXT)         │
 │ explanation (TEXT)      │          │ aiExplanation (TEXT)       │
 │ aiExplanation (TEXT)    │          │ images (TEXT)              │
 │ images (TEXT)           │          │ audio (TEXT)               │
 │ audio (TEXT)            │          │ sourceUrl (TEXT)           │
 │ sourceUrl (TEXT - Index)│          │ bookmarkedAt (INTEGER)     │
 └─────────────────────────┘          │ lastUpdated (INTEGER)      │
                                      └────────────────────────────┘
```

### 3.1 Relational Database Table Schema Definitions

#### 3.1.1 Questions Entity Table (`mcq_questions`)
- Column: `id` (Integer, Primary Key with Autoincrement)
- Column: `subject` (String, Nullable, Indexed for subject sweeps)
- Column: `topic` (String, Nullable, Indexed for focus reviews)
- Column: `question` (String, Holds the full clinical question)
- Column: `options` (List of Strings, mapped to database text via a JSON Array converter)
- Column: `correctAnswer` (String, holds the text/identifier of the correct option)
- Column: `explanation` (String, Nullable, stores text annotations)
- Column: `aiExplanation` (String, Nullable, logs streamed AI mentor notes)
- Column: `images` (String, Nullable, comma-separated local URLs or file paths)
- Column: `audio` (String, Nullable, path to local spoken files)
- Column: `sourceUrl` (String, Nullable, Indexed, tracks parent file or imported source)

#### 3.1.2 Bookmark Questions Table (`bookmarks`)
- Column: `question` (String, Primary Key)
- Column: `subject`, `topic`, `correctAnswer` (String)
- Column: `options` (List of Strings mapped via JSON Array converter)
- Column: `explanation`, `aiExplanation`, `images`, `audio`, `sourceUrl` (String, Nullable)
- Column: `bookmarkedAt` (Long, holds millisecond timestamp of insertion)
- Column: `lastUpdated` (Long, Unix epoch used for cloud synchronization)

#### 3.1.3 Progress Track Records (`progress_history`)
- Column: `question` (String, Primary Key)
- Column: `subject`, `topic` (String, Nullable)
- Column: `selectedOption` (String, logs the user's latest selection)
- Column: `isCorrect` (Boolean, records if selection was correct)
- Column: `lastUpdated` (Long, Unix epoch used for cloud synchronization)

---

## 4. WorkManager & Firestore Cloud Sync Pipeline

The synchronization architecture minimizes battery drain and network traffic while protecting against Firestore write limitations.

### 4.1 Highly-Optimized Periodic Execution (Every 4 Hours)
The sync pipeline utilizes WorkManager to enforce energy-efficient background execution:
- Scheduled via `enqueueUniquePeriodicWork` using `PeriodicWorkRequestBuilder` with a frequency set to **4 Hours**.
- Constraints enforce execution only when the device is connected to an active network (`NetworkType.CONNECTED`).
- Policy configured to `KEEP` existing tasks, preventing redundant worker schedules from interrupting running routines.

### 4.2 Local Synchronization Toggle Flags
Firestore push and pull requests are managed by static flags inside `FirestoreSyncManager.kt` to allow simple developer overrides:
- `isProgressSyncEnabled = false` (Set to true to activate progress tracking updates on cloud databases)
- `isBookmarksSyncEnabled = false` (Set to true to activate bookmark tracking updates on cloud databases)

### 4.3 Write Crash Mitigation (Suboptimal Batching Fix)
Firestore limits write requests to 500 documents per transaction batch. To prevent write crashes on massive databases:
- Local database diff arrays (`updatedProgress` and `updatedBookmarks`) are partitioned into chunks of **strictly 499 elements or fewer** using Kotlin's `.chunked(499)` API.
- Each chunk is written and committed sequentially (`batch.commit().await()`), ensuring thousands of offline attempts are uploaded atomically without crashing the transaction block.

### 4.4 Network Timeout & Query Optimization (Range Queries)
To limit expensive queries on slower cellular lines:
- Sync pipelines discard individual, high-frequency remote document reads.
- Sync operations utilize Firestore range-based querying using `.whereGreaterThan("lu", lastSyncTime)`.
- Pull operations fetch updated progress and bookmark sets in highly concentrated, network-friendly delta payloads that execute instantly.

---

## 5. UI Rendering & Interactive UX Optimizations

### 5.1 Text Selection Architecture
* **Selectable Blocks**: Standard question titles and AI explanation panels inside interactive quiz cards are wrapped inside Material Design 3 `SelectionContainer` blocks.
* **Gesture Safety Isolation**: This selection layout is structurally compartmentalized to prevent interference with surrounding interactive nodes. Users can freely copy clinical statements, while preserving click listeners on parent components and custom horizontal swipe/elimination gestures.

### 5.2 Zero-Latency Interaction Engine
* **Double-Tap Latency Elimination**: Removed generic, blocking gesture detectors (`detectTapGestures`) on the card surfaces.
* **Instant Flipping Clickables**: Card elements leverage explicit `clickable(...)` modifiers with a bypassed indication and isolated `MutableInteractionSource`. This immediately processes user actions on touch-down, removing standard double-tap threshold latency and providing instantaneous tactile feedback.

### 5.3 Ergonomic, One-Handed Image Dismissal
Clinical scenarios frequently include high-resolution medical imagery. The fullscreen image viewer is configured with:
* An accessible, prominent top-right close icon for quick modal closure.
* Integrated `detectVerticalDragGestures` directly on the image canvas. When scale is at bounds (`1f`), dragging downwards by **more than 100dp** reactively dismisses the dialog. This provides an ergonomic, swipe-to-dismiss mechanical experience optimized for one-handed operation.

### 5.4 Self-Healing Image Rendering
* Coil's `SubcomposeAsyncImage` loads remote clinical illustrations progressively, avoiding layout jumps.
* Uses immediate layout wrappers (`var isError by remember { mutableStateOf(false) }`) that catch network image timeouts or corrupt URLs, collapsing the parent container immediately. This hides failure indicators and maintains highly professional layouts.
