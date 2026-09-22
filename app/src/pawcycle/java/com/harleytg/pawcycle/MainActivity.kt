package com.harleytg.pawcycle

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

private val Red = Color(0xFFE93645)
private val HotRed = Color(0xFFFF4D5D)
private val NearBlack = Color(0xFF120D0F)
private val Panel = Color(0xFF21171A)
private val Panel2 = Color(0xFF2B1B1F)
private val SoftPink = Color(0xFFFFA2AB)
private val Muted = Color(0xFFBDA9AD)

private enum class Screen { Home, Calendar, Log, Insights, Settings }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val store = PawStore(this)
        setContent { PawCycleApp(store) }
    }
}

private class PawStore(context: Context) {
    private val p = context.getSharedPreferences("pawcycle_data", Context.MODE_PRIVATE)
    val today: LocalDate get() = LocalDate.now()

    var onboardingDone: Boolean
        get() = p.getBoolean("onboarding_done", false)
        set(v) = p.edit().putBoolean("onboarding_done", v).apply()

    var cycleLength: Int
        get() = p.getInt("cycle_length", 28)
        set(v) = p.edit().putInt("cycle_length", v.coerceIn(20, 45)).apply()

    var periodLength: Int
        get() = p.getInt("period_length", 5)
        set(v) = p.edit().putInt("period_length", v.coerceIn(1, 10)).apply()

    var lastPeriodStart: LocalDate
        get() = runCatching {
            LocalDate.parse(p.getString("last_period_start", today.minusDays(1).toString()))
        }.getOrDefault(today.minusDays(1))
        set(v) = p.edit().putString("last_period_start", v.toString()).apply()

    private fun key(name: String, date: LocalDate = today) = "${name}_${date}"

    fun flow(date: LocalDate = today): String = p.getString(key("flow", date), "Medium") ?: "Medium"
    fun mood(date: LocalDate = today): String = p.getString(key("mood", date), "Calm") ?: "Calm"
    fun note(date: LocalDate = today): String = p.getString(key("note", date), "") ?: ""
    fun symptoms(date: LocalDate = today): Set<String> = p.getStringSet(key("symptoms", date), emptySet())?.toSet() ?: emptySet()

    fun saveToday(flow: String, mood: String, symptoms: Set<String>, note: String) {
        p.edit()
            .putString(key("flow"), flow)
            .putString(key("mood"), mood)
            .putStringSet(key("symptoms"), symptoms.toSet())
            .putString(key("note"), note)
            .apply()
        if (flow != "None") {
            val gap = ChronoUnit.DAYS.between(lastPeriodStart, today)
            if (gap > periodLength + 4) lastPeriodStart = today
        }
    }

    fun cycleDay(date: LocalDate = today): Int {
        val diff = ChronoUnit.DAYS.between(lastPeriodStart, date)
        val mod = ((diff % cycleLength) + cycleLength) % cycleLength
        return mod.toInt() + 1
    }

    fun predictedPeriod(date: LocalDate): Boolean {
        val diff = ChronoUnit.DAYS.between(lastPeriodStart, date)
        val mod = ((diff % cycleLength) + cycleLength) % cycleLength
        return mod in 0 until periodLength.toLong()
    }

    fun nextPeriod(): LocalDate {
        var next = lastPeriodStart.plusDays(cycleLength.toLong())
        while (!next.isAfter(today)) next = next.plusDays(cycleLength.toLong())
        return next
    }

    fun exportText(): String = """
        {
          "app": "PawCycle",
          "exported": "${today}",
          "cycleLength": ${cycleLength},
          "periodLength": ${periodLength},
          "lastPeriodStart": "${lastPeriodStart}",
          "today": {
            "flow": "${flow()}",
            "mood": "${mood()}",
            "symptoms": "${symptoms().joinToString(", ")}",
            "note": "${note().replace(""", "\\"")}"
          }
        }
    """.trimIndent()
}

@Composable
private fun PawCycleApp(store: PawStore) {
    var screen by remember { mutableStateOf(Screen.Home) }
    var refresh by remember { mutableIntStateOf(0) }
    var onboarded by remember { mutableStateOf(store.onboardingDone) }

    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = HotRed,
            secondary = SoftPink,
            background = NearBlack,
            surface = Panel,
            onPrimary = Color.White,
            onBackground = Color.White,
            onSurface = Color.White
        )
    ) {
        Surface(Modifier.fillMaxSize(), color = NearBlack) {
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(listOf(Color(0xFF19090D), NearBlack, Color(0xFF0C090A)))
                )
            ) {
                if (!onboarded) {
                    Onboarding {
                        store.onboardingDone = true
                        onboarded = true
                    }
                } else {
                    BoxWithConstraints(Modifier.fillMaxSize()) {
                        val tablet = maxWidth >= 700.dp
                        Row(Modifier.fillMaxSize()) {
                            if (tablet) {
                                PawRail(screen) { screen = it }
                            }
                            Scaffold(
                                modifier = Modifier.weight(1f),
                                containerColor = Color.Transparent,
                                bottomBar = {
                                    if (!tablet) PawBottomBar(screen) { screen = it }
                                }
                            ) { padding ->
                                Box(Modifier.fillMaxSize().padding(padding)) {
                                    when (screen) {
                                        Screen.Home -> HomeScreen(store, tablet, refresh) { screen = Screen.Log }
                                        Screen.Calendar -> CalendarScreen(store, tablet, refresh)
                                        Screen.Log -> LogScreen(store, tablet, refresh) {
                                            refresh++
                                            screen = Screen.Home
                                        }
                                        Screen.Insights -> InsightsScreen(store, tablet, refresh)
                                        Screen.Settings -> SettingsScreen(store, tablet, refresh, onChanged = { refresh++ }) {
                                            store.onboardingDone = false
                                            onboarded = false
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Onboarding(onDone: () -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize().padding(24.dp)) {
        val tablet = maxWidth >= 700.dp
        if (tablet) {
            Row(
                Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(36.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Mascots(Modifier.weight(1f).heightIn(max = 520.dp))
                OnboardingCopy(Modifier.weight(1f), onDone)
            }
        } else {
            LazyColumn(
                Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                item { BrandTitle() }
                item { Spacer(Modifier.height(18.dp)) }
                item { Mascots(Modifier.fillMaxWidth().height(250.dp)) }
                item { Spacer(Modifier.height(18.dp)) }
                item { OnboardingCopy(Modifier.fillMaxWidth(), onDone, showTitle = false) }
            }
        }
    }
}

@Composable
private fun OnboardingCopy(modifier: Modifier, onDone: () -> Unit, showTitle: Boolean = true) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        if (showTitle) BrandTitle()
        Text("Track • Understand • Feel Your Best", color = SoftPink, textAlign = TextAlign.Center)
        Spacer(Modifier.height(24.dp))
        FeatureLine("♥", "Track your cycle, flow, symptoms and mood")
        FeatureLine("🐾", "Private local storage — no account required")
        FeatureLine("🦖", "Cute cat + dinosaur comfort theme")
        FeatureLine("▦", "Responsive phone and tablet layouts")
        Spacer(Modifier.height(28.dp))
        Button(
            onClick = onDone,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(20.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Red)
        ) { Text("Get Started", fontWeight = FontWeight.Bold, fontSize = 17.sp) }
        Spacer(Modifier.height(12.dp))
        Text("Cycle estimates are informational and are not medical advice.", color = Muted, fontSize = 12.sp, textAlign = TextAlign.Center)
    }
}

@Composable
private fun FeatureLine(icon: String, text: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(icon, color = HotRed, fontSize = 22.sp)
        Spacer(Modifier.width(12.dp))
        Text(text, color = Color.White)
    }
}

@Composable
private fun BrandTitle() {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
        Text("🐾", fontSize = 32.sp)
        Spacer(Modifier.width(8.dp))
        Text("Paw", fontSize = 34.sp, fontWeight = FontWeight.Black)
        Text("Cycle", fontSize = 34.sp, fontWeight = FontWeight.Black, color = HotRed)
    }
}

@Composable
private fun Mascots(modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        Image(
            painterResource(R.drawable.mascot_cat),
            contentDescription = "Cat mascot",
            modifier = Modifier.weight(1f).fillMaxHeight(),
            contentScale = ContentScale.Fit
        )
        Image(
            painterResource(R.drawable.mascot_dino),
            contentDescription = "Dinosaur mascot",
            modifier = Modifier.weight(1f).fillMaxHeight(),
            contentScale = ContentScale.Fit
        )
    }
}

@Composable
private fun HomeScreen(store: PawStore, tablet: Boolean, refresh: Int, onLog: () -> Unit) {
    val day = store.cycleDay()
    val flow = store.flow()
    val mood = store.mood()
    val symptoms = store.symptoms()
    val dateText = store.today.format(DateTimeFormatter.ofPattern("EEEE, MMMM d"))

    if (tablet) {
        Column(Modifier.fillMaxSize().padding(28.dp)) {
            Header("Good ${if (java.time.LocalTime.now().hour < 12) "Morning" else "Evening"}! ♥", dateText)
            Spacer(Modifier.height(18.dp))
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(22.dp)) {
                LazyColumn(Modifier.weight(1.25f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    item { WeekStrip(store.today) }
                    item { CycleHero(day, flow, Modifier.fillMaxWidth().height(390.dp)) }
                }
                LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    item { DashboardCard("Flow", flow, "●") }
                    item { DashboardCard("Mood", mood, "☺") }
                    item { DashboardCard("Symptoms", if (symptoms.isEmpty()) "None logged" else symptoms.size.toString(), "♥") }
                    item { DashboardCard("Cycle Day", "$day / ${store.cycleLength}", "▦") }
                    item {
                        Button(
                            onClick = onLog,
                            modifier = Modifier.fillMaxWidth().height(58.dp),
                            shape = RoundedCornerShape(20.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Red)
                        ) { Text("Log Today", fontWeight = FontWeight.Bold) }
                    }
                    item { MascotMessage("Take care of you", "Small steps. Brighter days.") }
                }
            }
        }
    } else {
        LazyColumn(
            Modifier.fillMaxSize().padding(horizontal = 18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(top = 18.dp, bottom = 24.dp)
        ) {
            item { Header("PawCycle", dateText) }
            item { WeekStrip(store.today) }
            item { CycleHero(day, flow, Modifier.fillMaxWidth().height(310.dp)) }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    MiniCard("Flow", flow, Modifier.weight(1f))
                    MiniCard("Mood", mood, Modifier.weight(1f))
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    MiniCard("Symptoms", if (symptoms.isEmpty()) "None" else "${symptoms.size}", Modifier.weight(1f))
                    MiniCard("Cycle", "$day / ${store.cycleLength}", Modifier.weight(1f))
                }
            }
            item {
                Button(
                    onClick = onLog,
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(20.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Red)
                ) { Text("Log Today", fontWeight = FontWeight.Bold) }
            }
            item { MascotMessage("You’ve got this ♥", "Your cat + dino comfort crew is here.") }
        }
    }
}

@Composable
private fun Header(title: String, subtitle: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 25.sp, fontWeight = FontWeight.ExtraBold)
            Text(subtitle, color = Muted, fontSize = 13.sp)
        }
        Text("🐾", fontSize = 28.sp)
    }
}

@Composable
private fun WeekStrip(today: LocalDate) {
    val start = today.minusDays(3)
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Panel).padding(12.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        (0L..6L).forEach { offset ->
            val date = start.plusDays(offset)
            val active = date == today
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(date.dayOfWeek.name.take(1), color = if (active) SoftPink else Muted, fontSize = 11.sp)
                Spacer(Modifier.height(5.dp))
                Box(
                    Modifier.size(38.dp).clip(CircleShape).background(if (active) Red else Color.Transparent),
                    contentAlignment = Alignment.Center
                ) { Text(date.dayOfMonth.toString(), fontWeight = if (active) FontWeight.Bold else FontWeight.Normal) }
            }
        }
    }
}

@Composable
private fun CycleHero(day: Int, flow: String, modifier: Modifier) {
    Box(
        modifier.clip(RoundedCornerShape(28.dp))
            .background(Brush.radialGradient(listOf(Color(0xFF6A111C), Color(0xFF2A1116), Panel)))
            .border(2.dp, Red, RoundedCornerShape(28.dp)),
        contentAlignment = Alignment.Center
    ) {
        Box(
            Modifier.fillMaxHeight(0.72f).aspectRatio(1f).border(8.dp, Red, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Today", color = SoftPink)
                Text("Day $day", fontSize = 38.sp, fontWeight = FontWeight.Black)
                Text(if (flow == "None") "Cycle" else "Period", fontSize = 18.sp)
                Text("Flow: $flow", color = Muted, fontSize = 13.sp)
                Spacer(Modifier.height(8.dp))
                Mascots(Modifier.fillMaxWidth(0.64f).height(90.dp))
            }
        }
    }
}

@Composable
private fun MiniCard(label: String, value: String, modifier: Modifier) {
    Column(
        modifier.clip(RoundedCornerShape(18.dp)).background(Panel).padding(15.dp)
    ) {
        Text(label, color = Muted, fontSize = 12.sp)
        Spacer(Modifier.height(4.dp))
        Text(value, fontWeight = FontWeight.Bold, fontSize = 18.sp)
    }
}

@Composable
private fun DashboardCard(label: String, value: String, icon: String) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Panel).padding(17.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(46.dp).clip(CircleShape).background(Color(0xFF4B171D)), contentAlignment = Alignment.Center) {
            Text(icon, color = HotRed, fontSize = 24.sp)
        }
        Spacer(Modifier.width(14.dp))
        Column {
            Text(label, color = Muted, fontSize = 12.sp)
            Text(value, fontWeight = FontWeight.Bold, fontSize = 19.sp)
        }
    }
}

@Composable
private fun LogScreen(store: PawStore, tablet: Boolean, refresh: Int, onSaved: () -> Unit) {
    var flow by remember(refresh) { mutableStateOf(store.flow()) }
    var mood by remember(refresh) { mutableStateOf(store.mood()) }
    val symptoms = remember(refresh) { mutableStateListOf<String>().apply { addAll(store.symptoms()) } }
    var note by remember(refresh) { mutableStateOf(store.note()) }

    val content: @Composable () -> Unit = {
        Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Header("Log Today", store.today.format(DateTimeFormatter.ofPattern("EEEE, MMM d, yyyy")))
            SectionTitle("Flow")
            ChoiceGrid(listOf("None", "Light", "Medium", "Heavy", "Spotting"), flow) { flow = it }
            SectionTitle("Symptoms")
            MultiChoiceGrid(
                listOf("Cramps", "Headache", "Bloating", "Back Pain", "Nausea", "Fatigue", "Acne", "Tender Breasts"),
                symptoms
            )
            SectionTitle("Mood")
            ChoiceGrid(listOf("Happy", "Calm", "Irritable", "Sad", "Anxious"), mood) { mood = it }
            OutlinedTextField(
                value = note,
                onValueChange = { note = it.take(300) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Notes") },
                minLines = 3,
                shape = RoundedCornerShape(18.dp)
            )
            Button(
                onClick = {
                    store.saveToday(flow, mood, symptoms.toSet(), note)
                    onSaved()
                },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(20.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Red)
            ) { Text("Save Entry", fontWeight = FontWeight.Bold) }
        }
    }

    if (tablet) {
        Row(Modifier.fillMaxSize().padding(28.dp), horizontalArrangement = Arrangement.spacedBy(28.dp)) {
            LazyColumn(Modifier.weight(1.25f), contentPadding = PaddingValues(bottom = 24.dp)) { item { content() } }
            Column(Modifier.weight(0.75f), horizontalAlignment = Alignment.CenterHorizontally) {
                Mascots(Modifier.fillMaxWidth().height(330.dp))
                MascotMessage("Log gently", "There is no perfect cycle. Track what you notice.")
            }
        }
    } else {
        LazyColumn(
            Modifier.fillMaxSize().padding(horizontal = 18.dp),
            contentPadding = PaddingValues(top = 18.dp, bottom = 24.dp)
        ) { item { content() } }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
}

@Composable
private fun ChoiceGrid(options: List<String>, selected: String, onSelected: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
        options.chunked(2).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                row.forEach { option ->
                    ChoiceTile(option, option == selected, Modifier.weight(1f)) { onSelected(option) }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun MultiChoiceGrid(options: List<String>, selected: MutableList<String>) {
    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
        options.chunked(2).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                row.forEach { option ->
                    ChoiceTile(option, option in selected, Modifier.weight(1f)) {
                        if (option in selected) selected.remove(option) else selected.add(option)
                    }
                }
            }
        }
    }
}

@Composable
private fun ChoiceTile(text: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier.clip(RoundedCornerShape(17.dp))
            .background(if (selected) Color(0xFF4D171E) else Panel)
            .border(if (selected) 2.dp else 1.dp, if (selected) HotRed else Color(0xFF4D3036), RoundedCornerShape(17.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 15.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal, textAlign = TextAlign.Center)
    }
}

@Composable
private fun CalendarScreen(store: PawStore, tablet: Boolean, refresh: Int) {
    var month by remember { mutableStateOf(YearMonth.now()) }
    val first = month.atDay(1)
    val offset = first.dayOfWeek.value % 7
    val dates: List<LocalDate?> = List(offset) { null } + (1..month.lengthOfMonth()).map { month.atDay(it) }

    val calendar: @Composable () -> Unit = {
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Panel).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { month = month.minusMonths(1) }) { Icon(Icons.Default.ChevronLeft, null) }
                Text(month.format(DateTimeFormatter.ofPattern("MMMM yyyy")), Modifier.weight(1f), textAlign = TextAlign.Center, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                IconButton(onClick = { month = month.plusMonths(1) }) { Icon(Icons.Default.ChevronRight, null) }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                listOf("S","M","T","W","T","F","S").forEach { Text(it, color = Muted, modifier = Modifier.width(38.dp), textAlign = TextAlign.Center, fontSize = 12.sp) }
            }
            dates.chunked(7).forEach { week ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    week.forEach { date ->
                        if (date == null) {
                            Spacer(Modifier.size(38.dp))
                        } else {
                            val today = date == store.today
                            val period = store.predictedPeriod(date)
                            Box(
                                Modifier.size(38.dp).clip(CircleShape).background(
                                    when {
                                        today -> Red
                                        period -> Color(0xFF5A2027)
                                        else -> Color.Transparent
                                    }
                                ),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(date.dayOfMonth.toString(), color = if (period || today) Color.White else Color(0xFFE7DDE0), fontWeight = if (today) FontWeight.Bold else FontWeight.Normal)
                            }
                        }
                    }
                    repeat(7 - week.size) { Spacer(Modifier.size(38.dp)) }
                }
            }
        }
    }

    if (tablet) {
        Row(Modifier.fillMaxSize().padding(28.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            Column(Modifier.weight(1.25f)) {
                Header("Calendar", "Estimated cycle view")
                Spacer(Modifier.height(16.dp))
                calendar()
            }
            LazyColumn(Modifier.weight(0.75f), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                item { DashboardCard("Next period", store.nextPeriod().format(DateTimeFormatter.ofPattern("MMM d, yyyy")), "♥") }
                item { DashboardCard("Cycle length", "${store.cycleLength} days", "▦") }
                item { DashboardCard("Period length", "${store.periodLength} days", "●") }
                item { MascotMessage("Every cycle tells a story", "Dates shown here are estimates, not guarantees.") }
            }
        }
    } else {
        LazyColumn(
            Modifier.fillMaxSize().padding(horizontal = 18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(top = 18.dp, bottom = 24.dp)
        ) {
            item { Header("Calendar", "Estimated cycle view") }
            item { calendar() }
            item { DashboardCard("Next period", store.nextPeriod().format(DateTimeFormatter.ofPattern("MMM d, yyyy")), "♥") }
            item { MascotMessage("Rest • Track • Thrive", "Dates shown here are estimates, not guarantees.") }
        }
    }
}

@Composable
private fun InsightsScreen(store: PawStore, tablet: Boolean, refresh: Int) {
    val cards: @Composable () -> Unit = {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatCard("${store.cycleLength} days", "Average cycle", Modifier.weight(1f))
                StatCard("${store.periodLength} days", "Period length", Modifier.weight(1f))
            }
            StatCard(store.nextPeriod().format(DateTimeFormatter.ofPattern("MMM d, yyyy")), "Next period estimate", Modifier.fillMaxWidth())
            StatCard(store.mood(), "Today's mood", Modifier.fillMaxWidth())
            StatCard(if (store.symptoms().isEmpty()) "None logged" else store.symptoms().joinToString(", "), "Today's symptoms", Modifier.fillMaxWidth())
        }
    }

    if (tablet) {
        Row(Modifier.fillMaxSize().padding(28.dp), horizontalArrangement = Arrangement.spacedBy(28.dp)) {
            LazyColumn(Modifier.weight(1.15f), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                item { Header("Insights", "Your locally saved cycle summary") }
                item { cards() }
            }
            Column(Modifier.weight(0.85f), horizontalAlignment = Alignment.CenterHorizontally) {
                Mascots(Modifier.fillMaxWidth().height(330.dp))
                MascotMessage("Progress, not perfection ♥", "Patterns become clearer as you keep logging.")
            }
        }
    } else {
        LazyColumn(
            Modifier.fillMaxSize().padding(horizontal = 18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(top = 18.dp, bottom = 24.dp)
        ) {
            item { Header("Insights", "Your locally saved cycle summary") }
            item { cards() }
            item { MascotMessage("Progress, not perfection ♥", "Patterns become clearer as you keep logging.") }
        }
    }
}

@Composable
private fun StatCard(value: String, label: String, modifier: Modifier) {
    Column(modifier.clip(RoundedCornerShape(20.dp)).background(Panel).padding(17.dp)) {
        Text(value, fontWeight = FontWeight.ExtraBold, fontSize = 21.sp)
        Spacer(Modifier.height(4.dp))
        Text(label, color = Muted, fontSize = 12.sp)
    }
}

@Composable
private fun SettingsScreen(store: PawStore, tablet: Boolean, refresh: Int, onChanged: () -> Unit, replayOnboarding: () -> Unit) {
    val context = LocalContext.current
    var showCycle by remember { mutableStateOf(false) }
    val rows: @Composable () -> Unit = {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SettingsRow("Cycle Settings", "Cycle ${store.cycleLength}d • Period ${store.periodLength}d", Icons.Default.DateRange) { showCycle = true }
            SettingsRow("App Theme", "Red (Default)", Icons.Default.Palette) {}
            SettingsRow("Privacy", "Data stays on this device", Icons.Default.Lock) {}
            SettingsRow("Export Data", "Share a JSON-style backup", Icons.Default.Share) {
                val send = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_SUBJECT, "PawCycle data export")
                    putExtra(Intent.EXTRA_TEXT, store.exportText())
                }
                context.startActivity(Intent.createChooser(send, "Export PawCycle data"))
            }
            SettingsRow("Replay Welcome", "Show onboarding again", Icons.Default.Refresh, replayOnboarding)
            SettingsRow("About PawCycle", "1.0.0 • Cat + dinosaur edition", Icons.Default.Info) {}
        }
    }

    if (tablet) {
        Row(Modifier.fillMaxSize().padding(28.dp), horizontalArrangement = Arrangement.spacedBy(28.dp)) {
            LazyColumn(Modifier.weight(1.15f), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                item { Header("Settings", "PawCycle preferences") }
                item { rows() }
            }
            Column(Modifier.weight(0.85f), horizontalAlignment = Alignment.CenterHorizontally) {
                Mascots(Modifier.fillMaxWidth().height(330.dp))
                MascotMessage("Same strength. Different days.", "Your data is stored locally on this device.")
            }
        }
    } else {
        LazyColumn(
            Modifier.fillMaxSize().padding(horizontal = 18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(top = 18.dp, bottom = 24.dp)
        ) {
            item { Header("Settings", "PawCycle preferences") }
            item { rows() }
            item { MascotMessage("Same strength. Different days.", "Your data is stored locally on this device.") }
        }
    }

    if (showCycle) {
        var cycleText by remember { mutableStateOf(store.cycleLength.toString()) }
        var periodText by remember { mutableStateOf(store.periodLength.toString()) }
        AlertDialog(
            onDismissRequest = { showCycle = false },
            title = { Text("Cycle Settings") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(cycleText, { cycleText = it.filter(Char::isDigit).take(2) }, label = { Text("Cycle length (20–45 days)") })
                    OutlinedTextField(periodText, { periodText = it.filter(Char::isDigit).take(2) }, label = { Text("Period length (1–10 days)") })
                    Text("These values are used only for estimates.", color = Muted, fontSize = 12.sp)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    cycleText.toIntOrNull()?.let { store.cycleLength = it }
                    periodText.toIntOrNull()?.let { store.periodLength = it }
                    showCycle = false
                    onChanged()
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { showCycle = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun SettingsRow(title: String, subtitle: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Panel).clickable(onClick = onClick).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = HotRed, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = Muted, fontSize = 12.sp)
        }
        Icon(Icons.Default.ChevronRight, null, tint = Muted)
    }
}

@Composable
private fun MascotMessage(title: String, subtitle: String) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp))
            .background(Brush.horizontalGradient(listOf(Color(0xFF3B1218), Color(0xFF1A1113))))
            .padding(17.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Mascots(Modifier.fillMaxWidth().height(100.dp))
        Text(title, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Text(subtitle, color = SoftPink, fontSize = 12.sp, textAlign = TextAlign.Center)
    }
}

@Composable
private fun PawBottomBar(selected: Screen, onSelected: (Screen) -> Unit) {
    NavigationBar(containerColor = Color(0xFF171012)) {
        NavItems.forEach { item ->
            NavigationBarItem(
                selected = selected == item.screen,
                onClick = { onSelected(item.screen) },
                icon = { Icon(item.icon, item.label) },
                label = { Text(item.label, fontSize = 10.sp) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = HotRed,
                    selectedTextColor = HotRed,
                    indicatorColor = Color(0xFF361319),
                    unselectedIconColor = Muted,
                    unselectedTextColor = Muted
                )
            )
        }
    }
}

@Composable
private fun PawRail(selected: Screen, onSelected: (Screen) -> Unit) {
    NavigationRail(
        containerColor = Color(0xFF171012),
        header = {
            Spacer(Modifier.height(18.dp))
            Text("🐾", fontSize = 32.sp)
            Text("PawCycle", fontWeight = FontWeight.Bold, color = HotRed)
            Spacer(Modifier.height(20.dp))
        }
    ) {
        NavItems.forEach { item ->
            NavigationRailItem(
                selected = selected == item.screen,
                onClick = { onSelected(item.screen) },
                icon = { Icon(item.icon, item.label) },
                label = { Text(item.label) },
                colors = NavigationRailItemDefaults.colors(
                    selectedIconColor = HotRed,
                    selectedTextColor = HotRed,
                    indicatorColor = Color(0xFF361319),
                    unselectedIconColor = Muted,
                    unselectedTextColor = Muted
                )
            )
        }
    }
}

private data class NavItem(val screen: Screen, val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector)
private val NavItems = listOf(
    NavItem(Screen.Home, "Home", Icons.Default.Home),
    NavItem(Screen.Calendar, "Calendar", Icons.Default.DateRange),
    NavItem(Screen.Log, "Log", Icons.Default.AddCircle),
    NavItem(Screen.Insights, "Insights", Icons.Default.Favorite),
    NavItem(Screen.Settings, "Settings", Icons.Default.Settings)
)
