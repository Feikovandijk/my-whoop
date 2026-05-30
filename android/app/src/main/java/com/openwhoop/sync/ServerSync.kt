package com.openwhoop.sync

import android.content.Context
import com.google.gson.Gson
import com.openwhoop.ble.protocol.*
import com.openwhoop.database.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Calendar
import java.util.TimeZone

class ServerSync(
    private val context: Context,
    private val store: WhoopStore,
    private val deviceId: String
) {
    private val service = NetworkClient.getService()
    private val gson = Gson()

    companion object {
        val DECODED_KINDS = listOf("hr", "rr", "spo2", "skin_temp", "resp", "gravity", "battery", "events")
        const val PAGE_LIMIT = 5000
        const val DERIVED_WINDOW_DAYS = 60
        const val FULL_RESTORE_WINDOW_DAYS = 400

        private fun coerceInt(obj: Any?): Int? {
            if (obj is Number) return obj.toInt()
            if (obj is String) return obj.toIntOrNull()
            return null
        }

        private fun coerceDouble(obj: Any?): Double? {
            if (obj is Number) return obj.toDouble()
            if (obj is String) return obj.toDoubleOrNull()
            return null
        }

        fun parseEpoch(iso: String): Long? {
            try {
                return Instant.parse(iso).epochSecond
            } catch (e: Exception) {}
            try {
                return OffsetDateTime.parse(iso).toEpochSecond()
            } catch (e: Exception) {}
            try {
                val zdt = ZonedDateTime.parse(iso)
                return zdt.toEpochSecond()
            } catch (e: Exception) {}
            return null
        }

        private fun parseEpochFromField(obj: Any?): Long? {
            if (obj == null) return null
            if (obj is Number) return obj.toLong()
            if (obj is String) {
                return obj.toLongOrNull() ?: parseEpoch(obj)
            }
            return null
        }
    }

    suspend fun pull() = withContext(Dispatchers.IO) {
        pullDecoded()
        pullDerived()
    }

    private suspend fun isDecodedStoreEmpty(): Boolean {
        return try {
            val stats = store.storageStats()
            if (stats.decodedRows > 0) return false
            for (kind in DECODED_KINDS) {
                if (store.readHighwater(kind) != null) return false
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    suspend fun restoreIfEmpty(): Boolean {
        if (!isDecodedStoreEmpty()) return false
        pullDecodedFull()
        pullDerivedFull()
        return true
    }

    private suspend fun pullDecoded() {
        for (kind in DECODED_KINDS) {
            pullStream(kind)
        }
    }

    private suspend fun pullDecodedFull() {
        for (kind in DECODED_KINDS) {
            pullStream(kind, forcedFrom = 0L)
        }
    }

    private suspend fun pullStream(kind: String, forcedFrom: Long? = null) {
        val config = NetworkConfig.load(context)
        var overrideFrom = forcedFrom
        var highwater = store.readHighwater(kind) ?: Long.MIN_VALUE

        while (true) {
            val from = if (overrideFrom != null) {
                val f = overrideFrom!!
                overrideFrom = null
                f
            } else {
                if (highwater <= 0L) 0L else highwater + 1
            }

            val result = getStreamRows(config, kind, from, Long.MAX_VALUE, PAGE_LIMIT) ?: return
            val rows = result.first
            val rawCount = result.second

            if (rows.isEmpty()) return

            val streams = buildStreams(kind, rows) ?: return
            val maxTs = rows.mapNotNull { parseEpochFromField(it["ts"]) }.maxOrNull()

            try {
                store.insert(streams, deviceId, markSynced = true)
            } catch (e: Exception) {
                return
            }

            if (maxTs != null && maxTs > highwater) {
                try {
                    store.setReadHighwater(kind, maxTs)
                } catch (e: Exception) {}
                highwater = maxTs
            }

            if (rawCount < PAGE_LIMIT) return
        }
    }

    private fun buildStreams(kind: String, rows: List<Map<String, Any>>): Streams? {
        val s = Streams()
        when (kind) {
            "hr" -> {
                s.hr = rows.mapNotNull { r ->
                    val ts = parseEpochFromField(r["ts"])
                    val bpm = coerceInt(r["bpm"])
                    if (ts != null && bpm != null) HRSample(ts, bpm) else null
                }.toMutableList()
            }
            "rr" -> {
                s.rr = rows.mapNotNull { r ->
                    val ts = parseEpochFromField(r["ts"])
                    val rrMs = coerceInt(r["rr_ms"]) ?: coerceInt(r["rrMs"])
                    if (ts != null && rrMs != null) RRInterval(ts, rrMs) else null
                }.toMutableList()
            }
            "spo2" -> {
                s.spo2 = rows.mapNotNull { r ->
                    val ts = parseEpochFromField(r["ts"])
                    val red = coerceInt(r["red"])
                    val ir = coerceInt(r["ir"])
                    if (ts != null && red != null && ir != null) SpO2Sample(ts, red, ir) else null
                }.toMutableList()
            }
            "skin_temp" -> {
                s.skinTemp = rows.mapNotNull { r ->
                    val ts = parseEpochFromField(r["ts"])
                    val raw = coerceInt(r["raw"])
                    if (ts != null && raw != null) SkinTempSample(ts, raw) else null
                }.toMutableList()
            }
            "resp" -> {
                s.resp = rows.mapNotNull { r ->
                    val ts = parseEpochFromField(r["ts"])
                    val raw = coerceInt(r["raw"])
                    if (ts != null && raw != null) RespSample(ts, raw) else null
                }.toMutableList()
            }
            "gravity" -> {
                s.gravity = rows.mapNotNull { r ->
                    val ts = parseEpochFromField(r["ts"])
                    val x = coerceDouble(r["x"])
                    val y = coerceDouble(r["y"])
                    val z = coerceDouble(r["z"])
                    if (ts != null && x != null && y != null && z != null) GravitySample(ts, x, y, z) else null
                }.toMutableList()
            }
            "battery" -> {
                s.battery = rows.mapNotNull { r ->
                    val ts = parseEpochFromField(r["ts"]) ?: return@mapNotNull null
                    val charging = r["charging"] as? Boolean ?: (coerceInt(r["charging"])?.let { it != 0 })
                    BatterySample(ts, coerceDouble(r["soc"]), coerceInt(r["mv"]), charging)
                }.toMutableList()
            }
            "events" -> {
                s.events = rows.mapNotNull { r ->
                    val ts = parseEpochFromField(r["ts"]) ?: return@mapNotNull null
                    val eventKind = r["kind"] as? String ?: return@mapNotNull null
                    val payload = mutableMapOf<String, ParsedValue>()
                    
                    @Suppress("UNCHECKED_CAST")
                    val pMap = r["payload"] as? Map<String, Any>
                    pMap?.forEach { (k, v) ->
                        val parsedVal = when (v) {
                            is Boolean -> ParsedValue.BoolVal(v)
                            is Number -> {
                                val d = v.toDouble()
                                if (d == d.toInt().toDouble()) ParsedValue.IntVal(d.toInt()) else ParsedValue.DoubleVal(d)
                            }
                            is String -> ParsedValue.StringVal(v)
                            is List<*> -> ParsedValue.IntArrayVal(v.filterIsInstance<Number>().map { it.toInt() })
                            else -> ParsedValue.NullVal
                        }
                        payload[k] = parsedVal
                    }
                    WhoopEvent(ts, eventKind, payload)
                }.toMutableList()
            }
            else -> return null
        }
        return s
    }

    private suspend fun getStreamRows(config: NetworkConfig, kind: String, from: Long, to: Long, limit: Int): Pair<List<Map<String, Any>>, Int>? {
        return try {
            val toVal = if (to == Long.MAX_VALUE) (System.currentTimeMillis() / 1000 + 86400) else to
            val url = "${config.baseURL}/v1/streams/$kind"
            val auth = "Bearer ${config.apiKey}"
            val response = service.getStream(url, auth, deviceId, from, toVal, limit)
            if (response.isSuccessful) {
                val list = response.body() ?: emptyList()
                val rawCount = list.size
                val parsedList = list.mapNotNull { r ->
                    val tsField = r["ts"]
                    val epoch = parseEpochFromField(tsField) ?: return@mapNotNull null
                    val mutableRow = r.toMutableMap()
                    mutableRow["ts"] = epoch
                    mutableRow
                }
                Pair(parsedList, rawCount)
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    private suspend fun pullDerived() {
        pullDerivedWindow(DERIVED_WINDOW_DAYS)
    }

    private suspend fun pullDerivedFull() {
        pullDerivedWindow(FULL_RESTORE_WINDOW_DAYS)
    }

    private suspend fun pullDerivedWindow(windowDays: Int) {
        val config = NetworkConfig.load(context)
        val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        val endYear = cal.get(Calendar.YEAR)
        val endMonth = cal.get(Calendar.MONTH) + 1
        val endDay = cal.get(Calendar.DAY_OF_MONTH)
        val toDay = String.format("%04d-%02d-%02d", endYear, endMonth, endDay)

        cal.add(Calendar.DAY_OF_YEAR, -windowDays)
        val startYear = cal.get(Calendar.YEAR)
        val startMonth = cal.get(Calendar.MONTH) + 1
        val startDay = cal.get(Calendar.DAY_OF_MONTH)
        val fromDay = String.format("%04d-%02d-%02d", startYear, startMonth, startDay)

        val days = getDaily(config, fromDay, toDay) ?: return
        if (days.isNotEmpty()) {
            try {
                store.upsertDailyMetrics(days, deviceId)
            } catch (e: Exception) {}
        }

        for (metric in days) {
            val sessions = getSleep(config, metric.day)
            if (!sessions.isNullOrEmpty()) {
                try {
                    store.upsertSleepSessions(sessions, deviceId)
                } catch (e: Exception) {}
            }
        }
    }

    private suspend fun getDaily(config: NetworkConfig, from: String, to: String): List<DailyMetric>? {
        return try {
            val url = "${config.baseURL}/v1/daily"
            val auth = "Bearer ${config.apiKey}"
            val response = service.getDaily(url, auth, deviceId, from, to)
            if (response.isSuccessful) {
                val list = response.body() ?: return null
                list.mapNotNull { r ->
                    val day = r["day"] as? String ?: return@mapNotNull null
                    DailyMetric(
                        day = day,
                        totalSleepMin = coerceDouble(r["total_sleep_min"] ?: r["totalSleepMin"]),
                        efficiency = coerceDouble(r["efficiency"]),
                        deepMin = coerceDouble(r["deep_min"] ?: r["deepMin"]),
                        remMin = coerceDouble(r["rem_min"] ?: r["remMin"]),
                        lightMin = coerceDouble(r["light_min"] ?: r["lightMin"]),
                        disturbances = coerceInt(r["disturbances"]),
                        restingHr = coerceInt(r["resting_hr"] ?: r["restingHr"]),
                        avgHrv = coerceDouble(r["avg_hrv"] ?: r["avgHrv"]),
                        recovery = coerceDouble(r["recovery"])?.let { it / 100.0 }, // normalize recovery
                        strain = coerceDouble(r["strain"]),
                        exerciseCount = coerceInt(r["exercise_count"] ?: r["exerciseCount"]),
                        spo2Pct = coerceDouble(r["spo2_pct"] ?: r["spo2Pct"]),
                        skinTempDevC = coerceDouble(r["skin_temp_dev_c"] ?: r["skinTempDevC"]),
                        respRateBpm = coerceDouble(r["resp_rate_bpm"] ?: r["respRateBpm"])
                    )
                }
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    private suspend fun getSleep(config: NetworkConfig, date: String): List<CachedSleepSession>? {
        return try {
            val url = "${config.baseURL}/v1/sleep"
            val auth = "Bearer ${config.apiKey}"
            val response = service.getSleep(url, auth, deviceId, date)
            if (response.isSuccessful) {
                val bodyStr = response.body()?.string() ?: return null
                if (bodyStr.trim().isEmpty() || bodyStr.trim() == "{}") return emptyList()
                
                // Decode either a single object or an array of objects
                val dicts = when {
                    bodyStr.trim().startsWith("[") -> {
                        gson.fromJson<List<Map<String, Any>>>(bodyStr, object : com.google.gson.reflect.TypeToken<List<Map<String, Any>>>() {}.type)
                    }
                    bodyStr.trim().startsWith("{") -> {
                        val single = gson.fromJson<Map<String, Any>>(bodyStr, object : com.google.gson.reflect.TypeToken<Map<String, Any>>() {}.type)
                        listOf(single)
                    }
                    else -> emptyList()
                }

                dicts.mapNotNull { r ->
                    val start = parseEpochFromField(r["start_ts"] ?: r["startTs"]) ?: return@mapNotNull null
                    val end = parseEpochFromField(r["end_ts"] ?: r["endTs"]) ?: return@mapNotNull null
                    val stagesObj = r["stages"]
                    val stagesJSON = if (stagesObj != null) gson.toJson(stagesObj) else null
                    CachedSleepSession(
                        startTs = start,
                        endTs = end,
                        efficiency = coerceDouble(r["efficiency"]),
                        restingHr = coerceInt(r["resting_hr"] ?: r["restingHr"]),
                        avgHrv = coerceDouble(r["avg_hrv"] ?: r["avgHrv"]),
                        stagesJSON = stagesJSON
                    )
                }
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    suspend fun getWorkouts(from: String, to: String): List<Workout> = withContext(Dispatchers.IO) {
        val config = NetworkConfig.load(context)
        val url = "${config.baseURL}/v1/workouts"
        val auth = "Bearer ${config.apiKey}"
        try {
            val response = service.getWorkouts(url, auth, deviceId, from, to)
            if (response.isSuccessful) {
                val list = response.body() ?: return@withContext emptyList()
                val workouts = list.mapNotNull { r ->
                    val start = parseEpochFromField(r["start_ts"] ?: r["startTs"]) ?: return@mapNotNull null
                    val end = parseEpochFromField(r["end_ts"] ?: r["endTs"]) ?: return@mapNotNull null
                    val avgHr = coerceDouble(r["avg_hr"] ?: r["avgHr"]) ?: return@mapNotNull null
                    val peakHr = coerceInt(r["peak_hr"] ?: r["peakHr"]) ?: return@mapNotNull null
                    val durS = coerceInt(r["duration_s"] ?: r["durationS"]) ?: return@mapNotNull null
                    val strain = coerceDouble(r["strain"])
                    val kind = r["kind"] as? String
                    val dev = (r["device_id"] as? String) ?: deviceId
                    val hrmax = coerceDouble(r["hrmax"])
                    val hrmaxSource = (r["hrmax_source"] ?: r["hrmaxSource"] ?: "") as String
                    val caloriesKcal = coerceDouble(r["calories_kcal"] ?: r["caloriesKcal"])
                    val caloriesKj = coerceDouble(r["calories_kj"] ?: r["caloriesKj"])
                    
                    val zoneTimePct = mutableMapOf<Int, Double>()
                    val zObj = r["zone_time_pct"] as? Map<*, *>
                    zObj?.forEach { (k, v) ->
                        val zone = k.toString().toIntOrNull()
                        val pct = coerceDouble(v)
                        if (zone != null && pct != null) {
                            zoneTimePct[zone] = pct
                        }
                    }

                    Workout(
                        id = "$dev|$start",
                        deviceId = dev,
                        startTs = start,
                        endTs = end,
                        avgHr = avgHr,
                        peakHr = peakHr,
                        strain = strain,
                        kind = kind,
                        durationS = durS,
                        zoneTimePct = zoneTimePct,
                        avgHrrPct = coerceDouble(r["avg_hrr_pct"] ?: r["avgHrrPct"]),
                        hrmax = hrmax,
                        hrmaxSource = hrmaxSource,
                        caloriesKcal = caloriesKcal,
                        caloriesKj = caloriesKj
                    )
                }
                workouts.reversed()
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun getDailyExplanation(date: String): MetricExplanation? = withContext(Dispatchers.IO) {
        val config = NetworkConfig.load(context)
        val url = "${config.baseURL}/v1/explain/daily"
        val auth = "Bearer ${config.apiKey}"
        try {
            val response = service.getDailyExplanation(url, auth, deviceId, date)
            if (response.isSuccessful) response.body() else null
        } catch (e: Exception) {
            null
        }
    }

    suspend fun getHRSeries(fromEpoch: Long, toEpoch: Long, maxPoints: Int): List<TrendPoint> = withContext(Dispatchers.IO) {
        val config = NetworkConfig.load(context)
        val url = "${config.baseURL}/v1/streams/hr"
        val auth = "Bearer ${config.apiKey}"
        try {
            val response = service.getHRSeries(url, auth, deviceId, fromEpoch, toEpoch, maxPoints)
            if (response.isSuccessful) {
                val list = response.body() ?: return@withContext emptyList()
                list.mapNotNull { r ->
                    val ts = parseEpochFromField(r["ts"]) ?: return@mapNotNull null
                    val bpm = coerceDouble(r["bpm"]) ?: return@mapNotNull null
                    TrendPoint(
                        id = ts.toString(),
                        date = ts,
                        value = bpm
                    )
                }
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun backfillWorkouts(from: String, to: String): Boolean = withContext(Dispatchers.IO) {
        val config = NetworkConfig.load(context)
        val url = "${config.baseURL}/v1/backfill-workouts"
        val auth = "Bearer ${config.apiKey}"
        val body = mapOf("device" to deviceId, "from" to from, "to" to to)
        try {
            val response = service.backfillWorkouts(url, auth, body)
            response.isSuccessful
        } catch (e: Exception) {
            false
        }
    }
}
