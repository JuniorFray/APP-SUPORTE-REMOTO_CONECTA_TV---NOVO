package com.junio.tvremote

object RemoteConfig {
    const val WS_URL = "ws://10.0.2.2:8080"
    const val DEVICE_ID = "conecta-tv-rustdesk"
    const val AUTH_TOKEN = "b2136c040c88c7237ec6450c97cfad9b4307cb9bcc2e0192c61be61d004d6427"

    const val JPEG_QUALITY = 45
    const val SEND_INTERVAL_MS = 400L
    const val MAX_DIMENSION = 1280

    const val STREAM_MAX_DIMENSION = 854
    const val STREAM_JPEG_QUALITY = 28
    const val MAX_WS_QUEUE_BYTES = 131072L
}