package org.barrierfoss.androidclient.protocol

import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets

data class ClipboardPayload(
    val text: String? = null,
    val html: String? = null,
    val bitmap: ByteArray? = null,
)

object ClipboardCodec {
    private const val FORMAT_TEXT = 0
    private const val FORMAT_HTML = 1
    private const val FORMAT_BITMAP = 2
    private const val MAX_PAYLOAD_BYTES = 4 * 1024 * 1024

    fun normalizeText(value: String): String {
        return value.replace("\r\n", "\n").replace("\r", "\n")
    }

    fun encodeText(text: String): ByteArray {
        return encode(ClipboardPayload(text = text))
    }

    fun encode(payload: ClipboardPayload): ByteArray {
        val formats = ArrayList<Pair<Int, ByteArray>>(3)

        payload.text?.let { text ->
            val normalized = normalizeText(text)
            val bytes = normalized.toByteArray(StandardCharsets.UTF_8)
            formats += FORMAT_TEXT to bytes
        }

        payload.html?.let { html ->
            val normalized = normalizeText(html)
            val bytes = normalized.toByteArray(StandardCharsets.UTF_8)
            formats += FORMAT_HTML to bytes
        }

        payload.bitmap?.let { bytes ->
            formats += FORMAT_BITMAP to bytes
        }

        if (formats.isEmpty()) {
            return ByteArray(0)
        }

        val totalSize = 4 + formats.sumOf { 8 + it.second.size }
        if (totalSize > MAX_PAYLOAD_BYTES) {
            return ByteArray(0)
        }

        val stream = ByteArrayOutputStream(totalSize)
        writeInt32(stream, formats.size)
        formats.sortedBy { it.first }.forEach { (format, bytes) ->
            writeInt32(stream, format)
            writeInt32(stream, bytes.size)
            stream.write(bytes)
        }
        return stream.toByteArray()
    }

    fun decode(data: ByteArray): ClipboardPayload? {
        if (data.size < 4) {
            return null
        }

        var offset = 0
        val numFormats = readInt32(data, offset)
        if (numFormats < 0 || numFormats > 8) {
            return null
        }
        offset += 4

        var text: String? = null
        var html: String? = null
        var bitmap: ByteArray? = null

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

            val bytes = data.copyOfRange(offset, offset + size)
            when (format) {
                FORMAT_TEXT -> text = normalizeText(String(bytes, StandardCharsets.UTF_8))
                FORMAT_HTML -> html = normalizeText(String(bytes, StandardCharsets.UTF_8))
                FORMAT_BITMAP -> bitmap = bytes
            }
            offset += size
        }

        if (text == null && html == null && bitmap == null) {
            return null
        }

        return ClipboardPayload(text = text, html = html, bitmap = bitmap)
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
