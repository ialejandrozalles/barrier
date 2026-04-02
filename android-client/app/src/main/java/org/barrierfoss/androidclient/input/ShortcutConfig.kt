package org.barrierfoss.androidclient.input

enum class ShortcutAction {
    BACK,
    HOME,
    RECENTS,
    NOTIFICATIONS,
    QUICK_SETTINGS,
    POWER_DIALOG,
    LOCK_SCREEN,
    SCREENSHOT,
}

data class ShortcutBinding(
    val keyId: Int,
    val modifierMask: Int,
) {
    fun matches(keyId: Int, modifierMask: Int): Boolean {
        return this.keyId == keyId && this.modifierMask == modifierMask
    }
}

object ShortcutModifiers {
    const val SHIFT = 0x0001
    const val CONTROL = 0x0002
    const val ALT = 0x0004
    const val META = 0x0008
    const val SUPER = 0x0010
}

object ShortcutDefaults {
    private const val KEY_ESCAPE = 0xEF1B
    private const val KEY_TAB = 0xEF09

    val bindings: Map<ShortcutAction, ShortcutBinding> = linkedMapOf(
        ShortcutAction.BACK to ShortcutBinding(KEY_ESCAPE, 0),
        ShortcutAction.HOME to ShortcutBinding(KEY_ESCAPE, ShortcutModifiers.CONTROL),
        ShortcutAction.RECENTS to ShortcutBinding(KEY_TAB, ShortcutModifiers.ALT),
        ShortcutAction.NOTIFICATIONS to ShortcutBinding('N'.code, ShortcutModifiers.CONTROL),
        ShortcutAction.QUICK_SETTINGS to ShortcutBinding('Q'.code, ShortcutModifiers.CONTROL),
        ShortcutAction.POWER_DIALOG to ShortcutBinding('P'.code, ShortcutModifiers.CONTROL),
        ShortcutAction.LOCK_SCREEN to ShortcutBinding('L'.code, ShortcutModifiers.CONTROL),
        ShortcutAction.SCREENSHOT to ShortcutBinding('S'.code, ShortcutModifiers.CONTROL),
    )
}

data class KeyOption(
    val label: String,
    val keyId: Int,
)

object ShortcutKeyCatalog {
    private const val KEY_BACKSPACE = 0xEF08
    private const val KEY_TAB = 0xEF09
    private const val KEY_RETURN = 0xEF0D
    private const val KEY_ESCAPE = 0xEF1B
    private const val KEY_DELETE = 0xEFFF
    private const val KEY_HOME = 0xEF50
    private const val KEY_END = 0xEF57
    private const val KEY_MENU = 0xEF67

    val options: List<KeyOption> = buildList {
        add(KeyOption("Escape", KEY_ESCAPE))
        add(KeyOption("Tab", KEY_TAB))
        add(KeyOption("Enter", KEY_RETURN))
        add(KeyOption("Backspace", KEY_BACKSPACE))
        add(KeyOption("Delete", KEY_DELETE))
        add(KeyOption("Home", KEY_HOME))
        add(KeyOption("End", KEY_END))
        add(KeyOption("Menu", KEY_MENU))

        for (digit in '0'..'9') {
            add(KeyOption(digit.toString(), digit.code))
        }

        for (letter in 'A'..'Z') {
            add(KeyOption(letter.toString(), letter.code))
        }

        for (f in 1..12) {
            add(KeyOption("F$f", 0xEFBD + f))
        }
    }

    fun indexOfKeyId(keyId: Int): Int {
        val index = options.indexOfFirst { it.keyId == keyId }
        return if (index >= 0) index else 0
    }
}
