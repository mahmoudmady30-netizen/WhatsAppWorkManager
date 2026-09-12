package com.whatsappworkmanager.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.whatsappworkmanager.app.data.local.dao.ImportantContactDao
import com.whatsappworkmanager.app.data.local.dao.AutoReplyRuleDao
import com.whatsappworkmanager.app.data.local.dao.AutoReplyReplyHistoryDao
import com.whatsappworkmanager.app.data.local.dao.KeywordRuleDao
import com.whatsappworkmanager.app.data.local.dao.MessageDao
import com.whatsappworkmanager.app.data.local.dao.ReplyPhraseRuleDao
import com.whatsappworkmanager.app.data.local.dao.ScheduleDao
import com.whatsappworkmanager.app.data.local.dao.ScheduledMessageDao
import com.whatsappworkmanager.app.data.local.dao.SummaryDao
import com.whatsappworkmanager.app.data.local.dao.WorkGroupDao
import com.whatsappworkmanager.app.data.local.entity.ImportantContactEntity
import com.whatsappworkmanager.app.data.local.entity.AutoReplyRuleEntity
import com.whatsappworkmanager.app.data.local.entity.AutoReplyReplyHistoryEntity
import com.whatsappworkmanager.app.data.local.entity.KeywordRuleEntity
import com.whatsappworkmanager.app.data.local.entity.MessageEntity
import com.whatsappworkmanager.app.data.local.entity.ReplyPhraseRuleEntity
import com.whatsappworkmanager.app.data.local.entity.ScheduleEntity
import com.whatsappworkmanager.app.data.local.entity.ScheduledOutgoingMessageEntity
import com.whatsappworkmanager.app.data.local.entity.SummaryEntity
import com.whatsappworkmanager.app.data.local.entity.WorkGroupEntity

@Database(
    entities = [
        MessageEntity::class,
        WorkGroupEntity::class,
        SummaryEntity::class,
        ScheduleEntity::class,
        KeywordRuleEntity::class,
        ScheduledOutgoingMessageEntity::class,
        ImportantContactEntity::class,
        ReplyPhraseRuleEntity::class,
        AutoReplyRuleEntity::class,
        AutoReplyReplyHistoryEntity::class
    ],
    version = 14,
    // exportSchema=true is Room's normal best practice (it lets you write real Migrations
    // later by diffing exported JSON schemas). It's off here only because this project has
    // fallbackToDestructiveMigration() enabled for pre-release convenience (see below) and no
    // schema-diff workflow set up yet — turn it back on, and set the matching
    // `room.schemaLocation` KSP arg in app/build.gradle.kts, once you add real Migrations
    // before a production release.
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun messageDao(): MessageDao
    abstract fun workGroupDao(): WorkGroupDao
    abstract fun summaryDao(): SummaryDao
    abstract fun scheduleDao(): ScheduleDao
    abstract fun keywordRuleDao(): KeywordRuleDao
    abstract fun scheduledMessageDao(): ScheduledMessageDao
    abstract fun importantContactDao(): ImportantContactDao
    abstract fun replyPhraseRuleDao(): ReplyPhraseRuleDao
    abstract fun autoReplyRuleDao(): AutoReplyRuleDao
    abstract fun autoReplyReplyHistoryDao(): AutoReplyReplyHistoryDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "wwm_database.db"
                )
                    // Pre-release convenience only: this project has no shipped users yet, so a
                    // schema bump (like adding a table) just resets local data instead of
                    // requiring a Migration. Replace this with real Migration objects before
                    // your first production release, so existing users' data survives updates.
                    .fallbackToDestructiveMigration()
                    .build().also { INSTANCE = it }
            }
        }
    }
}
