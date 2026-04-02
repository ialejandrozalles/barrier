package org.barrierfoss.androidclient.input

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Build
import android.os.SystemClock
import android.util.DisplayMetrics
import android.view.WindowManager
import org.barrierfoss.androidclient.data.ShortcutPreferences
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max

class BarrierInputController(private val service: AccessibilityService) {
    private val textInjector = TextInjector(service)
    private val cursorOverlay = CursorOverlay(service)
    private val shortcutPreferences = ShortcutPreferences(service)
    private var shortcutBindings = shortcutPreferences.loadAll()

    private var width = 1
    private var height = 1

    private var entered = false
    private var cursorX = 0f
    private var cursorY = 0f

    private var leftButtonDown = false
    private var pressStartX = 0f
    private var pressStartY = 0f

    private var dragActive = false
    private var lastDragX = 0f
    private var lastDragY = 0f
    private var lastDragDispatch = 0L

    private var rightButtonDown = false
    private var rightPressX = 0f
    private var rightPressY = 0f

    private val keyButtonCommands = HashMap<Int, KeyMapper.KeyCommand>()

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

    fun onEnter(modifierMask: Int, sequence: Int) {
        refreshDisplayInfo()
        shortcutBindings = shortcutPreferences.loadAll()

        if (sequence != 0) {
            leftButtonDown = false
            rightButtonDown = false
            dragActive = false
            keyButtonCommands.clear()
        }

        // If shift is active when entering, keep pointer explicitly visible.
        if ((modifierMask and KeyMapper.MOD_SHIFT) != 0) {
            cursorOverlay.moveTo(cursorX, cursorY)
        }

        entered = true
        cursorOverlay.show()
        cursorOverlay.moveTo(cursorX, cursorY)
    }

    fun onLeave() {
        entered = false
        leftButtonDown = false
        rightButtonDown = false
        dragActive = false
        keyButtonCommands.clear()
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
                pressStartX = cursorX
                pressStartY = cursorY
                lastDragX = cursorX
                lastDragY = cursorY
                dragActive = false
            }

            2 -> performSystemAction(AccessibilityService.GLOBAL_ACTION_HOME)

            3 -> {
                rightButtonDown = true
                rightPressX = cursorX
                rightPressY = cursorY
            }

            4 -> performSystemAction(AccessibilityService.GLOBAL_ACTION_BACK)
            5 -> performSystemAction(AccessibilityService.GLOBAL_ACTION_RECENTS)
        }
    }

    fun onMouseUp(buttonId: Int) {
        if (!entered) {
            return
        }

        if (buttonId == 1) {
            if (!leftButtonDown) {
                return
            }

            if (!dragActive) {
                // Keep left click deterministic as a regular tap.
                dispatchTap(cursorX, cursorY, TAP_DURATION_MS)
            }

            leftButtonDown = false
            dragActive = false
            return
        }

        if (buttonId == 3) {
            if (!rightButtonDown) {
                return
            }

            rightButtonDown = false
            val distance = hypot(
                (cursorX - rightPressX).toDouble(),
                (cursorY - rightPressY).toDouble(),
            )

            if (distance <= RIGHT_CLICK_SLOP_PX) {
                // Right click triggers context action via long press.
                dispatchTap(cursorX, cursorY, LONG_PRESS_MS)
            }
        }
    }

    fun onMouseWheel(xDelta: Int, yDelta: Int) {
        if (!entered) {
            return
        }

        if (xDelta != 0) {
            val xTicks = max(1, abs(xDelta) / 120).coerceAtMost(8)
            repeat(xTicks) {
                dispatchHorizontalScrollStep(if (xDelta > 0) 1 else -1)
            }
        }

        if (yDelta != 0) {
            val yTicks = max(1, abs(yDelta) / 120).coerceAtMost(8)
            repeat(yTicks) {
                dispatchScrollStep(if (yDelta > 0) 1 else -1)
            }
        }
    }

    fun onKeyDown(keyId: Int, modifierMask: Int, keyButton: Int) {
        val command = KeyMapper.mapKeyDown(keyId, modifierMask, shortcutBindings)
        if (keyButton > 0) {
            keyButtonCommands[keyButton] = command
        }
        executeKeyCommand(command)
    }

    fun onKeyRepeat(keyId: Int, modifierMask: Int, count: Int, keyButton: Int) {
        val command =
            if (keyButton > 0) {
                keyButtonCommands[keyButton] ?: KeyMapper.mapKeyDown(keyId, modifierMask, shortcutBindings)
            } else {
                KeyMapper.mapKeyDown(keyId, modifierMask, shortcutBindings)
            }

        repeat(count.coerceAtMost(32)) {
            executeKeyCommand(command)
        }
    }

    @Suppress("UNUSED_PARAMETER")
    fun onKeyUp(keyId: Int, modifierMask: Int, keyButton: Int) {
        if (keyButton > 0) {
            keyButtonCommands.remove(keyButton)
        }
    }

    private fun executeKeyCommand(command: KeyMapper.KeyCommand) {
        when (command) {
            is KeyMapper.KeyCommand.CommitText -> textInjector.commitText(command.value)
            KeyMapper.KeyCommand.Backspace -> textInjector.backspace()
            KeyMapper.KeyCommand.Enter -> textInjector.commitText("\n")
            KeyMapper.KeyCommand.Tab -> textInjector.commitText("\t")
            is KeyMapper.KeyCommand.MoveCursor -> textInjector.moveCursor(command.delta)
            KeyMapper.KeyCommand.Home -> {
                if (!textInjector.moveToStart()) {
                    performSystemAction(AccessibilityService.GLOBAL_ACTION_HOME)
                }
            }
            KeyMapper.KeyCommand.End -> {
                if (!textInjector.moveToEnd()) {
                    performSystemAction(AccessibilityService.GLOBAL_ACTION_RECENTS)
                }
            }
            KeyMapper.KeyCommand.NavigateBack ->
                performSystemAction(AccessibilityService.GLOBAL_ACTION_BACK)
            KeyMapper.KeyCommand.NavigateHome ->
                performSystemAction(AccessibilityService.GLOBAL_ACTION_HOME)
            KeyMapper.KeyCommand.ShowRecents ->
                performSystemAction(AccessibilityService.GLOBAL_ACTION_RECENTS)
            KeyMapper.KeyCommand.ShowNotifications ->
                performSystemAction(AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS)
            KeyMapper.KeyCommand.ShowQuickSettings ->
                performSystemAction(AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS)
            KeyMapper.KeyCommand.ShowPowerDialog -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    performSystemAction(AccessibilityService.GLOBAL_ACTION_POWER_DIALOG)
                }
            }
            KeyMapper.KeyCommand.LockScreen -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    performSystemAction(AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN)
                }
            }
            KeyMapper.KeyCommand.TakeScreenshot -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    performSystemAction(AccessibilityService.GLOBAL_ACTION_TAKE_SCREENSHOT)
                }
            }
            KeyMapper.KeyCommand.Copy -> textInjector.copy()
            KeyMapper.KeyCommand.Paste -> textInjector.paste()
            KeyMapper.KeyCommand.Cut -> textInjector.cut()
            KeyMapper.KeyCommand.SelectAll -> textInjector.selectAll()
            is KeyMapper.KeyCommand.ScrollByPage -> dispatchScrollStep(command.direction)
            KeyMapper.KeyCommand.None -> Unit
        }
    }

    private fun refreshDisplayInfo() {
        val (displayWidth, displayHeight) = getRealDisplaySize()
        width = displayWidth.coerceAtLeast(1)
        height = displayHeight.coerceAtLeast(1)
        cursorX = cursorX.coerceIn(0f, (width - 1).toFloat())
        cursorY = cursorY.coerceIn(0f, (height - 1).toFloat())
    }

    private fun getRealDisplaySize(): Pair<Int, Int> {
        val wm = service.getSystemService(WindowManager::class.java)
        if (wm != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val bounds = wm.maximumWindowMetrics.bounds
                return bounds.width() to bounds.height()
            }

            @Suppress("DEPRECATION")
            val display = wm.defaultDisplay
            if (display != null) {
                val metrics = DisplayMetrics()
                @Suppress("DEPRECATION")
                display.getRealMetrics(metrics)
                return metrics.widthPixels to metrics.heightPixels
            }
        }

        val fallback = service.resources.displayMetrics
        return fallback.widthPixels to fallback.heightPixels
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
        val distance = (height * 0.10f).coerceIn(70f, 240f)
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

    private fun dispatchHorizontalScrollStep(direction: Int) {
        val distance = (width * 0.10f).coerceIn(70f, 240f)
        val startY = cursorY
        val startX: Float
        val endX: Float

        if (direction >= 0) {
            startX = (cursorX + distance / 2f).coerceIn(0f, (width - 1).toFloat())
            endX = (cursorX - distance / 2f).coerceIn(0f, (width - 1).toFloat())
        } else {
            startX = (cursorX - distance / 2f).coerceIn(0f, (width - 1).toFloat())
            endX = (cursorX + distance / 2f).coerceIn(0f, (width - 1).toFloat())
        }

        val path = Path().apply {
            moveTo(startX, startY)
            lineTo(endX, startY)
        }

        val stroke = GestureDescription.StrokeDescription(path, 0, SCROLL_GESTURE_MS)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        service.dispatchGesture(gesture, null, null)
    }

    private fun performSystemAction(action: Int) {
        service.performGlobalAction(action)
    }

    private companion object {
        const val TAP_DURATION_MS = 45L
        const val LONG_PRESS_MS = 550L
        const val MAX_GESTURE_MS = 1500L
        const val DRAG_GESTURE_MS = 12L
        const val SCROLL_GESTURE_MS = 90L
        const val DRAG_MIN_INTERVAL_MS = 8L
        const val DRAG_THRESHOLD_PX = 8f
        const val RIGHT_CLICK_SLOP_PX = 14f
    }
}
