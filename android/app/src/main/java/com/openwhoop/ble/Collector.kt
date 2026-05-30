package com.openwhoop.ble

import android.content.Context
import com.openwhoop.ble.protocol.*
import com.openwhoop.database.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.UUID

class CollectorPolicy(
    val maxFrames: Int,
    val maxInterval: Long, // in seconds
    val maxPreClockFrames: Int = 4096
) {
    companion object {
        val DEFAULT = CollectorPolicy(maxFrames = 64, maxInterval = 30)
    }
}

class RawCaptureWindow {
    private var start: Double = 0.0
    private var duration: Double = 0.0

    fun open(at: Double, duration: Double) {
        this.start = at
        this.duration = duration
    }

    fun close() {
        start = 0.0
        duration = 0.0
    }

    fun isActive(at: Double): Boolean {
        if (start == 0.0) return false
        return at < start + duration
    }

    companion object {
        fun clamp(seconds: Double): Double {
            return seconds.coerceIn(5.0, 300.0)
        }
    }
}

class Collector(
    private val context: Context,
    private val store: WhoopStore,
    private val deviceId: String,
    private val scope: CoroutineScope,
    private val policy: CollectorPolicy = CollectorPolicy.DEFAULT,
    private val enableRawCapture: Boolean = false,
    private val nowProvider: () -> Long = { System.currentTimeMillis() / 1000 },
    private val monotonicProvider: () -> Double = { (System.nanoTime() / 1_000_000_000.0) }
) {
    var clockRef: ClockRef? = null
    private var rawCapture = RawCaptureWindow()
    private val buffer = mutableListOf<ByteArray>()
    private var batchStartedAt: Double = monotonicProvider()

    val bufferedCount: Int
        get() = synchronized(buffer) { buffer.size }

    fun ingest(frame: ByteArray) {
        synchronized(buffer) {
            buffer.add(frame)
            if (clockRef == null && buffer.size > policy.maxPreClockFrames) {
                buffer.removeAt(0)
            }
        }

        val currentMonotonic = monotonicProvider()
        val size = synchronized(buffer) { buffer.size }
        if (size >= policy.maxFrames || (currentMonotonic - batchStartedAt) >= policy.maxInterval) {
            scope.launch(Dispatchers.IO) {
                flush()
            }
        }
    }

    suspend fun flush() {
        val ref = clockRef ?: run {
            val now = System.currentTimeMillis() / 1000
            ClockRef(now, now)
        }
        val frames = synchronized(buffer) {
            if (buffer.isEmpty()) return
            val copy = ArrayList(buffer)
            buffer.clear()
            copy
        }

        val parsed = frames.map { Interpreter.parseFrame(context, it) }
        val streams = Streams.extractStreams(parsed, ref.device, ref.wall)
        try {
            store.insert(streams, deviceId)
        } catch (e: Exception) {
            synchronized(buffer) {
                buffer.addAll(0, frames)
            }
            return
        }
        batchStartedAt = monotonicProvider()

        val monotonicNow = monotonicProvider()
        if (enableRawCapture || rawCapture.isActive(monotonicNow)) {
            val wall = nowProvider()
            val tsValues = streams.hr.map { it.ts } + streams.rr.map { it.ts } +
                    streams.events.map { it.ts } + streams.battery.map { it.ts }
            val meta = RawBatchMeta(
                batchId = UUID.randomUUID().toString(),
                deviceId = deviceId,
                clockRef = ref,
                capturedAt = wall,
                startTs = tsValues.minOrNull() ?: wall,
                endTs = tsValues.maxOrNull() ?: wall,
                frameCount = frames.size,
                byteSize = frames.sumOf { it.size }
            )
            try {
                store.enqueueRawBatch(meta, frames)
            } catch (e: Exception) {
                // non-fatal raw batch failure
            }
        }
    }

    fun beginRawCapture(seconds: Double) {
        rawCapture.open(monotonicProvider(), seconds)
    }

    suspend fun endRawCapture() {
        flush()
        rawCapture.close()
    }

    suspend fun prune(): Int {
        return try {
            store.pruneRaw(nowProvider(), 24 * 3600, 50 * 1024 * 1024)
        } catch (e: Exception) {
            0
        }
    }

    suspend fun latestHRSampleTs(): Long? {
        return try {
            store.latestHRSampleTs(deviceId)
        } catch (e: Exception) {
            null
        }
    }
}
