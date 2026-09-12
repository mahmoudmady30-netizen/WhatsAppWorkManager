# WA premium v92

## Fixes
- Scheduled background send now caches the latest WhatsApp Direct Reply RemoteInput action for each chat while Notification Listener is connected. The alarm first tries the current notification and then the cached action, so a schedule is no longer dependent on the target notification still being visible at the exact scheduled minute.
- Scheduled jobs carry the saved phone number as well as the recipient name for safer matching.
- Auto Reply, Scheduled Message, Important Message and Summary notifications are explicitly dismissible; swipe-away invokes a cancellation receiver. Scheduled notifications also have an explicit Delete action.
- Auto Reply screen is more compact: smaller Add FAB/icons, tighter premium header, reduced card padding, and less vertical spacing while retaining readable multi-line AI instructions/replies.

## Important Android limitation
A local Android app cannot manufacture a WhatsApp Direct Reply action when WhatsApp has never exposed one for the target chat. Therefore screen-off scheduled sending is possible when WhatsApp's notification RemoteInput action is available (including a cached action while the notification-listener service remains connected). If WhatsApp provides no RemoteInput for the target, there is no supported local API that can send a brand-new WhatsApp message while the phone is locked. Accessibility can only be the unlocked/visible-UI fallback.
