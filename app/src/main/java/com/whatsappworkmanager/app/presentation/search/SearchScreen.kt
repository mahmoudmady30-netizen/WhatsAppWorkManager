package com.whatsappworkmanager.app.presentation.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.filled.ContactPhone
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Reply
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.whatsappworkmanager.app.R
import com.whatsappworkmanager.app.WwmApplication
import com.whatsappworkmanager.app.domain.model.WorkMessage
import com.whatsappworkmanager.app.presentation.components.EmptyState
import com.whatsappworkmanager.app.presentation.components.MessagingPlatformBadge
import com.whatsappworkmanager.app.presentation.components.ScreenPadding
import com.whatsappworkmanager.app.presentation.theme.OkGreen
import com.whatsappworkmanager.app.utils.IntentHelper
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Best-effort language detection for a captured message — looks for any character in the
 * Arabic Unicode blocks. Good enough to decide "should the reply come back in Egyptian Arabic
 * or English", which is all this is used for; not a general-purpose language detector.
 */
internal fun detectLanguage(text: String): String {
    val arabicRange = Regex("[\u0600-\u06FF\u0750-\u077F\u08A0-\u08FF\uFB50-\uFDFF\uFE70-\uFEFF]")
    return if (arabicRange.containsMatchIn(text)) "ar" else "en"
}

/**
 * Best-effort "is this a group or a 1:1 chat" — WhatsApp's own notification format is the
 * signal: group notifications include a "Sender: message" prefix in the body (so our parser —
 * see NotificationTextParser — extracts a non-null [WorkMessage.sender]), while 1:1 chats don't
 * need that prefix (title already is the contact's name), so sender comes back null. Not
 * perfect for every WhatsApp build/OEM, but requires no extra data and is right the vast
 * majority of the time.
 */
private fun isGroupMessage(message: WorkMessage): Boolean = message.sender != null

internal fun formatMessageTimestamp(timestamp: Long): String {
    val time = SimpleDateFormat("h:mm a", Locale.getDefault())
    val now = Calendar.getInstance()
    val then = Calendar.getInstance().apply { timeInMillis = timestamp }
    val isToday = now.get(Calendar.YEAR) == then.get(Calendar.YEAR) &&
        now.get(Calendar.DAY_OF_YEAR) == then.get(Calendar.DAY_OF_YEAR)
    val isThisYear = now.get(Calendar.YEAR) == then.get(Calendar.YEAR)
    return when {
        isToday -> "Today · ${time.format(Date(timestamp))}"
        isThisYear -> SimpleDateFormat("MMM d · h:mm a", Locale.getDefault()).format(Date(timestamp))
        else -> SimpleDateFormat("MMM d, yyyy · h:mm a", Locale.getDefault()).format(Date(timestamp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(initialFilter: MessageFilter = MessageFilter.ALL) {
    val context = LocalContext.current
    val app = context.applicationContext as WwmApplication
    val viewModel: SearchViewModel = viewModel(factory = SearchViewModel.Factory(app, initialFilter))
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val refreshedMessage = stringResource(R.string.dashboard_refreshed)

    var replyDialogFor by remember { mutableStateOf<WorkMessage?>(null) }
    var showDeleteAllConfirm by remember { mutableStateOf(false) }
    var suggestedReplies by remember { mutableStateOf<List<String>>(emptyList()) }
    var isLoadingReplies by remember { mutableStateOf(false) }
    var deleteConfirmFor by remember { mutableStateOf<WorkMessage?>(null) }
    var addImportantPersonFor by remember { mutableStateOf<WorkMessage?>(null) }
    var customReplyText by remember { mutableStateOf("") }
    var isRefiningDraft by remember { mutableStateOf(false) }
    // Which provider actually produced the suggestions currently shown — so the user can see
    // for themselves whether a cloud AI genuinely responded, or the app silently fell back to
    // the local, offline suggestions (e.g. because the API key is wrong or the request failed).
    var replySourceLabel by remember { mutableStateOf<String?>(null) }

    fun startReplyGeneration(message: WorkMessage) {
        suggestedReplies = emptyList()
        replySourceLabel = null
        isLoadingReplies = true
        val language = detectLanguage(message.text)
        scope.launch {
            val conversation = fetchRecentConversationTexts(app, message.groupName, message.timestamp)
            val provider = app.aiProviderFactory.resolveActiveProvider()
            if (provider != null) {
                try {
                    suggestedReplies = provider.suggestRepliesForConversation(conversation, language)
                    replySourceLabel = provider.id
                } catch (e: Exception) {
                    suggestedReplies = app.aiProviderFactory.localProvider()
                        .suggestReplies(message.text, language)
                    replySourceLabel = "local (fallback — ${provider.id} failed: ${e.message ?: "error"})"
                }
            } else {
                suggestedReplies = app.aiProviderFactory.localProvider().suggestReplies(message.text, language)
                replySourceLabel = "local"
            }
            isLoadingReplies = false
        }
    }

    /** Sends the user's own draft to the AI as an instruction to polish — not as an incoming
     *  message to reply to (see AiSummaryProvider.refineReply) — and replaces the draft with
     *  the result, so the user can review/edit it further before tapping Use. */
    fun refineDraft(originalMessage: WorkMessage) {
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

    fun openReplyDialog(message: WorkMessage) {
        replyDialogFor = message
        customReplyText = ""
        startReplyGeneration(message)
    }

    /** Shared by both an AI suggestion's "Use" button and the custom reply field's "Use"
     *  button — same best-effort direct-chat logic either way. */
    fun useReply(message: WorkMessage, text: String) {
        if (text.isBlank()) return
        scope.launch {
            val variant = app.settingsDataStore.whatsappVariant.first()
            val phone = findSavedPhoneForSender(app, message)
            val pkg = if (variant == IntentHelper.WHATSAPP_VARIANT_BUSINESS)
                com.whatsappworkmanager.app.utils.Constants.WHATSAPP_BUSINESS_PACKAGE
            else com.whatsappworkmanager.app.utils.Constants.WHATSAPP_PACKAGE
            // Premium "Use" means use-and-send: queue the exact approved text for the
            // user-enabled Accessibility Service, which performs the final Send tap only
            // after verifying the visible WhatsApp chat.
            val key = "manual|${message.id}|${text.trim()}"
            com.whatsappworkmanager.app.service.AutoReplyQueue.enqueue(
                context,
                com.whatsappworkmanager.app.service.AutoReplyQueue.Job(
                    groupName = message.groupName,
                    incomingText = message.text,
                    replyText = text.trim(),
                    packageName = pkg,
                    phoneNumber = phone,
                    notBefore = System.currentTimeMillis() + 350L,
                    messageKey = key
                )
            )
            if (phone == null) {
                clipboard.setText(AnnotatedString(text))
            }
            IntentHelper.openWhatsApp(context, variant)
            android.widget.Toast.makeText(
                context,
                context.getString(R.string.auto_reply_use_queued),
                android.widget.Toast.LENGTH_SHORT
            ).show()
        }
        replyDialogFor = null
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.search_hint)) },
                actions = {
                    IconButton(onClick = {
                        // Same on-demand catch-up as the Dashboard's Refresh button: forces the
                        // notification listener to reconnect and re-scan every currently
                        // active WhatsApp notification against what's already captured — a
                        // genuine resync, not just a UI re-read of existing data (which the
                        // Flow-backed list below already does automatically anyway).
                        com.whatsappworkmanager.app.service.WhatsAppNotificationListenerService.triggerCatchUpScan(context)
                        scope.launch { snackbarHostState.showSnackbar(refreshedMessage) }
                    }) {
                        Icon(Icons.Filled.Refresh, contentDescription = stringResource(R.string.action_refresh))
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxWidth()) {
            OutlinedTextField(
                value = state.query,
                onValueChange = viewModel::onQueryChanged,
                modifier = Modifier.fillMaxWidth().padding(ScreenPadding),
                placeholder = { Text(stringResource(R.string.search_hint)) },
                singleLine = true
            )

            LazyRow(
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filterOptions()) { (filter, labelRes) ->
                    FilterChip(
                        selected = state.filter == filter,
                        onClick = { viewModel.onFilterChanged(filter) },
                        label = { Text(stringResource(labelRes)) }
                    )
                }
            }

            // Pinned above the results list regardless of which filter is active — not just
            // Today — since both already operate generically on whatever's currently in
            // `state.results`, whatever filter produced it.
            if (state.results.isNotEmpty()) {
                Row(modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)) {
                    if (state.results.any { !it.isRead }) {
                        TextButton(onClick = { viewModel.markAllVisibleAsRead() }) {
                            Icon(Icons.Filled.DoneAll, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(stringResource(R.string.read_all))
                        }
                    }
                    TextButton(onClick = { showDeleteAllConfirm = true }) {
                        Icon(Icons.Filled.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.delete_all))
                    }
                }
            }

            if (state.results.isEmpty()) {
                EmptyState(message = stringResource(R.string.empty_no_messages))
            } else {
                LazyColumn(
                    contentPadding = ScreenPadding,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    itemsIndexed(state.results, key = { _, message -> message.id }) { index, message ->
                        MessageResultCard(
                            number = index + 1,
                            message = message,
                            // Zebra striping: alternate row shading for a more professional,
                            // easier-to-scan list, matching what was asked for.
                            isAlternate = index % 2 == 1,
                            onOpen = { if (!message.isRead) viewModel.markAsRead(message.id) },
                            onGenerateReply = { openReplyDialog(message) },
                            onDelete = { deleteConfirmFor = message },
                            onToggleImportant = { viewModel.setImportant(message, !message.isImportant) },
                            onSaveAsImportantPerson = { addImportantPersonFor = message }
                        )
                    }
                }
            }
        }
    }

    replyDialogFor?.let { message ->
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
                    Text(message.text, style = MaterialTheme.typography.bodyMedium)
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
                        Text(
                            "Pick one. If this person has a saved phone number (Settings → " +
                                "Important People) or their WhatsApp name is already a phone " +
                                "number, it opens their exact chat with the reply already typed " +
                                "in — you just tap Send. Otherwise it copies the text and opens " +
                                "WhatsApp for you to paste it yourself.",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.secondary
                        )
                        suggestedReplies.forEach { suggestion ->
                            ReplySuggestionRow(text = suggestion, onUse = { useReply(message, suggestion) })
                        }
                        TextButton(onClick = { startReplyGeneration(message) }) {
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
                            onClick = { refineDraft(message) },
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
                            onClick = { useReply(message, customReplyText) },
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

    deleteConfirmFor?.let { message ->
        AlertDialog(
            onDismissRequest = { deleteConfirmFor = null },
            title = { Text(stringResource(R.string.delete_message_title)) },
            text = { Text(stringResource(R.string.delete_message_body)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteMessage(message.id)
                    deleteConfirmFor = null
                }) { Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { deleteConfirmFor = null }) { Text(stringResource(R.string.action_cancel)) }
            }
        )
    }

    if (showDeleteAllConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteAllConfirm = false },
            title = { Text(stringResource(R.string.delete_all_title)) },
            text = { Text(stringResource(R.string.delete_all_body_fmt, state.results.size)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteAllVisible()
                    showDeleteAllConfirm = false
                }) { Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteAllConfirm = false }) { Text(stringResource(R.string.action_cancel)) }
            }
        )
    }

    addImportantPersonFor?.let { message ->
        QuickAddImportantPersonDialog(
            app = app,
            prefillName = message.sender ?: message.groupName,
            onDismiss = { addImportantPersonFor = null }
        )
    }
}

/**
 * Looks up a saved phone number for the sender of [message] among Important People (Settings
 * → Important People) — same case-insensitive substring match used for importance scoring
 * elsewhere (e.g. "Ahmed" matches a saved name like "Ahmed Hassan").
 *
 * Checks **both** possible name fields, because WhatsApp's own notification format puts the
 * person's name in a different place depending on whether it's a 1:1 chat or a group message:
 *  - **1:1 chat**: WhatsApp doesn't prefix the notification body with a sender name (you
 *    already know who it's from) — the *title* is the contact's name, which this app stores
 *    as [WorkMessage.groupName]. [WorkMessage.sender] is null for these.
 *  - **Group message**: the title is the *group's* name, and the body is prefixed
 *    "Sender: message", so [WorkMessage.sender] holds the actual person's name.
 * The previous version only ever checked `sender`, which meant it could never find a match for
 * 1:1 chats — exactly the case where opening the right chat matters most — since `sender` is
 * always null there.
 */
/**
 * Gathers up to [maxCount] recent messages from the same conversation (group/contact), oldest
 * first, formatted as plain text lines ready for [AiSummaryProvider.suggestRepliesForConversation]
 * — so a reply can be generated with actual context (an earlier question this message is
 * answering, a thread of back-and-forth) instead of reacting to one line in isolation. Only
 * messages up to and including [uptoTimestamp] are included, so a reply generated for an older
 * message in the list never "sees" messages that arrived after it.
 */
internal suspend fun fetchRecentConversationTexts(
    app: WwmApplication,
    groupName: String,
    uptoTimestamp: Long,
    maxCount: Int = 5
): List<String> {
    val allForGroup = app.messageRepository.observeMessagesForGroup(groupName).first()
    return allForGroup
        .filter { it.timestamp <= uptoTimestamp }
        .sortedBy { it.timestamp }
        .takeLast(maxCount)
        .map { it.text }
}

internal suspend fun findSavedPhoneForSender(app: WwmApplication, message: WorkMessage): String? =
    findSavedPhoneForName(app, message.sender ?: message.groupName)

/**
 * Looks up a usable phone number for [name] — first among saved Important People (Settings →
 * Important People, case-insensitive substring match, e.g. "Ahmed" matches a saved "Ahmed
 * Hassan"), then falling back to recognizing [name] as already being a raw phone number itself
 * (see [extractPhoneNumberIfPresent]) for someone WhatsApp shows only by their number, not a
 * saved contact name. Shared by both the Search screen's reply flow and the Summary screen's
 * (a [WorkSummary]'s `SummaryItem`s only carry a group/sender name, not a full `WorkMessage`).
 */
internal suspend fun findSavedPhoneForName(app: WwmApplication, name: String): String? {
    if (name.isBlank()) return null
    val contacts = app.importantContactRepository.observeContacts().first()

    // Name-based match — bidirectional and whitespace-normalized. Originally only checked
    // "does the message's name contain the saved name", which silently failed whenever the
    // saved label was longer/more specific than what actually shows up in a message (e.g.
    // saved as "Eng. Ahmed Hassan - Supplier" but the message's sender is just "Ahmed") —
    // exactly the kind of case that made this feel randomly broken ("works sometimes"),
    // since it depended on which of the two names happened to be longer. Checking both
    // directions, and collapsing repeated whitespace before comparing, catches those.
    val normalizedName = name.trim().replace(Regex("\\s+"), " ")
    val savedMatch = contacts.firstOrNull { contact ->
        val normalizedContact = contact.name.trim().replace(Regex("\\s+"), " ")
        !contact.phoneNumber.isNullOrBlank() &&
            (normalizedName.contains(normalizedContact, ignoreCase = true) ||
                normalizedContact.contains(normalizedName, ignoreCase = true))
    }?.phoneNumber
    if (savedMatch != null) return savedMatch

    // Fallback: match by the actual phone digits when [name] itself looks like a raw phone
    // number (WhatsApp shows this for anyone not saved as a phone contact). More reliable
    // than name-substring matching for this specific case, since the *display name* text can
    // legitimately differ from whatever was typed in when the contact was saved (e.g. saved
    // via Important People with a chosen label, rather than the raw WhatsApp name) while the
    // underlying number is still exactly the same person.
    val nameAsPhone = extractPhoneNumberIfPresent(name)
    if (nameAsPhone != null) {
        val phoneMatch = contacts.firstOrNull { contact ->
            val savedDigits = contact.phoneNumber?.filter { it.isDigit() }
            !savedDigits.isNullOrBlank() && (savedDigits == nameAsPhone || savedDigits.endsWith(nameAsPhone) || nameAsPhone.endsWith(savedDigits))
        }
        if (phoneMatch != null) return phoneMatch.phoneNumber
    }

    // No saved Important Person matches — but for someone not in the user's phone contacts,
    // WhatsApp itself shows their raw phone number as the display name (e.g. "+971 56 233
    // 1154"). That name IS already a usable phone number, so use it directly rather than
    // requiring the user to separately save it as an Important Person first.
    return nameAsPhone
}

/**
 * Recognizes a WhatsApp-style "name" that's really just a phone number — WhatsApp shows raw
 * numbers (with a leading "+", spaces/dashes) as the display name for anyone not saved in the
 * user's phone contacts. Returns digits-only (ready for the wa.me deep link) or null if
 * [name] doesn't look like a phone number (an actual contact/group name will have letters and
 * fail the digit-ratio check below).
 */
internal fun extractPhoneNumberIfPresent(name: String): String? {
    val trimmed = name.trim()
    if (!trimmed.startsWith("+")) return null
    val digitsOnly = trimmed.filter { it.isDigit() }
    // A real international number is roughly 8-15 digits (ITU E.164); anything shorter or
    // longer is more likely a coincidental "+" somewhere in an actual name.
    if (digitsOnly.length !in 8..15) return null
    // Guard against a name like "+1 Team" that happens to start with "+1" — everything after
    // the leading "+" and its digits should itself be only formatting characters (spaces,
    // dashes, parens), never letters.
    val nonDigitsAfterPlus = trimmed.drop(1).filterNot { it.isDigit() }
    if (nonDigitsAfterPlus.any { it.isLetter() }) return null
    return digitsOnly
}

@Composable
private fun ReplySuggestionRow(text: String, onUse: () -> Unit) {
    // The reply's own language decides this card's layout direction — independent of whatever
    // language the app's UI chrome is currently in (exactly the mismatch in the screenshot:
    // an English UI with an Arabic-language reply). Without this, "align to End" always meant
    // the same fixed physical side regardless of the text's own reading direction, which for
    // RTL Arabic content put the Use button on the side an Arabic reader wouldn't expect —
    // the reading flow goes right-to-left, so the "next/confirm" action reads naturally on the
    // left, not the right.
    val direction = if (detectLanguage(text) == "ar") LayoutDirection.Rtl else LayoutDirection.Ltr
    CompositionLocalProvider(LocalLayoutDirection provides direction) {
        // Clearly-styled, filled Button (not a TextButton) so "Use" reads as an obvious
        // tappable action against any card background, instead of blending into the
        // surrounding text.
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
private fun MessageResultCard(
    number: Int,
    message: WorkMessage,
    isAlternate: Boolean,
    onOpen: () -> Unit,
    onGenerateReply: () -> Unit,
    onDelete: () -> Unit,
    onToggleImportant: () -> Unit,
    onSaveAsImportantPerson: () -> Unit
) {
    // Subtle zebra striping: alternating rows get a faint tint of the surface color rather
    // than a second hardcoded color, so it still looks right in both light and dark theme.
    // Unread messages get a slightly stronger tint on top of that, so they visually stand out
    // from already-read ones at a glance — similar to how a chat app's own inbox looks.
    val baseColor = if (isAlternate) {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
    } else {
        MaterialTheme.colorScheme.surface
    }
    val backgroundColor = if (!message.isRead) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
    } else {
        baseColor
    }
    val isGroup = isGroupMessage(message)
    val typeIcon = if (isGroup) Icons.Filled.Groups else Icons.Filled.Person
    val typeColor = if (isGroup) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary

    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = backgroundColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        // The whole card is tappable (with the standard Material ripple for visual feedback on
        // tap/press — the closest thing to "hover" this platform has). Tapping marks it read;
        // Reply and Delete stay as their own explicit actions below.
        modifier = Modifier.clickable(onClick = onOpen)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = androidx.compose.ui.Alignment.Top) {
                Text(
                    "$number.",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.padding(end = 8.dp)
                )
                Icon(
                    typeIcon,
                    contentDescription = if (isGroup) "Group" else "Individual",
                    tint = typeColor,
                    modifier = Modifier.size(18.dp).padding(top = 2.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                    ) {
                        Text(
                            message.groupName,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = if (!message.isRead) FontWeight.Bold else FontWeight.Normal,
                            color = typeColor,
                            modifier = Modifier.weight(1f)
                        )
                        MessagingPlatformBadge(message.platform, modifier = Modifier.padding(end = 2.dp))
                        IconButton(onClick = onToggleImportant, modifier = Modifier.size(28.dp)) {
                            Icon(
                                if (message.isImportant) Icons.Filled.Star else Icons.Filled.StarBorder,
                                contentDescription = stringResource(R.string.toggle_important),
                                modifier = Modifier.size(18.dp),
                                tint = if (message.isImportant) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.secondary
                            )
                        }
                        IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
                            Icon(
                                Icons.Filled.Delete,
                                contentDescription = "Delete",
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.secondary
                            )
                        }
                    }
                    var expanded by remember(message.id) { mutableStateOf(false) }
                    var showSeeMoreFor by remember(message.id) { mutableStateOf(false) }
                    Text(
                        message.text,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (!message.isRead) FontWeight.Medium else FontWeight.Normal,
                        maxLines = if (expanded) Int.MAX_VALUE else 4,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        onTextLayout = { layoutResult ->
                            if (!expanded && layoutResult.hasVisualOverflow) {
                                showSeeMoreFor = true
                            }
                        }
                    )
                    // "See more"/"See less" only actually appears once the text is long enough
                    // to be clipped at 4 lines in the first place — a short message never
                    // shows a toggle with nothing behind it to expand.
                    if (showSeeMoreFor || expanded) {
                        TextButton(
                            onClick = { expanded = !expanded },
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 2.dp)
                        ) {
                            Text(
                                stringResource(if (expanded) R.string.summary_show_less else R.string.summary_show_more),
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    }
                    Row(
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                        modifier = Modifier.padding(top = 4.dp)
                    ) {
                        Text(
                            formatMessageTimestamp(message.timestamp),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.secondary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        if (message.isRead) {
                            Icon(
                                Icons.Filled.DoneAll,
                                contentDescription = "Read",
                                modifier = Modifier.size(14.dp),
                                tint = Color(0xFF34B7F1) // WhatsApp's familiar "read" blue — purely a
                                // local, in-app indicator; this never touches WhatsApp's own
                                // read receipts or the actual sender in any way.
                            )
                            Spacer(modifier = Modifier.width(2.dp))
                            Text(stringResource(R.string.read_label), style = MaterialTheme.typography.labelSmall, color = Color(0xFF34B7F1))
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .background(MaterialTheme.colorScheme.error, shape = androidx.compose.foundation.shape.CircleShape)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                stringResource(R.string.unread_label),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
            // Reply is offered for every message — seeing any message should let you act on it.
            Row(modifier = Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onGenerateReply) {
                    Icon(Icons.Filled.Reply, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(R.string.generate_reply))
                }
                OutlinedButton(onClick = onSaveAsImportantPerson) {
                    Icon(Icons.Filled.PersonAdd, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(R.string.save_as_important_person))
                }
            }
        }
    }
}

/**
 * Opened from a message's "Save as Important Person" button — lets the user register (or
 * update) that sender as an Important Person right from the message itself, instead of having
 * to separately navigate to Settings → Important People and type the name in again. If this
 * name is already saved, shows its current state instead of creating a duplicate entry.
 */
@Composable
private fun QuickAddImportantPersonDialog(app: WwmApplication, prefillName: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val existingContacts by app.importantContactRepository.observeContacts().collectAsStateWithLifecycle(initialValue = emptyList())
    val existing = existingContacts.firstOrNull { it.name.equals(prefillName, ignoreCase = true) }
    val scope = rememberCoroutineScope()

    var name by remember(prefillName) { mutableStateOf(prefillName) }
    val lastUsedCode = remember { com.whatsappworkmanager.app.utils.CountryCodePrefs.getLastUsed(app) }
    val fallbackCountry = remember {
        com.whatsappworkmanager.app.utils.CountryCodes.byDialCode(lastUsedCode) ?: com.whatsappworkmanager.app.utils.CountryCodes.ALL.first()
    }
    // When the WhatsApp "name" is itself a raw phone number (see extractPhoneNumberIfPresent),
    // that extraction returns the FULL digit string — country code and local number still
    // combined — so it must be split the same way a saved number would be, via
    // CountryCodes.split, before landing in these two separate fields. Previously the whole
    // combined string was dumped straight into localNumber with countryCode left at its
    // unrelated last-used default, silently saving a corrupted number (the real last-used
    // country code prefixed onto the *already-includes-its-own-country-code* full number) —
    // which is exactly why a contact added this way could never be found again correctly.
    val prefillSplit = remember(prefillName) {
        extractPhoneNumberIfPresent(prefillName)?.let { com.whatsappworkmanager.app.utils.CountryCodes.split(it, fallbackCountry) }
    }
    var countryCode by remember {
        mutableStateOf(
            existing?.phoneNumber?.let { com.whatsappworkmanager.app.utils.CountryCodes.split(it, fallbackCountry).first.dialCode }
                ?: prefillSplit?.first?.dialCode
                ?: lastUsedCode
        )
    }
    var localNumber by remember {
        mutableStateOf(
            existing?.phoneNumber?.let { com.whatsappworkmanager.app.utils.CountryCodes.split(it, fallbackCountry).second }
                ?: prefillSplit?.second
                ?: ""
        )
    }
    // Same auto-fill-by-name as ImportantPeopleScreen's Add/Edit dialog — see that dialog's
    // doc for the full reasoning. Only relevant here when prefillName wasn't itself already a
    // raw phone number (prefillSplit == null): if it was, localNumber is already filled from
    // that, and there's nothing left to look up.
    var hasTriedContactsLookup by remember(prefillName) { mutableStateOf(false) }
    val contactsPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted && localNumber.isBlank()) {
            val found = com.whatsappworkmanager.app.utils.ContactsLookup.findPhoneNumberByName(context, name)
            if (found != null) {
                val (country, local) = com.whatsappworkmanager.app.utils.CountryCodes.parsePickedContactNumber(found, fallbackCountry)
                countryCode = country.dialCode
                localNumber = local
            }
        }
        hasTriedContactsLookup = true
    }
    LaunchedEffect(prefillName) {
        if (existing == null && prefillSplit == null && name.isNotBlank() && localNumber.isBlank() &&
            IntentHelper.isReadContactsGranted(context)
        ) {
            val found = com.whatsappworkmanager.app.utils.ContactsLookup.findPhoneNumberByName(context, name)
            if (found != null) {
                val (country, local) = com.whatsappworkmanager.app.utils.CountryCodes.parsePickedContactNumber(found, fallbackCountry)
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
                if (existing != null) {
                    Text(stringResource(R.string.already_saved_as_important), style = MaterialTheme.typography.labelMedium, color = OkGreen)
                }
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.important_person_name_label)) },
                    modifier = Modifier.fillMaxWidth()
                )
                if (existing == null && prefillSplit == null && name.isNotBlank() && localNumber.isBlank() &&
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
                com.whatsappworkmanager.app.presentation.components.CountryCodePhoneField(
                    countryCode = countryCode,
                    onCountryCodeChange = { countryCode = it },
                    localNumber = localNumber,
                    onLocalNumberChange = { localNumber = it },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedButton(
                    onClick = {
                        val phone = if (localNumber.isBlank()) null else countryCode + localNumber
                        IntentHelper.openAddToPhoneContacts(context, name.trim(), phone)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = name.isNotBlank()
                ) {
                    Icon(Icons.Filled.PersonAdd, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(R.string.add_to_phone_contacts))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (name.isNotBlank()) {
                    val phone = if (localNumber.isBlank()) null else countryCode + localNumber
                    scope.launch {
                        app.importantContactRepository.upsert(
                            com.whatsappworkmanager.app.domain.model.ImportantContact(
                                id = existing?.id ?: 0,
                                name = name.trim(),
                                enabled = true,
                                phoneNumber = phone
                            )
                        )
                    }
                    onDismiss()
                }
            }) { Text(stringResource(if (existing != null) R.string.action_update else R.string.action_add)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } }
    )
}

private fun filterOptions(): List<Pair<MessageFilter, Int>> = listOf(
    MessageFilter.ALL to R.string.filter_all,
    MessageFilter.IMPORTANT to R.string.filter_important,
    MessageFilter.NEED_REPLY to R.string.filter_need_reply,
    MessageFilter.UNREAD to R.string.filter_unread,
    MessageFilter.READ to R.string.filter_read,
    MessageFilter.TODAY to R.string.filter_today,
    MessageFilter.YESTERDAY to R.string.filter_yesterday,
    MessageFilter.THIS_WEEK to R.string.filter_this_week
)
