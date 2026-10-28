package com.kashef.textsearch.ui.overlay

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.app.Application
import android.content.Context
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.kashef.textsearch.R
import com.kashef.textsearch.ui.search.ScanStage
import com.kashef.textsearch.ui.search.ScanUi
import com.kashef.textsearch.ui.search.dialogue
import kotlin.math.abs

/**
 * Floating Kashef drawn over other apps (needs the "Display over other apps" permission).
 * It is shown only while the app is in the background and a search is running or just finished.
 * Drag to move it; tap it to dismiss it for the current search. Call on the main thread.
 */
class OverlayController(private val app: Application) {
    private val wm = app.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val main = Handler(Looper.getMainLooper())
    private var root: LinearLayout? = null
    private var bubble: TextView? = null
    private var animator: ObjectAnimator? = null
    private var dismissed = false
    private val autoHide = Runnable {
        dismissed = true
        hide()
    }

    fun canDraw(): Boolean = Settings.canDrawOverlays(app)

    fun newRun() {
        dismissed = false
    }

    fun render(ui: ScanUi?, allowed: Boolean) {
        if (ui == null || !allowed || dismissed || !canDraw()) {
            hide()
            return
        }
        if (!ensure()) return
        val s = ui.snap
        bubble?.text = dialogue(ui) + "\n" + "فُحص ${s.scanned} ملفًا • مطابق: ${s.matchedFiles}"
        main.removeCallbacks(autoHide)
        if (ui.stage != ScanStage.Running) main.postDelayed(autoHide, 8000)
    }

    @Suppress("DEPRECATION")
    private fun overlayType(): Int =
        if (Build.VERSION.SDK_INT >= 26) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else WindowManager.LayoutParams.TYPE_PHONE

    private fun ensure(): Boolean {
        if (root != null) return true
        val d = app.resources.displayMetrics
        val dp = d.density

        val tv = TextView(app).apply {
            setTextColor(0xFFEAF1FF.toInt())
            textSize = 13f
            maxWidth = (230 * dp).toInt()
            setPadding((12 * dp).toInt(), (8 * dp).toInt(), (12 * dp).toInt(), (8 * dp).toInt())
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            textDirection = View.TEXT_DIRECTION_RTL
            background = GradientDrawable().apply {
                setColor(0xEE0B1A40.toInt())
                cornerRadius = 16 * dp
                setStroke((1 * dp).toInt(), 0xFF3D8BFF.toInt())
            }
        }
        val img = ImageView(app).apply {
            setImageResource(R.drawable.kashef_character)
            scaleType = ImageView.ScaleType.FIT_CENTER
        }
        val box = LinearLayout(app).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            addView(tv, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT))
            addView(img, LinearLayout.LayoutParams((130 * dp).toInt(), (200 * dp).toInt()))
        }

        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = (d.widthPixels - 240 * dp).toInt().coerceAtLeast(0)
            y = (d.heightPixels * 0.30f).toInt()
        }

        var downX = 0f
        var downY = 0f
        var startX = 0
        var startY = 0
        var moved = false
        box.setOnTouchListener { _, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = e.rawX; downY = e.rawY; startX = lp.x; startY = lp.y; moved = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = e.rawX - downX
                    val dy = e.rawY - downY
                    if (abs(dx) > 8 * dp || abs(dy) > 8 * dp) moved = true
                    lp.x = startX + dx.toInt()
                    lp.y = startY + dy.toInt()
                    try { wm.updateViewLayout(box, lp) } catch (ex: Exception) { }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!moved) {
                        dismissed = true
                        hide()
                    }
                    true
                }
                else -> false
            }
        }

        try {
            wm.addView(box, lp)
        } catch (ex: Exception) {
            return false
        }
        root = box
        bubble = tv
        animator = ObjectAnimator.ofFloat(img, View.TRANSLATION_Y, -8 * dp, 8 * dp).apply {
            duration = 1400
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            start()
        }
        return true
    }

    fun hide() {
        main.removeCallbacks(autoHide)
        animator?.cancel()
        animator = null
        root?.let { try { wm.removeView(it) } catch (ex: Exception) { } }
        root = null
        bubble = null
    }
}
