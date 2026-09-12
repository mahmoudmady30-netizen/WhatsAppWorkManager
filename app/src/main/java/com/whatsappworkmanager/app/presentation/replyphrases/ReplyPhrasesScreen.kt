package com.whatsappworkmanager.app.presentation.replyphrases

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
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
import com.whatsappworkmanager.app.domain.model.ReplyPhraseRule
import com.whatsappworkmanager.app.presentation.components.EmptyState
import com.whatsappworkmanager.app.presentation.components.ScreenPadding

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReplyPhrasesScreen() {
    val context = LocalContext.current
    val app = context.applicationContext as WwmApplication
    val viewModel: ReplyPhrasesViewModel = viewModel(factory = ReplyPhrasesViewModel.Factory(app))
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showAddDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.reply_detection_title)) }) },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Filled.Add, contentDescription = null)
            }
        }
    ) { padding ->
        if (state.rules.isEmpty()) {
            EmptyState(
                message = stringResource(R.string.reply_phrases_empty),
                modifier = Modifier.padding(padding)
            )
        } else {
            LazyColumn(
                modifier = Modifier.padding(padding).fillMaxWidth(),
                contentPadding = ScreenPadding,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(state.rules, key = { it.id }) { rule ->
                    ReplyPhraseCard(
                        rule = rule,
                        onToggle = { viewModel.setEnabled(rule, it) },
                        onDelete = { viewModel.delete(rule.id) }
                    )
                }
            }
        }
    }

    if (showAddDialog) {
        AddReplyPhraseDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { phrase ->
                viewModel.addPhrase(phrase)
                showAddDialog = false
            }
        )
    }
}

@Composable
private fun ReplyPhraseCard(rule: ReplyPhraseRule, onToggle: (Boolean) -> Unit, onDelete: () -> Unit) {
    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(rule.phrase, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Switch(checked = rule.enabled, onCheckedChange = onToggle)
            IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, contentDescription = null) }
        }
    }
}

@Composable
private fun AddReplyPhraseDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var phrase by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.add_phrase_title)) },
        text = {
            OutlinedTextField(
                value = phrase,
                onValueChange = { phrase = it },
                label = { Text(stringResource(R.string.phrase_label)) },
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = { TextButton(onClick = { onConfirm(phrase) }) { Text(stringResource(R.string.action_add)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } }
    )
}
