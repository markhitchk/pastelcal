# PastelCal Cycle Tracking

## Prediction model

PastelCal does not assume every user has a 28-day cycle once enough history exists. It derives consecutive period-start intervals, keeps 15–120-day intervals as a broad prediction sanity range, uses up to the 12 most recent intervals, rejects large outliers when there is enough history, and weights newer intervals more heavily.

The UI exposes a likely-start range and confidence level. A single logged period uses a 28-day low-confidence bootstrap estimate only until personal history is available.

Completed period records are used to learn the user's usual period duration (1–14 day accepted range).

## Fertile-window estimate

The optional fertile-window layer is off by default. When enabled and at least two usable cycle intervals exist, PastelCal estimates ovulation as 14 days before the predicted next period and marks the preceding 5 days through 1 day after that estimate. This is only a calendar estimate; it cannot detect actual ovulation and must not be represented as reliable contraception.

## Privacy

Cycle tracking is disabled by default. Records live in the local `pastelcal.db` Room database. Android Auto Backup remains disabled. Cycle records leave the device only if the user explicitly creates a PastelCal JSON backup and then moves that file elsewhere.
