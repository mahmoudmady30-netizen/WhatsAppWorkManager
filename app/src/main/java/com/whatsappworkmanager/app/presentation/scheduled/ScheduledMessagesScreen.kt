package com.whatsappworkmanager.app.presentation.scheduled

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.whatsappworkmanager.app.R
import com.whatsappworkmanager.app.WwmApplication
import com.whatsappworkmanager.app.domain.model.ImportantContact
import com.whatsappworkmanager.app.domain.model.ScheduledOutgoingMessage
import com.whatsappworkmanager.app.domain.model.MessagingPlatform
import com.whatsappworkmanager.app.domain.model.displayName
import com.whatsappworkmanager.app.presentation.components.EmptyState
import com.whatsappworkmanager.app.presentation.components.ScreenPadding
import com.whatsappworkmanager.app.presentation.components.WwmTimePickerDialog
import com.whatsappworkmanager.app.presentation.components.formatTime12Hour
import com.whatsappworkmanager.app.presentation.components.CountryCodePhoneField
import com.whatsappworkmanager.app.presentation.components.MessagingPlatformBadge
import com.whatsappworkmanager.app.presentation.components.MessagingPlatformSelector
import com.whatsappworkmanager.app.utils.CountryCodePrefs
import com.whatsappworkmanager.app.utils.CountryCodes

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduledMessagesScreen() {
    val context = LocalContext.current
    val app = context.applicationContext as WwmApplication
    val viewModel: ScheduledMessagesViewModel = viewModel(factory = ScheduledMessagesViewModel.Factory(app))
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showAddDialog by remember { mutableStateOf(false) }
    var editingMessage by remember { mutableStateOf<ScheduledOutgoingMessage?>(null) }

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.scheduled_messages_title)) }) },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Filled.Add, contentDescription = null)
            }
        }
    ) { padding ->
        if (state.messages.isEmpty()) {
            EmptyState(
                message = stringResource(R.string.scheduled_messages_empty),
                modifier = Modifier.padding(padding)
            )
        } else {
            LazyColumn(
                modifier = Modifier.padding(padding).fillMaxWidth(),
                contentPadding = ScreenPadding,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    Text(
                        stringResource(R.string.scheduled_messages_explainer),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                }
                items(state.messages, key = { it.id }) { message ->
                    ScheduledMessageCard(
                        message = message,
                        whatsappVariant = state.whatsappVariant,
                        onEdit = { editingMessage = message },
                        onDelete = { viewModel.deleteMessage(message.id) }
                    )
                }
            }
        }
    }

    if (showAddDialog) {
        ScheduledMessageDialog(
            title = stringResource(R.string.scheduled_messages_title),
            initial = null,
            onDismiss = { showAddDialog = false },
            onConfirm = { text, hour, minute, repeat, phoneNumber, recipientName, platform ->
                viewModel.addMessage(text, hour, minute, repeat, phoneNumber, recipientName, platform)
                showAddDialog = false
            }
        )
    }

    editingMessage?.let { message ->
        ScheduledMessageDialog(
            title = stringResource(R.string.edit_scheduled_message_title),
            initial = message,
            onDismiss = { editingMessage = null },
            onConfirm = { text, hour, minute, repeat, phoneNumber, recipientName, platform ->
                viewModel.updateMessage(message.id, text, hour, minute, repeat, phoneNumber, recipientName, platform)
                editingMessage = null
            }
        )
    }
}

@Composable
private fun ScheduledMessageCard(
    message: ScheduledOutgoingMessage,
    whatsappVariant: String,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    val hour = message.timeMinutes / 60
    val minute = message.timeMinutes % 60
    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onEdit)
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(message.text, style = MaterialTheme.typography.bodyLarge)
                if (!message.recipientName.isNullOrBlank()) {
                    Text(
                        if (message.platform == MessagingPlatform.MESSENGER) "Messenger users: ${message.recipientName}" else stringResource(R.string.scheduled_message_to_fmt, message.recipientName),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                val timeLabel = formatTime12Hour(hour, minute) +
                    (if (message.repeatDaily) " · " + stringResource(R.string.scheduled_message_every_day) else "")
                MessagingPlatformBadge(message.platform)
                Text(timeLabel, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary)
                Text(
                    stringResource(R.string.scheduled_message_background),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                if (message.lastSentAt != null) {
                    Text(
                        "Last sent: " + java.text.SimpleDateFormat("dd MMM, h:mm a", java.util.Locale.getDefault()).format(java.util.Date(message.lastSentAt)),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            IconButton(onClick = {
                com.whatsappworkmanager.app.utils.IntentHelper.openMessagingChat(context, message.platform, message.phoneNumber, message.text, whatsappVariant)
            }) {
                Icon(Icons.Filled.Send, contentDescription = stringResource(R.string.action_send))
            }
            IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, contentDescription = null) }
        }
    }
}

/**
 * Shared Add/Edit dialog. When [initial] is null this behaves as "Add" (defaults: 08:00, repeat
 * daily, no phone number); when non-null, every field is pre-filled from the existing message so
 * editing feels like a normal form rather than starting over.
 *
 * Time is picked via a real clock-face [WwmTimePickerDialog] (with a built-in AM/PM toggle),
 * not typed as raw numbers — tapping the "Time" button below opens it. This also removes the
 * old side-by-side Hour/Minute text field pair, whose narrow width was causing their labels to
 * visually overlap on smaller screens.
 */
@Composable
private fun ScheduledMessageDialog(
    title: String,
    initial: ScheduledOutgoingMessage?,
    onDismiss: () -> Unit,
    onConfirm: (text: String, hour: Int, minute: Int, repeat: Boolean, phoneNumber: String?, recipientName: String?, platform: MessagingPlatform) -> Unit
) {
    val context = LocalContext.current
    val app = context.applicationContext as WwmApplication
    var text by remember { mutableStateOf(initial?.text.orEmpty()) }
    val now = remember { java.util.Calendar.getInstance() }
    var hour by remember { mutableIntStateOf(initial?.let { it.timeMinutes / 60 } ?: now.get(java.util.Calendar.HOUR_OF_DAY)) }
    var minute by remember { mutableIntStateOf(initial?.let { it.timeMinutes % 60 } ?: now.get(java.util.Calendar.MINUTE)) }
    var repeat by remember { mutableStateOf(initial?.repeatDaily ?: true) }
    var recipientName by remember { mutableStateOf(initial?.recipientName.orEmpty()) }
    var platform by remember { mutableStateOf(initial?.platform ?: MessagingPlatform.WHATSAPP) }
    var showTimePicker by remember { mutableStateOf(false) }
    var showContactPicker by remember { mutableStateOf(false) }

    // Contacts with a saved phone number (see Settings → Important People) — lets the user
    // pick a name instead of typing a number, without needing real device Contacts access.
    val importantContacts by app.importantContactRepository.observeContacts()
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val contactsWithPhone = importantContacts.filter { !it.phoneNumber.isNullOrBlank() }

    // Split an existing "201001234567"-style stored number back into code + local part for
    // editing; for a brand-new message, default the code to whatever was used last time (see
    // CountryCodePrefs), so the user isn't stuck re-picking it every single time.
    val lastUsedCode = remember { CountryCodePrefs.getLastUsed(context) }
    val fallbackCountry = remember { CountryCodes.byDialCode(lastUsedCode) ?: CountryCodes.ALL.first() }
    val (initialCountry, initialLocalNumber) = remember(initial) {
        val stored = initial?.phoneNumber
        if (stored.isNullOrBlank()) fallbackCountry to "" else CountryCodes.split(stored, fallbackCountry)
    }
    var countryCode by remember { mutableStateOf(initialCountry.dialCode) }
    var localNumber by remember { mutableStateOf(initialLocalNumber) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Icon(Icons.Filled.Schedule, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Column {
                    Text(title, style = MaterialTheme.typography.titleLarge)
                    Text(
                        "Choose the app, recipient and delivery time",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    stringResource(R.string.messaging_platform_label),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                MessagingPlatformSelector(
                    selected = platform,
                    onSelected = { platform = it }
                )
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text(stringResource(R.string.message_text_label)) },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedButton(
                    onClick = { showTimePicker = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Filled.Schedule, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.time_label_fmt, formatTime12Hour(hour, minute)))
                }
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Checkbox(checked = repeat, onCheckedChange = { repeat = it })
                    Text(stringResource(R.string.repeat_every_day))
                }
                if (platform == MessagingPlatform.MESSENGER) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.large,
                        color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.30f)
                    ) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                com.whatsappworkmanager.app.presentation.components.MessagingPlatformIcon(
                                    MessagingPlatform.MESSENGER, Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(7.dp))
                                Text("Messenger recipients", style = MaterialTheme.typography.titleSmall)
                            }
                            OutlinedTextField(
                                value = recipientName,
                                onValueChange = { recipientName = it },
                                label = { Text("Messenger users") },
                                placeholder = { Text("e.g. Ahmed, Sarah, @client") },
                                supportingText = { Text("Add one or more Messenger names/usernames, separated by commas.") },
                                modifier = Modifier.fillMaxWidth(),
                                minLines = 1,
                                maxLines = 3
                            )
                        }
                    }
                } else {
                    if (contactsWithPhone.isNotEmpty()) {
                        Box {
                            OutlinedButton(
                                onClick = { showContactPicker = true },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Filled.Person, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(stringResource(R.string.pick_from_important_people))
                            }
                            DropdownMenu(
                                expanded = showContactPicker,
                                onDismissRequest = { showContactPicker = false }
                            ) {
                                contactsWithPhone.forEach { contact ->
                                    DropdownMenuItem(
                                        text = { Text(contact.name) },
                                        onClick = {
                                            val (country, local) = CountryCodes.split(contact.phoneNumber!!, fallbackCountry)
                                            countryCode = country.dialCode
                                            localNumber = local
                                            recipientName = contact.name
                                            showContactPicker = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                    CountryCodePhoneField(
                        countryCode = countryCode,
                        onCountryCodeChange = { countryCode = it },
                        localNumber = localNumber,
                        onLocalNumberChange = { localNumber = it },
                        modifier = Modifier.fillMaxWidth()
                    )
                    com.whatsappworkmanager.app.presentation.components.PickFromContactsButton(
                        label = stringResource(R.string.pick_contact_instead),
                        onPicked = { pickedName, pickedNumber ->
                            if (!pickedNumber.isNullOrBlank()) {
                                val (country, local) = CountryCodes.parsePickedContactNumber(pickedNumber, fallbackCountry)
                                countryCode = country.dialCode
                                localNumber = local
                                recipientName = pickedName
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                ) {
                    Row(Modifier.padding(10.dp), verticalAlignment = androidx.compose.ui.Alignment.Top) {
                        Icon(Icons.Filled.Schedule, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            if (platform == MessagingPlatform.MESSENGER)
                                "Messenger uses the names/usernames above. Background delivery depends on Messenger notification reply support or visible UI automation."
                            else
                                "WhatsApp uses the phone number above for 1:1 targeting. You can still review and send from the selected app.",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (text.isNotBlank()) {
                    val fullPhone = if (localNumber.isBlank()) null else countryCode + localNumber
                    if (countryCode.isNotBlank()) CountryCodePrefs.setLastUsed(context, countryCode)
                    // A name without a number to go with it isn't useful to show, so it's
                    // dropped along with the number whenever the number field ends up empty
                    // (e.g. the person picked a contact, then cleared the number by hand).
                    val nameToSave = if (platform == MessagingPlatform.MESSENGER) {
                        recipientName.trim().ifBlank { null }
                    } else {
                        if (fullPhone == null) null else recipientName.ifBlank { null }
                    }
                    onConfirm(text, hour, minute, repeat, if (platform == MessagingPlatform.MESSENGER) null else fullPhone, nameToSave, platform)
                }
            }) { Text(if (initial == null) stringResource(R.string.action_save) else stringResource(R.string.action_update)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } }
    )

    if (showTimePicker) {
        WwmTimePickerDialog(
            initialHour = hour,
            initialMinute = minute,
            onDismiss = { showTimePicker = false },
            onConfirm = { newHour, newMinute ->
                hour = newHour
                minute = newMinute
                showTimePicker = false
            }
        )
    }
}
