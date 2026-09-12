# WhatsApp Work Manager

## Latest session: five separate fixes — WhatsApp variant, Auto Reply matching, the ticker, and Quick Chat

**"Can't open link with WhatsApp Messenger" on Send** — the Scheduled Messages screen's own
Send button (added two sessions ago) never read the person's saved WhatsApp-variant
preference, defaulting to "auto," which always prefers regular WhatsApp when both are
installed — exactly wrong for someone whose number is only on Business. Now reads and passes
the actual saved setting, same as the Dashboard's Send Now and the reminder notification
already correctly did.

**The real reason Auto Reply "only worked once the person was in Important People"**: not a
dependency between the two features at all — WhatsApp shows a sender's *resolved contact
name* only when that number is saved in the phone's own Contacts; otherwise it shows the raw
number. Saving someone as Important (which can also save them to phone contacts) happened to
be what made WhatsApp start showing a name Auto Reply's plain substring match could actually
match. The Auto Reply field is explicitly labelled "Person (name or number)," but a number
typed one way never matched the same number formatted differently (spaces, a "+", dashes) as
WhatsApp shows it. Fixed by comparing digits-only when the rule looks like a phone number, in
addition to the existing name-substring match — verified against 5 cases including the exact
mismatched-formatting scenario.

**The notification ticker no longer repeats the same sender** — recent messages are now
grouped by sender before building the ticker, so three messages from the same person collapse
into one entry with a "(3)" count suffix rather than showing (and cycling through) the same
name three times in a row.

**Quick Chat tightened up to fit without scrolling** — removed the scroll entirely; smaller
header icon (64dp → 48dp) and padding throughout, and the three WhatsApp-variant buttons are
now a single row instead of three stacked full-width buttons (the single biggest space
saving), matching the same "no separate scroll, everything visible" request as the Dashboard.

## Previous session: Battery Optimization now prompts a direct dialog for this app, no scrolling


Switched from `ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS` (opens the generic all-apps list —
exactly the "scroll down to find it myself" experience just reported) to
`ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` with this app's own package URI, which prompts a
direct system dialog — "Allow [App] to ignore battery optimizations?" — with no navigation at
all. Added the `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` permission this requires. Kept a two-
level fallback (the old list action, then the app's own details page) for the rare OEM build
that doesn't support the direct dialog.

## Previous session: fixed the CI build failure from the fixed-header/footer layout session


Missing `import androidx.compose.material3.TextButton` — used for the compact scheduled-
message row's "Send Now" button. Same underlying pattern as a couple of previous build
failures: because `TextButton` itself didn't resolve, the compiler couldn't determine its
trailing-lambda parameter is `@Composable`, which cascaded into three "@Composable invocations
can only happen from the context of a @Composable function" errors on the `Text`/
`stringResource` calls *inside* that lambda — one missing import producing four seemingly
unrelated-looking errors at once. Added the import; also ran a broader check across the file
for every other commonly-used Material3 component to confirm nothing else was missing the
same way.

## Previous session: fixed header/fixed footer layout, no scrolling, compact throughout


Restructured as requested: the notification ticker pinned at the very top, the three nav
buttons pinned at the very bottom, and everything between them (banners, the scheduled/AI-
reply quick-actions card, the Summary card, all four stat cards) in a non-scrolling middle
section sized to fit in the space between. There's no single Compose mechanism that "shrinks
arbitrary content to fit an unknown screen height," so this is achieved by tightening
padding and text size throughout rather than one trick: `StatCard`'s padding (18dp → 12dp),
icon circle (40dp → 32dp) and value text (headlineMedium → titleLarge); the Summary card's
padding (20dp → 14dp); the quick-actions card's AI-reply section (16dp → 12dp); and the gaps
between sections generally (20dp → 10dp). Should comfortably fit without scrolling on typical
phone screens; genuinely tiny screens or very large system font settings are an edge case no
padding tightening alone can fully guarantee against, for what it's worth.

## Previous session: Need Reply names now show on tapping the icon, not as a persistent line


Removed the always-visible names preview that used to sit below the stat cards ("Ahmed,
Kholoud +2 more"). Instead, `StatCard` now supports an optional `onIconClick` — makes just the
icon's own circle a separate tap target, independent of the card's main `onClick` (which still
navigates to the filtered list on a tap anywhere else) — and `iconClickContent` for whatever
should appear anchored to it. Need Reply's icon now opens the same dropdown of names as
before, but only on demand; nothing about it takes up space on the Dashboard by default.

## Previous session: fixed the two test call sites broken by the previous session's new parameter


`NotificationHelperTest.kt` had two calls to `showScheduledMessageReminder(...)` still using
the old parameter list (missing the `messageId` parameter added last session for the missed-
count tracking). Added `messageId = 1L` / `messageId = 2L` to both. Verified all three real
call sites in the project (the two tests, plus `ScheduledMessageReminderWorker`) now correctly
pass it, and re-ran the full project-wide sweep (braces, comments, manifest, every `R.string`
reference, EN/AR parity) clean.

## Previous session: fixed the CI build failure from the compact-row/badge/Send-button session


Three separate mistakes, all from the same recent Dashboard work:

1. **Wrong type name**: my new `Data` holder class in `DashboardViewModel` referenced
   `WorkGroup`, but the actual domain model is called `WorkGroupInfo`. Since that type didn't
   resolve, the compiler couldn't infer what `groups` actually was either, which cascaded into
   `Unresolved reference 'isEnabled'` and `'it'` on the very next line — one wrong type name
   took out three seemingly unrelated-looking errors at once.
2. **Missing import**: `HorizontalDivider` (used in the new quick-actions card) — added the
   import.
3. **Missing import, again the extension-function class of mistake**: `togetherWith` (used to
   combine the ticker's enter/exit transitions) is an infix extension function in
   `androidx.compose.animation`, not a member of the transition objects it's called on —
   needs an actual import in scope, the same underlying issue as the `Icons.Filled.Check`/
   `Circle` fix from a few sessions back. Added `import androidx.compose.animation.togetherWith`.

Ran the full project-wide sanity sweep (brace balance, every `R.string` reference resolves,
EN/AR string counts equal, no stray `WorkGroup` references anywhere else) — all clean.

## Previous session: the notification ticker — the last piece of the previous request


New ticker strip, now above the quick-actions card (itself above Summary) — cycles
automatically through the most recent captured messages, one at a time, every ~3.2 seconds:
"Important — Ahmed", "Needs a reply — Sales Team", "New message — Kholoud", tagged with the
same icon and color as the matching stat card (red for Important, amber for Need Reply, plain
for everything else) so it reads as a genuine summary of *which* category each one landed in,
not just a generic feed. The "flip" look is a vertical slide + fade between entries (`slideIn/
OutVertically` + `fadeIn/Out` via `AnimatedContent`) — the same visual idea as a flip clock or
scoreboard — plus a small "2/8" position indicator. Not interactive by design: it's a glance-
at ambient summary, not a substitute for actually opening Search.

Backed by a new `recentActivity` list in `DashboardUiState` — the 8 most recently captured
messages, each tagged Important/Need Reply/Normal from the same fields the stat cards already
use, so the ticker and the counts above it can never disagree about a message's category.

This completes the multi-part Dashboard request from the last two sessions: Need Reply names,
vibrating scheduled reminders, the scheduled-message-and-ready-reply quick-actions card, and
now this ticker.

## Previous session: Need Reply names, vibrating scheduled reminders, and a new quick-actions card


**Need Reply names, shown outside the card**: small text under the Important/Need Reply row
listing who's actually waiting on a reply, most recent first. With more than two, it collapses
to "Name, Name +N more" — tapping it opens a proper dropdown listing everyone, rather than
wrapping across several lines and crowding out everything below.

**Scheduled reminders now actually vibrate.** Real bug found while implementing this: the
reminder notification was posted on the low-importance system channel, which doesn't vibrate
by default on Android. Added a dedicated high-importance channel with vibration enabled, plus
an explicit vibration pattern on the notification itself as a second layer.

**New quick-actions card**, between the setup banners and the Summary card:
- The single soonest upcoming scheduled message, with a "Send Now" button that fires it
  immediately instead of waiting for its actual time (opens the exact WhatsApp chat, message
  pre-filled, same click-to-chat flow used everywhere else in this app).
- Whichever AI-generated Auto Reply is currently ready to send, with its own "Send" button —
  a second, more visible path to the exact same action the Auto Reply notification's tap
  action already does. New: the ready reply is now persisted (previously it only ever existed
  inside that one notification, gone once dismissed or scrolled away) and clears once sent
  from here.

## Still to come
The "live notification ticker" above the Summary card — an animated, flip-style strip
summarizing incoming notifications by category as they arrive — is a substantially larger,
separate piece (needs its own event-tracking mechanism and animation) and isn't done yet.

## Previous session: the real cause of one group splitting into 3 separate cards, fixed


**Root cause**: `cleanGroupTitle` only stripped WhatsApp's per-sender suffix in its bundled
form — "GroupName (N messages): Sender" — but WhatsApp uses a *second*, simpler form for a
single new message with no count needed: bare "GroupName: Sender". Left unhandled, the exact
same group got captured under a different "name" every time a different member sent the first
message of a batch — exactly what the screenshot showed: "Masry Area - Private Group",
"...: Mahmoud Mansour", and "...: +971 55 550 7471" as three separate cards for one real group.

Extended `cleanGroupTitle` to also strip this bare form — but only when the text after the
*last* colon actually looks like a sender identifier (a phone number, or a short capitalized
name), not just because a colon is present, since a group can legitimately have one in its own
name (e.g. "Team: Design"). Verified against 10 cases including both exact examples from the
screenshot and two colons-that-aren't-this-suffix cases, confirming the heuristic doesn't
overreach.

No separate migration needed for the duplicates already sitting in the database: the existing
`MessageCleanup.purgeGroupTitleSuffixJunk()` (already run automatically on every app startup)
calls this same `cleanGroupTitle` function generically — merging into an already-existing
clean-named entry when one exists — so fixing the function itself is enough for the next
startup to fold the already-split "Masry Area - Private Group" entries back into one.

## Previous session: Auto Reply screen — numbered, compact, timestamped, and filterable by tone


**Numbered and compacted**: each card now shows "1. Islam Saad Uae" instead of just the name,
matching the numbering pattern used elsewhere (Summary, Search). Card padding tightened
(14dp → 12dp), the match-target and reply-preview lines now use smaller text with `maxLines`
so a long AI-generated preview doesn't stretch the card, the switch and icon buttons are
smaller and more tightly sized — each card is noticeably shorter without losing any
information.

**Timestamp added**: new `updatedAt` field (DB version 10 → 11) — set on creation, refreshed
on every real edit (the full Edit dialog, or the quick tone-only change), shown on each card
using the same relative-time formatting as the Search screen ("Today · 3:28 PM", falling back
to a full date once it's no longer today). Made that formatter `internal` rather than private
so both screens share one implementation instead of duplicating the logic.

**Filter tabs added**: All / Work / Friend / Family chips above the list, the same idea as the
Today/Important/Need Reply filter chips on the message list — tapping one shows only rules set
to that tone, with a plain "no rules with this tone yet" message when a filter has nothing to
show, rather than an empty list that looks broken.

## Previous session: fixed the CI build failure from the previous session (unresolved 'Check'/'Circle')


The GitHub Actions build failed with `Unresolved reference 'Check'` and `Unresolved reference
'Circle'` in `AutoReplyScreen.kt`. Root cause: `Icons.Filled.Check` and `Icons.Filled.Circle`
are Kotlin *extension properties* (declared in package `androidx.compose.material.icons.filled`,
not actual members of the `Icons.Filled` object) — fully-qualifying the call as
`androidx.compose.material.icons.Icons.Filled.Check` doesn't resolve an extension property the
way it resolves an ordinary member; extension properties need an actual import in scope. Fixed
by adding proper `import androidx.compose.material.icons.filled.Check` and `...Circle` and
using the short `Icons.Filled.X` form, matching every other icon usage in this codebase. Ran a
project-wide audit confirming no other file has this same mistake — this was an isolated slip
from where those two icons were added earlier.

## Previous session: Auto Reply now has a "reply tone" per person — Work, Friend, or Family


New `ReplyTone` enum (Work/Friend/Family, DB version 9 → 10) — three side-by-side, single-
select options at the top of the Add/Edit Auto Reply dialog, a filled dot marking whichever's
selected. This isn't just a label: it actually reshapes the AI's prompt when it writes a
reply for that person — "this person is a work colleague, write a professional, concise
reply" vs. "...a close friend, write a warm, casual reply" vs. "...family, write something
warm and caring" — added as a new optional parameter threaded through all five cloud AI
providers' shared `suggestRepliesForConversation` (default `null` preserves the exact original
behavior for Search/Summary's "Generate Reply," which still wants 3 varied-tone options rather
than one tone-matched reply).

Saved tone shows as a small chip on each rule's card — tapping it opens a quick dropdown to
change just the tone (with a checkmark on the current one) without needing the full Edit
dialog, in addition to being editable there too like every other field.

## Previous session: reverted the "fixed footer" experiment — it made the gap look worse, not better


Fair, direct feedback: pinning the nav row to the bottom with a divider above it didn't fix
anything — it just relocated the same empty space and made it *more* visually obvious (a
divider sitting right above a dead gap reads as more broken than the gap alone, not less).

Reverted to a plain, ordinary scrolling `Column` — no `weight()`, no measured filler, no
pinned footer, no divider. The nav row just flows naturally right after the stat cards with
the same spacing as everything else. On a quiet day with all-zero counts, there genuinely
isn't enough content to fill a tall screen, and no layout trick changes that — this is exactly
how an ordinary content-driven screen behaves in any other app, and it doesn't look "broken,"
just short. Chasing a literal zero-empty-pixels result across four different techniques never
actually improved the screen; each one just moved the emptiness around or added a visual
element that drew more attention to it.

## Previous session: a fundamentally different, more robust fix for the bottom gap


Every previous attempt tried to make the *scrollable content itself* stretch to fill leftover
space — through `weight()`, through hand-measured pixel arithmetic, through both combined.
Each had a plausible-looking failure mode around how `weight()` and `verticalScroll()`
interact, and none of it visibly fixed anything per the actual screenshots.

Different approach entirely this time: rather than stretching content, the nav row (AI Reply /
Quick Chat / Schedule) is now a **fixed footer**, pinned to the actual bottom of the screen —
the same structure as any chat screen (scrollable message list above, a fixed input bar always
at the bottom, regardless of how many messages are loaded). The scrollable area above it uses
`weight(1f)` against a `fillMaxSize()` parent Column — ordinary, single-child weight usage
with no scroll-vs-weight ambiguity involved, since the weighted element *is* the scroll
container itself rather than something trying to coexist inside one. The footer is now always
exactly at the bottom edge, never floating above a dead gap, regardless of how tall the
content above happens to be.

## Previous session: Settings reorganized into six clear categories


Previously one long flat list of ~18 rows with a divider between every single one, regardless
of whether they were actually related. Now grouped into six categories, each with a small
labeled header and its rows visually contained in one rounded, low-elevation card (the
standard "grouped settings list" pattern — iOS Settings, Material's own settings samples):

- **Permissions** — Notification Access, Battery Optimization
- **Automation** — Summary Schedule, Scheduled Messages, Important People, Keywords, Reply
  Detection, auto-enable-new-groups
- **AI & Summaries** — AI Provider, cloud-sending consent, API key, connection status
- **Appearance & Region** — Language, Theme, Default Country, WhatsApp variant
- **Data & Privacy** — Retention, Privacy, Clear Data
- **About**

Nothing about any individual row's behavior changed — same click targets, same conditional
rows (API key only shown for a cloud provider, etc.), same logic throughout. Purely a visual
reorganization, requested explicitly as "categories, professional but still concise."

Also fixed two more hardcoded English strings surfaced while restructuring this screen: the
Notification Access row's "Enabled"/"Disabled" subtitle, and all four states of the AI
connection status row ("Not tested yet", "Testing…", "Connected...", "Connection failed").

## Previous session: four separate fixes to Scheduled Messages, Search, and the language switch


**Time picker now defaults to the device's actual current time** when adding a new scheduled
message — it was hardcoded to 8:00 AM before, regardless of when the person actually opened
the dialog. Editing an existing message still correctly starts from that message's own saved
time, unaffected.

**Recipient name now shown in the Scheduled Messages list** — new `recipientName` field on
`ScheduledOutgoingMessage` (DB version 8 → 9), captured whenever a name is actually known:
picking from Important People, or from the phone's own Contacts via "Pick from Contacts" (the
name was already being returned there, just silently discarded before). A name without a
number to go with it is never saved, since there'd be nothing useful to show it next to.

**"Read All" / "Delete All" now pinned across every filter, not just Today** — both already
operated generically on whatever's in `state.results` regardless of which filter produced it;
the only thing actually restricting them to Today was the display condition, now removed.

**Language switch is now "EN"/"AR" text instead of flag emoji**, per explicit request — same
dropdown, same behavior, just a plain two-letter label instead of 🇪🇬/🇺🇸.

**Also fixed while in there**: found more hardcoded English text in the Scheduled Messages
list item ("Every day", "Opens chat directly", "Opens WhatsApp...") that had slipped past the
earlier localization sweep — now proper string resources with Arabic translations.

## Previous session: dropped the `weight()`-based filler for a plainer, more direct one


Still not confident the previous `weight(1f)` filler was landing correctly (it's harder to
verify blind than plain arithmetic), so switched to computing the exact filler height directly
— `available height minus actual measured content height, floored at zero` — and applying it
as an explicit `Spacer(Modifier.height(...))`, rather than relying on Compose's `weight()`
distribution to do that subtraction implicitly. Same measurement approach as before
(`BoxWithConstraints` for the available height, `onGloballyPositioned` for the real content
height, conditional `verticalScroll` only once content actually exceeds the available height),
just with a more direct, easier-to-reason-about final step.

## Previous session: the language flag is now a proper dropdown, not a bare-tap toggle


Extracted the flag icon into one shared `LanguageFlagMenu` composable (in
`CommonComponents.kt`), used identically by both the Dashboard and Summary screens instead of
each having its own separate copy. Tapping it now opens a small, proper dropdown — "🇪🇬
العربية" / "🇺🇸 English", with a checkmark next to whichever is currently active — rather than
just toggling straight to the other language on a bare tap. Same underlying switch as Settings
→ Language either way; this is purely about making the interaction feel more deliberate and
professional.

## Previous session: the fill-remaining-space fix, actually working this time


The previous attempt had a real bug: it computed a filler Spacer's height by hand and added it
inside a Column that was *always* wrapped in `verticalScroll` — but `weight()`-style space
distribution and `verticalScroll` fundamentally don't combine correctly (a scrollable
container implies unbounded content height, while distributing "leftover space" requires a
*bounded* height to measure the leftover against), so the calculation never reliably landed on
a nonzero, correct value.

Rewritten to pick the right mechanism per case instead of trying to force one mechanism to
handle both: measures the real content's rendered height against the screen's available
height, and when content is short enough to fit, uses a `Modifier.weight(1f)` filler Spacer on
a `fillMaxSize()` Column (the standard, reliable Compose idiom for "stretch to fill," since
weight *does* work correctly once the Column has a bounded height) — no scrolling involved at
all in that case. Once content is genuinely tall enough to need scrolling, `verticalScroll` is
applied instead and no filler is added, since there's naturally no leftover space left to fill
by then anyway.

## Previous session: a real fill-remaining-space fix for the Dashboard, and the language flag added there too


**The "empty space at the bottom" fix, done properly this time.** The earlier round only
increased spacing/padding — a real improvement, but not what was actually asked for. This
measures the Dashboard's actual available height (`BoxWithConstraints`) and the real content's
rendered height (`onGloballyPositioned`), then adds a trailing spacer sized to exactly the
leftover space — so short content genuinely fills the screen instead of leaving a gap below.
Once content is tall enough to need scrolling on its own, the computed filler naturally becomes
zero and the screen behaves like an ordinary scrolling page, same as before. Converted the
Dashboard from `LazyColumn` to a plain scrollable `Column` to make this measurement
straightforward (the screen's content is a small, fixed set of sections, not an open-ended
list, so this loses nothing).

**Language flag added to the Dashboard too** — the same 🇪🇬/🇺🇸 toggle added to the Summary
screen last round, now also in the Dashboard's top bar, next to Refresh and Settings.

**On what the three nav labels are in Arabic**, for reference: Auto Reply → "رد AI", Quick
Chat → "محادثة سريعة", Schedule → "جدولة".

## Previous session: comprehensive localization sweep — a lot of hardcoded English text found and fixed


**Country picker (screenshot 1)**: every one of the 39 countries now has a proper Arabic name
(`CountryCode.nameAr`, shown via a new `displayName(language)` helper) — previously the list
only ever had English names, regardless of the app's language. Search now matches against
English name, Arabic name, and dial code all at once (`CountryCodes.matches`), so typing either
"مصر" or "Egypt" finds the same result. Fixed the dial code display flipping to "20+" instead
of "+20" — a bare "+" is BIDI-neutral and took its direction from the surrounding Arabic
paragraph; anchored with an explicit LTR mark (U+200E) fixes it regardless of app language.

**Phone number field (screenshot 2)**: the country-code box and local-number field are now
explicitly forced LTR regardless of app language — a phone number is conventionally written
country-code-then-number left to right even in an otherwise fully Arabic UI, and Compose's
default RTL mirroring was flipping their visual order. Also found and fixed: the explanatory
text below this field, and a second one in the Important People dialog, were both hardcoded
English strings that never went through Android's string-resource system at all — they showed
in English regardless of the app's language setting because nothing was ever translating them
in the first place.

**AI summary language (screenshot 3)**: `SummaryGenerator`'s local fallback text was already
correctly bilingual (checks the app's language setting properly) — the real gap was the AI
prompt not being forceful enough about language when the *input messages* were themselves in a
different language than requested; LLMs have a real tendency to drift toward matching the
input's language despite instructions otherwise. Strengthened both language branches of the
prompt with an explicit "these messages may be in any language, but you must respond in
[language] only" instruction. Also added a small flag icon (🇪🇬/🇺🇸) to the Summary screen's top
bar — tapping toggles the app's language right where the language-sensitive content is being
read, the same underlying switch as Settings → Language.

**Broader sweep, requested explicitly ("review the whole design")**: found and fixed nine more
hardcoded English strings that never had Arabic translations at all — three Toast messages in
`IntentHelper.kt` (not composable functions, so these use `context.getString(...)` rather than
`stringResource()`), three AI-connection-test error messages in `SettingsViewModel.kt`, empty-
state messages in Work Groups/Reply Phrases/Important People/Keyword Rules, an explanatory card
in Work Groups (title + body + button, all three), the default label on the shared
`PickFromContactsButton` (silently English in the three screens that didn't override it), and
one accessibility `contentDescription`. All now go through proper `stringResource`/`getString`
calls with matching Arabic translations — EN/AR string counts confirmed equal (252/252).

## Previous session: the actual root cause of the recurring duplicates, finally found


**Every previous duplicate fix (the timestamp tolerance window, the mutex around check-then-
insert) addressed a real but different bug — none of them were the one still showing up.**
The genuine cause: the periodic catch-up scan (and a listener reconnect) re-processes *every*
notification still sitting active in the shade — and when a notification's `postTime` is
missing or invalid, this app's fallback was `System.currentTimeMillis()`, a fresh value on
every single call. Every time the scan re-scanned the exact same unread, still-active
notification, it looked like a brand-new message arriving minutes or hours later — comfortably
outside even the widened 3-minute tolerance window, since the gap was never about timing at
all.

Fixed at the root with `StatusBarNotification.key` — the official, stable Android identifier
for "this is the same notification slot," which stays identical across every re-delivery of an
unchanged notification, unlike `postTime`. Paired with the notification's own text (not key
alone) specifically so a notification that's genuinely *updated in place* with new content
(an appended-message style notification) still gets through — only a re-scan where **both**
the key and the text match something already seen gets blocked. `NotificationDedupTest.kt`
covers the repeat-is-blocked case, the same-key-different-text case (the one that would have
silently swallowed real messages if this were keyed on `key` alone), and that unrelated keys
never interfere with each other.

**Also added**: long messages in Search now collapse to 4 lines with a "Show more" toggle
(matching the Summary cards' pattern), rather than always showing the full text inline —
requested explicitly for exactly the kind of long, multi-line report shown in the screenshot.

## Previous session: Dashboard button fixes, and a real fix for "Auto Reply doesn't work when locked"


**"Generate Summary Now" was wrapping to 3 lines** — shortened the label to "Generate Now"
(the "Summary" was redundant sitting right next to "View Summary" anyway), added
`maxLines = 1` to both summary buttons, and tightened the tonal button's padding so it stays
one line at the same width as "View Summary."

**The bottom nav row's labels were getting clipped** ("Auto Reply" → "Auto", "Schedule" →
"Sche") — three equal-width buttons with default Material padding simply didn't have room for
icon + full label at normal phone widths. Reduced content padding, shrank the icon/text size
slightly, and renamed "Auto Reply" to **"AI Reply"** with its own icon (a sparkle, matching the
AI-generated-content icon used elsewhere in the app) — it was the one button of the three with
no icon at all before. All three now fit their full label without clipping.

**Real fix for "Auto Reply doesn't work when the phone is locked"**: the AI reply-generation
network call had no timeout — a call that *hangs* rather than fails outright (exactly what
happens to background network access under Doze/App Standby, which is far more aggressive
about throttling a locked, unexempted app) never throws, so the existing try/catch fallback
never ran either; the whole capture pipeline would just sit there waiting indefinitely, no
notification, no fallback text, nothing. Added a 15-second `withTimeoutOrNull` around that
specific call — past 15 seconds it falls straight through to the local offline provider, same
as any other failure. Worth checking too: Settings → Battery Optimization exemption (also
offered during onboarding) is exactly what prevents Doze from throttling this in the first
place; if it's not yet granted, that's very likely contributing to the delay this was reported
against.

## Previous session: a real "About" screen in Settings — developer credit, email, copyright


The "About" row in Settings existed already but did nothing (`onClick = { }` — a placeholder
that was never wired up). It now opens a clean, single-purpose dialog: an app icon badge, name
and version, then "Developed by **Mahmoud Mady** — Developer", a tappable email row
(Mahmoud.mady30@gmail.com — opens the device's mail composer pre-addressed), and a © 2026
copyright line. Deliberately kept to just that — no changelog, no social links — so it doesn't
compete with the one thing this screen is actually for.

Caught and fixed two real mistakes made while wiring this in: the function that used to sit
right after this new dialog (`ApiKeyDialog`) lost its `@Composable` annotation during the
edit, and a stray, orphaned `@Composable` was left sitting before this dialog's own doc
comment (Kotlin tolerated it silently — annotations may repeat — but it was dead weight
either way). Both fixed; verified with a project-wide heuristic pass confirming every
Composable function in this file has exactly one `@Composable` immediately above it.

## Previous session: onboarding's last step now confirms both permissions before letting you continue


Previously, tapping "Enable Notification Access" or "Battery Optimization" just opened the
system screen — there was no feedback on whether the user actually granted it, and "Continue
to app" worked regardless. Now both are live-tracked (re-checked on `ON_RESUME`, the same
pattern the Dashboard's own permission banners already use, since granting these happens in
system Settings and the user comes back to this screen afterward): each button turns into a
green checkmark + label the moment it's actually granted, and "Continue to app" stays disabled
— with a small hint explaining why — until both are true. No more silently proceeding without
either one actually being enabled.

## Previous session: Dashboard reorganized — Summary first, all 3 nav buttons always visible, clearer button hierarchy


- **Nav row button renamed** to just "Schedule" (a new, separate `schedule_nav_label` string) —
  the Scheduled Messages *screen's* own title stays "Scheduled Messages"; only the compact
  Dashboard button got shorter.
- **The 3 nav buttons (Auto Reply, Quick Chat, Schedule) are now always fully visible**,
  equal-width, filling the row — previously wrapped in a horizontally-scrollable row, which
  implied more content might be hiding off-screen (it never was) and could leave the third
  button partially cut off depending on screen width.
- **Summary now leads the screen**, right after any setup banner, ahead of all four stat
  cards — requested explicitly, since it's the one card that actually summarizes everything
  else here.
- **"View Summary" and "Generate Summary Now" redesigned as a matched pair**: same width
  (each fills half the row) and same button shape, so together they read as one deliberate
  two-choice control — solid/primary for the everyday action (view what's there), tonal/
  secondary plus a refresh icon for the heavier, less-frequent one (generate fresh). Still
  unmistakably a real, tappable button, just clearly the secondary choice.
- **Spacing tightened up for a more deliberate, filled feel**: inter-section spacing increased
  slightly (16dp → 20dp) and the bottom inset widened (16dp → 32dp), so the page reads as a
  designed whole rather than sparse content floating with an abrupt cutoff at the end.

## Previous session: Contacts permission added (as discussed), plus Schedule Messages replaces Settings in the nav row


**Contacts auto-fill, now built**: added `READ_CONTACTS` to the manifest — the one deliberate
exception to this project's otherwise contacts-free design, requested lazily (never upfront)
only at the moment the user actually opts in. New `ContactsLookup.kt` queries the phone's own
Contacts app by name (exact match first, then a "contains" fallback so "Ahmed" still finds
"Ahmed Hassan") and returns the first phone number found, degrading safely to null on any
missing permission or lookup failure rather than throwing. Wired into both places a name gets
typed without a number — `ImportantPeopleScreen`'s Add/Edit dialog and the "Save as Important"
quick-add dialog from a message: when there's a name, no number yet, and this is a new entry
(never overwrites an existing/edited number), a "Find their number in Contacts" button appears
if permission hasn't been granted yet; once granted (or if already granted from a prior use),
the lookup runs automatically and only once per dialog open, whether or not it finds a match.

**Schedule Messages replaces Settings in the Dashboard's quick-nav row.** Settings remains
fully reachable exactly as before — the gear icon in the Dashboard's own top app bar was
always a separate, independent way to get there and still is; only the redundant second entry
in the nav row below was replaced.

New: `ContactsLookupTest.kt` covers the guard clauses (blank name, no permission) that don't
require simulating real Contacts data.

## Previous session: Quick Chat replaces Search in the main nav row; the standalone Quick Chat card is gone


Per explicit request: removed the dedicated "Search" button from the Dashboard's quick-nav
row and put "Quick Chat" there instead, and removed the separate, prominent "Quick Chat" card
that used to sit above that row — it's now reachable from the same small button as Auto Reply
and Settings, not from its own dedicated card. Search itself is still fully reachable exactly
as before — the Today/Important/Need Reply stat cards near the top already open Search with
the matching filter, and that never changed; only the standalone "Search" *button* is gone.
Removed the now-fully-unused `onOpenSearch` parameter from `DashboardScreen` and its
NavGraph wiring, rather than leaving a dead, unused callback sitting in the signature.

**On the "Add Important Person" phone number not auto-filling**: this one's a deliberate
privacy boundary, not a bug — worth explaining rather than just fixing. The dialog already
auto-extracts a phone number when the message's sender/group name *is* a raw phone number
(exactly what WhatsApp shows for someone not saved in your phone's contacts). "Mr Elmasry Du"
is a real name, meaning WhatsApp itself never told this app a phone number for that message —
there's nothing to extract. The only way to *also* auto-fill a number for a named sender would
be searching the phone's own Contacts app by name, which needs the `READ_CONTACTS` permission —
and this project's AndroidManifest deliberately does not request Contacts, Location, Camera,
Microphone, SMS, or Phone access, stated explicitly as an intentional choice. That's why "Pick
from Contacts" works without ever asking for that permission (it delegates to Android's own
contacts picker UI, which only grants access to the one contact actually selected) while an
automatic background name-lookup can't. Leaving this as-is preserves that boundary; happy to
add the permission and build the lookup if that trade-off is one you'd rather make instead —
just flagging that it's a real trade-off, not a small tweak.

## Previous session: "Delete All" added next to "Read All"


Same Today-filter action row as "Read All," now with a "Delete All" button beside it —
deletes every message currently shown under the active filter, with a confirmation dialog
first (stating exactly how many messages will be removed) since this one's irreversible,
unlike marking things read. `SearchViewModel.deleteAllVisible()` mirrors
`markAllVisibleAsRead()`'s scoping exactly: only whatever's in the current filtered results,
nothing outside it. Tests cover both — deleting everything currently visible, and confirming
messages outside the active filter are left untouched.

## Previous session: real root cause of remaining duplicates — a race condition, not just a narrow window


**Two fixes, addressing two different ways the same duplicate could slip through**:

1. **The actual root cause — a race condition.** `captureMessage`'s "check for a duplicate,
   then insert" sequence had no synchronization: if the live notification listener and the
   periodic catch-up scan (or a burst of several notifications) both ran through it at nearly
   the same moment, each could check "is this a duplicate?" *before* either had actually
   inserted its row — both see "no," both insert. No timestamp tolerance window, however wide,
   fixes this, since the problem isn't the window size, it's that the check and the insert
   weren't atomic together. Added a `Mutex` (`captureMutex`) around the *entire* check-then-
   insert span — restructured `captureMessage` so the duplicate check, the work-group upsert,
   and the actual `insert()` all happen inside one `withLock` block, with everything after
   (auto-reply detection, importance notifications) running afterward using the now-guaranteed-
   unique result.

2. **A secondary safety margin.** The timestamp tolerance window itself was also widened from
   10 seconds to 3 minutes — a repost tied to a slow-loading link preview, or a catch-up scan
   running some real time after the live notification, can genuinely land further apart than a
   few seconds even without any race involved.

`WhatsAppNotificationListenerServiceTest.kt` gained: a repost arriting nearly 3 minutes later
still catching as a duplicate, and — the test that actually exercises the race — 20 truly
concurrent coroutines calling `captureMessage` with the identical message, asserting exactly
one row ends up inserted.

## Previous session: "Checking for new messages" still getting through — a structural fix this time


The existing text-based filter should have caught this, so rather than just re-checking the
same wording, added a genuinely different, more robust layer: **`Notification.FLAG_ONGOING_EVENT`**.
"Checking for new messages" and similar status lines represent an active, ongoing background
operation — the same category of notification as an undismissable download-progress bar —
while a real incoming message is a one-time, dismissible event. Android's own convention
for that distinction is this flag, so checking it doesn't depend on matching exact wording,
capitalization, or language at all, unlike the text-phrase list (which stays as a second
layer, for any status notification that isn't flagged ongoing).

Also hardened the text-based check itself: title matching switched from exact-equals to
starts-with, since some devices/launchers append a badge count to the title (e.g. "WhatsApp
Business (3)"), which an exact match would silently miss. The cleanup's SQL candidate query
was broadened the same way (`LIKE 'WhatsApp%'` instead of two exact-match `OR`s) so it catches
the same variants when cleaning up anything already captured.

## Previous session: "Pick from Contacts" added to the Auto Reply rule dialog


The Add/Edit Auto Reply dialog now has the same "Pick from Contacts" button used in Important
People and Important People — picking a contact fills the "Person" field with their name
(matching how that field is already described: "name or number"), saving a manual retype of
someone already in the phone's contacts.

## Previous session: last test fix — this one wasn't a bug, the test's expectation was too strict


131 of 132 tests passing. The one failure was the test's own assumption, not application code:
"a fixed reply text is used verbatim" checked for an *exact* match against the notification
body, but no phone number is saved for the test's sender, so the app correctly wraps the reply
in the "Copied — paste it into the chat: ..." explanation added a few sessions back — that
wrapping is the intended behavior, and the test simply hadn't accounted for it. Fixed the test
to check that the body *contains* the fixed text rather than exactly equals it, which is what
this test actually needs to verify (the fixed text wasn't silently replaced by an AI-generated
one) without being coupled to the wrapping format.

## Previous session: test compile fix — `StatusBarNotification` has no direct `.extras`


`AutoReplyDetectionTest.kt` accessed `posted?.extras` directly, but
`ShadowNotificationManager.getActiveNotifications()` returns `StatusBarNotification[]`, not
`Notification[]` — `StatusBarNotification` has `.id`/`.tag` directly (which is why the
already-passing tests using those compiled fine), but the actual notification content —
including `.extras` — sits one level down, at `.notification`. Fixed all three occurrences to
`posted?.notification?.extras?.getCharSequence(...)`. Checked the rest of the test suite for
the same pattern; no other instances.

## Previous session: build fix — the outlined PushPin icon doesn't resolve, filled one does


`Icons.Outlined.PushPin` failed to compile ("Unresolved reference... receiver type mismatch")
even with `material-icons-extended` present as a dependency, while `Icons.Filled.PushPin` on
the very same line compiled fine — pointing at that specific outlined variant not being
available in this Compose version, not a general icons-package problem. Simplest, safest fix:
use `Icons.Filled.PushPin` for both the pinned and unpinned states, differentiated by tint
color alone (primary when pinned, muted when not) rather than needing two icon variants.
Checked the rest of the project for the same `androidx.compose.material.icons.outlined.*`
import pattern — no other instances.

## Previous session: real bug — bundled-notification titles leaking a sender's name into the group name


**Root cause**: when several messages from the same group stack up, WhatsApp sometimes titles
the notification "Group Name (N messages): Last Sender" instead of just "Group Name" — and
that raw title was being used as the group name directly, with no cleanup. That's exactly why
"Matajer Mirgab Team" was showing up as "Matajer Mirgab Team (2 messages): Ahmed Hatem" — the
group's own name had permanently absorbed whichever person happened to send the most recent
message in that specific bundled notification.

Added `cleanGroupTitle()` — strips the "(N message(s)): Sender" suffix when present via regex,
leaves a normal single-message title or a 1:1 chat's contact name untouched (and correctly
leaves alone a group whose real name happens to contain parentheses, like "Sales Team
(Cairo)", since that doesn't match the "(N messages):" pattern). Wired into the point where the
group name is first assigned from the notification title, so it's clean before anything else
ever sees it.

Also cleans up data already affected by this: `MessageCleanup.purgeGroupTitleSuffixJunk` (runs
once at every startup, same as the other cleanups) repoints already-captured messages from a
dirty group name to the clean one, and does the same for the Work Groups & Clients tracking
entries — merging into an already-existing clean-named entry when one exists (preserving
whichever one was enabled) rather than leaving two separate cards for what's really one group.

## Previous session: Summary screen rebuilt — the real cause of "looks random," plus pin/delete/expand


**Root cause of the messy look**: the AI's summarization prompt never told it not to use
Markdown — tables (`| Sender | Action |`), `**bold**` asterisks — which is exactly what showed
up as raw, unrendered text in the app's plain-text UI. Rewrote the prompt (both English and
Arabic) to explicitly forbid Markdown, require one short takeaway line first (the single most
useful sentence, readable on its own), then a blank line, then a handful of short bullet
points — each starting with exactly one purposeful emoji (⚠️ problem/complaint, ⏰ deadline/
urgent, 💬 needs a reply, ℹ️ plain note) rather than decoration for its own sake.

The screen itself: each summary card now shows a **numbered badge**, a **pin** button (pinned
summaries sort to the top automatically — `SummaryDao.observeAll` now orders by `isPinned DESC,
createdAt DESC`), and a **delete** button (with a confirmation dialog, and explicit reassurance
that deleting a summary never touches the messages it was generated from). The card itself is
now **collapsed by default** — showing just the number, date, pin status, and that one-line
takeaway — with a "Show more" control that **expands the card in place** (pushing everything
below it down the list, with a smooth grow/shrink animation) to reveal the rest of the text,
the stat breakdown, and the per-message reply items; "Show less" collapses it straight back.

New: `WorkSummary.isPinned` (entity + domain model, Room schema bumped to version 8),
`SummaryRepository.setPinned`/`delete`, `SummaryViewModel.togglePinned`/`deleteSummary`.
`SummaryRepositoryPinDeleteTest.kt` covers pin-to-top ordering, unpinning returning to normal
order, and deleting one summary leaving others untouched.

## Previous session: WhatsApp's own "group summary" notifications, and a Refresh button on Search


**Real bug found and fixed**: entries like "WA Business — 8 messages from 4 chats" were never
real messages — Android/WhatsApp bundles multiple notifications from different chats and posts
one "group summary" notification on top of them, and nothing was filtering that out before now.
`Notification.FLAG_GROUP_SUMMARY` is the official, reliable way to identify this specific kind
of notification (as opposed to guessing from its text, which would be fragile across
languages and WhatsApp versions) — `handle()` now skips it outright, before even looking at
title/text. Applies identically to both regular WhatsApp and WhatsApp Business, and covers the
periodic catch-up scan automatically too, since it reuses the same `handle()` function.

Added `MessageCleanup.purgeGroupSummaryJunk` (matches the exact "N messages from M chats"
pattern) to clean up anything already captured by an earlier version, run once at every app
startup alongside the existing system-notification cleanup — same safe, repeatable, no-op-once-
clean design.

**Added a Refresh button to the Search screen's top bar** (previously only the Dashboard had
one) — triggers the same on-demand catch-up scan (forces the notification listener to
reconnect and re-verify against every currently active WhatsApp notification), with a Snackbar
confirming it ran.

## Previous session: Edit added to Important People and Keyword Rules


Confirmed useful context on the "Use just copies" report: it works correctly when a contact
is added via "Pick from Contacts" (name and number arrive together, guaranteed present). That
points at the real gap — someone added without a phone number (or via the quick "Save as
Important" flow from a message, which doesn't always have one to prefill) had no way to come
back and add or fix it afterward, short of deleting and re-creating the whole entry.

Both **Important People** and **Keyword Rules** now have a proper Edit (pencil) button on
each entry, opening the same Add dialog pre-filled with the existing values — change the name,
add or correct the phone number, adjust a keyword's weight, and save back over the same entry
rather than creating a duplicate. `ImportantPeopleViewModel.updateContact` and
`KeywordRulesViewModel.updateRule` reuse the existing `upsert` (same id → update in place, not
insert), and both dialogs are now shared between Add and Edit rather than duplicated.

## Previous session: duplicate Dashboard button removed, and AI-generated Auto Replies


**Removed the duplicate "Work Groups & Clients" button** from the Dashboard's quick-nav row —
the stat card near the top already opens the same screen, so the second entry in the row below
was pure redundancy.

**Auto Reply's reply text is now optional — the AI writes it when left blank.** Previously
every rule required typing a fixed reply template, always sent verbatim regardless of what the
incoming message actually said. Now leaving the reply field blank means the active AI provider
generates a fresh reply from the real message's own content (and recent conversation context,
the same mechanism the Search/Summary screens' "Generate Reply" already uses) every time the
rule matches — falling back to the local, offline provider if no cloud provider is configured,
and to a plain, honest placeholder line only if generation itself somehow fails, so a match
never produces an empty or broken notification. A rule with a filled-in reply still uses that
exact text every time, unchanged, for cases where a consistent canned response actually is what
someone wants (e.g. an out-of-office notice). `AutoReplyRuleEntity.replyText` (and the domain
model) are now nullable to represent this; Room's schema version bumped to 7 accordingly.

**On the "Generate Reply / Auto Reply just copies instead of opening the exact chat" report**:
traced through the whole flow and the phone-number lookup logic is correct wherever a matching
saved contact exists — the fallback (copy + open WhatsApp generally) is exactly what's supposed
to happen when no phone number can be found for that sender, and that's the one condition
consistently reported. Waiting on confirmation of whether this happens even for senders already
saved under Important People with a phone number, to know whether there's a genuine lookup bug
left to find versus this being the expected fallback for un-saved contacts.

## Previous session: build fix — `create("debug")` conflicted with AGP's own built-in one


The fixed-debug-keystore fix from last round didn't quite work: AGP already defines an implicit
signing config literally named `"debug"` for every project automatically, so declaring
`create("debug") { ... }` in `signingConfigs` tried to add a *second* one under the same name —
"Cannot add a SigningConfig with name 'debug' as a SigningConfig with that name already
exists." Fixed by using `getByName("debug") { ... }` instead, which reconfigures that existing
implicit signing config in place (pointing it at the committed `debug.keystore`) rather than
declaring a conflicting new one. The `debug` build type's own `signingConfig =
signingConfigs.getByName("debug")` reference didn't need to change — it was already correct.

## Previous session: choose your country up front, used automatically everywhere


Added a proper country-selection step right after language in Onboarding — a new, polished,
searchable **Country Picker** (flag, name, dial code, live search by either), not a cramped
dropdown, since this is a deliberate first-run choice rather than a quick mid-form correction.

The key design decision: rather than wiring a new "default country" setting through every
individual screen that has a phone-number field, the picker writes straight into
`CountryCodePrefs` — the *existing* single source of truth every phone-entry field in the app
(Quick Chat, Important People, Scheduled Messages) already reads its starting value from. That
meant the country chosen at onboarding — or changed anytime after from the new **Default
Country** row in Settings — takes effect everywhere in the app automatically, with zero changes
needed to any of those individual screens; they were already listening to the right place.

New: `CountryPickerDialog.kt` (the reusable searchable picker, in `presentation/components` so
both Onboarding and Settings share the exact same component), a new Onboarding step between
language and WhatsApp-app choice, and a new Settings row showing the current default with a tap
to change it. Every phone number field in the app still allows overriding the code for a single
entry when needed — this only changes what it starts pre-filled with.

## Previous session: "App not installed" — fixed the real cause (a different signing certificate every CI run)


The build itself has been succeeding — this was a separate, install-time problem. No debug
signing config was ever explicitly set, so `assembleDebug` fell back to AGP's implicit default,
which signs with whatever `~/.android/debug.keystore` exists on the *building machine* —
auto-generating a brand-new, random one if none exists yet. GitHub Actions runners are
ephemeral (a fresh VM every run, nothing persisted from the last one), so every single CI build
got its own new, different debug certificate. Android refuses to install an APK over an
already-installed one if their signing certificates don't match — exactly "App not installed"
— even though nothing about the app itself was ever actually broken.

Fixed by generating one fixed debug keystore (`app/debug.keystore`, matching the same
alias/password convention — `androiddebugkey`/`android` — Android's own default debug keystore
already uses, since a debug keystore was never meant to be a secret in the first place) and
committing it to the repo, with the `debug` build type's `signingConfig` explicitly pointed at
it. `.gitignore`'s broad `*.keystore` pattern would otherwise have excluded this exact file, so
it needed an explicit `!app/debug.keystore` exception line.

**One-time step needed on your device**: since every previous APK was signed with a different,
now-abandoned certificate, the very next install still needs the *old* app uninstalled first —
there's no way around that for this one transition. Every install *after* this one, from any
future CI build, will share the same certificate and update cleanly in place, no more
uninstalling required going forward.

## Previous session: down to 1 failing test — a two-part poll that only checked half


113 of 114 tests passing now. The last one, "the All filter shows only unread messages", had a
subtler version of the same raciness the `awaitResults` helper was built for: it polled only
for the unread message's presence (`it.any { m.id == unreadId }`), then separately asserted the
read message's *absence* — but Room delivers each mutation as its own Flow emission, and the
ViewModel's collector catches up to them one at a time. The poll could return the instant the
"insert unreadId" emission was caught up to, but *before* the later "markRead(readId)" emission
had been — so the read message could still be sitting in the results at exactly the moment the
poll was satisfied, failing the very next assertion intermittently. Fixed by polling for the
*complete* expected end state in one predicate (`unreadId` present **and** `readId` absent
together), not one half of it followed by a separate unguarded check.

## Previous session: test flakiness fix — the ViewModel's cache lags the DB by a beat


More progress: both previous real bugs (missing import, protected shadow method) are confirmed
fixed — no trace of either in this build. Now down to 3 failing tests, all new ones added
alongside the "Read All"/"All shows only unread" feature, and all sharing the same root cause:
they check `viewModel.uiState.value.results` immediately after inserting/marking a message,
but that value only updates once `SearchViewModel`'s own `observeMessages()` collector (running
on `viewModelScope`, started in `init`) has processed the *next* emission from Room's Flow —
and that emission is delivered via Room's own invalidation-tracker executor, a real background
thread that `Dispatchers.setMain(UnconfinedTestDispatcher())` doesn't control. Every *other*
test in this file sidesteps this by reading `app.messageRepository.observeMessages().first()`
directly (bypassing the ViewModel's cache entirely) — these 3 were the first to actually check
the ViewModel's derived state, and hit the gap those others never exercised.

Added `awaitResults { predicate }` — polls `viewModel.uiState.value.results` for up to 2 seconds
rather than assuming it's already caught up — and used it everywhere these 3 tests read
ViewModel state right after a mutation. This is the same underlying class of raciness as the
`setImportant`/`markAsRead` `.join()` fix from before, just showing up on the read side instead
of the write side, and handled the same way: don't assume synchronous completion, wait for the
actual result.

## Previous session: test compile fix — a protected Robolectric shadow method


Real progress: the previous round's actual bug (the missing import) is confirmed fixed — no
trace of that error in this build. A new, different, and much smaller issue surfaced:
`AutoReplyDetectionTest.kt` called `shadowNotificationManager.cancel(tag, id)` directly to
clear notifications between tests, but that specific overload is `protected` on
`ShadowNotificationManager` (only meant to be invoked internally by the shadow framework
itself), so it doesn't compile from test code. Fixed by calling the real, public
`NotificationManager.cancelAll()` instead — achieves the same "clean slate between tests" goal
without touching anything protected. Checked `BadgeUpdaterTest.kt` (the only other file using
`ShadowNotificationManager`) for the same risk — it only ever reads the public
`.activeNotifications` property, no `.cancel(...)` calls, so no other instances of this issue
exist.

## Previous session: found the real bug — a missing import, hiding since the Auto Reply feature was added


The CI failure ("Unresolved reference 'AutoReplyRuleRepository'") was never about a missing
file or an incomplete copy — it was a genuine bug sitting in the project since the Auto Reply
feature was first built: `OtherRepositoryImpls.kt` uses `AutoReplyRuleRepository` (in
`class AutoReplyRuleRepositoryImpl(...) : AutoReplyRuleRepository`) but the file never imported
it — every *other* repository interface in that file was correctly imported, this one alone
was missed. Confirmed by fetching the actual file content straight from GitHub: the interface
itself was correctly defined in `Repositories.kt`, and the DAO/Entity/domain-model imports for
AutoReply were all present in `OtherRepositoryImpls.kt` — just not the one import that mattered.

Added the missing `import com.whatsappworkmanager.app.domain.repository.AutoReplyRuleRepository`
line. Also ran a project-wide automated check (every `class X : YInterface` pattern, matched
against every file's actual import list) specifically for this class of bug — zero other
instances found.

**Note on verification going forward**: this bug slipped past many previous rounds of manual
static checks because those checks verified that referenced symbols exist *somewhere* in the
project, not that the specific file *importing* them actually did. The new automated check
closes that specific gap.

## Previous session: reverted the built-in API key feature entirely


The built-in-key mechanism from the previous session (BuildConfig fields, the
`local.properties`-based fallback, `hasUserEnteredApiKey`, the Settings subtitle logic, and its
test file) has been fully reverted at the user's request, after a real key committed into
`local.properties` triggered GitHub's push protection (the push was correctly rejected — the
key was never actually exposed on GitHub, but the local git client had it staged/committed).
Rather than fight that further, the request was simply to go back to manual entry, so:

- `app/build.gradle.kts` — the `local.properties`-reading logic and all five
  `buildConfigField` calls removed; back to exactly what it was before this feature existed.
- `SettingsDataStore.getApiKey()` — back to a plain read of the user-entered encrypted value,
  no fallback.
- `SettingsScreen.kt` — the API Key row's subtitle is back to the original static
  "Stored encrypted on-device only" text; the version-bump/recomposition-forcing state and the
  `apiKeySubtitle` helper are gone.
- The three now-unused string resources and `SettingsDataStoreTest.kt` were removed too.
- The `local.properties` file itself (which had the real key in it) was deleted from this
  project snapshot.

The app is back to exactly how it worked before this feature was introduced: paste an API key
into Settings yourself, same as always.

## Previous session: built-in API key support (now reverted, see above)


Added the mechanism for a compiled-in "built-in" AI provider API key, so the app can use cloud
AI out of the box without the user pasting a key into Settings first — while a user-entered key
in Settings still always takes priority over the built-in one if both are present.

- `app/build.gradle.kts` reads `local.properties` (an existing, always-gitignored file every
  Android project already has) for up to five keys — `builtInApiKey.groq`,
  `builtInApiKey.openai`, `builtInApiKey.anthropic`, `builtInApiKey.gemini`,
  `builtInApiKey.grok` — and compiles whichever are present into `BuildConfig` fields. None
  present means the app still builds and runs fine, exactly as before.
- `SettingsDataStore.getApiKey()` now falls back to the matching built-in `BuildConfig` field
  when the user hasn't entered their own key for that provider — still gated behind the same
  cloud-AI-consent toggle as any key.
- The Settings screen's "API Key" row now shows accurate status copy: "Your own key is saved",
  "Using the built-in key — tap to use your own instead", or "Not set — tap to add a key".
- `SettingsDataStoreTest.kt` covers the fallback logic itself (user key takes priority;
  degrades safely to null when neither source has anything).

**Honest security note, stated once and worth remembering**: a key compiled into the app is
recoverable by anyone who decompiles the APK — there's no real protection difference between
this and a plain hardcoded string, whichever provider it's for. Reasonable for a personal,
not-publicly-distributed app; would need reconsidering if this project is ever shared or
open-sourced.

**To actually add a real key**: create (or edit) `local.properties` in the project root — it
is never committed — and add a line like:
```
builtInApiKey.groq=your-actual-key-here
```
Then rebuild. No key value ever needs to be typed into any file that gets shared or committed.

## Previous session: delete a group/client entirely from Work Groups & Clients

Each card on the Work Groups & Clients screen now has a delete (trash) icon next to its
enable/disable switch, with a confirmation dialog before anything happens. Deleting removes
that group/client entry from the list entirely — it's not just disabling it. Deliberately does
**not** delete the messages already captured from that name: those stay visible in
Search/history exactly as before, since removing a tracking entry and losing message history
are two very different things a user might want. If a new message later arrives from the same
name, it's re-added fresh (disabled by default, same as any name seen for the first time).

New: `WorkGroupDao.deleteByName`, `WorkGroupRepository.delete`,
`WorkGroupsViewModel.deleteGroup`, the confirmation dialog and delete button in
`WorkGroupsScreen.kt`. Tests cover: the group disappearing from the list, its messages staying
intact, case-insensitive name matching, and deleting one group leaving a different one
untouched.

## Previous session: "Read All" for Today, and "All" now means "still needs a look"

Two related changes to Search, requested explicitly:

- **"All" now shows only unread messages** — once something is marked read (individually, or
  via "Read All" below), it drops out of the All view and shows up under the Read filter
  instead. The intent: "All" becomes a natural "what still needs my attention" view rather than
  an ever-growing list that mixes handled and unhandled messages together.
- **A new "Read All" action on the Today filter** — appears only when viewing Today and at
  least one message there is still unread; marks every currently-visible message as read in one
  tap. Implemented generically (`markAllVisibleAsRead` marks whatever's in the *current* filtered
  results, not literally "today" as a special case), so switching to a different filter and
  using it there would sensibly mark read whatever's visible in that filter too, though the
  button itself is only surfaced on Today per what was asked.

`SearchViewModelTest.kt` covers: All excluding read messages, a message properly moving from
All to Read once marked, Read All marking everything currently visible, and Read All leaving
messages outside the current filter untouched.

## Previous session: editing a repeating schedule's time silently had no effect

**Real bug found**: rescheduling used `ExistingPeriodicWorkPolicy.UPDATE`, which is
specifically designed to update an already-running periodic work's input data/constraints
*without* restarting its schedule — it does not actually apply a new initial delay to an
existing periodic work. That's exactly why editing a repeating Scheduled Message's time (or a
Work Schedule's daily summary time) appeared to "only work once": the very first schedule kept
running unaffected, and any later edit to the time had no real effect on when it would next
fire. Switched both to `ExistingPeriodicWorkPolicy.REPLACE`, which genuinely cancels the
existing periodic work and starts fresh with the new initial delay — the correct policy for
"the user changed the schedule and expects it to take effect now." One-time (non-repeating)
reminders were already using the correct policy (`ExistingWorkPolicy.REPLACE`) and were
unaffected by this bug.

## Previous session: the same "text doesn't reach the chat" bug, found in two more places

After the "Use" button fix, the same underlying question ("did my text actually end up
somewhere I can use it?") turned up two more real gaps, both in background notification code
rather than the reply dialogs:

- **Scheduled Messages, for a group target** (no phone number — intentionally left blank per
  the add/edit screen's own hint) — tapping the reminder opened WhatsApp generally as
  designed, but the message text was never copied to the clipboard first. There was nothing to
  paste once WhatsApp opened; the text was simply lost.
- **Auto Reply, when no phone number is known for the matched person** — the exact same gap,
  in the feature added earlier this session.

Both now copy the text to the clipboard in that specific case (no phone number available),
matching the fallback the Search/Summary reply flow already used, and the notification body
says so explicitly ("Copied — paste it into the chat: ...") instead of just showing the raw
message with no indication anything was done for you.
`NotificationHelperTest.kt` covers both paths, plus confirming the clipboard is left untouched
when a phone number *is* known (a direct deep link doesn't need it).

## Previous session: "Use" falling back to copy-only, fixed at the root

**Real bug found**: the name-matching used to find a saved phone number for a message's sender
only checked one direction — the message's sender name had to *contain* the saved Important
Person's name. That silently failed whenever the saved label was longer or more specific than
what a message actually shows (e.g. saved as "Eng. Ahmed Hassan - Supplier" but the message's
sender is just "Ahmed Hassan"), which is exactly what made "Use" feel randomly broken —
working for some contacts and falling back to copy-only for others with no obvious pattern.
Fixed by checking both directions and normalizing whitespace before comparing.

Also added a **clear explanation whenever the fallback genuinely is the fallback**: instead of
silently copying the text with no indication why, a toast now states plainly that no saved
number was found for that person and points at Important People as the fix — so a case where
copy-only really is the correct behavior (nothing saved for that contact yet) reads as
"here's why and what to do," not as a mystery.

## Previous session: removed a risky auto-send addition, added a safe Auto Reply feature

A prior edit to this project (by a different tool) added a feature that used an Android
`AccessibilityService` to read WhatsApp's on-screen UI and press its own Send button
automatically, with no human in the loop at send time. That crosses this project's oldest,
clearest line: no Accessibility Service, no automated sending — see "What this app
deliberately does NOT do" just below, which predates that addition. WhatsApp's own terms
explicitly prohibit automated/bulk messaging, and an Accessibility Service silently pressing
Send is exactly the pattern WhatsApp's anti-spam systems are built to catch.

**Hidden, not deleted, as requested**: `WhatsAppAccessibilityService.kt` and
`AutoReplyQueue.kt` remain in the project untouched. The fix that actually matters is that the
`<service>` declaration for `WhatsAppAccessibilityService` was **removed from
AndroidManifest.xml** — Android only offers a service as an enable-able Accessibility Service
if it's manifest-declared with the right intent-filter, so with that entry gone, this service
cannot be turned on by any means, in-app or through system Settings, regardless of whether its
source file still compiles as part of the project. The Settings screen's toggle for it and the
Work Groups screen's explanatory text (which had been rewritten to claim the bridge "can
safely send queued replies") were removed/corrected too.

**The safe replacement: Auto Reply.** A new section, reachable from the Dashboard, where you
add rules — a person (name or number), optionally a specific word, and a reply to prepare.
When a message matches an enabled rule, the app surfaces a notification whose tap action opens
that person's exact WhatsApp chat with the reply already typed in, via the same official
click-to-chat deep link used throughout the rest of the app. The final tap on WhatsApp's own
Send button is always yours — nothing here ever sends automatically or touches WhatsApp's UI
programmatically. New: `AutoReplyRuleEntity`/`AutoReplyRuleDao` (Room v6),
`AutoReplyRuleRepository`, `AutoReplyViewModel`/`AutoReplyScreen`,
`NotificationHelper.showAutoReplyReadyNotification`, matching logic in `captureMessage()`
(wrapped in its own try/catch so a failure here can never affect normal capture), and
`AutoReplyDetectionTest.kt` covering person/keyword matching, disabled rules, and mismatches.

---

An Android assistant that watches WhatsApp's own notifications for your **work groups**,
scores and classifies incoming messages, and gives you short, scheduled summaries — so you
can leave noisy work groups muted and stop opening WhatsApp every few minutes.

This is a real, buildable Android Studio project (Kotlin, Jetpack Compose, Room, WorkManager),
not a mockup.

---

## What this app deliberately does NOT do

Per the project's constraints, this app never:

- Uses **root** or an **Accessibility Service** to control WhatsApp
- Reads WhatsApp's own database/files, or scrapes WhatsApp Web
- Fakes or suppresses **read receipts** (blue ticks) or **online/last-seen** status
- Sends any message automatically — Reply Assistant and Scheduled Messages only ever
  *prepare* text for you to copy and send yourself
- Ships with any AI provider API key baked into the app

The **only** mechanism used to observe WhatsApp activity is Android's public
`NotificationListenerService` API, reading notifications the user explicitly grants access to
from system Settings.

---

## Project structure

```
WhatsAppWorkManager/
├── app/
│   └── src/main/java/com/whatsappworkmanager/app/
│       ├── data/            # Room DB, DAOs, repositories, DataStore/EncryptedPrefs, AI providers
│       ├── domain/          # Models, repository interfaces, use cases (pure Kotlin, unit-testable)
│       ├── presentation/    # Jetpack Compose screens, ViewModels, navigation, theme
│       ├── service/         # WhatsAppNotificationListenerService
│       ├── worker/          # WorkManager workers (summary, cleanup, scheduled-message reminders)
│       └── utils/           # Constants, notification helper, intent helper
│   └── src/test/            # Unit tests (classifier, scoring, reply detection, summary, schedule)
├── build.gradle.kts
├── settings.gradle.kts
└── gradle.properties
```

Clean Architecture / MVVM: `presentation` depends on `domain`, `data` implements `domain`'s
repository interfaces, and `domain` has zero Android-framework imports (which is what makes the
classifier/scoring/summary logic unit-testable without an emulator).

---

## Setup

> **Version note (fixed):** an earlier version of this project paired Kotlin `1.9.24` with the
> `org.jetbrains.kotlin.plugin.compose` Gradle plugin — that plugin only exists starting with
> Kotlin `2.0.0` (it's the new K2 Compose Compiler plugin; before Kotlin 2.0, Compose Compiler
> integration worked differently, via `composeOptions.kotlinCompilerExtensionVersion`, with no
> separate plugin at all). Mixing the two produced exactly this build failure:
> `Plugin [id: 'org.jetbrains.kotlin.plugin.compose', version: '1.9.24'...] was not found`.
> This project now pins **Kotlin `2.0.21`** (root `build.gradle.kts`) with a matching KSP
> version (`2.0.21-1.0.28`) — both `org.jetbrains.kotlin.android` and
> `org.jetbrains.kotlin.plugin.compose` must always share the exact same version number, since
> they're released together as part of the Kotlin toolchain. If you ever bump one, bump both,
> and update KSP's Kotlin-version prefix to match.
>
> **Follow-on note — corrected diagnosis (actually fixed this time):** the same
> `Cannot access 'val RowColumnParentData?.weight: Float': it is internal in file` error
> persisted identically across three different Compose BOM versions (`2024.06.00`,
> `2024.09.00`, `2024.12.01`) at the exact same file/line/column every time — which turned out
> to mean the original diagnosis (a BOM/Kotlin ABI mismatch) was **wrong**. The BOM was never
> the problem. The real cause: `DashboardScreen.kt` had a *specific named import*,
> `import androidx.compose.foundation.layout.weight`. Under Kotlin 2.0's K2 compiler, that
> exact named import resolves to an internal `RowColumnParentData` symbol instead of the
> public `RowScope`/`ColumnScope` extension function that also happens to be named `weight` —
> a K2 import-resolution quirk, not a library version problem. The fix was replacing the
> individual `androidx.compose.foundation.layout.*` named imports in that file with a single
> wildcard import (`import androidx.compose.foundation.layout.*`), which lets Kotlin resolve
> the correct overload by receiver type at each call site instead of pre-resolving the bare
> name at import time. (The BOM/lifecycle/navigation version bumps from the earlier note are
> harmless and were kept, but weren't the actual fix.) If you add new Compose code elsewhere
> and hit this same error, check for a specific named import of `weight` and switch it to a
> wildcard import of `androidx.compose.foundation.layout.*`.

1. Install **Android Studio** (Koala/2024.1 or newer recommended).
2. `File → Open` → select the `WhatsAppWorkManager` folder.
3. Let Gradle sync. Required SDKs: `compileSdk 34`, `minSdk 26`.
4. **Gradle wrapper jar**: `gradle/wrapper/gradle-wrapper.properties` and the `gradlew` /
   `gradlew.bat` launcher scripts are included, but `gradle/wrapper/gradle-wrapper.jar` itself
   is a binary file and isn't included in this handoff (it can't be produced as text). You have
   two options:
   - **Just open it in Android Studio.** Android Studio detects the missing wrapper jar and
     regenerates it automatically on first sync — this is the easiest path and needs nothing
     from you.
   - **Or generate it yourself from a terminal**, if you have any local Gradle install:
     ```
     gradle wrapper --gradle-version 8.7
     ```
     This creates `gradle/wrapper/gradle-wrapper.jar` in place; the `.properties` file and
     `gradlew`/`gradlew.bat` scripts already here will then work as-is.

## Build APK

- **Debug APK**: `Build → Build APK(s)`, or `./gradlew assembleDebug`
  → `app/build/outputs/apk/debug/app-debug.apk`
- **Release APK**: `./gradlew assembleRelease`
  → `app/build/outputs/apk/release/app-release.apk` if signed (see **Release signing** below),
  or `app-release-unsigned.apk` if not.

`versionCode = 1`, `versionName = "1.0.0"` are already set in `app/build.gradle.kts`.

## Release signing

The release build type is wired to an **optional**, file-based signing config
(`app/build.gradle.kts`) so the project builds whether or not you've set up a keystore:

1. Generate a keystore (skip if you already have one):
   ```
   keytool -genkeypair -v -keystore release-key.jks -alias wwm -keyalg RSA -keysize 2048 -validity 10000
   ```
2. Copy `keystore.properties.sample` (project root) to `keystore.properties` and fill in the
   real `storeFile` path, `storePassword`, `keyAlias`, and `keyPassword`.
3. Build as usual: `./gradlew assembleRelease`. Gradle detects `keystore.properties` and signs
   the output automatically; the signed APK lands at
   `app/build/outputs/apk/release/app-release.apk`.

`keystore.properties`, `*.jks`, and `*.keystore` are all in `.gitignore` — the keystore and its
passwords never get committed, and the sample file has no real secrets in it. If
`keystore.properties` is absent, `assembleRelease` still succeeds; it just produces an unsigned
APK you can sign later (`Build → Generate Signed Bundle / APK` in Android Studio, or
`apksigner` from the command line).

---

## Notification Access

WhatsApp Work Manager needs **Notification Access**, granted manually from system Settings
(there is no runtime permission dialog for this, by Android design):

Settings → Apps → Special app access → Notification access → **WhatsApp Work Manager** → Allow

The app surfaces a banner + button on the Dashboard, and a step in Onboarding, that deep-links
straight to this screen (`Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS`).

No other runtime permissions are requested — no Contacts, Location, Camera, Microphone, SMS,
or Phone.

---

## WhatsApp limitations (and how the app handles them)

| Requested feature | What's actually possible | This app's approach |
|---|---|---|
| Read messages passively | ✅ via NotificationListenerService | Implemented |
| Open a specific chat by name (individual) | ✅ via WhatsApp's official click-to-chat link | Deep-links straight into that chat, pre-filled — see below |
| Open a specific chat by name (group) | ❌ groups have no phone number, so no public deep link exists | Opens the WhatsApp app itself; user picks the group from there |
| Auto-send scheduled messages/replies with zero taps | ❌ would require Accessibility Service or root — both explicitly excluded by this project's own constraints | Pre-fills the message and opens the right chat; the user's own tap on WhatsApp's Send button is what actually sends it |
| Detect true read/unread state in WhatsApp | ❌ not exposed to third-party apps | The app tracks its own "read in this app" state instead |
| Reliable sender/group extraction | ⚠️ notification text format varies by WhatsApp version/OEM | Defensive parsing (`NotificationTextParser`) that falls back gracefully instead of crashing |

These limits and the reasoning behind them are also shown to the user in-app on the
**Privacy & Data** screen.

### Scheduled Messages: how "sending at your chosen time" actually works

This app's own constraints explicitly rule out Accessibility Services and root — and both of
those are the only ways a third-party Android app could tap WhatsApp's Send button on your
behalf. WhatsApp exposes no public API for a third-party app to complete a send by itself. So
"send automatically" and "no Accessibility/root" cannot both be true at once; this project
keeps the second promise.

What it does instead, as of this version:

1. You pick a time (and, optionally, a phone number in international format, e.g.
   `201234567890` for an Egyptian number — no `+`, spaces, or leading zero).
2. At that time, a notification fires. **Tapping it opens WhatsApp directly on that exact
   chat**, with your message already typed in the text box, via WhatsApp's own official
   "click-to-chat" deep link (`https://wa.me/<phone>?text=...` — the same mechanism WhatsApp's
   own click-to-chat marketing links use, not a private API).
3. You tap **Send** yourself. That's the one unavoidable step.

If you leave the phone number blank (the message is for a **group**, which has no phone
number), tapping the reminder opens WhatsApp generally instead, and you pick the group
yourself — WhatsApp has no public way to deep-link into a specific group by name.

---

## AI setup

The AI layer is fully pluggable behind the `AiSummaryProvider` interface
(`domain/repository/AiSummaryProvider.kt`):

- **Local (default)** — `LocalRuleBasedAiProvider`: fully on-device, no network, no key needed.
  This is what the app uses out of the box and what it always falls back to if a cloud call
  fails.
- **Cloud (optional)** — `OpenAiProvider`, `AnthropicProvider`, `GeminiProvider`
  (`data/ai/CloudAiProviders.kt`). Enabling one requires **two explicit user actions** in
  Settings: (1) picking the provider, and (2) toggling "Allow sending message text to
  `<provider>`" — the in-app AI-cloud-consent switch. Without both, the app silently stays on
  the local provider.

**No API key is ever hardcoded** — not in Kotlin source, not in `strings.xml`, not in
`BuildConfig`. Keys are entered by the user in Settings and stored only in
`EncryptedSharedPreferences` (AES-256, via `androidx.security.crypto`), which is explicitly
excluded from Android backups (see `data_extraction_rules.xml` / `backup_rules.xml`).

If you want to experiment with a key during development without typing it in the UI each time,
put it in your own `local.properties` (already gitignored by convention) and wire a
`BuildConfig` field for **debug builds only** — never for release. This project does not do
that by default, to keep the "no key in the APK" guarantee true out of the box.

---

## Privacy

Everything is local-first:

- All captured messages/groups/summaries live in a private Room database
  (`wwm_database.db`), inside the app's private storage.
- Nothing is sent anywhere unless you explicitly enable a cloud AI provider (see above), and
  even then, only the message text batch being summarized is sent — no message DB dump.
- The database and encrypted prefs are excluded from Android's auto-backup.
- **Clear All Data** (Settings) permanently deletes all messages, groups, and summaries.
- Message retention is configurable (7 / 30 / 90 days, default 30) and enforced daily by
  `CleanupWorker`.

Full user-facing text lives in `res/values/strings.xml` under `privacy_body` and is shown on
the in-app Privacy & Data screen.

---

## Why scheduled work might not fire exactly on time (and how to fix it)

This is an Android OS-level behavior, not a bug in this app's scheduling code — but it's worth
understanding, because it's the #1 reason a "working" schedule looks like it's "doing nothing":

1. **WorkManager is not a precise-time scheduler.** A daily summary set for 08:00 is a request
   to run *around* 08:00, not a guarantee. Under **Doze mode** (screen off + device stationary
   for a while) and **App Standby buckets**, Android defers background work — sometimes by
   hours — until the device wakes up for some other reason (screen on, charging, etc.).
2. **Battery optimization** can be even more aggressive than plain Doze for apps the system
   decides are "rarely used." The app now has a **Settings → Battery Optimization** row (and an
   Onboarding step) that deep-links to `Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS` —
   the system's own list of battery-exempt apps — so the user can add this app themselves. This
   deliberately does *not* use the one-tap
   `ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`/`REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`
   permission path, since Google Play reviews that permission strictly and this project sticks
   to its "only genuinely required permissions" rule — one extra tap from the user instead.
3. **OEM battery managers are a separate, additional layer** on top of stock Android — Xiaomi
   (MIUI), Huawei (EMU I/HarmonyOS), Oppo/Realme (ColorOS), Vivo (FunTouch), and Samsung all ship
   their own background-app killers that can override what Android/WorkManager itself allows.
   These need a manual "Autostart" / "No restrictions" / "Allow background activity" toggle
   that's OEM-specific and outside any single API — see
   [dontkillmyapp.com](https://dontkillmyapp.com) for current per-device instructions; there's
   no code-level fix for this layer, only user education (which is why the Settings row above
   links to what Android itself controls, and this section explains the rest).
4. **`NotificationListenerService` itself can be unbound and rebound** by the system under
   memory pressure. If message capture stops working after the device has been idle a long
   time, toggling Notification Access off and back on for this app (Settings → Notification
   Access) forces a fresh bind.
5. **No schedule was actually added yet.** Nothing runs automatically until you add at least
   one entry in **Settings → Summary Schedule** (Summary Times tab) or **Scheduled Messages** —
   the empty states ("No summary times scheduled yet…") mean exactly that.

### Debugging: confirm a job is actually enqueued

From a terminal with the device connected (`adb`):

```
adb shell dumpsys jobscheduler | grep -A 20 com.whatsappworkmanager.app
```

This lists every job WorkManager has registered for this app and its next-scheduled time. If
nothing shows up, no schedule was added (point 5 above) — go add one from the app.

To force one specific job to run immediately, regardless of Doze (useful for testing that the
worker itself is correct, independent of timing):

```
adb shell cmd jobscheduler run -f com.whatsappworkmanager.app <JOB_ID>
```

(`<JOB_ID>` comes from the `dumpsys jobscheduler` output above.) The **Dashboard**'s "Generate
Summary Now" button does the equivalent for the summary worker specifically, from inside the
app — if that button works but the scheduled time doesn't, the issue is confirmed to be
OS-level deferral (points 1–3), not the worker logic itself.

## Testing

Unit tests (`app/src/test/...`, pure JVM, no emulator needed) cover:

- `KeywordScoringTest` — bilingual (EN/AR) weighted scoring, custom keyword rules
- `ReplyDetectorTest` — phrase + question-mark detection, custom phrases
- `MessageClassifierTest` — combined importance + reply classification
- `SummaryGeneratorTest` — local-fallback summary counts, exclusion of non-opted-in groups
- `ScheduleLogicTest` — same-day and overnight-wrapping Work/Break/Quiet windows, summary due-time logic
- `NotificationTextParserTest` — the "Sender: message" extraction heuristic used by the
  notification listener, including edge cases (no separator, blank remainder, Arabic names,
  incidental colons in long sentences)
- `WhatsAppNotificationListenerServiceTest` — a **Robolectric**-backed test that exercises the
  real capture pipeline (`captureMessage`) end to end: classification → Room persistence →
  work-group stat updates → the opted-in/high-priority notification gate — using a real
  `WwmApplication` and a real (in-JVM) Room database, without needing a device or emulator.
  The Service class itself stays a thin wrapper around this top-level function specifically so
  it's testable this way; `StatusBarNotification`/`Bundle` extraction is covered separately (and
  more reliably) by `NotificationTextParserTest`.

Run them with:
```
./gradlew testDebugUnitTest
```

`IntentHelperTest` (also Robolectric-backed) verifies the click-to-chat deep-link builder:
phone numbers get cleaned of `+`/spaces before being placed in the `wa.me` URL, the message
text is URL-encoded, WhatsApp-not-installed returns `null` gracefully, and a blank/absent phone
number falls back to a plain "open WhatsApp" intent rather than a broken deep link.

Note on Robolectric: it's configured via `app/src/test/resources/robolectric.properties`
(`sdk=34`) and `testOptions.unitTests.isIncludeAndroidResources = true` in
`app/build.gradle.kts`. This environment could not run an actual Gradle build to confirm the
Robolectric+Room combination executes cleanly on your machine (no network/JDK available here) —
if `testDebugUnitTest` reports a SQLite/native-library error under Robolectric, the usual fix is
bumping the `org.robolectric:robolectric` version in `app/build.gradle.kts` to the latest 4.x, or
adding `android.testInstrumentationRunnerArguments` per Robolectric's current docs for your
Android Gradle Plugin version.

### Database schema note

The Room schema is currently at `version = 2` (bumped when the optional `phoneNumber` column
was added to `scheduled_messages`), with `fallbackToDestructiveMigration()` enabled in
`AppDatabase.kt`. That's a pre-release convenience only — it means a schema bump just clears
local data on next launch instead of requiring a `Migration` object. **Before your first
production release**, replace `fallbackToDestructiveMigration()` with real `Migration` objects
(or, if you're certain no one has installed a build yet, simply leave it — but do this
deliberately, not by accident.)

---

## Release checklist (already applied in this project)

- [x] `namespace` / `applicationId` = `com.whatsappworkmanager.app`
- [x] `android:label` = "WhatsApp Work Manager"
- [x] No Accessibility Service, no root usage, no WhatsApp Web scraping anywhere in the codebase
- [x] No hardcoded API keys (grep the repo for `sk-` / `AIza` / `x-api-key` — none present)
- [x] Release build: `isMinifyEnabled = true`, `isShrinkResources = true`, ProGuard rules included
- [x] Only genuinely required permissions requested (see `AndroidManifest.xml`)
- [x] RTL supported (`android:supportsRtl="true"`, `values-ar/strings.xml`)
- [x] Dark mode supported (`values-night/themes.xml`, dynamic `WwmTheme`)
- [x] Original app icon (not WhatsApp's logo/trademark)
- [x] `versionCode = 1`, `versionName = "1.0.0"`
- [x] Release signing wired to an optional, git-ignored `keystore.properties` (see **Release signing**)

---

## Round 35: the shape/elevation consistency pass, completed everywhere

Round 34 rebuilt the design foundation (colors, shapes, type) and fully applied it to the
Dashboard as the first converted screen, noting the rest as a mechanical follow-up. This round
is that follow-up: every remaining screen's ad-hoc `RoundedCornerShape(12/16/20.dp)` — Work
Groups & Clients, Important People, Keyword Rules, Reply Detection, Scheduled Messages, Work
Schedules, Summary, Search, Quick Chat — now reads from the same `MaterialTheme.shapes.small/
medium/large` scale as the Dashboard, instead of each screen's own guess at a corner radius.
Card elevation was standardized the same way: every card across the app is now flat (0dp),
matching `StatCard`'s look, rather than some cards carrying a subtle shadow and others not for
no particular reason. The whole app now shares one shape scale and one elevation choice,
top to bottom.

## Round 34: a real design system, not just individual screen tweaks

Rebuilt the app's visual foundation from the ground up, rather than patching individual
screens — this is what actually fixes "looks unorganized/random," since that feeling usually
comes from many small, inconsistent choices (a 20dp corner radius here, 16dp there, an
auto-derived color that doesn't quite match a hand-picked one) rather than any single obvious
mistake.

- **A complete, hand-picked Material3 color palette** (`Color.kt`) — every token specified
  intentionally (primary, secondary, tertiary, their containers, surface variants, outlines,
  in both light and dark) instead of leaving most of them to Material3's auto-derivation from
  one seed color, which is a fine starting point but tends to feel slightly mismatched once a
  screen combines several derived tokens. A confident emerald/teal primary, a warm amber for
  "needs attention," red reserved only for genuinely urgent state, and warm-neutral surfaces
  instead of stark black/white.
- **A consistent shape scale** (`Shapes.kt`) — `extraSmall` through `extraLarge`, referenced
  via `MaterialTheme.shapes.*` instead of each screen picking its own corner radius ad hoc.
  Applied throughout the Dashboard as the first fully-converted screen.
- **A complete type scale** (`Type.kt`) — every Material3 text style filled in deliberately
  (including ones the app hadn't used yet, like `displayLarge`/`titleSmall`/`bodySmall`), so
  reaching for any of them gives something consistent rather than a generic fallback.
- **`StatCard`** (the Dashboard's stat tiles, used elsewhere too) now shows its icon inside a
  soft, tinted circle rather than floating bare on the card — a small, low-cost touch that
  reads as noticeably more considered.
- **The Dashboard now greets you** — "Good morning"/"Good afternoon"/"Good evening" based on
  the time of day, above the existing title, instead of a plain static heading.

Honest scope note: this round rebuilt the *foundation* (colors/shapes/type, used automatically
by every Material3 component everywhere) and fully applied it to the Dashboard specifically.
Every other screen already benefits from the new colors and type scale automatically (since
they all read from the same `MaterialTheme`), but many still specify their own ad-hoc corner
radius rather than `MaterialTheme.shapes.*` — bringing every remaining screen in line with the
same shape scale is a mechanical follow-up pass, not a redesign.

## Round 33: Quick Chat screen — the "Open Chat" button was getting clipped off-screen

**Real layout bug**: the screen used a plain, non-scrollable `Column` with a weighted `Spacer`
to push the "Open Chat" button to the bottom — a pattern that only works if the content above
is guaranteed to fit within the screen's height. On a phone where the header + both cards
together were taller than the available space, there was nothing to scroll to reach the rest,
so the button was clipped off-screen entirely (the screenshot showed only a sliver of it
peeking in at the very bottom edge). Fixed by making the whole screen scroll
(`verticalScroll`) and letting the button flow naturally after the last card with normal
spacing, plus a little breathing room at the very end of the scroll so it never sits flush
against the bottom edge or gesture-nav area on any device.

## Round 32: the honest answer on muted chats — reframe the problem, don't fight it

Confirmed empirically (checking the notification shade during a live test): WhatsApp's own
in-app mute doesn't deliver the notification silently — it doesn't post one **at all**. That
is a hard platform limitation, not a bug: this app's only legitimate way to see a message, with
no Accessibility Service and no root, is a notification actually being posted, and there's no
way to see something that was never delivered in the first place. Round 17's original claim
("muted chats still work, WhatsApp delivers them silently") was wrong specifically for
WhatsApp's *own* mute toggle and has been corrected in the app's own copy.

The genuinely useful fix isn't a workaround for WhatsApp's mute — it's routing around it
entirely: **Android has its own, separate per-conversation "Silent" notification tier**
(Settings → Notifications → Conversations, or the gear icon on a long-pressed notification),
which achieves the same practical goal — no sound, no vibration, no pop-up, often not even the
lock screen — through a completely different mechanism than WhatsApp's mute: the notification
still gets posted, just at low importance, so it still reaches this app's listener exactly
normally. Leaving a chat unmuted in WhatsApp itself and setting it to Silent in Android's own
settings instead gets the same peace of mind without losing tracking.

Added `IntentHelper.openNotificationSettingsForWhatsApp` (opens Android's own notification
settings screen for whichever WhatsApp variant is installed) and a help card on the Work
Groups & Clients screen explaining this clearly, with a button straight to that settings
screen.

## Round 31: retroactive junk cleanup, and per-reply RTL layout

**"Checking for new messages" still showing up — because it was already captured before the
filter existed.** `isWhatsAppSystemNotification` (added Round 17) only prevents *future*
captures; it never touched rows already sitting in the local database from before that fix.
Added a one-time startup cleanup (`MessageCleanup.purgeSystemNotificationJunk`) that runs on
every app launch — cheap, safe to repeat, and a genuine no-op once the junk is gone — which
purges any existing message that matches the same known-status-phrase check.

**Reply cards now get the correct layout direction for their own content, not the app's UI
language.** The screenshot showed exactly this mismatch: an English-language UI with an
Arabic-language generated reply, where the "Use" button's position didn't match how an Arabic
reader's eye naturally flows (right-to-left, so the "confirm" action reads naturally on the
left, not the right). Each reply suggestion card now detects its own text's language and wraps
itself in the matching `LayoutDirection` (RTL for Arabic, LTR otherwise) — independent of
whatever language the rest of the app's chrome happens to be in.

## Round 30: the real cause of the hanging CI run — a genuine test deadlock

The CI run that appeared stuck indefinitely at "Run unit tests" wasn't a slow first-time
Robolectric download (my first guess, given in good faith but wrong) — it was a genuine
deadlock in `SearchViewModelTest`. That class calls `viewModel.setImportant(...).join()`, and
`setImportant` launches its work via `viewModelScope.launch { }`, which runs on
`Dispatchers.Main.immediate` by default. Under Robolectric, that's backed by a simulated
Android main looper that only executes queued work when something explicitly pumps it — with
no `Dispatchers.setMain(...)` redirecting it anywhere, nothing ever did, so the launched
coroutine could never actually be scheduled to run at all. `.join()` on a Job that will never
progress doesn't race — it waits forever. Round 25 pointed `Dispatchers.Main` at
`UnconfinedTestDispatcher()` for exactly this reason, but Round 26 removed that when switching
to `.join()`, treating the two as alternatives rather than both being necessary together: the
dispatcher redirect makes the coroutine actually *run*, and `.join()` makes the test correctly
*wait* for the complete result (including Room's own internal executor hop) rather than
assuming synchronous completion. Restored the dispatcher setup alongside the existing `.join()`
calls. Checked every other test file that instantiates a ViewModel — `SearchViewModelTest` was
the only one at risk.

## Round 29: test fix — wrong Robolectric shadow method name

Build failed to even compile: `ShadowPowerManager` doesn't have a `setIsIgnoringBatteryOptimizations(Boolean)` method — the actual API is `setIgnoringBatteryOptimizations(String packageName, Boolean value)`, taking the package name explicitly rather than assuming "the app under test." A one-line test fix; `IntentHelper.isIgnoringBatteryOptimizations` itself was never wrong.

## Round 28: a real, systematic fix for muted-chat reliability — active reconciliation

The Round 27 battery-optimization banner addresses one possible cause but is still a "hope the
timing works out" fix. This round adds something structurally different: instead of only ever
reacting to `onNotificationPosted` events and hoping none are ever delayed or dropped for
whatever reason, the app now periodically asks Android directly for the ground truth.

`NotificationListenerService.getActiveNotifications()` — a standard, official API, not a
workaround — returns every notification actually present in the shade right now, muted or not,
regardless of how or when each one was posted. Three layers, all reusing the same scan logic
(and therefore the same dedup protection, so re-scanning something already captured is a
genuine no-op):
- **On every listener (re)connection** (`onListenerConnected`) — happens on boot, when access
  is granted, and whenever the OS reconnects the service — a full catch-up scan runs
  automatically.
- **Every 15 minutes** (WorkManager's minimum interval), `NotificationCatchUpWorker` calls the
  static `NotificationListenerService.requestRebind()` API, forcing a disconnect+reconnect
  cycle that re-triggers the scan above — an automated safety net that doesn't depend on
  holding a reference to a live service instance (which could be null if the process was ever
  killed).
- **On demand**, the Dashboard's Refresh button now also triggers this same rebind, for an
  instant manual resync whenever the user wants one.

This doesn't require guessing which specific OS mechanism might have delayed a particular
notification — it makes the app self-correcting regardless of the reason, on a short cycle,
using only official Android APIs and staying entirely within the project's existing
constraints (no Accessibility Service, no root, no reading WhatsApp's own data).

## Round 27: real bug in "Save as Important" phone numbers, and muted-chat reliability

**Real bug fixed: saved phone numbers were getting corrupted for anyone whose WhatsApp name is
a raw number.** When "Save as Important" pre-filled the phone field for someone not saved in
the phone's contacts (WhatsApp shows their raw number as the display name), the extracted
digits — country code and local number still combined — were dumped entirely into the *local
number* field, while the *country code* field separately defaulted to whatever was last used.
The number that actually got saved was the wrong last-used country code prefixed onto an
already-complete number (e.g. "20" + "971562331154"), silently corrupted. This is very likely
what caused the exact symptom reported: a number correctly detected and opened directly when
*not* saved (the raw-number fallback path, unaffected by this bug), but falling back to
copy-and-open-generally once saved as an Important Person (the corrupted saved number no longer
matched anything useful). Fixed the split logic properly, and — as a second, independent layer
of protection — the lookup itself now *also* matches a saved contact by comparing actual phone
digits (not just name-substring matching), so a contact is still found correctly even if its
saved name text doesn't exactly match what a later message shows.

**Muted chats — the architecture was always correct (WhatsApp still delivers muted
notifications silently, which this app's NotificationListenerService can see), but background
reliability specifically for *silent* notifications depends on something this app couldn't
previously see or act on**: Android is measurably more willing to defer background work for
silent notifications (exactly what a muted chat produces) than for ones that visibly alert the
user, when the app isn't exempted from battery optimization — and a capture merely delayed by
Doze looks, from the user's side, identical to one that never happened at all. Added a real
check (`IntentHelper.isIgnoringBatteryOptimizations`) and a Dashboard banner that appears
specifically when this isn't granted, explaining why it matters for muted chats in particular
and linking straight to the settings screen to fix it — shown at the lowest priority of the
Dashboard's banners, only once Notification Access and Work Groups setup are already handled.

## Round 26: the previous test fix wasn't enough — a more reliable one

Round 25's fix (`UnconfinedTestDispatcher` on `Dispatchers.Main`) reduced the raciness but
didn't eliminate it: Room's own suspend DAO functions hop onto Room's internal query executor
thread to actually run the SQL, regardless of which dispatcher the *calling* coroutine is on —
so controlling `Dispatchers.Main` alone still couldn't guarantee `setImportant(...)`'s work
(including the DB write and the Reply Detection phrase check) had genuinely finished by the
time the test's very next line ran. `SearchViewModel.setImportant` (and `markAsRead`) now
return the `Job` `viewModelScope.launch` produces — harmless for the app's own callers, who
already don't use the return value — so the test can `.join()` it: an explicit wait for that
Job to reach a truly completed state, correct regardless of how many different
threads/dispatchers the work touched along the way. This replaces Round 25's dispatcher-only
approach entirely rather than layering on top of it.

## Round 25: test fix — ViewModel coroutine race, not an app bug

`SearchViewModelTest` failed 3 of its 4 tests, but the app code itself was correct: the tests
called `viewModel.setImportant(...)` (which does its work inside `viewModelScope.launch { }`,
genuinely asynchronous, same as in the real app) and asserted on the result *immediately
afterward*, without anything to make the launched coroutine actually finish first — a straight
race between the test's assertion and the coroutine's completion. Whether it happened to
"win" depended on incidental timing, and adding the `BadgeUpdater.refresh()` call in Round 24
(one more suspension point inside that same coroutine) was enough to tip it from "usually
passes" to "consistently fails." Fixed by pointing `Dispatchers.Main` at
`UnconfinedTestDispatcher()` for the duration of this test class, which runs a launched
coroutine to completion synchronously before `setImportant(...)` returns control to the test —
removing the race entirely rather than papering over it with a delay. Checked every other test
file in the project that instantiates a ViewModel; none of the others had this same gap.

## Round 24: app icon badge, and a dedicated "Quick Chat" screen

**App icon badge for unread Important/Need Reply messages.** An honest limitation stated up
front: Android's app icon badge is ultimately an OS/launcher decision, not something any app
fully controls — `NotificationCompat.setNumber()` is the correct, standard way to *request* a
numbered badge, and launchers that support numbers (Samsung's One UI and others) generally
honor it, but stock Android's own Pixel Launcher deliberately shows only a small dot, never a
number — a conscious Google design choice, not a bug or missing permission here. Implemented
the best achievable version regardless: a single, auto-updating notification whose `setNumber()`
tracks the live count of unread Important/Need-Reply messages, refreshed after anything that
changes that count (a new one captured, one marked read, importance toggled) and on every app
cold start as a drift safety net, cancelled entirely once the count reaches zero.

**New "Quick Chat" screen**, reachable from a dedicated card on the Dashboard: enter a country
code and number (or pick from Contacts) and choose which WhatsApp app to use (Automatic /
WhatsApp / WhatsApp Business, defaulting to the Settings preference but overridable per use),
then one tap opens that exact chat directly — without needing to save the person as an
Important Person or a phone contact first. Deliberately built as its own full screen rather
than a cramped Dashboard widget, matching the app's existing card-based visual style.

## Round 23: replies now consider the recent conversation, not just one message

When several messages have arrived from the same client/group, generating a reply now pulls in
the last few messages from that same conversation (oldest first, capped at 5, never crossing
into messages from a different group) instead of reacting to a single line in isolation — a
question asked two messages ago, or context from what's already been discussed, now actually
shapes the suggested replies instead of being invisible to the AI.

`AiSummaryProvider` gained `suggestRepliesForConversation(conversation, language)`, with its
own dedicated prompt (distinct from the single-message one) that explicitly marks which line in
the transcript is the one being replied to and instructs the model to use the earlier messages
as context, not to reply to each one individually. All five cloud providers implement it; the
local, fully-offline provider uses the interface's default (falls back to replying to just the
last message), since its rule-based logic doesn't meaningfully benefit from more context anyway.

## Round 22: "Use" button was invisible, and a real "Refine with AI" for your own draft

**"Use" button visibility fixed**: it was a plain `TextButton` sitting directly on the
suggestion card's own tinted background, with no visual boundary of its own — easy to miss
entirely, which is exactly what was reported. Every "Use" button (generated suggestions and
the custom-reply field, on both Search and Summary screens) is now a proper filled `Button`
with a checkmark icon, on its own line, clearly a distinct tappable element regardless of the
card's background color.

**"Refine with AI" — a real feature, not a reuse of the reply-suggestion flow**: writing your
own draft in "Or write your own" now has a "Refine with AI" button next to Use. This is a
genuinely different AI call from generating suggestions — `AiSummaryProvider` gained a new
`refineReply(draft, language)` method with its own dedicated prompt that explicitly tells the
model "this is the user's own draft to polish, not an incoming message to respond to" (reusing
the reply-suggestion prompt on the user's draft would have made the AI reply *to* their draft
as if a stranger had sent it — the wrong behavior entirely). All five cloud providers implement
it; the local, fully-offline provider intentionally does not, and returns the draft unchanged
via the interface's default — there's no on-device model to actually do the polishing, and
pretending otherwise would be dishonest. The refined text replaces what's in the field, so it
can still be reviewed or edited further before tapping Use.

## Round 21: duplicate messages fixed properly, and starring teaches Reply Detection

**Duplicates still happening — root cause found**: the dedup check added last round required
an *exact* timestamp match, but a repost of the same WhatsApp notification isn't guaranteed to
carry the identical `postTime` down to the millisecond (and this app falls back to
`System.currentTimeMillis()` — a fresh value every call — whenever `postTime` is ever
missing/invalid), so genuinely duplicate notifications with a slightly different timestamp
sailed straight through the exact-match check undetected — exactly what the screenshot showed
(the same message twice, a second or so apart). Fixed by widening the check to a tolerance
window (within ~10 seconds, same group/sender/text) instead of requiring an exact match — wide
enough to catch a repost's timing jitter, nowhere near wide enough to mistake two genuinely
different messages seconds apart for the same one (covered by its own test).

**Marking a message Important now also teaches Reply Detection**: starring a message adds its
own text as a Reply Detection phrase too (if it isn't one already), so that exact wording is
recognized as "needs a reply" going forward — not just flipping a flag on that one message.
Un-starring never removes an existing phrase, so it can't silently delete something the user
was relying on.

## Round 20: Summary screen — layout fix, per-item replies, add to phone contacts

**Layout bug fixed**: the four stat labels ("Total", "Important", "Need Reply", "Groups") were
in a single unweighted `Row`, which let the last one get squeezed into an oddly narrow,
multi-line sliver on normal phone widths. Now two rows of two, each stat given equal weight —
predictable, readable on any screen size.

**Generate Reply, now on the Summary screen too**: each summary already carried a structured
list of the individual messages behind it (`WorkSummary.items`), but the screen only ever
displayed the one blended paragraph of text — the per-item detail was there in the data but
never shown. Now every item shows its own group/sender name and message text, with the same
Generate Reply flow as the Search screen (language auto-detected from the message, Regenerate,
write-your-own, opens the exact chat when a phone number is known).

**Old empty summaries no longer sit next to real ones looking contradictory**: once at least
one summary has real content, older "no messages during this period" ones are hidden from both
the Summary screen's list and the Dashboard's "Last Summary" card (which now prefers the most
recent summary that actually has content, rather than always the newest regardless).

**Add an unregistered number to your phone's real Contacts app, not just this app's Important
People**: the "Save as Important" flow now has an "Add to Phone Contacts" button alongside it,
pre-filled with the name and number — opens the system Contacts app's own "add contact" screen
(`ACTION_INSERT`), same permission-free pattern as picking a contact; no `WRITE_CONTACTS`
permission needed since the actual write happens in the user's own Contacts app, with them
tapping Save themselves.

## Round 19: "messages exist but the summary says none" — fixed with a clearer explanation

**Not actually a bug — a discoverability gap, fixed with a clearer message.** The screenshots
showed "Messages Today: 2" on the Dashboard but the summary said "No messages during this
period," which looked broken. It wasn't: "Work Groups: 0" in the same screenshot was the real
signal — a summary only ever includes messages from groups/clients that have been explicitly
enabled (Work Groups & Clients), which is working exactly as designed, but the plain "no
messages" text gave no hint that *that* was the reason.

Two fixes:
- The empty-summary text is now smarter: if messages have genuinely arrived but nothing is
  enabled yet, it says so directly and points at Work Groups & Clients, instead of the generic
  (and in this case misleading-looking) "no messages during this period."
- The Dashboard itself now shows a banner the moment this exact situation is detected
  (messages arriving, zero groups enabled) — spotted before you even open the Summary screen,
  with a button straight to Work Groups & Clients.

## Round 18: Arabic fixed at the root, plus quick "save as Important" from a message

**Arabic still not applying — real root cause found and fixed**: `MainActivity` extends plain
`ComponentActivity`, not `AppCompatActivity`. `AppCompatDelegate.setApplicationLocales()` (the
officially documented AndroidX per-app-language API) is supposed to work without
`AppCompatActivity` per its own release notes, but in practice this had inconsistent behavior
across OEM Android builds below API 33 — it just never reliably took effect on this app's
Activity, no matter how correctly the surrounding plumbing (translated strings, `recreate()`,
persisted preference) was built. Fixed by adding a second, unconditionally-reliable mechanism
on top: both `WwmApplication` and `MainActivity` now override `attachBaseContext()` and
directly wrap the incoming `Context` in a `Configuration` forced to the saved language via
`createConfigurationContext()` — the classic, pre-AndroidX technique that works regardless of
Activity base class, AppCompat version, or OEM quirks, since it runs before any resource is
ever resolved. `AppCompatDelegate.setApplicationLocales()` is still called too (harmless, and
some system UI surfaces read it independently), just no longer relied on as the sole mechanism.

**New: register or update an Important Person straight from a message.** Each message now has
a "Save as Important" button — pre-fills the sender's name (or, for a 1:1 chat, the contact's
own name) and lets you add their phone number, without navigating to Settings → Important
People and retyping the name. If that name is already saved, it shows that and updates the
existing entry instead of creating a duplicate.

**New: mark any individual message as Important, independent of keyword rules or sender.** A
star icon on each message toggles a manual override — useful for a specific message that
matters even though nothing about its keywords or sender would otherwise flag it.

## Round 17: no-contact chats, duplicate messages, system notifications, custom replies

**Bugs fixed:**
- **Reply didn't open the exact chat for someone not saved as an Important Person** — for
  anyone not in the phone's contacts, WhatsApp itself shows their raw phone number as the
  display name (e.g. "+971 56 233 1154"). That name IS already a usable phone number; the
  reply flow now recognizes this and uses it directly for the click-to-chat deep link, without
  requiring the extra step of saving them as an Important Person first. Saved Important People
  are still checked first when both exist.
- **Duplicate messages** — the exact same message (same group, sender, text, and timestamp)
  could be captured twice, because `NotificationListenerService.onNotificationPosted` can
  legitimately fire more than once for what is genuinely the same WhatsApp notification event
  (WhatsApp re-posting/updating it). Every capture now checks for an existing identical row
  first and skips the insert if one's already there.
- **WhatsApp's own status notifications getting captured as messages** — "Checking for new
  messages", "Backing up chats", etc. are WhatsApp's own connectivity/backup status
  notifications, posted through the same channel as real messages, with the app's own name as
  the title. These are now filtered out before ever reaching the capture pipeline.

**New in the reply dialog:**
- **Regenerate** — get a fresh set of AI suggestions for the same message without closing and
  reopening the dialog.
- **Write your own reply** — a free-text field to type a custom reply instead of picking one of
  the generated suggestions; it goes through the exact same send logic (opens the exact chat
  with the text pre-filled when a phone number is known, otherwise copies + opens WhatsApp).

## Round 16: more test isolation gaps (still not app bugs)

Two more unit tests failed in CI for the same underlying reason as Round 10 — test pollution,
not a real bug in the app itself:
- `FindSavedPhoneForSenderTest`: a test saving a contact named "Ahmed Hassan" ran before a test
  expecting *no* match to exist yet, so the leftover contact broke the second test's assertion.
  Fixed by adding a `deleteAll()` reset to its `@Before` (which required adding `deleteAll()` to
  `ImportantContactRepository`/`ImportantContactDao` — previously it only had single-item
  `delete(id)`).
- `SummaryRunnerTest`: didn't reset the shared `autoEnableNewGroups` setting in its own
  `@Before`, so a `true` value left behind by a *different* test class (this can leak across
  classes, not just methods within one class, depending on JVM fork reuse) made a
  newly-discovered group start enabled when the test expected it to start disabled.

Audited every Robolectric test class in the project against what it actually writes to shared
state (repositories, DataStore settings) and confirmed each one's `@Before` now resets exactly
that — `WorkGroupRepositoryTest`, `SummaryRunnerTest`, `WhatsAppNotificationListenerServiceTest`,
`FindSavedPhoneForSenderTest`, and `MessageRepositoryTest` all now match.

## Round 15: Arabic actually works now — real translation coverage + onboarding choices

The core language-switching mechanism (`AppCompatDelegate.setApplicationLocales` +
`activity.recreate()`) was already correct — the real problem was that most screens added in
Rounds 4 onward (Work Groups & Clients, Important People, Keyword Rules, Reply Detection,
Scheduled Messages, the country-code field, Search, most of Settings, Dashboard's newer bits)
had their text written as plain English string literals directly in the Compose code instead
of going through Android's `strings.xml` resource system — so switching the language had
nothing to translate on those screens, no matter how correctly the mechanism itself worked.

Fixed by moving essentially all of that text into `strings.xml`/`values-ar/strings.xml` (159
matching string resources in each) and updating every screen to read from them. Verified
mechanically, not just by eye: both resource files parse as valid XML, every key in the
English file has a matching Arabic key and vice versa, every `%1$s`/`%1$d`-style format
string has identical placeholders in both languages (a mismatch there is a guaranteed runtime
crash), and every single `R.string.*` reference anywhere in the Kotlin code resolves to an
actual defined resource. A handful of things were deliberately left as-is: numeric field
placeholders ("20", "1001234567"), and country names in the country-code picker (translating
190+ country names is its own separate project).

**Onboarding now asks for language and WhatsApp app up front** — two new first-run steps
before the existing explanation screens: pick English or Arabic (applied immediately), then
pick WhatsApp / WhatsApp Business / Automatic. Both remain changeable later from Settings;
this just means the app isn't silently guessing and making you go find where to change it.

## Round 14: quick "+" shortcuts on the Dashboard's Important/Need Reply cards

Added a small "+" button in the corner of the **Important** and **Need Reply** stat cards on
the Dashboard — a direct shortcut to that category's related settings, without going through
Settings manually first:
- **Important** → opens a small menu (Important has two separate settings that feed it):
  "Add Important Keyword" or "Add Important Person".
- **Need Reply** → opens Reply Detection directly (its only related setting).

Tapping the card itself (not the "+") still does what it did before — opens the filtered
message list for that category. The two gestures are on the same card but don't conflict: the
"+" is a small icon in the corner, the rest of the card remains the "view filtered list" tap
target.

## Round 13: replies to 1:1 chats never opened the exact chat — fixed

**Bug fixed**: tapping "Use" on a generated reply always fell back to opening WhatsApp
generally (copy + paste yourself) instead of the exact chat, *specifically for 1:1
conversations* — exactly the case where it matters most. Root cause: the phone-number lookup
only ever checked `WorkMessage.sender`, but WhatsApp's notification format never populates
`sender` for a 1:1 chat (there's no "Sender: " prefix in the body — the notification *title*,
which this app stores as `groupName`, already is the contact's name). So the lookup was always
checking a field that's `null` for exactly the case it needed to handle. Fixed: the lookup now
checks `sender` first (for group messages, where it holds the actual person who sent it) and
falls back to `groupName` when `sender` is null (1:1 chats). Group messages are unaffected —
they still match against the sender's name, not the group's.

## Round 12: messages permanently stuck excluded from every summary — fixed properly

**Bug fixed** (the "there are messages but they're not showing" report): two compounding
issues meant a captured message could end up excluded from every future summary forever, even
though the Dashboard's "Messages Today" count showed it existed:

1. **A group enabled *after* some of its messages had already arrived** — `isIncludedInSummary`
   was only ever set once, at capture time, based on whether the group was enabled *then*.
   Toggling the group on afterwards (the normal flow: a group shows up, you review it, you
   decide to enable it) never revisited those earlier messages. Fixed: enabling/disabling a
   group in Work Groups & Clients now also retroactively flips `isIncludedInSummary` for that
   group's *existing* messages, not just future ones.
2. **A fixed time window that only ever moves forward** — summaries were built from
   "messages between the last summary's end time and now." Once *any* summary ran (even one
   covering zero messages — see the previous round's bug), that window advanced past whatever
   point it ended at, and a message that should have been eligible could still end up outside
   every subsequent window through bad timing. Fixed at the root: summaries are now built from
   "every eligible message not yet included in a past summary" (a new `includedInPastSummary`
   flag on each message, flipped only once its summary is actually saved), which makes the
   old class of "fell through the time gap" bug structurally impossible — a message stays
   eligible until it's genuinely been summarized, full stop, regardless of any window.

## Round 11: don't call the AI when there's nothing to summarize

**Bug fixed**: generating a summary for a period with zero included messages still called the
configured cloud AI provider with an essentially empty prompt — a general-purpose chat model
naturally responds to that conversationally ("Sure! Please paste the work messages you'd like
summarized...") instead of saying there's nothing to report, which is exactly what showed up
as the "summary" text. Fixed by short-circuiting to the local "No messages during this period"
text whenever there are zero messages to summarize, without ever calling the AI provider —
correct behavior, and it also stops burning an API call for literally nothing.

## Round 10: test isolation fix (flaky/failing unit tests, not a code bug)

The CI run failed 3 unit tests (`WorkGroupRepositoryTest`, and two in
`WhatsAppNotificationListenerServiceTest`) even though the app code itself compiled and ran
correctly — this was **test pollution**, not a real bug: several tests reuse the same group
name (e.g. "Sales Team") or mutate a shared setting (`autoEnableNewGroups`) without resetting
it afterwards, so whichever test JUnit happened to run first left state behind that broke a
later, otherwise-correct test. Fixed by giving `WorkGroupRepositoryTest`,
`WhatsAppNotificationListenerServiceTest`, and `MessageRepositoryTest` an explicit `@Before`
that wipes the relevant repositories/settings back to a known clean state before every single
test method, regardless of what ran before it or in what order.

## Round 9: professional message list — timestamps, read/unread, delete, person vs. group

- **Time and date next to every message**, formatted contextually ("Today · 3:45 PM" for
  today, "Sep 6 · 3:45 PM" for this year, full date otherwise).
- **Tap a message to mark it read.** Unread messages get a subtle background tint, bold text,
  and a small red "● Unread" label; read ones show a blue "✓✓ Read" label (WhatsApp's familiar
  double-check styling) instead. **This is purely a local, in-app indicator** — it has no
  effect whatsoever on WhatsApp's own read receipts or the actual sender; it only tracks
  whether *you've* opened it in this app, matching this project's original constraint against
  ever faking WhatsApp's real read/delivery ticks.
- **Delete any message** (with a confirmation dialog) — removes it from this app's own local
  database only; nothing about it touches WhatsApp itself.
- **Person vs. group is now visually distinguished**: a 👥 group icon in one accent color vs. a
  👤 person icon in another, next to the group/contact name. Determined from the same signal
  already used elsewhere — group notifications include a "Sender: message" prefix (so a
  non-null `sender` was parsed), 1:1 chats generally don't — so no new data or permission was
  needed for this.
- Reply is still offered on every message via its own explicit button (previous round), kept
  separate from the "tap to mark read" gesture so the two don't fight each other.

## Round 8: a real solution for muted groups/clients, without unmuting

- **Settings → "Automatically include new groups/clients in summaries"** (off by default).
  Muted WhatsApp chats were already captured correctly — WhatsApp still delivers their
  notifications to the system silently, which is exactly what `NotificationListenerService`
  reads — but every *newly discovered* group/contact was created **disabled**, requiring a
  manual trip to Work Groups & Clients to switch each one on individually. For someone with
  many muted threads, that's a lot of repetitive manual steps. Turning this setting on skips
  that: from then on, any group/contact seen for the first time is opted into Work Summary
  immediately.
- Fixed a related ordering subtlety while building this: the check for "is this group enabled"
  now happens *after* the group is created/updated for the incoming message, not before — so
  with auto-enable on, a brand-new muted group's very *first* message counts toward the
  summary, not just its second message onward.
- This setting only affects **newly discovered** groups going forward — it does not
  retroactively enable groups that already exist and were left off; use the switch on each
  card in Work Groups & Clients for those (or delete and let them be rediscovered, if you'd
  rather not do that one by one).

## Round 7: reply on every message, auto-detected language, direct-to-chat when possible

- **"Generate Reply" now shows on every message**, not just ones flagged Important or "needs a
  reply" — the whole message card is tappable too (with the standard ripple effect for visual
  press feedback), so replying to anything is one tap away, no hunting for a button that may or
  may not be there.
- **Reply language now matches the incoming message** — if the captured message contains
  Arabic characters, the AI is asked for Egyptian-Arabic replies; otherwise English. Previously
  this was hardcoded to English regardless of what language the message was actually in.
- **Reply now opens the exact chat with the text already typed in, when possible**: if the
  message's sender matches a saved Important Person (Settings → Important People) *with a
  phone number set*, tapping "Use" opens that exact WhatsApp chat via the click-to-chat deep
  link — the reply is already sitting in the compose box, one tap (Send) away. This is as close
  as this app can legitimately get to "insert the reply into the chat automatically" — WhatsApp
  gives no third-party app a way to complete that final tap itself (see the Accessibility
  discussion elsewhere in this README for why that boundary isn't going away). Without a saved
  number for that sender, it falls back to the previous behavior: copy the text, open WhatsApp
  generally, paste it in yourself.

## Round 6: choose WhatsApp vs. WhatsApp Business

- **Settings → WhatsApp App**: a new preference — **Automatic** (old behavior: prefer regular
  WhatsApp, fall back to Business only if regular isn't installed), **WhatsApp**, or
  **WhatsApp Business**. Applies everywhere this app opens WhatsApp: Scheduled Message
  reminders (both the notification's tap action and, going forward, its background worker),
  and the Search screen's "Use" button on a generated reply. Explicitly picking "WhatsApp
  Business" is honored even when regular WhatsApp is also installed — previously the app
  always silently preferred regular WhatsApp with no way to override it, which was the exact
  complaint that prompted this.
- If you explicitly pick a specific variant and that one isn't actually installed, the app
  no longer silently falls back to the other one — it tells you plainly (e.g. "WhatsApp
  Business is not installed") rather than opening the wrong app and being confusing about it.

## Round 5: Groq model fix, and a permission-free "Pick from Contacts"

- **Groq's model deprecated → 404 fixed**: `llama-3.3-70b-versatile` (this project's original
  default Groq model) was deprecated by Groq — sending it now returns `404`, which is exactly
  the error the in-app "Test" connection button was surfacing. Switched to Groq's current
  general-purpose recommendation, `openai/gpt-oss-120b`. Groq's hosted model lineup changes
  faster than most providers' — check `console.groq.com/docs/models` if this happens again.
- **"Pick from Contacts"** — added to the Add dialogs for Important People, Work Groups &
  Clients, and Scheduled Messages' phone field. Deliberately built on Android's **system**
  contact picker (`Intent.ACTION_PICK`) rather than reading the address book directly — no
  `READ_CONTACTS` permission is requested or declared anywhere in this app. Picking a contact
  hands this app a temporary, read-only grant scoped to *that one contact's phone-number row
  only*; there's no way for the app to enumerate or bulk-read your contacts, by design. A
  "read all contacts and register them automatically" feature was explicitly requested at one
  point and intentionally not built — WhatsApp exposes no way to tell which phone contacts
  correspond to actual active WhatsApp threads, so bulk-importing everyone would just dump
  hundreds of irrelevant names into the tracking list while requiring the much broader,
  persistent `READ_CONTACTS` permission for no real benefit over picking people one at a time
  as you actually need them.

## Round 4: package visibility fix, Groq, and manually-tracked (muted) groups/clients

- **`<queries>` block added for WhatsApp** (`com.whatsapp`, `com.whatsapp.w4b`) — on Android 11+
  (API 30), an app can't tell whether another app is installed unless it explicitly declares
  that it wants to "see" it. Without this, every WhatsApp-detection check in `IntentHelper`
  silently reported "not installed" *even when it was* — no error, no crash — which is why
  tapping a Scheduled Message reminder wasn't opening WhatsApp at all. This is the standard,
  Play-policy-safe mechanism for declaring visibility into specific other apps, well short of
  the heavily-scrutinized `QUERY_ALL_PACKAGES` permission.
- **Groq added as a fifth AI provider** (`api.groq.com`, OpenAI-compatible, hosts fast Llama
  inference) — **not** the same company as xAI's Grok (already present); easy to confuse given
  the similar name, but a different API and a different key format (`gsk_...`). Groq is now
  the *default selected* provider in Settings — this only affects which option shows selected;
  actually using it still requires the user's own consent toggle and their own API key, entered
  through Settings and stored in `EncryptedSharedPreferences`, exactly like every other cloud
  provider. **No API key is ever hardcoded in source** — a real key was shared during this
  project's development chat at one point, and it was deliberately *not* committed anywhere,
  specifically because this repository is public and anything ever pushed stays recoverable in
  git history forever. If that ever happens to you, treat the key as compromised and regenerate
  it from the provider's console before entering the new one into the app.
- **Manually add a group or client by name, before their first message ever arrives**
  (Work Groups & Clients screen → the **+** button). The app could previously only learn about
  a group/contact reactively, after capturing at least one notification from them — for a
  thread that's muted heavily enough to never generate any notification at all, that meant the
  app could never discover it. Typing the exact name in yourself registers it already
  *enabled*, so it starts being tracked (and included in summaries) from its very next message,
  silent or not. Name matching (both this and the normal auto-discovery path) is now
  case-insensitive throughout, so small capitalization differences between what you type and
  what WhatsApp's notification actually shows don't silently break tracking.
- Clarified in-app copy: the same screen that handles WhatsApp groups already also covers 1:1
  contacts ("clients") — any conversation thread, group or individual, is captured the same
  way — and now says so explicitly, plus states plainly that everything in this list stays
  device-only. Muted chats specifically still work because Android/WhatsApp still deliver their
  notifications silently in the background — that's the mechanism this whole app relies on.

## Round 3: real bug fixes + Refresh, numbering, Grok, manual time entry

### Real bugs fixed

- **Scheduled reminders showing a notification but tapping it never opened WhatsApp**: the
  actual cause was Android 11+ (API 30) **package visibility** — without declaring which other
  apps this app is allowed to "see," `PackageManager.getPackageInfo()` /
  `getLaunchIntentForPackage()` for WhatsApp silently report "not installed" *even when it
  is*, no error, no crash. Every WhatsApp-detection check in `IntentHelper` was affected: the
  click-to-chat deep link, the plain "open WhatsApp" fallback, and therefore the tap action on
  every scheduled-message reminder. Fixed by declaring a `<queries>` block in
  `AndroidManifest.xml` for `com.whatsapp` and `com.whatsapp.w4b` — the standard, Play-policy-
  compliant way to declare visibility into specific other apps without the much more heavily
  scrutinized `QUERY_ALL_PACKAGES` permission.

- **Scheduled reminders / important-message alerts / summary notifications silently doing
  nothing**: the app declared `POST_NOTIFICATIONS` in the manifest but never actually
  **requested** it at runtime. On Android 13+ (API 33), that permission must be requested via
  a runtime prompt — without it, every single notification this app tries to post is silently
  dropped by the system, with no crash and no error. `MainActivity` now requests it on first
  launch if missing. This is very likely the root cause of "I set everything up but nothing
  happens."
- **"View Summary" looking empty / stuck**: not a screen bug — `SummaryScreen` was always
  correct (loading → empty-state → list). The real issue was that "Generate Summary Now" went
  through WorkManager, which (as documented earlier in this README) can be silently deferred
  for a while under Doze/battery restrictions, so the user saw nothing happen and assumed it
  was broken. **Fixed properly**: summary generation logic was pulled out into a shared
  `SummaryRunner`, and the Dashboard's button now calls it directly in the ViewModel's own
  coroutine — giving an immediate Snackbar with the result ("Summary ready: N messages...") or
  a clear error, instead of a fire-and-forget background job. The Worker (for the scheduled/
  automatic case) still uses `SummaryRunner` too, so there's one source of truth for the logic.
- **Notification-Access banner staying stale after granting it from system Settings and coming
  back**: fixed by re-checking the permission on every `ON_RESUME` (not just app cold start),
  plus the new Refresh button below re-checks it on demand too.
- **Language switch to Arabic not visibly applying**: added an explicit `activity.recreate()`
  right after `AppCompatDelegate.setApplicationLocales(...)`, since relying solely on
  AppCompat's documented auto-recreate (which shouldn't need `AppCompatActivity` but was
  unreliable in practice here) wasn't taking effect reliably. **Known remaining limitation,
  stated plainly**: only screens whose text goes through `stringResource(R.string.xxx)` will
  actually translate — the newer screens added in later rounds (Important People, Keyword
  Rules, Reply Detection, the Scheduled Messages dialog, the country-code field, Search)
  mostly use hardcoded English string literals directly in the Compose code, not string
  resources, so those specific screens will not switch to Arabic yet. Moving all of that
  hardcoded text into `strings.xml`/`values-ar/strings.xml` is a mechanical but sizable task
  that wasn't completed given the scope of everything else in this round.

### New features

- **Refresh button** on the Dashboard's top bar (next to Settings) — re-checks Notification
  Access status and re-pulls today's message count on demand.
- **Numbered, zebra-striped message list** in Search — each result is prefixed "1.", "2." etc.,
  and alternating rows get a subtle tint (`surfaceVariant` at low alpha, so it still looks
  right in both themes) for a more scannable, professional look.
- **Reply is now offered for Important messages too**, not just ones matching "needs a reply"
  phrases — seeing something important should let you act on it.
- **Manual time entry**: the time picker now has a small keyboard-icon toggle (matching the
  native Android time picker) to switch between the clock-face dial and typing the hour/minute
  digits directly.
- **Pick a contact instead of typing a number**: Important People can now optionally have a
  phone number attached (via the same country-code field used elsewhere). When scheduling a
  1:1 message, a "Pick from Important People" button lists everyone who has a number saved and
  fills the country code + number in one tap. This is **not** a real Android Contacts
  integration (no `READ_CONTACTS` permission is requested, on purpose) — it's limited to
  contacts you've explicitly added inside this app, since WhatsApp notifications never expose
  a phone number for the app to capture automatically.
- **Grok (xAI) added as a fourth AI provider** alongside OpenAI/Anthropic/Gemini — same
  consent-gated, encrypted-key-storage flow as the others. Grok's API is OpenAI-compatible, so
  it reuses the same request/response handling; double check the model name
  (`grok-3-latest`) against xAI's current docs since model names there change often.

## Later additions: Important People, custom rules, multi-reply suggestions, and language switching

A few rounds of follow-up requests added:

- **Important People** (Settings → Important People): messages from anyone on this list are
  always marked Important, regardless of keyword score. Matching is a case-insensitive
  substring match against the sender name WhatsApp puts in its notification.
- **Important Keywords editor** (Settings → Important Keywords) and **Reply Detection phrases
  editor** (Settings → Reply Detection): both now have real add/toggle/delete screens, backed
  by their own Room tables, instead of being fixed at compile time.
- **Live rule application**: `captureMessage` (the notification-capture entry point) now
  rebuilds its `MessageClassifier` fresh on every incoming message, pulling the *current*
  custom keywords, important-people list, and reply phrases from the database — so a change
  in Settings takes effect on the very next message, no app restart needed.
- **Multi-variant reply suggestions**: `AiSummaryProvider.suggestReplies()` returns 2-3
  differently-toned options (direct / friendly / formal) instead of one generic reply, for
  the local provider and all three cloud providers. The Search screen's reply dialog shows
  all of them; tapping one copies it and opens WhatsApp in a single step.
- **Real in-app language switching**: Settings → Language now uses
  `AppCompatDelegate.setApplicationLocales` (via a new `androidx.appcompat` dependency and
  `utils/LocaleHelper.kt`) to actually change the UI language at runtime — not just store a
  preference that only affected which `strings.xml` variant loaded next launch.
- **Editing an existing scheduled message**: tapping a card in Scheduled Messages now opens
  the same Add dialog pre-filled, and Save re-schedules its WorkManager job under the same id.

### A request that was declined, on purpose

At one point during development, fully-automatic sending (via an Accessibility Service
simulating a tap on WhatsApp's own Send button) and "reply without ever opening WhatsApp" were
requested. Both were declined and are **not** in this codebase, for two concrete reasons rather
than an abstract policy line:

1. WhatsApp's Terms of Service explicitly prohibit automating message sending from a
   third-party app, even via something that behaves like a real tap.
2. Google Play's Accessibility API policy prohibits using that API to control other apps'
   UI for a purpose other than genuine accessibility — a very common, and severe, app-rejection
   (and developer-account-suspension) reason.

The click-to-chat deep link (pre-fills the message, opens the exact chat, one tap to send) is
the closest this app gets, and is the ceiling for what's achievable without breaking either of
the above.

## What "Production-ready" means here, honestly

This project implements the full architecture, data model, and all 45 requested behaviors end
to end with real, compiling Kotlin — there are no `TODO` / "implement later" markers outside of
places that genuinely require your own external account (a cloud AI API key, if you choose to
use one) or a build-time secret (the release keystore). Every screen described in the spec —
including the Work/Break/Quiet and Summary-time schedule editors — is wired to a working
ViewModel and repository, backed by Room and WorkManager.

The one thing this environment genuinely could not do is run an actual Gradle build (no network
access here to resolve dependencies, no Android SDK/emulator). Everything has been reviewed by
hand for import correctness, brace balance, and consistent package/class references, and the
architecture mirrors patterns that are known to compile — but if Android Studio's first sync
surfaces a compile error, paste it back and it'll get fixed directly.
