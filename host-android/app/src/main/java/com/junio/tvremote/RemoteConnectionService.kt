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

                    if (type == "remote-command") {
                        handleRemoteCommand(webSocket, json)
                        return
                    }

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

    private fun handleRemoteCommand(socket: WebSocket, json: JSONObject) {
        val commandId = json.optString("commandId", "")
        val command = json.optString("command")
        val accessibility = RemoteAccessibilityService.instance

        if (accessibility == null) {
            Log.e(TAG, "AccessibilityService indisponível para command=$command")
            sendCommandAck(socket, commandId, command, "error", "accessibility_unavailable")
            return
        }

        when (command) {
            "home" -> {
                val result = accessibility.performGlobalHome()
                Log.d(TAG, "command=home result=$result")
                sendCommandAck(socket, commandId, command, if (result) "ok" else "error")
            }

            "back" -> {
                val result = accessibility.performGlobalBack()
                Log.d(TAG, "command=back result=$result")
                sendCommandAck(socket, commandId, command, if (result) "ok" else "error")
            }

            "recents" -> {
                val result = accessibility.performGlobalRecents()
                Log.d(TAG, "command=recents result=$result")
                sendCommandAck(socket, commandId, command, if (result) "ok" else "error")
            }

            "tap" -> {
                val x = json.optDouble("x", -1.0).toFloat()
                val y = json.optDouble("y", -1.0).toFloat()
                val duration = json.optLong("durationMs", 100L)

                if (x >= 0f && y >= 0f) {
                    Log.d(TAG, "command=tap x=$x y=$y duration=$duration")
                    accessibility.performTap(x, y, duration) { success ->
                        sendCommandAck(
                            socket,
                            commandId,
                            command,
                            if (success) "ok" else "error"
                        )
                    }
                } else {
                    Log.e(TAG, "tap inválido x=$x y=$y")
                    sendCommandAck(socket, commandId, command, "error", "invalid_coordinates")
                }
            }

            "swipe" -> {
                val x1 = json.optDouble("x1", -1.0).toFloat()
                val y1 = json.optDouble("y1", -1.0).toFloat()
                val x2 = json.optDouble("x2", -1.0).toFloat()
                val y2 = json.optDouble("y2", -1.0).toFloat()
                val duration = json.optLong("durationMs", 300L)

                if (x1 >= 0f && y1 >= 0f && x2 >= 0f && y2 >= 0f) {
                    Log.d(TAG, "command=swipe x1=$x1 y1=$y1 x2=$x2 y2=$y2 duration=$duration")
                    accessibility.performSwipe(x1, y1, x2, y2, duration) { success ->
                        sendCommandAck(
                            socket,
                            commandId,
                            command,
                            if (success) "ok" else "error"
                        )
                    }
                } else {
                    Log.e(TAG, "swipe inválido x1=$x1 y1=$y1 x2=$x2 y2=$y2")
                    sendCommandAck(socket, commandId, command, "error", "invalid_coordinates")
                }
            }

            else -> {
                Log.e(TAG, "Comando desconhecido: $command")
                sendCommandAck(socket, commandId, command, "error", "unknown_command")
            }
        }
    }

    private fun sendCommandAck(
        socket: WebSocket,
        commandId: String,
        command: String,
        status: String,
        error: String? = null
    ) {
        val json = JSONObject()
            .put("type", "remote-command-ack")
            .put("commandId", commandId)
            .put("command", command)
            .put("status", status)

        if (error != null) {
            json.put("error", error)
        }

        val sent = socket.send(json.toString())
        Log.d(TAG, "remote-command-ack enviado=$sent commandId=$commandId command=$command status=$status error=$error")
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