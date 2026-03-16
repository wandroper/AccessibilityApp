package com.example.accessibilityapp

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Langkah 1: Minta izin overlay
        findViewById<Button>(R.id.btnRequestOverlay).setOnClickListener {
            if (!Settings.canDrawOverlays(this)) {
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
                startActivity(intent)
            } else {
                Toast.makeText(this, "✓ Izin overlay sudah aktif", Toast.LENGTH_SHORT).show()
            }
        }

        // Langkah 2: Buka pengaturan Accessibility
        findViewById<Button>(R.id.btnOpenAccessibility).setOnClickListener {
            Toast.makeText(
                this,
                "Cari 'Accessibility Helper' lalu aktifkan",
                Toast.LENGTH_LONG
            ).show()
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        // Langkah 3: Start floating service
        findViewById<Button>(R.id.btnStartService).setOnClickListener {
            when {
                !Settings.canDrawOverlays(this) -> {
                    Toast.makeText(this, "⚠ Selesaikan Langkah 1 dulu!", Toast.LENGTH_SHORT).show()
                }
                else -> {
                    val intent = Intent(this, FloatingOverlayService::class.java)
                    startForegroundService(intent)
                    Toast.makeText(this, "✓ Floating menu aktif!", Toast.LENGTH_SHORT).show()
                    // Minimize app agar overlay terlihat
                    moveTaskToBack(true)
                }
            }
        }

        // Stop service
        findViewById<Button>(R.id.btnStopService).setOnClickListener {
            stopService(Intent(this, FloatingOverlayService::class.java))
            Toast.makeText(this, "Floating menu dihentikan", Toast.LENGTH_SHORT).show()
        }
    }
}
