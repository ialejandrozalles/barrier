package org.barrierfoss.androidclient.data

import android.content.Context
import org.barrierfoss.androidclient.input.ShortcutAction
import org.barrierfoss.androidclient.input.ShortcutBinding
import org.barrierfoss.androidclient.input.ShortcutDefaults

class ShortcutPreferences(context: Context) {
    private val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    fun loadAll(): Map<ShortcutAction, ShortcutBinding> {
        val defaults = ShortcutDefaults.bindings
        val result = LinkedHashMap<ShortcutAction, ShortcutBinding>()

        for (action in ShortcutAction.values()) {
            val defaultBinding = defaults[action] ?: continue
            val keyId = prefs.getInt(keyKey(action), defaultBinding.keyId)
            val modifierMask = prefs.getInt(modifierKey(action), defaultBinding.modifierMask)
            result[action] = ShortcutBinding(keyId = keyId, modifierMask = modifierMask)
        }

        return result
    }

    fun saveAll(bindings: Map<ShortcutAction, ShortcutBinding>) {
        val editor = prefs.edit()
        for (action in ShortcutAction.values()) {
            val binding = bindings[action] ?: continue
            editor.putInt(keyKey(action), binding.keyId)
            editor.putInt(modifierKey(action), binding.modifierMask)
        }
        editor.apply()
    }

    fun resetDefaults() {
        saveAll(ShortcutDefaults.bindings)
    }

    private fun keyKey(action: ShortcutAction): String = "${action.name}_key"

    private fun modifierKey(action: ShortcutAction): String = "${action.name}_mod"

    private companion object {
        const val PREF_NAME = "barrier_android_shortcuts"
    }
}
