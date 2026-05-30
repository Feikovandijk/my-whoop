package com.openwhoop.ble.protocol

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf

data class ClockRef(
    val device: Long,
    val wall: Long
)

data class RawBatchMeta(
    val batchId: String,
    val deviceId: String,
    val clockRef: ClockRef,
    val capturedAt: Long,
    val startTs: Long,
    val endTs: Long,
    val frameCount: Int,
    val byteSize: Int
)

data class StorageStatsResult(
    val decodedRows: Long,
    val rawBatches: Int,
    val rawBytes: Long
)

data class CachedSleepSession(
    val startTs: Long,
    val endTs: Long,
    val efficiency: Double?,
    val restingHr: Int?,
    val avgHrv: Double?,
    val stagesJSON: String?
)

data class DailyMetric(
    val day: String,
    val totalSleepMin: Double?,
    val efficiency: Double?,
    val deepMin: Double?,
    val remMin: Double?,
    val lightMin: Double?,
    val disturbances: Int?,
    val restingHr: Int?,
    val avgHrv: Double?,
    val recovery: Double?,
    val strain: Double?,
    val exerciseCount: Int?,
    val spo2Pct: Double? = null,
    val skinTempDevC: Double? = null,
    val respRateBpm: Double? = null
)

enum class BackfillTrigger {
    PERIODIC,
    CONNECT,
    FOREGROUND,
    MANUAL,
    STRAP
}

object BackfillPolicy {
    const val PERIODIC_FLOOR_SECONDS = 900L
    const val EVENT_FLOOR_SECONDS = 90L

    fun shouldRun(trigger: BackfillTrigger, now: Double, lastBackfillAt: Double?): Boolean {
        if (lastBackfillAt == null) return true
        val elapsed = now - lastBackfillAt
        return when (trigger) {
            BackfillTrigger.MANUAL -> true
            BackfillTrigger.CONNECT, BackfillTrigger.FOREGROUND, BackfillTrigger.STRAP -> elapsed >= EVENT_FLOOR_SECONDS
            BackfillTrigger.PERIODIC -> elapsed >= PERIODIC_FLOOR_SECONDS
        }
    }
}

class LiveState {
    val connected = mutableStateOf(false)
    val bonded = mutableStateOf(false)
    val heartRate = mutableStateOf<Int?>(null)
    val rr = mutableStateListOf<Int>()
    val batteryPct = mutableStateOf<Double?>(null)
    val lastFrameType = mutableStateOf<String?>(null)
    val lastEvent = mutableStateOf<String?>(null)
    val log = mutableStateListOf<String>()
    val strapNeedsReboot = mutableStateOf(false)
    val lastSyncedAt = mutableStateOf<Double?>(null)

    var onBatteryUpdate: ((Double) -> Unit)? = null

    fun setBattery(pct: Double) {
        batteryPct.value = pct
        onBatteryUpdate?.invoke(pct)
    }

    fun updateRR(newRR: List<Int>) {
        androidx.compose.runtime.snapshots.Snapshot.withMutableSnapshot {
            rr.clear()
            rr.addAll(newRR)
        }
    }

    fun appendLog(line: String) {
        androidx.compose.runtime.snapshots.Snapshot.withMutableSnapshot {
            log.add(line)
            if (log.size > 200) {
                log.removeAt(0)
            }
        }
    }
}

data class Workout(
    val id: String,
    val deviceId: String,
    val startTs: Long,
    val endTs: Long,
    val avgHr: Double,
    val peakHr: Int,
    val strain: Double?,
    val kind: String?,
    val durationS: Int,
    val zoneTimePct: Map<Int, Double>,
    val avgHrrPct: Double?,
    val hrmax: Double?,
    val hrmaxSource: String,
    val caloriesKcal: Double?,
    val caloriesKj: Double?
)

data class TrendPoint(
    val id: String,
    val date: Long,
    val value: Double
)
