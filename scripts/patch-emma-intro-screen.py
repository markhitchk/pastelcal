from pathlib import Path

main_path = Path("app/src/main/java/com/pastelcal/app/MainActivity.kt")
build_path = Path("app/build.gradle.kts")

main = main_path.read_text(encoding="utf-8")

old_theme = '''            PastelCalTheme(themeMode = settings.themeMode, dynamicColors = settings.dynamicColors, accentColor = settings.accentColor) {\n                PastelCalApp(settings = settings, onSettingsChange = ::updateSettings)\n            }'''
new_theme = '''            PastelCalTheme(themeMode = settings.themeMode, dynamicColors = settings.dynamicColors, accentColor = settings.accentColor) {\n                var showEmmaIntro by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(true) }\n                if (showEmmaIntro) {\n                    EmmaIntroScreen(onContinue = { showEmmaIntro = false })\n                } else {\n                    PastelCalApp(settings = settings, onSettingsChange = ::updateSettings)\n                }\n            }'''
if new_theme not in main:
    if old_theme not in main:
        raise SystemExit("Could not find PastelCalTheme launch block")
    main = main.replace(old_theme, new_theme, 1)

note_block = '''\n        item { SectionTitle("Author's Note") }\n        item {\n            Surface(\n                shape = RoundedCornerShape(24.dp),\n                color = Mint.copy(alpha = .22f),\n                modifier = Modifier.fillMaxWidth()\n            ) {\n                Column(\n                    Modifier.fillMaxWidth().padding(18.dp),\n                    verticalArrangement = Arrangement.spacedBy(10.dp)\n                ) {\n                    Row(verticalAlignment = Alignment.CenterVertically) {\n                        Text("🐸", fontSize = 30.sp)\n                        Spacer(Modifier.width(10.dp))\n                        Column {\n                            Text("For Emma", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)\n                            Text("A little note from the author", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)\n                        }\n                    }\n                    Text(\n                        "Emma, I love you so much. You make my days brighter just by being you. I know how much you love frogs, so I wanted this little corner of PastelCal to always carry one for you. No matter how busy life gets or how full the calendar becomes, you mean more to me than I could ever fit into words. 🐸💚",\n                        style = MaterialTheme.typography.bodyLarge\n                    )\n                    Text(\n                        "— From the author 💚",\n                        style = MaterialTheme.typography.bodyMedium,\n                        fontWeight = FontWeight.SemiBold,\n                        color = MaterialTheme.colorScheme.onSurfaceVariant\n                    )\n                }\n            }\n        }\n'''
if note_block in main:
    main = main.replace(note_block, "\n", 1)

main = main.replace("PastelCal 1.1.2 ·", "PastelCal 1.1.3 ·")
main_path.write_text(main, encoding="utf-8")

build = build_path.read_text(encoding="utf-8")
build = build.replace("versionCode = 1010002", "versionCode = 1010003")
build = build.replace('versionName = "1.1.2"', 'versionName = "1.1.3"')
if "versionCode = 1010003" not in build or 'versionName = "1.1.3"' not in build:
    raise SystemExit("Version bump to 1.1.3 did not apply")
build_path.write_text(build, encoding="utf-8")

print("Moved Emma dedication to launch screen and bumped PastelCal to 1.1.3 / 1010003")
