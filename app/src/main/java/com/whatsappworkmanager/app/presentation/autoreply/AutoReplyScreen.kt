package com.whatsappworkmanager.app.presentation.autoreply

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.whatsappworkmanager.app.R
import com.whatsappworkmanager.app.WwmApplication
import com.whatsappworkmanager.app.domain.model.AutoReplyRule
import com.whatsappworkmanager.app.domain.model.MessagingPlatform
import com.whatsappworkmanager.app.domain.model.displayName
import com.whatsappworkmanager.app.presentation.components.EmptyState
import com.whatsappworkmanager.app.presentation.components.PickFromContactsButton
import com.whatsappworkmanager.app.presentation.components.ScreenPadding
import com.whatsappworkmanager.app.presentation.components.MessagingPlatformBadge
import com.whatsappworkmanager.app.presentation.components.MessagingPlatformSelector

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AutoReplyScreen() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val app = context.applicationContext as WwmApplication
    val vm: AutoReplyViewModel = viewModel(factory = AutoReplyViewModel.Factory(app))
    val state by vm.uiState.collectAsStateWithLifecycle()
    var showAdd by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<AutoReplyRule?>(null) }
    var toneFilter by remember { mutableStateOf<com.whatsappworkmanager.app.domain.model.ReplyTone?>(null) }
    var repliesForRule by remember { mutableStateOf<AutoReplyRule?>(null) }
    val visible = if (toneFilter == null) state.rules else state.rules.filter { it.tone == toneFilter }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("WA premium")
                        Spacer(Modifier.width(8.dp))
                        AssistChip(
                            onClick = {},
                            enabled = false,
                            label = { Text("PREMIUM", style = MaterialTheme.typography.labelSmall) },
                            leadingIcon = { Icon(Icons.Filled.AutoAwesome, null, Modifier.size(14.dp)) }
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            SmallFloatingActionButton(
                onClick = { showAdd = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.action_add), Modifier.size(20.dp))
            }
        }
    ) { padding ->
        if (state.rules.isEmpty()) {
            EmptyState(
                message = stringResource(R.string.auto_reply_empty),
                modifier = Modifier.padding(padding)
            )
        } else {
            LazyColumn(
                modifier = Modifier.padding(padding).fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(9.dp)
            ) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.padding(horizontal = 14.dp, vertical = 11.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.AutoAwesome, null, Modifier.size(17.dp), tint = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.width(7.dp))
                                Text("AI Auto Reply", style = MaterialTheme.typography.titleSmall)
                            }
                            Text(
                                stringResource(R.string.auto_reply_premium_subtitle),
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            OutlinedButton(
                                onClick = {
                                    context.startActivity(
                                        android.content.Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS)
                                    )
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Filled.Send, null, Modifier.size(16.dp))
                                Spacer(Modifier.width(7.dp))
                                Text(stringResource(R.string.auto_reply_enable_accessibility))
                            }
                        }
                    }
                }
                item {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth().horizontalScroll(androidx.compose.foundation.rememberScrollState())
                    ) {
                        FilterChip(toneFilter == null, { toneFilter = null }, label = { Text(stringResource(R.string.filter_all)) })
                        com.whatsappworkmanager.app.domain.model.ReplyTone.entries.forEach {
                            FilterChip(toneFilter == it, { toneFilter = it }, label = { Text(toneLabel(it)) })
                        }
                    }
                }
                itemsIndexed(visible, key = { _, r -> r.id }) { index, rule ->
                    AutoReplyCard(
                        index + 1, rule,
                        onToggle = { vm.setEnabled(rule, it) },
                        onEdit = { editing = rule },
                        onDelete = { vm.delete(rule.id) },
                        onToneChange = { vm.setTone(rule, it) },
                        replyCount = state.replyHistory.count { it.ruleId == rule.id },
                        onShowReplies = { repliesForRule = rule }
                    )
                }
            }
        }
    }

    if (showAdd) {
        AutoReplyRuleDialog(
            existing = null,
            onDismiss = { showAdd = false },
            onConfirm = { person, phone, keyword, reply, instruction, autoSend, tone, platform ->
                vm.addRule(person, phone, keyword, reply, instruction, autoSend, tone, platform)
                showAdd = false
            }
        )
    }
    editing?.let { rule ->
        AutoReplyRuleDialog(
            existing = rule,
            onDismiss = { editing = null },
            onConfirm = { person, phone, keyword, reply, instruction, autoSend, tone, platform ->
                vm.updateRule(rule, person, phone, keyword, reply, instruction, autoSend, tone, platform)
                editing = null
            }
        )
    }
    repliesForRule?.let { rule ->
        AutoReplyHistoryDialog(
            rule = rule,
            replies = state.replyHistory.filter { it.ruleId == rule.id },
            onDismiss = { repliesForRule = null }
        )
    }
}

@Composable
private fun AutoReplyCard(
    index: Int,
    rule: AutoReplyRule,
    onToggle: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onToneChange: (com.whatsappworkmanager.app.domain.model.ReplyTone) -> Unit,
    replyCount: Int,
    onShowReplies: () -> Unit
) {
    var showTone by remember { mutableStateOf(false) }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        "$index. ${rule.personMatch.trim().takeIf { it.isNotBlank() } ?: stringResource(R.string.auto_reply_all_contacts)}",
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                    MessagingPlatformBadge(rule.platform)
                }
                Switch(checked = rule.enabled, onCheckedChange = onToggle)
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AssistChip(onClick = { showTone = true }, label = { Text(toneLabel(rule.tone)) })
                if (rule.autoSend) {
                    AssistChip(
                        onClick = {},
                        label = { Text(stringResource(R.string.auto_reply_auto_send_badge)) },
                        leadingIcon = { Icon(Icons.Filled.Send, null, Modifier.size(12.dp)) }
                    )
                }
                if (replyCount > 0) {
                    AssistChip(
                        onClick = onShowReplies,
                        label = { Text(stringResource(R.string.auto_reply_show_replies, replyCount), style = MaterialTheme.typography.labelSmall) },
                        leadingIcon = { Icon(Icons.Filled.History, null, Modifier.size(13.dp)) }
                    )
                }
            }
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
            ) {
                Text(
                    rule.keyword?.let { "Match: \"$it\"" } ?: stringResource(R.string.auto_reply_any_message),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                )
            }
            if (!rule.aiInstruction.isNullOrBlank()) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.35f)
                ) {
                    Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text("AI instruction", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        Text(
                            rule.aiInstruction,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 3, overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            } else {
                Text(
                    rule.replyText?.takeIf { it.isNotBlank() } ?: stringResource(R.string.auto_reply_ai_generated_badge),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 3, overflow = TextOverflow.Ellipsis
                )
            }
            Row {
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onEdit) { Icon(Icons.Filled.Edit, null) }
                IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, null) }
            }
        }
    }
    DropdownMenu(showTone, { showTone = false }) {
        com.whatsappworkmanager.app.domain.model.ReplyTone.entries.forEach {
            DropdownMenuItem(
                text = { Text(toneLabel(it)) },
                trailingIcon = { if (it == rule.tone) Icon(Icons.Filled.Check, null) },
                onClick = { showTone = false; onToneChange(it) }
            )
        }
    }
}

@Composable
private fun AutoReplyHistoryDialog(
    rule: AutoReplyRule,
    replies: List<com.whatsappworkmanager.app.domain.model.AutoReplyReply>,
    onDismiss: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text("AI replies used")
                Text(
                    rule.personMatch,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        },
        text = {
            if (replies.isEmpty()) {
                Text("No AI-generated replies have been used for this person yet.")
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 520.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    itemsIndexed(replies, key = { _, item -> item.id }) { _, item ->
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                        ) {
                            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(dateFormat.format(Date(item.createdAt)), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                Text("Incoming", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(item.incomingText, style = MaterialTheme.typography.bodySmall)
                                HorizontalDivider()
                                Text("AI reply", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                Text(item.replyText, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) }
        }
    )
}

@Composable
private fun AutoReplyRuleDialog(
    existing: AutoReplyRule?,
    onDismiss: () -> Unit,
    onConfirm: (
        person: String, phone: String?, keyword: String?, reply: String?,
        instruction: String?, autoSend: Boolean,
        tone: com.whatsappworkmanager.app.domain.model.ReplyTone,
        platform: MessagingPlatform
    ) -> Unit
) {
    var person by remember { mutableStateOf(existing?.personMatch.orEmpty()) }
    var phone by remember { mutableStateOf(existing?.phoneNumber.orEmpty()) }
    var keyword by remember { mutableStateOf(existing?.keyword.orEmpty()) }
    var reply by remember { mutableStateOf(existing?.replyText.orEmpty()) }
    var instruction by remember { mutableStateOf(existing?.aiInstruction.orEmpty()) }
    var autoSend by remember { mutableStateOf(existing?.autoSend ?: false) }
    var platform by remember { mutableStateOf(existing?.platform ?: MessagingPlatform.WHATSAPP) }
    var tone by remember { mutableStateOf(existing?.tone ?: com.whatsappworkmanager.app.domain.model.ReplyTone.DEFAULT) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.AutoAwesome, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Column {
                    Text(
                        stringResource(if (existing == null) R.string.auto_reply_add_title else R.string.auto_reply_edit_title),
                        style = MaterialTheme.typography.titleLarge
                    )
                    Text(
                        "Configure how AI handles this conversation",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        text = {
            Column(
                Modifier.fillMaxWidth().heightIn(max = 580.dp)
                    .verticalScroll(androidx.compose.foundation.rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(stringResource(R.string.auto_reply_premium_rule_hint), style = MaterialTheme.typography.bodySmall)
                Text(
                    stringResource(R.string.messaging_platform_label),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                MessagingPlatformSelector(
                    selected = platform,
                    onSelected = { platform = it }
                )
                if (platform != MessagingPlatform.MESSENGER) {
                    PickFromContactsButton(
                        onPicked = { name, number ->
                            val existingNames = person.split(',', ';', '\n').map { it.trim() }.filter { it.isNotBlank() }
                            person = (existingNames + name.trim()).distinct().joinToString(", ")
                            if (existingNames.isEmpty()) phone = number.orEmpty()
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                OutlinedTextField(
                    value = person, onValueChange = { person = it },
                    label = { Text(if (platform == MessagingPlatform.MESSENGER) "Messenger users" else stringResource(R.string.auto_reply_person_label)) },
                    placeholder = { Text(if (platform == MessagingPlatform.MESSENGER) "e.g. Ahmed, Sarah, @client" else stringResource(R.string.auto_reply_person_hint)) },
                    supportingText = { Text(if (platform == MessagingPlatform.MESSENGER) "Add one or more Messenger names/usernames, separated by commas." else stringResource(R.string.auto_reply_person_support)) },
                    modifier = Modifier.fillMaxWidth(), minLines = 1, maxLines = 3
                )
                if (platform != MessagingPlatform.MESSENGER && phone.isNotBlank()) {
                    Text("${platform.displayName()} target: $phone", style = MaterialTheme.typography.labelSmall)
                }
                Text(stringResource(R.string.auto_reply_tone_label), style = MaterialTheme.typography.labelLarge)
                ToneSelector(tone, { tone = it })
                OutlinedTextField(
                    value = keyword, onValueChange = { keyword = it },
                    label = { Text(stringResource(R.string.auto_reply_keyword_label)) },
                    modifier = Modifier.fillMaxWidth(), singleLine = true
                )
                OutlinedTextField(
                    value = instruction, onValueChange = { instruction = it },
                    label = { Text(stringResource(R.string.auto_reply_ai_instruction_label)) },
                    placeholder = { Text(stringResource(R.string.auto_reply_ai_instruction_hint)) },
                    modifier = Modifier.fillMaxWidth(), minLines = 3
                )
                Text(
                    stringResource(R.string.auto_reply_ai_instruction_explanation),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = reply, onValueChange = { reply = it },
                    label = { Text(stringResource(R.string.auto_reply_text_label)) },
                    placeholder = { Text(stringResource(R.string.auto_reply_text_ai_hint)) },
                    modifier = Modifier.fillMaxWidth(), minLines = 2
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.auto_reply_auto_send_title), style = MaterialTheme.typography.titleSmall)
                        Text(stringResource(R.string.auto_reply_auto_send_desc), style = MaterialTheme.typography.labelSmall)
                    }
                    Switch(checked = autoSend, onCheckedChange = { autoSend = it })
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm(
                        person.trim(), phone.trim().ifBlank { null },
                        keyword.trim().ifBlank { null }, reply.trim().ifBlank { null },
                        instruction.trim().ifBlank { null }, autoSend, tone, platform
                    )
                }
            ) { Text(stringResource(if (existing == null) R.string.action_add else R.string.action_update)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } }
    )
}

@Composable
fun ToneSelector(
    selected: com.whatsappworkmanager.app.domain.model.ReplyTone,
    onSelected: (com.whatsappworkmanager.app.domain.model.ReplyTone) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(horizontalArrangement = Arrangement.spacedBy(7.dp), modifier = modifier.fillMaxWidth()) {
        com.whatsappworkmanager.app.domain.model.ReplyTone.entries.forEach {
            val selectedNow = it == selected
            OutlinedButton(
                onClick = { onSelected(it) },
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 9.dp),
                colors = if (selectedNow)
                    ButtonDefaults.outlinedButtonColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                else ButtonDefaults.outlinedButtonColors()
            ) {
                if (selectedNow) {
                    Icon(Icons.Filled.Circle, null, Modifier.size(8.dp))
                    Spacer(Modifier.width(5.dp))
                }
                Text(toneLabel(it), maxLines = 1, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
fun toneLabel(tone: com.whatsappworkmanager.app.domain.model.ReplyTone): String = when (tone) {
    com.whatsappworkmanager.app.domain.model.ReplyTone.WORK -> stringResource(R.string.auto_reply_tone_work)
    com.whatsappworkmanager.app.domain.model.ReplyTone.FRIEND -> stringResource(R.string.auto_reply_tone_friend)
    com.whatsappworkmanager.app.domain.model.ReplyTone.FAMILY -> stringResource(R.string.auto_reply_tone_family)
}
