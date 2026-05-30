package com.openwhoop.ble

import android.content.Context
import com.openwhoop.ble.protocol.*
import com.openwhoop.database.*

class Backfiller(
    private val context: Context,
    private val store: WhoopStore,
    private val deviceId: String,
    private val ackTrim: (trim: Long, endData: ByteArray) -> Unit,
    private val enableRawCapture: Boolean = false
) {
    var clockRef: ClockRef? = null
    var isBackfilling = false
        private set

    private val chunk = mutableListOf<ByteArray>()
    private var chunkOpen = false

    fun begin() {
        isBackfilling = true
        chunk.clear()
        chunkOpen = true
    }

    suspend fun ingest(frame: ByteArray) {
        val parsed = Interpreter.parseFrame(context, frame)
        when (val meta = HistoricalMetaClassifier.classify(parsed)) {
            is HistoricalMeta.Start -> {
                isBackfilling = true
                chunk.clear()
                chunkOpen = true
            }
            is HistoricalMeta.End -> {
                finishChunk(meta.unix, meta.trim, frame)
            }
            is HistoricalMeta.Complete -> {
                isBackfilling = false
                chunk.clear()
                chunkOpen = false
            }
            is HistoricalMeta.Other -> {
                if (chunkOpen) {
                    chunk.add(frame)
                }
            }
        }
    }

    companion object {
        fun endData(frame: ByteArray): ByteArray? {
            if (frame.size < 25) return null
            val out = ByteArray(8)
            System.arraycopy(frame, 17, out, 0, 8)
            return out
        }
    }

    private suspend fun finishChunk(unix: Long, trim: Long, endFrame: ByteArray) {
        val endData = endData(endFrame) ?: return
        val frames = synchronized(chunk) {
            val copy = ArrayList(chunk)
            chunk.clear()
            copy
        }

        if (frames.isNotEmpty()) {
            val ref = clockRef ?: run {
                val now = System.currentTimeMillis() / 1000
                ClockRef(now, now)
            }
            val parsed = frames.map { Interpreter.parseFrame(context, it) }
            val decoded = HistoricalStreams.extract(parsed, ref.device, ref.wall)
            try {
                store.insert(decoded, deviceId)
            } catch (e: Exception) {
                return
            }

            if (enableRawCapture) {
                val meta = RawBatchMeta(
                    batchId = "hist-$deviceId-$trim",
                    deviceId = deviceId,
                    clockRef = ref,
                    capturedAt = System.currentTimeMillis() / 1000,
                    startTs = ref.wall,
                    endTs = ref.wall,
                    frameCount = frames.size,
                    byteSize = frames.sumOf { it.size }
                )
                try {
                    store.enqueueRawBatch(meta, frames)
                } catch (e: Exception) {
                    return
                }
            }
        }

        try {
            store.setCursor("strap_trim", trim)
        } catch (e: Exception) {
            return
        }

        ackTrim(trim, endData)
    }

    fun timeoutFired() {
        isBackfilling = false
        synchronized(chunk) {
            chunk.clear()
        }
        chunkOpen = false
    }
}
