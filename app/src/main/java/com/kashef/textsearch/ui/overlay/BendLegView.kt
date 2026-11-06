package com.kashef.textsearch.ui.overlay

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.view.View
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * One trouser leg drawn as a bitmap MESH so that it bends smoothly at the knee like a flexible
 * leg, with no hard joint. The whole leg rotates by [hip] degrees from its top, below the knee
 * the rotation blends smoothly into [hip] + [knee] degrees, and the foot adds [ankle] degrees
 * (positive = clockwise = foot goes left).
 *
 * The sprite is placed inside the 321x800 artwork canvas at ([offX], [offY]); [kneeRow] is the knee
 * row inside the sprite (in bitmap pixels). The view must be laid out as the full body box.
 */
class BendLegView(
    context: Context,
    private val bmp: Bitmap,
    private val offX: Float,
    private val offY: Float,
    private val kneeRow: Float,
    private val ankleRow: Float,
) : View(context) {
    private companion object {
        const val CANVAS_W = 321f
        const val CANVAS_H = 800f
        const val ZONE = 36.2f // half-width of the knee bend zone, in bitmap pixels
        const val ANKLE_ZONE = 8f
        const val N = 40       // mesh rows
    }

    var hip = 0f
        set(v) { if (field != v) { field = v; invalidate() } }
    var knee = 0f
        set(v) { if (field != v) { field = v; invalidate() } }
    /** Extra rotation of the foot below the ankle row (positive = clockwise). */
    var ankle = 0f
        set(v) { if (field != v) { field = v; invalidate() } }

    private val rows = FloatArray(N + 1)
    private val cx = FloatArray(N + 1)   // leg axis (centre of the leg) per mesh row, in bitmap pixels
    private val verts = FloatArray((N + 1) * 2 * 2)
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)

    init {
        val w = bmp.width
        val h = bmp.height
        val line = IntArray(w)
        for (k in 0..N) {
            rows[k] = k * h.toFloat() / N
            val y = min(rows[k].toInt(), h - 1)
            bmp.getPixels(line, 0, w, 0, y, w, 1)
            var first = -1
            var last = -1
            for (x in 0 until w) {
                if ((line[x] ushr 24) > 128) {
                    if (first < 0) first = x
                    last = x
                }
            }
            cx[k] = if (first >= 0) (first + last) / 2f else if (k > 0) cx[k - 1] else w / 2f
        }
        repeat(2) { // light smoothing of the axis
            val c = cx.copyOf()
            for (k in 1 until N) cx[k] = (c[k - 1] + c[k] + c[k + 1]) / 3f
        }
    }

    private fun smooth(t: Float): Float {
        val x = t.coerceIn(0f, 1f)
        return x * x * (3f - 2f * x)
    }

    override fun onDraw(canvas: Canvas) {
        val k = min(width / CANVAS_W, height / CANVAS_H)
        val ox = (width - CANVAS_W * k) / 2f + offX * k
        val oy = (height - CANVAS_H * k) / 2f + offY * k
        val w = bmp.width.toFloat()

        // Forward kinematics along the leg axis. With zero angles this reproduces the original sprite exactly.
        var px = cx[0]
        var py = 0f
        for (i in 0..N) {
            val t = ((rows[i] - kneeRow) / ZONE + 1f) / 2f
            val ta = ((rows[i] - ankleRow) / ANKLE_ZONE + 1f) / 2f
            val th = Math.toRadians((hip + knee * smooth(t) + ankle * smooth(ta)).toDouble())
            val c = cos(th).toFloat()
            val s = sin(th).toFloat()
            val dl = -cx[i]
            val dr = w - cx[i]
            val a = (i * 2) * 2
            verts[a] = ox + (px + dl * c) * k
            verts[a + 1] = oy + (py + dl * s) * k
            verts[a + 2] = ox + (px + dr * c) * k
            verts[a + 3] = oy + (py + dr * s) * k
            if (i < N) {
                val dx = cx[i + 1] - cx[i]
                val dy = rows[i + 1] - rows[i]
                px += dx * c - dy * s
                py += dx * s + dy * c
            }
        }
        canvas.drawBitmapMesh(bmp, 1, N, verts, 0, null, 0, paint)
    }
}
