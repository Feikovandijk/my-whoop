package com.openwhoop.ble.protocol

object HistoricalStreams {
    fun extract(
        parsed: List<ParsedFrame>,
        deviceClockRef: Long,
        wallClockRef: Long
    ): Streams {
        fun wall(deviceTs: Long?): Long? {
            if (deviceTs == null) return null
            return wallClockRef + (deviceTs - deviceClockRef)
        }
        val out = Streams()
        for (r in parsed) {
            if (!r.ok || r.crcOK == false) continue
            val p = r.parsed
            when (r.typeName) {
                "HISTORICAL_DATA" -> {
                    val ts = p["unix"]?.intValue?.toLong() ?: continue
                    val bpm = p["heart_rate"]?.intValue
                    if (bpm != null && bpm != 0) {
                        out.hr.add(HRSample(ts, bpm))
                    }
                    val rrs = p["rr_intervals"]?.intArrayValue
                    if (rrs != null) {
                        for (rr in rrs) {
                            out.rr.add(RRInterval(ts, rr))
                        }
                    }
                    val red = p["spo2_red"]?.intValue
                    if (red != null) {
                        out.spo2.add(SpO2Sample(ts, red, p["spo2_ir"]?.intValue ?: 0))
                    }
                    val rawTemp = p["skin_temp_raw"]?.intValue
                    if (rawTemp != null) {
                        out.skinTemp.add(SkinTempSample(ts, rawTemp))
                    }
                    val rawResp = p["resp_rate_raw"]?.intValue
                    if (rawResp != null) {
                        out.resp.add(RespSample(ts, rawResp))
                    }
                    val gx = p["gravity_x"]?.doubleValue
                    if (gx != null) {
                        out.gravity.add(
                            GravitySample(
                                ts = ts,
                                x = gx,
                                y = p["gravity_y"]?.doubleValue ?: 0.0,
                                z = p["gravity_z"]?.doubleValue ?: 0.0
                            )
                        )
                    }
                }
                "REALTIME_RAW_DATA" -> {
                    val ts = wall(p["timestamp"]?.intValue?.toLong())
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
                        val soc = p["battery_pct"]?.doubleValue
                        val mv = p["battery_mV"]?.intValue
                        if (soc != null || mv != null) {
                            val charging = p["battery_charging"]?.intValue?.let { it != 0 }
                            out.battery.add(BatterySample(ts, soc, mv, charging))
                        }
                    }
                    val payload = p.toMutableMap()
                    payload.remove("event")
                    payload.remove("event_timestamp")
                    out.events.add(WhoopEvent(ts, kind, payload))
                }
                "COMMAND_RESPONSE" -> {
                    val soc = p["battery_pct"]?.doubleValue
                    val mv = p["battery_mV"]?.intValue
                    if (soc != null || mv != null) {
                        val charging = p["battery_charging"]?.intValue?.let { it != 0 }
                        out.battery.add(BatterySample(wallClockRef, soc, mv, charging))
                    }
                }
            }
        }
        return out
    }
}
