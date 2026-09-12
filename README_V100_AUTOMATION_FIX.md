# WA premium v100 — Scheduled Automation Reliability Fix

- Dashboard notification cards now dismiss with a leftward swipe only.
- Scheduled automation keeps the selected WhatsApp package (`com.whatsapp` or `com.whatsapp.w4b`) end-to-end.
- New Chat/Search automation has broader localized/resource-id matching.
- Phone-number schedules have a deterministic recovery path using WhatsApp click-to-chat for the exact selected package, followed by Accessibility Send.
- Send confirmation now uses the scheduled target matcher, so phone-only schedules can confirm the exact-chat recovery even when WhatsApp displays the contact name instead of the phone number.
- The queue remains pending unless the Send control is actually clicked.
