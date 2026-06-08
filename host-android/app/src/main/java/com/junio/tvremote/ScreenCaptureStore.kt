package com.junio.tvremote

import android.content.Intent
import android.graphics.Bitmap

object ScreenCaptureStore {
    var resultCode: Int? = null
    var data: Intent? = null

    @Volatile
    private var latestBitmap: Bitmap? = null

    fun hasPermission(): Boolean {
        return resultCode != null && data != null
    }

    fun hasFrame(): Boolean {
        return latestBitmap != null
    }

    @Synchronized
    fun clear() {
        resultCode = null
        data = null
        latestBitmap?.recycle()
        latestBitmap = null
    }

    @Synchronized
    fun updateBitmap(bitmap: Bitmap) {
        val previous = latestBitmap
        latestBitmap = bitmap
        if (previous != null && previous != bitmap && !previous.isRecycled) {
            previous.recycle()
        }
    }

    @Synchronized
    fun getLatestBitmapCopy(): Bitmap? {
        val current = latestBitmap ?: return null
        if (current.isRecycled) return null
        return current.copy(current.config ?: Bitmap.Config.ARGB_8888, false)
    }
}