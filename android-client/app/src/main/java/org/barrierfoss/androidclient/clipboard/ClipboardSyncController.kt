package org.barrierfoss.androidclient.clipboard

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.barrierfoss.androidclient.protocol.ClipboardCodec

class ClipboardSyncController(
    context: Context,
    private val scope: CoroutineScope,
    private val onLocalText: (String) -> Unit,
) {
    private val appContext = context.applicationContext
    private val clipboard = appContext.getSystemService(ClipboardManager::class.java)
    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile
    private var ignoreNextChange = false

    @Volatile
    private var lastSentText: String? = null

    private val listener = ClipboardManager.OnPrimaryClipChangedListener {
        handleClipboardChanged()
    }

    fun start() {
        clipboard?.addPrimaryClipChangedListener(listener)
    }

    fun stop() {
        clipboard?.removePrimaryClipChangedListener(listener)
    }

    fun setRemoteClipboardText(text: String) {
        val normalized = ClipboardCodec.normalizeText(text)
        lastSentText = normalized
        ignoreNextChange = true

        if (clipboard == null) {
            ignoreNextChange = false
            return
        }

        mainHandler.post {
            clipboard.setPrimaryClip(ClipData.newPlainText("Barrier", normalized))
        }
    }

    private fun handleClipboardChanged() {
        val manager = clipboard ?: return
        val clip = manager.primaryClip ?: return
        if (clip.itemCount == 0) {
            return
        }

        val item = clip.getItemAt(0)
        val text = item.coerceToText(appContext)?.toString() ?: return
        val normalized = ClipboardCodec.normalizeText(text)

        if (ignoreNextChange) {
            ignoreNextChange = false
            if (normalized == lastSentText) {
                return
            }
        }

        if (normalized == lastSentText) {
            return
        }

        lastSentText = normalized
        scope.launch {
            onLocalText(normalized)
        }
    }
}
