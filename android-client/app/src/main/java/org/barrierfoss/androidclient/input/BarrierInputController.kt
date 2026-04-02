package org.barrierfoss.androidclient.input

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.SystemClock
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max

class BarrierInputController(private val service: AccessibilityService) {
    private val textInjector = TextInjector(service)
    private val cursorOverlay = CursorOverlay(service)

    private var width = 1
    private var height = 1

    private var entered = false
    private var cursorX = 0f
    private var cursorY = 0f

    private var leftButtonDown = false
    private var pressStartTime = 0L
    private var pressStartX = 0f
    private var pressStartY = 0f

    private var dragActive = false
    private var lastDragX = 0f
    private var lastDragY = 0f
    private var lastDragDispatch = 0L

    init {
        refreshDisplayInfo()
        cursorX = width * 0.5f
        cursorY = height * 0.5f
    }

    fun release() {
        cursorOverlay.release()
    }

    fun clientInfo() = org.barrierfoss.androidclient.protocol.BarrierProtocolClient.ClientInfo(
        x = 0,
        y = 0,
        width = width,
        height = height,
        cursorX = cursorX.toInt(),
        cursorY = cursorY.toInt(),
    )

    fun onEnter(modifierMask: Int) {
        refreshDisplayInfo()
        entered = true
        cursorOverlay.show()
        cursorOverlay.moveTo(cursorX, cursorY)
    }

    fun onLeave() {
        entered = false
        leftButtonDown = false
        dragActive = false
        cursorOverlay.hide()
    }

    fun onMouseMove(x: Int, y: Int) {
        if (!entered) {
            return
        }

        setCursor(
            x.toFloat().coerceIn(0f, (width - 1).toFloat()),
            y.toFloat().coerceIn(0f, (height - 1).toFloat()),
        )

        if (leftButtonDown) {
            dispatchDragIfNeeded()
        }
    }

    fun onMouseRelativeMove(dx: Int, dy: Int) {
        if (!entered) {
            return
        }

        setCursor(
            (cursorX + dx).coerceIn(0f, (width - 1).toFloat()),
            (cursorY + dy).coerceIn(0f, (height - 1).toFloat()),
        )

        if (leftButtonDown) {
            dispatchDragIfNeeded()
        }
    }

    fun onMouseDown(buttonId: Int) {
        if (!entered) {
            return
        }

        when (buttonId) {
            1 -> {
                leftButtonDown = true
                pressStartTime = SystemClock.uptimeMillis()
                pressStartX = cursorX
                pressStartY = cursorY
                lastDragX = cursorX
                lastDragY = cursorY
                dragActive = false
            }

            3 -> dispatchTap(cursorX, cursorY, LONG_PRESS_MS)
            2 -> dispatchTap(cursorX, cursorY, TAP_DURATION_MS)
        }
    }

    fun onMouseUp(buttonId: Int) {
        if (!entered) {
            return
        }

        if (buttonId != 1 || !leftButtonDown) {
            return
        }

        val duration = (SystemClock.uptimeMillis() - pressStartTime)
            .coerceAtLeast(TAP_DURATION_MS)
            .coerceAtMost(MAX_GESTURE_MS)

        if (!dragActive) {
            dispatchTap(cursorX, cursorY, duration)
        }

        leftButtonDown = false
        dragActive = false
    }

    fun onMouseWheel(xDelta: Int, yDelta: Int) {
        if (!entered || yDelta == 0) {
            return
        }

        val ticks = max(1, abs(yDelta) / 120).coerceAtMost(8)
        repeat(ticks) {
            dispatchScrollStep(if (yDelta > 0) 1 else -1)
        }
    }

    fun onKeyDown(keyId: Int, modifierMask: Int, keyButton: Int) {
        executeKeyCommand(KeyMapper.mapKeyDown(keyId, modifierMask))
    }

    fun onKeyRepeat(keyId: Int, modifierMask: Int, count: Int, keyButton: Int) {
        repeat(count.coerceAtMost(32)) {
            executeKeyCommand(KeyMapper.mapKeyDown(keyId, modifierMask))
        }
    }

    fun onKeyUp(keyId: Int, modifierMask: Int, keyButton: Int) {
        // Intentionally ignored for text-oriented Android input mapping.
    }

    private fun executeKeyCommand(command: KeyMapper.KeyCommand) {
        when (command) {
            is KeyMapper.KeyCommand.CommitText -> textInjector.commitText(command.value)
            KeyMapper.KeyCommand.Backspace -> textInjector.backspace()
            KeyMapper.KeyCommand.Enter -> textInjector.commitText("\n")
            KeyMapper.KeyCommand.Tab -> textInjector.commitText("\t")
            is KeyMapper.KeyCommand.MoveCursor -> textInjector.moveCursor(command.delta)
            KeyMapper.KeyCommand.Home -> textInjector.moveToStart()
            KeyMapper.KeyCommand.End -> textInjector.moveToEnd()
            KeyMapper.KeyCommand.NavigateBack -> service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
            KeyMapper.KeyCommand.Copy -> textInjector.copy()
            KeyMapper.KeyCommand.Paste -> textInjector.paste()
            KeyMapper.KeyCommand.Cut -> textInjector.cut()
            KeyMapper.KeyCommand.SelectAll -> textInjector.selectAll()
            is KeyMapper.KeyCommand.ScrollByPage -> dispatchScrollStep(command.direction)
            KeyMapper.KeyCommand.None -> Unit
        }
    }

    private fun refreshDisplayInfo() {
        val dm = service.resources.displayMetrics
        width = dm.widthPixels.coerceAtLeast(1)
        height = dm.heightPixels.coerceAtLeast(1)
        cursorX = cursorX.coerceIn(0f, (width - 1).toFloat())
        cursorY = cursorY.coerceIn(0f, (height - 1).toFloat())
    }

    private fun setCursor(x: Float, y: Float) {
        cursorX = x
        cursorY = y
        cursorOverlay.moveTo(cursorX, cursorY)
    }

    private fun dispatchDragIfNeeded() {
        val now = SystemClock.uptimeMillis()

        val distance = hypot(
            (cursorX - pressStartX).toDouble(),
            (cursorY - pressStartY).toDouble(),
        )

        if (!dragActive && distance >= DRAG_THRESHOLD_PX) {
            dragActive = true
            lastDragX = pressStartX
            lastDragY = pressStartY
            lastDragDispatch = 0L
        }

        if (!dragActive) {
            return
        }

        if (now - lastDragDispatch < DRAG_MIN_INTERVAL_MS) {
            return
        }

        dispatchDrag(lastDragX, lastDragY, cursorX, cursorY)
        lastDragX = cursorX
        lastDragY = cursorY
        lastDragDispatch = now
    }

    private fun dispatchTap(x: Float, y: Float, durationMs: Long) {
        val path = Path().apply {
            moveTo(x, y)
        }
        val stroke = GestureDescription.StrokeDescription(
            path,
            0,
            durationMs.coerceIn(1L, MAX_GESTURE_MS),
        )
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        service.dispatchGesture(gesture, null, null)
    }

    private fun dispatchDrag(fromX: Float, fromY: Float, toX: Float, toY: Float) {
        val path = Path().apply {
            moveTo(fromX, fromY)
            lineTo(toX, toY)
        }
        val stroke = GestureDescription.StrokeDescription(path, 0, DRAG_GESTURE_MS)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        service.dispatchGesture(gesture, null, null)
    }

    private fun dispatchScrollStep(direction: Int) {
        val distance = (height * 0.13f).coerceIn(90f, 320f)
        val startX = cursorX
        val startY: Float
        val endY: Float

        if (direction >= 0) {
            startY = (cursorY - distance / 2f).coerceIn(0f, (height - 1).toFloat())
            endY = (cursorY + distance / 2f).coerceIn(0f, (height - 1).toFloat())
        } else {
            startY = (cursorY + distance / 2f).coerceIn(0f, (height - 1).toFloat())
            endY = (cursorY - distance / 2f).coerceIn(0f, (height - 1).toFloat())
        }

        val path = Path().apply {
            moveTo(startX, startY)
            lineTo(startX, endY)
        }

        val stroke = GestureDescription.StrokeDescription(path, 0, SCROLL_GESTURE_MS)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        service.dispatchGesture(gesture, null, null)
    }

    private companion object {
        const val TAP_DURATION_MS = 60L
        const val LONG_PRESS_MS = 650L
        const val MAX_GESTURE_MS = 1500L
        const val DRAG_GESTURE_MS = 18L
        const val SCROLL_GESTURE_MS = 120L
        const val DRAG_MIN_INTERVAL_MS = 12L
        const val DRAG_THRESHOLD_PX = 12f
    }
}
