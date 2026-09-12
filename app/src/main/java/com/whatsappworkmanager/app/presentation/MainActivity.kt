package com.whatsappworkmanager.app.presentation

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.runtime.collectAsState
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import com.whatsappworkmanager.app.WwmApplication
import com.whatsappworkmanager.app.presentation.navigation.WwmNavGraph
import com.whatsappworkmanager.app.presentation.theme.ThemeMode
import com.whatsappworkmanager.app.presentation.theme.WwmTheme
import com.whatsappworkmanager.app.utils.LocaleHelper
import com.whatsappworkmanager.app.worker.WorkScheduler

class MainActivity : ComponentActivity() {

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* no-op either way */ }

    // Runs before onCreate() — this is the mechanism that actually makes language switching
    // work reliably. `AppCompatDelegate.setApplicationLocales()` alone (called from
    // LocaleHelper/SettingsViewModel) is the officially documented approach, but in practice
    // didn't reliably apply to this Activity, since it extends plain `ComponentActivity` rather
    // than `AppCompatActivity` — the "no AppCompatActivity required" compat path for per-app
    // language has had inconsistent behavior across OEM Android builds below API 33. Wrapping
    // the base Context directly, the classic pre-AndroidX technique, works unconditionally: by
    // the time `setContent { }` below runs, every `stringResource()` call already reads from a
    // Configuration forced to the saved language, regardless of Activity base class or Android
    // version.
    override fun attachBaseContext(newBase: Context) {
        val languageCode = LocaleHelper.readSavedLanguage(newBase)
        super.attachBaseContext(LocaleHelper.wrapContext(newBase, languageCode))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val app = applicationContext as WwmApplication
        WorkScheduler.scheduleDailyCleanup(app)
        WorkScheduler.scheduleNotificationCatchUp(app)

        // Android 13+ (API 33) requires the POST_NOTIFICATIONS *runtime* permission, on top of
        // the manifest declaration — without this, EVERY notification this app posts (scheduled
        // message reminders, important-message alerts, summary notifications) is silently
        // dropped by the system, with no error and no crash. This was never requested anywhere
        // before, which is very likely why scheduled reminders looked like they "did nothing".
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                this, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        lifecycleScope.launch {
            app.settingsDataStore.ensurePremiumDefault()
        }

        setContent {
            val themeSetting by app.settingsDataStore.theme.collectAsState(initial = "premium")
            val themeMode = when (themeSetting) {
                "light" -> ThemeMode.LIGHT
                "dark" -> ThemeMode.DARK
                "premium" -> ThemeMode.PREMIUM
                else -> ThemeMode.SYSTEM
            }
            WwmTheme(themeMode = themeMode) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    WwmNavGraph()
                }
            }
        }
    }
}
