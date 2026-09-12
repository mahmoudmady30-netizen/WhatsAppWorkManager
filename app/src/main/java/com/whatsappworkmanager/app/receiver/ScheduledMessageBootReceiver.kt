package com.whatsappworkmanager.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.whatsappworkmanager.app.WwmApplication
import com.whatsappworkmanager.app.worker.WorkScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Restores Premium scheduled-message alarms after a device reboot. */
class ScheduledMessageBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return

        val pending = goAsync()
        val app = context.applicationContext as WwmApplication
        CoroutineScope(Dispatchers.IO).launch {
            try {
                app.scheduledMessageRepository.observeScheduledMessages().first()
                    .filter { it.enabled }
                    .forEach { message ->
                        val hour = message.timeMinutes / 60
                        val minute = message.timeMinutes % 60
                        WorkScheduler.scheduleNextMessageAlarm(
                            context = app,
                            id = message.id,
                            text = message.text,
                            hour = hour,
                            minute = minute,
                            phoneNumber = message.phoneNumber,
                            recipientName = message.recipientName,
                            repeatDaily = message.repeatDaily
                        )
                    }
            } finally {
                pending.finish()
            }
        }
    }
}
