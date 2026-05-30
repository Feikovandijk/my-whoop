package com.openwhoop.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "device")
data class DeviceEntity(
    @PrimaryKey val id: String,
    val mac: String?,
    val name: String?,
    val firstSeen: Long,
    val lastSeen: Long
)

@Entity(tableName = "hrSample", primaryKeys = ["deviceId", "ts"])
data class HrSampleEntity(
    val deviceId: String,
    val ts: Long,
    val bpm: Int,
    val synced: Int = 0
)

@Entity(tableName = "rrInterval", primaryKeys = ["deviceId", "ts", "rrMs"])
data class RrIntervalEntity(
    val deviceId: String,
    val ts: Long,
    val rrMs: Int,
    val synced: Int = 0
)

@Entity(tableName = "event", primaryKeys = ["deviceId", "ts", "kind"])
data class EventEntity(
    val deviceId: String,
    val ts: Long,
    val kind: String,
    val payloadJSON: String,
    val synced: Int = 0
)

@Entity(tableName = "battery", primaryKeys = ["deviceId", "ts"])
data class BatteryEntity(
    val deviceId: String,
    val ts: Long,
    val soc: Double?,
    val mv: Int?,
    val charging: Boolean?,
    val synced: Int = 0
)

@Entity(tableName = "rawBatch")
data class RawBatchEntity(
    @PrimaryKey val batchId: String,
    val deviceId: String,
    val capturedAt: Long,
    val deviceClockRef: Long,
    val wallClockRef: Long,
    val startTs: Long,
    val endTs: Long,
    val frameCount: Int,
    val byteSize: Int,
    val framesBlob: ByteArray,
    val syncedAt: Long? = null
)

@Entity(tableName = "cursors")
data class CursorEntity(
    @PrimaryKey val name: String,
    val value: Long
)

@Entity(tableName = "spo2Sample", primaryKeys = ["deviceId", "ts"])
data class Spo2SampleEntity(
    val deviceId: String,
    val ts: Long,
    val red: Int,
    val ir: Int,
    val synced: Int = 0
)

@Entity(tableName = "skinTempSample", primaryKeys = ["deviceId", "ts"])
data class SkinTempSampleEntity(
    val deviceId: String,
    val ts: Long,
    val raw: Int,
    val synced: Int = 0
)

@Entity(tableName = "respSample", primaryKeys = ["deviceId", "ts"])
data class RespSampleEntity(
    val deviceId: String,
    val ts: Long,
    val raw: Int,
    val synced: Int = 0
)

@Entity(tableName = "gravitySample", primaryKeys = ["deviceId", "ts"])
data class GravitySampleEntity(
    val deviceId: String,
    val ts: Long,
    val x: Double,
    val y: Double,
    val z: Double,
    val synced: Int = 0
)

@Entity(tableName = "sleepSession", primaryKeys = ["deviceId", "startTs"])
data class SleepSessionEntity(
    val deviceId: String,
    val startTs: Long,
    val endTs: Long,
    val efficiency: Double?,
    val restingHr: Int?,
    val avgHrv: Double?,
    val stagesJSON: String?
)

@Entity(tableName = "dailyMetric", primaryKeys = ["deviceId", "day"])
data class DailyMetricEntity(
    val deviceId: String,
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
    val spo2Pct: Double?,
    val skinTempDevC: Double?,
    val respRateBpm: Double?
)
