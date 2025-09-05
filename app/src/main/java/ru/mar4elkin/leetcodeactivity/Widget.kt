package ru.mar4elkin.leetcodeactivity

import android.annotation.SuppressLint
import android.appwidget.AppWidgetManager
import android.content.Context
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.background
import androidx.glance.appwidget.provideContent
import androidx.glance.layout.Column
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.compose.ui.graphics.Color
import androidx.datastore.dataStore
import androidx.glance.appwidget.cornerRadius
import androidx.glance.layout.Box
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.size
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json.Default.parseToJsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.util.concurrent.TimeUnit

enum class ActivityLevel {
    LEVEL0,
    LEVEL1,
    LEVEL2,
    LEVEL3,
    LEVEL4
}

class Week(context: Context, today: LocalDate = LocalDate.now()) {
    private val context: Context = context
    var yearMonth: YearMonth = YearMonth.from(today)
    var firstDay: LocalDate = yearMonth.atDay(1)
    var lastDay: LocalDate = yearMonth.atEndOfMonth()
    var dayToActivity: HashMap<Int, ActivityLevel> = hashMapOf()
    var httpclient: OkHttpClient = OkHttpClient()

    fun ActivityLevelToColor(level: ActivityLevel?): Color {
        return when (level) {
            ActivityLevel.LEVEL0 -> Color(0xff383838)
            ActivityLevel.LEVEL1 -> Color(0xFF9be9a8)
            ActivityLevel.LEVEL2 -> Color(0xFF40c463)
            ActivityLevel.LEVEL3 -> Color(0xFF30a14e)
            ActivityLevel.LEVEL4 -> Color(0xFF216e39)
            null -> Color(0xff383838)
        }
    }

    suspend fun build(): Boolean {
        return try {
            val nickname = context.dataStore.data
                .map { it[NICKNAME_KEY] ?: "" }
                .first()
            if (nickname.isBlank()) {
                for (i in 1..lastDay.dayOfMonth) dayToActivity[i] = ActivityLevel.LEVEL0
                return false
            }
            val dateToCount = mutableMapOf<LocalDate, Int>()
            val req = Request.Builder()
                .url("https://leetcode-api-faisalshohag.vercel.app/$nickname")
                .get()
                .build()
            val response = withContext(Dispatchers.IO) {
                httpclient.newCall(req).execute()
            }
            response.use {
                if (!response.isSuccessful) {
                    throw IOException("Запрос к серверу не был успешен: ${response.code} ${response.message}")
                }
                val result = response.body?.string() ?: ""
                val resultObject = parseToJsonElement(result)
                val submissionCalendar = resultObject.jsonObject["submissionCalendar"] as? JsonObject
                submissionCalendar?.let { calendar ->
                    var minCount = Int.MAX_VALUE
                    var maxCount = Int.MIN_VALUE
                    calendar.entries.forEach { (timestamp, countElement) ->
                        try {
                            val instant = Instant.ofEpochSecond(timestamp.toLong())
                            val dateTime = instant.atZone(ZoneId.systemDefault())
                            if (yearMonth.month != dateTime.month || yearMonth.year != dateTime.year) {
                                return@forEach
                            }
                            val count = countElement.jsonPrimitive.int
                            val localDate = LocalDate.of(yearMonth.year, yearMonth.month, dateTime.dayOfMonth)
                            dateToCount[localDate] = count
                            if (count < minCount) minCount = count
                            if (count > maxCount) maxCount = count
                        } catch (_: Exception) {
                        }
                    }
                    if (minCount == Int.MAX_VALUE) minCount = 0
                    if (maxCount == Int.MIN_VALUE) maxCount = 0
                    val range = maxCount - minCount
                    val step = if (range > 0) range / 4.0 else 1.0
                    val levelToSubmission = mapOf(
                        ActivityLevel.LEVEL0 to (0..0),
                        ActivityLevel.LEVEL1 to (minCount..(minCount + step).toInt()),
                        ActivityLevel.LEVEL2 to ((minCount + step + 0.1).toInt()..(minCount + step * 2).toInt()),
                        ActivityLevel.LEVEL3 to ((minCount + step * 2 + 0.1).toInt()..(minCount + step * 3).toInt()),
                        ActivityLevel.LEVEL4 to ((minCount + step * 3 + 0.1).toInt()..maxCount)
                    )
                    for (i in 1..lastDay.dayOfMonth) {
                        val day = LocalDate.of(yearMonth.year, yearMonth.month, i)
                        val count = dateToCount[day] ?: 0
                        val level = levelToSubmission.entries.find { (_, range) -> count in range }?.key
                            ?: ActivityLevel.LEVEL0
                        dayToActivity[i] = level
                    }
                    true
                } ?: run {
                    for (i in 1..lastDay.dayOfMonth) {
                        dayToActivity[i] = ActivityLevel.LEVEL0
                    }
                    false
                }
            }
        } catch (e: Exception) {
            for (i in 1..lastDay.dayOfMonth) {
                dayToActivity[i] = ActivityLevel.LEVEL0
            }
            false
        }
    }
}

class ActivityWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ActivityWidget()
    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        val req = PeriodicWorkRequestBuilder<WidgetUpdateWorker>(1, TimeUnit.HOURS).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "WidgetUpdateWork",
            ExistingPeriodicWorkPolicy.KEEP,
            req
        )
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        WorkManager.getInstance(context).cancelUniqueWork("WidgetUpdateWork")
    }
}

class ActivityWidget : GlanceAppWidget() {
    override val stateDefinition = PreferencesGlanceStateDefinition
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val week = Week(context)
        val success = week.build()
        provideContent { Content(week) }
    }
    @SuppressLint("RestrictedApi")
    @Composable
    private fun Content(week: Week) {
        val cellSize = 18.dp
        val gap = 2.dp
        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(day = Color.Red, night = Color.Red)
                .padding(3.dp)
                .background(day = Color.Black, night = Color.Black)
                .padding(12.dp)
        ) {
            Row {
                var index = 1
                repeat(5) { colIndex ->
                    Column(modifier = GlanceModifier.padding(start = if (colIndex > 0) gap else 0.dp)) {
                        Column {
                            repeat(5) {
                                val color = week.ActivityLevelToColor(week.dayToActivity[index])
                                if (index <= week.lastDay.dayOfMonth - 1) {
                                    Box(
                                        modifier = GlanceModifier
                                            .size(cellSize)
                                            .cornerRadius(6.dp)
                                            .background(day = color, night = color),
                                        content = {}
                                    )
                                    index += 1
                                    Spacer(GlanceModifier.size(gap))
                                }
                            }
                        }
                        Column {
                            repeat(2) {
                                val color = week.ActivityLevelToColor(week.dayToActivity[index])
                                if (index <= week.lastDay.dayOfMonth - 1) {
                                    Box(
                                        modifier = GlanceModifier
                                            .size(cellSize)
                                            .cornerRadius(6.dp)
                                            .background(day = color, night = color),
                                        content = {}
                                    )
                                    index += 1
                                    Spacer(GlanceModifier.size(gap))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
