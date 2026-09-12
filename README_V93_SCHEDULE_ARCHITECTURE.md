# WA premium v93 — Scheduled Send Architecture

## What changed

The previous design had a fragile chain:
AlarmManager -> WorkManager -> ordered broadcast -> live NotificationListener process.
If the listener process was disconnected or had been killed, the alarm only produced a reminder.

v93 changes this to a durable transaction model:

1. AlarmManager fires at the requested time (RTC_WAKEUP / allow-while-idle when available).
2. The alarm writes the scheduled message ID to a persistent pending-send set.
3. The alarm requests a NotificationListener rebind.
4. When the listener connects, it scans active WhatsApp notifications and attempts the pending send.
5. Every new WhatsApp notification also triggers a pending-send pass, so a notification arriving
   just after the scheduled time can complete a pending transaction without user interaction.
6. Only after WhatsApp's Direct Reply RemoteInput actually accepts the PendingIntent is the
   scheduled message marked sent and its reminder removed.
7. Failed attempts remain pending; they are not converted into a fake successful send.

## Important Android / WhatsApp limitation

A normal Android app cannot create an arbitrary WhatsApp outbound message in the background.
WhatsApp must expose an Android notification Direct Reply RemoteInput for the target chat.
This implementation therefore provides the strongest local, screen-off path available without
using an official WhatsApp Business API: it reuses WhatsApp's own notification reply action.

If WhatsApp has no active notification with a reply action for that target, v93 deliberately keeps
retrying the durable transaction instead of opening the UI, pressing Send blindly, or claiming that
it was sent. For guaranteed arbitrary scheduled outbound messages while the phone is locked,
an official WhatsApp Business/Cloud API integration is required.
