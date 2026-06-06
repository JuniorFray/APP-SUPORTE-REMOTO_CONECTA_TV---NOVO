package com.junio.tvremote

import android.app.Activity
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity

class ScreenCaptureActivity : AppCompatActivity() {

    private lateinit var mediaProjectionManager: MediaProjectionManager

    private val captureLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val resultCode = result.resultCode
            val data = result.data

            if (resultCode == Activity.RESULT_OK && data != null) {
                ScreenCaptureStore.resultCode = resultCode
                ScreenCaptureStore.data = data

                val serviceIntent = Intent(this, ScreenCaptureService::class.java).apply {
                    putExtra("resultCode", resultCode)
                    putExtra("data", data)
                }
                startForegroundService(serviceIntent)
                Toast.makeText(this, "Captura liberada com sucesso", Toast.LENGTH_SHORT).show()
            } else {
                ScreenCaptureStore.clear()
                Toast.makeText(this, "Você não liberou a captura da tela", Toast.LENGTH_SHORT).show()
            }

            finish()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        mediaProjectionManager =
            getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager

        captureLauncher.launch(mediaProjectionManager.createScreenCaptureIntent())
    }
}