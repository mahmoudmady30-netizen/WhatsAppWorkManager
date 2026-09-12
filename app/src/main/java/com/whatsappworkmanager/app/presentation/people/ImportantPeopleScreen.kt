package com.whatsappworkmanager.app.presentation.people

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContactPhone
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.whatsappworkmanager.app.WwmApplication
import com.whatsappworkmanager.app.R
import com.whatsappworkmanager.app.domain.model.ImportantContact
import com.whatsappworkmanager.app.presentation.components.CountryCodePhoneField
import com.whatsappworkmanager.app.presentation.components.EmptyState
import com.whatsappworkmanager.app.presentation.components.PickFromContactsButton
import com.whatsappworkmanager.app.presentation.components.ScreenPadding
import com.whatsappworkmanager.app.utils.ContactsLookup
import com.whatsappworkmanager.app.utils.CountryCodePrefs
import com.whatsappworkmanager.app.utils.CountryCodes
import com.whatsappworkmanager.app.utils.IntentHelper

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportantPeopleScreen() {
    val context = LocalContext.current
    val app = context.applicationContext as WwmApplication
    val viewModel: ImportantPeopleViewModel = viewModel(factory = ImportantPeopleViewModel.Factory(app))
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showAddDialog by remember { mutableStateOf(false) }
    var editingContact by remember { mutableStateOf<ImportantContact?>(null) }

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.important_people_title)) }) },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Filled.Add, contentDescription = null)
            }
        }
    ) { padding ->
        if (state.contacts.isEmpty()) {
            EmptyState(
                message = stringResource(R.string.important_people_empty),
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
                        stringResource(R.string.important_people_matching_hint),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
                items(state.contacts, key = { it.id }) { contact ->
                    ImportantPersonCard(
                        contact = contact,
                        onToggle = { viewModel.setEnabled(contact, it) },
                        onEdit = { editingContact = contact },
                        onDelete = { viewModel.delete(contact.id) }
                    )
                }
            }
        }
    }

    if (showAddDialog) {
        ImportantPersonDialog(
            existing = null,
            onDismiss = { showAddDialog = false },
            onConfirm = { name, phone ->
                viewModel.addContact(name, phone)
                showAddDialog = false
            }
        )
    }

    editingContact?.let { contact ->
        ImportantPersonDialog(
            existing = contact,
            onDismiss = { editingContact = null },
            onConfirm = { name, phone ->
                viewModel.updateContact(contact, name, phone)
                editingContact = null
            }
        )
    }
}

@Composable
private fun ImportantPersonCard(
    contact: ImportantContact,
    onToggle: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(contact.name, style = MaterialTheme.typography.titleMedium)
                if (contact.phoneNumber != null) {
                    val country = CountryCodes.split(contact.phoneNumber, CountryCodes.ALL.first()).first
                    Text(
                        "${country.flag} +${contact.phoneNumber}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.secondary
                    )
                } else {
                    Text(
                        stringResource(R.string.no_phone_number_saved),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            }
            Switch(checked = contact.enabled, onCheckedChange = onToggle)
            IconButton(onClick = onEdit) { Icon(Icons.Filled.Edit, contentDescription = null) }
            IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, contentDescription = null) }
        }
    }
}

/**
 * Shared by both "Add" (existing = null) and "Edit" (existing = the contact being edited) —
 * requested explicitly: someone added via the quick "Save as Important" flow from a message,
 * or added here without a phone number, needs a way to come back and add or fix it, without
 * deleting and re-creating the whole entry from scratch.
 */
@Composable
private fun ImportantPersonDialog(
    existing: ImportantContact?,
    onDismiss: () -> Unit,
    onConfirm: (name: String, phoneNumber: String?) -> Unit
) {
    val context = LocalContext.current
    var name by remember { mutableStateOf(existing?.name.orEmpty()) }
    val lastUsedCode = remember { CountryCodePrefs.getLastUsed(context) }
    val fallbackCountry = remember { CountryCodes.byDialCode(lastUsedCode) ?: CountryCodes.ALL.first() }
    var countryCode by remember {
        mutableStateOf(existing?.phoneNumber?.let { CountryCodes.split(it, fallbackCountry).first.dialCode } ?: lastUsedCode)
    }
    var localNumber by remember {
        mutableStateOf(existing?.phoneNumber?.let { CountryCodes.split(it, fallbackCountry).second } ?: "")
    }
    // Auto-fill-by-name is only attempted for a genuinely new entry with nothing typed into
    // the number field yet — never overwrites a number the user (or an edit) already has in
    // there, and never runs a second time once a lookup has already been tried once (whether
    // or not it found anything), so it can't repeatedly re-query as the user types more of the
    // name.
    var hasTriedContactsLookup by remember { mutableStateOf(false) }
    val contactsPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted && localNumber.isBlank()) {
            val found = ContactsLookup.findPhoneNumberByName(context, name)
            if (found != null) {
                val (country, local) = CountryCodes.parsePickedContactNumber(found, fallbackCountry)
                countryCode = country.dialCode
                localNumber = local
            }
        }
        hasTriedContactsLookup = true
    }

    LaunchedEffect(existing) {
        if (existing == null && name.isNotBlank() && localNumber.isBlank() && IntentHelper.isReadContactsGranted(context)) {
            val found = ContactsLookup.findPhoneNumberByName(context, name)
            if (found != null) {
                val (country, local) = CountryCodes.parsePickedContactNumber(found, fallbackCountry)
                countryCode = country.dialCode
                localNumber = local
            }
            hasTriedContactsLookup = true
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (existing != null) R.string.update_important_person_title else R.string.add_important_person_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                PickFromContactsButton(
                    onPicked = { pickedName, pickedNumber ->
                        name = pickedName
                        if (!pickedNumber.isNullOrBlank()) {
                            val fallback = CountryCodes.byDialCode(countryCode) ?: CountryCodes.ALL.first()
                            val (country, local) = CountryCodes.parsePickedContactNumber(pickedNumber, fallback)
                            countryCode = country.dialCode
                            localNumber = local
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.important_person_name_label)) },
                    modifier = Modifier.fillMaxWidth()
                )
                // Only offered when there's genuinely something to look up (a name typed, no
                // number yet, permission not already resolved either way for this dialog
                // instance) — not shown for an edit of an existing entry, and not shown again
                // after one attempt already ran, found or not.
                if (existing == null && name.isNotBlank() && localNumber.isBlank() &&
                    !IntentHelper.isReadContactsGranted(context) && !hasTriedContactsLookup
                ) {
                    OutlinedButton(
                        onClick = { contactsPermissionLauncher.launch(android.Manifest.permission.READ_CONTACTS) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Filled.ContactPhone, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.find_number_in_contacts))
                    }
                }
                CountryCodePhoneField(
                    countryCode = countryCode,
                    onCountryCodeChange = { countryCode = it },
                    localNumber = localNumber,
                    onLocalNumberChange = { localNumber = it },
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    stringResource(R.string.important_person_phone_optional_hint),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (name.isNotBlank()) {
                        val phone = if (localNumber.isBlank()) null else countryCode + localNumber
                        if (localNumber.isNotBlank() && countryCode.isNotBlank()) {
                            CountryCodePrefs.setLastUsed(context, countryCode)
                        }
                        onConfirm(name, phone)
                    }
                },
                enabled = name.isNotBlank()
            ) { Text(stringResource(if (existing != null) R.string.action_update else R.string.action_add)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } }
    )
}
