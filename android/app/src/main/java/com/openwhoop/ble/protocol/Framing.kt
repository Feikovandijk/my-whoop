package com.openwhoop.ble.protocol

object Framing {
    private val crc8Table = intArrayOf(
        0x00, 0x07, 0x0E, 0x09, 0x1C, 0x1B, 0x12, 0x15, 0x38, 0x3F, 0x36, 0x31, 0x24, 0x23, 0x2A, 0x2D,
        0x70, 0x77, 0x7E, 0x79, 0x6C, 0x6B, 0x62, 0x65, 0x48, 0x4F, 0x46, 0x41, 0x54, 0x53, 0x5A, 0x5D,
        0xE0, 0xE7, 0xEE, 0xE9, 0xFC, 0xFB, 0xF2, 0xF5, 0xD8, 0xDF, 0xD6, 0xD1, 0xC4, 0xC3, 0xCA, 0xCD,
        0x90, 0x97, 0x9E, 0x99, 0x8C, 0x8B, 0x82, 0x85, 0xA8, 0xAF, 0xA6, 0xA1, 0xB4, 0xB3, 0xBA, 0xBD,
        0xC7, 0xC0, 0xC9, 0xCE, 0xDB, 0xDC, 0xD5, 0xD2, 0xFF, 0xF8, 0xF1, 0xF6, 0xE3, 0xE4, 0xED, 0xEA,
        0xB7, 0xB0, 0xB9, 0xBE, 0xAB, 0xAC, 0xA5, 0xA2, 0x8F, 0x88, 0x81, 0x86, 0x93, 0x94, 0x9D, 0x9A,
        0x27, 0x20, 0x29, 0x2E, 0x3B, 0x3C, 0x35, 0x32, 0x1F, 0x18, 0x11, 0x16, 0x03, 0x04, 0x0D, 0x0A,
        0x57, 0x50, 0x59, 0x5E, 0x4B, 0x4C, 0x45, 0x42, 0x6F, 0x68, 0x61, 0x66, 0x73, 0x74, 0x7D, 0x7A,
        0x89, 0x8E, 0x87, 0x80, 0x95, 0x92, 0x9B, 0x9C, 0xB1, 0xB6, 0xBF, 0xB8, 0xAD, 0xAA, 0xA3, 0xA4,
        0xF9, 0xFE, 0xF7, 0xF0, 0xE5, 0xE2, 0xEB, 0xEC, 0xC1, 0xC6, 0xCF, 0xC8, 0xDD, 0xDA, 0xD3, 0xD4,
        0x69, 0x6E, 0x67, 0x60, 0x75, 0x72, 0x7B, 0x7C, 0x51, 0x56, 0x5F, 0x58, 0x4D, 0x4A, 0x43, 0x44,
        0x19, 0x1E, 0x17, 0x10, 0x05, 0x02, 0x0B, 0x0C, 0x21, 0x26, 0x2F, 0x28, 0x3D, 0x3A, 0x33, 0x34,
        0x4E, 0x49, 0x40, 0x47, 0x52, 0x55, 0x5C, 0x5B, 0x76, 0x71, 0x78, 0x7F, 0x6A, 0x6D, 0x64, 0x63,
        0x3E, 0x39, 0x30, 0x37, 0x22, 0x25, 0x2C, 0x2B, 0x06, 0x01, 0x08, 0x0F, 0x1A, 0x1D, 0x14, 0x13,
        0xAE, 0xA9, 0xA0, 0xA7, 0xB2, 0xB5, 0xBC, 0xBB, 0x96, 0x91, 0x98, 0x9F, 0x8A, 0x8D, 0x84, 0x83,
        0xDE, 0xD9, 0xD0, 0xD7, 0xC2, 0xC5, 0xCC, 0xCB, 0xE6, 0xE1, 0xE8, 0xEF, 0xFA, 0xFD, 0xF4, 0xF3
    )

    fun crc8(bytes: ByteArray): Int {
        var crc = 0
        for (b in bytes) {
            val index = (crc xor b.toInt()) and 0xFF
            crc = crc8Table[index]
        }
        return crc
    }

    private val crc32Table: LongArray = LongArray(256) { i ->
        var c = i.toLong()
        for (j in 0 until 8) {
            c = if ((c and 1) != 0L) {
                0xEDB88320L xor (c shr 1)
            } else {
                c shr 1
            }
        }
        c
    }

    fun crc32(bytes: ByteArray): Long {
        var crc = 0xFFFFFFFFL
        for (b in bytes) {
            val index = ((crc xor b.toLong()) and 0xFFL).toInt()
            crc = crc32Table[index] xor (crc shr 8)
        }
        return crc xor 0xFFFFFFFFL
    }

    data class FrameCheck(
        val ok: Boolean,
        val length: Int? = null,
        val crc8OK: Boolean? = null,
        val crc32OK: Boolean? = null
    )

    private fun u16le(bytes: ByteArray, off: Int): Int {
        return (bytes[off].toInt() and 0xFF) or ((bytes[off + 1].toInt() and 0xFF) shl 8)
    }

    private fun u32le(bytes: ByteArray, off: Int): Long {
        return (bytes[off].toLong() and 0xFFL) or
                ((bytes[off + 1].toLong() and 0xFFL) shl 8) or
                ((bytes[off + 2].toLong() and 0xFFL) shl 16) or
                ((bytes[off + 3].toLong() and 0xFFL) shl 24)
    }

    fun verifyFrame(frame: ByteArray): FrameCheck {
        if (frame.size < 8 || (frame[0].toInt() and 0xFF) != 0xAA) {
            return FrameCheck(ok = false)
        }
        val length = u16le(frame, 1)
        val crc8OK = crc8(byteArrayOf(frame[1], frame[2])) == (frame[3].toInt() and 0xFF)
        var crc32OK: Boolean? = null
        if (7 <= length && length + 4 <= frame.size) {
            val inner = frame.copyOfRange(4, length)
            crc32OK = crc32(inner) == u32le(frame, length)
        }
        val ok = crc8OK && (crc32OK ?: false)
        return FrameCheck(ok = ok, length = length, crc8OK = crc8OK, crc32OK = crc32OK)
    }

    fun frameFromPayload(data: ByteArray, type: Byte, seq: Byte = 0, cmd: Byte = 0): ByteArray {
        val inner = ByteArray(3 + data.size)
        inner[0] = type
        inner[1] = seq
        inner[2] = cmd
        System.arraycopy(data, 0, inner, 3, data.size)

        val length = inner.size + 4
        val frame = ByteArray(4 + inner.size + 4)
        frame[0] = 0xAA.toByte()
        frame[1] = (length and 0xFF).toByte()
        frame[2] = ((length shr 8) and 0xFF).toByte()
        frame[3] = 0x00.toByte() // placeholder

        System.arraycopy(inner, 0, frame, 4, inner.size)

        val c = crc32(inner)
        val crcOff = 4 + inner.size
        frame[crcOff] = (c and 0xFF).toByte()
        frame[crcOff + 1] = ((c shr 8) and 0xFF).toByte()
        frame[crcOff + 2] = ((c shr 16) and 0xFF).toByte()
        frame[crcOff + 3] = ((c shr 24) and 0xFF).toByte()

        return frame
    }
}

class Reassembler {
    private val buf = mutableListOf<Byte>()

    fun feed(fragment: ByteArray): List<ByteArray> {
        synchronized(this) {
            for (b in fragment) {
                buf.add(b)
            }
            val out = mutableListOf<ByteArray>()
            while (true) {
                val sof = buf.indexOf(0xAA.toByte())
                if (sof == -1) {
                    buf.clear()
                    break
                }
                if (sof > 0) {
                    repeat(sof) { buf.removeAt(0) }
                }
                if (buf.size < 4) {
                    break
                }
                val length = (buf[1].toInt() and 0xFF) or ((buf[2].toInt() and 0xFF) shl 8)
                val total = length + 4
                if (buf.size < total) {
                    break
                }
                val frame = ByteArray(total)
                for (i in 0 until total) {
                    frame[i] = buf[i]
                }
                out.add(frame)
                repeat(total) { buf.removeAt(0) }
            }
            return out
        }
    }
}
