package com.openwhoop.ble.protocol

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

object PostHooks {
    val postHooks = mutableMapOf<String, (Interpreter.FieldBuilder, ByteArray, Int?, Schema) -> Unit>()

    private val utcRangeFormatter = SimpleDateFormat("yyyy-MM-dd HH:mm 'UTC'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    private fun u8(f: ByteArray, off: Int): Int? =
        if (off + 1 <= f.size) f[off].toInt() and 0xFF else null

    private fun u16(f: ByteArray, off: Int): Int? =
        if (off + 2 <= f.size) (f[off].toInt() and 0xFF) or ((f[off + 1].toInt() and 0xFF) shl 8) else null

    private fun u32(f: ByteArray, off: Int): Long? {
        if (off + 4 > f.size) return null
        return (f[off].toLong() and 0xFFL) or
                ((f[off + 1].toLong() and 0xFFL) shl 8) or
                ((f[off + 2].toLong() and 0xFFL) shl 16) or
                ((f[off + 3].toLong() and 0xFFL) shl 24)
    }

    private fun s24(f: ByteArray, off: Int): Int? {
        if (off + 3 > f.size) return null
        val v = (f[off].toInt() and 0xFF) or ((f[off + 1].toInt() and 0xFF) shl 8) or ((f[off + 2].toInt() and 0xFF) shl 16)
        return if ((v and 0x800000) != 0) v - 0x1000000 else v
    }

    private fun f32(f: ByteArray, off: Int): Double? {
        val bits = u32(f, off) ?: return null
        return java.lang.Float.intBitsToFloat(bits.toInt()).toDouble()
    }

    private fun readHistInt(f: ByteArray, off: Int, dtype: String): Int? {
        return when (dtype) {
            "u8" -> u8(f, off)
            "u16" -> u16(f, off)
            "u32" -> u32(f, off)?.toInt()
            else -> null
        }
    }

    private fun i16Block(frame: ByteArray, off: Int, count: Int): IntArray {
        var n = count
        if (off + n * 2 > frame.size) {
            n = Math.max(0, (frame.size - off) / 2)
        }
        if (n <= 0) return intArrayOf()
        val out = IntArray(n)
        for (i in 0 until n) {
            val p = off + i * 2
            val raw = (frame[p].toInt() and 0xFF) or ((frame[p + 1].toInt() and 0xFF) shl 8)
            out[i] = raw.toShort().toInt()
        }
        return out
    }

    private fun twoProduct(a: Double, b: Double): DoubleArray {
        val p = a * b
        val split = 134217729.0
        val ca = split * a; val ah = ca - (ca - a); val al = a - ah
        val cb = split * b; val bh = cb - (cb - b); val bl = b - bh
        val err = ((ah * bh - p) + ah * bl + al * bh) + al * bl
        return doubleArrayOf(p, err)
    }

    private fun round1(x: Double): Double {
        val y = x * 10.0
        val fl = Math.floor(y)
        val frac = y - fl
        if (Math.abs(frac - 0.5) < 1e-14) {
            val prodErr = twoProduct(x, 10.0)
            val err = prodErr[1]
            return if (err > 0) {
                Math.ceil(y) / 10.0
            } else if (err < 0) {
                fl / 10.0
            } else {
                val z = fl.toInt()
                val r = if (z % 2 == 0) fl else fl + 1.0
                r / 10.0
            }
        }
        return BigDecimal(y).setScale(0, RoundingMode.HALF_EVEN).toDouble() / 10.0
    }

    private fun formatMean(x: Double): String {
        return String.format(Locale.US, "%.1f", x)
    }

    init {
        postHooks["realtime_data"] = { fb, frame, _, _ ->
            val rrn = u8(frame, 13) ?: 0
            val rrs = mutableListOf<Int>()
            for (i in 0 until rrn) {
                val off = 14 + i * 2
                val v = u16(frame, off)
                if (v != null) {
                    fb.add(off, 2, "rr[$i]", "rr", ParsedValue.IntVal(v), "ms")
                    rrs.add(v)
                }
            }
            fb.parsed["rr_intervals"] = ParsedValue.IntArrayVal(rrs)
        }

        postHooks["event"] = { fb, frame, length, schema ->
            val evVal = if (frame.size > 6) frame[6].toInt() and 0xFF else null
            val evName = evVal?.let { schema.enums["EventNumber"]?.get(it.toString()) }
            if (length != null) {
                if (evName == "BATTERY_LEVEL") {
                    fb.region(7, length, "BATTERY_LEVEL payload", "battery", "soc@17(/10) mv@21 charge@26")
                    val raw = u16(frame, 17)
                    if (raw != null && raw <= 1100) {
                        fb.parsed["battery_pct"] = ParsedValue.DoubleVal(raw.toDouble() / 10.0)
                    }
                    val mv = u16(frame, 21)
                    if (mv != null && mv in 3000..4300) {
                        fb.parsed["battery_mV"] = ParsedValue.IntVal(mv)
                    }
                    val ch = u8(frame, 26)
                    if (ch != null && ch <= 1) {
                        fb.parsed["battery_charging"] = ParsedValue.IntVal(ch and 1)
                    }
                } else if (evName == "EXTENDED_BATTERY_INFORMATION") {
                    val payEnd = Math.min(length, frame.size)
                    if (7 < payEnd) {
                        fb.region(7, length, "EXTENDED_BATTERY_INFORMATION payload", "battery", "mV (heuristic scan)")
                        val paySize = payEnd - 7
                        if (paySize >= 2) {
                            for (o in 0 until (paySize - 1)) {
                                val pIdx = 7 + o
                                val v = (frame[pIdx].toInt() and 0xFF) or ((frame[pIdx + 1].toInt() and 0xFF) shl 8)
                                if (v in 3000..4300) {
                                    fb.parsed["battery_mV?"] = ParsedValue.IntVal(v)
                                    break
                                }
                            }
                        }
                    }
                }
            }
        }

        postHooks["command_response"] = { fb, frame, length, schema ->
            if (length != null) {
                val payEnd = Math.min(length, frame.size)
                if (7 <= payEnd) {
                    fb.region(7, length, "response payload", "cmd")
                    val cmd = if (frame.size > 6) frame[6].toInt() and 0xFF else null
                    val name = cmd?.let { schema.enums["CommandNumber"]?.get(it.toString()) }
                    val paySize = payEnd - 7
                    when (name) {
                        "GET_BATTERY_LEVEL" -> {
                            if (paySize >= 4) {
                                val v = (frame[7 + 2].toInt() and 0xFF) or ((frame[7 + 3].toInt() and 0xFF) shl 8)
                                fb.parsed["battery_pct"] = ParsedValue.DoubleVal(v.toDouble() / 10.0)
                            }
                        }
                        "GET_CLOCK" -> {
                            if (paySize >= 6) {
                                val v = (frame[7 + 2].toLong() and 0xFFL) or
                                        ((frame[7 + 3].toLong() and 0xFFL) shl 8) or
                                        ((frame[7 + 4].toLong() and 0xFFL) shl 16) or
                                        ((frame[7 + 5].toLong() and 0xFFL) shl 24)
                                fb.parsed["clock"] = ParsedValue.IntVal(v)
                            }
                        }
                        "GET_EXTENDED_BATTERY_INFO" -> {
                            if (paySize >= 9) {
                                val v = (frame[7 + 7].toInt() and 0xFF) or ((frame[7 + 8].toInt() and 0xFF) shl 8)
                                fb.parsed["battery_mV"] = ParsedValue.IntVal(v)
                            }
                        }
                        "REPORT_VERSION_INFO" -> {
                            if (paySize >= 31) {
                                val buf = ByteArray(35)
                                if (paySize >= 35) {
                                    System.arraycopy(frame, 7, buf, 0, 35)
                                } else {
                                    System.arraycopy(frame, 7, buf, 0, paySize)
                                }
                                fun le32(at: Int): Long {
                                    return (buf[at].toLong() and 0xFFL) or
                                            ((buf[at + 1].toLong() and 0xFFL) shl 8) or
                                            ((buf[at + 2].toLong() and 0xFFL) shl 16) or
                                            ((buf[at + 3].toLong() and 0xFFL) shl 24)
                                }
                                val h0 = le32(3)
                                val h1 = le32(7)
                                val h2 = le32(11)
                                val h3 = le32(15)
                                val b0 = le32(19)
                                val b1 = le32(23)
                                val b2 = le32(27)
                                val b3 = le32(31)
                                fb.parsed["fw_harvard"] = ParsedValue.StringVal("$h0.$h1.$h2.$h3")
                                fb.parsed["fw_boylston"] = ParsedValue.StringVal("$b0.$b1.$b2.$b3")
                            }
                        }
                        "GET_DATA_RANGE" -> {
                            val uniq = mutableListOf<Long>()
                            var o = 3
                            while (o < paySize - 3) {
                                val v = (frame[7 + o].toLong() and 0xFFL) or
                                        ((frame[7 + o + 1].toLong() and 0xFFL) shl 8) or
                                        ((frame[7 + o + 2].toLong() and 0xFFL) shl 16) or
                                        ((frame[7 + o + 3].toLong() and 0xFFL) shl 24)
                                if (v in 1600000000L..1800000000L && !uniq.contains(v)) {
                                    uniq.add(v)
                                }
                                o += 1
                            }
                            val lo = uniq.minOrNull()
                            val hi = uniq.maxOrNull()
                            if (lo != null && hi != null) {
                                fb.parsed["history_oldest"] = ParsedValue.StringVal(utcRangeFormatter.format(Date(lo * 1000)))
                                fb.parsed["history_newest"] = ParsedValue.StringVal(utcRangeFormatter.format(Date(hi * 1000)))
                            }
                        }
                    }
                }
            }
        }

        postHooks["raw_data"] = { fb, frame, length, schema ->
            if (length != null) {
                val spec = schema.packet(frame[4].toInt() and 0xFF)
                val dataLen = length - 7
                val variant = spec?.variants?.get(dataLen.toString())
                if (variant == null) {
                    fb.region(21, length, "sensor payload (short/alt subtype)", "unknown")
                } else {
                    if (variant.kind == "imu") {
                        val hrOff = variant.hrOff ?: 0
                        val rrCountOff = variant.rrCountOff ?: 0
                        val rrFirstOff = variant.rrFirstOff ?: 0
                        val samples = variant.samples ?: 0
                        val tailFrom = variant.tailFrom ?: 0

                        val hr = u8(frame, hrOff)
                        val rrn = u8(frame, rrCountOff) ?: 0
                        fb.add(hrOff, 1, "heart_rate", "hr", hr?.let { ParsedValue.IntVal(it) }, "bpm")
                        fb.add(rrCountOff, 1, "rr_count", "rr", ParsedValue.IntVal(rrn))
                        
                        val rrVals = mutableListOf<Int>()
                        for (i in 0 until Math.min(rrn, 4)) {
                            val off = rrFirstOff + i * 2
                            val v = u16(frame, off)
                            fb.add(off, 2, "rr[$i]", "rr", v?.let { ParsedValue.IntVal(it) }, "ms")
                            if (v != null) rrVals.add(v)
                        }
                        fb.parsed["heart_rate"] = hr?.let { ParsedValue.IntVal(it) } ?: ParsedValue.NullVal
                        fb.parsed["rr_intervals"] = ParsedValue.IntArrayVal(rrVals)

                        for (axis in variant.axes) {
                            val vals = i16Block(frame, axis.off, samples)
                            val mean: Double? = if (vals.isEmpty()) null
                            else round1(vals.sum().toDouble() / vals.size.toDouble())
                            
                            val text = mean?.let { ParsedValue.StringVal("mean=${formatMean(it)} (${vals.size}xi16)") }
                            fb.add(axis.off, samples * 2, axis.name, axis.cat, text, variant.note)
                            
                            if (mean != null) {
                                if (mean == Math.round(mean).toDouble()) {
                                    fb.parsed["${axis.name}_mean"] = ParsedValue.IntVal(mean.toInt())
                                } else {
                                    fb.parsed["${axis.name}_mean"] = ParsedValue.DoubleVal(mean)
                                }
                            }
                        }
                        fb.region(tailFrom, length, "tail (optical? - not parsed by app)", "unknown")
                    } else if (variant.kind == "optical") {
                        val ppgOff = variant.ppgOff ?: 0
                        val ppgStride = variant.ppgStride ?: 0
                        val ppgSamples = variant.ppgSamples ?: 0
                        val configFrom = variant.configFrom ?: 0
                        
                        fb.region(configFrom, ppgOff, "optical config header (UNKNOWN)", "unknown", variant.note)
                        val vals = mutableListOf<Int>()
                        for (i in 0 until ppgSamples) {
                            val v = s24(frame, ppgOff + i * ppgStride) ?: break
                            vals.add(v)
                        }
                        if (vals.isNotEmpty()) {
                            val mean = round1(vals.sum().toDouble() / vals.size.toDouble())
                            fb.add(
                                ppgOff,
                                vals.size * ppgStride,
                                "ppg_green_ac",
                                "ppg",
                                ParsedValue.StringVal("mean=${formatMean(mean)} (${vals.size}xs24)"),
                                variant.note
                            )
                            fb.parsed["ppg_sample_count"] = ParsedValue.IntVal(vals.size)
                            if (mean == Math.round(mean).toDouble()) {
                                fb.parsed["ppg_mean"] = ParsedValue.IntVal(mean.toInt())
                            } else {
                                fb.parsed["ppg_mean"] = ParsedValue.DoubleVal(mean)
                            }
                        }
                    }
                }
            }
        }

        postHooks["historical_data"] = { fb, frame, length, schema ->
            if (length != null) {
                val spec = schema.packet(frame[4].toInt() and 0xFF)
                val version = frame[5].toInt() and 0xFF
                fb.parsed["hist_version"] = ParsedValue.IntVal(version)
                val entry = spec?.let { schema.resolveVersion(it.versions, version) }
                if (entry == null) {
                    fb.region(7, length, "HISTORICAL_DATA v$version (unmapped layout)", "unknown")
                } else {
                    for (fld in entry.fields) {
                        val dtype = fld.dtype ?: continue
                        val value = when (dtype) {
                            "u8", "u16", "u32" -> {
                                val v = readHistInt(frame, fld.off, dtype) ?: continue
                                if (fld.enum != null) {
                                    ParsedValue.StringVal(schema.enumName(fld.enum, v))
                                } else {
                                    ParsedValue.IntVal(v)
                                }
                            }
                            "f32" -> {
                                val d = f32(frame, fld.off) ?: continue
                                ParsedValue.DoubleVal(d)
                            }
                            else -> continue
                        }
                        fb.add(fld.off, fld.len, fld.name, fld.cat, value, fld.note)
                    }
                    val rrVals = mutableListOf<Int>()
                    val rrFirst = entry.rrFirstOff
                    if (rrFirst != null) {
                        val rrn = fb.parsed["rr_count"]?.intValue ?: 0
                        for (i in 0 until Math.min(rrn, 4)) {
                            val o = rrFirst + i * 2
                            val v = u16(frame, o)
                            if (v != null && v != 0) {
                                fb.add(o, 2, "rr[$i]", "rr", ParsedValue.IntVal(v), "ms")
                                rrVals.add(v)
                            }
                        }
                    }
                    fb.parsed["rr_intervals"] = ParsedValue.IntArrayVal(rrVals)
                }
            }
        }

        postHooks["metadata"] = { fb, frame, length, _ ->
            if (length != null) {
                val payEnd = Math.min(length, frame.size)
                if (7 < payEnd) {
                    val paySize = payEnd - 7
                    if (paySize >= 14) {
                        val unix = (frame[7].toLong() and 0xFFL) or
                                ((frame[7 + 1].toLong() and 0xFFL) shl 8) or
                                ((frame[7 + 2].toLong() and 0xFFL) shl 16) or
                                ((frame[7 + 3].toLong() and 0xFFL) shl 24)
                        val ss = (frame[7 + 4].toInt() and 0xFF) or ((frame[7 + 5].toInt() and 0xFF) shl 8)
                        val unk0 = (frame[7 + 6].toLong() and 0xFFL) or
                                ((frame[7 + 7].toLong() and 0xFFL) shl 8) or
                                ((frame[7 + 8].toLong() and 0xFFL) shl 16) or
                                ((frame[7 + 9].toLong() and 0xFFL) shl 24)
                        val trim = (frame[7 + 10].toLong() and 0xFFL) or
                                ((frame[7 + 11].toLong() and 0xFFL) shl 8) or
                                ((frame[7 + 12].toLong() and 0xFFL) shl 16) or
                                ((frame[7 + 13].toLong() and 0xFFL) shl 24)
                        
                        fb.add(7, 4, "unix", "time", ParsedValue.IntVal(unix.toInt()))
                        fb.add(11, 2, "subsec", "time", ParsedValue.IntVal(ss))
                        fb.add(13, 4, "unk0", "meta", ParsedValue.IntVal(unk0.toInt()))
                        fb.add(17, 4, "trim_cursor", "meta", ParsedValue.IntVal(trim.toInt()), "ack with this to advance")
                    }
                }
            }
        }

        postHooks["console_logs"] = { fb, frame, length, _ ->
            if (length != null) {
                var txt = ""
                val lo = 11
                val hi = length - 1
                if (lo < hi && hi <= frame.size) {
                    txt = String(frame, lo, hi - lo, Charsets.UTF_8)
                }
                val head = if (txt.length > 80) txt.substring(0, 80) else txt
                fb.region(7, length, "console log text", "text", head)
                fb.parsed["log"] = ParsedValue.StringVal(txt)
            }
        }
    }
}
