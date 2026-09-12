package com.whatsappworkmanager.app.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@Composable
fun StatCard(
    title: String,
    value: String,
    icon: ImageVector,
    accentColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.primary,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    // Small "+" in the corner for a quick shortcut straight to this card's related settings
    // (e.g. Important → Important Keywords/People) — separate from [onClick], which navigates
    // to the filtered message list instead. Only rendered when provided.
    onAddClick: (() -> Unit)? = null,
    // Makes the icon itself its own separate tap target — e.g. Need Reply's icon opens a
    // dropdown of who's actually waiting, without disturbing [onClick]'s own behavior
    // (navigating to the filtered list) for taps anywhere else on the card. Only the icon's
    // own circle becomes clickable; nothing else about the card's layout changes.
    onIconClick: (() -> Unit)? = null,
    iconClickContent: @Composable (() -> Unit)? = null
) {
    Box(modifier = modifier) {
        Card(
            modifier = if (onClick != null) Modifier.fillMaxWidth().clickable(onClick = onClick) else Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(12.dp)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Icon inside a soft, tinted circle rather than a bare glyph — a small touch
                // that reads as noticeably more considered/premium than an icon floating
                // directly on the card background, at basically no extra cost.
                Box {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .background(accentColor.copy(alpha = 0.14f), shape = CircleShape)
                            .let { if (onIconClick != null) it.clickable(onClick = onIconClick) else it },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(icon, contentDescription = null, tint = accentColor, modifier = Modifier.size(16.dp))
                    }
                    iconClickContent?.invoke()
                }
                Text(value, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
                Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
        }
        if (onAddClick != null) {
            IconButton(
                onClick = onAddClick,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
                    .size(32.dp)
            ) {
                Icon(
                    Icons.Filled.Add,
                    contentDescription = "Quick settings for $title",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
fun FullScreenLoading(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
fun EmptyState(
    message: String,
    modifier: Modifier = Modifier,
    icon: ImageVector = Icons.Filled.Inbox
) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.outline)
            Text(
                message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.padding(horizontal = 32.dp)
            )
        }
    }
}

@Composable
fun ErrorState(
    message: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null
) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(
                Icons.Filled.ErrorOutline,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.error
            )
            Text(
                message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.padding(horizontal = 32.dp)
            )
            action?.invoke()
        }
    }
}

val ScreenPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp)

/**
 * A real clock-face time picker (Material 3's `TimePicker`), with a built-in AM/PM toggle
 * (`is24Hour = false`) — used anywhere the app needs a time from the user, instead of a raw
 * "type the hour as a number" text field. Includes a small keyboard-icon toggle (matching the
 * native Android time picker) to switch to manual digit entry (`TimeInput`) for anyone who'd
 * rather tap in the exact time than drag the clock hands.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WwmTimePickerDialog(
    initialHour: Int,
    initialMinute: Int,
    onDismiss: () -> Unit,
    onConfirm: (hour: Int, minute: Int) -> Unit
) {
    val state = rememberTimePickerState(
        initialHour = initialHour,
        initialMinute = initialMinute,
        is24Hour = false
    )
    var manualEntry by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(com.whatsappworkmanager.app.R.string.time_picker_title))
                IconButton(onClick = { manualEntry = !manualEntry }) {
                    Icon(
                        if (manualEntry) Icons.Filled.Schedule else Icons.Filled.Keyboard,
                        contentDescription = stringResource(
                            if (manualEntry) com.whatsappworkmanager.app.R.string.time_picker_switch_to_clock
                            else com.whatsappworkmanager.app.R.string.time_picker_switch_to_manual
                        )
                    )
                }
            }
        },
        text = {
            if (manualEntry) TimeInput(state = state) else TimePicker(state = state)
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(state.hour, state.minute) }) { Text(stringResource(com.whatsappworkmanager.app.R.string.action_ok)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(com.whatsappworkmanager.app.R.string.action_cancel)) } }
    )
}

/** Formats a 24h hour/minute pair as "8:05 AM" / "1:30 PM" for display next to a picker button. */
fun formatTime12Hour(hour: Int, minute: Int): String {
    val period = if (hour < 12) "AM" else "PM"
    val hour12 = when {
        hour == 0 -> 12
        hour > 12 -> hour - 12
        else -> hour
    }
    return String.format("%d:%02d %s", hour12, minute, period)
}

/**
 * The app's language switch, surfaced as a small "EN"/"AR" text label (whichever matches the
 * *currently applied* language, read from the live configuration rather than the raw saved
 * setting, so it's correct even if that setting is "system") that opens a proper dropdown to
 * choose from, rather than just toggling on a bare tap — the same language-switching mechanism
 * as Settings → Language and the Onboarding language step, surfaced right where AI-generated,
 * language-sensitive content is being read (Dashboard, Summary) instead of making the person
 * go find it in Settings.
 */
@Composable
fun LanguageFlagMenu(app: com.whatsappworkmanager.app.WwmApplication, context: android.content.Context) {
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val currentLanguage = androidx.compose.ui.platform.LocalConfiguration.current.locales[0].language
    var expanded by remember { mutableStateOf(false) }

    fun switchTo(languageCode: String) {
        expanded = false
        if (languageCode == currentLanguage) return
        com.whatsappworkmanager.app.utils.LocaleHelper.setLanguage(app, languageCode)
        scope.launch {
            app.settingsDataStore.setLanguage(languageCode)
            (context as? android.app.Activity)?.recreate()
        }
    }

    Box {
        IconButton(onClick = { expanded = true }) {
            Text(
                if (currentLanguage == "ar") "AR" else "EN",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text("AR  —  العربية") },
                trailingIcon = {
                    if (currentLanguage == "ar") {
                        Icon(Icons.Filled.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    }
                },
                onClick = { switchTo("ar") }
            )
            DropdownMenuItem(
                text = { Text("EN  —  English") },
                trailingIcon = {
                    if (currentLanguage != "ar") {
                        Icon(Icons.Filled.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    }
                },
                onClick = { switchTo("en") }
            )
        }
    }
}
