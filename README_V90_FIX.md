# WA premium v90 – Auto Reply multi-client fix

- Premium Auto Reply no longer uses `firstOrNull`; all independently matching client rules are processed.
- Duplicate rules for the same configured client are collapsed to one reply, preferring a keyword-specific rule and then the newest rule.
- Matching supports both the visible client name and the saved phone number, with formatting ignored.
- Each non-auto-send matched rule gets its own notification ID so one client's notification cannot replace another's.
- Auto-send queue keys include the rule ID so different clients/rules cannot collide.
- Added regression tests for multiple clients, rule specificity, and phone-number matching.
