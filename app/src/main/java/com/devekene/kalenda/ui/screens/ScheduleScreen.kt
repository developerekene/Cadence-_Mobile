package com.devekene.kalenda.ui.screens

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
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Menu
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.devekene.kalenda.R
import com.devekene.kalenda.domain.model.CalendarEvent
import com.devekene.kalenda.domain.model.CalendarViewMode
import com.devekene.kalenda.domain.model.EventCategory
import com.devekene.kalenda.domain.util.LocalDay
import com.devekene.kalenda.ui.CalendarUiState
import com.devekene.kalenda.ui.components.DraggableDayTimeline
import com.devekene.kalenda.ui.components.EventAgendaCard
import com.devekene.kalenda.ui.components.MonthCalendarCard
import com.devekene.kalenda.ui.components.WeekStripSelector
import com.devekene.kalenda.ui.theme.LocalIsDarkTheme

@Composable
fun ScheduleScreen(
    uiState: CalendarUiState,
    onSelectDay: (LocalDay) -> Unit,
    onShiftMonth: (Int) -> Unit,
    onJumpToday: () -> Unit,
    onSelectCategory: (EventCategory?) -> Unit,
    onSetViewMode: (CalendarViewMode) -> Unit,
    onOpenTopMenu: () -> Unit,
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
            // Atmospheric Dark-Purple Header Card with Hamburger Menu Trigger
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp, bottom = 10.dp),
                shape = MaterialTheme.shapes.large,
                border = BorderStroke(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)
                ),
                tonalElevation = 2.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.linearGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f),
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f),
                                    MaterialTheme.colorScheme.surface
                                )
                            )
                        )
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.app_name),
                                style = MaterialTheme.typography.headlineMedium,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Lock,
                                        contentDescription = stringResource(R.string.cd_encrypted_badge),
                                        modifier = Modifier.size(11.dp),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = "E2EE",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = stringResource(R.string.app_motto),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
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
                                            modifier = Modifier.size(15.dp)
                                        )
                                    }
                                ) {
                                    Text(
                                        text = if (mode == CalendarViewMode.MONTH_AND_AGENDA) "Month" else "Drag",
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                }
                            }
                        }

                        IconButton(
                            onClick = onOpenTopMenu,
                            modifier = Modifier.testTag("hamburger_menu_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Menu,
                                contentDescription = stringResource(R.string.cd_hamburger_menu),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

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
                            selectedContainerColor = catColor.copy(alpha = 0.20f),
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
                                unlockedEventIds = uiState.unlockedEventIds,
                                onEventClick = onEventClick,
                                onCreateAtHour = onCreateEvent,
                                onRescheduleByDrag = onRescheduleByDrag
                            )
                        } else {
                            DayAgendaList(
                                events = uiState.selectedDayEvents,
                                unlockedEventIds = uiState.unlockedEventIds,
                                onEventClick = onEventClick,
                                onCreateEvent = { onCreateEvent(9) }
                            )
                        }
                    }
                }
            } else {
                if (uiState.preferences.viewMode == CalendarViewMode.MONTH_AND_AGENDA) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 104.dp),
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
                                    isLockedMasked = event.isBiometricLocked && event.id !in uiState.unlockedEventIds,
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
                            unlockedEventIds = uiState.unlockedEventIds,
                            onEventClick = onEventClick,
                            onCreateAtHour = onCreateEvent,
                            onRescheduleByDrag = onRescheduleByDrag,
                            modifier = Modifier
                                .weight(1f)
                                .padding(bottom = 84.dp)
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
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
        ) {
            Text(
                text = if (eventCount == 1) "1 event" else "$eventCount events",
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

@Composable
private fun DayAgendaList(
    events: List<CalendarEvent>,
    unlockedEventIds: Set<String>,
    onEventClick: (CalendarEvent) -> Unit,
    onCreateEvent: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 104.dp),
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
                    isLockedMasked = event.isBiometricLocked && event.id !in unlockedEventIds,
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
                .padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Image(
                painter = painterResource(id = R.drawable.img_empty_schedule),
                contentDescription = stringResource(R.string.cd_empty_schedule),
                modifier = Modifier
                    .fillMaxWidth(0.72f)
                    .height(148.dp)
                    .clip(MaterialTheme.shapes.medium),
                contentScale = ContentScale.Crop
            )
            Text(
                text = "Clear day ahead",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "Organise your schedules offline with AES-256 encryption, biometric locks, and lifecycle alerts.",
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
