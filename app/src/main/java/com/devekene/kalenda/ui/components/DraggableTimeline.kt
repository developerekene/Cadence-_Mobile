package com.devekene.kalenda.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.rounded.DragIndicator
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.devekene.kalenda.domain.model.CalendarEvent
import com.devekene.kalenda.domain.util.DateTimeUtils
import com.devekene.kalenda.domain.util.LocalDay
import com.devekene.kalenda.ui.theme.LocalIsDarkTheme
import kotlin.math.roundToInt

/**
 * Interactive hourly timeline supporting long-press drag-and-drop rescheduling across hours
 * (snapped to 15-minute increments) and across adjacent day drop zones.
 */
@Composable
fun DraggableDayTimeline(
    selectedDay: LocalDay,
    events: List<CalendarEvent>,
    unlockedEventIds: Set<String>,
    onEventClick: (CalendarEvent) -> Unit,
    onCreateAtHour: (Int) -> Unit,
    onRescheduleByDrag: (CalendarEvent, LocalDay, Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val isDark = LocalIsDarkTheme.current
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current

    val startHour = 6
    val endHour = 23
    val hourHeightDp = 64.dp
    val hourHeightPx = with(density) { hourHeightDp.toPx() }
    val scrollState = rememberScrollState()

    val allDayEvents = events.filter { it.isAllDay }
    val timedEvents = events.filter { !it.isAllDay }

    var draggingEvent by remember { mutableStateOf<CalendarEvent?>(null) }
    var dragDeltaY by remember { mutableFloatStateOf(0f) }
    var dragPointerRoot by remember { mutableStateOf(Offset.Zero) }
    val dayDropTargets = remember { mutableStateMapOf<LocalDay, Rect>() }
    var hoveredDay by remember { mutableStateOf<LocalDay?>(null) }

    val candidateDays = remember(selectedDay) {
        listOf(
            selectedDay.plusDays(-1),
            selectedDay,
            selectedDay.plusDays(1),
            selectedDay.plusDays(2)
        )
    }

    fun computeTargetMinutes(event: CalendarEvent, deltaPx: Float): Int {
        val origMinutes = DateTimeUtils.hourOf(event.startTimeMillis) * 60 +
            DateTimeUtils.minuteOf(event.startTimeMillis)
        val deltaMinutes = (deltaPx / hourHeightPx * 60f).roundToInt()
        val raw = origMinutes + deltaMinutes
        val snapped = ((raw + 7) / 15) * 15
        return snapped.coerceIn(startHour * 60, endHour * 60 + 30)
    }

    Column(modifier = modifier.fillMaxWidth()) {
        AnimatedVisibility(
            visible = draggingEvent != null,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 10.dp),
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.secondaryContainer,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f))
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    val active = draggingEvent
                    val targetMins = if (active != null) computeTargetMinutes(active, dragDeltaY) else 9 * 60
                    val previewTime = String.format(
                        "%02d:%02d",
                        targetMins / 60,
                        targetMins % 60
                    )
                    Text(
                        text = "Drop on a day or release to reschedule • $previewTime",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        candidateDays.forEach { day ->
                            val isHover = hoveredDay == day
                            Surface(
                                modifier = Modifier
                                    .weight(1f)
                                    .onGloballyPositioned { coords ->
                                        dayDropTargets[day] = coords.boundsInRoot()
                                    },
                                shape = RoundedCornerShape(10.dp),
                                color = if (isHover) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                                border = BorderStroke(
                                    width = if (isHover) 2.dp else 1.dp,
                                    color = if (isHover) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                                )
                            ) {
                                Column(
                                    modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = if (day == selectedDay) "Today Sel." else day.formatWeekdayShort(),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (isHover) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = day.formatShortDate(),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = if (isHover) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        if (allDayEvents.isNotEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "ALL DAY",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                allDayEvents.forEach { ev ->
                    EventAgendaCard(
                        event = ev,
                        isLockedMasked = ev.isBiometricLocked && ev.id !in unlockedEventIds,
                        onClick = { onEventClick(ev) }
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(scrollState)
                .testTag("drag_timeline_grid")
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                for (hour in startHour..endHour) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(hourHeightDp)
                            .clickable { onCreateAtHour(hour) }
                            .testTag("timeline_hour_$hour"),
                        verticalAlignment = Alignment.Top
                    ) {
                        Text(
                            text = DateTimeUtils.formatHourLabel(hour),
                            modifier = Modifier
                                .width(56.dp)
                                .padding(top = 2.dp, end = 8.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Box(modifier = Modifier.weight(1f)) {
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f),
                                thickness = 1.dp
                            )
                        }
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 60.dp, end = 4.dp)
            ) {
                timedEvents.forEach { event ->
                    val isDragging = draggingEvent?.id == event.id
                    val isMasked = event.isBiometricLocked && event.id !in unlockedEventIds
                    val eventStartMinutes = (DateTimeUtils.hourOf(event.startTimeMillis) * 60 +
                        DateTimeUtils.minuteOf(event.startTimeMillis)).coerceIn(startHour * 60, endHour * 60)
                    val durationMins = DateTimeUtils.durationMinutes(event.startTimeMillis, event.endTimeMillis)
                        .coerceAtLeast(30)

                    val baseTopPx = ((eventStartMinutes - startHour * 60) / 60f) * hourHeightPx
                    val offsetPx = if (isDragging) baseTopPx + dragDeltaY else baseTopPx
                    val cardHeightDp = ((durationMins / 60f) * 64f).dp.coerceAtLeast(46.dp)
                    val categoryColor = event.category.accentColor(isDark)

                    var cardRootPos by remember { mutableStateOf(Offset.Zero) }

                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(cardHeightDp)
                            .offset { IntOffset(0, offsetPx.roundToInt()) }
                            .zIndex(if (isDragging) 10f else 1f)
                            .onGloballyPositioned { coords ->
                                cardRootPos = coords.positionInRoot()
                            }
                            .pointerInput(event.id, event.startTimeMillis) {
                                detectDragGesturesAfterLongPress(
                                    onDragStart = { localOffset ->
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        draggingEvent = event
                                        dragDeltaY = 0f
                                        dragPointerRoot = cardRootPos + localOffset
                                        hoveredDay = null
                                    },
                                    onDrag = { change, dragAmount ->
                                        change.consume()
                                        dragDeltaY += dragAmount.y
                                        dragPointerRoot += dragAmount
                                        hoveredDay = dayDropTargets.entries
                                            .firstOrNull { (_, rect) -> rect.contains(dragPointerRoot) }
                                            ?.key
                                    },
                                    onDragEnd = {
                                        val targetDay = hoveredDay ?: selectedDay
                                        val newMinutes = computeTargetMinutes(event, dragDeltaY)
                                        draggingEvent = null
                                        dragDeltaY = 0f
                                        hoveredDay = null
                                        onRescheduleByDrag(event, targetDay, newMinutes)
                                    },
                                    onDragCancel = {
                                        draggingEvent = null
                                        dragDeltaY = 0f
                                        hoveredDay = null
                                    }
                                )
                            }
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onEventClick(event) }
                            .testTag("timeline_event_${event.id}"),
                        shape = RoundedCornerShape(12.dp),
                        color = if (isDragging) {
                            categoryColor.copy(alpha = 0.28f)
                        } else {
                            categoryColor.copy(alpha = 0.16f)
                        },
                        shadowElevation = if (isDragging) 10.dp else 1.dp,
                        border = BorderStroke(
                            width = if (isDragging) 2.dp else 1.dp,
                            color = categoryColor
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(4.dp)
                                    .height(28.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(categoryColor)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (isMasked) "Locked Event • Tap to unlock" else event.title,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = if (isMasked) categoryColor else MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = if (isDragging) {
                                        val m = computeTargetMinutes(event, dragDeltaY)
                                        "Moving to ${String.format("%02d:%02d", m / 60, m % 60)}"
                                    } else {
                                        DateTimeUtils.formatTimeRange(
                                            event.startTimeMillis,
                                            event.endTimeMillis,
                                            false
                                        )
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = categoryColor
                                )
                            }
                            if (event.isBiometricLocked) {
                                Icon(
                                    imageVector = Icons.Outlined.Fingerprint,
                                    contentDescription = "Biometric locked",
                                    modifier = Modifier.size(15.dp),
                                    tint = categoryColor
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                            }
                            if (event.reminderMinutesBefore >= 0) {
                                Icon(
                                    imageVector = Icons.Rounded.NotificationsActive,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp),
                                    tint = categoryColor
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                            }
                            Icon(
                                imageVector = Icons.Rounded.DragIndicator,
                                contentDescription = "Long press and drag to reschedule",
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}
