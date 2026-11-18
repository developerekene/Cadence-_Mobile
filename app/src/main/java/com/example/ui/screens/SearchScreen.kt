package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.domain.model.CalendarEvent
import com.example.domain.util.LocalDay
import com.example.ui.CalendarUiState
import com.example.ui.components.EventAgendaCard

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SearchScreen(
    uiState: CalendarUiState,
    onQueryChange: (String) -> Unit,
    onTogglePastOnly: () -> Unit,
    onEventClick: (CalendarEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    val suggestions = listOf(
        "Last week",
        "Last month",
        "Past 30 days",
        "Work yesterday",
        "Health morning",
        "With reminders"
    )

    val pastResults = uiState.searchResults.filter { it.isPast }
    val upcomingResults = uiState.searchResults.filter { !it.isPast }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 760.dp)
                .fillMaxSize()
        ) {
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Natural Language Search",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = "Query your encrypted offline history using natural dates, categories, or keywords.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = uiState.searchQuery,
                onValueChange = onQueryChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("nl_search_input"),
                placeholder = { Text("Try \"work meetings last month\" or \"health morning\"") },
                leadingIcon = {
                    Icon(imageVector = Icons.Outlined.Search, contentDescription = null)
                },
                trailingIcon = {
                    if (uiState.searchQuery.isNotEmpty()) {
                        IconButton(onClick = { onQueryChange("") }) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = stringResource(R.string.cd_clear_search)
                            )
                        }
                    }
                },
                singleLine = true,
                shape = MaterialTheme.shapes.large
            )

            Spacer(modifier = Modifier.height(10.dp))

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                item {
                    FilterChip(
                        selected = uiState.searchPastOnly,
                        onClick = onTogglePastOnly,
                        modifier = Modifier.testTag("search_past_only_chip"),
                        label = { Text("Past entries only") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Outlined.History,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    )
                }
                items(suggestions) { suggestion ->
                    AssistChip(
                        onClick = { onQueryChange(suggestion) },
                        label = { Text(suggestion) }
                    )
                }
            }

            if (uiState.parsedSearch.interpretationTags.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    uiState.parsedSearch.interpretationTags.forEach { tag ->
                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.AutoAwesome,
                                    contentDescription = null,
                                    modifier = Modifier.size(12.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = tag,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 88.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (uiState.searchResults.isEmpty()) {
                    item {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.large,
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                        ) {
                            Column(
                                modifier = Modifier.padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = if (uiState.allEvents.isEmpty()) "No encrypted entries stored yet" else "No matching events",
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    text = if (uiState.allEvents.isEmpty()) {
                                        "Create events on your Schedule tab to search through past and upcoming entries."
                                    } else {
                                        "Try phrasing like \"last week\", \"november\", \"morning\", or a keyword from your notes."
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                } else {
                    if (pastResults.isNotEmpty()) {
                        item {
                            Text(
                                text = "PAST ENTRIES (${pastResults.size})",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                        items(pastResults, key = { "past_${it.event.id}" }) { item ->
                            val dayLabel = LocalDay.fromEpochMillis(item.event.startTimeMillis).formatShortDate()
                            val badge = if (item.matchedFields.isNotEmpty()) {
                                "$dayLabel • ${item.matchedFields.joinToString("/")}"
                            } else {
                                dayLabel
                            }
                            EventAgendaCard(
                                event = item.event,
                                onClick = { onEventClick(item.event) },
                                extraBadge = badge
                            )
                        }
                    }

                    if (upcomingResults.isNotEmpty()) {
                        item {
                            Text(
                                text = "UPCOMING (${upcomingResults.size})",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        }
                        items(upcomingResults, key = { "up_${it.event.id}" }) { item ->
                            val dayLabel = LocalDay.fromEpochMillis(item.event.startTimeMillis).formatShortDate()
                            val badge = if (item.matchedFields.isNotEmpty()) {
                                "$dayLabel • ${item.matchedFields.joinToString("/")}"
                            } else {
                                dayLabel
                            }
                            EventAgendaCard(
                                event = item.event,
                                onClick = { onEventClick(item.event) },
                                extraBadge = badge
                            )
                        }
                    }
                }
            }
        }
    }
}
