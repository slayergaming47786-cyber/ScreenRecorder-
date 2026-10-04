package com.screcorder

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Environment
import android.os.IBinder
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.Toast
import androidx.core.app.NotificationCompat
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class RecordingService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var floatingView: View
    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var mediaRecorder: MediaRecorder? = null
    private var isRecording = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createNotificationChannel()
        val notification = NotificationCompat.Builder(this, "ScreenRecorderChannel")
            .setContentTitle("Screen Recorder Active")
            .setContentText("Tap floating button to control")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .build()
        startForeground(1, notification)

        if (intent != null) {
            val resultCode = intent.getIntExtra("code", -1)
            val data: Intent? = intent.getParcelableExtra("data")
            val resIndex = intent.getIntExtra("resIndex", 1)
            val bitIndex = intent.getIntExtra("bitIndex", 1)
            val fpsIndex = intent.getIntExtra("fpsIndex", 1)
            val audioIndex = intent.getIntExtra("audioIndex", 0)

            setupFloatingWindow(resultCode, data, resIndex, bitIndex, fpsIndex, audioIndex)
        }
        return START_NOT_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel("ScreenRecorderChannel", "Screen Recorder", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun setupFloatingWindow(resultCode: Int, data: Intent?, resIndex: Int, bitIndex: Int, fpsIndex: Int, audioIndex: Int) {
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        floatingView = LayoutInflater.from(this).inflate(R.layout.layout_floating_widget, null)

        val layoutFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
            layoutFlag, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.TOP or Gravity.START
        params.x = 50
        params.y = 150

        windowManager.addView(floatingView, params)

        val btnRecordStop = floatingView.findViewById<Button>(R.id.btnRecordStop)
        btnRecordStop.setOnClickListener {
            if (!isRecording) {
                startRecording(resultCode, data, resIndex, bitIndex, fpsIndex, audioIndex)
                btnRecordStop.text = "STOP"
            } else {
                stopRecording()
                btnRecordStop.text = "REC"
                Toast.makeText(this, "Video Saved in Movies/Screen Recorder", Toast.LENGTH_LONG).show()
                stopSelf() // App close ho jayegi
            }
        }
    }

    private fun startRecording(resultCode: Int, data: Intent?, resIndex: Int, bitIndex: Int, fpsIndex: Int, audioIndex: Int) {
        try {
            val projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            mediaProjection = projectionManager.getMediaProjection(resultCode, data!!)

            mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(this) else MediaRecorder()

            // Agar audio Mute nahi hai (Option 3 = Mute)
            if (audioIndex != 3) {
                mediaRecorder?.setAudioSource(MediaRecorder.AudioSource.MIC)
            }
            mediaRecorder?.setVideoSource(MediaRecorder.VideoSource.SURFACE)
            mediaRecorder?.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)

            // Storage Setup: Movies/Screen Recorder folder me jayega
            val directory = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES), "Screen Recorder")
            if (!directory.exists()) directory.mkdirs()
            val fileName = "Record_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())}.mp4"
            mediaRecorder?.setOutputFile(File(directory, fileName).absolutePath)

            // Customizations Apply: Resolution, Bitrate, FPS
            val width = when (resIndex) { 0 -> 480; 1 -> 720; else -> 1080 }
            val height = when (resIndex) { 0 -> 854; 1 -> 1280; else -> 1920 }
            mediaRecorder?.setVideoSize(width, height)

            val bitrate = when (bitIndex) {
                0 -> 500 * 1024 // 500 kbps (Super low size)
                1 -> 800 * 1024 // 800 kbps
                2 -> 1 * 1024 * 1024 // 1 Mbps
                3 -> 2 * 1024 * 1024 // 2 Mbps
                4 -> 4 * 1024 * 1024 // 4 Mbps
                else -> 8 * 1024 * 1024 // 8 Mbps
            }
            mediaRecorder?.setVideoEncodingBitRate(bitrate)

            val fps = when (fpsIndex) { 0 -> 24; 1 -> 30; else -> 60 }
            mediaRecorder?.setVideoFrameRate(fps)

            mediaRecorder?.setVideoEncoder(MediaRecorder.VideoEncoder.H264)
            if (audioIndex != 3) {
                mediaRecorder?.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            }

            mediaRecorder?.prepare()
            
            val metrics = resources.displayMetrics
            virtualDisplay = mediaProjection?.createVirtualDisplay("ScreenRecorder",
                width, height, metrics.densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                mediaRecorder?.surface, null, null
            )

            mediaRecorder?.start()
            isRecording = true
        } catch (e: Exception) {
            Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun stopRecording() {
        try {
            mediaRecorder?.stop()
            mediaRecorder?.reset()
            mediaRecorder?.release()
            virtualDisplay?.release()
            mediaProjection?.stop()
            isRecording = false
        } catch (e: Exception) { e.printStackTrace() }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::floatingView.isInitialized) windowManager.removeView(floatingView)
        if (isRecording) stopRecording()
    }
}
