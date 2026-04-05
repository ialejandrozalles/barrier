package org.barrierfoss.androidclient.protocol

import org.barrierfoss.androidclient.data.ConnectionConfig
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException
import java.nio.charset.StandardCharsets
import kotlin.math.max
import kotlin.math.min

/*
 * Módulo Android desarrollado por Izai Alejandro Zalles Merino (zallesrene@gmail.com)
 * Este crédito debe preservarse en versiones derivadas de este módulo Android.
 */
class BarrierProtocolClient(
    private val config: ConnectionConfig,
    private val listener: Listener,
    private val shouldContinue: () -> Boolean,
    private val connectTimeoutMs: Int = CONNECT_TIMEOUT_MS,
    private val readTimeoutMs: Int = READ_TIMEOUT_MS,
) {
    private val writeLock = Any()
    private val clipboardAssembler = ClipboardAssembler()

    @Volatile
    private var activeWriter: PacketWriter? = null

    @Volatile
    private var connected = false

    @Volatile
    private var currentSequence = 0

    data class ClientInfo(
        val x: Int,
        val y: Int,
        val width: Int,
        val height: Int,
        val cursorX: Int,
        val cursorY: Int,
    )

    interface Listener {
        fun onConnecting()
        fun onConnected()
        fun onDisconnected(reason: String?)

        fun onEnter(x: Int, y: Int, sequence: Int, modifierMask: Int)
        fun onLeave()

        fun onMouseMove(x: Int, y: Int)
        fun onMouseRelativeMove(dx: Int, dy: Int)
        fun onMouseWheel(xDelta: Int, yDelta: Int)
        fun onMouseDown(buttonId: Int)
        fun onMouseUp(buttonId: Int)

        fun onKeyDown(keyId: Int, modifierMask: Int, keyButton: Int)
        fun onKeyUp(keyId: Int, modifierMask: Int, keyButton: Int)
        fun onKeyRepeat(keyId: Int, modifierMask: Int, count: Int, keyButton: Int)

        fun onClipboardText(text: String)

        fun currentClientInfo(): ClientInfo
    }

    @Throws(IOException::class)
    fun runSession() {
        listener.onConnecting()

        var disconnectReason: String? = null
        try {
            Socket().use { socket ->
                socket.tcpNoDelay = true
                socket.keepAlive = true
                socket.soTimeout = readTimeoutMs
                socket.connect(
                    InetSocketAddress(config.serverHost, config.serverPort),
                    connectTimeoutMs,
                )

                val reader = PacketReader(socket.getInputStream())
                val writer = PacketWriter(socket.getOutputStream())
                activeWriter = writer

                val hello = readNextPacket(reader)
                processHello(hello)
                sendHelloBack(writer)

                var handshakeComplete = false

                while (shouldContinue()) {
                    val payload = try {
                        readNextPacket(reader)
                    } catch (_: SocketTimeoutException) {
                        continue
                    }

                    val code = payload.readMessageCodeOrNull()
                        ?: throw IOException("Malformed packet: missing code")

                    val cursor = BufferCursor(payload)
                    cursor.readAscii(4)

                    when (code) {
                        ProtocolConstants.MSG_Q_INFO -> {
                            sendClientInfo(writer, listener.currentClientInfo())
                        }

                        ProtocolConstants.MSG_C_INFO_ACK -> {
                            // Info ACK can be ignored in this client implementation.
                        }

                        ProtocolConstants.MSG_D_SET_OPTIONS -> {
                            parseOptionPairs(cursor)
                            if (!handshakeComplete) {
                                handshakeComplete = true
                                connected = true
                                listener.onConnected()
                            }
                        }

                        ProtocolConstants.MSG_C_RESET_OPTIONS -> {
                            // Reset options accepted; defaults are fine for Android client.
                        }

                        ProtocolConstants.MSG_C_KEEP_ALIVE -> {
                            sendKeepAlive(writer)
                        }

                        ProtocolConstants.MSG_C_NOOP -> {
                            // No operation.
                        }

                        ProtocolConstants.MSG_C_ENTER -> {
                            val x = cursor.readInt16()
                            val y = cursor.readInt16()
                            val sequence = cursor.readInt32()
                            val mask = cursor.readInt16() and 0xFFFF
                            currentSequence = sequence
                            listener.onEnter(x, y, sequence, mask)
                        }

                        ProtocolConstants.MSG_C_LEAVE -> {
                            listener.onLeave()
                        }

                        ProtocolConstants.MSG_C_CLIPBOARD -> {
                            cursor.readUInt8()
                            cursor.readInt32()
                        }

                        ProtocolConstants.MSG_D_MOUSE_MOVE -> {
                            val x = cursor.readInt16()
                            val y = cursor.readInt16()
                            listener.onMouseMove(x, y)
                        }

                        ProtocolConstants.MSG_D_MOUSE_REL_MOVE -> {
                            val dx = cursor.readInt16()
                            val dy = cursor.readInt16()
                            listener.onMouseRelativeMove(dx, dy)
                        }

                        ProtocolConstants.MSG_D_MOUSE_WHEEL -> {
                            val xDelta: Int
                            val yDelta: Int
                            if (cursor.remaining() >= 4) {
                                xDelta = cursor.readInt16()
                                yDelta = cursor.readInt16()
                            } else if (cursor.remaining() >= 2) {
                                xDelta = 0
                                yDelta = cursor.readInt16()
                            } else {
                                throw IOException("Malformed mouse wheel packet")
                            }
                            listener.onMouseWheel(xDelta, yDelta)
                        }

                        ProtocolConstants.MSG_D_MOUSE_DOWN -> {
                            listener.onMouseDown(cursor.readInt8())
                        }

                        ProtocolConstants.MSG_D_MOUSE_UP -> {
                            listener.onMouseUp(cursor.readInt8())
                        }

                        ProtocolConstants.MSG_D_KEY_DOWN -> {
                            val keyId = cursor.readInt16() and 0xFFFF
                            val mask = cursor.readInt16() and 0xFFFF
                            val keyButton = cursor.readInt16() and 0xFFFF
                            listener.onKeyDown(keyId, mask, keyButton)
                        }

                        ProtocolConstants.MSG_D_KEY_UP -> {
                            val keyId = cursor.readInt16() and 0xFFFF
                            val mask = cursor.readInt16() and 0xFFFF
                            val keyButton = cursor.readInt16() and 0xFFFF
                            listener.onKeyUp(keyId, mask, keyButton)
                        }

                        ProtocolConstants.MSG_D_KEY_REPEAT -> {
                            val keyId = cursor.readInt16() and 0xFFFF
                            val mask = cursor.readInt16() and 0xFFFF
                            val count = max(1, cursor.readInt16() and 0xFFFF)
                            val keyButton = cursor.readInt16() and 0xFFFF
                            listener.onKeyRepeat(keyId, mask, count, keyButton)
                        }

                        ProtocolConstants.MSG_D_CLIPBOARD -> {
                            handleClipboardChunk(cursor)
                        }

                        ProtocolConstants.MSG_C_SCREEN_SAVER,
                        ProtocolConstants.MSG_D_FILE_TRANSFER,
                        ProtocolConstants.MSG_D_DRAG_INFO -> {
                            // Accepted but intentionally ignored for Android input-only client.
                        }

                        ProtocolConstants.MSG_C_CLOSE -> {
                            disconnectReason = "Server requested disconnect"
                            return
                        }

                        ProtocolConstants.MSG_E_INCOMPATIBLE -> {
                            val major = cursor.readInt16()
                            val minor = cursor.readInt16()
                            throw IOException("Incompatible server protocol: $major.$minor")
                        }

                        ProtocolConstants.MSG_E_BUSY -> {
                            throw IOException("Server reports duplicated client name")
                        }

                        ProtocolConstants.MSG_E_UNKNOWN -> {
                            throw IOException("Server does not recognize configured screen name")
                        }

                        ProtocolConstants.MSG_E_BAD -> {
                            throw IOException("Server rejected client for protocol violation")
                        }

                        else -> {
                            throw IOException("Unsupported packet code: $code")
                        }
                    }
                }
            }
        } catch (e: IOException) {
            disconnectReason = e.message ?: "I/O error"
            throw e
        } finally {
            connected = false
            activeWriter = null
            listener.onDisconnected(disconnectReason)
        }
    }

    fun sendClipboardText(text: String) {
        if (!connected) {
            return
        }

        val writer = activeWriter ?: return
        val clipboardData = ClipboardCodec.encodeText(text)
        if (clipboardData.isEmpty()) {
            return
        }

        val sequence = currentSequence
        try {
            sendClipboardGrab(writer, CLIPBOARD_ID, sequence)
            sendClipboardChunks(writer, CLIPBOARD_ID, sequence, clipboardData)
        } catch (_: IOException) {
            // Ignore clipboard send failures; connection loop will handle disconnects.
        }
    }

    @Throws(IOException::class)
    private fun processHello(payload: ByteArray) {
        if (!payload.startsWithAscii(ProtocolConstants.HELLO_PREFIX)) {
            throw IOException("Expected Barrier hello packet")
        }

        val cursor = BufferCursor(payload)
        val prefix = cursor.readAscii(ProtocolConstants.HELLO_PREFIX.length)
        if (prefix != ProtocolConstants.HELLO_PREFIX) {
            throw IOException("Invalid hello header")
        }

        val serverMajor = cursor.readInt16()
        val serverMinor = cursor.readInt16()

        if (
            serverMajor < ProtocolConstants.PROTOCOL_MAJOR ||
            (serverMajor == ProtocolConstants.PROTOCOL_MAJOR &&
                serverMinor < ProtocolConstants.PROTOCOL_MINOR)
        ) {
            throw IOException(
                "Server protocol $serverMajor.$serverMinor is lower than required " +
                    "${ProtocolConstants.PROTOCOL_MAJOR}.${ProtocolConstants.PROTOCOL_MINOR}",
            )
        }
    }

    @Throws(IOException::class)
    private fun sendHelloBack(writer: PacketWriter) {
        val payload = PacketBuilder()
            .writeAscii(ProtocolConstants.HELLO_PREFIX)
            .writeInt16(ProtocolConstants.PROTOCOL_MAJOR)
            .writeInt16(ProtocolConstants.PROTOCOL_MINOR)
            .writeLengthPrefixedString(config.screenName)
            .toByteArray()

        writePacket(writer, payload)
    }

    @Throws(IOException::class)
    private fun sendKeepAlive(writer: PacketWriter) {
        writePacket(writer, PacketBuilder().writeAscii(ProtocolConstants.MSG_C_KEEP_ALIVE).toByteArray())
    }

    @Throws(IOException::class)
    private fun sendClientInfo(writer: PacketWriter, info: ClientInfo) {
        val payload = PacketBuilder()
            .writeAscii(ProtocolConstants.MSG_D_INFO)
            .writeInt16(info.x)
            .writeInt16(info.y)
            .writeInt16(info.width)
            .writeInt16(info.height)
            .writeInt16(0)
            .writeInt16(info.cursorX)
            .writeInt16(info.cursorY)
            .toByteArray()

        writePacket(writer, payload)
    }

    @Throws(IOException::class)
    private fun writePacket(writer: PacketWriter, payload: ByteArray) {
        synchronized(writeLock) {
            writer.writePacket(payload)
        }
    }

    @Throws(IOException::class)
    private fun sendClipboardGrab(writer: PacketWriter, clipboardId: Int, sequence: Int) {
        val payload = PacketBuilder()
            .writeAscii(ProtocolConstants.MSG_C_CLIPBOARD)
            .writeInt8(clipboardId)
            .writeInt32(sequence)
            .toByteArray()

        writePacket(writer, payload)
    }

    @Throws(IOException::class)
    private fun sendClipboardChunks(
        writer: PacketWriter,
        clipboardId: Int,
        sequence: Int,
        data: ByteArray,
    ) {
        val sizeBytes = data.size.toString().toByteArray(StandardCharsets.US_ASCII)
        sendClipboardChunk(writer, clipboardId, sequence, DATA_START, sizeBytes)

        var offset = 0
        while (offset < data.size) {
            val end = min(offset + CLIPBOARD_CHUNK_SIZE, data.size)
            sendClipboardChunk(
                writer,
                clipboardId,
                sequence,
                DATA_CHUNK,
                data.copyOfRange(offset, end),
            )
            offset = end
        }

        sendClipboardChunk(writer, clipboardId, sequence, DATA_END, ByteArray(0))
    }

    @Throws(IOException::class)
    private fun sendClipboardChunk(
        writer: PacketWriter,
        clipboardId: Int,
        sequence: Int,
        mark: Int,
        chunkData: ByteArray,
    ) {
        val payload = PacketBuilder()
            .writeAscii(ProtocolConstants.MSG_D_CLIPBOARD)
            .writeInt8(clipboardId)
            .writeInt32(sequence)
            .writeInt8(mark)
            .writeLengthPrefixedBytes(chunkData)
            .toByteArray()

        writePacket(writer, payload)
    }

    private fun handleClipboardChunk(cursor: BufferCursor) {
        val clipboardId = cursor.readUInt8()
        val sequence = cursor.readInt32()
        val mark = cursor.readUInt8()
        val chunkData = cursor.readLengthPrefixedBytes()

        val assembled = clipboardAssembler.consume(clipboardId, sequence, mark, chunkData)
        if (assembled != null) {
            val text = ClipboardCodec.decodeText(assembled) ?: return
            listener.onClipboardText(text)
        }
    }

    private class ClipboardAssembler {
        private var expectedSize = -1
        private var activeId = -1
        private var activeSequence = -1
        private val buffer = ByteArrayOutputStream()

        fun consume(id: Int, sequence: Int, mark: Int, data: ByteArray): ByteArray? {
            return when (mark) {
                DATA_START -> {
                    val size = parseExpectedSize(data) ?: return reset()
                    expectedSize = size
                    activeId = id
                    activeSequence = sequence
                    buffer.reset()
                    null
                }

                DATA_CHUNK -> {
                    if (!matches(id, sequence) || expectedSize < 0) {
                        return reset()
                    }
                    if (buffer.size() + data.size > expectedSize) {
                        return reset()
                    }
                    buffer.write(data)
                    null
                }

                DATA_END -> {
                    if (!matches(id, sequence) || expectedSize < 0) {
                        return reset()
                    }
                    if (buffer.size() != expectedSize) {
                        return reset()
                    }
                    val complete = buffer.toByteArray()
                    reset()
                    complete
                }

                else -> reset()
            }
        }

        private fun matches(id: Int, sequence: Int): Boolean {
            if (activeId == -1 && activeSequence == -1) {
                return true
            }
            return activeId == id && activeSequence == sequence
        }

        private fun parseExpectedSize(data: ByteArray): Int? {
            val sizeText = String(data, StandardCharsets.US_ASCII).trim()
            val size = sizeText.toLongOrNull() ?: return null
            if (size < 0 || size > MAX_CLIPBOARD_BYTES) {
                return null
            }
            return size.toInt()
        }

        private fun reset(): ByteArray? {
            expectedSize = -1
            activeId = -1
            activeSequence = -1
            buffer.reset()
            return null
        }
    }

    @Throws(IOException::class)
    private fun parseOptionPairs(cursor: BufferCursor): List<Pair<Int, Int>> {
        val pairCount = cursor.readInt32()
        if (pairCount < 0 || pairCount > 1024 * 1024) {
            throw IOException("Invalid options vector length: $pairCount")
        }

        val values = ArrayList<Int>(pairCount)
        repeat(pairCount) {
            values += cursor.readInt32()
        }

        val pairs = ArrayList<Pair<Int, Int>>(values.size / 2)
        var i = 0
        while (i + 1 < values.size) {
            pairs += values[i] to values[i + 1]
            i += 2
        }
        return pairs
    }

    @Throws(IOException::class)
    private fun readNextPacket(reader: PacketReader): ByteArray {
        val packet = reader.readPacket()
        if (packet.isEmpty()) {
            throw IOException("Empty packet received")
        }
        return packet
    }

    private companion object {
        const val CONNECT_TIMEOUT_MS = 5000
        const val READ_TIMEOUT_MS = 2000
        const val CLIPBOARD_ID = 0
        const val CLIPBOARD_CHUNK_SIZE = 32 * 1024
        const val MAX_CLIPBOARD_BYTES = 4 * 1024 * 1024
        const val DATA_START = 1
        const val DATA_CHUNK = 2
        const val DATA_END = 3
    }
}
