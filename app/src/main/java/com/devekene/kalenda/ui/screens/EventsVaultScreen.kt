package com.devekene.kalenda.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.EventNote
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.devekene.kalenda.R
import com.devekene.kalenda.domain.model.CalendarEvent
import com.devekene.kalenda.domain.model.EventLifecyclePhase
import com.devekene.kalenda.domain.util.LocalDay
import com.devekene.kalenda.ui.CalendarUiState
import com.devekene.kalenda.ui.components.EventAgendaCard

@Composable
fun EventsVaultScreen(
    uiState: CalendarUiState,
    onEventClick: (CalendarEvent) -> Unit,
    onCreateEvent: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val now = System.currentTimeMillis()
    val currentEvents = remember(uiState.allEvents) {
        uiState.allEvents.filter { it.lifecyclePhase(now) == EventLifecyclePhase.CURRENT }
            .sortedBy { it.startTimeMillis }
    }
    val upcomingEvents = remember(uiState.allEvents) {
        uiState.allEvents.filter { it.lifecyclePhase(now) == EventLifecyclePhase.UPCOMING }
            .sortedBy { it.startTimeMillis }
    }
    val pastEvents = remember(uiState.allEvents) {
        uiState.allEvents.filter { it.lifecyclePhase(now) == EventLifecyclePhase.PAST }
            .sortedByDescending { it.startTimeMillis }
    }

    var selectedPhase by remember { mutableStateOf<EventLifecyclePhase?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        LazyColumn(
            modifier = Modifier
                .widthIn(max = 760.dp)
                .fillMaxSize(),
            contentPadding = PaddingValues(top = 12.dp, bottom = 108.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(
                                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                                        MaterialTheme.colorScheme.surface
                                    )
                                )
                            )
                            .padding(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = stringResource(R.string.nav_events),
                                    style = MaterialTheme.typography.headlineMedium,
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                                Text(
                                    text = "${stringResource(R.string.app_name)} • ${stringResource(R.string.app_motto)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(
                                onClick = onOpenSettings,
                                modifier = Modifier.testTag("events_open_settings_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Settings,
                                    contentDescription = stringResource(R.string.nav_settings),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            PhaseStatPill(
                                label = "Upcoming",
                                value = upcomingEvents.size.toString(),
                                modifier = Modifier.weight(1f)
                            )
                            PhaseStatPill(
                                label = "Current",
                                value = currentEvents.size.toString(),
                                modifier = Modifier.weight(1f)
                            )
                            PhaseStatPill(
                                label = "Past",
                                value = pastEvents.size.toString(),
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = selectedPhase == null,
                        onClick = { selectedPhase = null },
                        label = { Text("All (${uiState.allEvents.size})") }
                    )
                    FilterChip(
                        selected = selectedPhase == EventLifecyclePhase.UPCOMING,
                        onClick = { selectedPhase = EventLifecyclePhase.UPCOMING },
                        label = { Text("Upcoming") }
                    )
                    FilterChip(
                        selected = selectedPhase == EventLifecyclePhase.CURRENT,
                        onClick = { selectedPhase = EventLifecyclePhase.CURRENT },
                        label = { Text("Current") }
                    )
                    FilterChip(
                        selected = selectedPhase == EventLifecyclePhase.PAST,
                        onClick = { selectedPhase = EventLifecyclePhase.PAST },
                        label = { Text("Past") }
                    )
                }
            }

            if (uiState.allEvents.isEmpty()) {
                item {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.large,
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.EventNote,
                                contentDescription = null,
                                modifier = Modifier.size(34.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "No events scheduled yet",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = "Create your first encrypted event or sync with your Google Calendar email in Settings.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Button(onClick = onCreateEvent) {
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
            } else {
                if ((selectedPhase == null || selectedPhase == EventLifecyclePhase.CURRENT) && currentEvents.isNotEmpty()) {
                    item {
                        SectionPhaseHeader(
                            title = "CURRENT EVENTS (${currentEvents.size})",
                            icon = Icons.Rounded.NotificationsActive
                        )
                    }
                    items(currentEvents, key = { "cur_${it.id}" }) { event ->
                        val dayLabel = LocalDay.fromEpochMillis(event.startTimeMillis).formatShortDate()
                        EventAgendaCard(
                            event = event,
                            isLockedMasked = event.isBiometricLocked && event.id !in uiState.unlockedEventIds,
                            onClick = { onEventClick(event) },
                            extraBadge = dayLabel
                        )
                    }
                }

                if ((selectedPhase == null || selectedPhase == EventLifecyclePhase.UPCOMING) && upcomingEvents.isNotEmpty()) {
                    item {
                        SectionPhaseHeader(
                            title = "UPCOMING EVENTS (${upcomingEvents.size})",
                            icon = Icons.Outlined.EventNote
                        )
                    }
                    items(upcomingEvents, key = { "up_${it.id}" }) { event ->
                        val dayLabel = LocalDay.fromEpochMillis(event.startTimeMillis).formatShortDate()
                        EventAgendaCard(
                            event = event,
                            isLockedMasked = event.isBiometricLocked && event.id !in uiState.unlockedEventIds,
                            onClick = { onEventClick(event) },
                            extraBadge = dayLabel
                        )
                    }
                }

                if ((selectedPhase == null || selectedPhase == EventLifecyclePhase.PAST) && pastEvents.isNotEmpty()) {
                    item {
                        SectionPhaseHeader(
                            title = "PAST EVENTS (${pastEvents.size})",
                            icon = Icons.Outlined.History
                        )
                    }
                    items(pastEvents, key = { "past_${it.id}" }) { event ->
                        val dayLabel = LocalDay.fromEpochMillis(event.startTimeMillis).formatShortDate()
                        EventAgendaCard(
                            event = event,
                            isLockedMasked = event.isBiometricLocked && event.id !in uiState.unlockedEventIds,
                            onClick = { onEventClick(event) },
                            extraBadge = dayLabel
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionPhaseHeader(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    Row(
        modifier = Modifier.padding(top = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(15.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Text(
            text = title,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun PhaseStatPill(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.78f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier.padding(vertical = 8.dp, horizontal = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
