package com.devekene.kalenda.domain.search

import com.devekene.kalenda.domain.model.CalendarEvent
import com.devekene.kalenda.domain.model.EventCategory
import com.devekene.kalenda.domain.util.DateTimeUtils
import com.devekene.kalenda.domain.util.LocalDay
import com.devekene.kalenda.domain.util.YearMonthKey
import java.util.Calendar
import java.util.Locale

enum class TimeOfDayFilter(val label: String) {
    MORNING("Morning (5am–12pm)"),
    AFTERNOON("Afternoon (12pm–5pm)"),
    EVENING("Evening (5pm–11pm)")
}

data class ParsedSearchQuery(
    val rawQuery: String,
    val startMillis: Long? = null,
    val endMillis: Long? = null,
    val categories: Set<EventCategory> = emptySet(),
    val timeOfDay: TimeOfDayFilter? = null,
    val requireRemindersOnly: Boolean = false,
    val weekendOnly: Boolean = false,
    val remainingKeywords: List<String> = emptyList(),
    val interpretationTags: List<String> = emptyList()
)

data class SearchResultItem(
    val event: CalendarEvent,
    val isPast: Boolean,
    val matchScore: Int,
    val matchedFields: List<String>
)

class NaturalLanguageSearchEngine {

    fun parse(query: String, nowMillis: Long = System.currentTimeMillis()): ParsedSearchQuery {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return ParsedSearchQuery(rawQuery = "")

        val lower = trimmed.lowercase(Locale.getDefault())
        val tags = mutableListOf<String>()
        var startMillis: Long? = null
        var endMillis: Long? = null
        var consumedPhrase = lower
        val today = LocalDay.fromEpochMillis(nowMillis)

        val phraseHandlers: List<Pair<String, () -> Unit>> = listOf(
            "last 30 days" to {
                startMillis = today.plusDays(-30).startOfDayMillis()
                endMillis = nowMillis
                tags += "Last 30 days"
            },
            "past 30 days" to {
                startMillis = today.plusDays(-30).startOfDayMillis()
                endMillis = nowMillis
                tags += "Past 30 days"
            },
            "last 7 days" to {
                startMillis = today.plusDays(-7).startOfDayMillis()
                endMillis = nowMillis
                tags += "Last 7 days"
            },
            "past 7 days" to {
                startMillis = today.plusDays(-7).startOfDayMillis()
                endMillis = nowMillis
                tags += "Past 7 days"
            },
            "last month" to {
                val prevMonth = today.toYearMonth().plusMonths(-1)
                startMillis = prevMonth.startMillis()
                endMillis = prevMonth.endMillis()
                tags += prevMonth.formatMonthYear()
            },
            "this month" to {
                val curMonth = today.toYearMonth()
                startMillis = curMonth.startMillis()
                endMillis = curMonth.endMillis()
                tags += curMonth.formatMonthYear()
            },
            "next month" to {
                val nextMonth = today.toYearMonth().plusMonths(1)
                startMillis = nextMonth.startMillis()
                endMillis = nextMonth.endMillis()
                tags += nextMonth.formatMonthYear()
            },
            "last week" to {
                val offset = today.dayOfWeek() - Calendar.SUNDAY
                val thisSunday = today.plusDays(-offset)
                val lastSunday = thisSunday.plusDays(-7)
                startMillis = lastSunday.startOfDayMillis()
                endMillis = thisSunday.plusDays(-1).endOfDayMillis()
                tags += "Last week"
            },
            "this week" to {
                val offset = today.dayOfWeek() - Calendar.SUNDAY
                val thisSunday = today.plusDays(-offset)
                startMillis = thisSunday.startOfDayMillis()
                endMillis = thisSunday.plusDays(6).endOfDayMillis()
                tags += "This week"
            },
            "next week" to {
                val offset = today.dayOfWeek() - Calendar.SUNDAY
                val nextSunday = today.plusDays(-offset + 7)
                startMillis = nextSunday.startOfDayMillis()
                endMillis = nextSunday.plusDays(6).endOfDayMillis()
                tags += "Next week"
            },
            "yesterday" to {
                val y = today.plusDays(-1)
                startMillis = y.startOfDayMillis()
                endMillis = y.endOfDayMillis()
                tags += "Yesterday"
            },
            "today" to {
                startMillis = today.startOfDayMillis()
                endMillis = today.endOfDayMillis()
                tags += "Today"
            },
            "tomorrow" to {
                val t = today.plusDays(1)
                startMillis = t.startOfDayMillis()
                endMillis = t.endOfDayMillis()
                tags += "Tomorrow"
            },
            "past events" to {
                endMillis = nowMillis
                tags += "Past entries"
            },
            "before today" to {
                endMillis = today.startOfDayMillis() - 1
                tags += "Before today"
            },
            "upcoming" to {
                startMillis = nowMillis
                tags += "Upcoming"
            },
            "history" to {
                endMillis = nowMillis
                tags += "History"
            },
            "earlier" to {
                endMillis = nowMillis
                tags += "Earlier"
            },
            "past" to {
                endMillis = nowMillis
                tags += "Past"
            }
        )

        phraseHandlers.forEach { (phrase, action) ->
            if (Regex("\\b${Regex.escape(phrase)}\\b").containsMatchIn(consumedPhrase)) {
                action()
                consumedPhrase = consumedPhrase.replace(Regex("\\b${Regex.escape(phrase)}\\b"), " ")
            }
        }

        var detectedYear: Int? = null
        Regex("\\b(20\\d{2})\\b").find(consumedPhrase)?.let { match ->
            detectedYear = match.groupValues[1].toIntOrNull()
            consumedPhrase = consumedPhrase.replace(match.value, " ")
        }

        val monthMap = mapOf(
            "january" to 1, "jan" to 1,
            "february" to 2, "feb" to 2,
            "march" to 3, "mar" to 3,
            "april" to 4, "apr" to 4,
            "may" to 5,
            "june" to 6, "jun" to 6,
            "july" to 7, "jul" to 7,
            "august" to 8, "aug" to 8,
            "september" to 9, "sep" to 9, "sept" to 9,
            "october" to 10, "oct" to 10,
            "november" to 11, "nov" to 11,
            "december" to 12, "dec" to 12
        )

        var detectedMonth: Int? = null
        for ((name, monthNum) in monthMap) {
            val regex = Regex("\\b$name\\b")
            if (regex.containsMatchIn(consumedPhrase)) {
                detectedMonth = monthNum
                consumedPhrase = consumedPhrase.replace(regex, " ")
                break
            }
        }

        if (detectedMonth != null) {
            val year = detectedYear ?: today.year
            val ym = YearMonthKey(year, detectedMonth)
            startMillis = ym.startMillis()
            endMillis = ym.endMillis()
            tags += ym.formatMonthYear()
        } else if (detectedYear != null && startMillis == null && endMillis == null) {
            startMillis = LocalDay(detectedYear!!, 1, 1).startOfDayMillis()
            endMillis = LocalDay(detectedYear!!, 12, 31).endOfDayMillis()
            tags += "Year $detectedYear"
        }

        var timeOfDay: TimeOfDayFilter? = null
        when {
            Regex("\\bmorning\\b").containsMatchIn(consumedPhrase) -> {
                timeOfDay = TimeOfDayFilter.MORNING
                consumedPhrase = consumedPhrase.replace(Regex("\\bmorning\\b"), " ")
                tags += "Morning"
            }
            Regex("\\bafternoon\\b").containsMatchIn(consumedPhrase) -> {
                timeOfDay = TimeOfDayFilter.AFTERNOON
                consumedPhrase = consumedPhrase.replace(Regex("\\bafternoon\\b"), " ")
                tags += "Afternoon"
            }
            Regex("\\bevening\\b|\\bnight\\b").containsMatchIn(consumedPhrase) -> {
                timeOfDay = TimeOfDayFilter.EVENING
                consumedPhrase = consumedPhrase.replace(Regex("\\bevening\\b|\\bnight\\b"), " ")
                tags += "Evening"
            }
        }

        var weekendOnly = false
        if (Regex("\\bweekends?\\b").containsMatchIn(consumedPhrase)) {
            weekendOnly = true
            consumedPhrase = consumedPhrase.replace(Regex("\\bweekends?\\b"), " ")
            tags += "Weekends"
        }

        var requireRemindersOnly = false
        if (Regex("\\b(with reminders?|reminded|alerts?|notifications?)\\b").containsMatchIn(consumedPhrase)) {
            requireRemindersOnly = true
            consumedPhrase = consumedPhrase.replace(
                Regex("\\b(with reminders?|reminded|alerts?|notifications?)\\b"),
                " "
            )
            tags += "Has reminder"
        }

        val matchedCategories = mutableSetOf<EventCategory>()
        EventCategory.entries.forEach { cat ->
            val catRegex = Regex("\\b${Regex.escape(cat.displayName.lowercase())}\\b")
            if (catRegex.containsMatchIn(consumedPhrase)) {
                matchedCategories += cat
                consumedPhrase = consumedPhrase.replace(catRegex, " ")
                tags += cat.displayName
            }
        }

        val stopWords = setOf(
            "show", "find", "search", "for", "me", "my", "all", "events", "event",
            "meetings", "schedule", "entries", "in", "on", "at", "from", "with", "during", "the", "a", "an"
        )
        val keywords = consumedPhrase
            .split(Regex("[\\s,.;:!?]+"))
            .map { it.trim() }
            .filter { it.length >= 2 && it !in stopWords }
            .distinct()

        if (keywords.isNotEmpty()) {
            tags += "Keywords: ${keywords.joinToString(", ")}"
        }

        return ParsedSearchQuery(
            rawQuery = trimmed,
            startMillis = startMillis,
            endMillis = endMillis,
            categories = matchedCategories,
            timeOfDay = timeOfDay,
            requireRemindersOnly = requireRemindersOnly,
            weekendOnly = weekendOnly,
            remainingKeywords = keywords,
            interpretationTags = tags.distinct()
        )
    }

    fun search(
        events: List<CalendarEvent>,
        query: String,
        nowMillis: Long = System.currentTimeMillis(),
        unlockedEventIds: Set<String> = emptySet()
    ): Pair<ParsedSearchQuery, List<SearchResultItem>> {
        val parsed = parse(query, nowMillis)
        if (parsed.rawQuery.isBlank()) {
            val all = events
                .sortedByDescending { it.startTimeMillis }
                .map {
                    SearchResultItem(
                        event = it,
                        isPast = it.endTimeMillis < nowMillis,
                        matchScore = 1,
                        matchedFields = emptyList()
                    )
                }
            return parsed to all
        }

        val matches = events.mapNotNull { event ->
            if (parsed.startMillis != null && event.endTimeMillis < parsed.startMillis) return@mapNotNull null
            if (parsed.endMillis != null && event.startTimeMillis > parsed.endMillis) return@mapNotNull null
            if (parsed.categories.isNotEmpty() && event.category !in parsed.categories) return@mapNotNull null
            if (parsed.requireRemindersOnly && event.reminderMinutesBefore < 0) return@mapNotNull null
            if (parsed.weekendOnly && !LocalDay.fromEpochMillis(event.startTimeMillis).isWeekend()) {
                return@mapNotNull null
            }

            if (parsed.timeOfDay != null && !event.isAllDay) {
                val hour = DateTimeUtils.hourOf(event.startTimeMillis)
                val matchesTod = when (parsed.timeOfDay) {
                    TimeOfDayFilter.MORNING -> hour in 5..11
                    TimeOfDayFilter.AFTERNOON -> hour in 12..16
                    TimeOfDayFilter.EVENING -> hour in 17..23
                }
                if (!matchesTod) return@mapNotNull null
            }

            var score = 10
            val matchedFields = mutableListOf<String>()
            if (parsed.remainingKeywords.isNotEmpty()) {
                val isHiddenByLock = event.isBiometricLocked && event.id !in unlockedEventIds
                val titleLower = if (isHiddenByLock) "locked event" else event.title.lowercase()
                val descLower = if (isHiddenByLock) "" else event.description.lowercase()
                val locLower = if (isHiddenByLock) "" else event.location.lowercase()
                val catLower = event.category.displayName.lowercase()
                val dateLower = LocalDay.fromEpochMillis(event.startTimeMillis).formatFullDate().lowercase()

                var keywordMatchedCount = 0
                parsed.remainingKeywords.forEach { kw ->
                    var hit = false
                    if (titleLower.contains(kw)) {
                        score += 30
                        if ("Title" !in matchedFields) matchedFields += "Title"
                        hit = true
                    }
                    if (descLower.contains(kw)) {
                        score += 15
                        if ("Notes" !in matchedFields) matchedFields += "Notes"
                        hit = true
                    }
                    if (locLower.contains(kw)) {
                        score += 18
                        if ("Location" !in matchedFields) matchedFields += "Location"
                        hit = true
                    }
                    if (catLower.contains(kw) || event.category.keywords.any { it.contains(kw) }) {
                        score += 12
                        if ("Category" !in matchedFields) matchedFields += "Category"
                        hit = true
                    }
                    if (dateLower.contains(kw)) {
                        score += 10
                        if ("Date" !in matchedFields) matchedFields += "Date"
                        hit = true
                    }
                    if (hit) keywordMatchedCount++
                }
                if (keywordMatchedCount == 0) return@mapNotNull null
                score += keywordMatchedCount * 10
            }

            SearchResultItem(
                event = event,
                isPast = event.endTimeMillis < nowMillis,
                matchScore = score,
                matchedFields = matchedFields
            )
        }.sortedWith(
            compareByDescending<SearchResultItem> { it.matchScore }
                .thenByDescending { it.event.startTimeMillis }
        )

        return parsed to matches
    }
}
