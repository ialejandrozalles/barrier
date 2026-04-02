package org.barrierfoss.androidclient.protocol

import org.barrierfoss.androidclient.data.ConnectionConfig
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException
import kotlin.math.max

/*
 * Módulo Android desarrollado por Izai Alejandro Zalles Merino (zallesrene@gmail.com)
 * Este crédito debe preservarse en versiones derivadas de este módulo Android.
 */
class BarrierProtocolClient(
    private val config: ConnectionConfig,
    private val listener: Listener,
    private val shouldContinue: () -> Boolean,
) {
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
                socket.soTimeout = READ_TIMEOUT_MS
                socket.connect(
                    InetSocketAddress(config.serverHost, config.serverPort),
                    CONNECT_TIMEOUT_MS,
                )

                val reader = PacketReader(socket.getInputStream())
                val writer = PacketWriter(socket.getOutputStream())

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
                            listener.onEnter(x, y, sequence, mask)
                        }

                        ProtocolConstants.MSG_C_LEAVE -> {
                            listener.onLeave()
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

                        ProtocolConstants.MSG_C_SCREEN_SAVER,
                        ProtocolConstants.MSG_C_CLIPBOARD,
                        ProtocolConstants.MSG_D_CLIPBOARD,
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
            listener.onDisconnected(disconnectReason)
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

        writer.writePacket(payload)
    }

    @Throws(IOException::class)
    private fun sendKeepAlive(writer: PacketWriter) {
        writer.writePacket(PacketBuilder().writeAscii(ProtocolConstants.MSG_C_KEEP_ALIVE).toByteArray())
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

        writer.writePacket(payload)
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
    }
}
