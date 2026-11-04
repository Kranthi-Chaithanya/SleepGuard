package com.sleepguard.app.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** One tracked night. */
data class SleepSession(
    val start: Long,
    val end: Long,
    val plannedEnd: Long,
    val mode: BlockMode,
    val screenOnCount: Int,
    val screenOnMillis: Long,
    val blockedAttempts: Int,
    val endReason: String,
    val rating: Int = 0,
) {
    val durationMillis: Long get() = (end - start).coerceAtLeast(0L)

    /**
     * Rough estimate: time in sleep mode minus time the screen was on, minus a typical
     * 15-minute "falling asleep" period.
     */
    val estimatedSleepMillis: Long
        get() = (durationMillis - screenOnMillis - SLEEP_LATENCY_MS).coerceAtLeast(0L)

    fun toJson(): JSONObject = JSONObject().apply {
        put("start", start)
        put("end", end)
        put("plannedEnd", plannedEnd)
        put("mode", mode.name)
        put("screenOnCount", screenOnCount)
        put("screenOnMillis", screenOnMillis)
        put("blockedAttempts", blockedAttempts)
        put("endReason", endReason)
        put("rating", rating)
    }

    companion object {
        const val REASON_COMPLETED = "completed"
        const val REASON_ENDED_EARLY = "ended_early"
        const val REASON_EMERGENCY = "emergency"
        const val SLEEP_LATENCY_MS = 15 * 60_000L

        fun fromJson(o: JSONObject) = SleepSession(
            start = o.getLong("start"),
            end = o.getLong("end"),
            plannedEnd = o.optLong("plannedEnd", o.getLong("end")),
            mode = BlockMode.entries.firstOrNull { it.name == o.optString("mode") } ?: BlockMode.BASIC,
            screenOnCount = o.optInt("screenOnCount", 0),
            screenOnMillis = o.optLong("screenOnMillis", 0L),
            blockedAttempts = o.optInt("blockedAttempts", 0),
            endReason = o.optString("endReason", REASON_COMPLETED),
            rating = o.optInt("rating", 0),
        )
    }
}

/** Sleep history kept as a small JSON file (last 365 nights). */
object SessionStore {
    private const val FILE_NAME = "sessions.json"
    private const val MAX_ENTRIES = 365
    private val lock = Any()

    /** Newest first. */
    fun all(ctx: Context): List<SleepSession> =
        synchronized(lock) { read(ctx) }.sortedByDescending { it.start }

    fun add(ctx: Context, session: SleepSession) = synchronized(lock) {
        val list = (read(ctx) + session).sortedBy { it.start }.takeLast(MAX_ENTRIES)
        write(ctx, list)
    }

    fun setRating(ctx: Context, sessionStart: Long, rating: Int) = synchronized(lock) {
        write(ctx, read(ctx).map { if (it.start == sessionStart) it.copy(rating = rating) else it })
    }

    private fun read(ctx: Context): List<SleepSession> {
        val f = File(ctx.filesDir, FILE_NAME)
        if (!f.exists()) return emptyList()
        return runCatching {
            val arr = JSONArray(f.readText())
            (0 until arr.length()).map { SleepSession.fromJson(arr.getJSONObject(it)) }
        }.getOrDefault(emptyList())
    }

    private fun write(ctx: Context, list: List<SleepSession>) {
        val arr = JSONArray()
        list.forEach { arr.put(it.toJson()) }
        val tmp = File(ctx.filesDir, "$FILE_NAME.tmp")
        tmp.writeText(arr.toString())
        tmp.renameTo(File(ctx.filesDir, FILE_NAME))
    }
}
