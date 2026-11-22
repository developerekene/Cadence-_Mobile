package com.devekene.kalenda.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.devekene.kalenda.R
import com.devekene.kalenda.domain.model.CalendarEvent
import com.devekene.kalenda.domain.util.LocalDay
import com.devekene.kalenda.domain.util.YearMonthKey
import com.devekene.kalenda.ui.theme.LocalIsDarkTheme
import java.util.Calendar

@Composable
fun MonthCalendarCard(
    visibleMonth: YearMonthKey,
    selectedDay: LocalDay,
    eventsByDay: Map<LocalDay, List<CalendarEvent>>,
    onSelectDay: (LocalDay) -> Unit,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onJumpToday: () -> Unit,
    modifier: Modifier = Modifier
) {
    val today = LocalDay.today()
    val isDark = LocalIsDarkTheme.current
    val firstOffset = visibleMonth.firstDayOffsetSundayStart()
    val daysCount = visibleMonth.daysInMonth()
    val totalCells = ((firstOffset + daysCount + 6) / 7) * 7
    val weekdayHeaders = listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant
        )
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = visibleMonth.formatMonthYear(),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(
                        onClick = onJumpToday,
                        modifier = Modifier.testTag("today_button")
                    ) {
                        Text(
                            text = "Today",
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                    IconButton(
                        onClick = onPreviousMonth,
                        modifier = Modifier.testTag("prev_month_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowLeft,
                            contentDescription = stringResource(R.string.cd_previous_month)
                        )
                    }
                    IconButton(
                        onClick = onNextMonth,
                        modifier = Modifier.testTag("next_month_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                            contentDescription = stringResource(R.string.cd_next_month)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Row(modifier = Modifier.fillMaxWidth()) {
                weekdayHeaders.forEach { label ->
                    Text(
                        text = label,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            val rows = totalCells / 7
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                for (rowIndex in 0 until rows) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        for (colIndex in 0 until 7) {
                            val cellIndex = rowIndex * 7 + colIndex
                            val dayNum = cellIndex - firstOffset + 1
                            if (dayNum in 1..daysCount) {
                                val day = LocalDay(visibleMonth.year, visibleMonth.month, dayNum)
                                val isSelected = day == selectedDay
                                val isToday = day == today
                                val dayEvents = eventsByDay[day].orEmpty()

                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(
                                            when {
                                                isSelected -> MaterialTheme.colorScheme.primary
                                                isToday -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                                                else -> MaterialTheme.colorScheme.surface
                                            }
                                        )
                                        .then(
                                            if (isToday && !isSelected) {
                                                Modifier.border(
                                                    1.dp,
                                                    MaterialTheme.colorScheme.primary,
                                                    RoundedCornerShape(12.dp)
                                                )
                                            } else Modifier
                                        )
                                        .clickable { onSelectDay(day) }
                                        .testTag("calendar_day_${day.year}_${day.month}_${day.dayOfMonth}"),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        Text(
                                            text = dayNum.toString(),
                                            style = MaterialTheme.typography.bodyMedium.copy(
                                                fontWeight = if (isSelected || isToday) FontWeight.Bold else FontWeight.Medium
                                            ),
                                            color = when {
                                                isSelected -> MaterialTheme.colorScheme.onPrimary
                                                isToday -> MaterialTheme.colorScheme.primary
                                                else -> MaterialTheme.colorScheme.onSurface
                                            }
                                        )
                                        if (dayEvents.isNotEmpty()) {
                                            Spacer(modifier = Modifier.height(3.dp))
                                            Row(
                                                horizontalArrangement = Arrangement.spacedBy(3.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                dayEvents.take(3).forEach { ev ->
                                                    Box(
                                                        modifier = Modifier
                                                            .size(5.dp)
                                                            .clip(CircleShape)
                                                            .background(
                                                                if (isSelected) MaterialTheme.colorScheme.onPrimary
                                                                else ev.category.accentColor(isDark)
                                                            )
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            } else {
                                Spacer(modifier = Modifier.weight(1f).height(48.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun WeekStripSelector(
    selectedDay: LocalDay,
    eventsByDay: Map<LocalDay, List<CalendarEvent>>,
    onSelectDay: (LocalDay) -> Unit,
    modifier: Modifier = Modifier
) {
    val today = LocalDay.today()
    val isDark = LocalIsDarkTheme.current
    val offset = selectedDay.dayOfWeek() - Calendar.SUNDAY
    val sunday = selectedDay.plusDays(-offset)

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        for (i in 0 until 7) {
            val day = sunday.plusDays(i)
            val isSelected = day == selectedDay
            val isToday = day == today
            val dayEvents = eventsByDay[day].orEmpty()

            Surface(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(14.dp))
                    .clickable { onSelectDay(day) },
                shape = RoundedCornerShape(14.dp),
                color = when {
                    isSelected -> MaterialTheme.colorScheme.primary
                    isToday -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                    else -> MaterialTheme.colorScheme.surface
                },
                border = BorderStroke(
                    width = 1.dp,
                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                )
            ) {
                Column(
                    modifier = Modifier.padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = day.formatWeekdayShort().take(3),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = day.dayOfMonth.toString(),
                        style = MaterialTheme.typography.titleMedium,
                        color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        if (dayEvents.isEmpty()) {
                            Spacer(modifier = Modifier.height(5.dp))
                        } else {
                            dayEvents.take(3).forEach { ev ->
                                Box(
                                    modifier = Modifier
                                        .size(5.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (isSelected) MaterialTheme.colorScheme.onPrimary
                                            else ev.category.accentColor(isDark)
                                        )
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
