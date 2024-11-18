package com.example.privox

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.media.MediaRecorder
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class RecordingService : Service() {

    companion object {
        private const val TAG = "RecordingService"
        var recordedFiles = mutableListOf<File>()
    }

    private var mediaRecorder: MediaRecorder? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var fileDuration = 60f // default duration in minutes
    private var rolloverJob: Job? = null
    private var partNumber = 1
    private val scope = CoroutineScope(Dispatchers.Default + Job())

    private var isFileRolloverEnabled = true

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service created")
        acquireWakeLock()
        startForegroundService()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        fileDuration = intent?.getFloatExtra("fileDuration", 60f) ?: 60f
        isFileRolloverEnabled = intent?.getBooleanExtra("isFileRolloverEnabled", true) ?: true
        startRecording()
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        stopRecording()
        releaseWakeLock()
        scope.cancel()
        Log.d(TAG, "Service destroyed")
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    private fun acquireWakeLock() {
        val powerManager = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "Privox::RecordingWakeLock"
        )
        wakeLock?.acquire()
    }

    private fun releaseWakeLock() {
        wakeLock?.let {
            if (it.isHeld) {
                it.release()
            }
        }
        wakeLock = null
    }

    private fun startForegroundService() {
        val channelId = "PrivoxRecordingChannel"
        val channelName = "Privox Recording Service"
        val notificationId = 1

        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val chan = NotificationChannel(
                channelId,
                channelName,
                NotificationManager.IMPORTANCE_LOW
            )
            notificationManager.createNotificationChannel(chan)
        }

        val notification: Notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Privox Recording")
            .setContentText("Recording audio in progress")
            .setSmallIcon(R.drawable.app_icon) // Ensure this drawable exists
            .setOngoing(true)
            .build()

        startForeground(notificationId, notification)
    }

    private fun startRecording() {
        Log.d(TAG, "Starting recording")
        try {
            val file = createNewRecordingFile()
            recordedFiles.add(file)

            mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(this)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioEncodingBitRate(512000)
                setAudioSamplingRate(48000)
                setAudioChannels(2)
                setOutputFile(file.absolutePath)
                prepare()
                start()
            }

            if (isFileRolloverEnabled) {
                scheduleFileRollover()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error starting recording: ${e.message}")
            stopSelf()
        }
    }

    private fun stopRecording() {
        Log.d(TAG, "Stopping recording")
        try {
            rolloverJob?.cancel()
            rolloverJob = null

            mediaRecorder?.apply {
                stop()
                release()
            }
            mediaRecorder = null
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping recording: ${e.message}")
        }
    }

    private fun scheduleFileRollover() {
        rolloverJob?.cancel()
        val durationInMillis = (fileDuration * 60 * 1000).toLong()
        rolloverJob = scope.launch {
            delay(durationInMillis)
            rolloverRecording()
            scheduleFileRollover()
        }
    }

    private fun rolloverRecording() {
        try {
            mediaRecorder?.apply {
                stop()
                release()
            }
            mediaRecorder = null

            partNumber++
            val file = createNewRecordingFile()
            recordedFiles.add(file)

            mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(this)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioEncodingBitRate(512000)
                setAudioSamplingRate(48000)
                setAudioChannels(2)
                setOutputFile(file.absolutePath)
                prepare()
                start()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error during file rollover: ${e.message}")
            stopSelf()
        }
    }

    private fun createNewRecordingFile(): File {
        val timestamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault())
            .format(Date())
        val prefix = "PRIVOX"
        val fileName = "${prefix}_Part${partNumber}_$timestamp.mp3"
        return File(getExternalFilesDir(null), fileName)
    }
}
