package org.barrierfoss.androidclient.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.barrierfoss.androidclient.MainActivity
import org.barrierfoss.androidclient.R
import org.barrierfoss.androidclient.clipboard.ClipboardSyncController
import org.barrierfoss.androidclient.data.ConnectionConfig
import org.barrierfoss.androidclient.data.SettingsRepository
import org.barrierfoss.androidclient.protocol.BarrierProtocolClient
import org.barrierfoss.androidclient.usb.UsbTransport
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException

/*
 * Módulo Android desarrollado por Izai Alejandro Zalles Merino (zallesrene@gmail.com)
 * Este crédito debe preservarse en versiones derivadas de este módulo Android.
 */
class BarrierConnectionService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private lateinit var settingsRepository: SettingsRepository
    private var workerJob: Job? = null

    private var clipboardSync: ClipboardSyncController? = null

    @Volatile
    private var activeProtocol: BarrierProtocolClient? = null

    @Volatile
    private var running = false

    override fun onCreate() {
        super.onCreate()
        settingsRepository = SettingsRepository(this)
        clipboardSync = ClipboardSyncController(this, scope) { text ->
            activeProtocol?.sendClipboardText(text)
        }.also { it.start() }
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> stopClient()
            ACTION_START, null -> startClient()
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        running = false
        workerJob?.cancel()
        workerJob = null
        clipboardSync?.stop()
        clipboardSync = null
        activeProtocol = null
        scope.cancel()
        super.onDestroy()
    }

    private fun startClient() {
        if (workerJob?.isActive == true) {
            return
        }

        startForeground(NOTIFICATION_ID, buildNotification(getString(R.string.notification_connecting)))

        val config = settingsRepository.load()
        if (!config.isValid()) {
            updateNotification("Configuración inválida.")
            stopClient()
            return
        }

        running = true

        workerJob = scope.launch {
            while (isActive && running) {
                val accessibilityService = BarrierAccessibilityService.instance()
                if (accessibilityService == null) {
                    updateNotification("Habilita el servicio de accesibilidad para continuar.")
                    delay(RETRY_DELAY_MS)
                    continue
                }

                val targets = buildConnectionTargets(config)
                for (targetIndex in targets.indices) {
                    val target = targets[targetIndex]
                    val isLast = targetIndex == targets.lastIndex
                    var connected = false

                    val protocol = BarrierProtocolClient(
                        config = target.config,
                        listener = object : BarrierProtocolClient.Listener {
                            override fun onConnecting() {
                                updateNotification(
                                    "${getString(R.string.notification_connecting)} [${target.label}]",
                                )
                            }

                            override fun onConnected() {
                                connected = true
                                updateNotification(
                                    "${getString(R.string.notification_connected)} [${target.label}]",
                                )
                            }

                            override fun onDisconnected(reason: String?) {
                                if (!connected && !isLast) {
                                    return
                                }
                                val suffix = if (reason.isNullOrBlank()) "" else " ($reason)"
                                updateNotification(
                                    "${getString(R.string.notification_disconnected)}$suffix",
                                )
                            }

                            override fun onEnter(x: Int, y: Int, sequence: Int, modifierMask: Int) {
                                BarrierAccessibilityService.instance()?.onEnter(x, y, sequence, modifierMask)
                            }

                            override fun onLeave() {
                                BarrierAccessibilityService.instance()?.onLeave()
                            }

                            override fun onMouseMove(x: Int, y: Int) {
                                BarrierAccessibilityService.instance()?.onMouseMove(x, y)
                            }

                            override fun onMouseRelativeMove(dx: Int, dy: Int) {
                                BarrierAccessibilityService.instance()?.onMouseRelativeMove(dx, dy)
                            }

                            override fun onMouseWheel(xDelta: Int, yDelta: Int) {
                                BarrierAccessibilityService.instance()?.onMouseWheel(xDelta, yDelta)
                            }

                            override fun onMouseDown(buttonId: Int) {
                                BarrierAccessibilityService.instance()?.onMouseDown(buttonId)
                            }

                            override fun onMouseUp(buttonId: Int) {
                                BarrierAccessibilityService.instance()?.onMouseUp(buttonId)
                            }

                            override fun onKeyDown(keyId: Int, modifierMask: Int, keyButton: Int) {
                                BarrierAccessibilityService.instance()
                                    ?.onKeyDown(keyId, modifierMask, keyButton)
                            }

                            override fun onKeyUp(keyId: Int, modifierMask: Int, keyButton: Int) {
                                BarrierAccessibilityService.instance()
                                    ?.onKeyUp(keyId, modifierMask, keyButton)
                            }

                            override fun onKeyRepeat(
                                keyId: Int,
                                modifierMask: Int,
                                count: Int,
                                keyButton: Int,
                            ) {
                                BarrierAccessibilityService.instance()
                                    ?.onKeyRepeat(keyId, modifierMask, count, keyButton)
                            }

                            override fun onClipboardText(text: String) {
                                clipboardSync?.setRemoteClipboardText(text)
                            }

                            override fun currentClientInfo(): BarrierProtocolClient.ClientInfo {
                                return BarrierAccessibilityService.instance()?.currentClientInfo()
                                    ?: BarrierProtocolClient.ClientInfo(
                                        x = 0,
                                        y = 0,
                                        width = 1080,
                                        height = 1920,
                                        cursorX = 540,
                                        cursorY = 960,
                                    )
                            }
                        },
                        shouldContinue = { running && isActive },
                        connectTimeoutMs = target.connectTimeoutMs,
                    )

                    try {
                        activeProtocol = protocol
                        protocol.runSession()
                        break
                    } catch (e: IOException) {
                        val shouldTryNext = !connected && !isLast && shouldTryNextEndpoint(e)
                        if (shouldTryNext) {
                            continue
                        }
                    } catch (e: Exception) {
                        updateNotification("Error inesperado: ${e.message}")
                    } finally {
                        if (activeProtocol === protocol) {
                            activeProtocol = null
                        }
                    }

                    break
                }

                if (!isActive || !running) {
                    break
                }

                delay(RETRY_DELAY_MS)
            }
        }
    }

    private fun stopClient() {
        running = false
        workerJob?.cancel()
        workerJob = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun updateNotification(content: String) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, buildNotification(content))
    }

    private fun buildNotification(content: String) = NotificationCompat.Builder(this, CHANNEL_ID)
        .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
        .setContentTitle(getString(R.string.app_name))
        .setContentText(content)
        .setOngoing(true)
        .setContentIntent(mainPendingIntent())
        .build()

    private fun mainPendingIntent(): PendingIntent {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val pendingFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }

        return PendingIntent.getActivity(this, 1, intent, pendingFlags)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return
        }

        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        )
        manager.createNotificationChannel(channel)
    }

    private fun buildConnectionTargets(config: ConnectionConfig): List<ConnectionTarget> {
        val targets = ArrayList<ConnectionTarget>(2)
        val normalizedHost = config.serverHost.trim()

        val usbTarget = ConnectionTarget(
            config = config.copy(serverHost = UsbTransport.USB_HOST),
            label = "USB",
            connectTimeoutMs = USB_CONNECT_TIMEOUT_MS,
        )

        if (normalizedHost == UsbTransport.USB_HOST) {
            targets += usbTarget
            return targets
        }

        // Try USB first with a short timeout; fall back to LAN if it fails.
        targets += usbTarget

        targets += ConnectionTarget(
            config = config,
            label = "LAN",
            connectTimeoutMs = LAN_CONNECT_TIMEOUT_MS,
        )

        return targets
    }

    private fun shouldTryNextEndpoint(error: IOException): Boolean {
        return error is ConnectException || error is SocketTimeoutException
    }

    private data class ConnectionTarget(
        val config: ConnectionConfig,
        val label: String,
        val connectTimeoutMs: Int,
    )

    companion object {
        const val ACTION_START = "org.barrierfoss.androidclient.action.START"
        const val ACTION_STOP = "org.barrierfoss.androidclient.action.STOP"

        private const val CHANNEL_ID = "barrier_android_client"
        private const val NOTIFICATION_ID = 11011
        private const val RETRY_DELAY_MS = 2000L
        private const val USB_CONNECT_TIMEOUT_MS = 1000
        private const val LAN_CONNECT_TIMEOUT_MS = 5000
    }
}
