package com.arodhan.wallpaper

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Xfermode
import kotlin.random.Random

/** Shared drawing helpers (paints, glow sprites, additive blending). */
open class Gfx(val d: Float) {
    val rnd = Random(7)
    val p = Paint(Paint.ANTI_ALIAS_FLAG)
    val bp = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
    val tp = Paint(Paint.ANTI_ALIAS_FLAG)
    private val addMode = PorterDuffXfermode(PorterDuff.Mode.ADD)
    val rf = RectF()
    val path = Path()
    val mat = Matrix()

    fun add(on: Boolean) {
        val m: Xfermode? = if (on) addMode else null
        p.xfermode = m
        bp.xfermode = m
    }

    fun spr(c: Canvas, b: Bitmap, x: Float, y: Float, r: Float, a: Float) {
        bp.alpha = (a.coerceIn(0f, 1f) * 255f).toInt()
        rf.set(x - r, y - r, x + r, y + r)
        c.drawBitmap(b, null, rf, bp)
    }

    fun glow(col: IntArray, a: Float): Bitmap {
        val bm = Bitmap.createBitmap(128, 128, Bitmap.Config.ARGB_8888)
        val cv = Canvas(bm)
        val pt = Paint(Paint.ANTI_ALIAS_FLAG)
        val a1 = (a * 255f).toInt().coerceIn(0, 255)
        pt.shader = RadialGradient(
            64f, 64f, 64f,
            intArrayOf(
                Color.argb(a1, col[0], col[1], col[2]),
                Color.argb((a1 * 0.4f).toInt(), col[0], col[1], col[2]),
                Color.argb(0, col[0], col[1], col[2])
            ),
            floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP
        )
        cv.drawRect(0f, 0f, 128f, 128f, pt)
        return bm
    }

    fun ball(col: IntArray): Bitmap {
        val r = col[0]; val g = col[1]; val b = col[2]
        val bm = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
        val cv = Canvas(bm)
        val pt = Paint(Paint.ANTI_ALIAS_FLAG)
        pt.shader = RadialGradient(
            25f, 23f, 40f,
            intArrayOf(
                Color.WHITE,
                Color.rgb(r, g, b),
                Color.rgb((r * 0.3f).toInt(), (g * 0.3f).toInt(), (b * 0.45f).toInt()),
                Color.argb(0, (r * 0.15f).toInt(), (g * 0.15f).toInt(), (b * 0.3f).toInt())
            ),
            floatArrayOf(0f, 0.2f, 0.75f, 1f), Shader.TileMode.CLAMP
        )
        cv.drawCircle(32f, 32f, 32f, pt)
        return bm
    }

    fun argbF(col: IntArray, a: Float): Int =
        Color.argb((a.coerceIn(0f, 1f) * 255f).toInt(), col[0], col[1], col[2])

    fun mixF(a: IntArray, b: IntArray, k: Float, alpha: Float): Int {
        val q = k.coerceIn(0f, 1f)
        return Color.argb(
            (alpha.coerceIn(0f, 1f) * 255f).toInt(),
            (a[0] + (b[0] - a[0]) * q).toInt(),
            (a[1] + (b[1] - a[1]) * q).toInt(),
            (a[2] + (b[2] - a[2]) * q).toInt()
        )
    }

    fun deg(r: Float): Float = r * 57.29578f
}

/** A wallpaper scene. tx, ty = phone tilt in [-1, 1] (parallax). */
abstract class Wall(d: Float, val th: Theme) : Gfx(d) {
    var w = 0
    var h = 0
    open fun resize(width: Int, height: Int) { w = width; h = height }
    abstract fun draw(c: Canvas, t: Float, dt: Float, tx: Float, ty: Float, fancy: Boolean)
}
