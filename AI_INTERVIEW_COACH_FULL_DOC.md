# AI Interview Coach — Full Implementation Documentation
### From Zero to Play Store | Vikash (Android Engineer, 2 YOE)

---

## Table of Contents
1. [Project Overview](#1-project-overview)
2. [Full System Architecture](#2-full-system-architecture)
3. [Android Project Structure](#3-android-project-structure)
4. [Database Schema (Room + PostgreSQL)](#4-database-schema)
5. [Android Implementation — Every File](#5-android-implementation)
6. [Spring Boot Backend — Every File](#6-spring-boot-backend)
7. [Claude API Integration](#7-claude-api-integration)
8. [Offline-First Sync Strategy](#8-offline-first-sync)
9. [WebSocket / STOMP Real-Time Flow](#9-websocket-stomp)
10. [Authentication (JWT + Refresh Token)](#10-authentication)
11. [Testing Strategy](#11-testing)
12. [CI/CD + Docker + Railway Deploy](#12-devops)
13. [Play Store Release Checklist](#13-play-store-release)
14. [Week-by-Week Sprint Plan](#14-sprint-plan)

---

## 1. Project Overview

**App name:** InterviewAce (Play Store listing name)
**Package:** `com.vikash.interviewace`
**Target users:** Android/SDE job seekers in India preparing for technical interviews
**Monetization:** Free (3 sessions/month) → ₹99/month subscription

### Core Features
- AI conducts mock interview via voice (STT) and speaks questions aloud (TTS)
- Two separate Claude roles: Interviewer + Evaluator
- Real-time session over WebSocket/STOMP
- Offline-first: all sessions saved in Room, synced to PostgreSQL
- Score history with charts (MPAndroidChart)
- Daily reminder via FCM
- Foreground service keeps mic alive during 30-min session

### Tech Stack Summary
| Layer | Technology |
|---|---|
| Android UI | Jetpack Compose, Material3 |
| Android Architecture | MVVM + Clean Architecture, multi-module |
| DI | Hilt |
| Local DB | Room |
| Network | Retrofit + OkHttp + STOMP (krossbow) |
| Background | WorkManager, Foreground Service |
| Voice | SpeechRecognizer (STT), TextToSpeech (TTS) |
| Auth | JWT + Refresh Token (DataStore encrypted) |
| Backend | Spring Boot 3.x (Java 17) |
| Backend DB | PostgreSQL |
| Real-time | Spring WebSocket + STOMP |
| AI | Claude claude-sonnet-4-6 API |
| DevOps | GitHub Actions, Docker, Railway |
| Monitoring | Firebase Crashlytics, Firebase Performance |

---

## 2. Full System Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                    ANDROID APP                              │
│                                                             │
│  :feature:auth   :feature:session   :feature:history       │
│       │                │                   │               │
│       └────────────────┴───────────────────┘               │
│                        │                                   │
│              :core:domain (UseCases)                       │
│                        │                                   │
│        :core:data (Repositories + Room + Retrofit)         │
│                        │                                   │
│              :core:network (API + WebSocket)               │
│                        │                                   │
└────────────────────────┼────────────────────────────────────┘
                         │  HTTPS + WSS
┌────────────────────────┼────────────────────────────────────┐
│              SPRING BOOT BACKEND                           │
│                                                             │
│   AuthController   SessionController   EvalController      │
│         │                │                   │             │
│   AuthService      SessionService      EvalService         │
│         │                │                   │             │
│   UserRepository   SessionRepository   QuestionRepository  │
│                        │                                   │
│              PostgreSQL (Railway)                          │
│                        │                                   │
│              Claude API (claude-sonnet-4-6)                │
└─────────────────────────────────────────────────────────────┘
```

### Data Flow: One Interview Session
```
User taps "Start Interview"
    → Android: create local Room session (status=PENDING)
    → Android: open WebSocket to /ws/session/{sessionId}
    → Backend: SessionService creates server-side session
    → Backend: calls Claude API (Interviewer role) → gets first question
    → Backend: pushes question over STOMP to /topic/session/{id}
    → Android: TTS speaks the question aloud
    → User answers by voice → SpeechRecognizer → text
    → Android: sends answer over STOMP to /app/session/{id}/answer
    → Backend: calls Claude API (Evaluator role) → gets score + feedback
    → Backend: pushes score over STOMP to /topic/session/{id}/score
    → Android: updates Room session with score
    → WorkManager: syncs final session to PostgreSQL when online
```

---

## 3. Android Project Structure

### Gradle modules
```
interviewace/
├── app/                          ← thin shell, DI graph root
├── feature/
│   ├── auth/                     ← login, register screens
│   ├── session/                  ← live interview screen
│   └── history/                  ← past sessions, score charts
├── core/
│   ├── domain/                   ← UseCases, domain models
│   ├── data/                     ← Repositories impl, Room, Retrofit
│   ├── network/                  ← ApiService, WebSocketClient
│   ├── database/                 ← Room DB, DAOs, Entities
│   ├── datastore/                ← EncryptedDataStore (JWT storage)
│   └── ui/                       ← shared Compose components, theme
└── build-logic/                  ← shared Gradle conventions
```

### settings.gradle.kts
```kotlin
rootProject.name = "InterviewAce"
include(
    ":app",
    ":feature:auth",
    ":feature:session",
    ":feature:history",
    ":core:domain",
    ":core:data",
    ":core:network",
    ":core:database",
    ":core:datastore",
    ":core:ui"
)
```

### app/build.gradle.kts
```kotlin
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.hilt)
    alias(libs.plugins.google.services)
    alias(libs.plugins.crashlytics)
    kotlin("kapt")
}

android {
    namespace = "com.vikash.interviewace"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.vikash.interviewace"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"
    }
    buildFeatures { compose = true }
    composeOptions { kotlinCompilerExtensionVersion = "1.5.8" }
}

dependencies {
    implementation(project(":feature:auth"))
    implementation(project(":feature:session"))
    implementation(project(":feature:history"))
    implementation(project(":core:domain"))
    implementation(project(":core:data"))
    implementation(project(":core:ui"))
    implementation(libs.hilt.android)
    kapt(libs.hilt.compiler)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.crashlytics)
    implementation(libs.firebase.messaging)
    implementation(libs.firebase.perf)
}
```

### libs.versions.toml (key dependencies)
```toml
[versions]
kotlin = "1.9.22"
compose-bom = "2024.02.02"
hilt = "2.51"
room = "2.6.1"
retrofit = "2.9.0"
okhttp = "4.12.0"
krossbow = "6.0.0"        # STOMP WebSocket client
datastore = "1.0.0"
workmanager = "2.9.0"
mpandroidchart = "v3.1.0"
firebase-bom = "32.7.4"

[libraries]
compose-bom = { group="androidx.compose", name="compose-bom", version.ref="compose-bom" }
compose-ui = { group="androidx.compose.ui", name="ui" }
compose-material3 = { group="androidx.compose.material3", name="material3" }
hilt-android = { group="com.google.dagger", name="hilt-android", version.ref="hilt" }
hilt-compiler = { group="com.google.dagger", name="hilt-android-compiler", version.ref="hilt" }
room-runtime = { group="androidx.room", name="room-runtime", version.ref="room" }
room-ktx = { group="androidx.room", name="room-ktx", version.ref="room" }
room-compiler = { group="androidx.room", name="room-compiler", version.ref="room" }
retrofit = { group="com.squareup.retrofit2", name="retrofit", version.ref="retrofit" }
retrofit-gson = { group="com.squareup.retrofit2", name="converter-gson", version.ref="retrofit" }
okhttp = { group="com.squareup.okhttp3", name="okhttp", version.ref="okhttp" }
krossbow-stomp = { group="org.hildan.krossbow", name="krossbow-stomp-core", version.ref="krossbow" }
krossbow-okhttp = { group="org.hildan.krossbow", name="krossbow-websocket-okhttp", version.ref="krossbow" }
datastore-prefs = { group="androidx.datastore", name="datastore-preferences", version.ref="datastore" }
workmanager = { group="androidx.work", name="work-runtime-ktx", version.ref="workmanager" }
mpandroidchart = { group="com.github.PhilJay", name="MPAndroidChart", version.ref="mpandroidchart" }
firebase-bom = { group="com.google.firebase", name="firebase-bom", version.ref="firebase-bom" }
firebase-crashlytics = { group="com.google.firebase", name="firebase-crashlytics-ktx" }
firebase-messaging = { group="com.google.firebase", name="firebase-messaging-ktx" }
firebase-perf = { group="com.google.firebase", name="firebase-perf-ktx" }
```

---

## 4. Database Schema

### Room Entities (Android)

```kotlin
// core/database/src/main/java/com/vikash/interviewace/database/entity/

// SessionEntity.kt
@Entity(tableName = "sessions")
data class SessionEntity(
    @PrimaryKey val id: String,           // UUID, generated on device
    val userId: String,
    val role: String,                     // "ANDROID_DEVELOPER", "SDE_1", etc.
    val status: String,                   // PENDING, IN_PROGRESS, COMPLETED, SYNCED
    val totalScore: Int = 0,
    val maxScore: Int = 0,
    val durationSeconds: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val syncedAt: Long? = null,
    val isSynced: Boolean = false
)

// QuestionAnswerEntity.kt
@Entity(
    tableName = "question_answers",
    foreignKeys = [ForeignKey(
        entity = SessionEntity::class,
        parentColumns = ["id"],
        childColumns = ["sessionId"],
        onDelete = ForeignKey.CASCADE
    )]
)
data class QuestionAnswerEntity(
    @PrimaryKey val id: String,
    val sessionId: String,
    val questionText: String,
    val answerText: String,
    val score: Int,                       // 0-10 per question
    val feedback: String,
    val category: String,                 // "DSA", "SYSTEM_DESIGN", "ANDROID", "BEHAVIORAL"
    val orderIndex: Int,
    val createdAt: Long = System.currentTimeMillis()
)

// UserEntity.kt
@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey val id: String,
    val name: String,
    val email: String,
    val targetRole: String,
    val experienceYears: Int,
    val sessionsThisMonth: Int = 0,
    val isPremium: Boolean = false
)
```

### Room DAOs

```kotlin
// SessionDao.kt
@Dao
interface SessionDao {
    @Query("SELECT * FROM sessions ORDER BY createdAt DESC")
    fun getAllSessions(): Flow<List<SessionEntity>>

    @Query("SELECT * FROM sessions WHERE isSynced = 0")
    suspend fun getUnsyncedSessions(): List<SessionEntity>

    @Query("SELECT * FROM sessions WHERE id = :id")
    suspend fun getSessionById(id: String): SessionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: SessionEntity)

    @Update
    suspend fun updateSession(session: SessionEntity)

    @Query("UPDATE sessions SET isSynced = 1, syncedAt = :time WHERE id = :id")
    suspend fun markSynced(id: String, time: Long = System.currentTimeMillis())

    @Query("SELECT COUNT(*) FROM sessions WHERE status = 'COMPLETED'")
    fun getTotalCompletedSessions(): Flow<Int>

    @Query("""
        SELECT AVG(totalScore * 100.0 / maxScore) 
        FROM sessions 
        WHERE status = 'COMPLETED' AND maxScore > 0
    """)
    fun getAverageScorePercent(): Flow<Float?>
}

// QuestionAnswerDao.kt
@Dao
interface QuestionAnswerDao {
    @Query("SELECT * FROM question_answers WHERE sessionId = :sessionId ORDER BY orderIndex")
    suspend fun getQAForSession(sessionId: String): List<QuestionAnswerEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertQA(qa: QuestionAnswerEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(qaList: List<QuestionAnswerEntity>)

    @Query("""
        SELECT category, AVG(score) as avgScore 
        FROM question_answers 
        GROUP BY category
    """)
    fun getCategoryScores(): Flow<List<CategoryScore>>
}

data class CategoryScore(val category: String, val avgScore: Float)
```

### Room Database

```kotlin
// InterviewAceDatabase.kt
@Database(
    entities = [SessionEntity::class, QuestionAnswerEntity::class, UserEntity::class],
    version = 1,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class InterviewAceDatabase : RoomDatabase() {
    abstract fun sessionDao(): SessionDao
    abstract fun questionAnswerDao(): QuestionAnswerDao
    abstract fun userDao(): UserDao
}
```

### PostgreSQL Schema (Backend)

```sql
-- users
CREATE TABLE users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email VARCHAR(255) UNIQUE NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    name VARCHAR(255) NOT NULL,
    target_role VARCHAR(100),
    experience_years INT DEFAULT 0,
    is_premium BOOLEAN DEFAULT FALSE,
    sessions_this_month INT DEFAULT 0,
    created_at TIMESTAMP DEFAULT NOW()
);

-- sessions
CREATE TABLE sessions (
    id UUID PRIMARY KEY,               -- same UUID as Room, device-generated
    user_id UUID REFERENCES users(id),
    role VARCHAR(100),
    status VARCHAR(50) DEFAULT 'PENDING',
    total_score INT DEFAULT 0,
    max_score INT DEFAULT 0,
    duration_seconds INT DEFAULT 0,
    created_at TIMESTAMP DEFAULT NOW(),
    completed_at TIMESTAMP
);

-- question_answers
CREATE TABLE question_answers (
    id UUID PRIMARY KEY,
    session_id UUID REFERENCES sessions(id) ON DELETE CASCADE,
    question_text TEXT NOT NULL,
    answer_text TEXT,
    score INT DEFAULT 0,
    feedback TEXT,
    category VARCHAR(50),
    order_index INT,
    created_at TIMESTAMP DEFAULT NOW()
);

-- refresh_tokens
CREATE TABLE refresh_tokens (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID REFERENCES users(id),
    token VARCHAR(512) UNIQUE NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP DEFAULT NOW()
);

-- Indexes
CREATE INDEX idx_sessions_user_id ON sessions(user_id);
CREATE INDEX idx_qa_session_id ON question_answers(session_id);
CREATE INDEX idx_refresh_tokens_token ON refresh_tokens(token);
```

---

## 5. Android Implementation

### 5.1 EncryptedDataStore (JWT storage)

```kotlin
// core/datastore/src/main/java/.../AuthDataStore.kt
@Singleton
class AuthDataStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val encryptedPrefs = EncryptedSharedPreferences.create(
        context,
        "auth_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun saveTokens(accessToken: String, refreshToken: String) {
        encryptedPrefs.edit {
            putString("access_token", accessToken)
            putString("refresh_token", refreshToken)
        }
    }

    fun getAccessToken(): String? = encryptedPrefs.getString("access_token", null)
    fun getRefreshToken(): String? = encryptedPrefs.getString("refresh_token", null)
    fun clearTokens() = encryptedPrefs.edit { clear() }
}
```

### 5.2 Retrofit + OkHttp Setup with JWT Interceptor

```kotlin
// core/network/src/main/java/.../NetworkModule.kt
@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides @Singleton
    fun provideOkHttpClient(
        authDataStore: AuthDataStore,
        tokenRefresher: TokenRefresher
    ): OkHttpClient = OkHttpClient.Builder()
        .addInterceptor { chain ->
            val token = authDataStore.getAccessToken()
            val request = chain.request().newBuilder()
                .addHeader("Authorization", "Bearer $token")
                .build()
            val response = chain.proceed(request)
            if (response.code == 401) {
                response.close()
                // Refresh token and retry
                val newToken = runBlocking { tokenRefresher.refresh() }
                if (newToken != null) {
                    val retryRequest = chain.request().newBuilder()
                        .addHeader("Authorization", "Bearer $newToken")
                        .build()
                    chain.proceed(retryRequest)
                } else response
            } else response
        }
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)    // AI responses can be slow
        .build()

    @Provides @Singleton
    fun provideRetrofit(okHttpClient: OkHttpClient): Retrofit = Retrofit.Builder()
        .baseUrl(BuildConfig.API_BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
}
```

### 5.3 WebSocket Client (STOMP via Krossbow)

```kotlin
// core/network/src/main/java/.../WebSocketClient.kt
@Singleton
class WebSocketClient @Inject constructor(
    private val authDataStore: AuthDataStore
) {
    private val wsClient = OkHttpWebSocketClient()
    private val stompClient = StompClient(wsClient)
    private var session: StompSession? = null

    suspend fun connect(): Result<Unit> = runCatching {
        val token = authDataStore.getAccessToken() ?: throw IllegalStateException("No token")
        session = stompClient.connect(
            url = "${BuildConfig.WS_BASE_URL}/ws",
            customStompConnectHeaders = mapOf("Authorization" to "Bearer $token")
        )
    }

    suspend fun subscribeToSession(
        sessionId: String,
        onMessage: (SessionMessage) -> Unit
    ) {
        session?.subscribe("/topic/session/$sessionId")
            ?.collect { frame ->
                val message = Gson().fromJson(frame.bodyAsText, SessionMessage::class.java)
                onMessage(message)
            }
    }

    suspend fun sendAnswer(sessionId: String, answer: String) {
        session?.sendText(
            destination = "/app/session/$sessionId/answer",
            body = Gson().toJson(AnswerPayload(answerText = answer))
        )
    }

    suspend fun disconnect() {
        session?.disconnect()
        session = null
    }
}

data class SessionMessage(
    val type: String,       // "QUESTION", "SCORE", "SESSION_END", "ERROR"
    val question: String? = null,
    val score: Int? = null,
    val feedback: String? = null,
    val category: String? = null,
    val isLastQuestion: Boolean = false
)

data class AnswerPayload(val answerText: String)
```

### 5.4 Repository Pattern

```kotlin
// core/data/src/main/java/.../SessionRepository.kt
interface SessionRepository {
    fun getAllSessions(): Flow<List<Session>>
    suspend fun createSession(role: String): Session
    suspend fun updateSessionScore(sessionId: String, score: Int, maxScore: Int)
    suspend fun completeSession(sessionId: String)
    fun getAverageScore(): Flow<Float?>
    fun getTotalSessions(): Flow<Int>
}

// SessionRepositoryImpl.kt
@Singleton
class SessionRepositoryImpl @Inject constructor(
    private val sessionDao: SessionDao,
    private val questionAnswerDao: QuestionAnswerDao,
    private val apiService: ApiService,
    private val syncScheduler: SyncScheduler
) : SessionRepository {

    override fun getAllSessions(): Flow<List<Session>> =
        sessionDao.getAllSessions().map { entities ->
            entities.map { it.toDomain() }
        }

    override suspend fun createSession(role: String): Session {
        val session = Session(
            id = UUID.randomUUID().toString(),
            role = role,
            status = SessionStatus.PENDING,
            createdAt = System.currentTimeMillis()
        )
        // Write to Room immediately (offline-first)
        sessionDao.insertSession(session.toEntity())
        // Schedule sync
        syncScheduler.scheduleSync()
        return session
    }

    override suspend fun completeSession(sessionId: String) {
        sessionDao.updateSession(
            sessionDao.getSessionById(sessionId)!!
                .copy(status = "COMPLETED")
        )
        syncScheduler.scheduleSync()
    }
}
```

### 5.5 WorkManager Sync

```kotlin
// core/data/src/main/java/.../SyncWorker.kt
@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val sessionDao: SessionDao,
    private val questionAnswerDao: QuestionAnswerDao,
    private val apiService: ApiService
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            val unsyncedSessions = sessionDao.getUnsyncedSessions()
            unsyncedSessions.forEach { session ->
                val qaList = questionAnswerDao.getQAForSession(session.id)
                apiService.syncSession(
                    SyncRequest(
                        session = session.toDto(),
                        questionAnswers = qaList.map { it.toDto() }
                    )
                )
                sessionDao.markSynced(session.id)
            }
            Result.success()
        } catch (e: Exception) {
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }
}

// SyncScheduler.kt
@Singleton
class SyncScheduler @Inject constructor(
    @ApplicationContext private val context: Context
) {
    fun scheduleSync() {
        val request = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.SECONDS)
            .addTag("session_sync")
            .build()
        WorkManager.getInstance(context)
            .enqueueUniqueWork("session_sync", ExistingWorkPolicy.APPEND_OR_REPLACE, request)
    }
}
```

### 5.6 Foreground Service (keep mic + WebSocket alive)

```kotlin
// feature/session/src/main/java/.../InterviewSessionService.kt
@AndroidEntryPoint
class InterviewSessionService : Service() {

    @Inject lateinit var webSocketClient: WebSocketClient
    @Inject lateinit var sessionRepository: SessionRepository

    private val binder = SessionBinder()
    private var sessionJob: Job? = null
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _sessionState = MutableStateFlow<SessionState>(SessionState.Idle)
    val sessionState: StateFlow<SessionState> = _sessionState.asStateFlow()

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val sessionId = intent?.getStringExtra("SESSION_ID") ?: return START_NOT_STICKY
        startForeground(NOTIFICATION_ID, buildNotification())
        startSession(sessionId)
        return START_STICKY
    }

    private fun startSession(sessionId: String) {
        sessionJob = serviceScope.launch {
            webSocketClient.connect().onFailure {
                _sessionState.value = SessionState.Error("Connection failed")
                return@launch
            }
            _sessionState.value = SessionState.Connected
            webSocketClient.subscribeToSession(sessionId) { message ->
                handleMessage(sessionId, message)
            }
        }
    }

    private fun handleMessage(sessionId: String, message: SessionMessage) {
        when (message.type) {
            "QUESTION" -> _sessionState.value = SessionState.QuestionReceived(
                question = message.question!!,
                category = message.category!!
            )
            "SCORE" -> _sessionState.value = SessionState.ScoreReceived(
                score = message.score!!,
                feedback = message.feedback!!
            )
            "SESSION_END" -> {
                serviceScope.launch { sessionRepository.completeSession(sessionId) }
                _sessionState.value = SessionState.SessionEnded
                stopSelf()
            }
        }
    }

    suspend fun sendAnswer(sessionId: String, answer: String) {
        webSocketClient.sendAnswer(sessionId, answer)
        _sessionState.value = SessionState.AnswerSent
    }

    private fun buildNotification(): Notification {
        val channel = NotificationChannel(
            CHANNEL_ID, "Interview Session",
            NotificationManager.IMPORTANCE_LOW
        )
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Interview in progress")
            .setContentText("Tap to return to your session")
            .setSmallIcon(R.drawable.ic_mic)
            .setOngoing(true)
            .build()
    }

    inner class SessionBinder : Binder() {
        fun getService() = this@InterviewSessionService
    }

    override fun onBind(intent: Intent) = binder

    override fun onDestroy() {
        sessionJob?.cancel()
        serviceScope.launch { webSocketClient.disconnect() }
        serviceScope.cancel()
        super.onDestroy()
    }

    companion object {
        const val NOTIFICATION_ID = 1001
        const val CHANNEL_ID = "interview_session"
    }
}

sealed class SessionState {
    object Idle : SessionState()
    object Connected : SessionState()
    data class QuestionReceived(val question: String, val category: String) : SessionState()
    object AnswerSent : SessionState()
    data class ScoreReceived(val score: Int, val feedback: String) : SessionState()
    object SessionEnded : SessionState()
    data class Error(val message: String) : SessionState()
}
```

### 5.7 ViewModel — Interview Session

```kotlin
// feature/session/src/main/java/.../SessionViewModel.kt
@HiltViewModel
class SessionViewModel @Inject constructor(
    private val sessionRepository: SessionRepository,
    private val createSessionUseCase: CreateSessionUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(SessionUiState())
    val uiState: StateFlow<SessionUiState> = _uiState.asStateFlow()

    private var sessionService: InterviewSessionService? = null
    private var serviceConnection: ServiceConnection? = null

    fun startSession(role: String, context: Context) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val session = sessionRepository.createSession(role)
            bindToService(context, session.id)
            _uiState.update { it.copy(
                isLoading = false,
                sessionId = session.id,
                phase = SessionPhase.CONNECTING
            )}
        }
    }

    private fun bindToService(context: Context, sessionId: String) {
        val intent = Intent(context, InterviewSessionService::class.java)
            .putExtra("SESSION_ID", sessionId)

        serviceConnection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, binder: IBinder) {
                sessionService = (binder as InterviewSessionService.SessionBinder).getService()
                observeServiceState()
            }
            override fun onServiceDisconnected(name: ComponentName) {
                sessionService = null
            }
        }
        context.startForegroundService(intent)
        context.bindService(intent, serviceConnection!!, Context.BIND_AUTO_CREATE)
    }

    private fun observeServiceState() {
        viewModelScope.launch {
            sessionService?.sessionState?.collect { state ->
                when (state) {
                    is SessionState.QuestionReceived -> _uiState.update { it.copy(
                        currentQuestion = state.question,
                        currentCategory = state.category,
                        phase = SessionPhase.LISTENING,
                        questionNumber = it.questionNumber + 1
                    )}
                    is SessionState.ScoreReceived -> _uiState.update { it.copy(
                        lastScore = state.score,
                        lastFeedback = state.feedback,
                        phase = SessionPhase.FEEDBACK,
                        totalScore = it.totalScore + state.score
                    )}
                    is SessionState.SessionEnded -> _uiState.update { it.copy(
                        phase = SessionPhase.COMPLETED
                    )}
                    is SessionState.Error -> _uiState.update { it.copy(
                        error = state.message
                    )}
                    else -> {}
                }
            }
        }
    }

    fun submitAnswer(sessionId: String, answer: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(
                userAnswer = answer,
                phase = SessionPhase.EVALUATING
            )}
            sessionService?.sendAnswer(sessionId, answer)
        }
    }
}

data class SessionUiState(
    val isLoading: Boolean = false,
    val sessionId: String? = null,
    val phase: SessionPhase = SessionPhase.IDLE,
    val currentQuestion: String = "",
    val currentCategory: String = "",
    val userAnswer: String = "",
    val lastScore: Int = 0,
    val lastFeedback: String = "",
    val totalScore: Int = 0,
    val questionNumber: Int = 0,
    val error: String? = null
)

enum class SessionPhase {
    IDLE, CONNECTING, LISTENING, RECORDING, EVALUATING, FEEDBACK, COMPLETED
}
```

### 5.8 Session Screen (Compose)

```kotlin
// feature/session/src/main/java/.../SessionScreen.kt
@Composable
fun SessionScreen(
    viewModel: SessionViewModel = hiltViewModel(),
    onSessionComplete: (String) -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(uiState.phase) {
        if (uiState.phase == SessionPhase.COMPLETED) {
            uiState.sessionId?.let { onSessionComplete(it) }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Mock Interview") },
                actions = {
                    Text(
                        text = "Score: ${uiState.totalScore}",
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(end = 16.dp)
                    )
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Phase indicator
            PhaseIndicator(phase = uiState.phase)

            Spacer(modifier = Modifier.height(24.dp))

            // Category badge
            if (uiState.currentCategory.isNotEmpty()) {
                CategoryBadge(category = uiState.currentCategory)
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Question card
            AnimatedVisibility(visible = uiState.currentQuestion.isNotEmpty()) {
                QuestionCard(question = uiState.currentQuestion)
            }

            Spacer(modifier = Modifier.weight(1f))

            // Score feedback card (shown after evaluation)
            AnimatedVisibility(
                visible = uiState.phase == SessionPhase.FEEDBACK,
                enter = slideInVertically() + fadeIn()
            ) {
                FeedbackCard(score = uiState.lastScore, feedback = uiState.lastFeedback)
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Voice input button
            when (uiState.phase) {
                SessionPhase.LISTENING -> {
                    VoiceRecordButton(
                        onAnswerRecorded = { answer ->
                            uiState.sessionId?.let { viewModel.submitAnswer(it, answer) }
                        }
                    )
                }
                SessionPhase.EVALUATING -> {
                    CircularProgressIndicator()
                    Text("Evaluating your answer...", style = MaterialTheme.typography.bodyMedium)
                }
                else -> {}
            }
        }
    }
}

@Composable
fun VoiceRecordButton(onAnswerRecorded: (String) -> Unit) {
    val context = LocalContext.current
    var isRecording by remember { mutableStateOf(false) }
    val speechRecognizer = remember { SpeechRecognizer.createSpeechRecognizer(context) }

    DisposableEffect(Unit) {
        onDispose { speechRecognizer.destroy() }
    }

    speechRecognizer.setRecognitionListener(object : RecognitionListener {
        override fun onResults(results: Bundle) {
            val text = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull() ?: ""
            if (text.isNotEmpty()) onAnswerRecorded(text)
            isRecording = false
        }
        override fun onError(error: Int) { isRecording = false }
        override fun onReadyForSpeech(params: Bundle) {}
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray) {}
        override fun onEndOfSpeech() {}
        override fun onPartialResults(partialResults: Bundle) {}
        override fun onEvent(eventType: Int, params: Bundle) {}
    })

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        FloatingActionButton(
            onClick = {
                if (!isRecording) {
                    isRecording = true
                    val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                            RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                        putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-IN")
                        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                    }
                    speechRecognizer.startListening(intent)
                } else {
                    speechRecognizer.stopListening()
                    isRecording = false
                }
            },
            containerColor = if (isRecording) MaterialTheme.colorScheme.error
                             else MaterialTheme.colorScheme.primary
        ) {
            Icon(
                imageVector = if (isRecording) Icons.Default.Stop else Icons.Default.Mic,
                contentDescription = if (isRecording) "Stop recording" else "Start recording"
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = if (isRecording) "Tap to stop" else "Tap to answer",
            style = MaterialTheme.typography.bodySmall
        )
    }
}
```

### 5.9 History Screen with MPAndroidChart

```kotlin
// feature/history/src/main/java/.../HistoryScreen.kt
@Composable
fun HistoryScreen(viewModel: HistoryViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            // Stats row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                StatCard(
                    label = "Sessions",
                    value = uiState.totalSessions.toString(),
                    modifier = Modifier.weight(1f)
                )
                StatCard(
                    label = "Avg Score",
                    value = "${uiState.averageScore.toInt()}%",
                    modifier = Modifier.weight(1f)
                )
            }
        }

        item {
            // Category radar chart using MPAndroidChart
            CategoryRadarChart(
                categoryScores = uiState.categoryScores,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(260.dp)
            )
        }

        items(uiState.sessions, key = { it.id }) { session ->
            SessionCard(
                session = session,
                onClick = { /* navigate to detail */ }
            )
        }
    }
}

@Composable
fun CategoryRadarChart(
    categoryScores: List<CategoryScore>,
    modifier: Modifier = Modifier
) {
    AndroidView(
        factory = { context ->
            RadarChart(context).apply {
                description.isEnabled = false
                webLineWidth = 1f
                webColor = Color.LTGRAY
                webLineWidthInner = 0.75f
                webColorInner = Color.LTGRAY
                webAlpha = 100
                animateXY(800, 800)

                xAxis.apply {
                    textSize = 9f
                    yOffset = 0f
                    xOffset = 0f
                    valueFormatter = IndexAxisValueFormatter(
                        categoryScores.map { it.category }
                    )
                }
                yAxis.apply {
                    setLabelCount(5, false)
                    textSize = 9f
                    axisMinimum = 0f
                    axisMaximum = 10f
                    setDrawLabels(false)
                }
            }
        },
        update = { chart ->
            val entries = categoryScores.mapIndexed { i, score ->
                RadarEntry(score.avgScore)
            }
            val dataSet = RadarDataSet(entries, "Your scores").apply {
                color = Color.parseColor("#534AB7")
                fillColor = Color.parseColor("#534AB7")
                setDrawFilled(true)
                fillAlpha = 80
                lineWidth = 2f
                setDrawHighlightCircleEnabled(true)
            }
            chart.data = RadarData(dataSet)
            chart.invalidate()
        },
        modifier = modifier
    )
}
```

---

## 6. Spring Boot Backend

### 6.1 Project Structure
```
interview-ace-backend/
├── src/main/java/com/vikash/interviewace/
│   ├── InterviewAceApplication.java
│   ├── config/
│   │   ├── SecurityConfig.java
│   │   ├── WebSocketConfig.java
│   │   └── JwtConfig.java
│   ├── controller/
│   │   ├── AuthController.java
│   │   ├── SessionController.java
│   │   └── SyncController.java
│   ├── service/
│   │   ├── AuthService.java
│   │   ├── SessionService.java
│   │   ├── EvaluatorService.java
│   │   ├── ClaudeService.java
│   │   └── NotificationService.java
│   ├── repository/
│   │   ├── UserRepository.java
│   │   ├── SessionRepository.java
│   │   └── QuestionAnswerRepository.java
│   ├── model/
│   │   ├── entity/ (User, Session, QuestionAnswer, RefreshToken)
│   │   ├── dto/ (request/response DTOs)
│   │   └── websocket/ (SessionMessage, AnswerPayload)
│   ├── security/
│   │   ├── JwtUtil.java
│   │   ├── JwtAuthFilter.java
│   │   └── JwtHandshakeInterceptor.java
│   └── websocket/
│       └── SessionWebSocketHandler.java
├── src/main/resources/
│   └── application.yml
└── Dockerfile
```

### 6.2 application.yml
```yaml
spring:
  datasource:
    url: ${DATABASE_URL}
    username: ${DATABASE_USER}
    password: ${DATABASE_PASS}
    driver-class-name: org.postgresql.Driver
  jpa:
    hibernate:
      ddl-auto: validate    # use Flyway in prod, never auto
    show-sql: false
    properties:
      hibernate:
        dialect: org.hibernate.dialect.PostgreSQLDialect

server:
  port: 8080

jwt:
  secret: ${JWT_SECRET}
  access-token-expiry: 900000       # 15 minutes
  refresh-token-expiry: 604800000   # 7 days

claude:
  api-key: ${CLAUDE_API_KEY}
  model: claude-sonnet-4-6
  max-tokens: 1000

firebase:
  credentials-path: ${FIREBASE_CREDENTIALS_PATH}
```

### 6.3 WebSocket Config

```java
// config/WebSocketConfig.java
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Autowired
    private JwtHandshakeInterceptor jwtHandshakeInterceptor;

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        config.enableSimpleBroker("/topic");
        config.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
            .addInterceptors(jwtHandshakeInterceptor)
            .setAllowedOriginPatterns("*");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(new ChannelInterceptor() {
            @Override
            public Message<?> preSend(Message<?> message, MessageChannel channel) {
                StompHeaderAccessor accessor =
                    MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
                if (StompCommand.CONNECT.equals(accessor.getCommand())) {
                    String token = accessor.getFirstNativeHeader("Authorization");
                    // validate JWT, set authentication
                }
                return message;
            }
        });
    }
}
```

### 6.4 Session Service

```java
// service/SessionService.java
@Service
@Transactional
public class SessionService {

    @Autowired private SessionRepository sessionRepo;
    @Autowired private ClaudeService claudeService;
    @Autowired private SimpMessagingTemplate messagingTemplate;

    private static final int QUESTIONS_PER_SESSION = 8;

    public void startSession(String sessionId, String userId, String role) {
        Session session = sessionRepo.findById(UUID.fromString(sessionId))
            .orElseGet(() -> {
                Session s = new Session();
                s.setId(UUID.fromString(sessionId));
                s.setUserId(UUID.fromString(userId));
                s.setRole(role);
                s.setStatus("IN_PROGRESS");
                return sessionRepo.save(s);
            });

        // Ask Claude for first question
        sendNextQuestion(session, 0);
    }

    public void handleAnswer(String sessionId, String answerText) {
        Session session = sessionRepo.findById(UUID.fromString(sessionId))
            .orElseThrow();

        List<QuestionAnswer> qas = session.getQuestionAnswers();
        QuestionAnswer current = qas.stream()
            .filter(qa -> qa.getAnswerText() == null)
            .findFirst()
            .orElseThrow();

        // Evaluate answer via Claude
        EvaluationResult eval = claudeService.evaluateAnswer(
            current.getQuestionText(), answerText, session.getRole()
        );

        current.setAnswerText(answerText);
        current.setScore(eval.getScore());
        current.setFeedback(eval.getFeedback());
        sessionRepo.save(session);

        // Update total score
        session.setTotalScore(session.getTotalScore() + eval.getScore());
        session.setMaxScore(session.getMaxScore() + 10);

        boolean isLastQuestion = qas.size() >= QUESTIONS_PER_SESSION;

        // Push score back to Android
        messagingTemplate.convertAndSend(
            "/topic/session/" + sessionId,
            new SessionMessage(
                "SCORE",
                null,
                eval.getScore(),
                eval.getFeedback(),
                current.getCategory(),
                isLastQuestion
            )
        );

        if (isLastQuestion) {
            session.setStatus("COMPLETED");
            sessionRepo.save(session);
            messagingTemplate.convertAndSend(
                "/topic/session/" + sessionId,
                new SessionMessage("SESSION_END", null, null, null, null, true)
            );
        } else {
            // Small delay then next question
            CompletableFuture.delayedExecutor(1500, TimeUnit.MILLISECONDS).execute(() ->
                sendNextQuestion(session, qas.size())
            );
        }
    }

    private void sendNextQuestion(Session session, int questionIndex) {
        QuestionResult question = claudeService.getNextQuestion(
            session.getRole(),
            session.getQuestionAnswers(),
            questionIndex
        );

        QuestionAnswer qa = new QuestionAnswer();
        qa.setQuestionText(question.getText());
        qa.setCategory(question.getCategory());
        qa.setOrderIndex(questionIndex);
        qa.setSession(session);
        session.getQuestionAnswers().add(qa);
        sessionRepo.save(session);

        messagingTemplate.convertAndSend(
            "/topic/session/" + session.getId(),
            new SessionMessage(
                "QUESTION",
                question.getText(),
                null,
                null,
                question.getCategory(),
                false
            )
        );
    }
}
```

### 6.5 Sync Controller (offline-first endpoint)

```java
// controller/SyncController.java
@RestController
@RequestMapping("/api/sync")
public class SyncController {

    @Autowired private SessionRepository sessionRepo;
    @Autowired private QuestionAnswerRepository qaRepo;

    @PostMapping("/sessions")
    public ResponseEntity<SyncResponse> syncSessions(
        @RequestBody SyncRequest request,
        @AuthenticationPrincipal UserDetails userDetails
    ) {
        List<String> syncedIds = new ArrayList<>();
        List<String> failedIds = new ArrayList<>();

        for (SessionDto dto : request.getSessions()) {
            try {
                Session session = dto.toEntity(userDetails.getUsername());
                sessionRepo.save(session);

                List<QuestionAnswer> qas = dto.getQuestionAnswers().stream()
                    .map(qaDto -> qaDto.toEntity(session))
                    .collect(Collectors.toList());
                qaRepo.saveAll(qas);
                syncedIds.add(dto.getId());
            } catch (Exception e) {
                failedIds.add(dto.getId());
            }
        }
        return ResponseEntity.ok(new SyncResponse(syncedIds, failedIds));
    }
}
```

---

## 7. Claude API Integration

### 7.1 ClaudeService (Spring Boot)

```java
// service/ClaudeService.java
@Service
public class ClaudeService {

    @Value("${claude.api-key}")
    private String apiKey;

    @Value("${claude.model}")
    private String model;

    private final RestTemplate restTemplate = new RestTemplate();

    private static final String INTERVIEWER_SYSTEM_PROMPT = """
        You are a senior Android/SDE technical interviewer at a top Indian tech company.
        Conduct a realistic mock interview for a %s role with %d years of experience.
        
        Rules:
        - Ask ONE focused technical question at a time
        - Cover these categories in order: DSA, Android-specific, System Design, Behavioral
        - Vary difficulty: start medium, increase if answers are strong
        - Return ONLY valid JSON: {"text": "question here", "category": "DSA|ANDROID|SYSTEM_DESIGN|BEHAVIORAL"}
        - No preamble, no markdown, just JSON
        
        Previous Q&A context: %s
        Now generate question number %d.
        """;

    private static final String EVALUATOR_SYSTEM_PROMPT = """
        You are a strict but fair technical interview evaluator.
        Score the candidate's answer to this question out of 10.
        
        Question: %s
        Candidate's answer: %s
        Role being interviewed for: %s
        
        Return ONLY valid JSON:
        {
          "score": <0-10>,
          "feedback": "<2-3 sentences: what was good, what was missing, ideal answer hint>"
        }
        No preamble, no markdown, just JSON.
        """;

    public QuestionResult getNextQuestion(
        String role,
        List<QuestionAnswer> previousQAs,
        int questionIndex
    ) {
        String context = previousQAs.stream()
            .map(qa -> String.format("Q: %s\nA: %s\nScore: %d/10",
                qa.getQuestionText(), qa.getAnswerText(), qa.getScore()))
            .collect(Collectors.joining("\n\n"));

        String prompt = String.format(INTERVIEWER_SYSTEM_PROMPT,
            role, 2, context, questionIndex + 1);

        String response = callClaude(prompt);

        try {
            JsonObject json = JsonParser.parseString(response).getAsJsonObject();
            return new QuestionResult(
                json.get("text").getAsString(),
                json.get("category").getAsString()
            );
        } catch (Exception e) {
            // Fallback question if JSON parse fails
            return new QuestionResult(
                "Can you explain the difference between a Stack and a Queue, and give a real-world use case for each?",
                "DSA"
            );
        }
    }

    public EvaluationResult evaluateAnswer(
        String question, String answer, String role
    ) {
        String prompt = String.format(EVALUATOR_SYSTEM_PROMPT, question, answer, role);
        String response = callClaude(prompt);

        try {
            JsonObject json = JsonParser.parseString(response).getAsJsonObject();
            return new EvaluationResult(
                json.get("score").getAsInt(),
                json.get("feedback").getAsString()
            );
        } catch (Exception e) {
            return new EvaluationResult(5, "Unable to evaluate. Please try again.");
        }
    }

    private String callClaude(String userMessage) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("x-api-key", apiKey);
        headers.set("anthropic-version", "2023-06-01");
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> body = new HashMap<>();
        body.put("model", model);
        body.put("max_tokens", 1000);
        body.put("messages", List.of(Map.of("role", "user", "content", userMessage)));

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

        ResponseEntity<Map> response = restTemplate.exchange(
            "https://api.anthropic.com/v1/messages",
            HttpMethod.POST, entity, Map.class
        );

        List<Map<String, Object>> content =
            (List<Map<String, Object>>) response.getBody().get("content");
        return (String) content.get(0).get("text");
    }
}
```

---

## 8. Offline-First Sync Strategy

### Decision Table
| Scenario | Action |
|---|---|
| Session created (no internet) | Write to Room with `isSynced=false`, enqueue WorkManager job |
| Internet returns | WorkManager fires SyncWorker, POST to `/api/sync/sessions` |
| Partial sync failure | Retry with exponential backoff (max 3 attempts) |
| Session already exists on server | Server uses `ON CONFLICT DO NOTHING` — safe to re-send |
| User reinstalls app | History only available if previously synced (by design — show sync badge) |

### Sync Status UI
Always show a sync indicator per session card:
- Green checkmark = synced to server
- Orange clock = pending sync (will sync when online)
- Red warning = sync failed (manual retry button)

---

## 9. WebSocket / STOMP Flow

### Complete Message Types
```
SERVER → CLIENT:
  QUESTION  { type, question, category, isLastQuestion }
  SCORE     { type, score, feedback, category, isLastQuestion }
  SESSION_END { type }
  ERROR     { type, message }

CLIENT → SERVER:
  /app/session/{id}/start   { role }
  /app/session/{id}/answer  { answerText }
  /app/session/{id}/abort   {}
```

### Reconnection Strategy (Android)
```kotlin
// In SessionViewModel
private suspend fun connectWithRetry(sessionId: String, maxAttempts: Int = 3) {
    repeat(maxAttempts) { attempt ->
        val result = webSocketClient.connect()
        if (result.isSuccess) {
            webSocketClient.subscribeToSession(sessionId) { handleMessage(it) }
            return
        }
        delay(2.0.pow(attempt).toLong() * 1000) // 1s, 2s, 4s backoff
    }
    _uiState.update { it.copy(error = "Cannot connect. Check your internet.") }
}
```

---

## 10. Authentication

### JWT Flow
```
1. POST /api/auth/register { name, email, password }
   ← 200 { accessToken, refreshToken, user }

2. Every API call: Authorization: Bearer <accessToken>

3. When 401 received:
   POST /api/auth/refresh { refreshToken }
   ← 200 { accessToken, refreshToken }   (rotate refresh token)
   ← 401 { error: "expired" } → force logout

4. Access token: 15 min expiry
   Refresh token: 7 days expiry, stored encrypted in EncryptedSharedPreferences
```

### JWT Util (Spring Boot)
```java
@Component
public class JwtUtil {
    @Value("${jwt.secret}")
    private String secret;

    public String generateAccessToken(String userId) {
        return Jwts.builder()
            .setSubject(userId)
            .setIssuedAt(new Date())
            .setExpiration(new Date(System.currentTimeMillis() + 900_000)) // 15 min
            .signWith(getKey(), SignatureAlgorithm.HS256)
            .compact();
    }

    public String validateAndGetUserId(String token) {
        return Jwts.parserBuilder()
            .setSigningKey(getKey()).build()
            .parseClaimsJws(token)
            .getBody()
            .getSubject();
    }

    private Key getKey() {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }
}
```

---

## 11. Testing Strategy

### Android Unit Tests
```kotlin
// SessionViewModelTest.kt
@OptIn(ExperimentalCoroutinesApi::class)
class SessionViewModelTest {
    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    private val fakeSessionRepository = FakeSessionRepository()
    private lateinit var viewModel: SessionViewModel

    @Before
    fun setup() {
        viewModel = SessionViewModel(fakeSessionRepository, CreateSessionUseCase(fakeSessionRepository))
    }

    @Test
    fun `createSession writes to Room and returns PENDING status`() = runTest {
        viewModel.startSession("ANDROID_DEVELOPER", mockContext)
        val uiState = viewModel.uiState.value
        assertThat(uiState.phase).isEqualTo(SessionPhase.CONNECTING)
        assertThat(fakeSessionRepository.sessions).hasSize(1)
    }
}

// FakeSessionRepository.kt
class FakeSessionRepository : SessionRepository {
    val sessions = mutableListOf<Session>()

    override suspend fun createSession(role: String): Session {
        val session = Session(
            id = UUID.randomUUID().toString(),
            role = role,
            status = SessionStatus.PENDING
        )
        sessions.add(session)
        return session
    }
    // ... other overrides
}
```

### Android Instrumentation Tests
```kotlin
// SessionDaoTest.kt
@RunWith(AndroidJUnit4::class)
class SessionDaoTest {
    private lateinit var db: InterviewAceDatabase
    private lateinit var dao: SessionDao

    @Before
    fun setup() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            InterviewAceDatabase::class.java
        ).build()
        dao = db.sessionDao()
    }

    @Test
    fun insertSession_and_getUnsyncedSessions() = runTest {
        val session = SessionEntity(
            id = "test-id", userId = "user-1",
            role = "ANDROID", status = "COMPLETED",
            isSynced = false
        )
        dao.insertSession(session)
        val unsynced = dao.getUnsyncedSessions()
        assertThat(unsynced).contains(session)
    }

    @After
    fun teardown() { db.close() }
}
```

### Spring Boot Tests
```java
@SpringBootTest
@AutoConfigureTestDatabase(replace = Replace.NONE)
@Testcontainers
class SessionServiceTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15");

    @Autowired SessionService sessionService;
    @MockBean ClaudeService claudeService;

    @Test
    void startSession_shouldSendFirstQuestion() {
        when(claudeService.getNextQuestion(any(), any(), anyInt()))
            .thenReturn(new QuestionResult("What is a coroutine?", "ANDROID"));

        // Start session and verify message is sent via STOMP
        sessionService.startSession("session-1", "user-1", "ANDROID_DEVELOPER");

        verify(claudeService, times(1)).getNextQuestion(eq("ANDROID_DEVELOPER"), any(), eq(0));
    }
}
```

### Test Coverage Targets
| Layer | Target |
|---|---|
| Domain/UseCases | 90%+ |
| Repository | 80%+ |
| ViewModel | 80%+ |
| Room DAOs | 85%+ |
| Spring Service | 75%+ |
| Spring Controller | 70%+ |

---

## 12. CI/CD + Docker + Railway

### Dockerfile (Spring Boot)
```dockerfile
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY target/interview-ace-backend-*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=75.0", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "-jar", "app.jar"]
```

### docker-compose.yml (local development)
```yaml
version: '3.8'
services:
  postgres:
    image: postgres:15
    environment:
      POSTGRES_DB: interviewace
      POSTGRES_USER: dev
      POSTGRES_PASSWORD: devpass
    ports:
      - "5432:5432"
    volumes:
      - postgres_data:/var/lib/postgresql/data

  backend:
    build: .
    ports:
      - "8080:8080"
    environment:
      DATABASE_URL: jdbc:postgresql://postgres:5432/interviewace
      DATABASE_USER: dev
      DATABASE_PASS: devpass
      JWT_SECRET: your-256-bit-secret-here
      CLAUDE_API_KEY: ${CLAUDE_API_KEY}
    depends_on:
      - postgres

volumes:
  postgres_data:
```

### GitHub Actions CI/CD
```yaml
# .github/workflows/backend-ci.yml
name: Backend CI/CD

on:
  push:
    branches: [main]
    paths: ['backend/**']

jobs:
  test-and-deploy:
    runs-on: ubuntu-latest
    services:
      postgres:
        image: postgres:15
        env:
          POSTGRES_PASSWORD: testpass
          POSTGRES_DB: testdb
        ports:
          - 5432:5432

    steps:
      - uses: actions/checkout@v4

      - name: Set up JDK 17
        uses: actions/setup-java@v4
        with:
          java-version: '17'
          distribution: 'temurin'

      - name: Run tests
        working-directory: ./backend
        env:
          DATABASE_URL: jdbc:postgresql://localhost:5432/testdb
          DATABASE_USER: postgres
          DATABASE_PASS: testpass
          JWT_SECRET: test-secret-key-256-bits-long-here
          CLAUDE_API_KEY: ${{ secrets.CLAUDE_API_KEY }}
        run: ./mvnw test

      - name: Build JAR
        working-directory: ./backend
        run: ./mvnw package -DskipTests

      - name: Deploy to Railway
        if: github.ref == 'refs/heads/main'
        run: |
          npm install -g @railway/cli
          railway up --service backend
        env:
          RAILWAY_TOKEN: ${{ secrets.RAILWAY_TOKEN }}

# .github/workflows/android-ci.yml
name: Android CI

on:
  push:
    branches: [main, develop]

jobs:
  test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4

      - name: Set up JDK 17
        uses: actions/setup-java@v4
        with:
          java-version: '17'
          distribution: 'temurin'

      - name: Run unit tests
        run: ./gradlew test

      - name: Run lint
        run: ./gradlew lint

      - name: Build debug APK
        run: ./gradlew assembleDebug
        env:
          API_BASE_URL: ${{ secrets.API_BASE_URL }}

      - name: Upload APK artifact
        uses: actions/upload-artifact@v4
        with:
          name: debug-apk
          path: app/build/outputs/apk/debug/app-debug.apk
```

### Railway Setup Steps
```bash
# 1. Install Railway CLI
npm install -g @railway/cli

# 2. Login
railway login

# 3. Create project
railway new interview-ace

# 4. Add PostgreSQL plugin
railway add postgresql

# 5. Set environment variables
railway variables set JWT_SECRET="your-256-bit-secret"
railway variables set CLAUDE_API_KEY="sk-ant-..."

# 6. Deploy
railway up

# Railway auto-detects Dockerfile and builds
# PostgreSQL URL is auto-injected as DATABASE_URL
```

---

## 13. Play Store Release Checklist

### App Signing
```bash
# Generate keystore (keep safe — losing this = can never update app)
keytool -genkey -v -keystore interviewace-release.jks \
  -keyalg RSA -keysize 2048 -validity 10000 \
  -alias interviewace

# In app/build.gradle.kts
signingConfigs {
    create("release") {
        storeFile = file("../interviewace-release.jks")
        storePassword = System.getenv("KEYSTORE_PASSWORD")
        keyAlias = "interviewace"
        keyPassword = System.getenv("KEY_PASSWORD")
    }
}
buildTypes {
    release {
        isMinifyEnabled = true
        isShrinkResources = true
        proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
        signingConfig = signingConfigs.getByName("release")
    }
}
```

### ProGuard Rules
```proguard
# proguard-rules.pro
-keep class com.vikash.interviewace.core.network.** { *; }
-keep class com.vikash.interviewace.core.database.entity.** { *; }
-keepattributes Signature
-keepattributes *Annotation*
-dontwarn org.hildan.krossbow.**
-keep class org.hildan.krossbow.** { *; }
```

### Play Store Assets Needed
- App icon: 512×512 PNG
- Feature graphic: 1024×500 PNG
- Screenshots: min 2, max 8 (phone screenshots at 1080×1920 or 1080×2400)
- Short description: max 80 chars → "AI mock interviews for Android & SDE roles"
- Full description: max 4000 chars
- Privacy policy URL (mandatory — you collect email + voice data)
- Content rating questionnaire

### Privacy Policy Must Cover
Since you record voice and store interview answers:
- What data you collect (email, voice transcripts, interview scores)
- Where it's stored (Railway PostgreSQL, India/EU region)
- How long you keep it (30 days for transcripts by default)
- User deletion right (add "Delete my account" in settings)

### Release Build Command
```bash
./gradlew bundleRelease
# Output: app/build/outputs/bundle/release/app-release.aab
# Upload .aab to Play Console (not .apk)
```

### Play Console Steps
1. Create app in Play Console
2. Complete content rating questionnaire
3. Add privacy policy URL
4. Set up internal testing track first
5. Install on your device via internal track, test all flows
6. Promote to production (review takes 3–7 days for new accounts)

---

## 14. Week-by-Week Sprint Plan

### Week 1 — Android Foundation
**Days 1–2:**
- Create multi-module project structure (all modules, settings.gradle)
- Set up libs.versions.toml with all dependencies
- Configure Hilt across all modules
- Create Room schema: SessionEntity, QuestionAnswerEntity, UserEntity
- Write all DAOs + InterviewAceDatabase
- Write DAO instrumentation tests

**Days 3–4:**
- Implement EncryptedDataStore for JWT
- Set up Retrofit + OkHttp + JWT interceptor
- Build :feature:auth screens (Login + Register with Compose)
- Wire up auth flow with fake backend (mock API responses for now)

**Days 5–7:**
- Build InterviewSessionService (foreground service skeleton)
- Implement SpeechRecognizer + TTS in SessionScreen
- Build SessionViewModel + SessionUiState
- Connect service to ViewModel via ServiceConnection + StateFlow
- Basic session screen renders question + voice button

### Week 2 — Spring Boot Backend
**Days 1–2:**
- Spring Boot project setup (Spring Initializr: Web, WebSocket, JPA, Security, PostgreSQL)
- Flyway migration scripts for all tables
- Run locally with docker-compose
- Implement UserEntity, SessionEntity, QuestionAnswerEntity JPA entities

**Days 3–4:**
- AuthController + AuthService + JWT (register, login, refresh)
- WebSocketConfig + STOMP setup
- SessionController — start session endpoint
- Write basic Spring tests for AuthService

**Days 5–7:**
- Implement ClaudeService (interviewer + evaluator prompts)
- SessionService: full question/answer/score flow over STOMP
- SyncController for WorkManager offline sync
- Integration test the full session flow locally

### Week 3 — Connect Android ↔ Backend
**Days 1–2:**
- Replace mock API with real Spring Boot running on local network
- Test full WebSocket session end-to-end on emulator
- Implement WorkManager SyncWorker + SyncScheduler
- Test offline-first: start session with airplane mode, sync after reconnect

**Days 3–4:**
- Build :feature:history screen (session list + category radar chart)
- StatCard, SessionCard, CategoryRadarChart components
- HistoryViewModel reading from Room Flow
- Navigate between screens with Compose Navigation

**Days 5–7:**
- Deploy Spring Boot to Railway (set up all env vars)
- Update Android BuildConfig.API_BASE_URL + BuildConfig.WS_BASE_URL
- Test full flow against Railway backend from real Android device
- Fix all network/WebSocket issues found in real device testing

### Week 4 — Polish + Release
**Days 1–2:**
- Firebase Crashlytics + Performance setup in Android
- FCM: daily reminder notification ("Time for your mock interview")
- Set up FCM server-side in NotificationService (Spring Boot)
- Error handling UX: no internet, session timeout, API errors

**Days 3–4:**
- GitHub Actions CI/CD for both Android and backend
- ProGuard rules, release signing config
- Generate signed AAB
- Play Console: create app, fill all metadata, upload screenshots

**Days 5–7:**
- Internal testing track: install on personal device, test all flows
- Fix all bugs found in testing
- Submit for Play Store review
- Write GitHub README with architecture diagram + setup instructions

---

## Environment Variables Summary

### Android (local.properties / CI secrets)
```
API_BASE_URL=https://your-app.railway.app
WS_BASE_URL=wss://your-app.railway.app
```

### Backend (Railway environment variables)
```
DATABASE_URL=postgresql://...   (auto-set by Railway PostgreSQL plugin)
DATABASE_USER=...
DATABASE_PASS=...
JWT_SECRET=<256-bit random string>
CLAUDE_API_KEY=sk-ant-...
FIREBASE_CREDENTIALS_PATH=/app/firebase-credentials.json
```

---

*Document version: 1.0 | Total implementation time: 4 weeks | Target: Play Store production release*
