package org.barrierfoss.androidclient.input

import android.accessibilityservice.AccessibilityService
import android.os.Bundle
import android.view.accessibility.AccessibilityNodeInfo
import kotlin.math.max

class TextInjector(private val service: AccessibilityService) {
    fun commitText(text: String): Boolean {
        if (text.isEmpty()) {
            return false
        }

        val node = focusedEditableNode() ?: return false
        val current = node.text?.toString().orEmpty()
        val selectionStart = normalizeIndex(node.textSelectionStart, current.length)
        val selectionEnd = normalizeIndex(node.textSelectionEnd, current.length)

        val start = minOf(selectionStart, selectionEnd)
        val end = maxOf(selectionStart, selectionEnd)

        val updated = buildString {
            append(current.substring(0, start))
            append(text)
            append(current.substring(end))
        }

        val newCursor = start + text.length
        val updatedOk = setNodeText(node, updated)
        if (updatedOk) {
            setSelection(node, newCursor)
        }
        return updatedOk
    }

    fun backspace(): Boolean {
        val node = focusedEditableNode() ?: return false
        val current = node.text?.toString().orEmpty()
        if (current.isEmpty()) {
            return false
        }

        val selectionStart = normalizeIndex(node.textSelectionStart, current.length)
        val selectionEnd = normalizeIndex(node.textSelectionEnd, current.length)

        val start = minOf(selectionStart, selectionEnd)
        val end = maxOf(selectionStart, selectionEnd)

        if (start == 0 && end == 0) {
            return false
        }

        val deleteFrom = if (start == end) start - 1 else start
        val deleteTo = if (start == end) end else end

        val updated = buildString {
            append(current.substring(0, deleteFrom))
            append(current.substring(deleteTo))
        }

        val updatedOk = setNodeText(node, updated)
        if (updatedOk) {
            setSelection(node, deleteFrom)
        }
        return updatedOk
    }

    fun moveCursor(delta: Int): Boolean {
        val node = focusedEditableNode() ?: return false
        val current = node.text?.toString().orEmpty()
        val selectionStart = normalizeIndex(node.textSelectionStart, current.length)
        val next = (selectionStart + delta).coerceIn(0, current.length)
        return setSelection(node, next)
    }

    fun moveToStart(): Boolean {
        val node = focusedEditableNode() ?: return false
        return setSelection(node, 0)
    }

    fun moveToEnd(): Boolean {
        val node = focusedEditableNode() ?: return false
        val end = node.text?.length ?: 0
        return setSelection(node, end)
    }

    fun copy(): Boolean = performTextAction(AccessibilityNodeInfo.ACTION_COPY)

    fun paste(): Boolean = performTextAction(AccessibilityNodeInfo.ACTION_PASTE)

    fun cut(): Boolean = performTextAction(AccessibilityNodeInfo.ACTION_CUT)

    fun selectAll(): Boolean {
        val node = focusedEditableNode() ?: return false
        val textLength = node.text?.length ?: 0
        if (textLength <= 0) {
            return false
        }

        val args = Bundle().apply {
            putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, 0)
            putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, textLength)
        }

        return node.performAction(AccessibilityNodeInfo.ACTION_SET_SELECTION, args) ||
            node.performAction(AccessibilityNodeInfo.ACTION_SELECT)
    }

    private fun performTextAction(action: Int): Boolean {
        val node = focusedEditableNode() ?: return false
        return node.performAction(action)
    }

    private fun setNodeText(node: AccessibilityNodeInfo, value: String): Boolean {
        val args = Bundle().apply {
            putCharSequence(
                AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                value,
            )
        }
        return node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
    }

    private fun setSelection(node: AccessibilityNodeInfo, index: Int): Boolean {
        val args = Bundle().apply {
            putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, index)
            putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, index)
        }
        return node.performAction(AccessibilityNodeInfo.ACTION_SET_SELECTION, args)
    }

    private fun focusedEditableNode(): AccessibilityNodeInfo? {
        val root = service.rootInActiveWindow ?: return null

        root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)?.let { focused ->
            if (focused.isEditable) {
                return focused
            }
        }

        return findFirstEditable(root)
    }

    private fun findFirstEditable(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (node.isEditable) {
            return node
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val result = findFirstEditable(child)
            if (result != null) {
                return result
            }
        }

        return null
    }

    private fun normalizeIndex(index: Int, maxValue: Int): Int {
        if (index < 0) {
            return maxValue
        }
        return index.coerceIn(0, maxValue)
    }
}
