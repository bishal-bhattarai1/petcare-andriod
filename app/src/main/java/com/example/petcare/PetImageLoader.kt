package com.example.petcare

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import android.widget.ImageView
import java.util.concurrent.Executors
import kotlin.math.max

/**
 * Loads photo URIs downsampled to the size they're shown at, off the main thread, with a small
 * memory cache. `ImageView.setImageURI` decodes full camera-resolution images on the UI thread,
 * which stutters and can run out of memory with several photos.
 */
object PetImageLoader {
    private val executor = Executors.newFixedThreadPool(2)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val cache = object : LruCache<String, Bitmap>((Runtime.getRuntime().maxMemory() / 16).toInt()) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }

    /**
     * Shows [uri] in [view], decoded to about [sizePx] on its longest side.
     * [onError] runs on the main thread if the image can't be read (e.g. it was deleted).
     */
    fun load(view: ImageView, uri: String, sizePx: Int, onError: (() -> Unit)? = null) {
        val key = "$uri@$sizePx"
        view.tag = key
        cache.get(key)?.let {
            view.setImageBitmap(it)
            return
        }
        view.setImageDrawable(null)
        val context = view.context.applicationContext
        executor.execute {
            val bitmap = decode(context, uri, sizePx)
            if (bitmap != null) cache.put(key, bitmap)
            mainHandler.post {
                // The view may have been recycled for a different image meanwhile.
                if (view.tag != key) return@post
                if (bitmap != null) view.setImageBitmap(bitmap) else onError?.invoke()
            }
        }
    }

    private fun decode(context: Context, uri: String, sizePx: Int): Bitmap? = try {
        val source = ImageDecoder.createSource(context.contentResolver, Uri.parse(uri))
        ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            val longest = max(info.size.width, info.size.height)
            if (longest > sizePx) {
                val scale = sizePx.toFloat() / longest
                decoder.setTargetSize(
                    max(1, (info.size.width * scale).toInt()),
                    max(1, (info.size.height * scale).toInt())
                )
            }
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
    } catch (_: Exception) {
        null
    }
}
