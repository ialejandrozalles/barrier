package org.barrierfoss.androidclient.protocol

import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets

object ClipboardCodec {
    private const val FORMAT_TEXT = 0
    private const val MAX_TEXT_BYTES = 4 * 1024 * 1024

    fun normalizeText(value: String): String {
        return value.replace("\r\n", "\n").replace("\r", "\n")
    }

    fun encodeText(text: String): ByteArray {
        val normalized = normalizeText(text)
        val textBytes = normalized.toByteArray(StandardCharsets.UTF_8)
        if (textBytes.size > MAX_TEXT_BYTES) {
            return ByteArray(0)
        }

        val stream = ByteArrayOutputStream(12 + textBytes.size)
        writeInt32(stream, 1)
        writeInt32(stream, FORMAT_TEXT)
        writeInt32(stream, textBytes.size)
        stream.write(textBytes)
        return stream.toByteArray()
    }

    fun decodeText(data: ByteArray): String? {
        if (data.size < 4) {
            return null
        }

        var offset = 0
        val numFormats = readInt32(data, offset)
        offset += 4

        for (i in 0 until numFormats) {
            if (offset + 8 > data.size) {
                return null
            }

            val format = readInt32(data, offset)
            offset += 4
            val size = readInt32(data, offset)
            offset += 4

            if (size < 0 || offset + size > data.size) {
                return null
            }

            if (format == FORMAT_TEXT) {
                val textBytes = data.copyOfRange(offset, offset + size)
                val text = String(textBytes, StandardCharsets.UTF_8)
                return normalizeText(text)
            }

            offset += size
        }

        return null
    }

    private fun writeInt32(stream: ByteArrayOutputStream, value: Int) {
        stream.write((value ushr 24) and 0xFF)
        stream.write((value ushr 16) and 0xFF)
        stream.write((value ushr 8) and 0xFF)
        stream.write(value and 0xFF)
    }

    private fun readInt32(data: ByteArray, offset: Int): Int {
        return ((data[offset].toInt() and 0xFF) shl 24) or
            ((data[offset + 1].toInt() and 0xFF) shl 16) or
            ((data[offset + 2].toInt() and 0xFF) shl 8) or
            (data[offset + 3].toInt() and 0xFF)
    }
}
