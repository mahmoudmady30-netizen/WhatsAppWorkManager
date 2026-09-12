package com.whatsappworkmanager.app.presentation.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.whatsappworkmanager.app.R
import com.whatsappworkmanager.app.WwmApplication
import com.whatsappworkmanager.app.presentation.components.ScreenPadding
import com.whatsappworkmanager.app.presentation.theme.OkGreen
import com.whatsappworkmanager.app.utils.IntentHelper

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onOpenPrivacy: () -> Unit,
    onOpenScheduledMessages: () -> Unit,
    onOpenWorkSchedules: () -> Unit,
    onOpenImportantPeople: () -> Unit,
    onOpenKeywordRules: () -> Unit,
    onOpenReplyPhrases: () -> Unit
) {
    val context = LocalContext.current
    val app = context.applicationContext as WwmApplication
    val viewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory(app))
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    var showLanguageDialog by remember { mutableStateOf(false) }
    var showThemeDialog by remember { mutableStateOf(false) }
    var showAiProviderDialog by remember { mutableStateOf(false) }
    var showApiKeyDialog by remember { mutableStateOf(false) }
    var showRetentionDialog by remember { mutableStateOf(false) }
    var showWhatsappVariantDialog by remember { mutableStateOf(false) }
    var showClearDataConfirm by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }
    var showCountryPickerDialog by remember { mutableStateOf(false) }
    var defaultCountryCode by remember { mutableStateOf(com.whatsappworkmanager.app.utils.CountryCodePrefs.getLastUsed(context)) }

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.settings_title)) }) }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxWidth(),
            contentPadding = ScreenPadding,
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            item {
                SettingsSectionHeader(stringResource(R.string.settings_section_permissions))
                SettingsGroupCard {
                    SettingsRow(
                        title = stringResource(R.string.settings_notification_access),
                        subtitle = if (IntentHelper.isNotificationAccessEnabled(context)) {
                            stringResource(R.string.settings_status_enabled)
                        } else {
                            stringResource(R.string.settings_status_disabled)
                        },
                        onClick = { IntentHelper.openNotificationAccessSettings(context) }
                    )
                    HorizontalDivider()
                    SettingsRow(
                        title = stringResource(R.string.settings_battery_optimization),
                        subtitle = stringResource(R.string.settings_battery_optimization_body),
                        onClick = { IntentHelper.openBatteryOptimizationSettings(context) }
                    )
                    HorizontalDivider()
                    SettingsRow(
                        title = stringResource(R.string.muted_chats_help_title),
                        subtitle = stringResource(R.string.muted_chats_help_body),
                        onClick = { IntentHelper.openNotificationSettingsForWhatsApp(context) }
                    )
                }
            }

            item {
                SettingsSectionHeader(stringResource(R.string.settings_section_automation))
                SettingsGroupCard {
                    SettingsRow(
                        title = stringResource(R.string.settings_summary_schedule),
                        subtitle = stringResource(R.string.settings_summary_schedule_subtitle),
                        onClick = onOpenWorkSchedules
                    )
                    HorizontalDivider()
                    SettingsRow(
                        title = stringResource(R.string.scheduled_messages_title),
                        subtitle = stringResource(R.string.settings_scheduled_messages_subtitle),
                        onClick = onOpenScheduledMessages
                    )
                    HorizontalDivider()
                    SettingsRow(
                        title = stringResource(R.string.important_people_title),
                        subtitle = stringResource(R.string.settings_important_people_subtitle),
                        onClick = onOpenImportantPeople
                    )
                    HorizontalDivider()
                    SettingsRow(
                        title = stringResource(R.string.settings_keywords),
                        subtitle = stringResource(R.string.settings_keywords_subtitle),
                        onClick = onOpenKeywordRules
                    )
                    HorizontalDivider()
                    SettingsRow(
                        title = stringResource(R.string.settings_reply_detection),
                        subtitle = stringResource(R.string.settings_reply_detection_subtitle),
                        onClick = onOpenReplyPhrases
                    )
                    HorizontalDivider()
                    SettingsToggleRow(
                        title = stringResource(R.string.settings_auto_enable_title),
                        checked = state.autoEnableNewGroups,
                        onCheckedChange = { viewModel.setAutoEnableNewGroups(it) }
                    )
                    Text(
                        stringResource(R.string.settings_auto_enable_desc),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 12.dp)
                    )
                }
            }

            item {
                SettingsSectionHeader(stringResource(R.string.settings_section_ai))
                SettingsGroupCard {
                    SettingsRow(
                        title = stringResource(R.string.settings_ai_provider),
                        subtitle = state.aiProvider,
                        onClick = { showAiProviderDialog = true }
                    )
                    if (state.aiProvider != "local") {
                        HorizontalDivider()
                        SettingsToggleRow(
                            title = stringResource(R.string.settings_allow_sending_fmt, state.aiProvider),
                            checked = state.aiCloudConsent,
                            onCheckedChange = { viewModel.setAiCloudConsent(it) }
                        )
                        HorizontalDivider()
                        if (state.aiProvider == "groq") {
                            SettingsRow(
                                title = stringResource(R.string.settings_groq_secure_proxy),
                                subtitle = stringResource(R.string.settings_groq_secure_proxy_desc),
                                onClick = { }
                            )
                        } else {
                            SettingsRow(
                                title = stringResource(R.string.api_key_title_fmt, state.aiProvider),
                                subtitle = stringResource(R.string.settings_api_key_stored_locally),
                                onClick = { showApiKeyDialog = true }
                            )
                        }
                    }
                    HorizontalDivider()
                    AiConnectionStatusRow(
                        status = state.aiConnectionStatus,
                        errorMessage = state.aiConnectionError,
                        onTest = { viewModel.testAiConnection() }
                    )
                }
            }

            item {
                SettingsSectionHeader(stringResource(R.string.settings_section_appearance))
                SettingsGroupCard {
                    SettingsRow(
                        title = stringResource(R.string.settings_language),
                        subtitle = state.language,
                        onClick = { showLanguageDialog = true }
                    )
                    HorizontalDivider()
                    SettingsRow(
                        title = stringResource(R.string.settings_theme),
                        subtitle = state.theme,
                        onClick = { showThemeDialog = true }
                    )
                    HorizontalDivider()
                    run {
                        val currentLanguage = androidx.compose.ui.platform.LocalConfiguration.current.locales[0].language
                        val currentCountry = com.whatsappworkmanager.app.utils.CountryCodes.byDialCode(defaultCountryCode)
                        SettingsRow(
                            title = stringResource(R.string.settings_default_country),
                            subtitle = if (currentCountry != null) {
                                stringResource(R.string.settings_default_country_body_fmt, currentCountry.flag, currentCountry.displayName(currentLanguage), currentCountry.dialCode)
                            } else {
                                stringResource(R.string.country_picker_title)
                            },
                            onClick = { showCountryPickerDialog = true }
                        )
                    }
                    HorizontalDivider()
                    SettingsRow(
                        title = stringResource(R.string.settings_whatsapp_app_title),
                        subtitle = stringResource(R.string.settings_whatsapp_app_subtitle_fmt, whatsappVariantLabel(state.whatsappVariant)),
                        onClick = { showWhatsappVariantDialog = true }
                    )
                }
            }

            item {
                SettingsSectionHeader(stringResource(R.string.settings_section_data))
                SettingsGroupCard {
                    SettingsRow(
                        title = stringResource(R.string.settings_retention),
                        subtitle = stringResource(R.string.retention_days_fmt, state.retentionDays),
                        onClick = { showRetentionDialog = true }
                    )
                    HorizontalDivider()
                    SettingsRow(
                        title = stringResource(R.string.settings_privacy),
                        subtitle = null,
                        onClick = onOpenPrivacy
                    )
                    HorizontalDivider()
                    SettingsRow(
                        title = stringResource(R.string.settings_clear_data),
                        subtitle = null,
                        titleColor = MaterialTheme.colorScheme.error,
                        onClick = { showClearDataConfirm = true }
                    )
                }
            }

            item {
                SettingsSectionHeader(stringResource(R.string.settings_section_about))
                SettingsGroupCard {
                    SettingsRow(
                        title = stringResource(R.string.settings_about),
                        subtitle = stringResource(R.string.settings_version_fmt, "1.0.0"),
                        onClick = { showAboutDialog = true }
                    )
                }
            }
        }
    }

    if (showAboutDialog) {
        AboutDialog(onDismiss = { showAboutDialog = false })
    }

    if (showCountryPickerDialog) {
        com.whatsappworkmanager.app.presentation.components.CountryPickerDialog(
            selectedDialCode = defaultCountryCode,
            onDismiss = { showCountryPickerDialog = false },
            onSelect = { country ->
                defaultCountryCode = country.dialCode
                com.whatsappworkmanager.app.utils.CountryCodePrefs.setLastUsed(context, country.dialCode)
                showCountryPickerDialog = false
            }
        )
    }

    if (showLanguageDialog) {
        SingleChoiceDialog(
            title = stringResource(R.string.settings_language),
            options = listOf("system", "en", "ar"),
            selected = state.language,
            optionLabel = { languageOptionLabel(it) },
            onSelect = {
                viewModel.setLanguage(it)
                showLanguageDialog = false
                // Belt-and-braces: AppCompatDelegate.setApplicationLocales (called inside
                // viewModel.setLanguage -> LocaleHelper) is documented to auto-recreate running
                // activities even without AppCompatActivity, but that auto-recreate has been
                // unreliable in practice on some OEM builds. Forcing recreate() here guarantees
                // the new language actually takes effect immediately instead of only applying
                // next cold start.
                (context as? android.app.Activity)?.recreate()
            },
            onDismiss = { showLanguageDialog = false }
        )
    }
    if (showThemeDialog) {
        SingleChoiceDialog(
            title = stringResource(R.string.settings_theme),
            options = listOf("system", "light", "dark", "premium"),
            selected = state.theme,
            onSelect = { viewModel.setTheme(it); showThemeDialog = false },
            onDismiss = { showThemeDialog = false }
        )
    }
    if (showAiProviderDialog) {
        SingleChoiceDialog(
            title = stringResource(R.string.settings_ai_provider),
            options = listOf("local", "groq", "openai", "anthropic", "gemini", "grok"),
            selected = state.aiProvider,
            onSelect = { viewModel.setAiProvider(it); showAiProviderDialog = false },
            onDismiss = { showAiProviderDialog = false }
        )
    }
    if (showRetentionDialog) {
        SingleChoiceDialog(
            title = stringResource(R.string.settings_retention),
            options = listOf("7", "30", "90"),
            selected = state.retentionDays.toString(),
            onSelect = { viewModel.setRetentionDays(it.toInt()); showRetentionDialog = false },
            onDismiss = { showRetentionDialog = false }
        )
    }
    if (showWhatsappVariantDialog) {
        SingleChoiceDialog(
            title = stringResource(R.string.settings_whatsapp_app_title),
            options = listOf(
                com.whatsappworkmanager.app.utils.IntentHelper.WHATSAPP_VARIANT_AUTO,
                com.whatsappworkmanager.app.utils.IntentHelper.WHATSAPP_VARIANT_REGULAR,
                com.whatsappworkmanager.app.utils.IntentHelper.WHATSAPP_VARIANT_BUSINESS
            ),
            selected = state.whatsappVariant,
            optionLabel = { whatsappVariantLabel(it) },
            onSelect = { viewModel.setWhatsappVariant(it); showWhatsappVariantDialog = false },
            onDismiss = { showWhatsappVariantDialog = false }
        )
    }
    if (showApiKeyDialog) {
        ApiKeyDialog(
            providerId = state.aiProvider,
            onSave = { key -> viewModel.saveApiKey(state.aiProvider, key); showApiKeyDialog = false },
            onDismiss = { showApiKeyDialog = false }
        )
    }
    if (showClearDataConfirm) {
        AlertDialog(
            onDismissRequest = { showClearDataConfirm = false },
            title = { Text(stringResource(R.string.settings_clear_data)) },
            text = { Text(stringResource(R.string.privacy_clear_data_confirm)) },
            confirmButton = {
                TextButton(onClick = { viewModel.clearAllData(); showClearDataConfirm = false }) {
                    Text(stringResource(R.string.settings_clear_data))
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDataConfirm = false }) { Text(stringResource(R.string.action_cancel)) }
            }
        )
    }
}

@Composable
private fun SettingsSectionHeader(title: String, modifier: Modifier = Modifier) {
    Text(
        title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        letterSpacing = 0.5.sp,
        modifier = modifier.padding(start = 16.dp, top = 4.dp, bottom = 4.dp)
    )
}

/**
 * Visually groups a category's rows into one rounded, low-elevation surface — the standard
 * "grouped settings list" pattern (iOS Settings, Material's own settings samples) rather than
 * one long undifferentiated list with a divider between every single row regardless of
 * relationship. [content] is expected to place a [HorizontalDivider] between its own rows
 * (not before the first or after the last) — kept manual rather than automatic since a couple
 * of rows in this screen are conditionally shown, and an automatic separator wouldn't know to
 * skip the divider that would otherwise precede a hidden row.
 */
@Composable
private fun SettingsGroupCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(content = content)
    }
}

@Composable
private fun SettingsRow(
    title: String,
    subtitle: String?,
    onClick: () -> Unit,
    titleColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface
) {
    ListItem(
        headlineContent = { Text(title, color = titleColor) },
        supportingContent = subtitle?.let { { Text(it) } },
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    )
}

@Composable
private fun SettingsToggleRow(title: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        trailingContent = { Switch(checked = checked, onCheckedChange = onCheckedChange) },
        modifier = Modifier.fillMaxWidth()
    )
}

/**
 * Green/red/gray dot showing whether the currently-selected AI provider actually responds —
 * not just whether a key is saved. Tapping "Test" makes one real, tiny request
 * (`suggestReplies` on a short test string) through the exact same code path a real reply
 * suggestion uses, so a green light here means replies will genuinely come from that provider,
 * not silently fall back to the local, offline suggestions.
 */
@Composable
private fun AiConnectionStatusRow(
    status: AiConnectionStatus,
    errorMessage: String?,
    onTest: () -> Unit
) {
    val (dotColor, label) = when (status) {
        AiConnectionStatus.UNTESTED -> MaterialTheme.colorScheme.outline to stringResource(R.string.ai_status_not_tested)
        AiConnectionStatus.TESTING -> MaterialTheme.colorScheme.outline to stringResource(R.string.ai_status_testing)
        AiConnectionStatus.CONNECTED -> OkGreen to stringResource(R.string.ai_status_connected)
        AiConnectionStatus.FAILED -> MaterialTheme.colorScheme.error to (errorMessage ?: stringResource(R.string.ai_status_failed))
    }
    ListItem(
        leadingContent = {
            if (status == AiConnectionStatus.TESTING) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
            } else {
                Box(
                    modifier = Modifier
                        .size(14.dp)
                        .background(color = dotColor, shape = CircleShape)
                )
            }
        },
        headlineContent = { Text(stringResource(R.string.ai_connection_title)) },
        supportingContent = { Text(label) },
        trailingContent = {
            TextButton(onClick = onTest, enabled = status != AiConnectionStatus.TESTING) { Text(stringResource(R.string.action_test)) }
        },
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun SingleChoiceDialog(
    title: String,
    options: List<String>,
    selected: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
    optionLabel: @Composable (String) -> String = { it }
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                options.forEach { option ->
                    Row(
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        RadioButton(selected = option == selected, onClick = { onSelect(option) })
                        Text(optionLabel(option))
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) } }
    )
}

@Composable
private fun languageOptionLabel(value: String): String = when (value) {
    "en" -> stringResource(R.string.onboarding_language_english)
    "ar" -> stringResource(R.string.onboarding_language_arabic)
    else -> stringResource(R.string.settings_system_default)
}

@Composable
private fun whatsappVariantLabel(value: String): String = when (value) {
    com.whatsappworkmanager.app.utils.IntentHelper.WHATSAPP_VARIANT_REGULAR -> stringResource(R.string.onboarding_whatsapp_variant_regular)
    com.whatsappworkmanager.app.utils.IntentHelper.WHATSAPP_VARIANT_BUSINESS -> stringResource(R.string.onboarding_whatsapp_variant_business)
    else -> stringResource(R.string.onboarding_whatsapp_variant_auto)
}

/**
 * A clean, single-purpose "About" screen — app identity, the developer's name/credit, and a
 * tappable email row that opens the device's mail composer pre-addressed. Deliberately no
 * clutter beyond that: no version-history changelog, no social links, nothing that would
 * compete with the one thing this dialog is actually for.
 */
@Composable
private fun AboutDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val developerEmail = "Mahmoud.mady30@gmail.com"

    AlertDialog(
        onDismissRequest = onDismiss,
        title = null,
        text = {
            Column(
                horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                androidx.compose.foundation.layout.Box(
                    modifier = Modifier
                        .size(64.dp)
                        .background(MaterialTheme.colorScheme.primary, shape = androidx.compose.foundation.shape.CircleShape),
                    contentAlignment = androidx.compose.ui.Alignment.Center
                ) {
                    Text(
                        stringResource(R.string.about_app_initials),
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text(stringResource(R.string.app_name_full), style = MaterialTheme.typography.titleLarge)
                Text(
                    stringResource(R.string.settings_version_fmt, "1.0.0"),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(20.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(20.dp))
                Text(
                    stringResource(R.string.about_developed_by),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    stringResource(R.string.about_developer_name),
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    stringResource(R.string.about_developer_title),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(16.dp))
                androidx.compose.material3.TextButton(onClick = {
                    val intent = android.content.Intent(android.content.Intent.ACTION_SENDTO).apply {
                        data = android.net.Uri.parse("mailto:$developerEmail")
                    }
                    context.startActivity(intent)
                }) {
                    Icon(Icons.Filled.Email, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(developerEmail)
                }
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    stringResource(R.string.about_copyright_fmt, "2026"),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) }
        }
    )
}

@Composable
private fun ApiKeyDialog(providerId: String, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var key by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.api_key_title_fmt, providerId)) },
        text = {
            Column {
                Text(
                    "Stored encrypted, on-device only. Never bundled into the app.",
                    style = MaterialTheme.typography.bodyMedium
                )
                OutlinedTextField(
                    value = key,
                    onValueChange = { key = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }
        },
        confirmButton = { TextButton(onClick = { onSave(key) }) { Text(stringResource(R.string.action_save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } }
    )
}
