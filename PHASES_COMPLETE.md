# PastelCal — completed roadmap through 1.0

PastelCal 1.0.0 consolidates the planned Phase 1–4 work and the final release-hardening pass.

## Phase 1 — application foundation
- Kotlin + Jetpack Compose + Material 3 application shell
- Pastel design system and primary navigation
- Today, Calendar, Tasks, Search, and Settings surfaces
- Quick-add flow

## Phase 2 — persistent calendar
- Room-backed events/tasks/reminders
- Real date/time editing
- Categories, notes, locations, recurrence, and Calendar Provider integration

## Phase 3 — reminders and widgets
- Alarm scheduling and notification actions
- Reboot/time-change rescheduling
- Recurrence expansion
- Persistent appearance preferences
- Agenda widget and Android calendar selection

## Phase 4 — planner and transfer tools
- Day / 3-Day / Week timelines
- Drag/reschedule foundation
- Multiple reminders
- Per-occurrence recurring-task completion
- ICS import/export, backup/restore, notification history
- Widget customization

## 1.0 release hardening
- This occurrence / this and future / entire-series edits and deletes
- Recurrence end dates and excluded occurrences
- Non-destructive Room v2→v3 migration
- Schedule-overlap warnings
- True all-day Calendar Provider export and recurrence exceptions
- Local crash capture and privacy-safe diagnostics export
- Import/restore size and schema guards
- Tablet/foldable content sizing and accessibility touch-target improvements
- Adaptive launcher branding and release build configuration
- API 36 target/compile configuration
- Unit-test coverage plus standalone recurrence/ICS/conflict validation

A signed release APK/AAB still requires an Android SDK/Gradle build environment and a private release signing key. Those secrets are intentionally not included in this package.
