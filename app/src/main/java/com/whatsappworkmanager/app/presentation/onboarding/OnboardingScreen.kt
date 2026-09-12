package com.whatsappworkmanager.app.presentation.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.whatsappworkmanager.app.R
import com.whatsappworkmanager.app.WwmApplication
import com.whatsappworkmanager.app.presentation.components.CountryPickerDialog
import com.whatsappworkmanager.app.utils.CountryCode
import com.whatsappworkmanager.app.utils.CountryCodePrefs
import com.whatsappworkmanager.app.utils.CountryCodes
import com.whatsappworkmanager.app.utils.IntentHelper
import com.whatsappworkmanager.app.utils.LocaleHelper
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * First-run flow: language, then home country (used to default every phone-number field
 * app-wide — see [CountryCodePrefs]), then which WhatsApp app to use, then the existing
 * explanation steps, then Notification Access / battery setup. Language, country, and the
 * WhatsApp-app choice are all also changeable later from Settings — this just makes sure
 * they're decided up front instead of the app silently guessing (Egypt, regular WhatsApp,
 * device language) and the person having to go find where to change it afterwards.
 */
@Composable
fun OnboardingScreen(onFinished: () -> Unit) {
    var step by remember { mutableIntStateOf(0) }
    val context = LocalContext.current
    val app = context.applicationContext as WwmApplication
    val scope = rememberCoroutineScopeCompat()
    var selectedWhatsappVariant by remember { mutableStateOf(IntentHelper.WHATSAPP_VARIANT_AUTO) }
    var selectedCountryCode by remember { mutableStateOf(CountryCodePrefs.getLastUsed(context)) }
    var showCountryPicker by remember { mutableStateOf(false) }
    // Picking a language on step 0 calls activity.recreate() to force the new language to
    // actually apply — but recreate() destroys and rebuilds this whole Composable, so the
    // `step` state above resets back to 0 every time. Without this check, that made step 0
    // impossible to ever get past: tap a language, the activity recreates, you're back on the
    // exact same "choose your language" screen with no visible sign anything happened. This
    // reads what language was actually saved (which *does* survive the recreate, since it's
    // persisted to DataStore) and skips straight to step 1 if one was already chosen.
    var readyToShow by remember { mutableStateOf(false) }
    // Live-tracked so the "Continue to app" button can require both to actually be granted
    // rather than just trusting that tapping the buttons above worked — the user genuinely
    // leaves this screen to go grant these in system Settings, so re-checking on ON_RESUME
    // (the same pattern the Dashboard's own Refresh/resume handling already uses) is what
    // notices they came back having actually done it.
    var notificationAccessEnabled by remember { mutableStateOf(IntentHelper.isNotificationAccessEnabled(context)) }
    var batteryOptimizationExempted by remember { mutableStateOf(IntentHelper.isIgnoringBatteryOptimizations(context)) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                notificationAccessEnabled = IntentHelper.isNotificationAccessEnabled(context)
                batteryOptimizationExempted = IntentHelper.isIgnoringBatteryOptimizations(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(Unit) {
        val savedLanguage = app.settingsDataStore.language.first()
        if (savedLanguage != "system" && step == 0) {
            step = 1
        }
        readyToShow = true
    }

    if (!readyToShow) return

    Box(modifier = Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(20.dp)) {
            when (step) {
                0 -> {
                    Text(stringResource(R.string.onboarding_language_title), style = MaterialTheme.typography.headlineMedium)
                    Text(
                        stringResource(R.string.onboarding_language_subtitle),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.secondary
                    )
                    Button(
                        onClick = {
                            LocaleHelper.setLanguage(app, "en")
                            scope.launch {
                                // Written first and awaited (not fire-and-forget) — recreate()
                                // right below tears down this coroutine's scope along with
                                // everything else, so the write must actually land on disk
                                // before that happens, or the language choice could be lost.
                                app.settingsDataStore.setLanguage("en")
                                (context as? android.app.Activity)?.recreate()
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(stringResource(R.string.onboarding_language_english)) }
                    Button(
                        onClick = {
                            LocaleHelper.setLanguage(app, "ar")
                            scope.launch {
                                app.settingsDataStore.setLanguage("ar")
                                (context as? android.app.Activity)?.recreate()
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(stringResource(R.string.onboarding_language_arabic)) }
                }
                1 -> {
                    Text(stringResource(R.string.onboarding_country_title), style = MaterialTheme.typography.headlineMedium)
                    Text(
                        stringResource(R.string.onboarding_country_subtitle),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.secondary
                    )
                    val currentLanguage = androidx.compose.ui.platform.LocalConfiguration.current.locales[0].language
                    val current = CountryCodes.byDialCode(selectedCountryCode)
                    OutlinedButton(onClick = { showCountryPicker = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(
                            if (current != null) {
                                stringResource(R.string.onboarding_country_selected_fmt, current.flag, current.displayName(currentLanguage), current.dialCode)
                            } else {
                                stringResource(R.string.country_picker_title)
                            }
                        )
                    }
                    Button(
                        onClick = {
                            CountryCodePrefs.setLastUsed(context, selectedCountryCode)
                            step++
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(stringResource(R.string.onboarding_next)) }
                }
                2 -> {
                    Text(stringResource(R.string.onboarding_whatsapp_variant_title), style = MaterialTheme.typography.headlineMedium)
                    Text(
                        stringResource(R.string.onboarding_whatsapp_variant_subtitle),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.secondary
                    )
                    listOf(
                        IntentHelper.WHATSAPP_VARIANT_AUTO to stringResource(R.string.onboarding_whatsapp_variant_auto),
                        IntentHelper.WHATSAPP_VARIANT_REGULAR to stringResource(R.string.onboarding_whatsapp_variant_regular),
                        IntentHelper.WHATSAPP_VARIANT_BUSINESS to stringResource(R.string.onboarding_whatsapp_variant_business)
                    ).forEach { (value, label) ->
                        val isSelected = selectedWhatsappVariant == value
                        // Explicit `() -> Unit` type is required here: without it, Kotlin
                        // infers this lambda's return type from its *last* statement — which
                        // is `scope.launch { ... }`, returning a `Job` — making the whole
                        // lambda's inferred type `() -> Job` instead of the `() -> Unit` that
                        // Button/OutlinedButton's `onClick` parameter actually expects.
                        val onPick: () -> Unit = {
                            selectedWhatsappVariant = value
                            scope.launch { app.settingsDataStore.setWhatsappVariant(value) }
                        }
                        if (isSelected) {
                            Button(onClick = onPick, modifier = Modifier.fillMaxWidth()) { Text(label) }
                        } else {
                            OutlinedButton(onClick = onPick, modifier = Modifier.fillMaxWidth()) { Text(label) }
                        }
                    }
                    Button(onClick = { step++ }, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.onboarding_next))
                    }
                }
                3 -> {
                    Text(stringResource(R.string.onboarding_1_title), style = MaterialTheme.typography.headlineMedium)
                    Text(stringResource(R.string.onboarding_1_body), style = MaterialTheme.typography.bodyLarge)
                }
                4 -> Text(stringResource(R.string.onboarding_2_title), style = MaterialTheme.typography.headlineMedium)
                5 -> Text(stringResource(R.string.onboarding_3_title), style = MaterialTheme.typography.headlineMedium)
                6 -> {
                    Text(stringResource(R.string.onboarding_4_title), style = MaterialTheme.typography.headlineMedium)
                    Text(stringResource(R.string.privacy_body), style = MaterialTheme.typography.bodyMedium)
                }
            }

            if (step in 3..5) {
                Button(onClick = { step++ }) { Text(stringResource(R.string.onboarding_next)) }
            } else if (step == 6) {
                if (notificationAccessEnabled) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text(
                            stringResource(R.string.onboarding_enable_notifications),
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                } else {
                    Button(onClick = {
                        IntentHelper.openNotificationAccessSettings(context)
                    }) { Text(stringResource(R.string.onboarding_enable_notifications)) }
                }
                if (batteryOptimizationExempted) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text(
                            stringResource(R.string.settings_battery_optimization),
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                } else {
                    Button(onClick = {
                        IntentHelper.openBatteryOptimizationSettings(context)
                    }) { Text(stringResource(R.string.settings_battery_optimization)) }
                }
                Text(
                    stringResource(R.string.settings_battery_optimization_body),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.secondary
                )
                if (!notificationAccessEnabled || !batteryOptimizationExempted) {
                    Text(
                        stringResource(R.string.onboarding_grant_both_hint),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
                Button(
                    onClick = {
                        scope.launch {
                            app.settingsDataStore.setOnboardingDone(true)
                            onFinished()
                        }
                    },
                    enabled = notificationAccessEnabled && batteryOptimizationExempted
                ) { Text(stringResource(R.string.onboarding_continue_to_app)) }
            }
        }
    }

    if (showCountryPicker) {
        CountryPickerDialog(
            selectedDialCode = selectedCountryCode,
            onDismiss = { showCountryPicker = false },
            onSelect = { country: CountryCode ->
                selectedCountryCode = country.dialCode
                showCountryPicker = false
            }
        )
    }
}

@Composable
private fun rememberCoroutineScopeCompat() = androidx.compose.runtime.rememberCoroutineScope()
