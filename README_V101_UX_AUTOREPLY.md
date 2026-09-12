# WA premium v101

- Dashboard notification surfaces now use a deterministic left-swipe reveal interaction with a visible Delete action. Partial swipes snap cleanly to either closed or the delete affordance; no coroutine is launched for each pointer movement.
- Delete can be tapped manually after revealing it, and the same interaction is used for scheduled dashboard cards.
- Auto Reply now supports a global rule by leaving People blank.
- Auto Reply People accepts multiple names in one field, separated by commas, semicolons, or new lines; contact picking appends names rather than replacing them.
- Updated copy to explain global/multi-person behavior.
- Scheduled Messages contact button label is now `Pick number` / `اختار رقم` (no `Or`).
