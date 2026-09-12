# WA premium v88 changes

- Added Delete action to AI Reply notifications.
- Added notification delete intent so swiping the AI Reply notification away dismisses it cleanly.
- Added per-person AI reply history persisted in Room.
- Added `Show replies (N)` next to each Auto Reply person.
- History dialog shows timestamp, incoming message, and the exact AI-generated reply used.
- Auto Reply screen layout was spaced and grouped into dedicated surfaces to prevent fields from visually running together.
- Auto Reply Add FAB remains fixed while the list reserves bottom space so cards/notifications do not sit underneath it.
- Room database version bumped to 14; this pre-release project uses destructive migration fallback.
