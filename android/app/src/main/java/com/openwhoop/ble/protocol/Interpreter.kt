package com.openwhoop.ble.protocol

import android.content.Context
import java.util.Locale

object Interpreter {
    
    private fun readU8(f: ByteArray, off: Int): Int? {
        return if (off + 1 <= f.size) f[off].toInt() and 0xFF else null
    }

    private fun readU16(f: ByteArray, off: Int): Int? {
        return if (off + 2 <= f.size) {
            (f[off].toInt() and 0xFF) or ((f[off + 1].toInt() and 0xFF) shl 8)
        } else null
    }

    private fun readU32(f: ByteArray, off: Int): Long? {
        if (off + 4 > f.size) return null
        return (f[off].toLong() and 0xFFL) or
                ((f[off + 1].toLong() and 0xFFL) shl 8) or
                ((f[off + 2].toLong() and 0xFFL) shl 16) or
                ((f[off + 3].toLong() and 0xFFL) shl 24)
    }

    private fun readI16(f: ByteArray, off: Int): Int? {
        if (off + 2 > f.size) return null
        val raw = (f[off].toInt() and 0xFF) or ((f[off + 1].toInt() and 0xFF) shl 8)
        return raw.toShort().toInt()
    }

    private fun readDType(f: ByteArray, off: Int, dtype: String): Long? {
        return when (dtype) {
            "u8" -> readU8(f, off)?.toLong()
            "u16" -> readU16(f, off)?.toLong()
            "u32" -> readU32(f, off)
            "i16" -> readI16(f, off)?.toLong()
            else -> null
        }
    }

    private fun hexString(bytes: ByteArray, off: Int, len: Int): String {
        val start = Math.max(0, off)
        val end = Math.min(off + len, bytes.size)
        if (start >= end) return ""
        val sb = StringBuilder()
        for (i in start until end) {
            sb.append(String.format("%02x", bytes[i]))
        }
        return sb.toString()
    }

    class FieldBuilder(val frame: ByteArray) {
        val fields = mutableListOf<DecodedField>()
        val parsed = mutableMapOf<String, ParsedValue>()

        fun add(off: Int, length: Int, name: String, cat: String, value: ParsedValue? = null, note: String? = null): FieldBuilder {
            val raw = hexString(frame, off, length)
            fields.add(DecodedField(off, length, name, cat, value, raw, note))
            if (value != null && cat != "frame" && cat != "unknown") {
                parsed[name] = value
            }
            return this
        }

        fun region(start: Int, end: Int, name: String, cat: String, note: String? = null) {
            if (start < end && end <= frame.size) {
                add(start, end - start, name, cat, ParsedValue.StringVal("[${end - start} bytes]"), note)
            }
        }
    }

    fun parseFrame(context: Context?, frame: ByteArray): ParsedFrame {
        val rawHex = hexString(frame, 0, frame.size)
        if (frame.size < 8 || (frame[0].toInt() and 0xFF) != 0xAA) {
            return ParsedFrame(
                ok = false,
                typeName = "INVALID/FRAGMENT",
                seq = null,
                cmdName = null,
                crcOK = null,
                lenBytes = frame.size,
                rawHex = rawHex,
                fields = emptyList(),
                parsed = emptyMap()
            )
        }

        val schema = Schema.get(context)
        val check = Framing.verifyFrame(frame)
        val length = check.length
        val crcOK = check.crc32OK

        val t = frame[4].toInt() and 0xFF
        val typeName = schema.typeName(t)
        val seq = frame[5].toInt() and 0xFF

        val fb = FieldBuilder(frame)
        fb.add(0, 1, "SOF", "frame", ParsedValue.StringVal("0xAA"))
        fb.add(1, 2, "length", "frame", length?.let { ParsedValue.IntVal(it) })
        fb.add(3, 1, "crc8", "frame", ParsedValue.StringVal(String.format("0x%02X", frame[3].toInt() and 0xFF)))
        fb.add(4, 1, "packet_type", "frame", ParsedValue.StringVal(typeName))
        fb.add(5, 1, "seq", "frame", ParsedValue.IntVal(seq))

        val spec = schema.packet(t)
        if (spec == null) {
            fb.add(6, 1, "cmd", "cmd", if (frame.size > 6) ParsedValue.IntVal(frame[6].toInt() and 0xFF) else null)
            length?.let { fb.region(7, it, "payload", "unknown") }
        } else {
            for (fld in spec.fields) {
                val dtype = fld.dtype ?: continue
                val valLong = readDType(frame, fld.off, dtype) ?: continue
                val value = if (fld.enum != null) {
                    ParsedValue.StringVal(schema.enumName(fld.enum, valLong.toInt()))
                } else {
                    ParsedValue.IntVal(valLong)
                }
                fb.add(fld.off, fld.len, fld.name, fld.cat, value, fld.note)
            }
            
            if (spec.post != null) {
                val hook = PostHooks.postHooks[spec.post]
                hook?.invoke(fb, frame, length, schema)
            }
        }

        if (length != null && length + 4 <= frame.size) {
            val crcVal = (frame[length].toLong() and 0xFFL) or
                    ((frame[length + 1].toLong() and 0xFFL) shl 8) or
                    ((frame[length + 2].toLong() and 0xFFL) shl 16) or
                    ((frame[length + 3].toLong() and 0xFFL) shl 24)
            fb.add(
                length,
                4,
                "crc32",
                "frame",
                ParsedValue.StringVal(String.format("0x%08X", crcVal)),
                if (check.crc32OK == true) "OK" else "MISMATCH"
            )
        }

        val cmdByte = if (frame.size > 6) (frame[6].toInt() and 0xFF) else 0
        val cmdName = if (t == 35 || t == 36) schema.enumName("CommandNumber", cmdByte) else null

        return ParsedFrame(
            ok = true,
            typeName = typeName,
            seq = seq,
            cmdName = cmdName,
            crcOK = crcOK,
            lenBytes = frame.size,
            rawHex = rawHex,
            fields = fb.fields,
            parsed = fb.parsed
        )
    }
}
