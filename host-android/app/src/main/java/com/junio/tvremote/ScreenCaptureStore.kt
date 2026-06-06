package com.junio.tvremote

import android.content.Intent
import android.graphics.Bitmap

object ScreenCaptureStore {
    var resultCode: Int? = null
    var data: Intent? = null
    @Volatile var latestBitmap: Bitmap? = null

    fun hasPermission(): Boolean {
        return resultCode != null && data != null
    }

    fun hasFrame(): Boolean {
        return latestBitmap != null
    }

    fun clear() {
        resultCode = null
        data = null
        latestBitmap?.recycle()
        latestBitmap = null
    }

    fun updateBitmap(bitmap: Bitmap) {
    latestBitmap = bitmap
}
}