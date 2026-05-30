package com.openwhoop.ble.protocol

data class HRSample(
    val ts: Long, // unix seconds
    val bpm: Int
)

data class RRInterval(
    val ts: Long,
    val rrMs: Int
)

data class WhoopEvent(
    val ts: Long,
    val kind: String,
    val payload: Map<String, ParsedValue>
)

data class BatterySample(
    val ts: Long,
    val soc: Double?,
    val mv: Int?,
    val charging: Boolean? = null
)

data class SpO2Sample(
    val ts: Long,
    val red: Int,
    val ir: Int,
    val unit: String = "raw_adc"
)

data class SkinTempSample(
    val ts: Long,
    val raw: Int,
    val unit: String = "raw_adc"
)

data class RespSample(
    val ts: Long,
    val raw: Int,
    val unit: String = "raw_adc"
)

data class GravitySample(
    val ts: Long,
    val x: Double,
    val y: Double,
    val z: Double,
    val unit: String = "g"
)

data class Streams(
    var hr: MutableList<HRSample> = mutableListOf(),
    var rr: MutableList<RRInterval> = mutableListOf(),
    var spo2: MutableList<SpO2Sample> = mutableListOf(),
    var skinTemp: MutableList<SkinTempSample> = mutableListOf(),
    var resp: MutableList<RespSample> = mutableListOf(),
    var gravity: MutableList<GravitySample> = mutableListOf(),
    var events: MutableList<WhoopEvent> = mutableListOf(),
    var battery: MutableList<BatterySample> = mutableListOf()
) {
    companion object {
        fun empty() = Streams()

        private fun toWall(deviceTs: Long?, deviceClockRef: Long, wallClockRef: Long): Long? {
            if (deviceTs == null) return null
            return wallClockRef + (deviceTs - deviceClockRef)
        }

        fun extractStreams(
            parsed: List<ParsedFrame>,
            deviceClockRef: Long,
            wallClockRef: Long
        ): Streams {
            val out = Streams()
            for (r in parsed) {
                if (!r.ok || r.crcOK == false) continue
                val p = r.parsed
                when (r.typeName) {
                    "REALTIME_DATA" -> {
                        val ts = toWall(p["timestamp"]?.intValue?.toLong(), deviceClockRef, wallClockRef)
                        if (ts != null) {
                            val bpm = p["heart_rate"]?.intValue
                            if (bpm != null) {
                                out.hr.add(HRSample(ts, bpm))
                            }
                            val rrs = p["rr_intervals"]?.intArrayValue
                            if (rrs != null) {
                                for (rr in rrs) {
                                    out.rr.add(RRInterval(ts, rr))
                                }
                            }
                        }
                    }
                    "EVENT" -> {
                        val ts = p["event_timestamp"]?.intValue?.toLong() ?: continue
                        val kind = p["event"]?.stringValue ?: ""
                        if (kind.startsWith("BATTERY_LEVEL")) {
                            appendBattery(out, ts, p)
                        }
                        val payload = p.toMutableMap()
                        payload.remove("event")
                        payload.remove("event_timestamp")
                        out.events.add(WhoopEvent(ts, kind, payload))
                    }
                    "COMMAND_RESPONSE" -> {
                        appendBattery(out, wallClockRef, p)
                    }
                }
            }
            return out
        }

        private fun appendBattery(out: Streams, ts: Long, p: Map<String, ParsedValue>) {
            val soc = p["battery_pct"]?.doubleValue
            val mv = p["battery_mV"]?.intValue
            if (soc != null || mv != null) {
                val charging = p["battery_charging"]?.intValue?.let { it != 0 }
                out.battery.add(BatterySample(ts, soc, mv, charging))
            }
        }
    }
}
