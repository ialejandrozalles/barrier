package org.barrierfoss.androidclient.input

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import kotlin.math.max

class CursorOverlay(service: AccessibilityService) {
    private val context: Context = service
    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val cursorSizePx = (24 * context.resources.displayMetrics.density).toInt().coerceAtLeast(16)
    private val cursorView = CursorView(context, cursorSizePx)

    private val params = WindowManager.LayoutParams(
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
        PixelFormat.TRANSLUCENT,
    ).apply {
        gravity = Gravity.TOP or Gravity.START
    }

    private var attached = false

    fun show() {
        if (attached) {
            return
        }
        try {
            windowManager.addView(cursorView, params)
            attached = true
        } catch (_: Exception) {
            // Overlay window could not be created; gesture injection continues without visual cursor.
        }
    }

    fun hide() {
        if (!attached) {
            return
        }
        try {
            windowManager.removeView(cursorView)
        } catch (_: Exception) {
            // Ignore removal errors.
        } finally {
            attached = false
        }
    }

    fun release() {
        hide()
    }

    fun moveTo(x: Float, y: Float) {
        if (!attached) {
            return
        }

        cursorView.setCursorPosition(x, y)
    }

    private class CursorView(context: Context, private val cursorSizePx: Int) : View(context) {
        private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(225, 12, 100, 123)
            style = Paint.Style.FILL
        }

        private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = context.resources.displayMetrics.density * 1.8f
        }

        private var cursorX = 0f
        private var cursorY = 0f

        fun setCursorPosition(x: Float, y: Float) {
            cursorX = x
            cursorY = y
            postInvalidateOnAnimation()
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)

            val radius = cursorSizePx * 0.35f
            val cx = cursorX.coerceIn(radius, max(radius, width - radius))
            val cy = cursorY.coerceIn(radius, max(radius, height - radius))

            canvas.drawCircle(cx, cy, radius, fillPaint)
            canvas.drawCircle(cx, cy, radius, strokePaint)
        }
    }
}
