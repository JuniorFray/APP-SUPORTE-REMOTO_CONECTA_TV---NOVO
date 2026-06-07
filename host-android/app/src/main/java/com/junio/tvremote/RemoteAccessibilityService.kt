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

    fun performTap(
        x: Float,
        y: Float,
        duration: Long = 100,
        onResult: ((Boolean) -> Unit)? = null
    ) {
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
                    onResult?.invoke(true)
                }

                override fun onCancelled(gestureDescription: GestureDescription?) {
                    super.onCancelled(gestureDescription)
                    Log.e(TAG, "Tap cancelado em ($x, $y)")
                    onResult?.invoke(false)
                }
            },
            null
        )

        Log.d(TAG, "dispatchGesture tap enviado=$dispatched em ($x, $y)")

        if (!dispatched) {
            onResult?.invoke(false)
        }
    }

    fun performSwipe(
        x1: Float,
        y1: Float,
        x2: Float,
        y2: Float,
        duration: Long = 300,
        onResult: ((Boolean) -> Unit)? = null
    ) {
        val path = Path().apply {
            moveTo(x1, y1)
            lineTo(x2, y2)
        }

        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, duration))
            .build()

        val dispatched = dispatchGesture(
            gesture,
            object : GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    super.onCompleted(gestureDescription)
                    Log.d(TAG, "Swipe executado com sucesso de ($x1, $y1) para ($x2, $y2)")
                    onResult?.invoke(true)
                }

                override fun onCancelled(gestureDescription: GestureDescription?) {
                    super.onCancelled(gestureDescription)
                    Log.e(TAG, "Swipe cancelado de ($x1, $y1) para ($x2, $y2)")
                    onResult?.invoke(false)
                }
            },
            null
        )

        Log.d(TAG, "dispatchGesture swipe enviado=$dispatched de ($x1, $y1) para ($x2, $y2)")

        if (!dispatched) {
            onResult?.invoke(false)
        }
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