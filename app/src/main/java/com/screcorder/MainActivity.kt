package com.screcorder

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.screcorder.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var projectionManager: MediaProjectionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        projectionManager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager

        setupPresets()

        binding.btnStart.setOnClickListener {
            checkPermissionsAndStart()
        }
    }

    private fun setupPresets() {
        // Preset radio buttons ka logic
        binding.radioGroupPresets.setOnCheckedChangeListener { _, checkedId ->
            val isCustom = checkedId == R.id.rbCustom
            
            // Agar custom nahi hai, toh drop-downs ko block kar do
            binding.spinnerResolution.isEnabled = isCustom
            binding.spinnerBitrate.isEnabled = isCustom
            binding.spinnerFps.isEnabled = isCustom

            if (!isCustom) {
                when (checkedId) {
                    R.id.rbLow -> {
                        binding.spinnerResolution.setSelection(0) // 480p
                        binding.spinnerBitrate.setSelection(0) // 500kbps (5MB/min)
                        binding.spinnerFps.setSelection(0) // 24 FPS
                    }
                    R.id.rbMedium -> {
                        binding.spinnerResolution.setSelection(1) // 720p
                        binding.spinnerBitrate.setSelection(1) // 800kbps
                        binding.spinnerFps.setSelection(1) // 30 FPS
                    }
                    R.id.rbHigh -> {
                        binding.spinnerResolution.setSelection(2) // 1080p
                        binding.spinnerBitrate.setSelection(4) // 4 Mbps
                        binding.spinnerFps.setSelection(2) // 60 FPS
                    }
                }
            }
        }
    }

    private fun checkPermissionsAndStart() {
        // Floating button ke liye permission check
        if (!Settings.canDrawOverlays(this)) {
            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
            startActivity(intent)
            Toast.makeText(this, "Please allow 'Display over other apps' to show Floating Button", Toast.LENGTH_LONG).show()
            return
        }

        // Audio permission check
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 101)
            return
        }

        // Screen capture permission (System popup aayega "Start Recording?")
        val captureIntent = projectionManager.createScreenCaptureIntent()
        screenCaptureLauncher.launch(captureIntent)
    }

    private val screenCaptureLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            startRecordingService(result.resultCode, result.data!!)
        } else {
            Toast.makeText(this, "Permission Denied! Cannot record screen.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun startRecordingService(resultCode: Int, data: Intent) {
        // Aapne jo options select kiye hain unko Service (Background engine) ko bhej rahe hain
        val intent = Intent(this, RecordingService::class.java).apply {
            putExtra("code", resultCode)
            putExtra("data", data)
            putExtra("resIndex", binding.spinnerResolution.selectedItemPosition)
            putExtra("bitIndex", binding.spinnerBitrate.selectedItemPosition)
            putExtra("fpsIndex", binding.spinnerFps.selectedItemPosition)
            putExtra("audioIndex", binding.spinnerAudio.selectedItemPosition)
        }
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        
        // App background me chali jayegi (minimize ho jayegi) aur floating button aa jayega
        finish() 
    }
}
