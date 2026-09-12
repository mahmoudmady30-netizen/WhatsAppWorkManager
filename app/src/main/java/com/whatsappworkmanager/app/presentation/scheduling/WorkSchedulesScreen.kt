package com.whatsappworkmanager.app.presentation.scheduling

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
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
import com.whatsappworkmanager.app.domain.model.ScheduleKind
import com.whatsappworkmanager.app.domain.model.WorkSchedule
import com.whatsappworkmanager.app.presentation.components.EmptyState
import com.whatsappworkmanager.app.presentation.components.ScreenPadding
import com.whatsappworkmanager.app.presentation.components.WwmTimePickerDialog
import com.whatsappworkmanager.app.presentation.components.formatTime12Hour

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkSchedulesScreen() {
    val context = LocalContext.current
    val app = context.applicationContext as WwmApplication
    val viewModel: WorkSchedulesViewModel = viewModel(factory = WorkSchedulesViewModel.Factory(app))
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    var selectedTab by remember { mutableIntStateOf(0) }
    var showAddSummaryDialog by remember { mutableStateOf(false) }
    var showAddWindowDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringRes(R.string.settings_summary_schedule)) }) },
        floatingActionButton = {
            FloatingActionButton(onClick = {
                if (selectedTab == 0) showAddSummaryDialog = true else showAddWindowDialog = true
            }) { Icon(Icons.Filled.Add, contentDescription = null) }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxWidth()) {
            TabRow(selectedTabIndex = selectedTab) {
                Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text(stringResource(R.string.tab_summary_times)) })
                Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text(stringResource(R.string.tab_work_break_quiet)) })
            }

            if (selectedTab == 0) {
                if (state.summarySchedules.isEmpty()) {
                    EmptyState(message = "No summary times scheduled yet. Tap + to add one, e.g. 08:00, 13:00, 17:00.")
                } else {
                    LazyColumn(contentPadding = ScreenPadding, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(state.summarySchedules, key = { it.id }) { schedule ->
                            SummaryScheduleCard(
                                schedule = schedule,
                                onToggle = { viewModel.setEnabled(schedule, it) },
                                onDelete = { viewModel.delete(schedule.id) }
                            )
                        }
                    }
                }
            } else {
                if (state.modeWindows.isEmpty()) {
                    EmptyState(message = "No Work/Break/Quiet windows yet. Tap + to add one, e.g. 08:00 → 12:00 Work.")
                } else {
                    LazyColumn(contentPadding = ScreenPadding, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(state.modeWindows, key = { it.id }) { schedule ->
                            ModeWindowCard(
                                schedule = schedule,
                                onToggle = { viewModel.setEnabled(schedule, it) },
                                onDelete = { viewModel.delete(schedule.id) }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showAddSummaryDialog) {
        AddSummaryScheduleDialog(
            onDismiss = { showAddSummaryDialog = false },
            onConfirm = { name, h, m ->
                viewModel.addSummarySchedule(name, h, m)
                showAddSummaryDialog = false
            }
        )
    }
    if (showAddWindowDialog) {
        AddModeWindowDialog(
            onDismiss = { showAddWindowDialog = false },
            onConfirm = { name, kind, sh, sm, eh, em ->
                viewModel.addModeWindow(name, kind, sh, sm, eh, em)
                showAddWindowDialog = false
            }
        )
    }
}

@Composable
private fun stringRes(id: Int): String = androidx.compose.ui.res.stringResource(id)

@Composable
private fun SummaryScheduleCard(schedule: WorkSchedule, onToggle: (Boolean) -> Unit, onDelete: () -> Unit) {
    val hour = schedule.startTimeMinutes / 60
    val minute = schedule.startTimeMinutes % 60
    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Column {
                Text(schedule.name, style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.schedule_every_day_fmt, formatTime12Hour(hour, minute)), style = MaterialTheme.typography.bodyMedium)
            }
            Row {
                Switch(checked = schedule.enabled, onCheckedChange = onToggle)
                IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, contentDescription = null) }
            }
        }
    }
}

@Composable
private fun ModeWindowCard(schedule: WorkSchedule, onToggle: (Boolean) -> Unit, onDelete: () -> Unit) {
    val startH = schedule.startTimeMinutes / 60
    val startM = schedule.startTimeMinutes % 60
    val end = schedule.endTimeMinutes ?: 0
    val endH = end / 60
    val endM = end % 60
    val kindLabel = when (schedule.kind) {
        ScheduleKind.WORK_MODE -> "Work"
        ScheduleKind.BREAK_MODE -> "Break"
        ScheduleKind.QUIET_MODE -> "Quiet"
        ScheduleKind.SUMMARY -> "Summary"
    }
    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Column {
                Text(schedule.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    "${formatTime12Hour(startH, startM)} → ${formatTime12Hour(endH, endM)} · $kindLabel",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            Row {
                Switch(checked = schedule.enabled, onCheckedChange = onToggle)
                IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, contentDescription = null) }
            }
        }
    }
}

@Composable
private fun TimePickerButton(label: String, hour: Int, minute: Int, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Icon(Icons.Filled.Schedule, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text("$label: ${formatTime12Hour(hour, minute)}")
    }
}

@Composable
private fun AddSummaryScheduleDialog(
    onDismiss: () -> Unit,
    onConfirm: (name: String, hour: Int, minute: Int) -> Unit
) {
    var name by remember { mutableStateOf("Morning Summary") }
    var hour by remember { mutableIntStateOf(8) }
    var minute by remember { mutableIntStateOf(0) }
    var showPicker by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.add_summary_time_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.schedule_name_label)) },
                    modifier = Modifier.fillMaxWidth()
                )
                TimePickerButton(label = stringResource(R.string.label_time), hour = hour, minute = minute, onClick = { showPicker = true })
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(name, hour, minute) }) { Text(stringResource(R.string.action_add)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } }
    )

    if (showPicker) {
        WwmTimePickerDialog(
            initialHour = hour,
            initialMinute = minute,
            onDismiss = { showPicker = false },
            onConfirm = { h, m -> hour = h; minute = m; showPicker = false }
        )
    }
}

@Composable
private fun AddModeWindowDialog(
    onDismiss: () -> Unit,
    onConfirm: (name: String, kind: ScheduleKind, startHour: Int, startMinute: Int, endHour: Int, endMinute: Int) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var kind by remember { mutableStateOf(ScheduleKind.WORK_MODE) }
    var startHour by remember { mutableIntStateOf(8) }
    var startMinute by remember { mutableIntStateOf(0) }
    var endHour by remember { mutableIntStateOf(12) }
    var endMinute by remember { mutableIntStateOf(0) }
    var pickingStart by remember { mutableStateOf(false) }
    var pickingEnd by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.add_window_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.window_name_placeholder)) },
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = kind == ScheduleKind.WORK_MODE, onClick = { kind = ScheduleKind.WORK_MODE }, label = { Text(stringResource(R.string.mode_work)) })
                    FilterChip(selected = kind == ScheduleKind.BREAK_MODE, onClick = { kind = ScheduleKind.BREAK_MODE }, label = { Text(stringResource(R.string.mode_break)) })
                    FilterChip(selected = kind == ScheduleKind.QUIET_MODE, onClick = { kind = ScheduleKind.QUIET_MODE }, label = { Text(stringResource(R.string.mode_quiet)) })
                }
                TimePickerButton(label = stringResource(R.string.label_start), hour = startHour, minute = startMinute, onClick = { pickingStart = true })
                TimePickerButton(label = stringResource(R.string.label_end), hour = endHour, minute = endMinute, onClick = { pickingEnd = true })
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val finalName = name.ifBlank {
                    when (kind) {
                        ScheduleKind.WORK_MODE -> "Work"
                        ScheduleKind.BREAK_MODE -> "Break"
                        ScheduleKind.QUIET_MODE -> "Quiet"
                        ScheduleKind.SUMMARY -> "Summary"
                    }
                }
                onConfirm(finalName, kind, startHour, startMinute, endHour, endMinute)
            }) { Text(stringResource(R.string.action_add)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } }
    )

    if (pickingStart) {
        WwmTimePickerDialog(
            initialHour = startHour,
            initialMinute = startMinute,
            onDismiss = { pickingStart = false },
            onConfirm = { h, m -> startHour = h; startMinute = m; pickingStart = false }
        )
    }
    if (pickingEnd) {
        WwmTimePickerDialog(
            initialHour = endHour,
            initialMinute = endMinute,
            onDismiss = { pickingEnd = false },
            onConfirm = { h, m -> endHour = h; endMinute = m; pickingEnd = false }
        )
    }
}
