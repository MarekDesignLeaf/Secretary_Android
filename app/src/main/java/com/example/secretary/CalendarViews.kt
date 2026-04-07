package com.example.secretary

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

enum class CalViewMode(val label: String) { DAY("Den"), WEEK("Týden"), MONTH("Měsíc") }

private val HOUR_DP = 56.dp
private val TIME_W = 44.dp
private val CS = Locale("cs", "CZ")

@Composable
fun CalendarScreen(viewModel: SecretaryViewModel) {
    val state by viewModel.uiState.collectAsState()
    val ctx = LocalContext.current
    var mode by remember { mutableStateOf(CalViewMode.MONTH) }
    var date by remember { mutableStateOf(LocalDate.now()) }
    var calEvents by remember { mutableStateOf<List<CalendarEvent>>(emptyList()) }

    LaunchedEffect(mode, date) {
        val (rs, re) = rangeFor(mode, date)
        calEvents = CalendarManager(ctx).getEventsInRange(rs, re)
    }

    val z = ZoneId.systemDefault()
    val taskEvents = remember(state.tasks) {
        state.tasks.mapNotNull { t ->
            val ds = t.plannedDate ?: t.deadline ?: return@mapNotNull null
            try {
                val ld = LocalDate.parse(ds)
                val ms = ld.atStartOfDay(z).toInstant().toEpochMilli()
                CalendarEvent(t.id.hashCode().toLong(), "✓ ${t.title}", ms, ms + 1_800_000L, true)
            } catch (_: Exception) { null }
        }
    }

    val allEvents = remember(calEvents, taskEvents) {
        (calEvents + taskEvents).sortedBy { it.startMillis }
    }

    Column(Modifier.fillMaxSize()) {
        // Mode selector
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            CalViewMode.values().forEach { m ->
                FilterChip(
                    selected = mode == m,
                    onClick = { mode = m },
                    label = { Text(m.label) }
                )
            }
        }
        // Navigation
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { date = prevDate(mode, date) }) {
                Icon(Icons.Default.KeyboardArrowLeft, null)
            }
            Text(
                dateLabel(mode, date),
                Modifier.weight(1f),
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
            IconButton(onClick = { date = nextDate(mode, date) }) {
                Icon(Icons.Default.KeyboardArrowRight, null)
            }
        }
        HorizontalDivider()
        when (mode) {
            CalViewMode.MONTH -> MonthView(date, allEvents) { date = it; mode = CalViewMode.DAY }
            CalViewMode.WEEK  -> WeekView(date, allEvents)
            CalViewMode.DAY   -> DayView(date, allEvents)
        }
    }
}

// ─── helpers ──────────────────────────────────────────────────────────────────

private fun rangeFor(mode: CalViewMode, date: LocalDate): Pair<Long, Long> {
    val z = ZoneId.systemDefault()
    return when (mode) {
        CalViewMode.DAY -> {
            val s = date.atStartOfDay(z).toInstant().toEpochMilli()
            Pair(s, s + 86_400_000L)
        }
        CalViewMode.WEEK -> {
            val s = date.with(DayOfWeek.MONDAY).atStartOfDay(z).toInstant().toEpochMilli()
            Pair(s, s + 7 * 86_400_000L)
        }
        CalViewMode.MONTH -> {
            val s = date.withDayOfMonth(1).atStartOfDay(z).toInstant().toEpochMilli()
            val e = date.withDayOfMonth(date.lengthOfMonth()).plusDays(1).atStartOfDay(z).toInstant().toEpochMilli()
            Pair(s, e)
        }
    }
}

private fun prevDate(mode: CalViewMode, date: LocalDate) = when (mode) {
    CalViewMode.DAY   -> date.minusDays(1)
    CalViewMode.WEEK  -> date.minusWeeks(1)
    CalViewMode.MONTH -> date.minusMonths(1)
}
private fun nextDate(mode: CalViewMode, date: LocalDate) = when (mode) {
    CalViewMode.DAY   -> date.plusDays(1)
    CalViewMode.WEEK  -> date.plusWeeks(1)
    CalViewMode.MONTH -> date.plusMonths(1)
}
private fun dateLabel(mode: CalViewMode, date: LocalDate): String = when (mode) {
    CalViewMode.DAY -> date.format(DateTimeFormatter.ofPattern("EEEE d. MMMM yyyy", CS))
        .replaceFirstChar { it.uppercase() }
    CalViewMode.WEEK -> {
        val mon = date.with(DayOfWeek.MONDAY)
        "${mon.format(DateTimeFormatter.ofPattern("d.M."))} – ${mon.plusDays(6).format(DateTimeFormatter.ofPattern("d.M.yyyy"))}"
    }
    CalViewMode.MONTH -> date.format(DateTimeFormatter.ofPattern("MMMM yyyy", CS))
        .replaceFirstChar { it.uppercase() }
}

private fun CalendarEvent.localDate(z: ZoneId = ZoneId.systemDefault()): LocalDate =
    LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(startMillis), z).toLocalDate()

private fun CalendarEvent.localStart(z: ZoneId = ZoneId.systemDefault()): LocalDateTime =
    LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(startMillis), z)

private fun CalendarEvent.localEnd(z: ZoneId = ZoneId.systemDefault()): LocalDateTime =
    LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(endMillis), z)

// ─── Month view ───────────────────────────────────────────────────────────────

@Composable
fun MonthView(date: LocalDate, events: List<CalendarEvent>, onDayClick: (LocalDate) -> Unit) {
    val today = LocalDate.now()
    val z = ZoneId.systemDefault()
    val firstDay = date.withDayOfMonth(1)
    val startOffset = firstDay.dayOfWeek.value - 1   // Mon=0, Sun=6
    val daysInMonth = date.lengthOfMonth()

    val eventsByDay = remember(events) {
        events.groupBy { it.localDate(z) }
    }

    Column(Modifier.fillMaxSize()) {
        // Day headers Mo–Ne
        Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant)) {
            listOf("Po", "Út", "St", "Čt", "Pá", "So", "Ne").forEach { d ->
                Text(
                    d, Modifier.weight(1f).padding(vertical = 4.dp),
                    textAlign = TextAlign.Center, fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        HorizontalDivider()
        // Grid
        val totalCells = startOffset + daysInMonth
        val rows = (totalCells + 6) / 7
        for (row in 0 until rows) {
            Row(Modifier.fillMaxWidth().weight(1f)) {
                for (col in 0..6) {
                    val dayNum = row * 7 + col - startOffset + 1
                    val validDay = dayNum in 1..daysInMonth
                    val cellDate = if (validDay) date.withDayOfMonth(dayNum) else null
                    val isToday = cellDate == today
                    val dayEvts = cellDate?.let { eventsByDay[it] } ?: emptyList()
                    val primary = MaterialTheme.colorScheme.primary
                    val onPrimary = MaterialTheme.colorScheme.onPrimary
                    val surface = MaterialTheme.colorScheme.surface
                    val outline = MaterialTheme.colorScheme.outlineVariant
                    val priCont = MaterialTheme.colorScheme.primaryContainer
                    val onPriCont = MaterialTheme.colorScheme.onPrimaryContainer

                    Box(
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .background(surface)
                            .border(0.5.dp, outline)
                            .then(if (cellDate != null) Modifier.clickable { onDayClick(cellDate) } else Modifier)
                    ) {
                        if (validDay) {
                            Column(Modifier.padding(2.dp)) {
                                Box(
                                    if (isToday)
                                        Modifier.size(22.dp).background(primary, CircleShape)
                                    else
                                        Modifier.size(22.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        dayNum.toString(), fontSize = 11.sp,
                                        fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isToday) onPrimary else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                dayEvts.take(2).forEach { ev ->
                                    Text(
                                        ev.title, fontSize = 9.sp,
                                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 1.dp)
                                            .background(priCont, MaterialTheme.shapes.extraSmall)
                                            .padding(horizontal = 2.dp),
                                        color = onPriCont
                                    )
                                }
                                if (dayEvts.size > 2) {
                                    Text("+${dayEvts.size - 2}", fontSize = 9.sp, color = primary)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ─── Week view ────────────────────────────────────────────────────────────────

@Composable
fun WeekView(date: LocalDate, events: List<CalendarEvent>) {
    val monday = date.with(DayOfWeek.MONDAY)
    val days = (0..6).map { monday.plusDays(it.toLong()) }
    val today = LocalDate.now()
    val z = ZoneId.systemDefault()
    val vScroll = rememberScrollState(initial = 6 * HOUR_DP.value.toInt())

    val allDayEvts = remember(events) { events.filter { it.allDay } }
    val timedEvts  = remember(events) { events.filter { !it.allDay } }

    Column(Modifier.fillMaxSize()) {
        // Day headers
        Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant)) {
            Spacer(Modifier.width(TIME_W))
            days.forEach { d ->
                val isToday = d == today
                Column(Modifier.weight(1f).padding(vertical = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        d.dayOfWeek.getDisplayName(TextStyle.SHORT, CS),
                        fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Box(
                        if (isToday) Modifier.size(24.dp).background(MaterialTheme.colorScheme.primary, CircleShape)
                        else Modifier.size(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            d.dayOfMonth.toString(), fontSize = 12.sp,
                            fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                            color = if (isToday) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
        // All-day strip
        if (allDayEvts.isNotEmpty()) {
            Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant).padding(bottom = 2.dp)) {
                Spacer(Modifier.width(TIME_W))
                days.forEach { d ->
                    val dayEvts = allDayEvts.filter { it.localDate(z) == d }
                    Column(Modifier.weight(1f).padding(horizontal = 1.dp)) {
                        dayEvts.take(2).forEach { ev ->
                            Text(
                                ev.title, fontSize = 9.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 1.dp)
                                    .background(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.shapes.extraSmall)
                                    .padding(horizontal = 2.dp),
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }
            }
        }
        HorizontalDivider()
        // Scrollable time grid + events
        val totalHeight = HOUR_DP * 24
        Box(Modifier.fillMaxSize().verticalScroll(vScroll)) {
            // Hour rows (background)
            Column(Modifier.fillMaxWidth().height(totalHeight)) {
                (0..23).forEach { h ->
                    Row(Modifier.fillMaxWidth().height(HOUR_DP)) {
                        Text(
                            "%02d:00".format(h),
                            Modifier.width(TIME_W).padding(end = 4.dp, top = 2.dp),
                            fontSize = 10.sp, textAlign = TextAlign.End,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(Modifier.weight(1f).fillMaxHeight()) {
                            days.forEach { _ ->
                                Box(
                                    Modifier.weight(1f).fillMaxHeight()
                                        .border(0.25.dp, MaterialTheme.colorScheme.outlineVariant)
                                )
                            }
                        }
                    }
                }
            }
            // Events overlay (scrolls with grid)
            Row(Modifier.fillMaxWidth().height(totalHeight).padding(start = TIME_W)) {
                days.forEachIndexed { idx, day ->
                    Box(Modifier.weight(1f).fillMaxHeight().padding(horizontal = 1.dp)) {
                        timedEvts.filter { it.localDate(z) == day }.forEach { ev ->
                            val startMin = ev.localStart(z).let { it.hour * 60 + it.minute }
                            val endMin = ev.localEnd(z).let { it.hour * 60 + it.minute }
                            val durMin = maxOf(endMin - startMin, 30)
                            val topDp = HOUR_DP * startMin / 60
                            val heightDp = HOUR_DP * durMin / 60
                            Box(
                                Modifier
                                    .absoluteOffset(y = topDp)
                                    .fillMaxWidth()
                                    .height(heightDp)
                                    .background(
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
                                        MaterialTheme.shapes.extraSmall
                                    )
                                    .padding(2.dp)
                            ) {
                                Text(ev.title, color = Color.White, fontSize = 9.sp,
                                    maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
        }
    }
}

// ─── Day view ─────────────────────────────────────────────────────────────────

@Composable
fun DayView(date: LocalDate, events: List<CalendarEvent>) {
    val z = ZoneId.systemDefault()
    val vScroll = rememberScrollState(initial = 6 * HOUR_DP.value.toInt())
    val fmt = DateTimeFormatter.ofPattern("HH:mm")

    val allDayEvts = remember(events) { events.filter { it.allDay && it.localDate(z) == date } }
    val timedEvts  = remember(events) { events.filter { !it.allDay && it.localDate(z) == date } }

    Column(Modifier.fillMaxSize()) {
        if (allDayEvts.isNotEmpty()) {
            Column(
                Modifier.fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                allDayEvts.forEach { ev ->
                    Text(
                        ev.title, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp)
                            .background(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.shapes.extraSmall)
                            .padding(horizontal = 8.dp, vertical = 3.dp),
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
            HorizontalDivider()
        }
        val totalHeight = HOUR_DP * 24
        Box(Modifier.fillMaxSize().verticalScroll(vScroll)) {
            // Hour rows
            Column(Modifier.fillMaxWidth().height(totalHeight)) {
                (0..23).forEach { h ->
                    Row(Modifier.fillMaxWidth().height(HOUR_DP)) {
                        Text(
                            "%02d:00".format(h),
                            Modifier.width(TIME_W).padding(end = 4.dp, top = 2.dp),
                            fontSize = 10.sp, textAlign = TextAlign.End,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Box(Modifier.weight(1f).fillMaxHeight().border(0.25.dp, MaterialTheme.colorScheme.outlineVariant))
                    }
                }
            }
            // Events
            Box(Modifier.fillMaxWidth().height(totalHeight).padding(start = TIME_W + 4.dp, end = 4.dp)) {
                timedEvts.forEach { ev ->
                    val startMin = ev.localStart(z).let { it.hour * 60 + it.minute }
                    val endMin = ev.localEnd(z).let { it.hour * 60 + it.minute }
                    val durMin = maxOf(endMin - startMin, 30)
                    val topDp = HOUR_DP * startMin / 60
                    val heightDp = HOUR_DP * durMin / 60
                    Box(
                        Modifier
                            .absoluteOffset(y = topDp)
                            .fillMaxWidth()
                            .height(heightDp)
                            .background(
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
                                MaterialTheme.shapes.extraSmall
                            )
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Column {
                            Text(
                                ev.title, color = Color.White, fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                "${ev.localStart(z).format(fmt)} – ${ev.localEnd(z).format(fmt)}",
                                color = Color.White.copy(alpha = 0.85f), fontSize = 11.sp
                            )
                        }
                    }
                }
            }
        }
    }
}
