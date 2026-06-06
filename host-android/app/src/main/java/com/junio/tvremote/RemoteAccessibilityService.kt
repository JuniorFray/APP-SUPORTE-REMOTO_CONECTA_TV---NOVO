package com.junio.tvremote

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.util.Log
import android.view.accessibility.AccessibilityEvent

class RemoteAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "RemoteAccessibility"
        var instance: RemoteAccessibilityService? = null
            private set
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.d(TAG, "Accessibility Service conectada")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
    }

    override fun onInterrupt() {
        Log.d(TAG, "Accessibility Service interrompida")
    }

    override fun onDestroy() {
        super.onDestroy()
        if (instance === this) {
            instance = null
        }
        Log.d(TAG, "Accessibility Service destruída")
    }

    fun performTap(x: Float, y: Float, duration: Long = 100) {
        val path = Path().apply {
            moveTo(x, y)
        }

        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, duration))
            .build()

        val dispatched = dispatchGesture(
            gesture,
            object : GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    super.onCompleted(gestureDescription)
                    Log.d(TAG, "Tap executado com sucesso em ($x, $y)")
                }

                override fun onCancelled(gestureDescription: GestureDescription?) {
                    super.onCancelled(gestureDescription)
                    Log.e(TAG, "Tap cancelado em ($x, $y)")
                }
            },
            null
        )

        Log.d(TAG, "dispatchGesture enviado=$dispatched em ($x, $y)")
    }

    fun performGlobalBack(): Boolean {
        val result = performGlobalAction(GLOBAL_ACTION_BACK)
        Log.d(TAG, "GLOBAL_ACTION_BACK resultado=$result")
        return result
    }

    fun performGlobalHome(): Boolean {
        val result = performGlobalAction(GLOBAL_ACTION_HOME)
        Log.d(TAG, "GLOBAL_ACTION_HOME resultado=$result")
        return result
    }

    fun performGlobalRecents(): Boolean {
        val result = performGlobalAction(GLOBAL_ACTION_RECENTS)
        Log.d(TAG, "GLOBAL_ACTION_RECENTS resultado=$result")
        return result
    }
}
