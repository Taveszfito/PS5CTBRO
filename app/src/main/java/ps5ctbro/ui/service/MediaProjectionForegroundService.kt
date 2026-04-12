package com.DueBoysenberry1226.ps5ctbro.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.DueBoysenberry1226.ps5ctbro.R
import com.DueBoysenberry1226.ps5ctbro.audio.AudioControllerImpl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class MediaProjectionForegroundService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    companion object {
        private const val CHANNEL_ID = "media_projection_channel"
        private const val NOTIFICATION_ID = 1001

        const val ACTION_START = "com.DueBoysenberry1226.ps5ctbro.START_STREAM"
        const val ACTION_STOP = "com.DueBoysenberry1226.ps5ctbro.STOP_STREAM"
        const val EXTRA_RESULT_CODE = "resultCode"
        const val EXTRA_DATA = "data"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification(getString(R.string.notification_text_preparing)))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, 0)
                val data = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(EXTRA_DATA, Intent::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(EXTRA_DATA)
                }

                if (resultCode != 0 && data != null) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        val serviceType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                        } else {
                            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
                        }
                        
                        startForeground(
                            NOTIFICATION_ID,
                            buildNotification(getString(R.string.notification_text_active)),
                            serviceType
                        )
                    } else {
                        startForeground(
                            NOTIFICATION_ID,
                            buildNotification(getString(R.string.notification_text_active))
                        )
                    }
                    startStreaming(resultCode, data)
                }
            }
            ACTION_STOP -> {
                stopStreaming()
                stopSelf()
            }
        }

        return START_STICKY
    }

    private fun startStreaming(resultCode: Int, data: Intent) {
        val controller = AudioControllerImpl.getInstance(this)
        serviceScope.launch {
            controller.startSystemAudioStreaming(this@MediaProjectionForegroundService, resultCode, data)
        }
    }

    private fun stopStreaming() {
        val controller = AudioControllerImpl.getInstance(this)
        controller.stopSystemAudioStreaming()
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun buildNotification(text: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(text)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val manager = getSystemService(NotificationManager::class.java) ?: return

        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_LOW
        )

        manager.createNotificationChannel(channel)
    }
}
