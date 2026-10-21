package com.example.auto_voice_recorder.ui.lock

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Tracks whether the app UI is locked. Starts locked, so a fresh process (including one
 * restored after process death with a saved navigation back stack) always requires
 * unlocking again; re-locks whenever the app goes to the background.
 *
 * Plain class so the rules are JVM-testable; the app uses the process-wide [process]
 * instance, driven by the Activity lifecycle from the navigation host.
 */
class AppLockController {

    private val _isLocked = MutableStateFlow(true)
    val isLocked: StateFlow<Boolean> = _isLocked.asStateFlow()

    private var skipNextBackgroundRelock = false

    fun unlock() {
        _isLocked.value = false
    }

    /**
     * Call right before launching an in-app flow that briefly covers the activity (Google
     * sign-in, the runtime-permission dialog), so returning from it doesn't relock and
     * drop that flow's result. Only covers the very next background/foreground cycle.
     */
    fun skipNextBackgroundRelock() {
        skipNextBackgroundRelock = true
    }

    /** Activity stopped. Config changes (rotation) are not "leaving the app". */
    fun onAppBackgrounded(isChangingConfigurations: Boolean) {
        if (isChangingConfigurations) return
        if (skipNextBackgroundRelock) {
            skipNextBackgroundRelock = false
            return
        }
        _isLocked.value = true
    }

    /** Activity resumed: any pending one-shot exemption has served its purpose (or was unused). */
    fun onAppResumed() {
        skipNextBackgroundRelock = false
    }

    companion object {
        /** Process-wide instance; dies (and so resets to locked) with the process. */
        val process: AppLockController = AppLockController()
    }
}
