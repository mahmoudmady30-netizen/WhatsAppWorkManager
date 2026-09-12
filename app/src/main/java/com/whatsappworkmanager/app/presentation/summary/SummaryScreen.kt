package com.whatsappworkmanager.app.presentation.summary

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Reply
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.whatsappworkmanager.app.R
import com.whatsappworkmanager.app.WwmApplication
import com.whatsappworkmanager.app.domain.model.SummaryItem
import com.whatsappworkmanager.app.domain.model.SummaryTier
import com.whatsappworkmanager.app.domain.model.displayName
import com.whatsappworkmanager.app.domain.model.WorkSummary
import com.whatsappworkmanager.app.presentation.components.EmptyState
import com.whatsappworkmanager.app.presentation.components.MessagingPlatformBadge
import com.whatsappworkmanager.app.presentation.components.FullScreenLoading
import com.whatsappworkmanager.app.presentation.components.ScreenPadding
import com.whatsappworkmanager.app.presentation.search.detectLanguage
import com.whatsappworkmanager.app.presentation.search.fetchRecentConversationTexts
import com.whatsappworkmanager.app.presentation.search.findSavedPhoneForName
import com.whatsappworkmanager.app.presentation.theme.OkGreen
import com.whatsappworkmanager.app.presentation.theme.UrgentRed
import com.whatsappworkmanager.app.presentation.theme.WarnAmber
import com.whatsappworkmanager.app.utils.IntentHelper
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SummaryScreen() {
    val context = LocalContext.current
    val app = context.applicationContext as WwmApplication
    val viewModel: SummaryViewModel = viewModel(factory = SummaryViewModel.Factory(app))
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()

    var replyDialogFor by remember { mutableStateOf<SummaryItem?>(null) }
    var suggestedReplies by remember { mutableStateOf<List<String>>(emptyList()) }
    var isLoadingReplies by remember { mutableStateOf(false) }
    var replySourceLabel by remember { mutableStateOf<String?>(null) }
    var customReplyText by remember { mutableStateOf("") }
    var isRefiningDraft by remember { mutableStateOf(false) }
    var deleteConfirmFor by remember { mutableStateOf<WorkSummary?>(null) }

    fun generateRepliesFor(item: SummaryItem) {
        suggestedReplies = emptyList()
        replySourceLabel = null
        isLoadingReplies = true
        val language = detectLanguage(item.text)
        scope.launch {
            // Summary items don't carry a timestamp of their own, so this can't exclude
            // messages that arrived after this specific one the way the Search screen's
            // version can — it just takes the most recent few from this group/contact as
            // context, which is still meaningfully better than a single line in isolation.
            val conversation = fetchRecentConversationTexts(app, item.groupName, System.currentTimeMillis())
            val provider = app.aiProviderFactory.resolveActiveProvider()
            if (provider != null) {
                try {
                    suggestedReplies = provider.suggestRepliesForConversation(conversation, language)
                    replySourceLabel = provider.id
                } catch (e: Exception) {
                    suggestedReplies = app.aiProviderFactory.localProvider().suggestReplies(item.text, language)
                    replySourceLabel = "local (fallback — ${provider.id} failed: ${e.message ?: "error"})"
                }
            } else {
                suggestedReplies = app.aiProviderFactory.localProvider().suggestReplies(item.text, language)
                replySourceLabel = "local"
            }
            isLoadingReplies = false
        }
    }

    fun openReplyDialog(item: SummaryItem) {
        replyDialogFor = item
        customReplyText = ""
        generateRepliesFor(item)
    }

    /** Sends the user's own draft to the AI as an instruction to polish — not as an incoming
     *  message to reply to (see AiSummaryProvider.refineReply) — and replaces the draft with
     *  the result, so the user can review/edit it further before tapping Use. */
    fun refineDraft() {
        if (customReplyText.isBlank() || isRefiningDraft) return
        val draft = customReplyText
        val language = detectLanguage(draft)
        isRefiningDraft = true
        scope.launch {
            val provider = app.aiProviderFactory.resolveActiveProvider()
            customReplyText = try {
                provider?.refineReply(draft, language) ?: draft
            } catch (e: Exception) {
                draft
            }
            isRefiningDraft = false
        }
    }

    fun useReply(item: SummaryItem, text: String) {
        if (text.isBlank()) return
        scope.launch {
            val variant = app.settingsDataStore.whatsappVariant.first()
            val phone = findSavedPhoneForName(app, item.groupName)
            if (phone != null) {
                IntentHelper.openWhatsAppChat(context, phone, text, variant)
            } else {
                clipboard.setText(AnnotatedString(text))
                IntentHelper.openWhatsApp(context, variant)
                android.widget.Toast.makeText(
                    context,
                    context.getString(R.string.no_saved_number_fallback_fmt, item.groupName),
                    android.widget.Toast.LENGTH_LONG
                ).show()
            }
        }
        replyDialogFor = null
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.summary_title)) },
                actions = {
                    // Same flag-icon language switch shared with the Dashboard — a proper
                    // dropdown to choose from rather than a bare-tap toggle. Surfaced right
                    // where the AI-generated content whose language it actually controls is
                    // being read, since that's where the question "why is this in the wrong
                    // language" naturally comes up.
                    com.whatsappworkmanager.app.presentation.components.LanguageFlagMenu(app, context)
                }
            )
        }
    ) { padding ->
        when {
            state.isLoading -> FullScreenLoading(Modifier.padding(padding))
            state.summaries.isEmpty() -> EmptyState(
                message = stringResource(R.string.no_summary_yet),
                modifier = Modifier.padding(padding)
            )
            else -> LazyColumn(
                modifier = Modifier.padding(padding).fillMaxWidth(),
                contentPadding = ScreenPadding,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                itemsIndexed(state.summaries, key = { _, summary -> summary.id }) { index, summary ->
                    SummaryCard(
                        number = index + 1,
                        summary = summary,
                        onGenerateReply = { item -> openReplyDialog(item) },
                        onTogglePinned = { viewModel.togglePinned(summary) },
                        onDeleteRequest = { deleteConfirmFor = summary }
                    )
                }
            }
        }
    }

    deleteConfirmFor?.let { summary ->
        AlertDialog(
            onDismissRequest = { deleteConfirmFor = null },
            title = { Text(stringResource(R.string.delete_summary_title)) },
            text = { Text(stringResource(R.string.delete_summary_body)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteSummary(summary.id)
                    deleteConfirmFor = null
                }) { Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { deleteConfirmFor = null }) { Text(stringResource(R.string.action_cancel)) }
            }
        )
    }

    replyDialogFor?.let { item ->
        AlertDialog(
            onDismissRequest = { replyDialogFor = null },
            title = { Text(stringResource(R.string.generate_reply)) },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 520.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(item.text, style = MaterialTheme.typography.bodyMedium)
                    if (isLoadingReplies) {
                        CircularProgressIndicator(modifier = Modifier.padding(top = 8.dp))
                    } else {
                        replySourceLabel?.let { source ->
                            val isLocal = source.startsWith("local")
                            Text(
                                if (isLocal) "⚪ Generated locally (offline) — $source" else "🟢 Generated by $source",
                                style = MaterialTheme.typography.labelMedium,
                                color = if (isLocal) MaterialTheme.colorScheme.secondary else OkGreen
                            )
                        }
                        suggestedReplies.forEach { suggestion ->
                            SummaryReplySuggestionRow(text = suggestion, onUse = { useReply(item, suggestion) })
                        }
                        TextButton(onClick = { generateRepliesFor(item) }) {
                            Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(stringResource(R.string.regenerate_replies))
                        }
                    }
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    Text(stringResource(R.string.write_your_own_reply), style = MaterialTheme.typography.labelLarge)
                    OutlinedTextField(
                        value = customReplyText,
                        onValueChange = { customReplyText = it },
                        placeholder = { Text(stringResource(R.string.write_your_own_reply_hint)) },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { refineDraft() },
                            enabled = customReplyText.isNotBlank() && !isRefiningDraft
                        ) {
                            if (isRefiningDraft) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Filled.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(stringResource(R.string.refine_with_ai))
                        }
                        Button(
                            onClick = { useReply(item, customReplyText) },
                            enabled = customReplyText.isNotBlank()
                        ) {
                            Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(stringResource(R.string.action_use))
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { replyDialogFor = null }) { Text(stringResource(R.string.action_close)) }
            }
        )
    }
}

@Composable
private fun SummaryReplySuggestionRow(text: String, onUse: () -> Unit) {
    // Same reasoning as SearchScreen's ReplySuggestionRow: the reply's own detected language
    // decides this card's layout direction, independent of the app UI's own current language.
    val direction = if (detectLanguage(text) == "ar") LayoutDirection.Rtl else LayoutDirection.Ltr
    CompositionLocalProvider(LocalLayoutDirection provides direction) {
        Card(
            shape = MaterialTheme.shapes.small,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Button(onClick = onUse, modifier = Modifier.align(androidx.compose.ui.Alignment.End)) {
                    Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(R.string.action_use))
                }
            }
        }
    }
}

@Composable
private fun SummaryCard(
    number: Int,
    summary: WorkSummary,
    onGenerateReply: (SummaryItem) -> Unit,
    onTogglePinned: () -> Unit,
    onDeleteRequest: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("EEE d MMM · hh:mm a", Locale.getDefault()) }
    // The AI prompt now explicitly asks for a one-line takeaway first, then a blank line,
    // then the details — splitting on the first blank line separates "the one thing worth
    // reading even with zero taps" from "the rest," which is what makes collapse/expand
    // actually useful instead of just hiding an equally-important second half.
    val trimmedText = summary.text.trim()
    val blankLineIndex = trimmedText.indexOf("\n\n")
    val headline = if (blankLineIndex > 0) trimmedText.substring(0, blankLineIndex).trim() else trimmedText
    val rest = if (blankLineIndex > 0) trimmedText.substring(blankLineIndex).trim() else ""
    var expanded by remember(summary.id) { mutableStateOf(false) }

    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = if (summary.isPinned) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                // A small numbered badge — a simple, considered touch that makes a long list
                // of summaries easy to refer to ("the third one") instead of an undifferentiated
                // stack of identical-looking cards.
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f), shape = CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        number.toString(),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(dateFormat.format(Date(summary.createdAt)), style = MaterialTheme.typography.labelMedium)
                    if (summary.isPinned) {
                        Text(
                            "📌 " + stringResource(R.string.summary_pinned_label),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                IconButton(onClick = onTogglePinned) {
                    Icon(
                        Icons.Filled.PushPin,
                        contentDescription = stringResource(if (summary.isPinned) R.string.summary_unpin else R.string.summary_pin),
                        tint = if (summary.isPinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onDeleteRequest) {
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = stringResource(R.string.action_delete),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Text(headline, style = MaterialTheme.typography.bodyLarge)

            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (rest.isNotEmpty()) {
                        Text(rest, style = MaterialTheme.typography.bodyMedium)
                    }
                    HorizontalDivider()
                    // Two rows of two, each stat given equal weight — a single row of four
                    // plain Text elements let the last one ("Groups: N") get squeezed into an
                    // oddly narrow, multi-line sliver on normal phone widths. Weighting
                    // guarantees each stat gets an even, predictable share of the width no
                    // matter the screen size.
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxWidth()) {
                        Text(
                            stringResource(R.string.summary_total_fmt, summary.totalMessages),
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            stringResource(R.string.summary_important_fmt, summary.importantCount),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxWidth()) {
                        Text(
                            stringResource(R.string.summary_need_reply_fmt, summary.needReplyCount),
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            stringResource(R.string.summary_groups_fmt, summary.groupCount),
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    if (summary.items.isNotEmpty()) {
                        HorizontalDivider()
                        summary.items.forEach { item ->
                            SummaryItemRow(item = item, onGenerateReply = { onGenerateReply(item) })
                        }
                    }
                }
            }

            // The expand/collapse control itself — tapping it is what makes the card grow
            // ("push" everything below it down the list) or shrink back, as requested.
            TextButton(
                onClick = { expanded = !expanded },
                modifier = Modifier.align(Alignment.CenterHorizontally)
            ) {
                Icon(
                    if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(stringResource(if (expanded) R.string.summary_show_less else R.string.summary_show_more))
            }
        }
    }
}

@Composable
private fun SummaryItemRow(item: SummaryItem, onGenerateReply: () -> Unit) {
    val tierColor = when (item.tier) {
        SummaryTier.IMPORTANT -> UrgentRed
        SummaryTier.FOLLOW_UP -> WarnAmber
        SummaryTier.GENERAL -> MaterialTheme.colorScheme.secondary
    }
    Column(modifier = Modifier.padding(vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            MessagingPlatformBadge(item.platform)
            Text(item.groupName, style = MaterialTheme.typography.labelLarge, color = tierColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(item.text, style = MaterialTheme.typography.bodyMedium)
        OutlinedButton(onClick = onGenerateReply, modifier = Modifier.padding(top = 4.dp)) {
            Icon(Icons.Filled.Reply, contentDescription = null, modifier = Modifier.size(14.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(stringResource(R.string.generate_reply))
        }
    }
}
