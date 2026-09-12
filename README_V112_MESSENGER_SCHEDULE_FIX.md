# WA premium v112 — Messenger Scheduled Messages Fix

- Scheduled Messenger jobs now use a dedicated Messenger UI automation lane instead of only launching Messenger.
- The scheduled job searches for the configured Messenger user, opens the matching result, verifies the conversation composer and Send control, enters the message, and clicks Send.
- Messenger search/new-message accessibility identifiers and common labels were expanded.
- Messenger schedules no longer fall back to WhatsApp when resolving the target package.
- The queue remains pending if Messenger search or the target conversation cannot be safely identified; opening Messenger alone is never treated as a successful send.
