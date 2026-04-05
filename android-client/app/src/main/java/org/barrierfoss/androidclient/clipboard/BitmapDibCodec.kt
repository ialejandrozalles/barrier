package org.barrierfoss.androidclient.clipboard

import android.graphics.Bitmap
import kotlin.math.abs

object BitmapDibCodec {
    private const val HEADER_SIZE = 40
    private const val BI_RGB = 0

    fun encode(bitmap: Bitmap): ByteArray? {
        val safeBitmap = if (bitmap.config == Bitmap.Config.ARGB_8888) {
            bitmap
        } else {
            bitmap.copy(Bitmap.Config.ARGB_8888, false) ?: return null
        }

        val width = safeBitmap.width
        val height = safeBitmap.height
        if (width <= 0 || height <= 0) {
            return null
        }

        val rowSize = width * 4
        val imageSize = rowSize * height
        val totalSize = HEADER_SIZE + imageSize

        val bytes = ByteArray(totalSize)
        writeInt32(bytes, 0, HEADER_SIZE)
        writeInt32(bytes, 4, width)
        writeInt32(bytes, 8, height)
        writeInt16(bytes, 12, 1)
        writeInt16(bytes, 14, 32)
        writeInt32(bytes, 16, BI_RGB)
        writeInt32(bytes, 20, imageSize)
        writeInt32(bytes, 24, 0)
        writeInt32(bytes, 28, 0)
        writeInt32(bytes, 32, 0)
        writeInt32(bytes, 36, 0)

        val pixels = IntArray(width * height)
        safeBitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        var offset = HEADER_SIZE
        for (y in height - 1 downTo 0) {
            val rowStart = y * width
            for (x in 0 until width) {
                val color = pixels[rowStart + x]
                bytes[offset++] = (color and 0xFF).toByte()
                bytes[offset++] = ((color ushr 8) and 0xFF).toByte()
                bytes[offset++] = ((color ushr 16) and 0xFF).toByte()
                bytes[offset++] = ((color ushr 24) and 0xFF).toByte()
            }
        }

        return bytes
    }

    fun decode(data: ByteArray): Bitmap? {
        if (data.size < HEADER_SIZE) {
            return null
        }

        val headerSize = readInt32(data, 0)
        if (headerSize < HEADER_SIZE || headerSize > data.size) {
            return null
        }

        val width = readInt32(data, 4)
        val heightValue = readInt32(data, 8)
        val planes = readInt16(data, 12)
        val bitCount = readInt16(data, 14)
        val compression = readInt32(data, 16)

        if (width <= 0 || heightValue == 0 || planes != 1 || compression != BI_RGB) {
            return null
        }

        if (bitCount != 24 && bitCount != 32) {
            return null
        }

        val height = abs(heightValue)
        val rowSize = ((bitCount * width + 31) / 32) * 4
        val dataOffset = headerSize
        val requiredSize = dataOffset + rowSize * height

        if (requiredSize > data.size) {
            return null
        }

        val pixels = IntArray(width * height)
        val isTopDown = heightValue < 0

        for (row in 0 until height) {
            val srcRow = if (isTopDown) row else height - 1 - row
            var srcOffset = dataOffset + srcRow * rowSize
            val dstRowStart = row * width

            for (x in 0 until width) {
                val b = data[srcOffset++].toInt() and 0xFF
                val g = data[srcOffset++].toInt() and 0xFF
                val r = data[srcOffset++].toInt() and 0xFF
                val a = if (bitCount == 32) {
                    data[srcOffset++].toInt() and 0xFF
                } else {
                    0xFF
                }

                pixels[dstRowStart + x] = (a shl 24) or (r shl 16) or (g shl 8) or b
            }
        }

        return Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
    }

    private fun writeInt16(buffer: ByteArray, offset: Int, value: Int) {
        buffer[offset] = (value and 0xFF).toByte()
        buffer[offset + 1] = ((value ushr 8) and 0xFF).toByte()
    }

    private fun writeInt32(buffer: ByteArray, offset: Int, value: Int) {
        buffer[offset] = (value and 0xFF).toByte()
        buffer[offset + 1] = ((value ushr 8) and 0xFF).toByte()
        buffer[offset + 2] = ((value ushr 16) and 0xFF).toByte()
        buffer[offset + 3] = ((value ushr 24) and 0xFF).toByte()
    }

    private fun readInt16(buffer: ByteArray, offset: Int): Int {
        return (buffer[offset].toInt() and 0xFF) or
            ((buffer[offset + 1].toInt() and 0xFF) shl 8)
    }

    private fun readInt32(buffer: ByteArray, offset: Int): Int {
        return (buffer[offset].toInt() and 0xFF) or
            ((buffer[offset + 1].toInt() and 0xFF) shl 8) or
            ((buffer[offset + 2].toInt() and 0xFF) shl 16) or
            ((buffer[offset + 3].toInt() and 0xFF) shl 24)
    }
}
