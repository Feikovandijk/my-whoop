package com.openwhoop.database

import java.io.ByteArrayOutputStream
import java.util.zip.Deflater
import java.util.zip.Inflater

object CompressionUtil {
    fun compress(input: ByteArray): ByteArray {
        val deflater = Deflater()
        deflater.setInput(input)
        deflater.finish()
        val bos = ByteArrayOutputStream()
        val buffer = ByteArray(1024)
        while (!deflater.finished()) {
            val count = deflater.deflate(buffer)
            bos.write(buffer, 0, count)
        }
        deflater.end()
        val compressed = bos.toByteArray()
        
        val size = input.size
        val out = ByteArray(4 + compressed.size)
        out[0] = (size and 0xFF).toByte()
        out[1] = ((size shr 8) and 0xFF).toByte()
        out[2] = ((size shr 16) and 0xFF).toByte()
        out[3] = ((size shr 24) and 0xFF).toByte()
        System.arraycopy(compressed, 0, out, 4, compressed.size)
        return out
    }

    fun decompress(input: ByteArray): ByteArray {
        if (input.size < 4) throw IllegalArgumentException("Compressed input too small")
        val size = (input[0].toInt() and 0xFF) or
                ((input[1].toInt() and 0xFF) shl 8) or
                ((input[2].toInt() and 0xFF) shl 16) or
                ((input[3].toInt() and 0xFF) shl 24)
        if (size == 0) return byteArrayOf()
        
        val inflater = Inflater()
        inflater.setInput(input, 4, input.size - 4)
        val bos = ByteArrayOutputStream(size)
        val buffer = ByteArray(1024)
        while (!inflater.finished()) {
            val count = inflater.inflate(buffer)
            if (count == 0 && inflater.needsInput()) break
            bos.write(buffer, 0, count)
        }
        inflater.end()
        return bos.toByteArray()
    }
}
