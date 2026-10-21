package com.example.auto_voice_recorder.ui.recordings

/**
 * Test-only [SystemStatusChecker] with settable booleans, standing in for the
 * real Android-backed permission/accessibility checks.
 */
class FakeSystemStatusChecker(
    var permissionGranted: Boolean = true,
    var accessibilityEnabled: Boolean = true,
) : SystemStatusChecker {
    override fun hasRecordAudioPermission(): Boolean = permissionGranted

    override fun isAccessibilityServiceEnabled(): Boolean = accessibilityEnabled
}
