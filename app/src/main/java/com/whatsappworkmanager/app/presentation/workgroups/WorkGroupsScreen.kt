package com.whatsappworkmanager.app.presentation.workgroups

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.whatsappworkmanager.app.R
import com.whatsappworkmanager.app.WwmApplication
import com.whatsappworkmanager.app.domain.model.WorkGroupInfo
import com.whatsappworkmanager.app.presentation.components.EmptyState
import com.whatsappworkmanager.app.presentation.components.FullScreenLoading
import com.whatsappworkmanager.app.presentation.components.PickFromContactsButton
import com.whatsappworkmanager.app.presentation.components.ScreenPadding
import com.whatsappworkmanager.app.presentation.components.MessagingPlatformsBadge
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkGroupsScreen(onBack: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val app = context.applicationContext as WwmApplication
    val viewModel: WorkGroupsViewModel = viewModel(factory = WorkGroupsViewModel.Factory(app))
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var showAddDialog by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var deleteConfirmFor by remember { mutableStateOf<WorkGroupInfo?>(null) }

    LaunchedEffect(state.addResultMessage) {
        state.addResultMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.dismissAddResultMessage()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = { TopAppBar(title = { Text("Work Groups & Clients") }) },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Filled.Add, contentDescription = "Add group or client")
            }
        }
    ) { padding ->
        when {
            state.isLoading -> FullScreenLoading(Modifier.padding(padding))
            state.groups.isEmpty() -> EmptyState(
                message = stringResource(R.string.work_groups_empty),
                modifier = Modifier.padding(padding)
            )
            else -> {
                val filtered = state.groups
                    .filter { it.name.contains(query.trim(), ignoreCase = true) }
                    .sortedWith(compareByDescending<WorkGroupInfo> { it.isEnabled }.thenByDescending { it.lastMessageTime }.thenBy { it.name.lowercase() })
                LazyColumn(
                    modifier = Modifier.padding(padding).fillMaxWidth(),
                    contentPadding = ScreenPadding,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        OutlinedTextField(
                            value = query,
                            onValueChange = { query = it },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            label = { Text("Search by name") },
                            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                            shape = MaterialTheme.shapes.large
                        )
                    }
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text("Tracked conversations", style = MaterialTheme.typography.titleSmall)
                                Text(
                                    "Choose which groups and clients stay in your work dashboard.",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text(
                                filtered.size.toString(),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    items(filtered, key = { it.id }) { group ->
                        WorkGroupCard(
                            group = group,
                            onToggle = { enabled -> viewModel.setGroupEnabled(group.name, enabled) },
                            onDeleteRequest = { deleteConfirmFor = group }
                        )
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AddGroupOrClientDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { name ->
                viewModel.addManually(name)
                showAddDialog = false
            }
        )
    }

    deleteConfirmFor?.let { group ->
        AlertDialog(
            onDismissRequest = { deleteConfirmFor = null },
            title = { Text(stringResource(R.string.delete_work_group_title)) },
            text = { Text(stringResource(R.string.delete_work_group_body_fmt, group.name)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteGroup(group.name)
                    deleteConfirmFor = null
                }) { Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { deleteConfirmFor = null }) { Text(stringResource(R.string.action_cancel)) }
            }
        )
    }
}

@Composable
private fun WorkGroupCard(group: WorkGroupInfo, onToggle: (Boolean) -> Unit, onDeleteRequest: () -> Unit) {
    val timeFormat = rememberTimeFormat()
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Avatar(name = group.name, enabled = group.isEnabled)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(group.name, style = MaterialTheme.typography.titleMedium, maxLines = 2, fontWeight = FontWeight.SemiBold)
                MessagingPlatformsBadge(group.platforms)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    InfoPill(
                        icon = if (group.name.contains("group", ignoreCase = true)) Icons.Filled.Groups else Icons.Filled.Person,
                        text = "Work"
                    )
                    Text(
                        stringResource(R.string.messages_count_fmt, group.messageCount),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (group.lastMessageTime > 0) {
                    Text(
                        stringResource(R.string.last_activity_fmt, timeFormat.format(Date(group.lastMessageTime))),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary,
                        maxLines = 1
                    )
                } else {
                    Text(
                        stringResource(R.string.work_group_waiting_first_message),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary,
                        maxLines = 1
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Switch(checked = group.isEnabled, onCheckedChange = onToggle)
                Text(
                    if (group.isEnabled) "On" else "Off",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (group.isEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onDeleteRequest) {
                Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.action_delete), tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun Avatar(name: String, enabled: Boolean) {
    val initials = name.trim().split(Regex("\\s+")).filter { it.isNotBlank() }.take(2).joinToString("") { it.first().uppercase() }
    Box(
        modifier = Modifier.size(50.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center
    ) {
        Text(
            initials.ifBlank { "WA" },
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun InfoPill(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    Row(
        modifier = Modifier
            .clip(MaterialTheme.shapes.small)
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .padding(horizontal = 7.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(13.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer)
        Text(text, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSecondaryContainer)
    }
}

@Composable
private fun AddGroupOrClientDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.add_group_or_client_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                PickFromContactsButton(
                    label = stringResource(R.string.pick_client_from_contacts),
                    onPicked = { pickedName, _ -> name = pickedName },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.add_group_or_client_name_label)) },
                    placeholder = { Text(stringResource(R.string.add_group_or_client_name_placeholder)) },
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    stringResource(R.string.add_group_or_client_contacts_hint),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { if (name.isNotBlank()) onConfirm(name.trim()) }) {
                Text(stringResource(R.string.action_add))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } }
    )
}

@Composable
private fun rememberTimeFormat(): SimpleDateFormat = remember {
    SimpleDateFormat("dd MMM, h:mm a", Locale.getDefault())
}
