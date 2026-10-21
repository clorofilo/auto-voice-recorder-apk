package com.example.auto_voice_recorder

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.fragment.app.FragmentActivity
import com.example.auto_voice_recorder.ui.navigation.AppNavHost
import com.example.auto_voice_recorder.ui.theme.AutovoicerecorderTheme

/**
 * Extends [FragmentActivity] (not [ComponentActivity]) because [LockScreen]'s biometric
 * unlock button requires casting the context to a [FragmentActivity] to drive
 * [androidx.biometric.BiometricPrompt]; a plain ComponentActivity makes that cast fail
 * and silently hides the biometric button.
 */
class MainActivity : FragmentActivity() {

    private val container by lazy { AppContainer(applicationContext) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AutovoicerecorderTheme {
                AppNavHost(container = container)
            }
        }
    }
}
