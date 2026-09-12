package com.whatsappworkmanager.app.presentation.keywords

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
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
import com.whatsappworkmanager.app.domain.model.KeywordRule
import com.whatsappworkmanager.app.presentation.components.EmptyState
import com.whatsappworkmanager.app.presentation.components.ScreenPadding

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KeywordRulesScreen() {
    val context = LocalContext.current
    val app = context.applicationContext as WwmApplication
    val viewModel: KeywordRulesViewModel = viewModel(factory = KeywordRulesViewModel.Factory(app))
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showAddDialog by remember { mutableStateOf(false) }
    var editingRule by remember { mutableStateOf<KeywordRule?>(null) }

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.important_keywords_title)) }) },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Filled.Add, contentDescription = null)
            }
        }
    ) { padding ->
        if (state.rules.isEmpty()) {
            EmptyState(
                message = stringResource(R.string.keyword_rules_empty),
                modifier = Modifier.padding(padding)
            )
        } else {
            LazyColumn(
                modifier = Modifier.padding(padding).fillMaxWidth(),
                contentPadding = ScreenPadding,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(state.rules, key = { it.id }) { rule ->
                    KeywordRuleCard(
                        rule = rule,
                        onToggle = { viewModel.setEnabled(rule, it) },
                        onEdit = { editingRule = rule },
                        onDelete = { viewModel.delete(rule.id) }
                    )
                }
            }
        }
    }

    if (showAddDialog) {
        KeywordRuleDialog(
            existing = null,
            onDismiss = { showAddDialog = false },
            onConfirm = { keyword, priority ->
                viewModel.addRule(keyword, priority)
                showAddDialog = false
            }
        )
    }

    editingRule?.let { rule ->
        KeywordRuleDialog(
            existing = rule,
            onDismiss = { editingRule = null },
            onConfirm = { keyword, priority ->
                viewModel.updateRule(rule, keyword, priority)
                editingRule = null
            }
        )
    }
}

@Composable
private fun KeywordRuleCard(
    rule: KeywordRule,
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
                Text(rule.keyword, style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.keyword_weight_fmt, rule.priority), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary)
            }
            Switch(checked = rule.enabled, onCheckedChange = onToggle)
            IconButton(onClick = onEdit) { Icon(Icons.Filled.Edit, contentDescription = null) }
            IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, contentDescription = null) }
        }
    }
}

/** Shared by both "Add" (existing = null) and "Edit" (existing = the rule being edited). */
@Composable
private fun KeywordRuleDialog(
    existing: KeywordRule?,
    onDismiss: () -> Unit,
    onConfirm: (String, Int) -> Unit
) {
    var keyword by remember { mutableStateOf(existing?.keyword.orEmpty()) }
    var priority by remember { mutableIntStateOf(existing?.priority ?: 5) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (existing != null) R.string.edit_keyword_title else R.string.add_keyword_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = keyword,
                    onValueChange = { keyword = it },
                    label = { Text(stringResource(R.string.keyword_or_phrase_label)) },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = priority.toString(),
                    onValueChange = { priority = it.toIntOrNull()?.coerceIn(1, 10) ?: priority },
                    label = { Text(stringResource(R.string.keyword_weight_label)) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(keyword, priority) },
                enabled = keyword.isNotBlank()
            ) { Text(stringResource(if (existing != null) R.string.action_update else R.string.action_add)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } }
    )
}
