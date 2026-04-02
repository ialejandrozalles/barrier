package org.barrierfoss.androidclient.protocol

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.charset.StandardCharsets

class PacketReader(inputStream: InputStream) {
    private val input = DataInputStream(BufferedInputStream(inputStream))

    @Throws(IOException::class)
    fun readPacket(): ByteArray {
        val size = input.readInt()
        if (size < 0 || size > ProtocolConstants.MAX_PACKET_SIZE) {
            throw IOException("Invalid packet length: $size")
        }

        val payload = ByteArray(size)
        input.readFully(payload)
        return payload
    }
}

class PacketWriter(outputStream: OutputStream) {
    private val output = DataOutputStream(BufferedOutputStream(outputStream))

    @Throws(IOException::class)
    fun writePacket(payload: ByteArray) {
        output.writeInt(payload.size)
        output.write(payload)
        output.flush()
    }
}

class BufferCursor(private val data: ByteArray) {
    var offset: Int = 0
        private set

    fun remaining(): Int = data.size - offset

    @Throws(IOException::class)
    fun readAscii(length: Int): String {
        requireAvailable(length)
        val value = String(data, offset, length, StandardCharsets.US_ASCII)
        offset += length
        return value
    }

    @Throws(IOException::class)
    fun readInt8(): Int {
        requireAvailable(1)
        return data[offset++].toInt()
    }

    @Throws(IOException::class)
    fun readUInt8(): Int {
        requireAvailable(1)
        return data[offset++].toInt() and 0xFF
    }

    @Throws(IOException::class)
    fun readInt16(): Int {
        requireAvailable(2)
        val value = ((data[offset].toInt() and 0xFF) shl 8) or
            (data[offset + 1].toInt() and 0xFF)
        offset += 2
        return value.toShort().toInt()
    }

    @Throws(IOException::class)
    fun readInt32(): Int {
        requireAvailable(4)
        val value = ((data[offset].toInt() and 0xFF) shl 24) or
            ((data[offset + 1].toInt() and 0xFF) shl 16) or
            ((data[offset + 2].toInt() and 0xFF) shl 8) or
            (data[offset + 3].toInt() and 0xFF)
        offset += 4
        return value
    }

    @Throws(IOException::class)
    fun readLengthPrefixedString(): String {
        val length = readInt32()
        if (length < 0 || length > ProtocolConstants.MAX_PACKET_SIZE) {
            throw IOException("Invalid string length: $length")
        }
        requireAvailable(length)
        val value = String(data, offset, length, StandardCharsets.UTF_8)
        offset += length
        return value
    }

    @Throws(IOException::class)
    private fun requireAvailable(required: Int) {
        if (required > remaining()) {
            throw IOException("Packet underflow. Required=$required remaining=${remaining()}")
        }
    }
}

class PacketBuilder {
    private val stream = ByteArrayOutputStream()

    fun writeAscii(value: String): PacketBuilder {
        stream.write(value.toByteArray(StandardCharsets.US_ASCII))
        return this
    }

    fun writeInt8(value: Int): PacketBuilder {
        stream.write(value and 0xFF)
        return this
    }

    fun writeInt16(value: Int): PacketBuilder {
        stream.write((value ushr 8) and 0xFF)
        stream.write(value and 0xFF)
        return this
    }

    fun writeInt32(value: Int): PacketBuilder {
        stream.write((value ushr 24) and 0xFF)
        stream.write((value ushr 16) and 0xFF)
        stream.write((value ushr 8) and 0xFF)
        stream.write(value and 0xFF)
        return this
    }

    fun writeLengthPrefixedString(value: String): PacketBuilder {
        val bytes = value.toByteArray(StandardCharsets.UTF_8)
        writeInt32(bytes.size)
        stream.write(bytes)
        return this
    }

    fun toByteArray(): ByteArray = stream.toByteArray()
}

fun ByteArray.startsWithAscii(prefix: String): Boolean {
    val p = prefix.toByteArray(StandardCharsets.US_ASCII)
    if (size < p.size) {
        return false
    }
    for (i in p.indices) {
        if (this[i] != p[i]) {
            return false
        }
    }
    return true
}

fun ByteArray.readMessageCodeOrNull(): String? {
    if (size < 4) {
        return null
    }
    return String(this, 0, 4, StandardCharsets.US_ASCII)
}
