package com.arodhan.wallpaper

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

private class Wave(val x: Float, val y: Float, val t0: Float, val c: Int)
private class Bolt(val t0: Float, val pts: FloatArray, val c: Int)
private class Rip(val t0: Float, val si: Int, val sj: Int, val sk: Int, val c: Int)

/** Layered 3D folding lattice with energy effects. */
class LatticeWall(d: Float, th: Theme) : Wall(d, th) {

    private val PAL = th.acc
    private val LIGHT = th.light
    private val RIPC = intArrayOf(0, 0, 0, 1, 2)

    private val palS = Array(5) { glow(PAL[it], 0.9f) }
    private val lightS = Array(3) { glow(LIGHT[it], 0.9f) }
    private val coreS = glow(intArrayOf(255, 190, 120), 0.9f)
    private val blueS = glow(intArrayOf(120, 190, 255), 0.9f)
    private val ballWarm = ball(PAL[1])
    private val nodeBall = Array(3) { ball(LIGHT[it]) }
    private val nodeHalo = Array(3) { glow(LIGHT[it], 0.9f) }
    private val bokS = Array(3) { glow(PAL[it], 0.9f) }

    private val cloudBmp = ArrayList<Bitmap>()
    private val cloudP = ArrayList<FloatArray>()
    private fun cloud(col: IntArray, a: Float, x: Float, y: Float, sz: Float, par: Float) {
        cloudBmp.add(glow(col, a)); cloudP.add(floatArrayOf(x, y, sz, par))
    }

    private val NR = 26
    private val rbY = FloatArray(NR); private val rbA = FloatArray(NR); private val rbF = FloatArray(NR)
    private val rbS = FloatArray(NR); private val rbP = FloatArray(NR); private val rbW = FloatArray(NR)
    private val rbL = FloatArray(NR); private val rbSide = IntArray(NR)
    private val rbShader = arrayOfNulls<Shader>(NR)

    private val NST = 6
    private val stY = FloatArray(NST); private val stA = FloatArray(NST); private val stF = FloatArray(NST)
    private val stSp = FloatArray(NST); private val stPh = FloatArray(NST); private val stV = FloatArray(NST)
    private val stW = FloatArray(NST)

    private val NS = 206
    private val sX = FloatArray(NS); private val sY = FloatArray(NS); private val sR = FloatArray(NS)
    private val sPh = FloatArray(NS); private val sPar = FloatArray(NS); private val sCol = IntArray(NS)
    private val NB = 18
    private val bX = FloatArray(NB); private val bY = FloatArray(NB); private val bR = FloatArray(NB)
    private val bV = FloatArray(NB); private val bPh = FloatArray(NB); private val bK = IntArray(NB)
    private val ND = 14
    private val dX = FloatArray(ND); private val dY = FloatArray(ND); private val dR = FloatArray(ND)
    private val dV = FloatArray(ND); private val dPh = FloatArray(ND); private val dK = IntArray(ND)

    private val NN = 729
    private val bx = FloatArray(NN); private val by = FloatArray(NN); private val bz = FloatArray(NN)
    private val hotA = FloatArray(NN)
    private val gi = IntArray(NN); private val gj = IntArray(NN); private val gk = IntArray(NN)
    private val pX = FloatArray(NN); private val pY = FloatArray(NN); private val pS = FloatArray(NN)
    private val key = IntArray(NN); private val order = IntArray(NN); private val bkt = IntArray(65)
    private val cls = IntArray(NN)
    private val NE = 1944
    private val ea = IntArray(NE); private val eb = IntArray(NE); private val esh = BooleanArray(NE)
    private val eBucket = IntArray(NE)
    private val lineBuf = FloatArray(NE * 4)
    private val NRUN = 40
    private val ra = IntArray(NRUN); private val rb = IntArray(NRUN); private val rfa = FloatArray(NRUN)
    private val rv = FloatArray(NRUN); private val rc = IntArray(NRUN)
    private val cand = IntArray(6)

    private val waves = ArrayList<Wave>()
    private val bolts = ArrayList<Bolt>()
    private val rips = ArrayList<Rip>()
    private var lastSw = -9f
    private var nextBolt = 1f
    private var lastB = 0f
    private var lastRp = -9f
    private var rpN = 0

    private val tintR = intArrayOf(110, 150, 215)
    private val tintG = intArrayOf(140, 195, 232)
    private val tintB = intArrayOf(235, 255, 255)
    private val tintA = floatArrayOf(0.7f, 1f, 1.15f)
    private val lineW = floatArrayOf(0.5f, 0.75f, 1.05f)

    private var bgShader: Shader? = null
    private var rayShader: Shader? = null

    init {
        cloud(th.cloud[0], .36f, .15f, .85f, .9f, .25f)
        cloud(th.cloud[1], .34f, .9f, .72f, .85f, .25f)
        cloud(th.cloud[2], .36f, .1f, .5f, .8f, .25f)
        cloud(th.cloud[3], .36f, .85f, .35f, .8f, .4f)
        cloud(th.cloud[4], .34f, .5f, .05f, 1.1f, .25f)
        cloud(th.cloud[5], .24f, .5f, 1f, 1f, .4f)
        cloud(th.cloud[2], .22f, .3f, .25f, 1.3f, .12f)
        cloud(th.cloud[3], .2f, .7f, .6f, 1.4f, .12f)
        cloud(PAL[3], .2f, .2f, .62f, .9f, .3f)
        cloud(PAL[4], .14f, .8f, .9f, .8f, .3f)

        for (i in 0 until NR) {
            rbY[i] = .55f + rnd.nextFloat() * .4f; rbA[i] = .03f + rnd.nextFloat() * .07f
            rbF[i] = 5f + rnd.nextFloat() * 7f; rbS[i] = .3f + rnd.nextFloat() * .6f
            rbP[i] = rnd.nextFloat() * 6f; rbW[i] = .5f + rnd.nextFloat() * 1.4f
            rbL[i] = .3f + rnd.nextFloat() * .22f; rbSide[i] = i % 2
        }
        for (i in 0 until NST) {
            stY[i] = .18f + i * .13f; stA[i] = .04f + rnd.nextFloat() * .07f; stF[i] = 6f + rnd.nextFloat() * 5f
            stSp[i] = .5f + rnd.nextFloat() * .8f; stPh[i] = rnd.nextFloat() * 6f
            stV[i] = .05f + rnd.nextFloat() * .07f; stW[i] = rnd.nextFloat()
        }
        for (i in 0 until NS) {
            sX[i] = rnd.nextFloat(); sY[i] = rnd.nextFloat(); sPh[i] = rnd.nextFloat() * 6f
            if (i < 130) { sR[i] = .3f + rnd.nextFloat() * .5f; sPar[i] = .2f }
            else if (i < 190) { sR[i] = .6f + rnd.nextFloat() * .7f; sPar[i] = .5f }
            else { sR[i] = 1.3f + rnd.nextFloat() * 1f; sPar[i] = 1f }
            val q = rnd.nextFloat()
            sCol[i] = if (q < .3f) Color.rgb(255, 170, 90) else if (q < .65f) Color.rgb(140, 210, 255) else Color.WHITE
        }
        for (i in 0 until NB) {
            bX[i] = rnd.nextFloat(); bY[i] = rnd.nextFloat(); bR[i] = 6f + rnd.nextFloat() * 14f
            bV[i] = .01f + rnd.nextFloat() * .03f; bPh[i] = rnd.nextFloat() * 6f; bK[i] = rnd.nextInt(3)
        }
        for (i in 0 until ND) {
            dX[i] = rnd.nextFloat(); dY[i] = rnd.nextFloat(); dR[i] = 14f + rnd.nextFloat() * 30f
            dV[i] = .02f + rnd.nextFloat() * .04f; dPh[i] = rnd.nextFloat() * 6f; dK[i] = rnd.nextInt(3)
        }

        var n = 0
        for (i in 0 until 9) for (j in 0 until 9) for (k in 0 until 9) {
            bx[n] = (i - 4) / 4f; by[n] = (j - 4) / 4f; bz[n] = (k - 4) / 4f
            gi[n] = i; gj[n] = j; gk[n] = k
            hotA[n] = max(0f, 1f - sqrt(bx[n] * bx[n] + by[n] * by[n] + bz[n] * bz[n]) * .8f)
            n++
        }
        var e = 0
        for (i in 0 until 9) for (j in 0 until 9) for (k in 0 until 9) {
            val a = (i * 9 + j) * 9 + k
            val sh = (i == 0 || i == 8 || j == 0 || j == 8 || k == 0 || k == 8)
            if (i + 1 < 9) { ea[e] = a; eb[e] = ((i + 1) * 9 + j) * 9 + k; esh[e] = sh; e++ }
            if (j + 1 < 9) { ea[e] = a; eb[e] = (i * 9 + j + 1) * 9 + k; esh[e] = sh; e++ }
            if (k + 1 < 9) { ea[e] = a; eb[e] = (i * 9 + j) * 9 + k + 1; esh[e] = sh; e++ }
        }
        val list = MutableList(NN) { if (it < 437) 0 else if (it < 583) 1 else 2 }
        list.shuffle(Random(12345))
        for (i in 0 until NN) cls[i] = list[i]

        for (i in 0 until NRUN) {
            ra[i] = rnd.nextInt(NN)
            rb[i] = nb(ra[i], -1)
            rfa[i] = rnd.nextFloat()
            rv[i] = .8f + rnd.nextFloat() * 1.5f
            rc[i] = if (i < 24) 0 else if (i < 32) 1 else 2
        }
    }

    private fun nb(a: Int, prev: Int): Int {
        val i = a / 81
        val j = (a / 9) % 9
        val k = a % 9
        var m = 0
        fun tryAdd(ii: Int, jj: Int, kk: Int) {
            if (ii in 0..8 && jj in 0..8 && kk in 0..8) {
                val q = (ii * 9 + jj) * 9 + kk
                if (q != prev) { cand[m] = q; m++ }
            }
        }
        tryAdd(i - 1, j, k); tryAdd(i + 1, j, k); tryAdd(i, j - 1, k)
        tryAdd(i, j + 1, k); tryAdd(i, j, k - 1); tryAdd(i, j, k + 1)
        return cand[rnd.nextInt(m)]
    }

    private fun ang(t: Float, per: Float, ph: Float, mx: Float): Float {
        val s = (1f - cos(6.2831855f * (t / per + ph))) / 2f
        return mx * s * s * (3f - 2f * s)
    }

    private fun jag(x1: Float, y1: Float, x2: Float, y2: Float, dep: Int, off: Float, out: ArrayList<Float>) {
        if (dep == 0) { out.add(x1); out.add(y1); return }
        val mx = (x1 + x2) / 2f + (rnd.nextFloat() - .5f) * off
        val my = (y1 + y2) / 2f + (rnd.nextFloat() - .5f) * off
        jag(x1, y1, mx, my, dep - 1, off / 2f, out)
        jag(mx, my, x2, y2, dep - 1, off / 2f, out)
    }

    override fun resize(width: Int, height: Int) {
        super.resize(width, height)
        val wf = w.toFloat(); val hf = h.toFloat()
        val land = w > h
        bgShader = LinearGradient(0f, 0f, 0f, hf, th.bg, floatArrayOf(0f, .45f, 1f), Shader.TileMode.CLAMP)
        for (i in 0 until NR) {
            val len = rbL[i]
            val x0 = if (rbSide[i] == 1) wf else 0f
            val x1 = if (rbSide[i] == 1) wf - wf * len else wf * len
            val c0 = if (rbSide[i] == 1) argbF(PAL[1], .6f) else argbF(PAL[0], .6f)
            rbShader[i] = LinearGradient(
                x0, 0f, x1, 0f,
                intArrayOf(c0, argbF(PAL[2], .5f), argbF(th.cloud[3], 0f)),
                floatArrayOf(0f, .5f, 1f), Shader.TileMode.CLAMP
            )
        }
        val S = min(wf, hf * (if (land) 1f else .62f))
        rayShader = RadialGradient(
            0f, 0f, S * 1.15f,
            intArrayOf(Color.argb(255, 255, 190, 120), Color.argb(0, 255, 190, 120)),
            floatArrayOf(0f, 1f), Shader.TileMode.CLAMP
        )
    }

    override fun draw(c: Canvas, t: Float, dt: Float, tx: Float, ty: Float, fancy: Boolean) {
        if (w == 0 || h == 0) { c.drawColor(th.bg[0]); return }
        val wf = w.toFloat(); val hf = h.toFloat()
        val sx = sin(t * .13f) * wf * .018f + tx * wf * .03f
        val sy = cos(t * .1f) * hf * .008f + ty * hf * .015f

        add(false)
        p.style = Paint.Style.FILL; p.shader = bgShader; p.color = Color.WHITE
        c.drawRect(0f, 0f, wf, hf, p)
        p.shader = null

        add(true)
        try { nebula(c, t, sx, sy) } catch (e: Throwable) { }
        try { stars(c, t, dt, sx, sy) } catch (e: Throwable) { }
        if (fancy) { try { energyBg(c, t, sx) } catch (e: Throwable) { } }
        try { lattice(c, t, dt, sx, sy, fancy) } catch (e: Throwable) { }
        if (fancy) { try { dust(c, t, dt, sx) } catch (e: Throwable) { } }
        add(false)
    }

    private fun nebula(c: Canvas, t: Float, sx: Float, sy: Float) {
        val wf = w.toFloat(); val hf = h.toFloat()
        for (i in 0 until cloudBmp.size) {
            val q = cloudP[i]
            val r = max(wf, hf) * q[2] * .5f
            val x = wf * (q[0] + .04f * sin(t * .2f + i)) + sx * q[3] * 3f
            val y = hf * (q[1] + .03f * cos(t * .17f + i * 2f)) + sy * q[3] * 3f
            spr(c, cloudBmp[i], x, y, r, 1f)
        }
        p.style = Paint.Style.STROKE
        p.strokeCap = Paint.Cap.ROUND
        for (i in 0 until NR) {
            path.reset()
            val len = rbL[i]
            for (q in 0..48) {
                val u = q / 48f
                val x = if (rbSide[i] == 1) wf - u * wf * len else u * wf * len
                val y = hf * (rbY[i] - .18f * u * u) + sin(u * rbF[i] + t * rbS[i] + rbP[i]) * hf * rbA[i] * (.3f + u)
                if (q == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            c.save()
            c.translate(sx * .6f, 0f)
            p.shader = rbShader[i]
            p.strokeWidth = d * rbW[i] * .9f
            c.drawPath(path, p)
            p.shader = null
            c.restore()
        }
    }

    private fun stars(c: Canvas, t: Float, dt: Float, sx: Float, sy: Float) {
        val wf = w.toFloat(); val hf = h.toFloat()
        p.style = Paint.Style.FILL
        for (i in 0 until NS) {
            val x = sX[i] * wf + sx * sPar[i] * 2.5f
            val y = sY[i] * hf + sy * sPar[i] * 2.5f
            val a = .2f + .8f * abs(sin(t * 1.2f + sPh[i]))
            if (sR[i] > 1.3f) spr(c, blueS, x, y, sR[i] * d * 3.2f, a * .5f)
            p.color = Color.argb((a * 255f).toInt().coerceIn(0, 255), Color.red(sCol[i]), Color.green(sCol[i]), Color.blue(sCol[i]))
            c.drawCircle(x, y, sR[i] * d, p)
        }
        for (i in 0 until NB) {
            bY[i] -= bV[i] * .18f * dt
            bX[i] += sin(t * .4f + bPh[i]) * .024f * dt
            if (bY[i] < -.05f) bY[i] = 1.05f
            val a = .3f + .7f * abs(sin(t * .7f + bPh[i]))
            spr(c, bokS[bK[i]], bX[i] * wf + sx * .8f, bY[i] * hf, bR[i] * d, a * .55f)
        }
    }

    private fun energyBg(c: Canvas, t: Float, sx: Float) {
        val wf = w.toFloat(); val hf = h.toFloat()
        p.strokeCap = Paint.Cap.ROUND
        for (s in 0 until NST) {
            val col = PAL[s % 5]
            for (m in 0 until 12) {
                val u = ((m / 12f) + t * stV[s]) % 1f
                val u2 = u - .06f
                if (u2 < 0f) continue
                val x = u * wf + sx * .5f
                val y = hf * (stY[s] + stA[s] * sin(u * stF[s] + t * stSp[s] + stPh[s]))
                val x2 = u2 * wf + sx * .5f
                val y2 = hf * (stY[s] + stA[s] * sin(u2 * stF[s] + t * stSp[s] + stPh[s]))
                p.style = Paint.Style.STROKE
                p.strokeWidth = d * (.7f + .9f * stW[s])
                p.color = argbF(col, .1f + .4f * sin(u * 3.14f))
                c.drawLine(x2, y2, x, y, p)
                p.style = Paint.Style.FILL
                p.color = argbF(col, .9f)
                c.drawCircle(x, y, d * (.6f + .6f * stW[s]), p)
            }
        }
        if (t - lastSw > 2.6f) {
            waves.add(Wave(.1f + rnd.nextFloat() * .8f, .1f + rnd.nextFloat() * .8f, t, rnd.nextInt(5)))
            lastSw = t
        }
        waves.removeAll { t - it.t0 >= 3f }
        for (q in waves) {
            val age = t - q.t0
            val r = age * wf * .14f
            val a = 1f - age / 3f
            p.style = Paint.Style.STROKE
            p.strokeWidth = d * (1f + 2f * a)
            p.color = argbF(PAL[q.c], a * .3f)
            c.drawCircle(q.x * wf, q.y * hf, r, p)
            p.strokeWidth = d
            p.color = Color.argb((a * .08f * 255f).toInt().coerceIn(0, 255), 255, 255, 255)
            c.drawCircle(q.x * wf, q.y * hf, r * .82f, p)
        }
        if (t - lastB > nextBolt) {
            val bx0 = wf * rnd.nextFloat()
            val by0 = hf * (.05f + rnd.nextFloat() * .85f)
            val an = rnd.nextFloat() * 6.28f
            val ln = wf * (.25f + rnd.nextFloat() * .25f)
            val out = ArrayList<Float>()
            val ex = bx0 + cos(an) * ln
            val ey = by0 + sin(an) * ln
            jag(bx0, by0, ex, ey, 5, ln * .22f, out)
            out.add(ex); out.add(ey)
            bolts.add(Bolt(t, out.toFloatArray(), rnd.nextInt(3)))
            lastB = t
            nextBolt = 1.8f + rnd.nextFloat() * 1.4f
        }
        bolts.removeAll { t - it.t0 >= .4f }
        for (b in bolts) {
            val age = t - b.t0
            val a = (1f - age / .4f) * (.6f + .4f * sin(age * 70f))
            path.reset()
            var i = 0
            while (i < b.pts.size) {
                if (i == 0) path.moveTo(b.pts[0] + sx * .4f, b.pts[1]) else path.lineTo(b.pts[i] + sx * .4f, b.pts[i + 1])
                i += 2
            }
            p.style = Paint.Style.STROKE
            p.strokeWidth = d * 7f
            p.color = argbF(PAL[b.c], a * .18f)
            c.drawPath(path, p)
            p.strokeWidth = d * 2f
            p.color = Color.argb((a * .9f * 255f).toInt().coerceIn(0, 255), 255, 255, 255)
            c.drawPath(path, p)
        }
    }

    private fun lattice(c: Canvas, t: Float, dt: Float, sx: Float, sy: Float, fancy: Boolean) {
        val wf = w.toFloat(); val hf = h.toFloat()
        val land = w > h
        val S = min(wf, hf * (if (land) 1f else .62f))
        val cx = (if (land) wf * .62f else wf / 2f) + sx * 1.4f
        val cy = (if (land) hf * .44f else hf * .55f) + sy * 1.4f
        val sc = S * .23f
        val yaw = t * .16f
        val tilt = .62f + .08f * sin(t * .25f)
        val pl = .5f + .5f * sin(t * 1.5f)

        c.save(); c.translate(cx, cy + sc * 1.5f); c.scale(1f, .28f)
        spr(c, blueS, 0f, 0f, sc * 1.6f, .5f)
        c.restore()

        if (fancy) {
            c.save(); c.translate(cx, cy)
            p.style = Paint.Style.FILL; p.shader = rayShader
            p.alpha = (((.045f + .025f * pl) * 255f).toInt()).coerceIn(0, 255)
            for (k in 0 until 10) {
                val a = k * .628f + t * .05f
                val wd = .05f + .03f * sin(t * .4f + k)
                val L = S * 1.15f
                path.reset(); path.moveTo(0f, 0f)
                path.lineTo(cos(a - wd) * L, sin(a - wd) * L)
                path.lineTo(cos(a + wd) * L, sin(a + wd) * L)
                path.close()
                c.drawPath(path, p)
            }
            p.shader = null; p.alpha = 255
            c.restore()
        }

        val t1 = ang(t, 11f, 0f, 2.4f); val t2 = ang(t, 13f, .33f, 2.2f); val t3 = ang(t, 17f, .66f, 2f)
        val c1 = cos(t1); val s1 = sin(t1); val c2 = cos(t2); val s2 = sin(t2); val c3 = cos(t3); val s3 = sin(t3)
        val cyw = cos(yaw); val syw = sin(yaw); val ctl = cos(tilt); val stl = sin(tilt)
        for (n in 0 until NN) {
            var x = bx[n]; var y = by[n]; var z = bz[n]
            if (x > .001f) { val u = x; val v = y; x = u * c1 + v * s1; y = -u * s1 + v * c1 }
            if (y > .001f) { val u = y; val v = z; y = u * c2 + v * s2; z = -u * s2 + v * c2 }
            if (z > .001f) { val u = z; val v = x; z = u * c3 + v * s3; x = -u * s3 + v * c3 }
            val x1 = x * cyw - z * syw
            val z1 = x * syw + z * cyw
            val y2 = y * ctl - z1 * stl
            val z2 = y * stl + z1 * ctl
            val s = 3.6f / (3.6f - z2 * .9f)
            pX[n] = cx + x1 * sc * s; pY[n] = cy + y2 * sc * s; pS[n] = s
        }

        for (e in 0 until NE) {
            val s = (pS[ea[e]] + pS[eb[e]]) / 2f
            eBucket[e] = if (s < .93f) 0 else if (s < 1.1f) 1 else 2
        }
        p.style = Paint.Style.STROKE
        p.strokeCap = Paint.Cap.ROUND
        for (b in 0 until 3) {
            for (shell in 0 until 2) {
                var cnt = 0
                var sum = 0f
                for (e in 0 until NE) {
                    if (eBucket[e] == b && (if (esh[e]) 1 else 0) == shell) {
                        val a = ea[e]; val q = eb[e]
                        lineBuf[cnt * 4] = pX[a]; lineBuf[cnt * 4 + 1] = pY[a]
                        lineBuf[cnt * 4 + 2] = pX[q]; lineBuf[cnt * 4 + 3] = pY[q]
                        sum += pS[a]
                        cnt++
                    }
                }
                if (cnt > 0) {
                    val avg = sum / cnt
                    val al = ((if (shell == 1) .45f else .17f) * tintA[b] * (.6f + .4f * avg / 1.4f)).coerceIn(0f, 1f)
                    p.strokeWidth = d * lineW[b]
                    p.color = Color.argb((al * 255f).toInt(), tintR[b], tintG[b], tintB[b])
                    c.drawLines(lineBuf, 0, cnt * 4, p)
                }
            }
        }

        java.util.Arrays.fill(bkt, 0)
        for (n in 0 until NN) {
            val q = ((pS[n] - .5f) * 40f).toInt().coerceIn(0, 63)
            key[n] = q; bkt[q + 1]++
        }
        for (i in 1..64) bkt[i] += bkt[i - 1]
        for (n in 0 until NN) { order[bkt[key[n]]] = n; bkt[key[n]]++ }
        add(false)
        for (idx in 0 until NN) {
            val n = order[idx]
            val s = pS[n]; val hot = hotA[n]
            val r = d * (1.2f + 1.5f * s * (.5f + hot * .5f))
            val far = if (s < .93f) .45f else if (s < 1.1f) .8f else 1f
            spr(c, if (hot > .35f) ballWarm else nodeBall[cls[n]], pX[n], pY[n], r, far)
        }
        add(true)
        for (idx in 0 until NN) {
            val n = order[idx]
            val s = pS[n]; val hot = hotA[n]
            if (hot > .35f || s > 1.15f) {
                val r = d * (1.2f + 1.5f * s * (.5f + hot * .5f))
                val far = if (s < .93f) .45f else if (s < 1.1f) .8f else 1f
                spr(c, if (hot > .35f) palS[1] else nodeHalo[cls[n]], pX[n], pY[n], r * 3f,
                    .35f * far * (if (hot > .35f) 1f else .6f))
            }
        }

        if (t - lastRp > 2.4f) {
            var si = rnd.nextInt(2) * 8; var sj = rnd.nextInt(2) * 8; var sk = rnd.nextInt(2) * 8
            if (rnd.nextFloat() < .3f) { si = 4; sj = 4; sk = 4 }
            rips.add(Rip(t, si, sj, sk, RIPC[rpN % 5])); rpN++
            lastRp = t
        }
        rips.removeAll { t - it.t0 >= 4.2f }
        for (r in rips) {
            val fr = (t - r.t0) * 7f
            val col = LIGHT[r.c]
            p.style = Paint.Style.STROKE
            p.strokeWidth = d * 1.3f
            for (e in 0 until NE) {
                val a = ea[e]; val b = eb[e]
                val da = abs(gi[a] - r.si) + abs(gj[a] - r.sj) + abs(gk[a] - r.sk)
                val db = abs(gi[b] - r.si) + abs(gj[b] - r.sj) + abs(gk[b] - r.sk)
                val wv = abs((da + db) / 2f - fr)
                if (wv < 1.1f) {
                    p.color = argbF(col, (1f - wv / 1.1f) * .9f)
                    c.drawLine(pX[a], pY[a], pX[b], pY[b], p)
                }
            }
            for (n in 0 until NN) {
                val dd = abs(gi[n] - r.si) + abs(gj[n] - r.sj) + abs(gk[n] - r.sk)
                val wv = abs(dd - fr)
                if (wv < 1.2f) {
                    val inten = 1f - wv / 1.2f
                    spr(c, lightS[r.c], pX[n], pY[n], d * (5f + 4f * pS[n]) * inten, inten * .85f)
                }
            }
        }

        for (i in 0 until NRUN) {
            rfa[i] += rv[i] * dt
            while (rfa[i] >= 1f) {
                rfa[i] -= 1f
                val pa = ra[i]
                ra[i] = rb[i]
                rb[i] = nb(ra[i], pa)
            }
            val a = ra[i]; val b = rb[i]
            for (m in 0 until 6) {
                val f = rfa[i] - m * .08f
                if (f < 0f) break
                val x = pX[a] + (pX[b] - pX[a]) * f
                val y = pY[a] + (pY[b] - pY[a]) * f
                spr(c, lightS[rc[i]], x, y, d * (6f - .8f * m) * (.7f + .3f * pS[a]), (1f - m * .16f) * .9f)
            }
        }

        if (fancy) {
            for (o in 0 until 3) {
                val rx = sc * (2f + .35f * o)
                val ry = rx * (.26f + .09f * o)
                val dir = if (o % 2 == 1) -1f else 1f
                val rot = t * (.1f + .05f * o) * dir + o
                val st = t * (1f + .4f * o) * dir
                val ha = st + 1.1f
                val hx = cos(ha) * rx
                val hy = sin(ha) * ry
                val hpx = cx + hx * cos(rot) - hy * sin(rot)
                val hpy = cy + hx * sin(rot) + hy * cos(rot)
                c.save()
                c.translate(cx, cy)
                c.rotate(deg(rot))
                rf.set(-rx, -ry, rx, ry)
                p.style = Paint.Style.STROKE
                p.strokeWidth = d
                p.color = argbF(PAL[o], .14f)
                c.drawOval(rf, p)
                p.strokeWidth = d * 2.6f
                p.color = argbF(PAL[o], .85f)
                c.drawArc(rf, deg(st), deg(1.1f), false, p)
                c.restore()
                spr(c, palS[o], hpx, hpy, d * 14f, 1f)
            }
        }

        val cr = sc * (.7f + .1f * pl)
        spr(c, coreS, cx, cy, cr, .45f + .25f * pl)
        c.save(); c.translate(cx, cy); c.scale(1f, .03f)
        spr(c, coreS, 0f, 0f, S * .8f, .16f + .1f * pl)
        c.restore()
        c.save(); c.translate(cx, cy); c.scale(1f, .015f)
        spr(c, blueS, 0f, 0f, S * 1.1f, .12f)
        c.restore()
        val q = (t % 4.5f) / 4.5f
        p.style = Paint.Style.STROKE
        p.strokeWidth = d * (2f + 5f * (1f - q))
        p.color = Color.argb((.45f * (1f - q) * 255f).toInt().coerceIn(0, 255), 255, 200, 140)
        c.drawCircle(cx, cy, sc * 1.5f * q, p)
    }

    private fun dust(c: Canvas, t: Float, dt: Float, sx: Float) {
        val wf = w.toFloat(); val hf = h.toFloat()
        for (i in 0 until ND) {
            dY[i] -= dV[i] * .18f * dt
            dX[i] += sin(t * .3f + dPh[i]) * .036f * dt
            if (dY[i] < -.1f) dY[i] = 1.1f
            spr(c, bokS[dK[i]], dX[i] * wf + sx * 2.4f, dY[i] * hf, dR[i] * d, .08f + .1f * abs(sin(t * .5f + dPh[i])))
        }
    }
}
