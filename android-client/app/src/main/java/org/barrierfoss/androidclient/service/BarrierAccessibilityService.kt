package org.barrierfoss.androidclient.service

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.TextUtils
import android.view.accessibility.AccessibilityEvent
import org.barrierfoss.androidclient.input.BarrierInputController
import org.barrierfoss.androidclient.protocol.BarrierProtocolClient

/*
 * Módulo Android desarrollado por Izai Alejandro Zalles Merino (zallesrene@gmail.com)
 * Este crédito debe preservarse en versiones derivadas de este módulo Android.
 */
class BarrierAccessibilityService : AccessibilityService() {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val moveDispatchRunnable = Runnable { flushPendingMoves() }
    private val moveLock = Any()

    private var inputController: BarrierInputController? = null
    private var moveDispatchScheduled = false
    private var pendingAbsoluteMove = false
    private var pendingAbsX = 0
    private var pendingAbsY = 0
    private var pendingRelDx = 0
    private var pendingRelDy = 0

    override fun onServiceConnected() {
        super.onServiceConnected()
        inputController = BarrierInputController(this)
        instance = this
    }

    override fun onDestroy() {
        inputController?.release()
        inputController = null
        if (instance === this) {
            instance = null
        }
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // No-op. This service injects events and does not need event stream processing.
    }

    override fun onInterrupt() {
        // No-op.
    }

    fun currentClientInfo(): BarrierProtocolClient.ClientInfo {
        return inputController?.clientInfo() ?: BarrierProtocolClient.ClientInfo(
            x = 0,
            y = 0,
            width = 1080,
            height = 1920,
            cursorX = 540,
            cursorY = 960,
        )
    }

    @Suppress("UNUSED_PARAMETER")
    fun onEnter(x: Int, y: Int, _sequence: Int, modifierMask: Int) {
        clearPendingMoves()
        runOnMain {
            inputController?.onEnter(modifierMask)
            inputController?.onMouseMove(x, y)
        }
    }

    fun onLeave() {
        clearPendingMoves()
        runOnMain {
            inputController?.onLeave()
        }
    }

    fun onMouseMove(x: Int, y: Int) {
        enqueueMove(absoluteX = x, absoluteY = y)
    }

    fun onMouseRelativeMove(dx: Int, dy: Int) {
        enqueueMove(relativeDx = dx, relativeDy = dy)
    }

    fun onMouseWheel(xDelta: Int, yDelta: Int) {
        runOnMain {
            inputController?.onMouseWheel(xDelta, yDelta)
        }
    }

    fun onMouseDown(buttonId: Int) {
        runOnMain {
            inputController?.onMouseDown(buttonId)
        }
    }

    fun onMouseUp(buttonId: Int) {
        runOnMain {
            inputController?.onMouseUp(buttonId)
        }
    }

    fun onKeyDown(keyId: Int, modifierMask: Int, keyButton: Int) {
        runOnMain {
            inputController?.onKeyDown(keyId, modifierMask, keyButton)
        }
    }

    fun onKeyRepeat(keyId: Int, modifierMask: Int, count: Int, keyButton: Int) {
        runOnMain {
            inputController?.onKeyRepeat(keyId, modifierMask, count, keyButton)
        }
    }

    fun onKeyUp(keyId: Int, modifierMask: Int, keyButton: Int) {
        runOnMain {
            inputController?.onKeyUp(keyId, modifierMask, keyButton)
        }
    }

    private fun runOnMain(action: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            action()
        } else {
            mainHandler.post(action)
        }
    }

    private fun enqueueMove(
        absoluteX: Int? = null,
        absoluteY: Int? = null,
        relativeDx: Int? = null,
        relativeDy: Int? = null,
    ) {
        synchronized(moveLock) {
            if (absoluteX != null && absoluteY != null) {
                pendingAbsoluteMove = true
                pendingAbsX = absoluteX
                pendingAbsY = absoluteY
                pendingRelDx = 0
                pendingRelDy = 0
            } else if (relativeDx != null && relativeDy != null) {
                if (pendingAbsoluteMove) {
                    pendingAbsX += relativeDx
                    pendingAbsY += relativeDy
                } else {
                    pendingRelDx += relativeDx
                    pendingRelDy += relativeDy
                }
            }

            if (!moveDispatchScheduled) {
                moveDispatchScheduled = true
                mainHandler.post(moveDispatchRunnable)
            }
        }
    }

    private fun flushPendingMoves() {
        val absoluteMove: Boolean
        val absX: Int
        val absY: Int
        val relDx: Int
        val relDy: Int

        synchronized(moveLock) {
            absoluteMove = pendingAbsoluteMove
            absX = pendingAbsX
            absY = pendingAbsY
            relDx = pendingRelDx
            relDy = pendingRelDy

            pendingAbsoluteMove = false
            pendingRelDx = 0
            pendingRelDy = 0
            moveDispatchScheduled = false
        }

        if (absoluteMove) {
            inputController?.onMouseMove(absX, absY)
        }
        if (relDx != 0 || relDy != 0) {
            inputController?.onMouseRelativeMove(relDx, relDy)
        }
    }

    private fun clearPendingMoves() {
        synchronized(moveLock) {
            pendingAbsoluteMove = false
            pendingRelDx = 0
            pendingRelDy = 0
            moveDispatchScheduled = false
        }
        mainHandler.removeCallbacks(moveDispatchRunnable)
    }

    companion object {
        @Volatile
        private var instance: BarrierAccessibilityService? = null

        fun instance(): BarrierAccessibilityService? = instance

        fun isEnabled(context: Context): Boolean {
            val expectedComponent = ComponentName(context, BarrierAccessibilityService::class.java)
                .flattenToString()
            val enabled = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
            ) ?: return false

            val splitter = TextUtils.SimpleStringSplitter(':')
            splitter.setString(enabled)
            while (splitter.hasNext()) {
                if (splitter.next().equals(expectedComponent, ignoreCase = true)) {
                    return true
                }
            }
            return false
        }
    }
}
