package com.screcorder

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.Spinner
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private val PERMISSION_CODE = 1000
    private val OVERLAY_CODE = 1001

    private lateinit var spinnerPreset: Spinner
    private lateinit var spinnerResolution: Spinner
    private lateinit var spinnerBitrate: Spinner
    private lateinit var spinnerFps: Spinner
    private lateinit var spinnerAudio: Spinner
    private lateinit var btnStart: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Naye UI Dropdowns (Spinners) ko link karna
        spinnerPreset = findViewById(R.id.spinnerPreset)
        spinnerResolution = findViewById(R.id.spinnerResolution)
        spinnerBitrate = findViewById(R.id.spinnerBitrate)
        spinnerFps = findViewById(R.id.spinnerFps)
        spinnerAudio = findViewById(R.id.spinnerAudio)
        btnStart = findViewById(R.id.btnStart)

        checkPermissions()

        btnStart.setOnClickListener {
            if (checkOverlayPermission()) {
                startScreenCapture()
            }
        }
    }

    private fun checkPermissions() {
        val permissions = mutableListOf<String>()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.RECORD_AUDIO)
        }
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P && ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        
        if (permissions.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, permissions.toTypedArray(), 111)
        }
    }

    private fun checkOverlayPermission(): Boolean {
        if (!Settings.canDrawOverlays(this)) {
            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
            startActivityForResult(intent, OVERLAY_CODE)
            Toast.makeText(this, "Please allow 'Display over other apps'", Toast.LENGTH_LONG).show()
            return false
        }
        return true
    }

    private fun startScreenCapture() {
        if (RecordingService.isServiceRunning) {
            Toast.makeText(this, "Floating button is already active!", Toast.LENGTH_SHORT).show()
            return
        }
        val projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        startActivityForResult(projectionManager.createScreenCaptureIntent(), PERMISSION_CODE)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == PERMISSION_CODE) {
            if (resultCode == RESULT_OK && data != null) {
                
                // Dropdowns se current selected item ka number nikalna
                val intent = Intent(this, RecordingService::class.java).apply {
                    putExtra("code", resultCode)
                    putExtra("data", data)
                    
                    // Agar preset "Custom Settings" (index 3) nahi hai, toh preset ki setting lagao
                    if (spinnerPreset.selectedItemPosition != 3) {
                        when (spinnerPreset.selectedItemPosition) {
                            0 -> { // Low Size
                                putExtra("resIndex", 0)
                                putExtra("bitIndex", 1)
                                putExtra("fpsIndex", 0)
                            }
                            1 -> { // Medium
                                putExtra("resIndex", 1)
                                putExtra("bitIndex", 2)
                                putExtra("fpsIndex", 1)
                            }
                            2 -> { // High Quality
                                putExtra("resIndex", 2)
                                putExtra("bitIndex", 4)
                                putExtra("fpsIndex", 3)
                            }
                        }
                    } else {
                        // Custom Settings (User ki marzi)
                        putExtra("resIndex", spinnerResolution.selectedItemPosition)
                        putExtra("bitIndex", spinnerBitrate.selectedItemPosition)
                        putExtra("fpsIndex", spinnerFps.selectedItemPosition)
                    }
                    
                    // Audio selection bhej rahe hain
                    putExtra("audioIndex", spinnerAudio.selectedItemPosition)
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(intent)
                } else {
                    startService(intent)
                }
                
                // Start dabane ke baad app ko automatically background me bhej do (Home screen par jao)
                moveTaskToBack(true)
            } else {
                Toast.makeText(this, "Screen capture permission denied", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
