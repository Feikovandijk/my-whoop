package com.openwhoop.ble.protocol

object StandardHeartRate {
    data class Result(val hr: Int, val rr: List<Int>)

    fun parse(data: ByteArray): Result? {
        if (data.isEmpty()) return null
        val flags = data[0].toInt() and 0xFF
        var idx = 1
        val hr: Int
        if ((flags and 0x01) != 0) { // 16-bit HR
            if (idx + 1 >= data.size) return null
            hr = (data[idx].toInt() and 0xFF) or ((data[idx + 1].toInt() and 0xFF) shl 8)
            idx += 2
        } else { // 8-bit HR
            if (idx >= data.size) return null
            hr = data[idx].toInt() and 0xFF
            idx += 1
        }
        if ((flags and 0x08) != 0) { // skip Energy Expended (bit 3)
            idx += 2
        }
        val rr = mutableListOf<Int>()
        if (((flags shr 4) and 0x01) != 0) { // R-R present (bit 4)
            while (idx + 1 < data.size) {
                val raw = (data[idx].toInt() and 0xFF) or ((data[idx + 1].toInt() and 0xFF) shl 8)
                val ms = Math.round(raw.toDouble() / 1024.0 * 1000.0).toInt()
                rr.add(ms)
                idx += 2
            }
        }
        return Result(hr, rr)
    }
}
