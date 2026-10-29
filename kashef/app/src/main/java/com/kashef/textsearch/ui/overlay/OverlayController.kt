package com.kashef.textsearch.ui.overlay

import android.animation.Animator
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.app.Application
import android.content.Context
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.text.TextUtils
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.animation.LinearInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.kashef.textsearch.R
import com.kashef.textsearch.ui.search.ScanStage
import com.kashef.textsearch.ui.search.ScanUi
import com.kashef.textsearch.ui.search.dialogue
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

/**
 * Full-body Kashef investigating over other apps (needs the "Display over other apps" permission).
 * Shown only while the app is in the background and a search is running or just finished.
 *
 * While searching he walks left and right (legs swing smoothly from the hips), a ring pulses on his magnifier lens,
 * and a chip shows the name of the file that is REALLY being scanned. He stops when the search ends.
 * Drag to move; tap to dismiss for the current search. Call on the main thread.
 */
class OverlayController(private val app: Application) {
    private val wm = app.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val main = Handler(Looper.getMainLooper())
    private val dp = app.resources.displayMetrics.density

    private companion object {
        const val BODY_W = 92 // dp; the artwork is 321x800 so 92x230 keeps its proportions
        const val BODY_H = 230
        const val BUBBLE_W = 210
        val CODE_EXT = setOf(
            "kt", "kts", "java", "js", "ts", "py", "c", "cpp", "h", "cs", "go", "rs", "php", "rb", "sql",
            "sh", "bat", "xml", "html", "htm", "css", "json", "yml", "yaml", "gradle", "properties",
        )
    }

    private var root: LinearLayout? = null
    private var bubble: TextView? = null
    private var chip: TextView? = null
    private var charBox: FrameLayout? = null
    private var ring: View? = null
    private var upper: FrameLayout? = null // torso + lens ring: they lean together
    private var hipL: FrameLayout? = null
    private var hipR: FrameLayout? = null
    private var kneeL: ImageView? = null
    private var kneeR: ImageView? = null
    private var lp: WindowManager.LayoutParams? = null
    private val animators = ArrayList<Animator>()

    private var dismissed = false
    private var running = false
    private var dragging = false
    private var dir = 1
    private var xf = 0f
    private var lastFrame = 0L
    private var phase = 0f
    private var lastName = ""

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
        val nowRunning = ui.stage == ScanStage.Running
        val s = ui.snap
        bubble?.text = dialogue(ui) + "\n" + "فُحص ${s.scanned} ملفًا • مطابق: ${s.matchedFiles}"
        updateChip(nowRunning, s.current)
        setRunning(nowRunning, ui.stage == ScanStage.Finished && s.matchedFiles > 0)
        main.removeCallbacks(autoHide)
        if (!nowRunning) main.postDelayed(autoHide, 8000)
    }

    private fun updateChip(isRunning: Boolean, name: String) {
        val c = chip ?: return
        if (!isRunning || name.isBlank()) {
            c.visibility = View.INVISIBLE // INVISIBLE keeps the layout from jumping
            lastName = ""
            return
        }
        if (name != lastName) {
            lastName = name
            val ext = name.substringAfterLast('.', "").lowercase(Locale.ROOT)
            val res = if (ext in CODE_EXT) R.drawable.ic_pile_code else R.drawable.ic_pile_doc
            app.resources.getDrawable(res, null).let { dr ->
                dr.setBounds(0, 0, (16 * dp).toInt(), (16 * dp).toInt())
                c.setCompoundDrawablesRelative(dr, null, null, null)
            }
            c.text = name
            c.visibility = View.VISIBLE
            c.alpha = 0f
            c.translationX = 40 * dp
            c.animate().alpha(1f).translationX(0f).setDuration(220).start()
        }
    }

    private fun loop(a: ObjectAnimator, ms: Long) {
        a.duration = ms
        a.repeatCount = ValueAnimator.INFINITE
        a.repeatMode = ValueAnimator.REVERSE
        a.start()
        animators.add(a)
    }

    private fun setRunning(nowRunning: Boolean, celebrate: Boolean) {
        val box = charBox ?: return
        if (nowRunning && !running) {
            box.pivotX = BODY_W * dp / 2
            box.pivotY = BODY_H * dp
            // Walk cycle: one vsync-driven animator moves the window and swings the legs together.
            lastFrame = 0L
            val walker = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = 1000
                repeatCount = ValueAnimator.INFINITE
                interpolator = LinearInterpolator()
                addUpdateListener { onFrame() }
                start()
            }
            animators.add(walker)
            ring?.let { r ->
                r.visibility = View.VISIBLE
                loop(ObjectAnimator.ofFloat(r, View.SCALE_X, 0.9f, 1.25f), 450)
                loop(ObjectAnimator.ofFloat(r, View.SCALE_Y, 0.9f, 1.25f), 450)
                loop(ObjectAnimator.ofFloat(r, View.ALPHA, 0.5f, 1f), 450)
            }
        } else if (!nowRunning && running) {
            stopAnims()
        }
        running = nowRunning
        if (!nowRunning) box.rotation = if (celebrate) -5f else 0f
    }

    private fun stopAnims() {
        animators.forEach { it.cancel() }
        animators.clear()
        ring?.apply { visibility = View.INVISIBLE; scaleX = 1f; scaleY = 1f; alpha = 1f }
        charBox?.apply { rotation = 0f; translationY = 0f }
        listOfNotNull<View>(upper, hipL, hipR, kneeL, kneeR).forEach { it.rotation = 0f }
    }

    private fun ease(u: Float) = u * u * (3f - 2f * u)

    /**
     * Stance (t in 0..0.6): the foot is planted and the leg swings from forward to back at the same
     * rate the body moves, so the foot does not slide; the knee flexes only slightly.
     * Swing (t in 0.6..1): the hip swings the leg forward while the KNEE BENDS (the shin folds back)
     * and then straightens before the foot lands.
     * Rotation is negative toward the facing direction (right); a positive knee angle folds the shin back.
     */
    private fun pose(hip: View?, knee: View?, t: Float) {
        if (hip == null || knee == null) return
        val reach = 14f
        val stance = 0.6f
        val pi = PI.toFloat()
        if (t < stance) {
            val k = t / stance
            hip.rotation = -reach + 2f * reach * k
            knee.rotation = 4f * sin(pi * k)
        } else {
            val u = (t - stance) / (1f - stance)
            hip.rotation = reach - 2f * reach * ease(u)
            knee.rotation = 32f * sin(pi * u)
        }
    }

    /** One animation frame, driven by the display's vsync (ValueAnimator), so motion is smooth. */
    private fun onFrame() {
        val v = root ?: return
        val p = lp ?: return
        val box = charBox ?: return
        val now = SystemClock.uptimeMillis()
        val dt = if (lastFrame == 0L) 16L else (now - lastFrame).coerceIn(1L, 64L)
        lastFrame = now

        // Gait: one full cycle (two steps) takes 1.2 s. Each leg is on the ground for 60% of its cycle,
        // the other leg is offset by half a cycle, so there is a short moment with both feet down.
        phase += dt / 1200f
        phase -= kotlin.math.floor(phase)
        pose(hipL, kneeL, phase)
        pose(hipR, kneeR, (phase + 0.5f) % 1f)
        upper?.rotation = 2f // slight forward lean; the lens ring leans with it

        if (dragging) return
        val screenW = app.resources.displayMetrics.widthPixels
        val w = if (v.width > 0) v.width else (BUBBLE_W * dp).toInt()
        xf += dir * 34 * dp * dt / 1000f // matches the planted foot speed of the gait
        val maxX = (screenW - w).coerceAtLeast(0).toFloat()
        if (xf >= maxX) { xf = maxX; dir = -1 }
        if (xf <= 0f) { xf = 0f; dir = 1 }
        box.scaleX = if (dir > 0) 1f else -1f // the artwork faces right; the lens ring flips with it
        p.x = xf.toInt()
        try { wm.updateViewLayout(v, p) } catch (ex: Exception) { }
    }

    @Suppress("DEPRECATION")
    private fun overlayType(): Int =
        if (Build.VERSION.SDK_INT >= 26) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else WindowManager.LayoutParams.TYPE_PHONE

    private fun pill(fill: Int, stroke: Int, radiusDp: Float) = GradientDrawable().apply {
        setColor(fill)
        cornerRadius = radiusDp * dp
        setStroke((1 * dp).toInt(), stroke)
    }

    private fun ensure(): Boolean {
        if (root != null) return true
        val d = app.resources.displayMetrics

        val tv = TextView(app).apply {
            setTextColor(0xFFEAF1FF.toInt())
            textSize = 12f
            setLines(3) // fixed height keeps Kashef from jumping when the text changes
            ellipsize = TextUtils.TruncateAt.END
            setPadding((10 * dp).toInt(), (6 * dp).toInt(), (10 * dp).toInt(), (6 * dp).toInt())
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            textDirection = View.TEXT_DIRECTION_RTL
            background = pill(0xEE0B1A40.toInt(), 0xFF3D8BFF.toInt(), 14f)
        }
        val fileChip = TextView(app).apply {
            setTextColor(0xFFE8B84A.toInt())
            textSize = 11f
            isSingleLine = true
            ellipsize = TextUtils.TruncateAt.MIDDLE
            maxWidth = (200 * dp).toInt()
            compoundDrawablePadding = (6 * dp).toInt()
            setPadding((10 * dp).toInt(), (3 * dp).toInt(), (10 * dp).toInt(), (3 * dp).toInt())
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            textDirection = View.TEXT_DIRECTION_RTL
            background = pill(0xCC081535.toInt(), 0xFFE8B84A.toInt(), 12f)
            visibility = View.INVISIBLE
            text = " "
        }

        // Full-body character and the lens ring, in one box so they flip together.
        // Lens centre in the artwork: 70.9% across, 22.3% down.
        val box = FrameLayout(app)
        box.clipChildren = false // the swinging legs may extend past the box
        val bw = (BODY_W * dp).toInt()
        val bh = (BODY_H * dp).toInt()
        fun layer(res: Int) = ImageView(app).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            setImageResource(res)
        }
        fun full() = FrameLayout.LayoutParams(bw, bh, Gravity.TOP or Gravity.START)

        // One leg = a hip container (rotates at the hip) holding the shin (rotates at the knee) behind
        // the thigh, whose round cap hides the joint. Pivots are fractions of the artwork, measured on the pieces.
        fun leg(thigh: Int, shin: Int, hipX: Float, hipY: Float, kneeX: Float, kneeY: Float): Pair<FrameLayout, ImageView> {
            val shinV = layer(shin).apply { pivotX = kneeX * bw; pivotY = kneeY * bh }
            val hip = FrameLayout(app).apply {
                clipChildren = false
                pivotX = hipX * bw; pivotY = hipY * bh
                addView(shinV, full())
                addView(layer(thigh), full())
            }
            return hip to shinV
        }
        val (hipRView, kneeRView) = leg(R.drawable.kashef_thigh_r, R.drawable.kashef_shin_r, 0.6881f, 0.7158f, 0.6849f, 0.8101f)
        val (hipLView, kneeLView) = leg(R.drawable.kashef_thigh_l, R.drawable.kashef_shin_l, 0.3682f, 0.7158f, 0.2918f, 0.8101f)

        // Torso and the lens ring lean together, so the ring stays on the lens (lens: 70.9% across, 22.3% down).
        val upperView = FrameLayout(app).apply {
            clipChildren = false
            pivotX = bw / 2f; pivotY = bh.toFloat()
        }
        upperView.addView(layer(R.drawable.kashef_torso), full())
        val ringDp = 22
        val lensRing = View(app).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(0x22E8B84A)
                setStroke((3 * dp).toInt(), 0xFFE8B84A.toInt())
            }
            visibility = View.INVISIBLE
        }
        upperView.addView(
            lensRing,
            FrameLayout.LayoutParams((ringDp * dp).toInt(), (ringDp * dp).toInt(), Gravity.TOP or Gravity.START).apply {
                leftMargin = ((0.709f * BODY_W - ringDp / 2f) * dp).toInt()
                topMargin = ((0.223f * BODY_H - ringDp / 2f) * dp).toInt()
            },
        )
        // Legs sit behind the torso so the coat hem hides the hips.
        box.addView(hipRView, full())
        box.addView(hipLView, full())
        box.addView(upperView, full())

        val column = LinearLayout(app).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            clipChildren = false
            addView(tv, LinearLayout.LayoutParams((BUBBLE_W * dp).toInt(), LinearLayout.LayoutParams.WRAP_CONTENT))
            addView(
                fileChip,
                LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                    .apply { topMargin = (4 * dp).toInt(); bottomMargin = (2 * dp).toInt() },
            )
            addView(box, LinearLayout.LayoutParams((BODY_W * dp).toInt(), (BODY_H * dp).toInt()))
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = ((d.widthPixels - BUBBLE_W * dp) / 2).toInt().coerceAtLeast(0)
            y = (d.heightPixels * 0.42f).toInt()
        }
        xf = params.x.toFloat()
        dir = 1

        var downX = 0f
        var downY = 0f
        var startX = 0
        var startY = 0
        var moved = false
        column.setOnTouchListener { _, e ->
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
                    try { wm.updateViewLayout(column, params) } catch (ex: Exception) { }
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
            wm.addView(column, params)
        } catch (ex: Exception) {
            return false
        }
        root = column
        bubble = tv
        chip = fileChip
        charBox = box
        ring = lensRing
        upper = upperView
        hipL = hipLView
        hipR = hipRView
        kneeL = kneeLView
        kneeR = kneeRView
        lp = params
        running = false
        lastName = ""
        return true
    }

    fun hide() {
        main.removeCallbacks(autoHide)
        stopAnims()
        root?.let { try { wm.removeView(it) } catch (ex: Exception) { } }
        root = null
        bubble = null
        chip = null
        charBox = null
        ring = null
        upper = null
        hipL = null
        hipR = null
        kneeL = null
        kneeR = null
        lp = null
        running = false
        dragging = false
        lastName = ""
    }
}
