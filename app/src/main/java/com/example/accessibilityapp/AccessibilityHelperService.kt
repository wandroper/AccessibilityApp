package com.example.accessibilityapp

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Path
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class AccessibilityHelperService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())
    private var scrollRunnable: Runnable? = null
    private var isAutoScrolling = false
    private var autoClickEnabled = false
    private var commandReceiver: BroadcastReceiver? = null

    // Ganti sesuai target elemen yang ingin di-klik otomatis
    private val targetText = "Submit"
    private val targetViewId = "com.example.targetapp:id/btn_submit"

    companion object {
        const val ACTION_COMMAND = "com.example.ACTION_ACCESSIBILITY_COMMAND"
        const val EXTRA_COMMAND = "command"
        const val CMD_SCROLL_UP = "scroll_up"
        const val CMD_SCROLL_DOWN = "scroll_down"
        const val CMD_STOP = "stop"
        const val CMD_AUTO_CLICK = "auto_click"
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        registerCommandReceiver()
    }

    // ==========================================================
    // BROADCAST RECEIVER
    // ==========================================================
    private fun registerCommandReceiver() {
        commandReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                when (intent?.getStringExtra(EXTRA_COMMAND)) {
                    CMD_SCROLL_UP   -> startAutoScroll(ScrollDirection.UP)
                    CMD_SCROLL_DOWN -> startAutoScroll(ScrollDirection.DOWN)
                    CMD_STOP        -> stopAutoScroll()
                    CMD_AUTO_CLICK  -> {
                        autoClickEnabled = !autoClickEnabled
                        stopAutoScroll()
                    }
                }
            }
        }
        val filter = IntentFilter(ACTION_COMMAND)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(commandReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(commandReceiver, filter)
        }
    }

    // ==========================================================
    // AUTO SCROLL LOGIC
    // ==========================================================
    enum class ScrollDirection { UP, DOWN }

    private fun startAutoScroll(direction: ScrollDirection, intervalMs: Long = 1500L) {
        stopAutoScroll()
        isAutoScrolling = true

        scrollRunnable = object : Runnable {
            override fun run() {
                if (!isAutoScrolling) return
                performScrollGesture(direction)
                handler.postDelayed(this, intervalMs)
            }
        }
        handler.post(scrollRunnable!!)
    }

    private fun stopAutoScroll() {
        isAutoScrolling = false
        scrollRunnable?.let { handler.removeCallbacks(it) }
        scrollRunnable = null
    }

    private fun performScrollGesture(direction: ScrollDirection) {
        val metrics = resources.displayMetrics
        val centerX = metrics.widthPixels / 2f
        val screenHeight = metrics.heightPixels

        val startY: Float
        val endY: Float

        when (direction) {
            ScrollDirection.DOWN -> {
                // Jari gerak ke atas → konten naik → scroll down
                startY = screenHeight * 0.65f
                endY   = screenHeight * 0.25f
            }
            ScrollDirection.UP -> {
                // Jari gerak ke bawah → konten turun → scroll up
                startY = screenHeight * 0.25f
                endY   = screenHeight * 0.65f
            }
        }

        val path = Path().apply {
            moveTo(centerX, startY)
            lineTo(centerX, endY)
        }

        // StrokeDescription(path, startTimeMs, durationMs)
        val stroke = GestureDescription.StrokeDescription(path, 0L, 400L)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()

        dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription) { /* sukses */ }
            override fun onCancelled(gestureDescription: GestureDescription) {
                stopAutoScroll() // Hentikan jika gesture dibatalkan sistem
            }
        }, null)
    }

    // ==========================================================
    // ELEMENT DETECTION & AUTO CLICK
    // ==========================================================
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return

        // Hanya proses saat ada perubahan tampilan
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED &&
            event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return

        if (autoClickEnabled) {
            searchAndClickTarget()
        }
    }

    private fun searchAndClickTarget() {
        val root = rootInActiveWindow ?: return

        try {
            // Metode 1: Cari berdasarkan teks yang tampil
            root.findAccessibilityNodeInfosByText(targetText)?.forEach { node ->
                if (node.isClickable && node.isEnabled && node.isVisibleToUser) {
                    clickNode(node)
                    node.recycle()
                    return
                }
                node.recycle()
            }

            // Metode 2: Cari berdasarkan resource-id
            root.findAccessibilityNodeInfosByViewId(targetViewId)?.forEach { node ->
                if (node.isEnabled && node.isVisibleToUser) {
                    clickNode(node)
                    node.recycle()
                    return
                }
                node.recycle()
            }
        } finally {
            root.recycle()
        }
    }

    private fun clickNode(node: AccessibilityNodeInfo) {
        // Coba performAction dulu (lebih reliable)
        val success = node.performAction(AccessibilityNodeInfo.ACTION_CLICK)

        if (!success) {
            // Fallback: tap via gesture pada koordinat node
            val bounds = Rect()
            node.getBoundsInScreen(bounds)
            tapAt(bounds.centerX().toFloat(), bounds.centerY().toFloat())
        }
    }

    private fun tapAt(x: Float, y: Float) {
        val path = Path().apply { moveTo(x, y) }
        // Duration 1ms = tap (bukan swipe)
        val stroke = GestureDescription.StrokeDescription(path, 0L, 1L)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        dispatchGesture(gesture, null, null)
    }

    override fun onInterrupt() {
        stopAutoScroll()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopAutoScroll()
        commandReceiver?.let {
            try { unregisterReceiver(it) } catch (e: Exception) { /* abaikan */ }
        }
    }
}
