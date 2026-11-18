package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.ViewAgenda
import androidx.compose.material.icons.rounded.ViewTimeline
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.domain.model.CalendarEvent
import com.example.domain.model.CalendarViewMode
import com.example.domain.model.EventCategory
import com.example.domain.util.LocalDay
import com.example.ui.CalendarUiState
import com.example.ui.components.DraggableDayTimeline
import com.example.ui.components.EventAgendaCard
import com.example.ui.components.MonthCalendarCard
import com.example.ui.components.WeekStripSelector
import com.example.ui.theme.LocalIsDarkTheme

@Composable
fun ScheduleScreen(
    uiState: CalendarUiState,
    onSelectDay: (LocalDay) -> Unit,
    onShiftMonth: (Int) -> Unit,
    onJumpToday: () -> Unit,
    onSelectCategory: (EventCategory?) -> Unit,
    onSetViewMode: (CalendarViewMode) -> Unit,
    onToggleTheme: () -> Unit,
    onCreateEvent: (Int) -> Unit,
    onEventClick: (CalendarEvent) -> Unit,
    onRescheduleByDrag: (CalendarEvent, LocalDay, Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val isDark = LocalIsDarkTheme.current

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val isWide = maxWidth >= 680.dp

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
        ) {
            // Minimalist Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "Cadence",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.CloudOff,
                            contentDescription = null,
                            modifier = Modifier.size(13.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Offline Vault",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Icon(
                            imageVector = Icons.Rounded.Lock,
                            contentDescription = stringResource(R.string.cd_encrypted_badge),
                            modifier = Modifier.size(12.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    SingleChoiceSegmentedButtonRow {
                        CalendarViewMode.entries.forEachIndexed { index, mode ->
                            SegmentedButton(
                                selected = uiState.preferences.viewMode == mode,
                                onClick = { onSetViewMode(mode) },
                                shape = SegmentedButtonDefaults.itemShape(
                                    index = index,
                                    count = CalendarViewMode.entries.size
                                ),
                                modifier = Modifier.testTag("view_mode_${mode.name.lowercase()}"),
                                icon = {
                                    Icon(
                                        imageVector = if (mode == CalendarViewMode.MONTH_AND_AGENDA) {
                                            Icons.Rounded.ViewAgenda
                                        } else {
                                            Icons.Rounded.ViewTimeline
                                        },
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            ) {
                                Text(
                                    text = if (mode == CalendarViewMode.MONTH_AND_AGENDA) "Month" else "Drag",
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }
                        }
                    }

                    IconButton(
                        onClick = onToggleTheme,
                        modifier = Modifier.testTag("quick_theme_toggle")
                    ) {
                        Icon(
                            imageVector = if (isDark) Icons.Outlined.LightMode else Icons.Outlined.DarkMode,
                            contentDescription = stringResource(R.string.cd_toggle_theme),
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            // Color-coded Category Filter Strip
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 10.dp)
            ) {
                item {
                    FilterChip(
                        selected = uiState.selectedCategoryFilter == null,
                        onClick = { onSelectCategory(null) },
                        label = { Text("All") },
                        modifier = Modifier.testTag("filter_all")
                    )
                }
                items(EventCategory.entries) { cat ->
                    val catColor = cat.accentColor(isDark)
                    val selected = uiState.selectedCategoryFilter == cat
                    FilterChip(
                        selected = selected,
                        onClick = { onSelectCategory(cat) },
                        label = { Text(cat.displayName) },
                        modifier = Modifier.testTag("filter_${cat.name.lowercase()}"),
                        leadingIcon = {
                            Box(
                                modifier = Modifier
                                    .size(9.dp)
                                    .clip(CircleShape)
                                    .background(catColor)
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = catColor.copy(alpha = 0.18f),
                            selectedLabelColor = catColor
                        )
                    )
                }
            }

            if (isWide) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(18.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .weight(0.46f)
                            .fillMaxHeight()
                    ) {
                        MonthCalendarCard(
                            visibleMonth = uiState.visibleMonth,
                            selectedDay = uiState.selectedDay,
                            eventsByDay = uiState.monthEventsByDay,
                            onSelectDay = onSelectDay,
                            onPreviousMonth = { onShiftMonth(-1) },
                            onNextMonth = { onShiftMonth(1) },
                            onJumpToday = onJumpToday
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        DayHeaderBanner(
                            selectedDay = uiState.selectedDay,
                            eventCount = uiState.selectedDayEvents.size,
                            showDragTip = uiState.preferences.viewMode == CalendarViewMode.TIMELINE_DRAG
                        )
                    }

                    Box(
                        modifier = Modifier
                            .weight(0.54f)
                            .fillMaxHeight()
                    ) {
                        if (uiState.preferences.viewMode == CalendarViewMode.TIMELINE_DRAG) {
                            DraggableDayTimeline(
                                selectedDay = uiState.selectedDay,
                                events = uiState.selectedDayEvents,
                                onEventClick = onEventClick,
                                onCreateAtHour = onCreateEvent,
                                onRescheduleByDrag = onRescheduleByDrag
                            )
                        } else {
                            DayAgendaList(
                                selectedDay = uiState.selectedDay,
                                events = uiState.selectedDayEvents,
                                onEventClick = onEventClick,
                                onCreateEvent = { onCreateEvent(9) }
                            )
                        }
                    }
                }
            } else {
                // Compact Handheld Layout
                if (uiState.preferences.viewMode == CalendarViewMode.MONTH_AND_AGENDA) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 88.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        item {
                            MonthCalendarCard(
                                visibleMonth = uiState.visibleMonth,
                                selectedDay = uiState.selectedDay,
                                eventsByDay = uiState.monthEventsByDay,
                                onSelectDay = onSelectDay,
                                onPreviousMonth = { onShiftMonth(-1) },
                                onNextMonth = { onShiftMonth(1) },
                                onJumpToday = onJumpToday
                            )
                        }
                        item {
                            DayHeaderBanner(
                                selectedDay = uiState.selectedDay,
                                eventCount = uiState.selectedDayEvents.size,
                                showDragTip = false
                            )
                        }
                        if (uiState.selectedDayEvents.isEmpty()) {
                            item {
                                EmptyDayCard(onCreateEvent = { onCreateEvent(9) })
                            }
                        } else {
                            items(uiState.selectedDayEvents, key = { "${it.id}_${it.startTimeMillis}" }) { event ->
                                EventAgendaCard(
                                    event = event,
                                    onClick = { onEventClick(event) }
                                )
                            }
                        }
                    }
                } else {
                    Column(modifier = Modifier.fillMaxSize()) {
                        WeekStripSelector(
                            selectedDay = uiState.selectedDay,
                            eventsByDay = uiState.monthEventsByDay,
                            onSelectDay = onSelectDay
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        DayHeaderBanner(
                            selectedDay = uiState.selectedDay,
                            eventCount = uiState.selectedDayEvents.size,
                            showDragTip = true
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        DraggableDayTimeline(
                            selectedDay = uiState.selectedDay,
                            events = uiState.selectedDayEvents,
                            onEventClick = onEventClick,
                            onCreateAtHour = onCreateEvent,
                            onRescheduleByDrag = onRescheduleByDrag,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DayHeaderBanner(
    selectedDay: LocalDay,
    eventCount: Int,
    showDragTip: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = selectedDay.formatFullDate(),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            if (showDragTip) {
                Text(
                    text = "Long-press & drag events to reschedule • Tap hour to add",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Surface(
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.surfaceVariant
        ) {
            Text(
                text = if (eventCount == 1) "1 event" else "$eventCount events",
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun DayAgendaList(
    selectedDay: LocalDay,
    events: List<CalendarEvent>,
    onEventClick: (CalendarEvent) -> Unit,
    onCreateEvent: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 88.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (events.isEmpty()) {
            item {
                EmptyDayCard(onCreateEvent = onCreateEvent)
            }
        } else {
            items(events, key = { "${it.id}_${it.startTimeMillis}" }) { event ->
                EventAgendaCard(
                    event = event,
                    onClick = { onEventClick(event) }
                )
            }
        }
    }
}

@Composable
private fun EmptyDayCard(onCreateEvent: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Image(
                painter = painterResource(id = R.drawable.img_empty_schedule),
                contentDescription = stringResource(R.string.cd_empty_schedule),
                modifier = Modifier
                    .fillMaxWidth(0.68f)
                    .height(140.dp)
                    .clip(MaterialTheme.shapes.medium),
                contentScale = ContentScale.Crop
            )
            Text(
                text = "Clear day ahead",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "Your offline encrypted schedule has no events for this date. Add an event with custom reminders.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Button(
                onClick = onCreateEvent,
                modifier = Modifier.testTag("empty_create_event_button")
            ) {
                Icon(
                    imageVector = Icons.Rounded.Add,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("Create Event")
            }
        }
    }
}
