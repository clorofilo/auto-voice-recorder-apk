# Auto Voice Recorder Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build an Android app (Kotlin + Compose) that records audio when the
volume-down button is held 3s (even while locked), stops on volume-up, lists
and plays back recordings, optionally backs them up to the app's private
Google Drive folder, and gates app access behind biometrics + a PIN.

**Architecture:** An `AccessibilityService` detects the volume-key gesture
and forwards start/stop intents to a foreground `RecordingService`, which
owns `MediaRecorder` and the mandatory notification. Recording metadata
lives in Room behind a repository; a `WorkManager` worker uploads pending
recordings to Drive. Compose UI reads everything through `StateFlow`s.
Every piece with real device/network/hardware dependencies (MediaRecorder,
AccessibilityService, BiometricPrompt, Drive REST calls) is kept as a thin
wrapper around a plain, dependency-free class that carries the actual logic
and is unit tested with fakes.

**Tech Stack:** Kotlin, Jetpack Compose, Room, WorkManager, AndroidX
Biometric, AndroidX Security-Crypto (EncryptedSharedPreferences), Google
Drive REST API (`google-api-services-drive` + `google-api-client-android`),
Google Sign-In, Navigation-Compose, kotlinx-coroutines(-test).

**Spec:** `docs/superpowers/specs/2026-10-21-voice-recorder-design.md`

## Global Constraints

- minSdk 29 (Android 10), per spec.
- Audio format: M4A container, AAC encoder (`MediaRecorder.OutputFormat.MPEG_4` / `MediaRecorder.AudioEncoder.AAC`).
- Recording notification text is exactly `"Recording"`, no action buttons, no file name or duration shown.
- No `RECEIVE_BOOT_COMPLETED` / boot receiver — the Accessibility Service restart after reboot is handled by the OS.
- The power/lock button is never intercepted; only volume-down (start, 3s hold) and volume-up (stop) are handled.
- No disguise / hidden mode of any kind — app identity is always visible; access is gated by biometrics + PIN only.
- Drive scope is `drive.appdata` only (app-private folder), never general Drive access.
- App lock: biometric prompt first, PIN as fallback, per spec.

## Review Focus

- RECORD_AUDIO permission denied/revoked, then user holds volume-down 3s: must not start a broken recording or crash — Task 3.
- Volume-down held 3s again while already recording (reentrant start): must not open a second `MediaRecorder` or corrupt the in-progress file — Task 3.
- Volume-up pressed (stop) while nothing is recording: must be a silent no-op, no empty file, no crash — Task 3.
- Drive sync toggled off globally: pending recordings must stay `PENDING` and never get uploaded until re-enabled, even if the worker runs — Task 6.
- Wrong PIN entered: must be rejected deterministically and never accidentally unlock, independent of how many prior attempts failed — Task 5.

---

### Task 1: Project dependencies

**Files:**
- Modify: `Project/gradle/libs.versions.toml`
- Modify: `Project/app/build.gradle.kts`

**Interfaces:**
- Produces: working Gradle build with all libraries later tasks depend on (Room + ksp, WorkManager, androidx.biometric, androidx.security:security-crypto, androidx.navigation:navigation-compose, androidx.lifecycle:lifecycle-viewmodel-compose, com.google.android.gms:play-services-auth, com.google.api-client:google-api-client-android, com.google.apis:google-api-services-drive, kotlinx-coroutines-android, kotlinx-coroutines-test, junit).

- [ ] **Step 1: Add version catalog entries** in `libs.versions.toml` for: `room` (2.6.x), `ksp` plugin matching the project's Kotlin version, `workManager` (2.9.x), `biometric` (1.2.x), `securityCrypto` (1.1.x), `navigationCompose` (2.8.x), `lifecycleViewmodelCompose` (2.8.x), `playServicesAuth` (21.x), `googleApiClientAndroid` (2.x), `googleApiServicesDrive` (v3-rev latest), `coroutinesAndroid`/`coroutinesTest` (1.8.x).
- [ ] **Step 2: Apply `ksp` plugin and add the dependencies** to `Project/app/build.gradle.kts` (`implementation` for runtime libs, `ksp` for `room-compiler`, `testImplementation` for `coroutines-test` and `junit`).
- [ ] **Step 3: Run the build**

Run: `cd Project && ./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add Project/gradle/libs.versions.toml Project/app/build.gradle.kts
git commit -m "build: add dependencies for recording, sync, security and UI"
```

---

### Task 2: Data layer (Room + repository)

**Files:**
- Create: `Project/app/src/main/java/com/example/auto_voice_recorder/data/SyncState.kt`
- Create: `Project/app/src/main/java/com/example/auto_voice_recorder/data/Recording.kt`
- Create: `Project/app/src/main/java/com/example/auto_voice_recorder/data/RecordingDao.kt`
- Create: `Project/app/src/main/java/com/example/auto_voice_recorder/data/AppDatabase.kt`
- Create: `Project/app/src/main/java/com/example/auto_voice_recorder/data/RecordingsRepository.kt`
- Test: `Project/app/src/test/java/com/example/auto_voice_recorder/data/RecordingsRepositoryTest.kt`
- Test (fake, not a real test class): `Project/app/src/test/java/com/example/auto_voice_recorder/data/FakeRecordingDao.kt`

**Interfaces:**
- Produces: `enum class SyncState { PENDING, SYNCED, DISABLED }`; `data class Recording(val id: Long = 0, val filePath: String, val createdAtEpochMs: Long, val durationMs: Long, val syncState: SyncState)`; `interface RecordingDao` (Room `@Dao`) with `suspend fun insert(recording: Recording): Long`, `suspend fun delete(id: Long)`, `suspend fun updateSyncState(id: Long, state: SyncState)`, `fun observeAll(): Flow<List<Recording>>`, `suspend fun pendingSync(): List<Recording>`; `class RecordingsRepository(private val dao: RecordingDao)` with `suspend fun add(filePath: String, createdAtEpochMs: Long, durationMs: Long, initialState: SyncState): Long`, `suspend fun delete(id: Long)`, `fun observeAll(): Flow<List<Recording>>`, `suspend fun markSynced(id: Long)`, `suspend fun pendingSyncRecordings(): List<Recording>`.

- [ ] **Step 1: Write `FakeRecordingDao`** implementing `RecordingDao` over an in-memory `MutableList<Recording>` plus a `MutableStateFlow` for `observeAll()`, auto-incrementing ids.
- [ ] **Step 2: Write failing tests in `RecordingsRepositoryTest`**:
  - `add stores a recording with the given initial sync state`
  - `delete removes the recording by id`
  - `markSynced transitions a PENDING recording to SYNCED`
  - `pendingSyncRecordings returns only PENDING recordings`
- [ ] **Step 3: Run tests to verify they fail**

Run: `cd Project && ./gradlew :app:testDebugUnitTest --tests "*RecordingsRepositoryTest*"`
Expected: FAIL (classes under test don't exist yet)

- [ ] **Step 4: Implement `SyncState`, `Recording` (`@Entity(tableName = "recordings")`), `RecordingDao` (`@Dao`), `AppDatabase` (`@Database(entities = [Recording::class], version = 1)`), and `RecordingsRepository`** per the signatures above.
- [ ] **Step 5: Run tests to verify they pass**

Run: `cd Project && ./gradlew :app:testDebugUnitTest --tests "*RecordingsRepositoryTest*"`
Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add Project/app/src/main/java/com/example/auto_voice_recorder/data Project/app/src/test/java/com/example/auto_voice_recorder/data
git commit -m "feat: add recordings data layer (Room + repository)"
```

---

### Task 3: Recording orchestration + foreground service

**Files:**
- Create: `Project/app/src/main/java/com/example/auto_voice_recorder/audio/AudioRecorderController.kt`
- Create: `Project/app/src/main/java/com/example/auto_voice_recorder/audio/MediaRecorderController.kt`
- Create: `Project/app/src/main/java/com/example/auto_voice_recorder/service/RecordingStatus.kt`
- Create: `Project/app/src/main/java/com/example/auto_voice_recorder/service/RecordingOrchestrator.kt`
- Create: `Project/app/src/main/java/com/example/auto_voice_recorder/service/RecordingService.kt`
- Modify: `Project/app/src/main/AndroidManifest.xml`
- Modify: `Project/app/src/main/res/values/strings.xml`
- Test: `Project/app/src/test/java/com/example/auto_voice_recorder/service/RecordingOrchestratorTest.kt`

**Interfaces:**
- Consumes: `RecordingsRepository` from Task 2 (`add`, exact signature above).
- Produces: `interface AudioRecorderController { fun start(outputFile: java.io.File); fun stop(): Long /* duration ms */; fun release() }`; `object RecordingStatus { val isRecording: StateFlow<Boolean> }` (backing `MutableStateFlow`, exposed read-only); `class RecordingOrchestrator(private val repository: RecordingsRepository, private val recorder: AudioRecorderController, private val outputFileFor: () -> java.io.File, private val hasRecordPermission: () -> Boolean, private val syncEnabled: () -> Boolean, private val clock: () -> Long = System::currentTimeMillis)` with `fun start()`, `fun stop()`; both update `RecordingStatus`. `RecordingService` with companion `const val ACTION_START_RECORDING`, `const val ACTION_STOP_RECORDING`, both `String` action constants for the `Intent` it handles in `onStartCommand`.

- [ ] **Step 1: Write a `FakeAudioRecorderController`** (test-only class, in the same test file or a small `FakeAudioRecorderController.kt` under the test source set) implementing `AudioRecorderController`, recording calls to `start`/`stop`/`release` and a settable fixed duration.
- [ ] **Step 2: Write failing tests in `RecordingOrchestratorTest`** (reusing `FakeRecordingDao` from Task 2 via `RecordingsRepository(FakeRecordingDao())`):
  - `start begins a recording and flips RecordingStatus.isRecording to true`
  - `start when RECORD_AUDIO permission is denied does not call recorder.start and leaves isRecording false`
  - `start while already recording is a no-op and does not call recorder.start a second time`
  - `stop while recording stops the recorder, persists a Recording with the returned duration, and flips isRecording to false`
  - `stop while not recording is a no-op and does not touch the repository`
  - `stop persists syncState PENDING when syncEnabled() is true, DISABLED when false`
- [ ] **Step 3: Run tests to verify they fail**

Run: `cd Project && ./gradlew :app:testDebugUnitTest --tests "*RecordingOrchestratorTest*"`
Expected: FAIL

- [ ] **Step 4: Implement `AudioRecorderController`, `RecordingStatus`, and `RecordingOrchestrator`** per the signatures above and the behaviors the tests pin.
- [ ] **Step 5: Run tests to verify they pass**

Run: `cd Project && ./gradlew :app:testDebugUnitTest --tests "*RecordingOrchestratorTest*"`
Expected: PASS

- [ ] **Step 6: Implement `MediaRecorderController`** (real `AudioRecorderController` using `android.media.MediaRecorder`, `OutputFormat.MPEG_4`, `AudioEncoder.AAC`, writing to the given file; `stop()` returns elapsed ms since `start()`). Not unit tested (device-only hardware dependency, per spec).
- [ ] **Step 7: Implement `RecordingService`** (`Service`, `foregroundServiceType="microphone"`): on `ACTION_START_RECORDING`, calls `startForeground` with a notification (channel id `recording_channel`, text from `R.string.notification_recording_text` = `"Recording"`, no actions) then `RecordingOrchestrator.start()` (checking `RECORD_AUDIO` via `ContextCompat.checkSelfPermission`); on `ACTION_STOP_RECORDING`, calls `RecordingOrchestrator.stop()` then `stopForeground`/`stopSelf`. Wires a real `MediaRecorderController`, `RecordingsRepository` (via `AppDatabase.getInstance(applicationContext).recordingDao()`), and a `syncEnabled` lambda reading the Drive-sync preference (stubbed to `true` for now; wired for real in Task 9).
- [ ] **Step 8: Add `R.string.notification_recording_text = "Recording"` and `R.string.notification_channel_name`** to `strings.xml`.
- [ ] **Step 9: Declare in `AndroidManifest.xml`**: permissions `RECORD_AUDIO`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MICROPHONE`, `POST_NOTIFICATIONS`; `<service android:name=".service.RecordingService" android:foregroundServiceType="microphone" android:exported="false" />`.
- [ ] **Step 10: Build to verify manifest/service compile**

Run: `cd Project && ./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 11: Commit**

```bash
git add Project/app/src/main/java/com/example/auto_voice_recorder/audio Project/app/src/main/java/com/example/auto_voice_recorder/service Project/app/src/main/AndroidManifest.xml Project/app/src/main/res/values/strings.xml Project/app/src/test/java/com/example/auto_voice_recorder/service
git commit -m "feat: add recording orchestration and foreground RecordingService"
```

---

### Task 4: Volume-key gesture detection + Accessibility Service

**Files:**
- Create: `Project/app/src/main/java/com/example/auto_voice_recorder/input/GestureScheduler.kt`
- Create: `Project/app/src/main/java/com/example/auto_voice_recorder/input/VolumeGestureDetector.kt`
- Create: `Project/app/src/main/java/com/example/auto_voice_recorder/service/VolumeKeyAccessibilityService.kt`
- Create: `Project/app/src/main/res/xml/accessibility_service_config.xml`
- Modify: `Project/app/src/main/AndroidManifest.xml`
- Modify: `Project/app/src/main/res/values/strings.xml`
- Test: `Project/app/src/test/java/com/example/auto_voice_recorder/input/VolumeGestureDetectorTest.kt`

**Interfaces:**
- Consumes: `RecordingService.ACTION_START_RECORDING` / `ACTION_STOP_RECORDING` from Task 3.
- Produces: `interface Cancellable { fun cancel() }`; `interface GestureScheduler { fun scheduleAfter(delayMs: Long, action: () -> Unit): Cancellable }`; `class VolumeGestureDetector(private val holdDurationMs: Long = 3000L, private val scheduler: GestureScheduler)` with `fun onVolumeDownKeyDown(onHoldThresholdReached: () -> Unit)`, `fun onVolumeDownKeyUp()`.

- [ ] **Step 1: Write a `FakeGestureScheduler`** (test-only) that records the last `(delayMs, action)` passed to `scheduleAfter`, returns a `Cancellable` that marks itself cancelled, and exposes a `fireIfPending()` helper to manually invoke the action (simulating elapsed time).
- [ ] **Step 2: Write failing tests in `VolumeGestureDetectorTest`**:
  - `onVolumeDownKeyDown schedules the hold callback after holdDurationMs`
  - `firing the scheduled action after the full hold invokes onHoldThresholdReached`
  - `onVolumeDownKeyUp before the hold elapses cancels the pending callback`
  - `a second onVolumeDownKeyDown while one is already pending replaces rather than stacks the pending callback`
- [ ] **Step 3: Run tests to verify they fail**

Run: `cd Project && ./gradlew :app:testDebugUnitTest --tests "*VolumeGestureDetectorTest*"`
Expected: FAIL

- [ ] **Step 4: Implement `GestureScheduler`, `Cancellable`, and `VolumeGestureDetector`** per the signatures above.
- [ ] **Step 5: Run tests to verify they pass**

Run: `cd Project && ./gradlew :app:testDebugUnitTest --tests "*VolumeGestureDetectorTest*"`
Expected: PASS

- [ ] **Step 6: Implement a real `HandlerGestureScheduler`** (`GestureScheduler` backed by `android.os.Handler(Looper.getMainLooper())`, `postDelayed`/`removeCallbacks` for cancellation). Not unit tested (thin Android wrapper).
- [ ] **Step 7: Implement `VolumeKeyAccessibilityService`** (`AccessibilityService`): overrides `onKeyEvent`; on `KEYCODE_VOLUME_DOWN` `ACTION_DOWN` calls `detector.onVolumeDownKeyDown { startIntent... }` (sends `Intent(this, RecordingService::class.java).setAction(ACTION_START_RECORDING)` via `startForegroundService`); on `KEYCODE_VOLUME_DOWN` `ACTION_UP` calls `detector.onVolumeDownKeyUp()`; on `KEYCODE_VOLUME_UP` `ACTION_DOWN` always sends `ACTION_STOP_RECORDING` (idempotent per Task 3's no-op-when-idle behavior); returns `false` from `onKeyEvent` so the system still applies the normal volume change. `onServiceConnected` constructs the real `VolumeGestureDetector(scheduler = HandlerGestureScheduler())`.
- [ ] **Step 8: Add `res/xml/accessibility_service_config.xml`** (`accessibilityEventTypes="typeAllMask"`, `accessibilityFlags="flagRequestFilterKeyEvents"`, `canRetrieveWindowContent="false"`, `description="@string/accessibility_service_description"`).
- [ ] **Step 9: Add `R.string.accessibility_service_description`** to `strings.xml` (plain description of volume-key recording control, no deceptive wording).
- [ ] **Step 10: Declare in `AndroidManifest.xml`**: `<service android:name=".service.VolumeKeyAccessibilityService" android:permission="android.permission.BIND_ACCESSIBILITY_SERVICE" android:exported="true"><intent-filter><action android:name="android.accessibilityservice.AccessibilityService" /></intent-filter><meta-data android:name="android.accessibilityservice" android:resource="@xml/accessibility_service_config" /></service>`.
- [ ] **Step 11: Build to verify manifest/service compile**

Run: `cd Project && ./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 12: Commit**

```bash
git add Project/app/src/main/java/com/example/auto_voice_recorder/input Project/app/src/main/java/com/example/auto_voice_recorder/service/VolumeKeyAccessibilityService.kt Project/app/src/main/res/xml/accessibility_service_config.xml Project/app/src/main/AndroidManifest.xml Project/app/src/main/res/values/strings.xml Project/app/src/test/java/com/example/auto_voice_recorder/input
git commit -m "feat: add volume-key gesture detection and accessibility service"
```

---

### Task 5: App lock (PIN + biometrics)

**Files:**
- Create: `Project/app/src/main/java/com/example/auto_voice_recorder/security/PinManager.kt`
- Create: `Project/app/src/main/java/com/example/auto_voice_recorder/security/DefaultPinManager.kt`
- Create: `Project/app/src/main/java/com/example/auto_voice_recorder/security/EncryptedPrefsFactory.kt`
- Create: `Project/app/src/main/java/com/example/auto_voice_recorder/security/BiometricAuthHelper.kt`
- Test: `Project/app/src/test/java/com/example/auto_voice_recorder/security/DefaultPinManagerTest.kt`

**Interfaces:**
- Produces: `interface PinManager { fun isPinSet(): Boolean; fun setPin(pin: String); fun verifyPin(pin: String): Boolean }`; `class DefaultPinManager(private val prefs: android.content.SharedPreferences) : PinManager`; `class EncryptedPrefsFactory { fun create(context: Context): SharedPreferences }` (wraps `EncryptedSharedPreferences.create`); `class BiometricAuthHelper { fun isBiometricAvailable(context: Context): Boolean; fun authenticate(activity: FragmentActivity, onSuccess: () -> Unit, onError: (String) -> Unit) }`.

- [ ] **Step 1: Write a `FakeSharedPreferences`** (test-only, implements `android.content.SharedPreferences` and its `Editor` over an in-memory `MutableMap<String, Any?>`).
- [ ] **Step 2: Write failing tests in `DefaultPinManagerTest`**:
  - `isPinSet is false before any PIN is set`
  - `after setPin, isPinSet is true`
  - `verifyPin returns true for the exact PIN that was set`
  - `verifyPin returns false for any other PIN`
  - `setPin followed by setPin with a new value invalidates the old PIN`
- [ ] **Step 3: Run tests to verify they fail**

Run: `cd Project && ./gradlew :app:testDebugUnitTest --tests "*DefaultPinManagerTest*"`
Expected: FAIL

- [ ] **Step 4: Implement `PinManager`/`DefaultPinManager`** (store a salted SHA-256 hash of the PIN plus the salt in two preference keys; `verifyPin` recomputes the hash with the stored salt and compares).
- [ ] **Step 5: Run tests to verify they pass**

Run: `cd Project && ./gradlew :app:testDebugUnitTest --tests "*DefaultPinManagerTest*"`
Expected: PASS

- [ ] **Step 6: Implement `EncryptedPrefsFactory`** (real `androidx.security.crypto.EncryptedSharedPreferences` using `MasterKey` with `AES256_GCM`). Not unit tested (thin Android/crypto wrapper).
- [ ] **Step 7: Implement `BiometricAuthHelper`** using `androidx.biometric.BiometricPrompt` with `BiometricPrompt.PromptInfo` requiring `BIOMETRIC_STRONG`; `isBiometricAvailable` checks `BiometricManager.canAuthenticate`. Not unit tested (thin Android wrapper, verified manually on device per spec).
- [ ] **Step 8: Commit**

```bash
git add Project/app/src/main/java/com/example/auto_voice_recorder/security Project/app/src/test/java/com/example/auto_voice_recorder/security
git commit -m "feat: add PIN storage and biometric auth helper"
```

---

### Task 6: Drive sync

**Files:**
- Create: `Project/app/src/main/java/com/example/auto_voice_recorder/sync/DriveUploader.kt`
- Create: `Project/app/src/main/java/com/example/auto_voice_recorder/sync/GoogleDriveUploader.kt`
- Create: `Project/app/src/main/java/com/example/auto_voice_recorder/sync/DriveSyncUseCase.kt`
- Create: `Project/app/src/main/java/com/example/auto_voice_recorder/sync/DriveSyncWorker.kt`
- Create: `Project/app/src/main/java/com/example/auto_voice_recorder/sync/SyncPreferences.kt`
- Create: `Project/app/src/main/java/com/example/auto_voice_recorder/sync/DefaultSyncPreferences.kt`
- Create: `Project/app/src/main/java/com/example/auto_voice_recorder/sync/GoogleSignInHelper.kt`
- Test: `Project/app/src/test/java/com/example/auto_voice_recorder/sync/DriveSyncUseCaseTest.kt`

**Interfaces:**
- Consumes: `RecordingsRepository` (Task 2): `pendingSyncRecordings()`, `markSynced(id)`.
- Produces: `interface DriveUploader { suspend fun upload(file: java.io.File, recording: Recording): Result<Unit> }`; `data class SyncSummary(val uploaded: Int, val failed: Int)`; `class DriveSyncUseCase(private val repository: RecordingsRepository, private val uploader: DriveUploader, private val syncEnabled: () -> Boolean)` with `suspend fun syncPending(): SyncSummary`; `interface SyncPreferences { fun isEnabled(): Boolean; fun setEnabled(enabled: Boolean) }`, `class DefaultSyncPreferences(private val prefs: android.content.SharedPreferences) : SyncPreferences`.

- [ ] **Step 1: Write a `FakeDriveUploader`** (test-only, implements `DriveUploader`, configurable to succeed or return `Result.failure(...)` per call, records which recordings it was asked to upload).
- [ ] **Step 2: Write failing tests in `DriveSyncUseCaseTest`** (using `RecordingsRepository(FakeRecordingDao())` from Task 2, pre-seeded with `PENDING` recordings):
  - `syncPending with syncEnabled true uploads every PENDING recording and marks each SYNCED`
  - `syncPending with syncEnabled false uploads nothing and leaves all recordings PENDING`
  - `syncPending does not mark a recording SYNCED when its upload fails`
  - `syncPending returns a SyncSummary with correct uploaded and failed counts`
- [ ] **Step 3: Run tests to verify they fail**

Run: `cd Project && ./gradlew :app:testDebugUnitTest --tests "*DriveSyncUseCaseTest*"`
Expected: FAIL

- [ ] **Step 4: Implement `DriveUploader`, `SyncSummary`, and `DriveSyncUseCase`** per the signatures above.
- [ ] **Step 5: Run tests to verify they pass**

Run: `cd Project && ./gradlew :app:testDebugUnitTest --tests "*DriveSyncUseCaseTest*"`
Expected: PASS

- [ ] **Step 6: Implement `SyncPreferences`/`DefaultSyncPreferences`** (boolean flag in `SharedPreferences`, key `"drive_sync_enabled"`, default `false`).
- [ ] **Step 7: Implement `GoogleSignInHelper`** (thin wrapper requesting the `DriveScopes.DRIVE_APPDATA` scope via `GoogleSignInClient`, exposing `fun signIn(activity: Activity, launcher: ActivityResultLauncher<Intent>)` and `fun lastAccount(context: Context): GoogleSignInAccount?`). Not unit tested (thin Android/Play Services wrapper).
- [ ] **Step 8: Implement `GoogleDriveUploader`** (real `DriveUploader` using `com.google.api.services.drive.Drive`, `spaces = "appDataFolder"`, built from the signed-in `GoogleSignInAccount`'s credential). Not unit tested (needs real network/Drive, per spec).
- [ ] **Step 9: Implement `DriveSyncWorker`** (`CoroutineWorker`): `doWork()` builds `DriveSyncUseCase` from the real repository, `GoogleDriveUploader`, and `DefaultSyncPreferences::isEnabled`, calls `syncPending()`, returns `Result.success()` on a summary with `failed == 0`, otherwise `Result.retry()`.
- [ ] **Step 10: Build to verify it compiles**

Run: `cd Project && ./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 11: Commit**

```bash
git add Project/app/src/main/java/com/example/auto_voice_recorder/sync Project/app/src/test/java/com/example/auto_voice_recorder/sync
git commit -m "feat: add Drive sync use case, worker and preferences"
```

---

### Task 7: Lock screen UI

**Files:**
- Create: `Project/app/src/main/java/com/example/auto_voice_recorder/ui/lock/LockViewModel.kt`
- Create: `Project/app/src/main/java/com/example/auto_voice_recorder/ui/lock/LockScreen.kt`
- Test: `Project/app/src/test/java/com/example/auto_voice_recorder/ui/lock/LockViewModelTest.kt`

**Interfaces:**
- Consumes: `PinManager` (Task 5).
- Produces: `sealed interface LockUiState { data object Idle: LockUiState; data object Error: LockUiState; data object Unlocked: LockUiState }`; `class LockViewModel(private val pinManager: PinManager) : ViewModel()` with `val uiState: StateFlow<LockUiState>`, `fun submitPin(pin: String)`, `fun onBiometricSuccess()`.

- [ ] **Step 1: Write a `FakePinManager`** (test-only, implements `PinManager` in-memory).
- [ ] **Step 2: Write failing tests in `LockViewModelTest`**:
  - `submitPin with the correct PIN moves uiState to Unlocked`
  - `submitPin with an incorrect PIN moves uiState to Error`
  - `onBiometricSuccess moves uiState to Unlocked regardless of PIN state`
  - `uiState starts as Idle`
- [ ] **Step 3: Run tests to verify they fail**

Run: `cd Project && ./gradlew :app:testDebugUnitTest --tests "*LockViewModelTest*"`
Expected: FAIL

- [ ] **Step 4: Implement `LockUiState` and `LockViewModel`** per the signatures above.
- [ ] **Step 5: Run tests to verify they pass**

Run: `cd Project && ./gradlew :app:testDebugUnitTest --tests "*LockViewModelTest*"`
Expected: PASS

- [ ] **Step 6: Implement `LockScreen` Composable** (PIN pad bound to `submitPin`, a "Usar huella/Face" button that calls `BiometricAuthHelper.authenticate` from Task 5 and forwards success to `onBiometricSuccess`, an error message shown on `LockUiState.Error`). Not unit tested (Compose UI, verified manually).
- [ ] **Step 7: Commit**

```bash
git add Project/app/src/main/java/com/example/auto_voice_recorder/ui/lock Project/app/src/test/java/com/example/auto_voice_recorder/ui/lock
git commit -m "feat: add lock screen UI"
```

---

### Task 8: Recordings list + playback UI

**Files:**
- Create: `Project/app/src/main/java/com/example/auto_voice_recorder/ui/recordings/RecordingUiModel.kt`
- Create: `Project/app/src/main/java/com/example/auto_voice_recorder/ui/recordings/SystemStatusChecker.kt`
- Create: `Project/app/src/main/java/com/example/auto_voice_recorder/ui/recordings/RecordingsViewModel.kt`
- Create: `Project/app/src/main/java/com/example/auto_voice_recorder/ui/recordings/AudioPlayerController.kt`
- Create: `Project/app/src/main/java/com/example/auto_voice_recorder/ui/recordings/RecordingsListScreen.kt`
- Test: `Project/app/src/test/java/com/example/auto_voice_recorder/ui/recordings/RecordingsViewModelTest.kt`

**Interfaces:**
- Consumes: `RecordingsRepository` (Task 2); `RecordingStatus.isRecording` (Task 3).
- Produces: `data class RecordingUiModel(val id: Long, val createdAtEpochMs: Long, val durationMs: Long, val syncState: SyncState)`; `interface SystemStatusChecker { fun hasRecordAudioPermission(): Boolean; fun isAccessibilityServiceEnabled(): Boolean }` (real impl `DefaultSystemStatusChecker(context: Context)` checking `ContextCompat.checkSelfPermission` and `AccessibilityManager.getEnabledAccessibilityServiceList`); `class RecordingsViewModel(private val repository: RecordingsRepository, isRecordingNow: StateFlow<Boolean>, private val statusChecker: SystemStatusChecker) : ViewModel()` with `val recordings: StateFlow<List<RecordingUiModel>>`, `val isRecordingNow: StateFlow<Boolean>`, `val setupWarning: StateFlow<SetupWarning?>` (`sealed interface SetupWarning { data object MissingMicPermission: SetupWarning; data object AccessibilityDisabled: SetupWarning }`), `fun delete(id: Long)`, `fun refreshStatus()`.

- [ ] **Step 1: Write a `FakeSystemStatusChecker`** (test-only, implements `SystemStatusChecker` with settable booleans).
- [ ] **Step 2: Write failing tests in `RecordingsViewModelTest`** (using `RecordingsRepository(FakeRecordingDao())` from Task 2, a `MutableStateFlow(false)` standing in for `RecordingStatus.isRecording`):
  - `recordings reflects everything the repository holds, mapped to RecordingUiModel`
  - `delete removes the recording from the repository and from the exposed recordings list`
  - `isRecordingNow mirrors the provided isRecording flow`
  - `setupWarning is MissingMicPermission when hasRecordAudioPermission is false`
  - `setupWarning is AccessibilityDisabled when the permission is granted but the accessibility service is not enabled`
  - `setupWarning is null when both permission and accessibility service are granted/enabled`
- [ ] **Step 3: Run tests to verify they fail**

Run: `cd Project && ./gradlew :app:testDebugUnitTest --tests "*RecordingsViewModelTest*"`
Expected: FAIL

- [ ] **Step 4: Implement `RecordingUiModel`, `SystemStatusChecker`, `SetupWarning`, and `RecordingsViewModel`** per the signatures above (`refreshStatus()` recomputes `setupWarning` from `statusChecker`, called on init and whenever the screen resumes).
- [ ] **Step 5: Run tests to verify they pass**

Run: `cd Project && ./gradlew :app:testDebugUnitTest --tests "*RecordingsViewModelTest*"`
Expected: PASS

- [ ] **Step 6: Implement `DefaultSystemStatusChecker`** (real `SystemStatusChecker`). Not unit tested (thin Android wrapper).
- [ ] **Step 7: Implement `AudioPlayerController`** (thin wrapper around `android.media.MediaPlayer`: `fun play(filePath: String, onCompletion: () -> Unit)`, `fun stop()`). Not unit tested (Android media framework).
- [ ] **Step 8: Implement `RecordingsListScreen` Composable** (banner shown when `isRecordingNow` is true with text `"Grabando ahora"`; a separate dismissible warning banner driven by `setupWarning` — `"Concede permiso de micrófono"` / `"Activa el servicio de accesibilidad"` with a button jumping to app settings or `Settings.ACTION_ACCESSIBILITY_SETTINGS` respectively; list of `recordings` each with play/stop and delete controls wired to `AudioPlayerController` and `RecordingsViewModel.delete`; calls `refreshStatus()` from an `ON_RESUME` lifecycle observer). Not unit tested (Compose UI, verified manually).
- [ ] **Step 9: Commit**

```bash
git add Project/app/src/main/java/com/example/auto_voice_recorder/ui/recordings Project/app/src/test/java/com/example/auto_voice_recorder/ui/recordings
git commit -m "feat: add recordings list and playback UI"
```

---

### Task 9: Settings UI (Drive toggle, PIN change, accessibility link)

**Files:**
- Create: `Project/app/src/main/java/com/example/auto_voice_recorder/ui/settings/SettingsViewModel.kt`
- Create: `Project/app/src/main/java/com/example/auto_voice_recorder/ui/settings/SettingsScreen.kt`
- Test: `Project/app/src/test/java/com/example/auto_voice_recorder/ui/settings/SettingsViewModelTest.kt`

**Interfaces:**
- Consumes: `SyncPreferences` (Task 6), `PinManager` (Task 5).
- Produces: `class SettingsViewModel(private val syncPreferences: SyncPreferences, private val pinManager: PinManager) : ViewModel()` with `val isDriveSyncEnabled: StateFlow<Boolean>`, `fun setDriveSyncEnabled(enabled: Boolean)`, `fun changePin(newPin: String)`.

- [ ] **Step 1: Write a `FakeSyncPreferences`** (test-only, implements `SyncPreferences` in-memory).
- [ ] **Step 2: Write failing tests in `SettingsViewModelTest`**:
  - `isDriveSyncEnabled reflects the current SyncPreferences value`
  - `setDriveSyncEnabled(true) persists through syncPreferences and updates isDriveSyncEnabled`
  - `changePin updates the PinManager so the new PIN verifies and the old one does not`
- [ ] **Step 3: Run tests to verify they fail**

Run: `cd Project && ./gradlew :app:testDebugUnitTest --tests "*SettingsViewModelTest*"`
Expected: FAIL

- [ ] **Step 4: Implement `SettingsViewModel`** per the signature above.
- [ ] **Step 5: Run tests to verify they pass**

Run: `cd Project && ./gradlew :app:testDebugUnitTest --tests "*SettingsViewModelTest*"`
Expected: PASS

- [ ] **Step 6: Implement `SettingsScreen` Composable** (switch bound to `isDriveSyncEnabled`/`setDriveSyncEnabled`, a "Conectar con Google" button calling `GoogleSignInHelper.signIn` from Task 6 before enabling sync, a PIN-change field calling `changePin`, and a button that opens `Settings.ACTION_ACCESSIBILITY_SETTINGS` with a line of plain-language text telling the user to enable "Auto Voice Recorder" there). Not unit tested (Compose UI, verified manually).
- [ ] **Step 7: Commit**

```bash
git add Project/app/src/main/java/com/example/auto_voice_recorder/ui/settings Project/app/src/test/java/com/example/auto_voice_recorder/ui/settings
git commit -m "feat: add settings UI for Drive sync, PIN and accessibility link"
```

---

### Task 10: Navigation wiring, MainActivity, and end-to-end verification

**Files:**
- Create: `Project/app/src/main/java/com/example/auto_voice_recorder/AppContainer.kt`
- Create: `Project/app/src/main/java/com/example/auto_voice_recorder/ui/navigation/AppNavHost.kt`
- Modify: `Project/app/src/main/java/com/example/auto_voice_recorder/MainActivity.kt`

**Interfaces:**
- Consumes: every ViewModel/repository/manager produced in Tasks 2–9.
- Produces: `class AppContainer(context: Context)` exposing lazily-built singletons: `val database: AppDatabase`, `val recordingsRepository: RecordingsRepository`, `val pinManager: PinManager`, `val syncPreferences: SyncPreferences`; `@Composable fun AppNavHost(container: AppContainer)` with routes `"lock"` → `"recordings"` ↔ `"settings"`.

- [ ] **Step 1: Implement `AppContainer`** building `AppDatabase` (Room, `Room.databaseBuilder`), `RecordingsRepository(database.recordingDao())`, `DefaultPinManager(EncryptedPrefsFactory().create(context))`, `DefaultSyncPreferences(EncryptedPrefsFactory().create(context))`.
- [ ] **Step 2: Implement `AppNavHost`** using `androidx.navigation.compose.NavHost`: start destination `"lock"` rendering `LockScreen` wired to `LockViewModel(container.pinManager)`, navigating to `"recordings"` on `LockUiState.Unlocked`; `"recordings"` renders `RecordingsListScreen` wired to `RecordingsViewModel(container.recordingsRepository, RecordingStatus.isRecording)` with a button to `"settings"`; `"settings"` renders `SettingsScreen` wired to `SettingsViewModel(container.syncPreferences, container.pinManager)`.
- [ ] **Step 3: Update `MainActivity`** to build an `AppContainer` from `applicationContext` and call `setContent { AppNavHost(container) }`.
- [ ] **Step 4: Build the app**

Run: `cd Project && ./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Run the full unit test suite**

Run: `cd Project && ./gradlew :app:testDebugUnitTest`
Expected: all tests PASS

- [ ] **Step 6: Commit**

```bash
git add Project/app/src/main/java/com/example/auto_voice_recorder/AppContainer.kt Project/app/src/main/java/com/example/auto_voice_recorder/ui/navigation Project/app/src/main/java/com/example/auto_voice_recorder/MainActivity.kt
git commit -m "feat: wire navigation and dependency container"
```

- [ ] **Step 7: Manual on-device verification** (per spec: MediaRecorder and physical key events aren't reliably simulated on an emulator), on a real Android 10+ device:
  - Grant microphone + notification permissions; enable the accessibility service via the in-app link.
  - Lock the phone, hold volume-down 3s, confirm the `"Recording"` notification appears.
  - Press volume-up, confirm the notification disappears and the recording stops.
  - Open the app, unlock with PIN then with biometrics, confirm the new recording is listed, playable, and deletable.
  - Enable Drive sync, sign in, confirm the recording uploads and its state updates; disable sync, confirm a new recording stays un-uploaded.
