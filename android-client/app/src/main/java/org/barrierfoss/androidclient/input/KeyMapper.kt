package org.barrierfoss.androidclient.input

object KeyMapper {
    const val MOD_SHIFT = 0x0001
    const val MOD_CONTROL = 0x0002

    private const val KEY_BACKSPACE = 0xEF08
    private const val KEY_TAB = 0xEF09
    private const val KEY_RETURN = 0xEF0D
    private const val KEY_ESCAPE = 0xEF1B
    private const val KEY_DELETE = 0xEFFF

    private const val KEY_LEFT = 0xEF51
    private const val KEY_UP = 0xEF52
    private const val KEY_RIGHT = 0xEF53
    private const val KEY_DOWN = 0xEF54
    private const val KEY_PAGE_UP = 0xEF55
    private const val KEY_PAGE_DOWN = 0xEF56
    private const val KEY_HOME = 0xEF50
    private const val KEY_END = 0xEF57

    private const val KEY_KP_ENTER = 0xEF8D

    private const val KEY_KP_0 = 0xEFB0
    private const val KEY_KP_9 = 0xEFB9

    sealed interface KeyCommand {
        data class CommitText(val value: String) : KeyCommand
        data class MoveCursor(val delta: Int) : KeyCommand
        data class ScrollByPage(val direction: Int) : KeyCommand

        data object Backspace : KeyCommand
        data object Enter : KeyCommand
        data object Tab : KeyCommand
        data object Home : KeyCommand
        data object End : KeyCommand
        data object NavigateBack : KeyCommand
        data object Copy : KeyCommand
        data object Paste : KeyCommand
        data object Cut : KeyCommand
        data object SelectAll : KeyCommand
        data object None : KeyCommand
    }

    fun mapKeyDown(keyId: Int, modifierMask: Int): KeyCommand {
        val ctrl = modifierMask and MOD_CONTROL != 0

        val printable = toPrintableChar(keyId, modifierMask)
        if (printable != null) {
            if (ctrl) {
                return when (printable.lowercaseChar()) {
                    'c' -> KeyCommand.Copy
                    'v' -> KeyCommand.Paste
                    'x' -> KeyCommand.Cut
                    'a' -> KeyCommand.SelectAll
                    else -> KeyCommand.None
                }
            }
            return KeyCommand.CommitText(printable.toString())
        }

        return when (keyId) {
            KEY_BACKSPACE, KEY_DELETE -> KeyCommand.Backspace
            KEY_TAB -> KeyCommand.Tab
            KEY_RETURN, KEY_KP_ENTER -> KeyCommand.Enter
            KEY_ESCAPE -> KeyCommand.NavigateBack
            KEY_LEFT -> KeyCommand.MoveCursor(-1)
            KEY_RIGHT -> KeyCommand.MoveCursor(1)
            KEY_UP -> KeyCommand.MoveCursor(-1)
            KEY_DOWN -> KeyCommand.MoveCursor(1)
            KEY_HOME -> KeyCommand.Home
            KEY_END -> KeyCommand.End
            KEY_PAGE_UP -> KeyCommand.ScrollByPage(-1)
            KEY_PAGE_DOWN -> KeyCommand.ScrollByPage(1)
            else -> KeyCommand.None
        }
    }

    private fun toPrintableChar(keyId: Int, modifierMask: Int): Char? {
        val shift = modifierMask and MOD_SHIFT != 0

        if (keyId in 0x20..0x7E) {
            val c = keyId.toChar()
            return applyShift(c, shift)
        }

        if (keyId in KEY_KP_0..KEY_KP_9) {
            val digit = (keyId - KEY_KP_0) + '0'.code
            return digit.toChar()
        }

        return null
    }

    private fun applyShift(c: Char, shift: Boolean): Char {
        if (!shift) {
            return c
        }

        if (c in 'a'..'z') {
            return c.uppercaseChar()
        }

        return SHIFTED_SYMBOLS[c] ?: c
    }

    private val SHIFTED_SYMBOLS = mapOf(
        '1' to '!',
        '2' to '@',
        '3' to '#',
        '4' to '$',
        '5' to '%',
        '6' to '^',
        '7' to '&',
        '8' to '*',
        '9' to '(',
        '0' to ')',
        '-' to '_',
        '=' to '+',
        '[' to '{',
        ']' to '}',
        '\\' to '|',
        ';' to ':',
        '\'' to '"',
        ',' to '<',
        '.' to '>',
        '/' to '?',
        '`' to '~',
    )
}
