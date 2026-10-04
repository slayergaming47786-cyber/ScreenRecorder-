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
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private val PERMISSION_CODE = 1000
    private val OVERLAY_CODE = 1001

    private lateinit var spinnerResolution: Spinner
    private lateinit var spinnerBitrate: Spinner
    private lateinit var spinnerFps: Spinner
    private lateinit var spinnerAudio: Spinner
    private lateinit var btnStart: Button
    
    // Naye 4 Preset Cards
    private lateinit var cardLow: LinearLayout
    private lateinit var cardMedium: LinearLayout
    private lateinit var cardHigh: LinearLayout
    private lateinit var cardCustom: LinearLayout
    
    // Default preset 3 (Custom) selected hai
    private var selectedPreset = 3 

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        spinnerResolution = findViewById(R.id.spinnerResolution)
        spinnerBitrate = findViewById(R.id.spinnerBitrate)
        spinnerFps = findViewById(R.id.spinnerFps)
        spinnerAudio = findViewById(R.id.spinnerAudio)
        btnStart = findViewById(R.id.btnStart)

        cardLow = findViewById(R.id.cardLow)
        cardMedium = findViewById(R.id.cardMedium)
        cardHigh = findViewById(R.id.cardHigh)
        cardCustom = findViewById(R.id.cardCustom)

        setupPresetClicks()
        checkPermissions()

        btnStart.setOnClickListener {
            if (checkOverlayPermission()) {
                startScreenCapture()
            }
        }
    }

    private fun setupPresetClicks() {
        val unselectedBg = ContextCompat.getDrawable(this, R.drawable.bg_preset_unselected)
        val selectedBg = ContextCompat.getDrawable(this, R.drawable.bg_preset_selected)

        val resetCards = {
            cardLow.background = unselectedBg
            cardMedium.background = unselectedBg
            cardHigh.background = unselectedBg
            cardCustom.background = unselectedBg
        }

        cardLow.setOnClickListener {
            resetCards()
            cardLow.background = selectedBg
            selectedPreset = 0
        }
        cardMedium.setOnClickListener {
            resetCards()
            cardMedium.background = selectedBg
            selectedPreset = 1
        }
        cardHigh.setOnClickListener {
            resetCards()
            cardHigh.background = selectedBg
            selectedPreset = 2
        }
        cardCustom.setOnClickListener {
            resetCards()
            cardCustom.background = selectedBg
            selectedPreset = 3
        }
    }

    private fun checkPermissions() {
        val permissions = mutableListOf<String>()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) permissions.add(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P && ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) permissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        if (permissions.isNotEmpty()) ActivityCompat.requestPermissions(this, permissions.toTypedArray(), 111)
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
            Toast.makeText(this, "Floating button is active!", Toast.LENGTH_SHORT).show()
            return
        }
        val projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        startActivityForResult(projectionManager.createScreenCaptureIntent(), PERMISSION_CODE)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == PERMISSION_CODE && resultCode == RESULT_OK && data != null) {
            val intent = Intent(this, RecordingService::class.java).apply {
                putExtra("code", resultCode)
                putExtra("data", data)
                
                if (selectedPreset != 3) {
                    when (selectedPreset) {
                        0 -> { putExtra("resIndex", 0); putExtra("bitIndex", 1); putExtra("fpsIndex", 0) }
                        1 -> { putExtra("resIndex", 1); putExtra("bitIndex", 2); putExtra("fpsIndex", 1) }
                        2 -> { putExtra("resIndex", 2); putExtra("bitIndex", 4); putExtra("fpsIndex", 3) }
                    }
                } else {
                    putExtra("resIndex", spinnerResolution.selectedItemPosition)
                    putExtra("bitIndex", spinnerBitrate.selectedItemPosition)
                    putExtra("fpsIndex", spinnerFps.selectedItemPosition)
                }
                putExtra("audioIndex", spinnerAudio.selectedItemPosition)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent) else startService(intent)
            moveTaskToBack(true)
        }
    }
}
