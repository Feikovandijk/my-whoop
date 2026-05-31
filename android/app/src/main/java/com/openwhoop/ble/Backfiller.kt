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

    // Ring-buffer read pointer (U) captured from GET_DATA_RANGE at session start.
    // Every chunk is ACKed with this fixed value so U never advances — the strap keeps
    // all data re-servable. Without this, each ACK moves U forward and the strap can
    // eventually overwrite old records as its write pointer catches up.
    var sessionStartTrim: Long? = null

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
        if (endFrame.size < 25) return   // sanity-check (endData would have returned null)
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

        // Build ACK payload: 0x01 + trim index (u32 LE) + padding (u32 LE).
        // Use sessionStartTrim (captured before the session began) so U never advances
        // past where we started. The strap sees a valid success ACK and sends the next
        // chunk, but never reclaims the flash slots we've already read.
        // Fall back to the chunk's own trim only if we somehow have no session anchor.
        val ackTrimIndex = sessionStartTrim ?: trim
        val safeEndData = ByteArray(8).also { buf ->
            buf[0] = (ackTrimIndex and 0xFF).toByte()
            buf[1] = ((ackTrimIndex shr 8) and 0xFF).toByte()
            buf[2] = ((ackTrimIndex shr 16) and 0xFF).toByte()
            buf[3] = ((ackTrimIndex shr 24) and 0xFF).toByte()
            // bytes [4..7] = 0x00 (second u32 = 0, matching the protocol)
        }

        ackTrim(ackTrimIndex, safeEndData)
    }

    fun timeoutFired() {
        isBackfilling = false
        synchronized(chunk) {
            chunk.clear()
        }
        chunkOpen = false
    }
}
