package com.whatsappworkmanager.app

import android.app.Application
import android.content.Context
import com.whatsappworkmanager.app.data.ai.AiProviderFactory
import com.whatsappworkmanager.app.data.local.AppDatabase
import com.whatsappworkmanager.app.data.prefs.SettingsDataStore
import com.whatsappworkmanager.app.data.repository.ImportantContactRepositoryImpl
import com.whatsappworkmanager.app.data.repository.AutoReplyRuleRepositoryImpl
import com.whatsappworkmanager.app.data.repository.AutoReplyReplyHistoryRepositoryImpl
import com.whatsappworkmanager.app.data.repository.KeywordRuleRepositoryImpl
import com.whatsappworkmanager.app.data.repository.MessageRepositoryImpl
import com.whatsappworkmanager.app.data.repository.ReplyPhraseRuleRepositoryImpl
import com.whatsappworkmanager.app.data.repository.ScheduleRepositoryImpl
import com.whatsappworkmanager.app.data.repository.ScheduledMessageRepositoryImpl
import com.whatsappworkmanager.app.data.repository.SummaryRepositoryImpl
import com.whatsappworkmanager.app.data.repository.WorkGroupRepositoryImpl
import com.whatsappworkmanager.app.domain.usecase.KeywordScoring
import com.whatsappworkmanager.app.domain.usecase.MessageClassifier
import com.whatsappworkmanager.app.domain.usecase.ReplyDetector
import com.whatsappworkmanager.app.domain.usecase.ScheduleLogic
import com.whatsappworkmanager.app.domain.usecase.SummaryGenerator
import com.whatsappworkmanager.app.utils.NotificationHelper
import com.whatsappworkmanager.app.utils.LocaleHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Lightweight, dependency-free "service locator" — deliberately avoids pulling in Hilt/Dagger
 * so the project has zero extra annotation-processing surface to go wrong. Every screen's
 * ViewModel factory reads what it needs from here.
 */
class WwmApplication : Application() {

    lateinit var database: AppDatabase
        private set
    lateinit var settingsDataStore: SettingsDataStore
        private set

    lateinit var messageRepository: MessageRepositoryImpl
        private set
    lateinit var workGroupRepository: WorkGroupRepositoryImpl
        private set
    lateinit var summaryRepository: SummaryRepositoryImpl
        private set
    lateinit var scheduleRepository: ScheduleRepositoryImpl
        private set
    lateinit var keywordRuleRepository: KeywordRuleRepositoryImpl
        private set
    lateinit var scheduledMessageRepository: ScheduledMessageRepositoryImpl
        private set
    lateinit var importantContactRepository: ImportantContactRepositoryImpl
        private set
    lateinit var replyPhraseRuleRepository: ReplyPhraseRuleRepositoryImpl
        private set
    lateinit var autoReplyRuleRepository: AutoReplyRuleRepositoryImpl
        private set
    lateinit var autoReplyReplyHistoryRepository: AutoReplyReplyHistoryRepositoryImpl
        private set

    lateinit var aiProviderFactory: AiProviderFactory
        private set
    lateinit var keywordScoring: KeywordScoring
        private set
    lateinit var replyDetector: ReplyDetector
        private set
    lateinit var messageClassifier: MessageClassifier
        private set
    lateinit var summaryGenerator: SummaryGenerator
        private set
    lateinit var scheduleLogic: ScheduleLogic
        private set

    // Runs before onCreate() — wraps the Application's own base Context in the saved language
    // (see LocaleHelper), so app.getString()/applicationContext.resources calls used outside
    // any Activity (ViewModels, Workers, this class's own onCreate below) are already correct
    // from the very first line, not just after some later recreate().
    override fun attachBaseContext(base: Context) {
        val languageCode = LocaleHelper.readSavedLanguage(base)
        super.attachBaseContext(LocaleHelper.wrapContext(base, languageCode))
    }

    override fun onCreate() {
        super.onCreate()

        // Kept alongside attachBaseContext() above — this also updates AppCompatDelegate's own
        // locale state, which some framework/system UI surfaces read independently.
        LocaleHelper.applySavedLocale(this)

        database = AppDatabase.getInstance(this)
        settingsDataStore = SettingsDataStore(this)

        messageRepository = MessageRepositoryImpl(database.messageDao())
        workGroupRepository = WorkGroupRepositoryImpl(database.workGroupDao())
        summaryRepository = SummaryRepositoryImpl(database.summaryDao())
        scheduleRepository = ScheduleRepositoryImpl(database.scheduleDao())
        keywordRuleRepository = KeywordRuleRepositoryImpl(database.keywordRuleDao())
        scheduledMessageRepository = ScheduledMessageRepositoryImpl(database.scheduledMessageDao())
        importantContactRepository = ImportantContactRepositoryImpl(database.importantContactDao())
        replyPhraseRuleRepository = ReplyPhraseRuleRepositoryImpl(database.replyPhraseRuleDao())
        autoReplyRuleRepository = AutoReplyRuleRepositoryImpl(database.autoReplyRuleDao())
        autoReplyReplyHistoryRepository = AutoReplyReplyHistoryRepositoryImpl(database.autoReplyReplyHistoryDao())

        // Default instances (no custom rules) — used as a fallback and by screens that don't
        // need the live, DB-backed rule set. The actual notification-capture path
        // (see captureMessage in WhatsAppNotificationListenerService.kt) builds a fresh
        // classifier per message using the *current* custom keyword rules, important-people
        // list, and reply phrases from the repositories above, so Settings changes take effect
        // immediately without an app restart.
        keywordScoring = KeywordScoring()
        replyDetector = ReplyDetector()
        messageClassifier = MessageClassifier(keywordScoring, replyDetector)
        summaryGenerator = SummaryGenerator(keywordScoring)
        scheduleLogic = ScheduleLogic()
        aiProviderFactory = AiProviderFactory(settingsDataStore)

        NotificationHelper.ensureChannels(this)

        // Resyncs the app icon badge with the actual unread Important/Need Reply count on
        // every cold start — a safety net in case it drifted (e.g. the process was killed
        // mid-update) rather than relying purely on the badge staying correct forever.
        CoroutineScope(Dispatchers.IO).launch {
            com.whatsappworkmanager.app.utils.MessageCleanup.purgeSystemNotificationJunk(this@WwmApplication)
            com.whatsappworkmanager.app.utils.MessageCleanup.purgeGroupSummaryJunk(this@WwmApplication)
            com.whatsappworkmanager.app.utils.MessageCleanup.purgeGroupTitleSuffixJunk(this@WwmApplication)
            com.whatsappworkmanager.app.utils.BadgeUpdater.refresh(this@WwmApplication)
        }
    }
}
