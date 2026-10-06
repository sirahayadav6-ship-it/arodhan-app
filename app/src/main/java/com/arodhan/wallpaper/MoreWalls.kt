package com.arodhan.wallpaper

import android.content.res.AssetManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

fun makeWall(id: Int, d: Float, th: Theme, am: AssetManager): Wall = when (id) {
    1 -> TesseractWall(d, th)
    2 -> OceanWall(d, th)
    3 -> NebulaWall(d, th)
    4 -> HorizonWall(d, th)
    5 -> GalaxyWall(d, th)
    6 -> MatrixWall(d, th)
    7 -> AuroraWall(d, th)
    8 -> PhotoWall(d, th, am)
    else -> LatticeWall(d, th)
}

/** Shared star field used by several scenes. */
private class StarField(n: Int, seed: Int) {
    val x = FloatArray(n); val y = FloatArray(n); val r = FloatArray(n); val ph = FloatArray(n)
    val count = n
    init {
        val q = kotlin.random.Random(seed)
        for (i in 0 until n) { x[i] = q.nextFloat(); y[i] = q.nextFloat(); r[i] = .4f + q.nextFloat() * 1.3f; ph[i] = q.nextFloat() * 6f }
    }
    fun draw(g: Gfx, c: Canvas, w: Int, h: Int, t: Float, sx: Float, sy: Float, maxY: Float) {
        g.p.style = Paint.Style.FILL
        for (i in 0 until count) {
            if (y[i] > maxY) continue
            val a = .2f + .8f * abs(sin(t * 1.2f + ph[i]))
            g.p.color = Color.argb((a * 255f).toInt().coerceIn(0, 255), 255, 255, 255)
            c.drawCircle(x[i] * w + sx * r[i], y[i] * h + sy * r[i], r[i] * g.d, g.p)
        }
    }
}

// =====================================================================
class TesseractWall(d: Float, th: Theme) : Wall(d, th) {
    private val vx = Array(16) { i ->
        floatArrayOf(
            if ((i and 1) != 0) 1f else -1f, if ((i and 2) != 0) 1f else -1f,
            if ((i and 4) != 0) 1f else -1f, if ((i and 8) != 0) 1f else -1f
        )
    }
    private val ea = IntArray(32)
    private val eb = IntArray(32)
    private val px = FloatArray(16); private val py = FloatArray(16)
    private val pw = FloatArray(16); private val ps = FloatArray(16)
    private val tmp = FloatArray(4)
    private val stars = StarField(80, 3)
    private var bg: Shader? = null
    private val dot = glow(th.acc[0], 0.9f)

    init {
        var e = 0
        for (i in 0 until 16) for (b in 0 until 4) {
            val j = i xor (1 shl b)
            if (j > i) { ea[e] = i; eb[e] = j; e++ }
        }
    }

    override fun resize(width: Int, height: Int) {
        super.resize(width, height)
        bg = RadialGradient(
            w / 2f, h * .38f, h * .75f,
            intArrayOf(th.bg[1], th.bg[0], Color.rgb(2, 2, 12)), floatArrayOf(0f, .6f, 1f), Shader.TileMode.CLAMP
        )
    }

    private fun rot(a: Int, b: Int, ang: Float) {
        val x = tmp[a]; val y = tmp[b]; val cs = cos(ang); val sn = sin(ang)
        tmp[a] = x * cs - y * sn; tmp[b] = x * sn + y * cs
    }

    private fun proj(t: Float, off: Float) {
        for (i in 0 until 16) {
            tmp[0] = vx[i][0]; tmp[1] = vx[i][1]; tmp[2] = vx[i][2]; tmp[3] = vx[i][3]
            rot(0, 3, t * .55f + off); rot(1, 3, t * .37f + off * .7f); rot(2, 3, t * .43f)
            rot(0, 1, t * .25f); rot(1, 2, t * .31f + off * .3f)
            val w4 = 4.2f / (4.2f - tmp[3])
            val x = tmp[0] * w4; val y = tmp[1] * w4; val z = tmp[2] * w4
            val f = 4.2f / (4.2f - z)
            px[i] = x * f; py[i] = y * f; pw[i] = tmp[3]; ps[i] = f * w4
        }
    }

    private fun cube(c: Canvas, sc: Float, t: Float, off: Float, alpha: Float, cx: Float, cy: Float) {
        proj(t, off)
        p.style = Paint.Style.STROKE
        p.strokeCap = Paint.Cap.ROUND
        for (e in 0 until 32) {
            val a = ea[e]; val b = eb[e]
            val wv = (pw[a] + pw[b]) / 2f
            val k = (wv + 1.6f) / 3.2f
            val s = (ps[a] + ps[b]) / 2f
            val al = alpha * (.45f + .55f * (1f - abs(wv) / 1.6f))
            p.strokeWidth = d * (3f + 3f * s) * 1.4f
            p.color = mixF(th.acc[0], th.acc[2], k, al * .22f)
            c.drawLine(cx + px[a] * sc, cy + py[a] * sc, cx + px[b] * sc, cy + py[b] * sc, p)
            p.strokeWidth = d * (1.1f + 1.1f * s)
            p.color = mixF(th.acc[0], th.acc[2], k, al)
            c.drawLine(cx + px[a] * sc, cy + py[a] * sc, cx + px[b] * sc, cy + py[b] * sc, p)
        }
        p.style = Paint.Style.FILL
        for (i in 0 until 16) {
            p.color = mixF(th.acc[0], th.acc[2], (pw[i] + 1.6f) / 3.2f, alpha)
            c.drawCircle(cx + px[i] * sc, cy + py[i] * sc, d * (1.8f + 2.6f * ps[i]), p)
        }
    }

    override fun draw(c: Canvas, t: Float, dt: Float, tx: Float, ty: Float, fancy: Boolean) {
        if (w == 0) return
        val wf = w.toFloat(); val hf = h.toFloat()
        val land = w > h
        val sx = tx * wf * .03f; val sy = ty * hf * .015f
        add(false)
        p.style = Paint.Style.FILL; p.shader = bg; p.color = Color.WHITE
        c.drawRect(0f, 0f, wf, hf, p); p.shader = null
        stars.draw(this, c, w, h, t, sx, sy, 1f)
        add(true)
        val cx = (if (land) wf * .62f else wf / 2f) + sx * 1.5f
        val cy = (if (land) hf * .45f else hf * .5f) + sy * 1.5f
        val base = min(wf, hf) * .17f
        val mode = ((t / 18f).toInt()) % 3
        if (mode == 0) cube(c, base * 1.3f, t, 0f, 1f, cx, cy)
        else if (mode == 1) {
            for (k in 6 downTo 0) {
                val f = (k + ((t * .35f) % 1f)) / 7f
                cube(c, base * (.2f + 1.1f * f * f), t, k * .45f + t * .1f, .15f + .85f * (1f - f) * .9f, cx, cy)
            }
        } else {
            for (k in 0 until 3) {
                val a = t * .5f + k * 2.094f
                cube(c, base * .62f, t + k * .6f, k * 1.2f, .9f, cx + cos(a) * base * .9f, cy + sin(a) * base * .6f)
            }
        }
        spr(c, dot, cx, cy, base * 1.2f, .25f)
        add(false)
    }
}

// =====================================================================
class OceanWall(d: Float, th: Theme) : Wall(d, th) {
    private var bg: Shader? = null
    private val moon = glow(intArrayOf(190, 250, 255), .9f)
    private val stars = StarField(90, 5)
    private val bubX = FloatArray(24); private val bubY = FloatArray(24); private val bubR = FloatArray(24)
    private val bubV = FloatArray(24); private val bubP = FloatArray(24)
    private val wc = arrayOf(th.acc[0], th.cloud[3], th.acc[2], th.cloud[4])
    private val wa = floatArrayOf(.22f, .3f, .26f, .35f)

    init {
        for (i in 0 until 24) {
            bubX[i] = rnd.nextFloat(); bubY[i] = rnd.nextFloat(); bubR[i] = 1f + rnd.nextFloat() * 3f
            bubV[i] = .02f + rnd.nextFloat() * .04f; bubP[i] = rnd.nextFloat() * 6f
        }
    }

    override fun resize(width: Int, height: Int) {
        super.resize(width, height)
        bg = LinearGradient(0f, 0f, 0f, h.toFloat(), intArrayOf(Color.rgb(4, 6, 26), th.bg[0], th.bg[1]), floatArrayOf(0f, .5f, 1f), Shader.TileMode.CLAMP)
    }

    override fun draw(c: Canvas, t: Float, dt: Float, tx: Float, ty: Float, fancy: Boolean) {
        if (w == 0) return
        val wf = w.toFloat(); val hf = h.toFloat()
        val sx = tx * wf * .03f; val sy = ty * hf * .015f
        add(false)
        p.style = Paint.Style.FILL; p.shader = bg; p.color = Color.WHITE
        c.drawRect(0f, 0f, wf, hf, p); p.shader = null
        stars.draw(this, c, w, h, t, sx * .5f, sy * .5f, .45f)
        add(true)
        spr(c, moon, wf * .78f + sx, hf * .2f + sy, wf * .5f, .55f)
        add(false)
        p.style = Paint.Style.FILL; p.color = Color.rgb(235, 252, 255)
        c.drawCircle(wf * .78f + sx, hf * .2f + sy, wf * .045f, p)
        for (k in 0 until 4) {
            val y0 = hf * (.5f + k * .1f)
            val amp = hf * (.018f + k * .006f)
            val sp = .5f + k * .25f
            path.reset(); path.moveTo(0f, hf)
            var x = 0f
            while (x <= wf) {
                path.lineTo(x, y0 + sin(x / wf * 6f + t * sp + k) * amp + sin(x / wf * 13f - t * sp * 1.4f) * amp * .4f + sy * (k + 1))
                x += 8f
            }
            path.lineTo(wf, hf); path.close()
            p.style = Paint.Style.FILL; p.color = argbF(wc[k], wa[k]); c.drawPath(path, p)
            p.style = Paint.Style.STROKE; p.strokeWidth = d * 1.4f; p.color = argbF(wc[k], .7f); c.drawPath(path, p)
        }
        p.style = Paint.Style.STROKE; p.strokeWidth = d
        for (i in 0 until 24) {
            bubY[i] -= bubV[i] * .18f * dt
            bubX[i] += sin(t + bubP[i]) * .018f * dt
            if (bubY[i] < .45f) { bubY[i] = 1.02f; bubX[i] = rnd.nextFloat() }
            p.color = Color.argb(120, 180, 250, 255)
            c.drawCircle(bubX[i] * wf, bubY[i] * hf, bubR[i] * d, p)
        }
    }
}

// =====================================================================
class NebulaWall(d: Float, th: Theme) : Wall(d, th) {
    private val stars = StarField(150, 9)
    private val cl = arrayOf(glow(th.cloud[0], .5f), glow(th.cloud[3], .5f), glow(th.acc[0], .4f), glow(th.cloud[1], .35f))
    private val cx0 = floatArrayOf(.2f, .8f, .4f, .75f)
    private val cy0 = floatArrayOf(.25f, .55f, .85f, .15f)
    private val sz = floatArrayOf(.9f, .8f, .8f, .7f)
    private val trail = glow(intArrayOf(255, 255, 255), .9f)

    override fun draw(c: Canvas, t: Float, dt: Float, tx: Float, ty: Float, fancy: Boolean) {
        if (w == 0) return
        val wf = w.toFloat(); val hf = h.toFloat()
        val sx = tx * wf * .03f; val sy = ty * hf * .015f
        add(false)
        c.drawColor(Color.rgb(3, 3, 12))
        add(true)
        for (i in 0 until 4) {
            val r = max(wf, hf) * sz[i] * .6f
            spr(c, cl[i], wf * (cx0[i] + .05f * sin(t * .2f + i)) + sx * (1 + i), hf * (cy0[i] + .04f * cos(t * .15f + i)) + sy * (1 + i), r, 1f)
        }
        stars.draw(this, c, w, h, t, sx, sy, 1f)
        val ph = (t % 7f) / 1.2f
        if (ph < 1f) {
            val x = wf * (1.1f - ph * 1.3f); val y = hf * (.05f + ph * .35f)
            p.style = Paint.Style.STROKE; p.strokeWidth = d * 2f; p.strokeCap = Paint.Cap.ROUND
            p.shader = LinearGradient(x, y, x + 90f * d, y - 36f * d, Color.WHITE, Color.argb(0, 255, 255, 255), Shader.TileMode.CLAMP)
            c.drawLine(x, y, x + 90f * d, y - 36f * d, p)
            p.shader = null
            spr(c, trail, x, y, d * 8f, .9f)
        }
        add(false)
    }
}

// =====================================================================
class HorizonWall(d: Float, th: Theme) : Wall(d, th) {
    private var sky: Shader? = null
    private var sun: Shader? = null
    private var haze: Shader? = null
    private val stars = StarField(70, 11)

    override fun resize(width: Int, height: Int) {
        super.resize(width, height)
        val hz = h * .5f
        sky = LinearGradient(0f, 0f, 0f, hz, intArrayOf(Color.rgb(6, 4, 28), th.bg[1], argbF(th.acc[2], 1f)), floatArrayOf(0f, .7f, 1f), Shader.TileMode.CLAMP)
        val sy = hz - w * .02f; val r = w * .26f
        sun = LinearGradient(0f, sy - r, 0f, sy + r, intArrayOf(Color.rgb(255, 225, 77), Color.rgb(255, 106, 61), argbF(th.acc[2], 1f)), null, Shader.TileMode.CLAMP)
        haze = LinearGradient(0f, hz - 20f * d, 0f, hz + 30f * d, intArrayOf(argbF(th.acc[2], 0f), argbF(th.acc[2], .6f), argbF(th.acc[2], 0f)), null, Shader.TileMode.CLAMP)
    }

    override fun draw(c: Canvas, t: Float, dt: Float, tx: Float, ty: Float, fancy: Boolean) {
        if (w == 0) return
        val wf = w.toFloat(); val hf = h.toFloat()
        val hz = hf * .5f
        val sx = tx * wf * .03f
        add(false)
        p.style = Paint.Style.FILL; p.shader = sky; p.color = Color.WHITE
        c.drawRect(0f, 0f, wf, hz, p); p.shader = null
        p.color = Color.rgb(7, 4, 26); c.drawRect(0f, hz, wf, hf, p)
        stars.draw(this, c, w, h, t, sx, 0f, .45f)
        val sy = hz - wf * .02f; val r = wf * .26f
        c.save(); c.clipRect(0f, 0f, wf, hz)
        p.shader = sun; c.drawCircle(wf / 2f + sx, sy, r, p); p.shader = null
        p.color = th.bg[1]
        for (i in 0 until 7) {
            val yy = sy + r * (.05f + i * .14f)
            c.drawRect(0f, yy, wf, yy + r * .015f * (1f + i * .9f), p)
        }
        c.restore()
        p.style = Paint.Style.STROKE; p.strokeWidth = d * 1.6f
        for (k in -12..12) {
            p.color = argbF(th.acc[0], .85f)
            c.drawLine(wf / 2f + sx + k * wf * .02f, hz, wf / 2f + sx + k * wf * .35f, hf, p)
        }
        val off = (t * .6f) % 1f
        for (j in 0 until 14) {
            val f = (j + off) / 14f
            val y = hz + (hf - hz) * f * f
            p.color = argbF(th.acc[0], .25f + .75f * f)
            c.drawLine(0f, y, wf, y, p)
        }
        p.style = Paint.Style.FILL; p.shader = haze; p.color = Color.WHITE
        c.drawRect(0f, hz - 20f * d, wf, hz + 30f * d, p); p.shader = null
    }
}

// =====================================================================
class GalaxyWall(d: Float, th: Theme) : Wall(d, th) {
    private val NG = 2200
    private val gr = FloatArray(NG); private val ga = FloatArray(NG)
    private val gn = FloatArray(NG); private val gc = IntArray(NG)
    private val buf = Array(3) { FloatArray(NG * 2) }
    private val cnt = IntArray(3)
    private val stars = StarField(120, 13)
    private val core = glow(intArrayOf(255, 210, 150), .9f)
    private val halo = glow(th.acc[2], .6f)
    private var bg: Shader? = null

    init {
        for (i in 0 until NG) {
            val u = rnd.nextFloat()
            gr[i] = (u * u) * .95f + .03f
            val arm = rnd.nextInt(3)
            ga[i] = arm * 2.0944f + gr[i] * 7f + (rnd.nextFloat() - .5f) * (.35f + gr[i] * .5f)
            gn[i] = (rnd.nextFloat() - .5f) * .05f
            gc[i] = if (gr[i] < .2f) 0 else if (gr[i] < .55f) 1 else 2
        }
    }

    override fun resize(width: Int, height: Int) {
        super.resize(width, height)
        bg = RadialGradient(w / 2f, h * .45f, max(w, h) * .8f, intArrayOf(th.bg[1], th.bg[0], Color.rgb(2, 2, 10)), floatArrayOf(0f, .55f, 1f), Shader.TileMode.CLAMP)
    }

    override fun draw(c: Canvas, t: Float, dt: Float, tx: Float, ty: Float, fancy: Boolean) {
        if (w == 0) return
        val wf = w.toFloat(); val hf = h.toFloat()
        val land = w > h
        val sx = tx * wf * .03f; val sy = ty * hf * .015f
        add(false)
        p.style = Paint.Style.FILL; p.shader = bg; p.color = Color.WHITE
        c.drawRect(0f, 0f, wf, hf, p); p.shader = null
        stars.draw(this, c, w, h, t, sx, sy, 1f)
        val cx = (if (land) wf * .5f else wf / 2f) + sx * 1.5f
        val cy = hf * .46f + sy * 1.5f
        val R = min(wf, hf) * (if (land) .46f else .5f)
        val tl = -.5f
        val ct = cos(tl); val st = sin(tl)
        cnt[0] = 0; cnt[1] = 0; cnt[2] = 0
        for (i in 0 until NG) {
            val a = ga[i] + t * .35f / (gr[i] + .3f)
            val x = cos(a) * (gr[i] + gn[i]) * R
            val y = sin(a) * (gr[i] + gn[i]) * R * .42f
            val k = gc[i]
            val n = cnt[k]
            buf[k][n] = cx + x * ct - y * st
            buf[k][n + 1] = cy + x * st + y * ct
            cnt[k] = n + 2
        }
        add(true)
        spr(c, halo, cx, cy, R * .9f, .55f)
        p.style = Paint.Style.STROKE; p.strokeCap = Paint.Cap.ROUND
        p.strokeWidth = d * 2.2f; p.color = Color.argb(215, 255, 230, 190); c.drawPoints(buf[0], 0, cnt[0], p)
        p.strokeWidth = d * 1.8f; p.color = argbF(th.acc[2], .8f); c.drawPoints(buf[1], 0, cnt[1], p)
        p.strokeWidth = d * 1.4f; p.color = argbF(th.acc[0], .75f); c.drawPoints(buf[2], 0, cnt[2], p)
        spr(c, core, cx, cy, R * (.3f + .03f * sin(t * 1.4f)), .85f)
        add(false)
    }
}

// =====================================================================
class MatrixWall(d: Float, th: Theme) : Wall(d, th) {
    private val glyphs = "01ABCDEFGHJKLMNPRSTUVXYZ0123456789<>+=*#".map { it.toString() }
    private var cols = 1
    private var rows = 1
    private var cell = 1f
    private var head = FloatArray(1)
    private var spd = FloatArray(1)
    private var len = IntArray(1)
    private var gl = IntArray(1)
    private val mono = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)

    override fun resize(width: Int, height: Int) {
        super.resize(width, height)
        cell = d * 15f
        cols = max(1, (w / cell).toInt())
        rows = max(1, (h / cell).toInt() + 2)
        head = FloatArray(cols) { -rnd.nextFloat() * rows }
        spd = FloatArray(cols) { 6f + rnd.nextFloat() * 14f }
        len = IntArray(cols) { 8 + rnd.nextInt(12) }
        gl = IntArray(cols * rows) { rnd.nextInt(glyphs.size) }
    }

    override fun draw(c: Canvas, t: Float, dt: Float, tx: Float, ty: Float, fancy: Boolean) {
        if (w == 0) return
        val wf = w.toFloat(); val hf = h.toFloat()
        add(false)
        p.style = Paint.Style.FILL
        p.shader = LinearGradient(0f, 0f, 0f, hf, Color.rgb(1, 8, 6), Color.rgb(2, 20, 12), Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, wf, hf, p); p.shader = null
        tp.typeface = mono; tp.textSize = cell * .95f; tp.textAlign = Paint.Align.CENTER; tp.style = Paint.Style.FILL
        val g = th.acc[3]
        for (i in 0 until cols) {
            head[i] += spd[i] * dt
            if (head[i] - len[i] > rows) { head[i] = -rnd.nextFloat() * rows * .5f; spd[i] = 6f + rnd.nextFloat() * 14f; len[i] = 8 + rnd.nextInt(12) }
            val hr = head[i].toInt()
            val x = i * cell + cell / 2f + tx * cell * 2f
            for (k in 0 until len[i]) {
                val row = hr - k
                if (row < 0 || row >= rows) continue
                if (rnd.nextInt(40) == 0) gl[i * rows + row] = rnd.nextInt(glyphs.size)
                val fade = 1f - k / len[i].toFloat()
                tp.color = if (k == 0) Color.argb(255, 235, 255, 240) else Color.argb((fade * fade * 230f).toInt().coerceIn(0, 255), g[0], g[1], g[2])
                c.drawText(glyphs[gl[i * rows + row]], x, (row + 1) * cell, tp)
            }
        }
    }
}

// =====================================================================
class AuroraWall(d: Float, th: Theme) : Wall(d, th) {
    private val stars = StarField(130, 17)
    private val NB = 5
    private val by = floatArrayOf(.2f, .28f, .35f, .24f, .4f)
    private val am = floatArrayOf(.07f, .09f, .06f, .08f, .05f)
    private val fr = floatArrayOf(5f, 7f, 4f, 9f, 6f)
    private val sp = floatArrayOf(.3f, .22f, .38f, .18f, .28f)
    private val ph = floatArrayOf(0f, 1.7f, 3.1f, 4.4f, 5.6f)
    private val col = arrayOf(th.acc[3], th.acc[0], th.cloud[3], th.acc[3], th.acc[2])
    private var bg: Shader? = null
    private val strands = FloatArray(80 * 4)
    private val glowS = glow(th.acc[0], .5f)

    override fun resize(width: Int, height: Int) {
        super.resize(width, height)
        bg = LinearGradient(0f, 0f, 0f, h.toFloat(), intArrayOf(Color.rgb(2, 4, 16), th.bg[0], th.bg[1]), floatArrayOf(0f, .55f, 1f), Shader.TileMode.CLAMP)
    }

    override fun draw(c: Canvas, t: Float, dt: Float, tx: Float, ty: Float, fancy: Boolean) {
        if (w == 0) return
        val wf = w.toFloat(); val hf = h.toFloat()
        val sx = tx * wf * .03f; val sy = ty * hf * .015f
        add(false)
        p.style = Paint.Style.FILL; p.shader = bg; p.color = Color.WHITE
        c.drawRect(0f, 0f, wf, hf, p); p.shader = null
        stars.draw(this, c, w, h, t, sx * .6f, sy * .6f, 1f)
        add(true)
        spr(c, glowS, wf * .5f, hf * 1.05f, wf * .9f, .5f)
        for (b in 0 until NB) {
            val steps = 48
            var n = 0
            val top = FloatArray(steps + 1)
            val bot = FloatArray(steps + 1)
            for (q in 0..steps) {
                val u = q / steps.toFloat()
                val y1 = hf * (by[b] + am[b] * sin(u * fr[b] + t * sp[b] + ph[b])) + sy * (b + 1)
                top[q] = y1
                bot[q] = y1 + hf * (.2f + .08f * sin(u * 3.3f + t * .4f + ph[b]))
            }
            path.reset(); path.moveTo(sx * (b + 1), top[0])
            for (q in 1..steps) path.lineTo(q / steps.toFloat() * wf + sx * (b + 1), top[q])
            for (q in steps downTo 0) path.lineTo(q / steps.toFloat() * wf + sx * (b + 1), bot[q])
            path.close()
            p.style = Paint.Style.FILL; p.color = argbF(col[b], .14f); c.drawPath(path, p)
            p.style = Paint.Style.STROKE; p.strokeWidth = d * 2.4f; p.strokeCap = Paint.Cap.ROUND
            p.color = argbF(col[b], .55f)
            path.reset(); path.moveTo(sx * (b + 1), top[0])
            for (q in 1..steps) path.lineTo(q / steps.toFloat() * wf + sx * (b + 1), top[q])
            c.drawPath(path, p)
            if (fancy) {
                n = 0
                for (q in 0 until 80) {
                    val u = q / 79f
                    val qi = (u * steps).toInt().coerceIn(0, steps)
                    val xx = u * wf + sx * (b + 1)
                    strands[n] = xx; strands[n + 1] = top[qi]; strands[n + 2] = xx; strands[n + 3] = bot[qi]; n += 4
                }
                p.strokeWidth = d * 1.2f; p.color = argbF(col[b], .09f)
                c.drawLines(strands, 0, n, p)
            }
        }
        add(false)
    }
}

// =====================================================================
/** Your own picture (put it at app/src/main/assets/photo.jpg). Falls back to Nebula if missing. */
class PhotoWall(d: Float, th: Theme, am: AssetManager) : Wall(d, th) {
    private var bmp: Bitmap? = null
    private val fb = NebulaWall(d, th)
    private val src = Rect()
    private val dst = RectF()
    private val core = glow(intArrayOf(255, 190, 120), .9f)
    private val sp = Array(3) { glow(th.acc[it], .9f) }
    private val bx = FloatArray(30); private val by = FloatArray(30); private val br = FloatArray(30)
    private val bv = FloatArray(30); private val bp2 = FloatArray(30); private val bk = IntArray(30)

    init {
        try {
            val s = am.open("photo.jpg")
            bmp = BitmapFactory.decodeStream(s)
            s.close()
        } catch (e: Throwable) { bmp = null }
        for (i in 0 until 30) {
            bx[i] = rnd.nextFloat(); by[i] = rnd.nextFloat(); br[i] = 4f + rnd.nextFloat() * 14f
            bv[i] = .01f + rnd.nextFloat() * .03f; bp2[i] = rnd.nextFloat() * 6f; bk[i] = rnd.nextInt(3)
        }
    }

    override fun resize(width: Int, height: Int) { super.resize(width, height); fb.resize(width, height) }

    override fun draw(c: Canvas, t: Float, dt: Float, tx: Float, ty: Float, fancy: Boolean) {
        val b = bmp
        if (b == null || w == 0) { fb.draw(c, t, dt, tx, ty, fancy); return }
        val wf = w.toFloat(); val hf = h.toFloat()
        val bw = b.width.toFloat(); val bh = b.height.toFloat()
        val s = max(wf / bw, hf / bh) * (1.08f + .03f * sin(t * .4f))
        val ox = (wf - bw * s) / 2f + sin(t * .3f) * wf * .01f + tx * wf * .03f
        val oy = (hf - bh * s) / 2f + cos(t * .25f) * hf * .008f + ty * hf * .015f
        add(false)
        c.drawColor(Color.BLACK)
        val n = 64
        for (i in 0 until n) {
            val sy0 = (i * b.height) / n
            val sy1 = ((i + 1) * b.height) / n
            src.set(0, sy0, b.width, sy1)
            val dx = sin(i * .13f + t * .9f) * d * 4f + sin(i * .05f - t * .5f) * d * 3f
            dst.set(ox + dx, oy + sy0 * s, ox + dx + bw * s, oy + sy1 * s + 1f)
            bp.alpha = 255
            c.drawBitmap(b, src, dst, bp)
        }
        add(true)
        c.save(); c.translate(wf / 2f, hf * .48f); c.rotate(sin(t * .18f) * 1.7f)
        val k2 = 1.05f + .03f * sin(t * .3f + 1f)
        c.scale(k2, k2)
        rf.set(-bw * s / 2f, -bh * s / 2f, bw * s / 2f, bh * s / 2f)
        bp.alpha = 56
        c.drawBitmap(b, null, rf, bp)
        c.restore()
        val pl = .5f + .5f * sin(t * 1.5f)
        spr(c, core, wf / 2f, hf * .48f, min(wf, hf) * (.2f + .06f * pl), .35f + .3f * pl)
        val q = (t % 4.5f) / 4.5f
        p.style = Paint.Style.STROKE; p.strokeWidth = d * (2f + 6f * (1f - q))
        p.color = Color.argb((.5f * (1f - q) * 255f).toInt().coerceIn(0, 255), 255, 200, 140)
        c.drawCircle(wf / 2f, hf * .48f, min(wf, hf) * .5f * q, p)
        for (i in 0 until 30) {
            by[i] -= bv[i] * .18f * dt
            bx[i] += sin(t * .4f + bp2[i]) * .024f * dt
            if (by[i] < -.05f) by[i] = 1.05f
            spr(c, sp[bk[i]], bx[i] * wf, by[i] * hf, br[i] * d, (.3f + .7f * abs(sin(t * .7f + bp2[i]))) * .6f)
        }
        add(false)
    }
}
