# PastelCal release changelog

## 1.1.0 — Cycle Tracking

- Added optional local-only cycle tracking, disabled by default.
- Added Room v4 `cycle_entries` storage with a non-destructive v3 → v4 migration.
- Added period start/end logging from the month calendar.
- Added personalized period predictions using up to 12 recent valid cycles, recency weighting, outlier rejection, variability ranges, and confidence labels.
- Added learned period-duration estimates from completed records.
- Added recorded/predicted cycle markers to month cells.
- Added an optional estimated fertile-window layer with an in-app safety disclaimer.
- Added cycle data and cycle settings to JSON backup/restore schema v3.
- Backup/restore now also preserves the selected custom accent color.
- Release application ID is `com.pastelcal.app.final` to match the install-fix lineage.
- Version code: 1010000.

## 1.0.0

### Release hardening
- Promoted the project from `0.4.0-alpha01` to `1.0.0`.
- Targeted Android 16 / API 36.
- Updated Android Gradle Plugin to 8.13.2.
- Added release minification/resource shrinking and conservative Room keep rules.
- Added adaptive/legacy launcher branding and Android 12+ splash branding.

### Recurring-series editing
- Added recurrence end dates and exclusion dates.
- Added Room v2 → v3 migration without destructive fallback.
- Added edit/delete scopes for one occurrence, this-and-future, or an entire series.
- Added split-series parent metadata.
- Added ICS `UNTIL` and `EXDATE` transfer support.

### Reliability and privacy
- Added local crash capture and privacy-safe diagnostics export.
- Added import/backup size and schema guards.
- Disabled platform Auto Backup in favor of explicit user-controlled backup files.
- Disabled cleartext network traffic.

### Planner polish
- Added timed overlap warnings.
- Improved all-day Calendar Provider import/export.
- Added database indexes and background model mapping.
- Improved tablet/foldable content sizing.
- Increased interactive pastel targets for accessibility.
