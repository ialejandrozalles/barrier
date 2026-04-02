package org.barrierfoss.androidclient

import android.os.Bundle
import android.view.LayoutInflater
import android.widget.ArrayAdapter
import android.widget.CheckBox
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import org.barrierfoss.androidclient.data.ShortcutPreferences
import org.barrierfoss.androidclient.databinding.ActivityShortcutSettingsBinding
import org.barrierfoss.androidclient.input.KeyOption
import org.barrierfoss.androidclient.input.ShortcutAction
import org.barrierfoss.androidclient.input.ShortcutBinding
import org.barrierfoss.androidclient.input.ShortcutKeyCatalog
import org.barrierfoss.androidclient.input.ShortcutModifiers

class ShortcutSettingsActivity : AppCompatActivity() {
    private lateinit var binding: ActivityShortcutSettingsBinding
    private lateinit var shortcutPreferences: ShortcutPreferences

    private val keyOptions: List<KeyOption> = ShortcutKeyCatalog.options
    private val rows = LinkedHashMap<ShortcutAction, ShortcutRow>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityShortcutSettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        shortcutPreferences = ShortcutPreferences(this)

        binding.settingsToolbar.setNavigationOnClickListener { finish() }

        buildRows()
        renderBindings(shortcutPreferences.loadAll())

        binding.saveShortcutsButton.setOnClickListener {
            shortcutPreferences.saveAll(collectBindingsFromUi())
            Toast.makeText(this, getString(R.string.shortcut_saved), Toast.LENGTH_SHORT).show()
        }

        binding.resetDefaultsButton.setOnClickListener {
            shortcutPreferences.resetDefaults()
            renderBindings(shortcutPreferences.loadAll())
            Toast.makeText(this, getString(R.string.shortcut_reset_done), Toast.LENGTH_SHORT).show()
        }
    }

    private fun buildRows() {
        val keyLabels = keyOptions.map { it.label }
        val keyAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, keyLabels).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }

        for (action in ShortcutAction.values()) {
            val rowView = LayoutInflater.from(this)
                .inflate(R.layout.item_shortcut_binding, binding.shortcutContainer, false)

            val row = ShortcutRow(
                actionTitle = rowView.findViewById(R.id.shortcutActionTitle),
                keySpinner = rowView.findViewById(R.id.shortcutKeySpinner),
                ctrlCheck = rowView.findViewById(R.id.modCtrlCheck),
                altCheck = rowView.findViewById(R.id.modAltCheck),
                shiftCheck = rowView.findViewById(R.id.modShiftCheck),
                metaCheck = rowView.findViewById(R.id.modMetaCheck),
                superCheck = rowView.findViewById(R.id.modSuperCheck),
            )

            row.actionTitle.text = actionLabel(action)
            row.keySpinner.adapter = keyAdapter
            binding.shortcutContainer.addView(rowView)
            rows[action] = row
        }
    }

    private fun renderBindings(bindings: Map<ShortcutAction, ShortcutBinding>) {
        for (action in ShortcutAction.values()) {
            val row = rows[action] ?: continue
            val binding = bindings[action] ?: continue

            row.keySpinner.setSelection(ShortcutKeyCatalog.indexOfKeyId(binding.keyId))
            row.ctrlCheck.isChecked = binding.modifierMask and ShortcutModifiers.CONTROL != 0
            row.altCheck.isChecked = binding.modifierMask and ShortcutModifiers.ALT != 0
            row.shiftCheck.isChecked = binding.modifierMask and ShortcutModifiers.SHIFT != 0
            row.metaCheck.isChecked = binding.modifierMask and ShortcutModifiers.META != 0
            row.superCheck.isChecked = binding.modifierMask and ShortcutModifiers.SUPER != 0
        }
    }

    private fun collectBindingsFromUi(): Map<ShortcutAction, ShortcutBinding> {
        val bindings = LinkedHashMap<ShortcutAction, ShortcutBinding>()

        for (action in ShortcutAction.values()) {
            val row = rows[action] ?: continue
            val selectedOption = keyOptions[row.keySpinner.selectedItemPosition]

            var mask = 0
            if (row.ctrlCheck.isChecked) mask = mask or ShortcutModifiers.CONTROL
            if (row.altCheck.isChecked) mask = mask or ShortcutModifiers.ALT
            if (row.shiftCheck.isChecked) mask = mask or ShortcutModifiers.SHIFT
            if (row.metaCheck.isChecked) mask = mask or ShortcutModifiers.META
            if (row.superCheck.isChecked) mask = mask or ShortcutModifiers.SUPER

            bindings[action] = ShortcutBinding(
                keyId = selectedOption.keyId,
                modifierMask = mask,
            )
        }

        return bindings
    }

    private fun actionLabel(action: ShortcutAction): String {
        return when (action) {
            ShortcutAction.BACK -> getString(R.string.shortcut_action_back)
            ShortcutAction.HOME -> getString(R.string.shortcut_action_home)
            ShortcutAction.RECENTS -> getString(R.string.shortcut_action_recents)
            ShortcutAction.NOTIFICATIONS -> getString(R.string.shortcut_action_notifications)
            ShortcutAction.QUICK_SETTINGS -> getString(R.string.shortcut_action_quick_settings)
            ShortcutAction.POWER_DIALOG -> getString(R.string.shortcut_action_power)
            ShortcutAction.LOCK_SCREEN -> getString(R.string.shortcut_action_lock)
            ShortcutAction.SCREENSHOT -> getString(R.string.shortcut_action_screenshot)
        }
    }

    private data class ShortcutRow(
        val actionTitle: TextView,
        val keySpinner: Spinner,
        val ctrlCheck: CheckBox,
        val altCheck: CheckBox,
        val shiftCheck: CheckBox,
        val metaCheck: CheckBox,
        val superCheck: CheckBox,
    )
}
