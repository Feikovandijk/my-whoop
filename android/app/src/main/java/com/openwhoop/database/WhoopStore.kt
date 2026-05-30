package com.openwhoop.database

import androidx.room.withTransaction
import com.google.gson.GsonBuilder
import com.openwhoop.ble.protocol.*

data class InsertResult(
    val hr: Int, val rr: Int, val events: Int, val battery: Int,
    val spo2: Int, val skinTemp: Int, val resp: Int, val gravity: Int
)

class WhoopStore(private val db: WhoopDatabase) {
    val dao = db.whoopDao()
    private val gson = GsonBuilder()
        .registerTypeAdapter(ParsedValue::class.java, ParsedValueAdapter())
        .create()

    suspend fun upsertDevice(id: String, mac: String?, name: String?) {
        val now = System.currentTimeMillis() / 1000
        dao.upsertDevice(id, mac, name, now)
    }

    suspend fun insert(streams: Streams, deviceId: String, markSynced: Boolean = false): InsertResult {
        val synced = if (markSynced) 1 else 0
        return db.withTransaction {
            val hrEntities = streams.hr.map { HrSampleEntity(deviceId, it.ts, it.bpm, synced) }
            val rrEntities = streams.rr.map { RrIntervalEntity(deviceId, it.ts, it.rrMs, synced) }
            val eventEntities = streams.events.map {
                val json = gson.toJson(it.payload)
                EventEntity(deviceId, it.ts, it.kind, json, synced)
            }
            val batteryEntities = streams.battery.map {
                BatteryEntity(deviceId, it.ts, it.soc, it.mv, it.charging, synced)
            }
            val spo2Entities = streams.spo2.map { Spo2SampleEntity(deviceId, it.ts, it.red, it.ir, synced) }
            val skinTempEntities = streams.skinTemp.map { SkinTempSampleEntity(deviceId, it.ts, it.raw, synced) }
            val respEntities = streams.resp.map { RespSampleEntity(deviceId, it.ts, it.raw, synced) }
            val gravityEntities = streams.gravity.map { GravitySampleEntity(deviceId, it.ts, it.x, it.y, it.z, synced) }

            val hrInserted = dao.insertHrSamples(hrEntities).filter { it > 0 }.size
            val rrInserted = dao.insertRrIntervals(rrEntities).filter { it > 0 }.size
            val eventInserted = dao.insertEvents(eventEntities).filter { it > 0 }.size
            val batteryInserted = dao.insertBattery(batteryEntities).filter { it > 0 }.size
            val spo2Inserted = dao.insertSpo2(spo2Entities).filter { it > 0 }.size
            val skinTempInserted = dao.insertSkinTemp(skinTempEntities).filter { it > 0 }.size
            val respInserted = dao.insertResp(respEntities).filter { it > 0 }.size
            val gravityInserted = dao.insertGravity(gravityEntities).filter { it > 0 }.size

            InsertResult(
                hr = hrInserted, rr = rrInserted, events = eventInserted, battery = batteryInserted,
                spo2 = spo2Inserted, skinTemp = skinTempInserted, resp = respInserted, gravity = gravityInserted
            )
        }
    }

    // Unsynced read helpers for Uploader
    suspend fun unsyncedHR(deviceId: String, limit: Int): List<HRSample> =
        dao.getUnsyncedHR(deviceId, limit).map { HRSample(it.ts, it.bpm) }

    suspend fun unsyncedRR(deviceId: String, limit: Int): List<RRInterval> =
        dao.getUnsyncedRR(deviceId, limit).map { RRInterval(it.ts, it.rrMs) }

    suspend fun unsyncedEvents(deviceId: String, limit: Int): List<WhoopEvent> =
        dao.getUnsyncedEvents(deviceId, limit).map {
            val payload = try {
                gson.fromJson<Map<String, ParsedValue>>(it.payloadJSON, object : com.google.gson.reflect.TypeToken<Map<String, ParsedValue>>() {}.type)
            } catch (e: Exception) {
                emptyMap()
            }
            WhoopEvent(it.ts, it.kind, payload)
        }

    suspend fun unsyncedBattery(deviceId: String, limit: Int): List<BatterySample> =
        dao.getUnsyncedBattery(deviceId, limit).map { BatterySample(it.ts, it.soc, it.mv, it.charging) }

    suspend fun unsyncedSpo2(deviceId: String, limit: Int): List<SpO2Sample> =
        dao.getUnsyncedSpo2(deviceId, limit).map { SpO2Sample(it.ts, it.red, it.ir) }

    suspend fun unsyncedSkinTemp(deviceId: String, limit: Int): List<SkinTempSample> =
        dao.getUnsyncedSkinTemp(deviceId, limit).map { SkinTempSample(it.ts, it.raw) }

    suspend fun unsyncedResp(deviceId: String, limit: Int): List<RespSample> =
        dao.getUnsyncedResp(deviceId, limit).map { RespSample(it.ts, it.raw) }

    suspend fun unsyncedGravity(deviceId: String, limit: Int): List<GravitySample> =
        dao.getUnsyncedGravity(deviceId, limit).map { GravitySample(it.ts, it.x, it.y, it.z) }

    // Mark synced
    suspend fun markHRSynced(deviceId: String, rows: List<HRSample>) {
        db.withTransaction {
            for (r in rows) {
                dao.markHRSynced(deviceId, r.ts)
            }
        }
    }

    suspend fun markRRSynced(deviceId: String, rows: List<RRInterval>) {
        db.withTransaction {
            for (r in rows) {
                dao.markRRSynced(deviceId, r.ts, r.rrMs)
            }
        }
    }

    suspend fun markEventsSynced(deviceId: String, rows: List<WhoopEvent>) {
        db.withTransaction {
            for (r in rows) {
                dao.markEventSynced(deviceId, r.ts, r.kind)
            }
        }
    }

    suspend fun markBatterySynced(deviceId: String, rows: List<BatterySample>) {
        db.withTransaction {
            for (r in rows) {
                dao.markBatterySynced(deviceId, r.ts)
            }
        }
    }

    suspend fun markSpo2Synced(deviceId: String, rows: List<SpO2Sample>) {
        db.withTransaction {
            for (r in rows) {
                dao.markSpo2Synced(deviceId, r.ts)
            }
        }
    }

    suspend fun markSkinTempSynced(deviceId: String, rows: List<SkinTempSample>) {
        db.withTransaction {
            for (r in rows) {
                dao.markSkinTempSynced(deviceId, r.ts)
            }
        }
    }

    suspend fun markRespSynced(deviceId: String, rows: List<RespSample>) {
        db.withTransaction {
            for (r in rows) {
                dao.markRespSynced(deviceId, r.ts)
            }
        }
    }

    suspend fun markGravitySynced(deviceId: String, rows: List<GravitySample>) {
        db.withTransaction {
            for (r in rows) {
                dao.markGravitySynced(deviceId, r.ts)
            }
        }
    }

    // Raw batch outbox
    suspend fun enqueueRawBatch(meta: RawBatchMeta, frames: List<ByteArray>) {
        val packed = packFrames(frames)
        val blob = CompressionUtil.compress(packed)
        dao.insertRawBatch(
            RawBatchEntity(
                batchId = meta.batchId,
                deviceId = meta.deviceId,
                capturedAt = meta.capturedAt,
                deviceClockRef = meta.clockRef.device,
                wallClockRef = meta.clockRef.wall,
                startTs = meta.startTs,
                endTs = meta.endTs,
                frameCount = meta.frameCount,
                byteSize = meta.byteSize,
                framesBlob = blob
            )
        )
    }

    suspend fun rawFrames(batchId: String): List<ByteArray> {
        val blob = dao.getRawFramesBlob(batchId) ?: return emptyList()
        val decompressed = CompressionUtil.decompress(blob)
        return unpackFrames(decompressed)
    }

    suspend fun pendingRawBatches(limit: Int): List<RawBatchMeta> {
        return dao.getPendingRawBatches(limit).map {
            RawBatchMeta(
                batchId = it.batchId,
                deviceId = it.deviceId,
                clockRef = ClockRef(it.deviceClockRef, it.wallClockRef),
                capturedAt = it.capturedAt,
                startTs = it.startTs,
                endTs = it.endTs,
                frameCount = it.frameCount,
                byteSize = it.byteSize
            )
        }
    }

    suspend fun markRawBatchSynced(batchId: String, at: Long) {
        dao.markRawBatchSynced(batchId, at)
    }

    suspend fun pruneRaw(now: Long, keepWindowSeconds: Long, maxUnsyncedBytes: Long): Int {
        val cutoff = now - keepWindowSeconds
        return dao.pruneRawBatches(cutoff)
    }

    // Cursors
    suspend fun setCursor(name: String, value: Long) {
        dao.setCursor(name, value)
    }

    suspend fun cursor(name: String): Long? {
        return dao.getCursor(name)
    }

    suspend fun setReadHighwater(stream: String, ts: Long) {
        setCursor("read:$stream", ts)
    }

    suspend fun readHighwater(stream: String): Long? {
        return cursor("read:$stream")
    }

    // Derived metrics
    suspend fun upsertSleepSessions(sessions: List<CachedSleepSession>, deviceId: String): Int {
        db.withTransaction {
            for (s in sessions) {
                dao.upsertSleepSession(deviceId, s.startTs, s.endTs, s.efficiency, s.restingHr, s.avgHrv, s.stagesJSON)
            }
        }
        return sessions.size
    }

    suspend fun upsertDailyMetrics(days: List<DailyMetric>, deviceId: String): Int {
        db.withTransaction {
            for (d in days) {
                dao.upsertDailyMetric(
                    deviceId, d.day, d.totalSleepMin, d.efficiency, d.deepMin, d.remMin, d.lightMin,
                    d.disturbances, d.restingHr, d.avgHrv, d.recovery, d.strain, d.exerciseCount,
                    d.spo2Pct, d.skinTempDevC, d.respRateBpm
                )
            }
        }
        return days.size
    }

    // Standard reads
    suspend fun hrSamples(deviceId: String, from: Long, to: Long, limit: Int): List<HRSample> =
        dao.getHRSamples(deviceId, from, to, limit).map { HRSample(it.ts, it.bpm) }

    suspend fun rrIntervals(deviceId: String, from: Long, to: Long, limit: Int): List<RRInterval> =
        dao.getRRIntervals(deviceId, from, to, limit).map { RRInterval(it.ts, it.rrMs) }

    suspend fun events(deviceId: String, from: Long, to: Long, limit: Int): List<WhoopEvent> =
        dao.getEvents(deviceId, from, to, limit).map {
            val payload = try {
                gson.fromJson<Map<String, ParsedValue>>(it.payloadJSON, object : com.google.gson.reflect.TypeToken<Map<String, ParsedValue>>() {}.type)
            } catch (e: Exception) {
                emptyMap()
            }
            WhoopEvent(it.ts, it.kind, payload)
        }

    suspend fun batterySamples(deviceId: String, from: Long, to: Long, limit: Int): List<BatterySample> =
        dao.getBatterySamples(deviceId, from, to, limit).map { BatterySample(it.ts, it.soc, it.mv) }

    suspend fun spo2Samples(deviceId: String, from: Long, to: Long, limit: Int): List<SpO2Sample> =
        dao.getSpo2Samples(deviceId, from, to, limit).map { SpO2Sample(it.ts, it.red, it.ir) }

    suspend fun skinTempSamples(deviceId: String, from: Long, to: Long, limit: Int): List<SkinTempSample> =
        dao.getSkinTempSamples(deviceId, from, to, limit).map { SkinTempSample(it.ts, it.raw) }

    suspend fun respSamples(deviceId: String, from: Long, to: Long, limit: Int): List<RespSample> =
        dao.getRespSamples(deviceId, from, to, limit).map { RespSample(it.ts, it.raw) }

    suspend fun gravitySamples(deviceId: String, from: Long, to: Long, limit: Int): List<GravitySample> =
        dao.getGravitySamples(deviceId, from, to, limit).map { GravitySample(it.ts, it.x, it.y, it.z) }

    suspend fun latestHRSampleTs(deviceId: String): Long? =
        dao.getLatestHRSampleTs(deviceId)

    suspend fun storageStats(): StorageStatsResult {
        val hr = dao.getHrCount()
        val rr = dao.getRrCount()
        val ev = dao.getEventCount()
        val bat = dao.getBatteryCount()
        val spo2 = dao.getSpo2Count()
        val skin = dao.getSkinTempCount()
        val resp = dao.getRespCount()
        val grav = dao.getGravityCount()
        val batches = dao.getRawBatchCount()
        val bytes = dao.getRawBytesSum()
        return StorageStatsResult(
            decodedRows = (hr + rr + ev + bat + spo2 + skin + resp + grav).toLong(),
            rawBatches = batches,
            rawBytes = bytes.toLong()
        )
    }

    suspend fun sleepSessions(deviceId: String, from: Long, to: Long, limit: Int): List<CachedSleepSession> =
        dao.getSleepSessions(deviceId, from, to, limit).map {
            CachedSleepSession(it.startTs, it.endTs, it.efficiency, it.restingHr, it.avgHrv, it.stagesJSON)
        }

    suspend fun dailyMetrics(deviceId: String, from: String, to: String): List<DailyMetric> =
        dao.getDailyMetrics(deviceId, from, to).map {
            DailyMetric(
                it.day, it.totalSleepMin, it.efficiency, it.deepMin, it.remMin, it.lightMin,
                it.disturbances, it.restingHr, it.avgHrv, it.recovery, it.strain, it.exerciseCount,
                it.spo2Pct, it.skinTempDevC, it.respRateBpm
            )
        }

    companion object {
        fun packFrames(frames: List<ByteArray>): ByteArray {
            val totalSize = 4 + frames.sumOf { 4 + it.size }
            val buf = java.nio.ByteBuffer.allocate(totalSize).order(java.nio.ByteOrder.LITTLE_ENDIAN)
            buf.putInt(frames.size)
            for (f in frames) {
                buf.putInt(f.size)
                buf.put(f)
            }
            return buf.array()
        }

        fun unpackFrames(data: ByteArray): List<ByteArray> {
            val buf = java.nio.ByteBuffer.wrap(data).order(java.nio.ByteOrder.LITTLE_ENDIAN)
            if (buf.remaining() < 4) return emptyList()
            val count = buf.getInt()
            val out = mutableListOf<ByteArray>()
            for (i in 0 until count) {
                if (buf.remaining() < 4) break
                val len = buf.getInt()
                if (buf.remaining() < len) break
                val f = ByteArray(len)
                buf.get(f)
                out.add(f)
            }
            return out
        }
    }
}
