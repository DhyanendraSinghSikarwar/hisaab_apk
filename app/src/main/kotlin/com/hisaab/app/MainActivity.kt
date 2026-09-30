package com.hisaab.app

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.hisaab.app.security.AppLockGate
import com.hisaab.app.settings.AppSettingsStore
import com.hisaab.app.settings.ThemeMode
import com.hisaab.app.ui.lock.LockScreen
import com.hisaab.app.ui.nav.HisaabNavHost
import com.hisaab.app.ui.theme.HisaabTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

/** FragmentActivity rather than ComponentActivity: BiometricPrompt needs it. */
@AndroidEntryPoint
class MainActivity : FragmentActivity() {
    @Inject lateinit var settings: AppSettingsStore

    private var lockEnabled by mutableStateOf<Boolean?>(null)
    private var unlocked by mutableStateOf(false)
    private var theme by mutableStateOf(ThemeMode.SYSTEM)

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Hold the splash until we know whether to show the lock, so content never flashes.
        splash.setKeepOnScreenCondition { lockEnabled == null }

        lifecycleScope.launch {
            settings.settings.collect { s ->
                // Turning the lock on from inside the app must not lock the user out mid-session.
                if (lockEnabled == false && s.appLock) unlocked = true
                lockEnabled = s.appLock
                theme = s.theme
                if (s.appLock && !unlocked) promptUnlock()
            }
        }

        setContent {
            HisaabTheme(theme) {
                if (lockEnabled == true && !unlocked) LockScreen(onUnlock = ::promptUnlock) else if (lockEnabled != null) HisaabNavHost()
            }
        }
    }

    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations && !AppLockGate.consumeSkip()) unlocked = false
    }

    override fun onResume() {
        super.onResume()
        if (lockEnabled == true && !unlocked) promptUnlock()
    }

    private var prompting = false

    private fun promptUnlock() {
        if (prompting || unlocked) return
        prompting = true
        val prompt = BiometricPrompt(this, ContextCompat.getMainExecutor(this), object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) { prompting = false; unlocked = true }
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) { prompting = false }
        })
        prompt.authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle("Unlock Hisaab")
                .setSubtitle("Your transactions are protected")
                .setAllowedAuthenticators(BIOMETRIC_WEAK or DEVICE_CREDENTIAL)
                .build(),
        )
    }
}
