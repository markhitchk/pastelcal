from pathlib import Path

path = Path("app/src/main/java/com/pastelcal/app/MainActivity.kt")
text = path.read_text(encoding="utf-8")
original = text

replacements = [
    (
        '    CALENDAR("Calendar", Icons.Default.CalendarMonth),\n    TASKS("Tasks", Icons.Default.CheckCircle),',
        '    CALENDAR("Calendar", Icons.Default.CalendarMonth),\n    PERIOD("Period", Icons.Default.Favorite),\n    TASKS("Tasks", Icons.Default.CheckCircle),'
    ),
    (
        '                AppTab.entries.forEach { tab ->',
        '                AppTab.entries.filter { it != AppTab.SEARCH }.forEach { tab ->'
    ),
    (
        '''        floatingActionButton = {\n            ExtendedFloatingActionButton(\n                onClick = { create(if (selected == AppTab.TASKS) ItemKind.TASK else ItemKind.EVENT) },\n                icon = { Icon(Icons.Default.Add, contentDescription = null) },\n                text = { Text(if (selected == AppTab.TASKS) "New task" else "New") }\n            )\n        }''',
        '''        floatingActionButton = {\n            if (selected == AppTab.TODAY || selected == AppTab.CALENDAR || selected == AppTab.TASKS) {\n                ExtendedFloatingActionButton(\n                    onClick = { create(if (selected == AppTab.TASKS) ItemKind.TASK else ItemKind.EVENT) },\n                    icon = { Icon(Icons.Default.Add, contentDescription = null) },\n                    text = { Text(if (selected == AppTab.TASKS) "New task" else "New") }\n                )\n            }\n        }'''
    ),
    (
        '''                AppTab.TASKS -> TasksScreen(\n                    tasks = allItems.filter { it.kind == ItemKind.TASK },''',
        '''                AppTab.PERIOD -> PeriodTrackerScreen(\n                    settings = settings,\n                    entries = cycleEntries,\n                    onSettingsChange = onSettingsChange,\n                    onStartPeriod = vm::startPeriod,\n                    onEndPeriod = vm::endPeriod,\n                    onDeleteCycleEntry = vm::deleteCycleEntry,\n                    onMessage = { scope.launch { snackbarHostState.showSnackbar(it) } }\n                )\n                AppTab.TASKS -> TasksScreen(\n                    tasks = allItems.filter { it.kind == ItemKind.TASK },'''
    ),
]

for old, new in replacements:
    if new in text:
        continue
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"Expected exactly one match, found {count}: {old[:80]!r}")
    text = text.replace(old, new, 1)

if text == original:
    print("Period Tracker navigation is already patched.")
else:
    path.write_text(text, encoding="utf-8")
    print("Patched MainActivity.kt with dedicated Period Tracker navigation.")
