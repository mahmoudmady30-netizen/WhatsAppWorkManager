package com.whatsappworkmanager.app.presentation.dashboard

// NOTE: using a wildcard import for foundation.layout rather than individual named imports
// (Arrangement, Column, Row, fillMaxWidth, padding, weight, ...). Kotlin 2.0's K2 compiler
// resolves a *specific* named import of `weight` to an internal `RowColumnParentData` symbol
// instead of the public `RowScope`/`ColumnScope` extension function of the same name,
// producing a "Cannot access ... it is internal in file" compile error — this reproduced
// identically across several different Compose BOM versions, so it isn't a library-version
// issue, it's specific to that import style. The wildcard import resolves the ambiguity
// correctly (Kotlin picks the right overload by receiver type at each call site instead of
// pre-resolving the bare name at import time).
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.gestures.draggable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.MarkChatUnread
import androidx.compose.material.icons.filled.PriorityHigh
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.ui.layout.onSizeChanged
import kotlin.math.roundToInt
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.whatsappworkmanager.app.domain.model.displayName
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.whatsappworkmanager.app.R
import com.whatsappworkmanager.app.WwmApplication
import com.whatsappworkmanager.app.presentation.components.StatCard
import com.whatsappworkmanager.app.presentation.components.MessagingPlatformIcon
import com.whatsappworkmanager.app.presentation.theme.UrgentRed
import com.whatsappworkmanager.app.presentation.theme.WarnAmber
import com.whatsappworkmanager.app.utils.IntentHelper
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onOpenSummary: () -> Unit,
    onOpenWorkGroups: () -> Unit,
    onOpenSearchFiltered: (filter: String) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenKeywordRules: () -> Unit,
    onOpenImportantPeople: () -> Unit,
    onOpenReplyPhrases: () -> Unit,
    onOpenQuickChat: () -> Unit,
    onOpenAutoReply: () -> Unit,
    onOpenScheduledMessages: () -> Unit
) {
    val context = LocalContext.current
    val app = context.applicationContext as WwmApplication
    val viewModel: DashboardViewModel = viewModel(factory = DashboardViewModel.Factory(app))
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    // Small dropdown for the Important card's "+" — Important is driven by *two* different
    // settings (keywords and specific people), so tapping it offers a choice instead of
    // guessing which one the user meant.
    var showImportantQuickMenu by remember { mutableStateOf(false) }
    var showNeedReplyNames by remember { mutableStateOf(false) }

    // Permission/access state can change "externally" (the user grants Notification Access from
    // system Settings, then taps Back to return here) — that's not a state change *inside* this
    // composition, so nothing would normally trigger a recomposition to notice it. Re-checking
    // on every ON_RESUME (i.e. whenever this screen becomes visible again, including "coming
    // back from Settings") fixes the "I have to close and reopen the app" symptom; the Refresh
    // button below re-checks the same thing on demand too.
    var notificationAccessEnabled by remember { mutableStateOf(IntentHelper.isNotificationAccessEnabled(context)) }
    var batteryOptimizationExempted by remember { mutableStateOf(IntentHelper.isIgnoringBatteryOptimizations(context)) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                notificationAccessEnabled = IntentHelper.isNotificationAccessEnabled(context)
                batteryOptimizationExempted = IntentHelper.isIgnoringBatteryOptimizations(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Resolved here (a Composable context) rather than inside the coroutine launch below,
    // since stringResource() can only be called from composable code.
    val refreshedMessage = stringResource(R.string.dashboard_refreshed)

    LaunchedEffect(state.summaryGenerationResult) {
        state.summaryGenerationResult?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.dismissSummaryGenerationResult()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(greeting(), style = MaterialTheme.typography.titleMedium)
                        Text(
                            stringResource(R.string.dashboard_title),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    // Same flag-icon language switch as the Summary screen — now a proper
                    // dropdown to choose from rather than a bare-tap toggle (the same
                    // underlying switch as Settings → Language). Placed here too since it's
                    // one of the two most-visited screens.
                    com.whatsappworkmanager.app.presentation.components.LanguageFlagMenu(app, context)
                    IconButton(onClick = {
                        notificationAccessEnabled = IntentHelper.isNotificationAccessEnabled(context)
                        batteryOptimizationExempted = IntentHelper.isIgnoringBatteryOptimizations(context)
                        // On-demand catch-up: forces the notification listener to reconnect,
                        // which re-scans every currently active WhatsApp notification against
                        // what's already captured (see triggerCatchUpScan's doc) — a genuine
                        // resync, not just a UI re-read of existing data.
                        com.whatsappworkmanager.app.service.WhatsAppNotificationListenerService.triggerCatchUpScan(context)
                        viewModel.refresh()
                        coroutineScope.launch { snackbarHostState.showSnackbar(refreshedMessage) }
                    }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Refresh")
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.settings_title))
                    }
                }
            )
        }
    ) { padding ->
        // Redesigned as a fixed header/scrollable-free body/fixed footer layout, requested
        // explicitly: the ticker pinned at the very top, the nav row pinned at the very
        // bottom, and everything between them sized to fit without scrolling — tighter
        // padding and smaller text throughout (StatCard, the Summary card, the quick-actions
        // card) rather than any one trick, since there's no single Compose mechanism that
        // "shrinks arbitrary content to fit" the way a native layout constraint would.
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            if (state.recentActivity.isNotEmpty()) {
                // Deliberate breathing room below the top app bar — the notification rail is a
                // premium content surface, not an extension of the header.
                Spacer(modifier = Modifier.height(12.dp))
                Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                    NotificationTicker(
                        items = state.recentActivity,
                        onDismiss = viewModel::dismissDashboardNotification
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
            } else {
                Spacer(modifier = Modifier.height(8.dp))
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (!notificationAccessEnabled) {
                    NotificationAccessBanner(onEnable = { IntentHelper.openNotificationAccessSettings(context) })
                } else if (state.messagesToday > 0 && state.workGroupsCount == 0) {
                    // Messages are clearly arriving (the count above proves it), but nothing is
                    // enabled for Work Summary yet — without this, the Summary card's "no
                    // messages" text looked wrong/broken sitting right next to a non-zero count.
                    NoEnabledGroupsBanner(onOpenWorkGroups = onOpenWorkGroups)
                } else if (!batteryOptimizationExempted) {
                    // Lowest priority of the three — shown only once the more urgent setup steps
                    // are already handled. Specifically relevant for muted chats: Android is far
                    // more willing to defer background work for *silent* notifications (exactly
                    // what a muted chat produces) than ones that visibly alert the user, and a
                    // capture merely delayed by Doze looks identical to one that never happened.
                    BatteryOptimizationBanner(onOpen = { IntentHelper.openBatteryOptimizationSettings(context) })
                }

                if (state.nextScheduledMessage != null || state.lastAutoReply != null) {
                    UpcomingActionsCard(
                        nextScheduledMessage = state.nextScheduledMessage,
                        upcomingScheduledQueueSize = state.upcomingScheduledQueueSize,
                        onDismissScheduled = viewModel::dismissDashboardScheduled,
                        lastAutoReply = state.lastAutoReply,
                        whatsappVariant = state.whatsappVariant,
                        onSendScheduledNow = { viewModel.markScheduledMessageSent(it) },
                        onAutoReplySent = { viewModel.clearLastAutoReply() }
                    )
                }

                // Summary leads the screen, requested explicitly — it's the one thing here that
                // actually summarizes everything else, so it earns the first slot rather than
                // sitting below four stat cards.
                Card(
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(stringResource(R.string.last_summary), style = MaterialTheme.typography.titleSmall)
                        Text(
                            state.lastSummary?.text?.lineSequence()?.firstOrNull()
                                    ?: stringResource(R.string.no_summary_yet),
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                // Both buttons fill an equal share of the row on purpose — same
                                // footprint, same corner radius (Material's default Button shape),
                                // so the pair reads as one deliberate two-choice control rather
                                // than two unrelated buttons that happen to be next to each other.
                                // The *color* is what actually distinguishes them: solid/primary
                                // for the everyday action (view what's already there), tonal/
                                // secondary for the heavier, less-frequent one (generate fresh) —
                                // still unmistakably a real button, just clearly the second choice.
                                Button(onClick = onOpenSummary, modifier = Modifier.weight(1f)) {
                                    Text(stringResource(R.string.view_summary), maxLines = 1)
                                }
                                androidx.compose.material3.FilledTonalButton(
                                    onClick = { viewModel.generateSummaryNow() },
                                    enabled = !state.isGeneratingSummary,
                                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 10.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    if (state.isGeneratingSummary) {
                                        CircularProgressIndicator(modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                    } else {
                                        Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                    }
                                    Text(stringResource(R.string.generate_summary_now), maxLines = 1, style = MaterialTheme.typography.labelLarge)
                                }
                            }
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        StatCard(
                            title = stringResource(R.string.stat_messages_today),
                            value = state.messagesToday.toString(),
                            icon = Icons.Filled.Chat,
                            modifier = Modifier.weight(1f),
                            onClick = { onOpenSearchFiltered("TODAY") }
                        )
                        StatCard(
                            title = stringResource(R.string.stat_work_groups),
                            value = state.workGroupsCount.toString(),
                            icon = Icons.Filled.Groups,
                            modifier = Modifier.weight(1f),
                            onClick = onOpenWorkGroups
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        Box(modifier = Modifier.weight(1f)) {
                            StatCard(
                                title = stringResource(R.string.stat_important),
                                value = state.importantCount.toString(),
                                icon = Icons.Filled.PriorityHigh,
                                accentColor = UrgentRed,
                                onClick = { onOpenSearchFiltered("IMPORTANT") },
                                onAddClick = { showImportantQuickMenu = true }
                            )
                            DropdownMenu(
                                expanded = showImportantQuickMenu,
                                onDismissRequest = { showImportantQuickMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.dashboard_add_important_keyword)) },
                                    onClick = { showImportantQuickMenu = false; onOpenKeywordRules() }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.dashboard_add_important_person)) },
                                    onClick = { showImportantQuickMenu = false; onOpenImportantPeople() }
                                )
                            }
                        }
                        StatCard(
                            title = stringResource(R.string.stat_need_reply),
                            value = state.needReplyCount.toString(),
                            icon = Icons.Filled.MarkChatUnread,
                            accentColor = WarnAmber,
                            modifier = Modifier.weight(1f),
                            onClick = { onOpenSearchFiltered("NEED_REPLY") },
                            onAddClick = onOpenReplyPhrases,
                            onIconClick = if (state.needReplyNames.isNotEmpty()) {
                                { showNeedReplyNames = true }
                            } else null,
                            iconClickContent = {
                                // A dropdown, tucked behind the icon itself, rather than a
                                // separate line of names sitting on its own below the cards —
                                // requested explicitly: only shown on demand, tapping the icon,
                                // never taking up space by default.
                                androidx.compose.material3.DropdownMenu(
                                    expanded = showNeedReplyNames,
                                    onDismissRequest = { showNeedReplyNames = false }
                                ) {
                                    Text(
                                        stringResource(R.string.need_reply_names_menu_title),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                                    )
                                    state.needReplyNames.forEach { name ->
                                        DropdownMenuItem(
                                            text = { Text(name) },
                                            onClick = {
                                                showNeedReplyNames = false
                                                onOpenSearchFiltered("NEED_REPLY")
                                            }
                                        )
                                    }
                                }
                            }
                        )
                    }

            }

            // All three fixed-weight on purpose — with three items exactly filling the row,
            // an equal share each reads as one coherent, deliberately designed control strip
            // rather than a scrollable list that hints at more content hiding off-screen.
            // Tight content padding and a smaller label size (rather than the default Button
            // text) are both deliberate — default padding left only "Auto"/"Sche" visible
            // with the rest of each label silently clipped off, on a perfectly ordinary phone
            // width. Pinned as a fixed footer, outside the scrollable-free middle section
            // above — always exactly at the bottom regardless of that section's content.
            val navButtonPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 4.dp, vertical = 10.dp)
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                OutlinedButton(
                    onClick = onOpenAutoReply,
                    contentPadding = navButtonPadding,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Filled.AutoAwesome, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(stringResource(R.string.ai_reply_nav_label), maxLines = 1, style = MaterialTheme.typography.labelMedium)
                }
                OutlinedButton(
                    onClick = onOpenQuickChat,
                    contentPadding = navButtonPadding,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Filled.Chat, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(stringResource(R.string.quick_chat_title), maxLines = 1, style = MaterialTheme.typography.labelMedium)
                }
                androidx.compose.material3.BadgedBox(
                    badge = {
                        if (state.missedScheduledCount > 0) {
                            androidx.compose.material3.Badge { Text(state.missedScheduledCount.toString()) }
                        }
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    OutlinedButton(
                        onClick = onOpenScheduledMessages,
                        contentPadding = navButtonPadding,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Filled.Schedule, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(stringResource(R.string.schedule_nav_label), maxLines = 1, style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }
}

/**
 * Sits between the top banners and the Summary card. The scheduled-message part is
 * deliberately a compact single line (not a full card) — requested explicitly, and only shown
 * at all once that message's time is actually coming up soon (see this screen's own call
 * site for the window), with a small "+N" badge when more than one currently qualifies rather
 * than trying to list them all. The AI Auto Reply part is unrelated to that scheduling window
 * and keeps its own full card, shown whenever a ready reply exists regardless of timing.
 */
@Composable
private fun UpcomingActionsCard(
    nextScheduledMessage: com.whatsappworkmanager.app.domain.model.ScheduledOutgoingMessage?,
    upcomingScheduledQueueSize: Int,
    onDismissScheduled: (com.whatsappworkmanager.app.domain.model.ScheduledOutgoingMessage) -> Unit,
    lastAutoReply: com.whatsappworkmanager.app.data.prefs.SettingsDataStore.LastAutoReply?,
    whatsappVariant: String,
    onSendScheduledNow: (com.whatsappworkmanager.app.domain.model.ScheduledOutgoingMessage) -> Unit,
    onAutoReplySent: () -> Unit
) {
    val context = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (nextScheduledMessage != null) {
            SwipeToDismissLeft(
                key = "scheduled-${nextScheduledMessage.id}",
                onDismiss = { onDismissScheduled(nextScheduledMessage) }
            ) {
                Card(
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
                ) {
                    Row(
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp)
                    ) {
                        Icon(Icons.Filled.Schedule, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                    val preview = nextScheduledMessage.recipientName?.takeIf { it.isNotBlank() }
                        ?.let { "${nextScheduledMessage.platform.displayName()} · $it — ${nextScheduledMessage.text}" }
                        ?: "${nextScheduledMessage.platform.displayName()} · ${nextScheduledMessage.text}"
                    Text(
                        stringResource(
                            R.string.dashboard_next_scheduled_compact_fmt,
                            com.whatsappworkmanager.app.presentation.components.formatTime12Hour(
                                nextScheduledMessage.timeMinutes / 60,
                                nextScheduledMessage.timeMinutes % 60
                            ),
                            preview
                        ),
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    if (upcomingScheduledQueueSize > 1) {
                        Text(
                            "+${upcomingScheduledQueueSize - 1}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 6.dp)
                        )
                    }
                    TextButton(
                        onClick = {
                            IntentHelper.openMessagingChat(context, nextScheduledMessage.platform, nextScheduledMessage.phoneNumber, nextScheduledMessage.text, whatsappVariant)
                            onSendScheduledNow(nextScheduledMessage)
                        },
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(stringResource(R.string.dashboard_send_now), style = MaterialTheme.typography.labelMedium, maxLines = 1)
                    }
                }
            }
        }
        }
        if (lastAutoReply != null) {
            Card(
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Filled.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            stringResource(R.string.dashboard_ready_reply_for_fmt, lastAutoReply.personName),
                            style = MaterialTheme.typography.labelLarge,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Button(
                            onClick = {
                                IntentHelper.openWhatsAppChat(context, lastAutoReply.phoneNumber, lastAutoReply.text, whatsappVariant)
                                onAutoReplySent()
                            },
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(stringResource(R.string.action_send), style = MaterialTheme.typography.labelMedium, maxLines = 1)
                        }
                    }
                    Text(
                        lastAutoReply.text,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 2,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

/**
 * A small ticker strip above the Summary card, cycling automatically through the most
 * recently captured messages — a flip/roll-style transition (vertical slide + fade, the same
 * visual idea as a flip clock or scoreboard) between "New in <category>: <person>" entries,
 * one at a time, every few seconds. Deliberately not interactive (no tap target, no manual
 * advance) — it's a glance-at ambient summary, not a substitute for actually opening Search
 * to review anything in it.
 */
@Composable
private fun NotificationTicker(
    items: List<RecentActivityItem>,
    onDismiss: (RecentActivityItem) -> Unit
) {
    var currentIndex by remember { mutableStateOf(0) }
    LaunchedEffect(items) {
        currentIndex = 0
        if (items.size <= 1) return@LaunchedEffect
        while (true) {
            kotlinx.coroutines.delay(3200L)
            currentIndex = (currentIndex + 1) % items.size
        }
    }
    val current = items.getOrNull(currentIndex) ?: return
    SwipeToDismissLeft(
        key = "notification-${current.category.name}-${current.personLabel}-${current.timestamp}",
        onDismiss = { onDismiss(current) }
    ) {
        Card(
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp).fillMaxWidth(),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
        ) {
            val (icon, tint) = when (current.category) {
                RecentActivityCategory.IMPORTANT -> Icons.Filled.PriorityHigh to UrgentRed
                RecentActivityCategory.NEED_REPLY -> Icons.Filled.MarkChatUnread to WarnAmber
                RecentActivityCategory.NORMAL -> Icons.Filled.Chat to MaterialTheme.colorScheme.primary
            }
                Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(16.dp))
                MessagingPlatformIcon(current.platform, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(7.dp))
                androidx.compose.animation.AnimatedContent(
                targetState = current,
                transitionSpec = {
                    (androidx.compose.animation.slideInVertically { height -> height } + androidx.compose.animation.fadeIn())
                        .togetherWith(androidx.compose.animation.slideOutVertically { height -> -height } + androidx.compose.animation.fadeOut())
                },
                    modifier = Modifier.weight(1f),
                    label = "notification_ticker"
                ) { item ->
                    Text(
                    stringResource(
                        when (item.category) {
                            RecentActivityCategory.IMPORTANT -> R.string.dashboard_ticker_important_fmt
                            RecentActivityCategory.NEED_REPLY -> R.string.dashboard_ticker_need_reply_fmt
                            RecentActivityCategory.NORMAL -> R.string.dashboard_ticker_normal_fmt
                        },
                        item.platform.displayName() + " · " + item.personLabel
                    ) + if (item.messageCount > 1) " " + stringResource(R.string.dashboard_ticker_count_suffix_fmt, item.messageCount) else "",
                    style = MaterialTheme.typography.labelLarge,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                }
                if (items.size > 1) {
                    Text(
                    "${currentIndex + 1}/${items.size}",
                    style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/**
 * Meta-style lightweight dismiss interaction for dashboard notification surfaces.
 * Only a leftward swipe dismisses the item; a partial drag snaps back. The dismissed key is
 * persisted by the ViewModel, so the same notification stays away until a genuinely newer
 * message / scheduled occurrence creates a new key.
 */
@Composable
private fun SwipeToDismissLeft(
    key: String,
    onDismiss: () -> Unit,
    content: @Composable () -> Unit
) {
    // Reveal a real delete action instead of trying to fling the card completely off-screen.
    // This is deliberately deterministic: drag state is updated synchronously and only the
    // settle animation uses a coroutine, avoiding the "stuck half way" behaviour caused by
    // launching a coroutine for every single pointer-move event.
    val scope = rememberCoroutineScope()
    val maxRevealPx = with(androidx.compose.ui.platform.LocalDensity.current) { 72.dp.toPx() }
    var offsetX by remember(key) { mutableStateOf(0f) }
    var widthPx by remember(key) { mutableStateOf(1f) }
    var isDeleted by remember(key) { mutableStateOf(false) }
    val settle = remember(key) { Animatable(0f) }

    fun settleTo(target: Float) {
        scope.launch {
            settle.snapTo(offsetX)
            settle.animateTo(target.coerceAtLeast(-maxRevealPx), tween(180))
            offsetX = settle.value
        }
    }

    if (isDeleted) return

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .onSizeChanged { widthPx = it.width.toFloat().coerceAtLeast(1f) }
    ) {
        // The action is physically underneath the card. It becomes tappable as soon as the
        // user reveals it, which also gives a reliable manual fallback when a short swipe is
        // easier than a full dismiss gesture.
        Row(
            modifier = Modifier
                .matchParentSize()
                .padding(end = 0.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
        ) {
            androidx.compose.material3.FilledTonalButton(
                onClick = {
                    scope.launch {
                        settle.snapTo(offsetX)
                        settle.animateTo(-widthPx, tween(180))
                        offsetX = settle.value
                        // Hide the entire surface immediately after the delete animation.
                        // This prevents the Delete button underneath from remaining visible
                        // while the parent list is processing the persisted dismissal.
                        isDeleted = true
                        onDismiss()
                    }
                },
                modifier = Modifier
                    .width(72.dp)
                    .fillMaxHeight()
                    .padding(vertical = 2.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 6.dp, vertical = 4.dp),
                colors = androidx.compose.material3.ButtonDefaults.filledTonalButtonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                ),
                shape = MaterialTheme.shapes.large
            ) {
                Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.dashboard_delete_notification), modifier = Modifier.size(19.dp))
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .offset { androidx.compose.ui.unit.IntOffset(offsetX.roundToInt(), 0) }
                .draggable(
                    orientation = androidx.compose.foundation.gestures.Orientation.Horizontal,
                    state = androidx.compose.foundation.gestures.rememberDraggableState { delta ->
                        // Left only. The card never follows a rightward swipe and never travels
                        // farther than the visible Delete action.
                        offsetX = (offsetX + delta).coerceIn(-maxRevealPx, 0f)
                    },
                    onDragStopped = {
                        val threshold = -maxRevealPx * 0.42f
                        settleTo(if (offsetX <= threshold) -maxRevealPx else 0f)
                    }
                )
        ) {
            content()
        }
    }
}

@Composable
private fun greeting(): String {
    val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
    return when {
        hour < 12 -> stringResource(R.string.greeting_morning)
        hour < 17 -> stringResource(R.string.greeting_afternoon)
        else -> stringResource(R.string.greeting_evening)
    }
}

@Composable
private fun NotificationAccessBanner(onEnable: () -> Unit) {
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                stringResource(R.string.error_notification_access_disabled),
                style = MaterialTheme.typography.titleMedium
            )
            Button(onClick = onEnable) { Text(stringResource(R.string.action_enable_access)) }
        }
    }
}

@Composable
private fun NoEnabledGroupsBanner(onOpenWorkGroups: () -> Unit) {
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = WarnAmber.copy(alpha = 0.18f))
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                stringResource(R.string.no_enabled_groups_banner_title),
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                stringResource(R.string.no_enabled_groups_banner_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.secondary
            )
            Button(onClick = onOpenWorkGroups) { Text(stringResource(R.string.work_groups_title)) }
        }
    }
}

@Composable
private fun BatteryOptimizationBanner(onOpen: () -> Unit) {
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = WarnAmber.copy(alpha = 0.18f))
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                stringResource(R.string.battery_banner_title),
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                stringResource(R.string.battery_banner_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.secondary
            )
            Button(onClick = onOpen) { Text(stringResource(R.string.settings_battery_optimization)) }
        }
    }
}
