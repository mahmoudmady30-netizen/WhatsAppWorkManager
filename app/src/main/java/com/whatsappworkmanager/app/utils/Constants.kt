package com.whatsappworkmanager.app.utils

object Constants {
    const val WHATSAPP_PACKAGE = "com.whatsapp"
    const val WHATSAPP_BUSINESS_PACKAGE = "com.whatsapp.w4b"
    const val MESSENGER_PACKAGE = "com.facebook.orca"

    const val CHANNEL_WORK_SUMMARY = "channel_work_summary"
    const val CHANNEL_IMPORTANT_MESSAGES = "channel_important_messages"
    const val CHANNEL_SYSTEM = "channel_system"
    // Deliberately separate from CHANNEL_SYSTEM (IMPORTANCE_LOW, no vibration by default) —
    // a scheduled reminder firing is a time-sensitive event the person specifically asked to
    // be alerted for, not a quiet background status update.
    const val CHANNEL_SCHEDULED_REMINDERS = "channel_scheduled_reminders"

    const val NOTIFICATION_ID_SUMMARY = 1001
    const val NOTIFICATION_ID_IMPORTANT_BASE = 2000
    const val NOTIFICATION_ID_SCHEDULED_MESSAGE_BASE = 3000
    const val NOTIFICATION_ID_BADGE = 4001
    const val NOTIFICATION_ID_AUTO_REPLY_BASE = 5000

    const val WORKER_SUMMARY_TAG = "wwm_summary_worker"
    const val WORKER_CLEANUP_TAG = "wwm_cleanup_worker"
    const val WORKER_SCHEDULED_MESSAGE_TAG = "wwm_scheduled_message_worker"
    const val WORKER_NOTIFICATION_CATCH_UP_TAG = "wwm_notification_catch_up_worker"

    const val EXTRA_SUMMARY_ID = "extra_summary_id"
    const val EXTRA_SCHEDULE_ID = "extra_schedule_id"
    const val EXTRA_SCHEDULED_MESSAGE_ID = "extra_scheduled_message_id"
}
