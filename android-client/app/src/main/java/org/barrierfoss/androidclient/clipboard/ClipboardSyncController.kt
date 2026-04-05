package org.barrierfoss.androidclient.clipboard

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.text.Html
import androidx.core.content.FileProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.barrierfoss.androidclient.protocol.ClipboardCodec
import org.barrierfoss.androidclient.protocol.ClipboardPayload
import java.io.File
import java.io.FileOutputStream
import java.util.zip.CRC32

class ClipboardSyncController(
    context: Context,
    private val scope: CoroutineScope,
    private val onLocalClipboard: (ClipboardPayload) -> Unit,
) {
    private val appContext = context.applicationContext
    private val clipboard = appContext.getSystemService(ClipboardManager::class.java)
    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile
    private var ignoreNextChange = false

    @Volatile
    private var lastSentSignature: String? = null

    private val listener = ClipboardManager.OnPrimaryClipChangedListener {
        handleClipboardChanged()
    }

    fun start() {
        clipboard?.addPrimaryClipChangedListener(listener)
    }

    fun stop() {
        clipboard?.removePrimaryClipChangedListener(listener)
    }

    fun setRemoteClipboard(payload: ClipboardPayload) {
        val signature = signature(payload)
        lastSentSignature = signature
        ignoreNextChange = true

        if (clipboard == null) {
            ignoreNextChange = false
            return
        }

        mainHandler.post {
            val clip = buildClipData(payload)
            if (clip == null) {
                ignoreNextChange = false
                return@post
            }
            clipboard.setPrimaryClip(clip)
        }
    }

    private fun handleClipboardChanged() {
        val manager = clipboard ?: return
        val clip = manager.primaryClip ?: return
        if (clip.itemCount == 0) {
            return
        }

        val description = clip.description
        val item = clip.getItemAt(0)
        val htmlText = if (hasHtml) {
            item.coerceToHtmlText(appContext)?.toString() ?: item.htmlText
        } else {
            item.htmlText
        }
        val hasHtml = htmlText != null || description.hasMimeType(ClipDescription.MIMETYPE_TEXT_HTML)
        val hasText = description.hasMimeType(ClipDescription.MIMETYPE_TEXT_PLAIN) || hasHtml

        val rawText = if (hasText) item.coerceToText(appContext)?.toString() else null
        val normalizedText = rawText?.let { ClipboardCodec.normalizeText(it) }?.takeIf { it.isNotBlank() }
        val normalizedHtml = htmlText?.let { ClipboardCodec.normalizeText(it) }?.takeIf { it.isNotBlank() }

        val textFallback = if (normalizedText.isNullOrBlank() && !normalizedHtml.isNullOrBlank()) {
            Html.fromHtml(normalizedHtml, Html.FROM_HTML_MODE_LEGACY).toString()
        } else {
            normalizedText
        }

        val uri = item.uri ?: item.intent?.data

        scope.launch {
            val bitmapBytes = if (uri != null && isImageMime(description)) {
                loadBitmapBytes(uri)
            } else {
                null
            }

            val payload = ClipboardPayload(
                text = textFallback?.takeIf { it.isNotBlank() },
                html = normalizedHtml,
                bitmap = bitmapBytes,
            )

            if (isPayloadEmpty(payload)) {
                return@launch
            }

            val signature = signature(payload)
            if (ignoreNextChange) {
                ignoreNextChange = false
                if (signature == lastSentSignature) {
                    return@launch
                }
            }

            if (signature == lastSentSignature) {
                return@launch
            }

            lastSentSignature = signature
            onLocalClipboard(payload)
        }
    }

    private fun buildClipData(payload: ClipboardPayload): ClipData? {
        val text = payload.text?.let { ClipboardCodec.normalizeText(it) }
        val html = payload.html?.let { ClipboardCodec.normalizeText(it) }
        val bitmap = payload.bitmap?.let { BitmapDibCodec.decode(it) }
        val uri = bitmap?.let { storeBitmap(it) }

        if (uri != null) {
            val mimeTypes = ArrayList<String>(3)
            if (!text.isNullOrBlank()) {
                mimeTypes += ClipDescription.MIMETYPE_TEXT_PLAIN
            }
            if (!html.isNullOrBlank()) {
                mimeTypes += ClipDescription.MIMETYPE_TEXT_HTML
            }
            mimeTypes += ClipDescription.MIMETYPE_TEXT_URILIST
            mimeTypes += "image/png"

            val itemText = text
                ?: html?.let { Html.fromHtml(it, Html.FROM_HTML_MODE_LEGACY).toString() }
                ?: ""
            val item = ClipData.Item(itemText, html, null, uri)
            val description = ClipDescription("Barrier", mimeTypes.toTypedArray())
            return ClipData(description, item)
        }

        if (!html.isNullOrBlank()) {
            val itemText = text ?: Html.fromHtml(html, Html.FROM_HTML_MODE_LEGACY).toString()
            return ClipData.newHtmlText("Barrier", itemText, html)
        }

        if (!text.isNullOrBlank()) {
            return ClipData.newPlainText("Barrier", text)
        }

        return null
    }

    private fun loadBitmapBytes(uri: Uri): ByteArray? {
        val resolver = appContext.contentResolver
        val bitmap = resolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream)
        } ?: return null

        return BitmapDibCodec.encode(bitmap)
    }

    private fun storeBitmap(bitmap: Bitmap): Uri? {
        val cacheDir = File(appContext.cacheDir, "clipboard")
        if (!cacheDir.exists() && !cacheDir.mkdirs()) {
            return null
        }

        val file = File(cacheDir, "barrier_clipboard.png")
        FileOutputStream(file).use { output ->
            if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) {
                return null
            }
        }

        val authority = "${appContext.packageName}.fileprovider"
        return FileProvider.getUriForFile(appContext, authority, file)
    }

    private fun isImageMime(description: ClipDescription): Boolean {
        return description.hasMimeType("image/*") ||
            description.hasMimeType(ClipDescription.MIMETYPE_TEXT_URILIST)
    }

    private fun isPayloadEmpty(payload: ClipboardPayload): Boolean {
        return payload.text.isNullOrBlank() &&
            payload.html.isNullOrBlank() &&
            payload.bitmap == null
    }

    private fun signature(payload: ClipboardPayload): String {
        val textHash = payload.text?.hashCode() ?: 0
        val htmlHash = payload.html?.hashCode() ?: 0
        val bitmapHash = payload.bitmap?.let { crc32(it) } ?: 0L
        val bitmapSize = payload.bitmap?.size ?: 0
        return "$textHash:$htmlHash:$bitmapSize:$bitmapHash"
    }

    private fun crc32(bytes: ByteArray): Long {
        val crc = CRC32()
        crc.update(bytes)
        return crc.value
    }
}
