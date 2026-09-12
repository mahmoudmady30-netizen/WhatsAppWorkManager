# WA premium v99 — Scheduled Message Automation

- Scheduled Messages now use a durable Accessibility UI automation lane in addition to WhatsApp Direct Reply RemoteInput.
- The automation targets the exact WhatsApp package selected in Settings: Regular WhatsApp (`com.whatsapp`) or WhatsApp Business (`com.whatsapp.w4b`); Auto resolves the installed app.
- For phone-number schedules, the automation opens WhatsApp, opens New Chat, searches the configured international phone number, selects the matching result, opens the conversation, enters the scheduled text, and clicks Send.
- UI actions are node-based (text/content-description/resource-id) with clickable-parent fallback rather than fixed screen coordinates.
- The app only marks a schedule Sent after the Send control is successfully clicked.
- The notification RemoteInput lane remains available as the preferred locked-screen path when WhatsApp exposes a Direct Reply action.
- Android Accessibility must be explicitly enabled by the user. A locked device can still block UI automation; this is an Android security limitation.
