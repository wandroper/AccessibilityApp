package com.example.accessibilityapp

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.*
import android.widget.Button
import android.widget.ImageButton
import android.widget.LinearLayout
import androidx.core.app.NotificationCompat

class FloatingOverlayService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var floatingView: View
    private lateinit var layoutParams: WindowManager.LayoutParams

    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private var isExpanded = false
    private var isDragging = false

    companion object {
        const val CHANNEL_ID = "floating_overlay_channel"
        const val NOTIFICATION_ID = 1
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
        setupFloatingWindow()
    }

    private fun setupFloatingWindow() {
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        floatingView = LayoutInflater.from(this).inflate(R.layout.floating_menu, null)

        layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 50
            y = 300
        }

        windowManager.addView(floatingView, layoutParams)
        setupTouchListener()
        setupButtonListeners()
    }

    private fun setupTouchListener() {
        val btnToggle = floatingView.findViewById<ImageButton>(R.id.btnToggle)

        btnToggle.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = layoutParams.x
                    initialY = layoutParams.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    isDragging = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val deltaX = (event.rawX - initialTouchX).toInt()
                    val deltaY = (event.rawY - initialTouchY).toInt()
                    if (Math.abs(deltaX) > 8 || Math.abs(deltaY) > 8) {
                        isDragging = true
                        layoutParams.x = initialX + deltaX
                        layoutParams.y = initialY + deltaY
                        windowManager.updateViewLayout(floatingView, layoutParams)
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!isDragging) togglePanel()
                    true
                }
                else -> false
            }
        }
    }

    private fun togglePanel() {
        val expandedPanel = floatingView.findViewById<LinearLayout>(R.id.expandedPanel)
        isExpanded = !isExpanded

        if (isExpanded) {
            expandedPanel.visibility = View.VISIBLE
            // Hapus FLAG_NOT_FOCUSABLE agar tombol bisa diklik
            layoutParams.flags = layoutParams.flags and
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()
        } else {
            expandedPanel.visibility = View.GONE
            layoutParams.flags = layoutParams.flags or
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        }
        windowManager.updateViewLayout(floatingView, layoutParams)
    }

    private fun setupButtonListeners() {
        floatingView.findViewById<Button>(R.id.btnScrollUp).setOnClickListener {
            sendCommand(AccessibilityHelperService.CMD_SCROLL_UP)
        }
        floatingView.findViewById<Button>(R.id.btnScrollDown).setOnClickListener {
            sendCommand(AccessibilityHelperService.CMD_SCROLL_DOWN)
        }
        floatingView.findViewById<Button>(R.id.btnStopScroll).setOnClickListener {
            sendCommand(AccessibilityHelperService.CMD_STOP)
        }
        floatingView.findViewById<Button>(R.id.btnClickTarget).setOnClickListener {
            sendCommand(AccessibilityHelperService.CMD_AUTO_CLICK)
        }
    }

    private fun sendCommand(command: String) {
        val intent = Intent(AccessibilityHelperService.ACTION_COMMAND).apply {
            putExtra(AccessibilityHelperService.EXTRA_COMMAND, command)
            setPackage(packageName)
        }
        sendBroadcast(intent)
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Floating Overlay Service",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Notifikasi untuk floating accessibility menu"
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("♿ Accessibility Helper")
            .setContentText("Floating menu sedang aktif. Tap untuk mengatur.")
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::floatingView.isInitialized) {
            windowManager.removeView(floatingView)
        }
    }
}
