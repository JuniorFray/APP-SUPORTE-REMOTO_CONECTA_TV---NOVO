package com.junio.tvremote

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class RemoteConnectionService : Service() {

    private var workerThread: HandlerThread? = null
    private var workerHandler: Handler? = null
    private var webSocket: WebSocket? = null

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .build()
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "onCreate")
        createNotificationChannel()

        val notification = Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("TV Remote Host")
            .setContentText("Conexão remota ativa")
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        workerThread = HandlerThread("RemoteConnectionThread").also { it.start() }
        workerHandler = Handler(workerThread!!.looper)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand action=${intent?.action} pending=$pendingStartRemote")
        workerHandler?.post {
            val shouldStartRemote = intent?.action == ACTION_START_REMOTE || pendingStartRemote
            if (shouldStartRemote) {
                pendingStartRemote = false
                startRemoteSession()
            } else {
                connectWebSocket()
            }
        }
        return START_REDELIVER_INTENT
    }

    override fun onDestroy() {
        Log.d(TAG, "onDestroy")
        webSocket?.close(1000, "service destroyed")
        webSocket = null
        workerThread?.quitSafely()
        workerThread = null
        workerHandler = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun connectWebSocket() {
        if (webSocket != null) {
            Log.d(TAG, "WebSocket já conectado ou em abertura")
            return
        }

        val service = this

        val request = Request.Builder()
            .url(RemoteConfig.WS_URL)
            .build()

        Log.d(TAG, "Conectando em ${RemoteConfig.WS_URL}")

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d(TAG, "WebSocket conectado")
                sendHello(webSocket)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                Log.d(TAG, "Mensagem recebida: $text")
                try {
                    val json = JSONObject(text)
                    val type = json.optString("type")
                    if (type == "ack" && pendingPair) {
                        Log.d(TAG, "ack recebido com pendingPair=true; enviando pair")
                        pendingPair = false
                        startRemoteSession()
                    }
                } catch (t: Throwable) {
                    Log.e(TAG, "Erro processando mensagem", t)
                }
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "WebSocket closing code=$code reason=$reason")
                webSocket.close(code, reason)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "WebSocket fechado code=$code reason=$reason")
                service.webSocket = null
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "Falha no WebSocket", t)
                service.webSocket = null
            }
        })
    }

    private fun sendHello(socket: WebSocket) {
        val json = JSONObject()
            .put("type", "hello")
            .put("role", "host")
            .put("deviceId", RemoteConfig.DEVICE_ID)
            .put("token", RemoteConfig.AUTH_TOKEN)

        val sent = socket.send(json.toString())
        Log.d(TAG, "hello enviado=$sent")
    }

    private fun startRemoteSession() {
        val socket = webSocket
        if (socket == null) {
            pendingPair = true
            Log.d(TAG, "START_REMOTE sem websocket; conectando primeiro")
            connectWebSocket()
            return
        }

        val json = JSONObject()
            .put("type", "pair")
            .put("deviceId", RemoteConfig.DEVICE_ID)
            .put("token", RemoteConfig.AUTH_TOKEN)

        val sent = socket.send(json.toString())
        Log.d(TAG, "pair enviado=$sent")
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Remote Connection",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    companion object {
        private const val TAG = "RemoteConnectionSvc"
        private const val CHANNEL_ID = "remote_connection_channel"
        private const val NOTIFICATION_ID = 2
        private const val ACTION_START_REMOTE = "com.junio.tvremote.START_REMOTE"
        @Volatile private var pendingStartRemote = false
        @Volatile private var pendingPair = false

        fun requestStartRemote() {
            pendingStartRemote = true
        }
    }
}








