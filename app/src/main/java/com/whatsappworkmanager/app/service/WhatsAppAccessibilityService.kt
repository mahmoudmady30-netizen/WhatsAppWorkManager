package com.whatsappworkmanager.app.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.app.KeyguardManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.util.Log
import com.whatsappworkmanager.app.utils.Constants
import com.whatsappworkmanager.app.domain.model.MessagingPlatform
import com.whatsappworkmanager.app.utils.IntentHelper
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * User-enabled messaging UI automation bridge. It never reads WhatsApp's private database.
 * It only operates on the visible WhatsApp UI and only sends a queued reply created by this app.
 */
class WhatsAppAccessibilityService : AccessibilityService() {
    private val handler = Handler(Looper.getMainLooper())
    private var busy = false
    private var searchStartedAt = 0L
    private var scheduledLaunchAttemptAt = 0L
    private var scheduledAutomationJobId = -1L
    private var scheduledAutomationAttempts = 0
    private var scheduledDirectChatAttempted = false
    private val poll = object : Runnable {
        override fun run() {
            trySendPending()
            handler.postDelayed(this, 1000L)
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        serviceInfo = serviceInfo.apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED or
                AccessibilityEvent.TYPE_VIEW_CLICKED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
            notificationTimeout = 100L
        }
        handler.post(poll)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Sending is driven by the persistent queue; events simply give us more opportunities
        // to act immediately when WhatsApp's UI changes.
        trySendPending()
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    private fun trySendPending() {
        if (busy) return

        // Scheduled messages get their own durable queue. This lane is deliberately checked
        // before AI replies so a scheduled send cannot sit behind an unrelated reply.
        ScheduledSendQueue.peek(this)?.let { scheduled ->
            trySendScheduled(scheduled)
            return
        }

        val job = AutoReplyQueue.peek(this) ?: return
        if (System.currentTimeMillis() < job.notBefore) return

        val root = rootInActiveWindow ?: return
        if (!isWhatsAppWindow(root.packageName?.toString())) return

        busy = true
        try {
            // First make sure the correct chat is open. We verify the visible title before
            // pressing Send so a delayed UI transition can never post into the wrong chat.
            if (!isChatOpen(root, job.groupName)) {
                navigateToChat(root, job.phoneNumber ?: job.groupName)
                busy = false
                return
            }
            val input = findEditable(root)
            if (input == null) {
                busy = false
                return
            }
            val args = Bundle().apply { putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, job.replyText) }
            val setOk = input.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
            if (!setOk) { busy = false; return }
            handler.postDelayed({
                val latestRoot = rootInActiveWindow
                val safe = latestRoot != null && isChatOpen(latestRoot, job.groupName)
                val send = if (safe) findSendButton(latestRoot!!) else null
                val sent = send != null && clickNodeOrParent(send)
                if (sent) AutoReplyQueue.clear(this)
                busy = false
            }, 350L)
        } catch (t: Throwable) {
            Log.w(TAG, "Auto reply UI action failed", t)
            busy = false
        }
    }


    private fun trySendScheduled(job: ScheduledSendQueue.Job) {
        // Reset the automation state whenever the queue advances to a new scheduled message.
        if (scheduledAutomationJobId != job.id) {
            scheduledAutomationJobId = job.id
            scheduledAutomationAttempts = 0
            scheduledDirectChatAttempted = false
            scheduledLaunchAttemptAt = 0L
        }

        val now = System.currentTimeMillis()
        val targetPackage = resolveScheduledPackage(job)
        val root = rootInActiveWindow

        // Never launch or interact with the wrong WhatsApp variant. The package was resolved
        // from Settings at alarm time, and we verify it again on every UI pass.
        if (root == null || root.packageName?.toString() != targetPackage) {
            val keyguard = getSystemService(KeyguardManager::class.java)
            if (keyguard?.isKeyguardLocked == true) return
            if (now - scheduledLaunchAttemptAt >= 2_500L) {
                scheduledLaunchAttemptAt = now
                launchScheduledMessagingApp(targetPackage)
            }
            return
        }

        if (busy) return
        busy = true
        try {
            val phone = job.phoneNumber?.filter(Char::isDigit)?.takeIf { it.length >= 7 }
            val target = if (job.platform == MessagingPlatform.MESSENGER.key) {
                job.recipientName?.split(',', ';', '\n')?.firstOrNull { it.isNotBlank() }?.trim()
            } else {
                phone ?: job.recipientName?.takeIf { it.isNotBlank() }
            }
            if (target.isNullOrBlank()) { busy = false; return }

            // Messenger does not have WhatsApp's phone-number click-to-chat URL. Its scheduled
            // delivery lane therefore has to complete the whole visible UI flow: open Messenger,
            // search the configured user, open the matching conversation, fill the composer and
            // click Send.
            if (job.platform == MessagingPlatform.MESSENGER.key) {
                val chatAlreadyOpen = isTargetChatOpen(root, job)
                if (chatAlreadyOpen) {
                    sendScheduledText(job, targetPackage)
                    return
                }
                scheduledAutomationAttempts++
                searchExistingChat(root, target, targetPackage)
                return
            }

            // If we already reached the exact WhatsApp chat through a previous step, send immediately.
            val chatAlreadyOpen = isTargetChatOpen(root, job)
            if (chatAlreadyOpen) {
                sendScheduledText(job, targetPackage)
                return
            }

            scheduledAutomationAttempts++

            // Primary UI automation: use WhatsApp's own New Chat/Search flow.
            if (phone != null) {
                startNewChatByPhone(root, phone, targetPackage)
            } else {
                searchExistingChat(root, target, targetPackage)
            }

            // Some WhatsApp releases do not expose the New Chat result as an accessible node.
            // After two UI attempts, use the official click-to-chat deep link as a recovery path.
            if (phone != null && scheduledAutomationAttempts >= 2 && !scheduledDirectChatAttempted) {
                scheduledDirectChatAttempted = true
                openExactScheduledChat(job, targetPackage)
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Scheduled UI automation failed", t)
        } finally {
            busy = false
        }
    }

    private fun openExactScheduledChat(job: ScheduledSendQueue.Job, targetPackage: String) {
        val preferred = if (targetPackage == Constants.WHATSAPP_BUSINESS_PACKAGE) {
            IntentHelper.WHATSAPP_VARIANT_BUSINESS
        } else {
            IntentHelper.WHATSAPP_VARIANT_REGULAR
        }
        val intent = IntentHelper.buildWhatsAppChatIntent(
            this,
            job.phoneNumber,
            job.text,
            preferred
        ) ?: return
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        runCatching { startActivity(intent) }
            .onFailure { Log.w(TAG, "Exact WhatsApp chat deep link failed", it) }
    }

    private fun isTargetChatOpen(root: AccessibilityNodeInfo, job: ScheduledSendQueue.Job): Boolean {
        val name = job.recipientName
            ?.split(',', ';', '\n')
            ?.firstOrNull { it.isNotBlank() }
            ?.trim()
            ?.takeIf { it.isNotBlank() }
        val phone = job.phoneNumber?.filter(Char::isDigit)?.takeIf { it.length >= 7 }

        if (job.platform == MessagingPlatform.MESSENGER.key) {
            // Do not treat the Messenger inbox/search result as an open conversation. We only
            // accept a match when the composer and Send control are both visible, then prefer a
            // toolbar/header match for the configured user. This prevents sending into a random
            // conversation simply because the user's name appears in the inbox.
            val composer = findMessengerComposer(root) ?: return false
            val send = findSendButton(root) ?: return false
            if (!composer.isVisibleToUser || !send.isVisibleToUser) return false
            return name != null && isMessengerChatHeaderMatch(root, name)
        }

        // Prefer the configured display name when available. For phone-only schedules, the
        // toolbar/contact area may show the resolved contact name instead of the number.
        if (name != null && isChatOpen(root, name)) return true
        if (phone != null && isChatOpenByPhone(root, phone)) return true

        // After the exact click-to-chat recovery, WhatsApp can render the contact name without
        // exposing the phone number. A message composer + Send button is the final signal.
        return scheduledDirectChatAttempted &&
            findEditable(root) != null &&
            findSendButton(root) != null
    }

    private fun sendScheduledText(job: ScheduledSendQueue.Job, targetPackage: String) {
        val root = rootInActiveWindow ?: run { busy = false; return }
        if (root.packageName?.toString() != targetPackage) { busy = false; return }
        val input = if (job.platform == MessagingPlatform.MESSENGER.key) {
            findMessengerComposer(root)
        } else {
            findEditable(root)
        } ?: run { busy = false; return }
        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, job.text)
        }
        if (!input.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)) { busy = false; return }
        handler.postDelayed({
            val latestRoot = rootInActiveWindow
            val safe = latestRoot != null && latestRoot.packageName?.toString() == targetPackage &&
                isTargetChatOpen(latestRoot, job)
            val send = if (safe) findSendButton(latestRoot!!) else null
            val sent = send != null && clickNodeOrParent(send)
            if (sent) completeScheduledSend(job.id)
            busy = false
        }, 500L)
    }

    private fun startNewChatByPhone(root: AccessibilityNodeInfo, phone: String, targetPackage: String): Boolean {
        val newChat = findNewChatButton(root)
        if (newChat != null && clickNodeOrParent(newChat)) {
            handler.postDelayed({
                val r = rootInActiveWindow ?: run { busy = false; return@postDelayed }
                if (r.packageName?.toString() != targetPackage) { busy = false; return@postDelayed }
                val search = findSearchInput(r) ?: findEditable(r)
                if (search == null) { busy = false; return@postDelayed }
                val args = Bundle().apply {
                    putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, phone)
                }
                if (!search.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)) { busy = false; return@postDelayed }
                handler.postDelayed({
                    val rr = rootInActiveWindow ?: run { busy = false; return@postDelayed }
                    if (rr.packageName?.toString() != targetPackage) { busy = false; return@postDelayed }
                    val result = findPhoneResult(rr, phone) ?: findNodeExactOrContaining(rr, phone)
                    if (result != null && clickNodeOrParent(result)) {
                        handler.postDelayed({
                            ScheduledSendQueue.peek(this)?.let { sendScheduledText(it, targetPackage) } ?: run { busy = false }
                        }, 700L)
                    } else busy = false
                }, 700L)
            }, 350L)
            return true
        } else {
            val search = findSearchInput(root) ?: findEditable(root)
            if (search == null) { busy = false; return false }
            val args = Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, phone)
            }
            if (!search.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)) { busy = false; return false }
            handler.postDelayed({
                val rr = rootInActiveWindow ?: run { busy = false; return@postDelayed }
                val result = findPhoneResult(rr, phone) ?: findNodeExactOrContaining(rr, phone)
                if (result != null && clickNodeOrParent(result)) {
                    handler.postDelayed({
                        ScheduledSendQueue.peek(this)?.let { sendScheduledText(it, targetPackage) } ?: run { busy = false }
                    }, 700L)
                } else busy = false
            }, 700L)
            return true
        }
        return false
    }

    private fun searchExistingChat(root: AccessibilityNodeInfo, target: String, targetPackage: String): Boolean {
        val messenger = targetPackage == Constants.MESSENGER_PACKAGE
        val search = findNodeContaining(root, searchTermsFor(targetPackage))
            ?: if (messenger) findMessengerSearchButton(root) else null
        val openedSearch = search?.let { clickNodeOrParent(it) } == true
        val openedNewMessage = if (!openedSearch && messenger) {
            findNewChatButton(root)?.let { clickNodeOrParent(it) } == true
        } else false
        if (!openedSearch && !openedNewMessage) {
            // Messenger can expose the search affordance only after the app's first screen has
            // settled. Keep the queue pending so the next accessibility poll can try again; do
            // not treat opening Messenger itself as a successful send.
            busy = false
            return false
        }
        handler.postDelayed({
            val r = rootInActiveWindow ?: run { busy = false; return@postDelayed }
            if (r.packageName?.toString() != targetPackage) { busy = false; return@postDelayed }
            val input = if (messenger) findMessengerSearchInput(r) else findSearchInput(r) ?: findEditable(r)
            if (input == null) { busy = false; return@postDelayed }
            val query = target.trim().removePrefix("@").trim()
            val args = Bundle().apply { putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, query) }
            if (!input.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)) { busy = false; return@postDelayed }
            handler.postDelayed({
                val rr = rootInActiveWindow ?: run { busy = false; return@postDelayed }
                if (rr.packageName?.toString() != targetPackage) { busy = false; return@postDelayed }
                val result = if (messenger) findMessengerSearchResult(rr, query) else findNodeExactOrContaining(rr, target)
                if (result != null && clickNodeOrParent(result)) {
                    handler.postDelayed({
                        ScheduledSendQueue.peek(this)?.let { current ->
                            if (current.platform == MessagingPlatform.MESSENGER.key) {
                                if (isTargetChatOpen(rootInActiveWindow ?: return@let, current)) {
                                    sendScheduledText(current, targetPackage)
                                } else {
                                    busy = false
                                }
                            } else {
                                sendScheduledText(current, targetPackage)
                            }
                        } ?: run { busy = false }
                    }, if (messenger) 1100L else 700L)
                } else busy = false
            }, if (messenger) 1100L else 650L)
        }, if (messenger) 450L else 300L)
        return true
    }

    private fun findMessengerSearchButton(root: AccessibilityNodeInfo): AccessibilityNodeInfo? =
        findNodeContaining(root, listOf(
            "search", "search people", "search messenger", "search chats", "search conversations",
            "بحث", "بحث عن أشخاص", "بحث في المحادثات",
            "com.facebook.orca:id/search", "com.facebook.orca:id/search_action",
            "com.facebook.orca:id/search_button", "com.facebook.orca:id/menu_search",
            "com.facebook.orca:id/search_edit_text", "com.facebook.orca:id/search_input",
            "com.facebook.orca:id/search_box"
        ))

    private fun findMessengerSearchInput(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val list = ArrayDeque<AccessibilityNodeInfo>()
        list.add(root)
        while (list.isNotEmpty()) {
            val n = list.removeFirst()
            val id = n.viewIdResourceName.orEmpty().lowercase()
            val hint = listOfNotNull(n.hintText?.toString(), n.text?.toString(), n.contentDescription?.toString())
                .joinToString(" ").lowercase()
            if (n.isEditable && n.isEnabled && n.isVisibleToUser &&
                (id.contains("search") || id.contains("query") || id.contains("search_edit") ||
                    id.contains("search_input") || id.contains("search_box") || hint.contains("search") ||
                    hint.contains("people") || hint.contains("messenger") || hint.contains("find") ||
                    hint.contains("بحث"))) return n
            for (i in 0 until n.childCount) n.getChild(i)?.let(list::addLast)
        }
        return null
    }

    private fun findMessengerSearchResult(root: AccessibilityNodeInfo, target: String): AccessibilityNodeInfo? {
        val wanted = target.trim().removePrefix("@").lowercase()
        val list = ArrayDeque<AccessibilityNodeInfo>()
        list.add(root)
        var candidate: AccessibilityNodeInfo? = null
        while (list.isNotEmpty()) {
            val n = list.removeFirst()
            if (n.isEnabled && n.isVisibleToUser && !n.isEditable) {
                val text = listOfNotNull(n.text?.toString(), n.contentDescription?.toString())
                    .joinToString(" ").trim().lowercase()
                if (text == wanted || text == "@$wanted") return n
                if (candidate == null && text.contains(wanted) && text.length <= wanted.length + 80) candidate = n
            }
            for (i in 0 until n.childCount) n.getChild(i)?.let(list::addLast)
        }
        return candidate
    }

    private fun findMessengerComposer(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val list = ArrayDeque<AccessibilityNodeInfo>()
        list.add(root)
        var fallback: AccessibilityNodeInfo? = null
        while (list.isNotEmpty()) {
            val n = list.removeFirst()
            if (n.isEditable && n.isEnabled && n.isVisibleToUser) {
                val id = n.viewIdResourceName.orEmpty().lowercase()
                val hint = listOfNotNull(n.hintText?.toString(), n.contentDescription?.toString(), n.text?.toString())
                    .joinToString(" ").lowercase()
                if (id.contains("composer") || id.contains("message") || id.contains("text_input") ||
                    hint.contains("message") || hint.contains("type a message") || hint.contains("اكتب رسالة")) return n
                if (fallback == null) fallback = n
            }
            for (i in 0 until n.childCount) n.getChild(i)?.let(list::addLast)
        }
        return fallback
    }

    private fun isMessengerChatHeaderMatch(root: AccessibilityNodeInfo, target: String): Boolean {
        val wanted = target.trim().removePrefix("@").lowercase()
        val list = ArrayDeque<AccessibilityNodeInfo>()
        list.add(root)
        while (list.isNotEmpty()) {
            val n = list.removeFirst()
            val cls = n.className?.toString().orEmpty().lowercase()
            val id = n.viewIdResourceName.orEmpty().lowercase()
            if (cls.contains("toolbar") || cls.contains("actionbar") || id.contains("toolbar") || id.contains("header")) {
                val text = listOfNotNull(n.text?.toString(), n.contentDescription?.toString())
                    .joinToString(" ").lowercase()
                if (text.contains(wanted)) return true
            }
            for (i in 0 until n.childCount) n.getChild(i)?.let(list::addLast)
        }
        // Some Messenger builds expose the title as a sibling rather than inside a toolbar.
        // The composer+Send checks in isTargetChatOpen make this fallback safe enough to use.
        return isChatOpen(root, target)
    }

    private fun findNewChatButton(root: AccessibilityNodeInfo): AccessibilityNodeInfo? = findNodeContaining(
        root,
        listOf(
            "new chat", "new conversation", "start chat", "new message", "new contact",
            "محادثة جديدة", "دردشة جديدة", "رسالة جديدة", "بدء محادثة",
            "com.whatsapp:id/fab", "com.whatsapp.w4b:id/fab",
            "com.whatsapp:id/menuitem_new_chat", "com.whatsapp.w4b:id/menuitem_new_chat",
            "com.whatsapp:id/menuitem_new_chat_fab", "com.whatsapp.w4b:id/menuitem_new_chat_fab",
            "com.facebook.orca:id/new_message", "com.facebook.orca:id/new_chat",
            "com.facebook.orca:id/compose", "com.facebook.orca:id/fab",
            "com.facebook.orca:id/action_new_message", "com.facebook.orca:id/action_compose",
            "menuitem_new_chat", "new_chat", "fab", "new_message", "compose"
        )
    )

    private fun findSearchInput(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val list = ArrayDeque<AccessibilityNodeInfo>(); list.add(root)
        while (list.isNotEmpty()) {
            val n = list.removeFirst()
            val id = n.viewIdResourceName.orEmpty().lowercase()
            val hint = listOfNotNull(n.hintText?.toString(), n.text?.toString(), n.contentDescription?.toString()).joinToString(" ").lowercase()
            if (n.isEditable && n.isEnabled && n.isVisibleToUser &&
                (id.contains("search") || hint.contains("search") || hint.contains("بحث"))) return n
            for (i in 0 until n.childCount) n.getChild(i)?.let(list::addLast)
        }
        return null
    }

    private fun findPhoneResult(root: AccessibilityNodeInfo, phone: String): AccessibilityNodeInfo? {
        val normalized = phone.filter(Char::isDigit)
        val list = ArrayDeque<AccessibilityNodeInfo>(); list.add(root)
        var candidate: AccessibilityNodeInfo? = null
        while (list.isNotEmpty()) {
            val n = list.removeFirst()
            if (n.isEnabled && n.isVisibleToUser) {
                val raw = listOfNotNull(n.text?.toString(), n.contentDescription?.toString()).joinToString(" ")
                val digits = raw.filter(Char::isDigit)
                if (digits == normalized || digits.contains(normalized)) return n
                if (candidate == null && normalized.length >= 7 && digits.endsWith(normalized.takeLast(7))) candidate = n
            }
            for (i in 0 until n.childCount) n.getChild(i)?.let(list::addLast)
        }
        return candidate
    }

    private fun isChatOpenByPhone(root: AccessibilityNodeInfo, phone: String): Boolean {
        val normalized = phone.filter(Char::isDigit)
        val list = ArrayDeque<AccessibilityNodeInfo>(); list.add(root)
        while (list.isNotEmpty()) {
            val n = list.removeFirst()
            val digits = listOfNotNull(n.text?.toString(), n.contentDescription?.toString()).joinToString(" ").filter(Char::isDigit)
            if (digits.contains(normalized) || (normalized.length >= 7 && digits.contains(normalized.takeLast(7)))) return true
            for (i in 0 until n.childCount) n.getChild(i)?.let(list::addLast)
        }
        return false
    }

    private fun completeScheduledSend(id: Long) {
        WhatsAppNotificationListenerService.markScheduledSendComplete(this, id)
        val app = applicationContext as? com.whatsappworkmanager.app.WwmApplication
        app?.let { wwm ->
            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                runCatching {
                    wwm.scheduledMessageRepository.observeScheduledMessages().first()
                        .firstOrNull { it.id == id }
                        ?.let { message -> wwm.scheduledMessageRepository.upsert(message.copy(lastSentAt = System.currentTimeMillis())) }
                }
            }
        }
        com.whatsappworkmanager.app.utils.NotificationHelper.cancelScheduledMessageReminder(this, id)
    }

    private fun resolveScheduledPackage(job: ScheduledSendQueue.Job): String {
        val explicit = job.packageName?.takeIf {
            it == Constants.WHATSAPP_PACKAGE ||
                it == Constants.WHATSAPP_BUSINESS_PACKAGE ||
                it == Constants.MESSENGER_PACKAGE
        }
        if (job.platform == MessagingPlatform.MESSENGER.key) {
            return explicit?.takeIf { it == Constants.MESSENGER_PACKAGE }
                ?: Constants.MESSENGER_PACKAGE
        }
        return explicit
            ?: Constants.WHATSAPP_PACKAGE.takeIf { packageManager.getLaunchIntentForPackage(it) != null }
            ?: Constants.WHATSAPP_BUSINESS_PACKAGE.takeIf { packageManager.getLaunchIntentForPackage(it) != null }
            ?: Constants.MESSENGER_PACKAGE
    }

    private fun launchScheduledMessagingApp(packageName: String) {
        val launch = packageManager.getLaunchIntentForPackage(packageName) ?: return
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        runCatching { startActivity(launch) }
    }

    private fun launchWhatsApp() {
        val pkg = AutoReplyQueue.peek(this)?.packageName ?: Constants.WHATSAPP_PACKAGE
        packageManager.getLaunchIntentForPackage(pkg)?.let {
            it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(it)
        }
    }

    private fun isChatOpen(root: AccessibilityNodeInfo, name: String): Boolean {
        val wanted = name.trim().lowercase()
        val list = ArrayDeque<AccessibilityNodeInfo>()
        list.add(root)
        while (list.isNotEmpty()) {
            val n = list.removeFirst()
            val text = listOfNotNull(n.text?.toString(), n.contentDescription?.toString()).joinToString(" ").trim().lowercase()
            if (text == wanted || text.contains(wanted)) return true
            for (i in 0 until n.childCount) n.getChild(i)?.let(list::addLast)
        }
        return false
    }

    private fun navigateToChat(root: AccessibilityNodeInfo, target: String) {
        // WhatsApp's search icon is stable across recent releases and localized by content
        // description. After opening search, type the configured chat name and tap the matching
        // result. If OEM/WhatsApp UI changes, we simply leave the queue pending rather than
        // risking a send to the wrong conversation.
        val search = findNodeContaining(root, SEARCH_TERMS)
        if (search != null && search.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
            searchStartedAt = System.currentTimeMillis()
            handler.postDelayed({
                val r = rootInActiveWindow ?: return@postDelayed
                val input = findEditable(r) ?: return@postDelayed
                val args = Bundle().apply { putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, target) }
                if (input.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)) {
                    handler.postDelayed({
                        val rr = rootInActiveWindow ?: return@postDelayed
                        findNodeExactOrContaining(rr, target)?.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    }, 500L)
                }
            }, 250L)
        }
    }

    private fun searchTermsFor(packageName: String): List<String> = buildList {
        if (packageName == Constants.MESSENGER_PACKAGE) {
            add("search")
            add("search people")
            add("search chats")
            add("search conversations")
            add("search messenger")
            add("بحث")
            add("بحث عن أشخاص")
            add("بحث في المحادثات")
            add("com.facebook.orca:id/search")
            add("com.facebook.orca:id/search_action")
            add("com.facebook.orca:id/search_button")
            add("com.facebook.orca:id/menu_search")
        } else {
            addAll(SEARCH_TERMS)
            if (packageName == Constants.WHATSAPP_BUSINESS_PACKAGE) {
                add("com.whatsapp.w4b:id/search")
                add("com.whatsapp.w4b:id/menuitem_search")
                add("com.whatsapp.w4b:id/search_action")
            } else {
                add("com.whatsapp:id/search")
                add("com.whatsapp:id/menuitem_search")
                add("com.whatsapp:id/search_action")
            }
        }
    }

    private fun findNodeContaining(root: AccessibilityNodeInfo, terms: List<String>): AccessibilityNodeInfo? {
        val list = ArrayDeque<AccessibilityNodeInfo>(); list.add(root)
        while (list.isNotEmpty()) {
            val n = list.removeFirst()
            val hay = listOfNotNull(n.contentDescription?.toString(), n.text?.toString(), n.viewIdResourceName).joinToString(" ").lowercase()
            if (n.isEnabled && n.isVisibleToUser && terms.any { hay.contains(it) }) return n
            for (i in 0 until n.childCount) n.getChild(i)?.let(list::addLast)
        }
        return null
    }

    private fun findNodeExactOrContaining(root: AccessibilityNodeInfo, value: String): AccessibilityNodeInfo? {
        val wanted = value.trim().lowercase(); val list = ArrayDeque<AccessibilityNodeInfo>(); list.add(root)
        var candidate: AccessibilityNodeInfo? = null
        while (list.isNotEmpty()) {
            val n = list.removeFirst(); val text = listOfNotNull(n.text?.toString(), n.contentDescription?.toString()).joinToString(" ").trim().lowercase()
            if (n.isEnabled && n.isVisibleToUser) {
                if (text == wanted) return n
                if (candidate == null && text.contains(wanted)) candidate = n
            }
            for (i in 0 until n.childCount) n.getChild(i)?.let(list::addLast)
        }
        return candidate
    }

    private fun clickNodeOrParent(node: AccessibilityNodeInfo): Boolean {
        if (node.isEnabled && node.isVisibleToUser && node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true
        var parent = node.parent
        var depth = 0
        while (parent != null && depth++ < 4) {
            if (parent.isEnabled && parent.isVisibleToUser && parent.isClickable &&
                parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true
            parent = parent.parent
        }
        return false
    }

    private fun findEditable(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val list = ArrayDeque<AccessibilityNodeInfo>()
        list.add(root)
        while (list.isNotEmpty()) {
            val n = list.removeFirst()
            if (n.isEditable && n.isEnabled && n.isVisibleToUser) return n
            for (i in 0 until n.childCount) n.getChild(i)?.let(list::addLast)
        }
        return null
    }

    private fun findSendButton(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val list = ArrayDeque<AccessibilityNodeInfo>()
        list.add(root)
        while (list.isNotEmpty()) {
            val n = list.removeFirst()
            val hay = listOfNotNull(n.contentDescription?.toString(), n.text?.toString(), n.viewIdResourceName).joinToString(" ").lowercase()
            if (n.isEnabled && n.isVisibleToUser && SEND_TERMS.any { hay.contains(it) }) return n
            for (i in 0 until n.childCount) n.getChild(i)?.let(list::addLast)
        }
        return null
    }

    private fun isWhatsAppWindow(pkg: String?): Boolean =
        pkg == Constants.WHATSAPP_PACKAGE || pkg == Constants.WHATSAPP_BUSINESS_PACKAGE || pkg == Constants.MESSENGER_PACKAGE

    companion object {
        private const val TAG = "WwmAutoReply"
        private val SEND_TERMS = listOf(
            "send", "إرسال", "ارسال", "submit",
            "com.whatsapp:id/send", "com.whatsapp.w4b:id/send",
            "com.whatsapp:id/send_button", "com.whatsapp.w4b:id/send_button",
            "com.facebook.orca:id/send", "com.facebook.orca:id/send_button"
        )
        private val SEARCH_TERMS = listOf(
            "search", "بحث", "بحث في",
            "com.whatsapp:id/search", "com.whatsapp.w4b:id/search",
            "com.whatsapp:id/menuitem_search", "com.whatsapp.w4b:id/menuitem_search",
            "com.facebook.orca:id/search", "com.facebook.orca:id/search_action"
        )
    }
}
