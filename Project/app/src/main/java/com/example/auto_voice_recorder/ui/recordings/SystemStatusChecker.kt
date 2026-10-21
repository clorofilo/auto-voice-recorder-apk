package com.example.auto_voice_recorder.ui.recordings

import android.Manifest
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.pm.PackageManager
import android.view.accessibility.AccessibilityManager
import androidx.core.content.ContextCompat
import com.example.auto_voice_recorder.service.VolumeKeyAccessibilityService

/** Checks the two preconditions the recorder needs to actually capture audio. */
interface SystemStatusChecker {
    fun hasRecordAudioPermission(): Boolean
    fun isAccessibilityServiceEnabled(): Boolean
}

/** Real [SystemStatusChecker] backed by the framework permission and accessibility APIs. */
class DefaultSystemStatusChecker(private val context: Context) : SystemStatusChecker {

    override fun hasRecordAudioPermission(): Boolean =
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO,
        ) == PackageManager.PERMISSION_GRANTED

    override fun isAccessibilityServiceEnabled(): Boolean {
        val accessibilityManager =
            context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
                ?: return false

        val enabledServices = accessibilityManager.getEnabledAccessibilityServiceList(
            AccessibilityServiceInfo.FEEDBACK_ALL_MASK,
        )

        return enabledServices.any { serviceInfo ->
            val resolvedInfo = serviceInfo.resolveInfo?.serviceInfo
            resolvedInfo != null &&
                resolvedInfo.packageName == context.packageName &&
                resolvedInfo.name == VolumeKeyAccessibilityService::class.java.name
        }
    }
}
