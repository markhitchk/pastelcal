# PastelCal 1.1.0

PastelCal is a local-first Android calendar and planner with a soft pastel Material 3 interface.

## Release status

This source package finishes the Phase 1–4 prototype work and the Phase 5/6 release-hardening pass as **PastelCal 1.1.0** with optional local cycle tracking.

- Application ID: `com.pastelcal.app.final`
- Minimum Android: Android 8.0 / API 26
- Compile SDK: API 36
- Target SDK: API 36
- Version: `1.1.0`
- Version code: `1010000`
- Language/UI: Kotlin + Jetpack Compose + Material 3
- Local database: Room v4

## Core calendar

- Today dashboard
- Month calendar
- Day, 3-Day, and Week planner views
- Events, tasks, and reminders
- Search
- Pastel categories
- Notes and locations
- Multiple reminder offsets
- Schedule-overlap warnings
- Long-press timeline rescheduling in 15-minute steps


## Optional cycle tracking

Cycle tracking is disabled by default and stores records in the same local Room database as the rest of PastelCal.

- Log a period start or end from a selected calendar date
- Recorded period days are shown directly on the month calendar
- Personalized next-period estimate from recent cycle history
- Recent cycles are weighted more heavily than older cycles
- Obvious cycle-length outliers are filtered when enough history is available
- Likely-start range and Low / Medium / High confidence are shown instead of a false exact date
- Period duration is learned from completed period records
- Optional calendar-only fertile-window estimate, disabled by default
- Fertile-window estimates are explicitly labeled as estimates and not suitable as contraception
- Cycle history is included in PastelCal JSON backup/restore

## Recurring items

- Daily, weekly, monthly, and yearly recurrence
- Optional recurrence end date
- Per-occurrence exclusions
- Per-occurrence recurring-task completion
- Edit/delete scope:
  - This occurrence
  - This and future
  - Entire series
- Split-series storage avoids generating duplicate rows for every recurrence

## Reminders

- Native AlarmManager scheduling
- Exact alarms when Android permits them, inexact fallback otherwise
- Android 13+ notification permission flow
- Snooze action
- Done action for task reminders
- Rescheduling after reboot, time/timezone changes, app replacement, and exact-alarm permission changes
- Local reminder-action history

## Android calendar integration

Device-calendar integration is optional.

- Select a writable Android calendar
- Import upcoming Calendar Provider instances
- Export timed and all-day events
- Export recurrence rules, recurrence end dates, exception dates, and multiple reminders
- Duplicate protection for imported provider events

## Transfer and backup

- ICS import/export through Android's Storage Access Framework
- `RRULE`, `UNTIL`, `EXDATE`, and `VALARM` support
- Local JSON backup/restore
- Backup includes settings and recurring-task occurrence completion
- Restore schema/version checks
- 20 MB transfer guard and 100,000-item guard
- No broad storage permission

## Home-screen widget

Resizable agenda widget with:

- Upcoming items
- Recurring-event expansion
- Adjustable transparency
- Adjustable text size
- Pastel background selector
- Optional completed-task visibility
- Manual/automatic refresh

## Appearance and accessibility

- System / Light / Dark appearance
- Optional Material You dynamic colors
- Compact calendar
- Week numbers
- Sunday/Monday week start
- System font scaling is respected by Compose
- 48dp interactive pastel color targets in editing/customization controls
- Main content width is capped on tablets/foldables for readability

## Diagnostics and privacy

- Local uncaught-crash capture
- View/clear last local crash
- Copy/export diagnostics
- Diagnostics intentionally omit event titles, notes, locations, calendar account names, and reminder contents
- No analytics SDK
- No PastelCal account
- `usesCleartextTraffic=false`
- Android Auto Backup disabled; use PastelCal's explicit backup flow instead

## Release build

The release build enables R8 minification and resource shrinking. Signing credentials are intentionally not included in source control.

Android Studio with Android 16 / API 36 SDK is required. The project is configured for AGP `8.13.2`; Gradle 8.13 (for Android Gradle Plugin 8.13.2) is required when a Gradle Wrapper is not present.

Typical release build after Android SDK + Gradle are installed:

```bash
gradle :app:assembleRelease
```

Without a signing configuration this produces an unsigned release artifact, which should be signed with your own private release key.

## Database migrations

PastelCal keeps existing local data through explicit Room migrations:

- v1 → v2: multiple reminders, recurring occurrence completion, notification history
- v2 → v3: recurrence end dates, excluded recurrence dates, split-series parent metadata, query indexes
- v3 → v4: local cycle-entry history and unique period-start index

No destructive migration is configured.

## Validation performed for this package

- Pure Kotlin recurrence tests, including month-end recurrence
- Recurrence end + exclusion tests
- ICS recurrence/end/EXDATE round-trip
- Schedule-overlap detector test
- XML parsing for manifest/resources
- Kotlin delimiter/source-structure scan
- Room migration source review
- Stale-version and packaging checks

A full Android APK/AAB compile and device instrumentation run could not be performed in this environment because the Android SDK and Gradle executable are not installed here.
