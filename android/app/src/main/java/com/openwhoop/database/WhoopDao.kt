package com.openwhoop.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface WhoopDao {

    @Query("""
        INSERT INTO device (id, mac, name, firstSeen, lastSeen)
        VALUES (:id, :mac, :name, :now, :now)
        ON CONFLICT(id) DO UPDATE SET
            mac = excluded.mac,
            name = excluded.name,
            lastSeen = excluded.lastSeen
    """)
    suspend fun upsertDevice(id: String, mac: String?, name: String?, now: Long)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertHrSamples(samples: List<HrSampleEntity>): List<Long>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertRrIntervals(samples: List<RrIntervalEntity>): List<Long>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertEvents(samples: List<EventEntity>): List<Long>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertBattery(samples: List<BatteryEntity>): List<Long>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertSpo2(samples: List<Spo2SampleEntity>): List<Long>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertSkinTemp(samples: List<SkinTempSampleEntity>): List<Long>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertResp(samples: List<RespSampleEntity>): List<Long>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertGravity(samples: List<GravitySampleEntity>): List<Long>

    // Unsynced queries for uploader
    @Query("SELECT * FROM hrSample WHERE deviceId = :deviceId AND synced = 0 ORDER BY ts ASC LIMIT :limit")
    suspend fun getUnsyncedHR(deviceId: String, limit: Int): List<HrSampleEntity>

    @Query("SELECT * FROM rrInterval WHERE deviceId = :deviceId AND synced = 0 ORDER BY ts ASC, rrMs ASC LIMIT :limit")
    suspend fun getUnsyncedRR(deviceId: String, limit: Int): List<RrIntervalEntity>

    @Query("SELECT * FROM event WHERE deviceId = :deviceId AND synced = 0 ORDER BY ts ASC, kind ASC LIMIT :limit")
    suspend fun getUnsyncedEvents(deviceId: String, limit: Int): List<EventEntity>

    @Query("SELECT * FROM battery WHERE deviceId = :deviceId AND synced = 0 ORDER BY ts ASC LIMIT :limit")
    suspend fun getUnsyncedBattery(deviceId: String, limit: Int): List<BatteryEntity>

    @Query("SELECT * FROM spo2Sample WHERE deviceId = :deviceId AND synced = 0 ORDER BY ts ASC LIMIT :limit")
    suspend fun getUnsyncedSpo2(deviceId: String, limit: Int): List<Spo2SampleEntity>

    @Query("SELECT * FROM skinTempSample WHERE deviceId = :deviceId AND synced = 0 ORDER BY ts ASC LIMIT :limit")
    suspend fun getUnsyncedSkinTemp(deviceId: String, limit: Int): List<SkinTempSampleEntity>

    @Query("SELECT * FROM respSample WHERE deviceId = :deviceId AND synced = 0 ORDER BY ts ASC LIMIT :limit")
    suspend fun getUnsyncedResp(deviceId: String, limit: Int): List<RespSampleEntity>

    @Query("SELECT * FROM gravitySample WHERE deviceId = :deviceId AND synced = 0 ORDER BY ts ASC LIMIT :limit")
    suspend fun getUnsyncedGravity(deviceId: String, limit: Int): List<GravitySampleEntity>

    // Mark synced
    @Query("UPDATE hrSample SET synced = 1 WHERE deviceId = :deviceId AND ts = :ts")
    suspend fun markHRSynced(deviceId: String, ts: Long)

    @Query("UPDATE rrInterval SET synced = 1 WHERE deviceId = :deviceId AND ts = :ts AND rrMs = :rrMs")
    suspend fun markRRSynced(deviceId: String, ts: Long, rrMs: Int)

    @Query("UPDATE event SET synced = 1 WHERE deviceId = :deviceId AND ts = :ts AND kind = :kind")
    suspend fun markEventSynced(deviceId: String, ts: Long, kind: String)

    @Query("UPDATE battery SET synced = 1 WHERE deviceId = :deviceId AND ts = :ts")
    suspend fun markBatterySynced(deviceId: String, ts: Long)

    @Query("UPDATE spo2Sample SET synced = 1 WHERE deviceId = :deviceId AND ts = :ts")
    suspend fun markSpo2Synced(deviceId: String, ts: Long)

    @Query("UPDATE skinTempSample SET synced = 1 WHERE deviceId = :deviceId AND ts = :ts")
    suspend fun markSkinTempSynced(deviceId: String, ts: Long)

    @Query("UPDATE respSample SET synced = 1 WHERE deviceId = :deviceId AND ts = :ts")
    suspend fun markRespSynced(deviceId: String, ts: Long)

    @Query("UPDATE gravitySample SET synced = 1 WHERE deviceId = :deviceId AND ts = :ts")
    suspend fun markGravitySynced(deviceId: String, ts: Long)

    // Raw batch outbox
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertRawBatch(batch: RawBatchEntity)

    @Query("SELECT framesBlob FROM rawBatch WHERE batchId = :batchId")
    suspend fun getRawFramesBlob(batchId: String): ByteArray?

    @Query("""
        SELECT batchId, deviceId, capturedAt, deviceClockRef, wallClockRef, startTs, endTs, frameCount, byteSize
        FROM rawBatch WHERE syncedAt IS NULL ORDER BY capturedAt ASC LIMIT :limit
    """)
    suspend fun getPendingRawBatches(limit: Int): List<RawBatchMetaMinimal>

    @Query("UPDATE rawBatch SET syncedAt = :syncedAt WHERE batchId = :batchId")
    suspend fun markRawBatchSynced(batchId: String, syncedAt: Long)

    @Query("DELETE FROM rawBatch WHERE syncedAt IS NOT NULL AND syncedAt < :cutoff")
    suspend fun pruneRawBatches(cutoff: Long): Int

    // Cursors
    @Query("""
        INSERT INTO cursors (name, value) VALUES (:name, :value)
        ON CONFLICT(name) DO UPDATE SET value = excluded.value
    """)
    suspend fun setCursor(name: String, value: Long)

    @Query("SELECT value FROM cursors WHERE name = :name")
    suspend fun getCursor(name: String): Long?

    // Derived metrics upsert
    @Query("""
        INSERT INTO sleepSession (deviceId, startTs, endTs, efficiency, restingHr, avgHrv, stagesJSON)
        VALUES (:deviceId, :startTs, :endTs, :efficiency, :restingHr, :avgHrv, :stagesJSON)
        ON CONFLICT(deviceId, startTs) DO UPDATE SET
            endTs = excluded.endTs,
            efficiency = excluded.efficiency,
            restingHr = excluded.restingHr,
            avgHrv = excluded.avgHrv,
            stagesJSON = excluded.stagesJSON
    """)
    suspend fun upsertSleepSession(
        deviceId: String, startTs: Long, endTs: Long, efficiency: Double?,
        restingHr: Int?, avgHrv: Double?, stagesJSON: String?
    )

    @Query("""
        INSERT INTO dailyMetric (deviceId, day, totalSleepMin, efficiency, deepMin, remMin, lightMin,
            disturbances, restingHr, avgHrv, recovery, strain, exerciseCount, spo2Pct, skinTempDevC, respRateBpm)
        VALUES (:deviceId, :day, :totalSleepMin, :efficiency, :deepMin, :remMin, :lightMin,
            :disturbances, :restingHr, :avgHrv, :recovery, :strain, :exerciseCount, :spo2Pct, :skinTempDevC, :respRateBpm)
        ON CONFLICT(deviceId, day) DO UPDATE SET
            totalSleepMin = excluded.totalSleepMin,
            efficiency = excluded.efficiency,
            deepMin = excluded.deepMin,
            remMin = excluded.remMin,
            lightMin = excluded.lightMin,
            disturbances = excluded.disturbances,
            restingHr = excluded.restingHr,
            avgHrv = excluded.avgHrv,
            recovery = excluded.recovery,
            strain = excluded.strain,
            exerciseCount = excluded.exerciseCount,
            spo2Pct = excluded.spo2Pct,
            skinTempDevC = excluded.skinTempDevC,
            respRateBpm = excluded.respRateBpm
    """)
    suspend fun upsertDailyMetric(
        deviceId: String, day: String, totalSleepMin: Double?, efficiency: Double?,
        deepMin: Double?, remMin: Double?, lightMin: Double?, disturbances: Int?,
        restingHr: Int?, avgHrv: Double?, recovery: Double?, strain: Double?,
        exerciseCount: Int?, spo2Pct: Double?, skinTempDevC: Double?, respRateBpm: Double?
    )

    // Standard reads
    @Query("SELECT * FROM hrSample WHERE deviceId = :deviceId AND ts >= :from AND ts <= :to ORDER BY ts ASC LIMIT :limit")
    suspend fun getHRSamples(deviceId: String, from: Long, to: Long, limit: Int): List<HrSampleEntity>

    @Query("SELECT * FROM rrInterval WHERE deviceId = :deviceId AND ts >= :from AND ts <= :to ORDER BY ts ASC, rrMs ASC LIMIT :limit")
    suspend fun getRRIntervals(deviceId: String, from: Long, to: Long, limit: Int): List<RrIntervalEntity>

    @Query("SELECT * FROM event WHERE deviceId = :deviceId AND ts >= :from AND ts <= :to ORDER BY ts ASC, kind ASC LIMIT :limit")
    suspend fun getEvents(deviceId: String, from: Long, to: Long, limit: Int): List<EventEntity>

    @Query("SELECT * FROM battery WHERE deviceId = :deviceId AND ts >= :from AND ts <= :to ORDER BY ts ASC LIMIT :limit")
    suspend fun getBatterySamples(deviceId: String, from: Long, to: Long, limit: Int): List<BatteryEntity>

    @Query("SELECT * FROM spo2Sample WHERE deviceId = :deviceId AND ts >= :from AND ts <= :to ORDER BY ts ASC LIMIT :limit")
    suspend fun getSpo2Samples(deviceId: String, from: Long, to: Long, limit: Int): List<Spo2SampleEntity>

    @Query("SELECT * FROM skinTempSample WHERE deviceId = :deviceId AND ts >= :from AND ts <= :to ORDER BY ts ASC LIMIT :limit")
    suspend fun getSkinTempSamples(deviceId: String, from: Long, to: Long, limit: Int): List<SkinTempSampleEntity>

    @Query("SELECT * FROM respSample WHERE deviceId = :deviceId AND ts >= :from AND ts <= :to ORDER BY ts ASC LIMIT :limit")
    suspend fun getRespSamples(deviceId: String, from: Long, to: Long, limit: Int): List<RespSampleEntity>

    @Query("SELECT * FROM gravitySample WHERE deviceId = :deviceId AND ts >= :from AND ts <= :to ORDER BY ts ASC LIMIT :limit")
    suspend fun getGravitySamples(deviceId: String, from: Long, to: Long, limit: Int): List<GravitySampleEntity>

    @Query("SELECT MAX(ts) FROM hrSample WHERE deviceId = :deviceId")
    suspend fun getLatestHRSampleTs(deviceId: String): Long?

    @Query("SELECT COUNT(*) FROM hrSample")
    suspend fun getHrCount(): Int

    @Query("SELECT COUNT(*) FROM rrInterval")
    suspend fun getRrCount(): Int

    @Query("SELECT COUNT(*) FROM event")
    suspend fun getEventCount(): Int

    @Query("SELECT COUNT(*) FROM battery")
    suspend fun getBatteryCount(): Int

    @Query("SELECT COUNT(*) FROM spo2Sample")
    suspend fun getSpo2Count(): Int

    @Query("SELECT COUNT(*) FROM skinTempSample")
    suspend fun getSkinTempCount(): Int

    @Query("SELECT COUNT(*) FROM respSample")
    suspend fun getRespCount(): Int

    @Query("SELECT COUNT(*) FROM gravitySample")
    suspend fun getGravityCount(): Int

    @Query("SELECT COUNT(*) FROM rawBatch")
    suspend fun getRawBatchCount(): Int

    @Query("SELECT COALESCE(SUM(byteSize), 0) FROM rawBatch")
    suspend fun getRawBytesSum(): Int

    @Query("SELECT * FROM sleepSession WHERE deviceId = :deviceId AND startTs >= :from AND startTs <= :to ORDER BY startTs ASC LIMIT :limit")
    suspend fun getSleepSessions(deviceId: String, from: Long, to: Long, limit: Int): List<SleepSessionEntity>

    @Query("SELECT * FROM dailyMetric WHERE deviceId = :deviceId AND day >= :from AND day <= :to ORDER BY day ASC")
    suspend fun getDailyMetrics(deviceId: String, from: String, to: String): List<DailyMetricEntity>
}

data class RawBatchMetaMinimal(
    val batchId: String,
    val deviceId: String,
    val capturedAt: Long,
    val deviceClockRef: Long,
    val wallClockRef: Long,
    val startTs: Long,
    val endTs: Long,
    val frameCount: Int,
    val byteSize: Int
)
