package com.screcorder

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Environment
import android.os.IBinder
import android.os.SystemClock
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Chronometer
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.NotificationCompat
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

class RecordingService : Service() {

    companion object {
        // Yeh check karega ki button pehle se bana hua hai ya nahi
        var isServiceRunning = false
    }

    private lateinit var windowManager: WindowManager
    private lateinit var floatingView: View
    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var mediaRecorder: MediaRecorder? = null
    private var isRecording = false

    private lateinit var tvAction: TextView
    private lateinit var timer: Chronometer
    private lateinit var floatingRoot: LinearLayout

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (isServiceRunning) {
            Toast.makeText(this, "Recorder is already active on screen!", Toast.LENGTH_SHORT).show()
            return START_NOT_STICKY
        }
        isServiceRunning = true

        startForegroundServiceSafe()

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

    private fun startForegroundServiceSafe() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel("ScreenRecorderChannel", "Screen Recorder", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
        val notification = NotificationCompat.Builder(this, "ScreenRecorderChannel")
            .setContentTitle("Screen Recorder Active")
            .setContentText("Tap floating button to control")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .build()

        // Android 14 fix: Adding ServiceInfo explicitly
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        } else {
            startForeground(1, notification)
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
        params.x = 100
        params.y = 200

        tvAction = floatingView.findViewById(R.id.tvAction)
        timer = floatingView.findViewById(R.id.timer)
        floatingRoot = floatingView.findViewById(R.id.floatingRoot)

        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        var isMoved = false

        floatingRoot.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    isMoved = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val diffX = (event.rawX - initialTouchX).toInt()
                    val diffY = (event.rawY - initialTouchY).toInt()
                    if (abs(diffX) > 10 || abs(diffY) > 10) {
                        isMoved = true
                        params.x = initialX + diffX
                        params.y = initialY + diffY
                        windowManager.updateViewLayout(floatingView, params)
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!isMoved) {
                        handleFloatingClick(resultCode, data, resIndex, bitIndex, fpsIndex, audioIndex)
                    }
                    true
                }
                else -> false
            }
        }
        windowManager.addView(floatingView, params)
    }

    private fun handleFloatingClick(resultCode: Int, data: Intent?, resIndex: Int, bitIndex: Int, fpsIndex: Int, audioIndex: Int) {
        if (!isRecording) {
            val success = startRecording(resultCode, data, resIndex, bitIndex, fpsIndex, audioIndex)
            if (success) {
                tvAction.text = "STOP"
                tvAction.setTextColor(android.graphics.Color.WHITE)
                timer.visibility = View.VISIBLE
                timer.base = SystemClock.elapsedRealtime()
                timer.start()
            }
        } else {
            stopRecording()
            tvAction.text = "REC"
            tvAction.setTextColor(android.graphics.Color.parseColor("#F44336"))
            timer.stop()
            timer.visibility = View.GONE
            Toast.makeText(this, "Video Saved in Movies/Screen Recorder!", Toast.LENGTH_LONG).show()
            stopSelf() 
        }
    }

    private fun startRecording(resultCode: Int, data: Intent?, resIndex: Int, bitIndex: Int, fpsIndex: Int, audioIndex: Int): Boolean {
        return try {
            val projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            mediaProjection = projectionManager.getMediaProjection(resultCode, data!!)
            mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(this) else MediaRecorder()

            if (audioIndex != 3) mediaRecorder?.setAudioSource(MediaRecorder.AudioSource.MIC)
            mediaRecorder?.setVideoSource(MediaRecorder.VideoSource.SURFACE)
            mediaRecorder?.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)

            val directory = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES), "Screen Recorder")
            if (!directory.exists()) directory.mkdirs()
            val fileName = "Record_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())}.mp4"
            mediaRecorder?.setOutputFile(File(directory, fileName).absolutePath)

            // DYNAMIC RESOLUTION FIX: Automatically fits any phone properly
            val metrics = resources.displayMetrics
            val screenWidth = metrics.widthPixels
            val screenHeight = metrics.heightPixels
            
            val scale = when (resIndex) {
                0 -> 0.5f // Half Quality
                1 -> 0.7f // Medium Quality
                else -> 1.0f // Original Quality
            }
            var width = (screenWidth * scale).toInt()
            var height = (screenHeight * scale).toInt()

            // FIX: Encoders crash if resolution is not a multiple of 16
            width -= (width % 16)
            height -= (height % 16)

            mediaRecorder?.setVideoSize(width, height)

            val bitrate = when (bitIndex) {
                0 -> 500 * 1024
                1 -> 800 * 1024
                2 -> 1 * 1024 * 1024
                3 -> 2 * 1024 * 1024
                4 -> 4 * 1024 * 1024
                else -> 8 * 1024 * 1024
            }
            mediaRecorder?.setVideoEncodingBitRate(bitrate)

            val fps = when (fpsIndex) { 0 -> 24; 1 -> 30; else -> 60 }
            mediaRecorder?.setVideoFrameRate(fps)
            mediaRecorder?.setVideoEncoder(MediaRecorder.VideoEncoder.H264)
            if (audioIndex != 3) mediaRecorder?.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)

            mediaRecorder?.prepare()
            
            virtualDisplay = mediaProjection?.createVirtualDisplay("ScreenRecorder",
                width, height, metrics.densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                mediaRecorder?.surface, null, null
            )

            mediaRecorder?.start()
            isRecording = true
            true
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Error: ${e.message ?: "Failed to start"}", Toast.LENGTH_LONG).show()
            false
        }
    }

    private fun stopRecording() {
        try { mediaRecorder?.stop() } catch (e: Exception) {}
        try {
            mediaRecorder?.reset()
            mediaRecorder?.release()
            virtualDisplay?.release()
            mediaProjection?.stop()
        } catch (e: Exception) {}
        isRecording = false
    }

    override fun onDestroy() {
        super.onDestroy()
        isServiceRunning = false
        if (::floatingView.isInitialized) windowManager.removeView(floatingView)
        if (isRecording) stopRecording()
    }
}
