from pathlib import Path

main_path = Path("app/src/main/java/com/pastelcal/app/MainActivity.kt")
build_path = Path("app/build.gradle.kts")

main = main_path.read_text(encoding="utf-8")

anchor = '''        item { ReleaseSettingsPanel(onMessage = onMessage) }\n\n        item {\n            Text(\n                "PastelCal 1.1.0 · Calendar and optional cycle data stay local unless you explicitly import, export, or back up data.",'''

replacement = '''        item { ReleaseSettingsPanel(onMessage = onMessage) }\n\n        item { SectionTitle("Author's Note") }\n        item {\n            Surface(\n                shape = RoundedCornerShape(24.dp),\n                color = Mint.copy(alpha = .22f),\n                modifier = Modifier.fillMaxWidth()\n            ) {\n                Column(\n                    Modifier.fillMaxWidth().padding(18.dp),\n                    verticalArrangement = Arrangement.spacedBy(10.dp)\n                ) {\n                    Row(verticalAlignment = Alignment.CenterVertically) {\n                        Text("🐸", fontSize = 30.sp)\n                        Spacer(Modifier.width(10.dp))\n                        Column {\n                            Text("For Emma", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)\n                            Text("A little note from the author", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)\n                        }\n                    }\n                    Text(\n                        "Emma, I love you so much. You make my days brighter just by being you. I know how much you love frogs, so I wanted this little corner of PastelCal to always carry one for you. No matter how busy life gets or how full the calendar becomes, you mean more to me than I could ever fit into words. 🐸💚",\n                        style = MaterialTheme.typography.bodyLarge\n                    )\n                    Text(\n                        "— From the author 💚",\n                        style = MaterialTheme.typography.bodyMedium,\n                        fontWeight = FontWeight.SemiBold,\n                        color = MaterialTheme.colorScheme.onSurfaceVariant\n                    )\n                }\n            }\n        }\n\n        item {\n            Text(\n                "PastelCal 1.1.2 · Calendar and optional cycle data stay local unless you explicitly import, export, or back up data.",'''

if 'Text("For Emma",' not in main:
    if anchor not in main:
        anchor_111 = anchor.replace("PastelCal 1.1.0", "PastelCal 1.1.1")
        if anchor_111 not in main:
            raise SystemExit("Could not find Settings footer insertion point")
        anchor = anchor_111
    main = main.replace(anchor, replacement, 1)
else:
    main = main.replace("PastelCal 1.1.0 ·", "PastelCal 1.1.2 ·").replace("PastelCal 1.1.1 ·", "PastelCal 1.1.2 ·")

main_path.write_text(main, encoding="utf-8")

build = build_path.read_text(encoding="utf-8")
build = build.replace("versionCode = 1010001", "versionCode = 1010002")
build = build.replace('versionName = "1.1.1"', 'versionName = "1.1.2"')
if "versionCode = 1010002" not in build or 'versionName = "1.1.2"' not in build:
    raise SystemExit("Version bump to 1.1.2 did not apply")
build_path.write_text(build, encoding="utf-8")

print("Added Emma author note and bumped PastelCal to 1.1.2 / 1010002")
