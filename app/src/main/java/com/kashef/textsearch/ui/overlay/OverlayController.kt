package com.kashef.textsearch.ui.overlay

import android.app.Application
import android.content.Context
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
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
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

/**
 * Kashef walking over other apps (needs the "Display over other apps" permission).
 * Shown only while the app is in the background and a search is running or just finished.
 * While searching he walks left and right across the screen; when finished he stops.
 * Drag to move him; tap to dismiss for the current search. Call on the main thread.
 *
 * Animation frames: if drawables named kashef_walk_1, kashef_walk_2, ... exist (a real walk cycle),
 * they are played in order. Without them, the existing bust image is used with a hopping motion.
 */
class OverlayController(private val app: Application) {
    private val wm = app.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val main = Handler(Looper.getMainLooper())
    private var root: LinearLayout? = null
    private var bubble: TextView? = null
    private var img: ImageView? = null
    private var lp: WindowManager.LayoutParams? = null

    private var dismissed = false
    private var walking = false
    private var dragging = false
    private var dir = 1
    private var xf = 0f
    private var phase = 0f
    private var frameIdx = 0
    private var frameAcc = 0L
    private var lastTick = 0L

    private val frames: List<Drawable> by lazy { loadFrames() }

    private val autoHide = Runnable {
        dismissed = true
        hide()
    }
    private val tick = object : Runnable {
        override fun run() {
            step()
            main.postDelayed(this, 33)
        }
    }

    fun canDraw(): Boolean = Settings.canDrawOverlays(app)

    fun newRun() {
        dismissed = false
    }

    private fun loadFrames(): List<Drawable> {
        val out = ArrayList<Drawable>()
        for (i in 1..12) {
            val id = app.resources.getIdentifier("kashef_walk_$i", "drawable", app.packageName)
            if (id == 0) break
            out.add(app.resources.getDrawable(id, null))
        }
        return out
    }

    fun render(ui: ScanUi?, allowed: Boolean) {
        if (ui == null || !allowed || dismissed || !canDraw()) {
            hide()
            return
        }
        if (!ensure()) return
        walking = ui.stage == ScanStage.Running
        val s = ui.snap
        bubble?.text = dialogue(ui) + "\n" + "فُحص ${s.scanned} ملفًا • مطابق: ${s.matchedFiles}"
        main.removeCallbacks(autoHide)
        if (ui.stage != ScanStage.Running) main.postDelayed(autoHide, 8000)
    }

    @Suppress("DEPRECATION")
    private fun overlayType(): Int =
        if (Build.VERSION.SDK_INT >= 26) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else WindowManager.LayoutParams.TYPE_PHONE

    private fun step() {
        val v = root ?: return
        val p = lp ?: return
        val im = img ?: return
        val d = app.resources.displayMetrics
        val dp = d.density
        val now = SystemClock.uptimeMillis()
        val dt = if (lastTick == 0L) 33L else (now - lastTick).coerceAtMost(100L)
        lastTick = now

        phase += dt / 1000f * (if (walking) 15.7f else 3.5f)
        if (walking) {
            im.translationY = -abs(sin(phase)) * 6 * dp
            im.rotation = sin(phase) * 4f
            if (frames.isNotEmpty()) {
                frameAcc += dt
                if (frameAcc >= 120) {
                    frameAcc = 0
                    frameIdx = (frameIdx + 1) % frames.size
                    im.setImageDrawable(frames[frameIdx])
                }
            }
        } else {
            im.translationY = sin(phase) * 4 * dp
            im.rotation = 0f
        }

        if (walking && !dragging) {
            val w = if (v.width > 0) v.width else (240 * dp).toInt()
            xf += dir * 70 * dp * dt / 1000f
            val maxX = (d.widthPixels - w).coerceAtLeast(0).toFloat()
            if (xf >= maxX) { xf = maxX; dir = -1 }
            if (xf <= 0f) { xf = 0f; dir = 1 }
            im.scaleX = if (dir > 0) 1f else -1f // the artwork faces right
            p.x = xf.toInt()
            try { wm.updateViewLayout(v, p) } catch (ex: Exception) { }
        }
    }

    private fun ensure(): Boolean {
        if (root != null) return true
        val d = app.resources.displayMetrics
        val dp = d.density

        val tv = TextView(app).apply {
            setTextColor(0xFFEAF1FF.toInt())
            textSize = 12f
            maxWidth = (210 * dp).toInt()
            setPadding((10 * dp).toInt(), (6 * dp).toInt(), (10 * dp).toInt(), (6 * dp).toInt())
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            textDirection = View.TEXT_DIRECTION_RTL
            background = GradientDrawable().apply {
                setColor(0xEE0B1A40.toInt())
                cornerRadius = 14 * dp
                setStroke((1 * dp).toInt(), 0xFF3D8BFF.toInt())
            }
        }
        val im = ImageView(app).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            if (frames.isNotEmpty()) setImageDrawable(frames[0]) else setImageResource(R.drawable.kashef_character)
        }
        val box = LinearLayout(app).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            addView(tv, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT))
            addView(im, LinearLayout.LayoutParams((100 * dp).toInt(), (150 * dp).toInt()))
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = (d.heightPixels * 0.60f).toInt()
        }
        xf = 0f
        dir = 1

        var downX = 0f
        var downY = 0f
        var startX = 0
        var startY = 0
        var moved = false
        box.setOnTouchListener { _, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    dragging = true
                    downX = e.rawX; downY = e.rawY; startX = params.x; startY = params.y; moved = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = e.rawX - downX
                    val dy = e.rawY - downY
                    if (abs(dx) > 8 * dp || abs(dy) > 8 * dp) moved = true
                    params.x = startX + dx.toInt()
                    params.y = startY + dy.toInt()
                    xf = params.x.toFloat()
                    try { wm.updateViewLayout(box, params) } catch (ex: Exception) { }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    dragging = false
                    if (!moved && e.actionMasked == MotionEvent.ACTION_UP) {
                        dismissed = true
                        hide()
                    }
                    true
                }
                else -> false
            }
        }

        try {
            wm.addView(box, params)
        } catch (ex: Exception) {
            return false
        }
        root = box
        bubble = tv
        img = im
        lp = params
        lastTick = 0L
        main.removeCallbacks(tick)
        main.postDelayed(tick, 33)
        return true
    }

    fun hide() {
        main.removeCallbacks(autoHide)
        main.removeCallbacks(tick)
        root?.let { try { wm.removeView(it) } catch (ex: Exception) { } }
        root = null
        bubble = null
        img = null
        lp = null
        walking = false
        dragging = false
    }
}
