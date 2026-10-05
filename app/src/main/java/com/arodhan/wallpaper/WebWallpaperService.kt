package com.arodhan.wallpaper

import android.content.res.AssetManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.SweepGradient
import android.graphics.Typeface
import android.graphics.Xfermode
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.service.wallpaper.WallpaperService
import android.view.SurfaceHolder
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/** ---- Easy settings (edit and rebuild) ---- */
private const val FPS = 30          // 20 = saves battery, 30 = smooth, 45 = very smooth
private const val FANCY = true      // false = removes lightning/streams/rays/orbits/dust (lighter on battery)

private fun deg(r: Float): Float = r * 57.29578f

class WebWallpaperService : WallpaperService() {

    override fun onCreateEngine(): Engine = ArodhanEngine()

    inner class ArodhanEngine : Engine() {
        private val handler = Handler(Looper.getMainLooper())
        private val t0 = SystemClock.uptimeMillis()
        private var lastMs = SystemClock.uptimeMillis()
        private var scene: Scene? = null
        private var visible = false
        private var useHw = true
        private var errors = 0

        private val loop = object : Runnable {
            override fun run() {
                val st = SystemClock.uptimeMillis()
                drawFrame()
                if (visible) {
                    val spent = SystemClock.uptimeMillis() - st
                    handler.postDelayed(this, max(1L, 1000L / FPS - spent))
                }
            }
        }

        override fun onCreate(surfaceHolder: SurfaceHolder) {
            super.onCreate(surfaceHolder)
            scene = Scene(resources.displayMetrics.density, assets)
        }

        override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            super.onSurfaceChanged(holder, format, width, height)
            try { scene?.resize(width, height) } catch (e: Throwable) { errors++ }
            drawFrame()
        }

        override fun onVisibilityChanged(isVisible: Boolean) {
            visible = isVisible
            handler.removeCallbacks(loop)
            if (isVisible) {
                lastMs = SystemClock.uptimeMillis()
                handler.post(loop)
            }
        }

        override fun onSurfaceDestroyed(holder: SurfaceHolder) {
            visible = false
            handler.removeCallbacks(loop)
            super.onSurfaceDestroyed(holder)
        }

        override fun onDestroy() {
            visible = false
            handler.removeCallbacks(loop)
            super.onDestroy()
        }

        private fun drawFrame() {
            val sc = scene ?: return
            val holder = surfaceHolder ?: return
            var canvas: Canvas? = null
            try {
                canvas = if (useHw && Build.VERSION.SDK_INT >= 26) {
                    try {
                        holder.lockHardwareCanvas()
                    } catch (e: Throwable) {
                        useHw = false
                        holder.lockCanvas()
                    }
                } else {
                    holder.lockCanvas()
                }
                if (canvas != null) {
                    val now = SystemClock.uptimeMillis()
                    val t = (now - t0) / 1000f
                    val dt = (now - lastMs).coerceIn(0L, 50L) / 1000f
                    lastMs = now
                    sc.draw(canvas, t, dt)
                }
            } catch (e: Throwable) {
                errors++
                if (errors > 3) useHw = false   // fall back to normal canvas if hardware canvas misbehaves
            } finally {
                if (canvas != null) {
                    try { holder.unlockCanvasAndPost(canvas) } catch (e: Throwable) { }
                }
            }
        }
    }
}

private class Wave(val x: Float, val y: Float, val t0: Float, val c: Int)
private class Bolt(val t0: Float, val pts: FloatArray, val c: Int)
private class Rip(val t0: Float, val si: Int, val sj: Int, val sk: Int, val c: Int)

/** Everything that is drawn: nebula, energy, folding lattice, clock and banner. */
private class Scene(private val d: Float, private val am: AssetManager) {

    var w = 0
    var h = 0

    private val rnd = Random(7)
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bp = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
    private val tp = Paint(Paint.ANTI_ALIAS_FLAG)
    private val addMode = PorterDuffXfermode(PorterDuff.Mode.ADD)
    private val rf = RectF()
    private val path = Path()
    private val mat = Matrix()

    private val PAL = arrayOf(
        intArrayOf(0, 242, 255), intArrayOf(255, 150, 50), intArrayOf(255, 0, 190),
        intArrayOf(110, 255, 150), intArrayOf(255, 230, 90)
    )
    private val LIGHT = arrayOf(intArrayOf(255, 40, 40), intArrayOf(60, 130, 255), intArrayOf(255, 255, 255))
    private val RIPC = intArrayOf(0, 0, 0, 1, 2)

    // ---------- sprites ----------
    private fun glow(col: IntArray, a: Float): Bitmap {
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

    private fun ball(r: Int, g: Int, b: Int): Bitmap {
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

    private val palS = Array(5) { glow(PAL[it], 0.9f) }
    private val lightS = Array(3) { glow(LIGHT[it], 0.9f) }
    private val coreS = glow(intArrayOf(255, 190, 120), 0.9f)
    private val blueS = glow(intArrayOf(120, 190, 255), 0.9f)
    private val ballWarm = ball(255, 165, 80)
    private val nodeBall = arrayOf(ball(255, 70, 70), ball(80, 140, 255), ball(200, 225, 255))
    private val nodeHalo = arrayOf(
        glow(intArrayOf(255, 50, 50), 0.9f), glow(intArrayOf(70, 130, 255), 0.9f), glow(intArrayOf(255, 255, 255), 0.9f)
    )
    private val bokS = arrayOf(
        glow(intArrayOf(255, 150, 60), 0.9f), glow(intArrayOf(90, 170, 255), 0.9f), glow(intArrayOf(255, 90, 200), 0.9f)
    )

    // ---------- nebula clouds ----------
    private val cloudBmp = ArrayList<Bitmap>()
    private val cloudP = ArrayList<FloatArray>() // x, y, size, parallax

    private fun cloud(r: Int, g: Int, b: Int, a: Float, x: Float, y: Float, sz: Float, par: Float) {
        cloudBmp.add(glow(intArrayOf(r, g, b), a))
        cloudP.add(floatArrayOf(x, y, sz, par))
    }

    // ---------- ribbons / streams ----------
    private val NR = 26
    private val rbY = FloatArray(NR); private val rbA = FloatArray(NR); private val rbF = FloatArray(NR)
    private val rbS = FloatArray(NR); private val rbP = FloatArray(NR); private val rbW = FloatArray(NR)
    private val rbL = FloatArray(NR); private val rbSide = IntArray(NR)
    private val rbShader = arrayOfNulls<Shader>(NR)

    private val NST = 6
    private val stY = FloatArray(NST); private val stA = FloatArray(NST); private val stF = FloatArray(NST)
    private val stSp = FloatArray(NST); private val stPh = FloatArray(NST); private val stV = FloatArray(NST)
    private val stW = FloatArray(NST)

    // ---------- stars, bokeh, dust ----------
    private val NS = 206
    private val sX = FloatArray(NS); private val sY = FloatArray(NS); private val sR = FloatArray(NS)
    private val sPh = FloatArray(NS); private val sPar = FloatArray(NS); private val sCol = IntArray(NS)
    private val NB = 18
    private val bX = FloatArray(NB); private val bY = FloatArray(NB); private val bR = FloatArray(NB)
    private val bV = FloatArray(NB); private val bPh = FloatArray(NB); private val bK = IntArray(NB)
    private val ND = 14
    private val dX = FloatArray(ND); private val dY = FloatArray(ND); private val dR = FloatArray(ND)
    private val dV = FloatArray(ND); private val dPh = FloatArray(ND); private val dK = IntArray(ND)

    // ---------- lattice ----------
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

    // ---------- clock / banner ----------
    private val anchor = Path()
    private val cal = Calendar.getInstance()
    private val dateFmt = SimpleDateFormat("EEE, d MMM", Locale.ENGLISH)
    private var dateStr = ""
    private var lastSecInt = -1
    private var digitFace: Typeface = Typeface.create("sans-serif-black", Typeface.NORMAL)
    private var textFace: Typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    private var bannerFace: Typeface = Typeface.create("sans-serif-black", Typeface.NORMAL)

    // ---------- shaders built on resize ----------
    private var bgShader: Shader? = null
    private var vigShader: Shader? = null
    private var rayShader: Shader? = null
    private var lineShader: Shader? = null
    private var borderShader: Shader? = null
    private var sweepShader: SweepGradient? = null
    private var textShader: LinearGradient? = null
    private var ckCx = 0f; private var ckDs = 0f; private var ckY0 = 0f
    private var cardL = 0f; private var cardT = 0f; private var cardW = 0f; private var cardH = 0f
    private var bannerTs = 0f; private var bannerTw = 0f

    init {
        cloud(255, 0, 170, .36f, .15f, .85f, .9f, .25f)
        cloud(255, 120, 30, .34f, .9f, .72f, .85f, .25f)
        cloud(30, 130, 255, .36f, .1f, .5f, .8f, .25f)
        cloud(140, 60, 255, .36f, .85f, .35f, .8f, .4f)
        cloud(20, 90, 235, .34f, .5f, .05f, 1.1f, .25f)
        cloud(255, 70, 140, .24f, .5f, 1f, 1f, .4f)
        cloud(60, 160, 255, .22f, .3f, .25f, 1.3f, .12f)
        cloud(190, 50, 255, .2f, .7f, .6f, 1.4f, .12f)
        cloud(0, 210, 190, .2f, .2f, .62f, .9f, .3f)
        cloud(255, 190, 60, .14f, .8f, .9f, .8f, .3f)

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
        // exactly 60% red, 20% blue, 20% white lights, shuffled
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

        anchor.addCircle(0f, 0f, 7f, Path.Direction.CW)
        anchor.moveTo(0f, 7f); anchor.lineTo(0f, 56f)
        anchor.moveTo(-13f, 20f); anchor.lineTo(13f, 20f)
        anchor.moveTo(-30f, 38f); anchor.quadTo(-30f, 62f, 0f, 66f); anchor.quadTo(30f, 62f, 30f, 38f)
        anchor.moveTo(-30f, 38f); anchor.lineTo(-36f, 45f)
        anchor.moveTo(-30f, 38f); anchor.lineTo(-23f, 43f)
        anchor.moveTo(30f, 38f); anchor.lineTo(36f, 45f)
        anchor.moveTo(30f, 38f); anchor.lineTo(23f, 43f)

        // optional nicer font: put orbitron.ttf in app/src/main/assets/fonts/
        try { bannerFace = Typeface.createFromAsset(am, "fonts/orbitron.ttf") } catch (t: Throwable) { }
        try { digitFace = Typeface.createFromAsset(am, "fonts/digits.ttf") } catch (t: Throwable) { }
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

    private fun argbF(col: IntArray, a: Float): Int =
        Color.argb((a.coerceIn(0f, 1f) * 255f).toInt(), col[0], col[1], col[2])

    private fun add(on: Boolean) {
        val m: Xfermode? = if (on) addMode else null
        p.xfermode = m
        bp.xfermode = m
    }

    private fun spr(c: Canvas, b: Bitmap, x: Float, y: Float, r: Float, a: Float) {
        bp.alpha = (a.coerceIn(0f, 1f) * 255f).toInt()
        rf.set(x - r, y - r, x + r, y + r)
        c.drawBitmap(b, null, rf, bp)
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

    // ================= layout =================
    fun resize(width: Int, height: Int) {
        w = width; h = height
        val wf = w.toFloat(); val hf = h.toFloat()
        val land = w > h

        bgShader = LinearGradient(
            0f, 0f, 0f, hf,
            intArrayOf(Color.rgb(4, 10, 56), Color.rgb(29, 10, 92), Color.rgb(90, 16, 104)),
            floatArrayOf(0f, .45f, 1f), Shader.TileMode.CLAMP
        )
        val vr = sqrt(wf * wf + hf * hf) * .55f
        vigShader = RadialGradient(
            wf / 2f, hf / 2f, vr,
            intArrayOf(Color.argb(0, 2, 2, 12), Color.argb(0, 2, 2, 12), Color.argb(153, 2, 2, 12)),
            floatArrayOf(0f, min(wf, hf) * .35f / vr, 1f), Shader.TileMode.CLAMP
        )
        for (i in 0 until NR) {
            val len = rbL[i]
            val x0 = if (rbSide[i] == 1) wf else 0f
            val x1 = if (rbSide[i] == 1) wf - wf * len else wf * len
            val c0 = if (rbSide[i] == 1) Color.argb(153, 255, 140, 60) else Color.argb(153, 60, 170, 255)
            rbShader[i] = LinearGradient(
                x0, 0f, x1, 0f,
                intArrayOf(c0, Color.argb(128, 255, 60, 190), Color.argb(0, 150, 80, 255)),
                floatArrayOf(0f, .5f, 1f), Shader.TileMode.CLAMP
            )
        }
        val S = min(wf, hf * (if (land) 1f else .62f))
        rayShader = RadialGradient(
            0f, 0f, S * 1.15f,
            intArrayOf(Color.argb(255, 255, 190, 120), Color.argb(0, 255, 190, 120)),
            floatArrayOf(0f, 1f), Shader.TileMode.CLAMP
        )

        // clock geometry
        ckCx = if (land) wf * .17f else wf / 2f
        ckDs = if (land) hf * .17f else min(wf * .17f, hf * .095f)
        ckY0 = if (land) hf * .16f else hf * .03f
        val lw = ckDs * .9f
        val a = ckDs * .0105f
        val ym = ckY0 + 80f * a + ckDs * .95f + ckDs * .98f
        val yl = ym + ckDs * .26f
        lineShader = LinearGradient(
            ckCx - lw / 2f, yl, ckCx + lw / 2f, yl,
            Color.rgb(0, 242, 255), Color.rgb(255, 0, 204), Shader.TileMode.CLAMP
        )

        // banner geometry
        cardW = if (land) wf * .48f else wf * .76f
        cardL = if (land) wf * .46f else wf * .12f
        tp.typeface = bannerFace
        tp.letterSpacing = .16f
        tp.textSize = 100f
        val tw100 = tp.measureText("ARODHAN")
        bannerTs = min(cardW * .8f / tw100 * 100f, hf * .045f)
        tp.textSize = bannerTs
        bannerTw = tp.measureText("ARODHAN")
        cardH = bannerTs * 2.2f
        cardT = hf * .94f - cardH
        val cx = cardL + cardW / 2f
        val cy = cardT + cardH / 2f
        borderShader = LinearGradient(
            cardL, cardT, cardL + cardW, cardT + cardH,
            intArrayOf(Color.argb(140, 0, 242, 255), Color.argb(140, 138, 43, 226), Color.argb(140, 255, 0, 204)),
            null, Shader.TileMode.CLAMP
        )
        sweepShader = SweepGradient(
            cx, cy,
            intArrayOf(
                Color.argb(0, 0, 242, 255), Color.argb(0, 0, 242, 255), Color.rgb(0, 242, 255),
                Color.WHITE, Color.rgb(255, 0, 204), Color.argb(0, 255, 0, 204)
            ),
            floatArrayOf(0f, .52f, .68f, .74f, .84f, 1f)
        )
        textShader = LinearGradient(
            0f, 0f, bannerTw * 2.5f, 0f,
            intArrayOf(
                Color.rgb(0, 242, 255), Color.WHITE, Color.rgb(255, 0, 204),
                Color.rgb(0, 242, 255), Color.WHITE
            ),
            null, Shader.TileMode.MIRROR
        )
    }

    // ================= frame =================
    fun draw(c: Canvas, t: Float, dt: Float) {
        if (w == 0 || h == 0) { c.drawColor(Color.rgb(4, 10, 56)); return }
        val wf = w.toFloat(); val hf = h.toFloat()
        val sx = sin(t * .13f) * wf * .018f
        val sy = cos(t * .1f) * hf * .008f

        add(false)
        p.style = Paint.Style.FILL; p.shader = bgShader; p.color = Color.WHITE
        c.drawRect(0f, 0f, wf, hf, p)
        p.shader = null

        add(true)
        try { nebula(c, t, sx, sy) } catch (e: Throwable) { }
        try { stars(c, t, dt, sx, sy) } catch (e: Throwable) { }
        if (FANCY) { try { energyBg(c, t, sx) } catch (e: Throwable) { } }
        try { lattice(c, t, dt, sx, sy) } catch (e: Throwable) { }
        if (FANCY) { try { dust(c, t, dt, sx) } catch (e: Throwable) { } }
        add(false)
        try { clock(c) } catch (e: Throwable) { }
        try {
            p.style = Paint.Style.FILL; p.shader = vigShader; p.color = Color.WHITE
            c.drawRect(0f, 0f, wf, hf, p); p.shader = null
        } catch (e: Throwable) { }
        try { banner(c, t) } catch (e: Throwable) { }
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
        // energy streams
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
        // shockwaves
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
        // lightning
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

    private fun lattice(c: Canvas, t: Float, dt: Float, sx: Float, sy: Float) {
        val wf = w.toFloat(); val hf = h.toFloat()
        val land = w > h
        val S = min(wf, hf * (if (land) 1f else .62f))
        val cx = (if (land) wf * .62f else wf / 2f) + sx * 1.4f
        val cy = (if (land) hf * .44f else hf * .55f) + sy * 1.4f
        val sc = S * .23f
        val yaw = t * .16f
        val tilt = .62f + .08f * sin(t * .25f)
        val pl = .5f + .5f * sin(t * 1.5f)

        // floating glow under the structure
        c.save(); c.translate(cx, cy + sc * 1.5f); c.scale(1f, .28f)
        spr(c, blueS, 0f, 0f, sc * 1.6f, .5f)
        c.restore()

        // light rays
        if (FANCY) {
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

        // 3D folding (three hinges) + rotation + perspective
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

        // edges grouped by depth (far / mid / near) and shell / inner
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

        // nodes: back to front, shaded spheres
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

        // ripples: energy waves running through the lattice
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

        // runners: pulses racing along the lines
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

        // orbit rings
        if (FANCY) {
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

        // core glow, lens streaks, expanding ring
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

    private fun clock(c: Canvas) {
        cal.timeInMillis = System.currentTimeMillis()
        val hr = cal.get(Calendar.HOUR).let { if (it == 0) 12 else it }
        val mn = cal.get(Calendar.MINUTE)
        val secInt = cal.get(Calendar.SECOND)
        val sec = secInt + cal.get(Calendar.MILLISECOND) / 1000f
        if (secInt != lastSecInt) {
            lastSecInt = secInt
            dateStr = dateFmt.format(cal.time).uppercase(Locale.ENGLISH) +
                "  \u00B7  " + (if (cal.get(Calendar.AM_PM) == Calendar.AM) "AM" else "PM")
        }
        val ds = ckDs; val cx = ckCx; val y0 = ckY0
        val a = ds * .0105f
        val hStr = hr.toString().padStart(2, '0')
        val mStr = mn.toString().padStart(2, '0')

        // anchor
        c.save(); c.translate(cx, y0 + 8f * a); c.scale(a, a)
        p.style = Paint.Style.STROKE; p.strokeCap = Paint.Cap.ROUND; p.strokeJoin = Paint.Join.ROUND
        p.strokeWidth = 9f; p.color = Color.argb(40, 0, 242, 255); c.drawPath(anchor, p)
        p.strokeWidth = 3.2f; p.color = Color.rgb(0, 242, 255); c.drawPath(anchor, p)
        c.restore()

        val yh = y0 + 80f * a + ds * .95f
        val ym = yh + ds * .98f
        tp.typeface = digitFace
        tp.textSize = ds
        tp.letterSpacing = 0f
        tp.textAlign = Paint.Align.CENTER
        tp.shader = null
        // hour: neon outline
        tp.style = Paint.Style.FILL; tp.color = Color.argb(90, 0, 20, 40)
        c.drawText(hStr, cx, yh, tp)
        tp.style = Paint.Style.STROKE
        tp.strokeWidth = d * 9f; tp.color = Color.argb(30, 0, 242, 255); c.drawText(hStr, cx, yh, tp)
        tp.strokeWidth = d * 5f; tp.color = Color.argb(55, 0, 242, 255); c.drawText(hStr, cx, yh, tp)
        tp.strokeWidth = d * 1.8f; tp.color = Color.rgb(0, 242, 255); c.drawText(hStr, cx, yh, tp)
        // minute: solid white with pink glow
        tp.style = Paint.Style.FILL_AND_STROKE
        tp.strokeWidth = d * 14f; tp.color = Color.argb(25, 255, 0, 204); c.drawText(mStr, cx, ym, tp)
        tp.strokeWidth = d * 7f; tp.color = Color.argb(55, 255, 0, 204); c.drawText(mStr, cx, ym, tp)
        tp.style = Paint.Style.FILL; tp.color = Color.WHITE; c.drawText(mStr, cx, ym, tp)

        // seconds line + dot
        val lw = ds * .9f
        val yl = ym + ds * .26f
        val x0 = cx - lw / 2f
        p.style = Paint.Style.STROKE; p.strokeWidth = d * 2.4f
        p.shader = lineShader; p.color = Color.WHITE
        c.drawLine(x0, yl, x0 + lw, yl, p)
        p.shader = null
        val dx = x0 + lw * (sec / 60f)
        p.style = Paint.Style.FILL
        p.color = Color.argb(90, 0, 242, 255); c.drawCircle(dx, yl, d * 7f, p)
        p.color = Color.WHITE; c.drawCircle(dx, yl, d * 3.4f, p)

        // date + AM/PM
        tp.typeface = textFace
        tp.textSize = ds * .11f
        tp.letterSpacing = .45f
        tp.style = Paint.Style.FILL
        tp.color = Color.rgb(180, 189, 224)
        c.drawText(dateStr, cx, yl + ds * .22f, tp)
        tp.letterSpacing = 0f
    }

    private fun banner(c: Canvas, t: Float) {
        val cx = cardL + cardW / 2f
        val cy = cardT + cardH / 2f
        val rad = d * 22f
        rf.set(cardL, cardT, cardL + cardW, cardT + cardH)
        p.style = Paint.Style.STROKE
        for (k in 1..4) {
            p.strokeWidth = d * k * 6f
            p.color = Color.argb(14, 0, 242, 255)
            c.drawRoundRect(rf, rad, rad, p)
        }
        p.style = Paint.Style.FILL; p.color = Color.argb(205, 6, 6, 24)
        c.drawRoundRect(rf, rad, rad, p)
        p.style = Paint.Style.STROKE
        p.strokeWidth = d * 2f
        p.shader = borderShader; p.color = Color.WHITE
        c.drawRoundRect(rf, rad, rad, p)
        // energy arc running around the border
        val sw = sweepShader
        if (sw != null) {
            mat.setRotate((t / 3.2f) * 360f, cx, cy)
            sw.setLocalMatrix(mat)
            p.shader = sw
            p.strokeWidth = d * 2.6f
            c.drawRoundRect(rf, rad, rad, p)
        }
        p.shader = null

        // ARODHAN with energy colours flowing through the letters
        tp.typeface = bannerFace
        tp.textSize = bannerTs
        tp.letterSpacing = .16f
        tp.textAlign = Paint.Align.CENTER
        val by = cy + bannerTs * .36f
        tp.shader = null
        tp.style = Paint.Style.FILL_AND_STROKE
        tp.strokeWidth = d * 8f; tp.color = Color.argb(28, 0, 242, 255); c.drawText("ARODHAN", cx, by, tp)
        tp.strokeWidth = d * 4f; tp.color = Color.argb(45, 255, 0, 204); c.drawText("ARODHAN", cx, by, tp)
        val ts = textShader
        if (ts != null) {
            mat.setTranslate(cx - bannerTw / 2f - ((t * bannerTw * .6f) % (bannerTw * 5f)), 0f)
            ts.setLocalMatrix(mat)
            tp.shader = ts
        }
        tp.style = Paint.Style.FILL
        tp.color = Color.WHITE
        c.drawText("ARODHAN", cx, by, tp)
        tp.shader = null
        tp.letterSpacing = 0f
    }
}
