package com.arodhan.wallpaper

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.service.wallpaper.WallpaperService
import android.view.SurfaceHolder
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

private class Dot(
    var x: Float, var y: Float,
    var vx: Float, var vy: Float,
    val r: Float, val rgb: Int, val ph: Float
)

/** ARODHAN live wallpaper, drawn natively (no WebView). */
class WebWallpaperService : WallpaperService() {

    override fun onCreateEngine(): Engine = ArodhanEngine()

    inner class ArodhanEngine : Engine() {
        private val handler = Handler(Looper.getMainLooper())
        private val t0 = SystemClock.uptimeMillis()
        private val d = resources.displayMetrics.density
        private val piF = PI.toFloat()

        private val cyan = Color.rgb(0, 242, 255)
        private val violet = Color.rgb(138, 43, 226)
        private val pink = Color.rgb(255, 0, 204)
        private val muted = Color.rgb(142, 152, 189)
        private val dim = Color.rgb(95, 105, 144)
        private val palette = intArrayOf(cyan, pink, Color.rgb(190, 140, 255))

        private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
        private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
        private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
        private val rect = RectF()
        private val path = Path()
        private val black = Typeface.create("sans-serif-black", Typeface.NORMAL)
        private val medium = Typeface.create("sans-serif-medium", Typeface.NORMAL)

        private var visible = false
        private var w = 0
        private var h = 0
        private var dots = ArrayList<Dot>()
        private var blobs: Array<RadialGradient> = emptyArray()
        private var vignette: RadialGradient? = null
        private var cardBorder: LinearGradient? = null
        private var lineGrad: LinearGradient? = null

        private var ts = 0f
        private var ws = 0f
        private var gs = 0f
        private var cardW = 0f
        private var cardH = 0f
        private var cardL = 0f
        private var cardT = 0f
        private var welcomeY = 0f
        private var titleY = 0f
        private var lineY = 0f
        private var tagY = 0f
        private var lineL = 0f
        private var lineR = 0f

        private val loop = object : Runnable {
            override fun run() {
                drawFrame()
                if (visible) handler.postDelayed(this, 33L)
            }
        }

        override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            super.onSurfaceChanged(holder, format, width, height)
            w = width
            h = height
            buildLayout()
            drawFrame()
        }

        override fun onVisibilityChanged(isVisible: Boolean) {
            visible = isVisible
            handler.removeCallbacks(loop)
            if (isVisible) handler.post(loop)
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

        private fun buildLayout() {
            val big = max(w, h) * 0.6f
            blobs = arrayOf(
                RadialGradient(0f, 0f, big, Color.argb(107, 0, 242, 255), Color.argb(0, 0, 242, 255), Shader.TileMode.CLAMP),
                RadialGradient(0f, 0f, big, Color.argb(128, 138, 43, 226), Color.argb(0, 138, 43, 226), Shader.TileMode.CLAMP),
                RadialGradient(0f, 0f, big, Color.argb(97, 255, 0, 204), Color.argb(0, 255, 0, 204), Shader.TileMode.CLAMP)
            )
            vignette = RadialGradient(
                w / 2f, h / 2f, hypot(w.toFloat(), h.toFloat()) / 2f,
                intArrayOf(Color.TRANSPARENT, Color.TRANSPARENT, Color.argb(190, 2, 2, 10)),
                floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP
            )

            cardW = min(w * 0.88f, 940f * d)
            text.typeface = black
            text.letterSpacing = 0.08f
            text.textSize = 100f
            val wide = text.measureText("ARODHAN")
            ts = min(cardW * 0.84f / wide * 100f, min(h * 0.12f, 130f * d))
            ws = max(11f * d, ts * 0.17f)
            gs = max(11f * d, ts * 0.19f)

            val padV = ts * 0.8f
            cardH = padV * 2 + ws + ts * 0.55f + ts * 0.75f + ts * 0.5f + 3f * d + ts * 0.5f + gs
            cardL = (w - cardW) / 2f
            cardT = (h - cardH) / 2f

            var y = cardT + padV
            welcomeY = y + ws * 0.8f
            y += ws + ts * 0.55f
            titleY = y + ts * 0.75f
            y += ts * 0.75f + ts * 0.5f
            lineY = y
            y += 3f * d + ts * 0.5f
            tagY = y + gs * 0.8f

            val lw = min(240f * d, cardW * 0.45f)
            lineL = (w - lw) / 2f
            lineR = lineL + lw
            lineGrad = LinearGradient(lineL, 0f, lineR, 0f, cyan, pink, Shader.TileMode.CLAMP)
            cardBorder = LinearGradient(
                cardL, cardT, cardL + cardW, cardT + cardH,
                intArrayOf(cyan, violet, pink), null, Shader.TileMode.CLAMP
            )

            val n = min(80, (w * h / (16000f * d * d)).toInt())
            dots = ArrayList()
            for (i in 0 until n) {
                dots.add(
                    Dot(
                        Random.nextFloat() * w, Random.nextFloat() * h,
                        (Random.nextFloat() - 0.5f) * 0.6f * d,
                        (Random.nextFloat() - 0.5f) * 0.6f * d,
                        (Random.nextFloat() * 1.4f + 0.8f) * d,
                        palette[i % 3], Random.nextFloat() * 6.28f
                    )
                )
            }
        }

        private fun drawFrame() {
            if (w == 0 || h == 0) return
            val holder = surfaceHolder ?: return
            var canvas: Canvas? = null
            try {
                canvas = try {
                    holder.lockHardwareCanvas()
                } catch (e: Exception) {
                    holder.lockCanvas()
                }
                canvas?.let { render(it, (SystemClock.uptimeMillis() - t0) / 1000f) }
            } catch (e: Exception) {
                // skip this frame
            } finally {
                if (canvas != null) {
                    try { holder.unlockCanvasAndPost(canvas) } catch (e: Exception) { }
                }
            }
        }

        private fun lerp(a: Int, b: Int, f: Float): Int = (a + (b - a) * f).toInt()

        private fun render(c: Canvas, t: Float) {
            val wf = w.toFloat()
            val hf = h.toFloat()
            val big = max(wf, hf) * 0.6f
            c.drawColor(Color.rgb(5, 5, 16))

            // Aurora
            val cx = floatArrayOf(
                wf * (0.15f + 0.3f * sin(t * 0.12f)),
                wf * (0.85f - 0.3f * sin(t * 0.10f + 2f)),
                wf * (0.40f + 0.3f * sin(t * 0.07f + 4f))
            )
            val cy = floatArrayOf(
                hf * (0.10f + 0.2f * sin(t * 0.09f + 1f)),
                hf * (0.30f + 0.2f * sin(t * 0.08f)),
                hf * (0.95f - 0.2f * sin(t * 0.11f))
            )
            for (i in 0 until 3) {
                c.save()
                c.translate(cx[i], cy[i])
                fill.shader = blobs[i]
                c.drawCircle(0f, 0f, big, fill)
                c.restore()
            }
            fill.shader = null

            // Grid
            stroke.shader = null
            stroke.color = Color.argb(22, 0, 242, 255)
            stroke.strokeWidth = 1f * d
            val step = 80f * d
            val off = (t * 7f * d) % step
            var gx = off - step
            while (gx < wf) { c.drawLine(gx, 0f, gx, hf, stroke); gx += step }
            var gy = off - step
            while (gy < hf) { c.drawLine(0f, gy, wf, gy, stroke); gy += step }

            // Particle network
            val link = 110f * d
            stroke.strokeWidth = 0.8f * d
            for (i in dots.indices) {
                val p = dots[i]
                p.x += p.vx
                p.y += p.vy
                if (p.x < 0 || p.x > wf) p.vx = -p.vx
                if (p.y < 0 || p.y > hf) p.vy = -p.vy
                val a = (0.6f + 0.4f * sin(t + p.ph))
                fill.color = ((a * 255).toInt() shl 24) or (p.rgb and 0x00FFFFFF)
                c.drawCircle(p.x, p.y, p.r, fill)
                for (j in i + 1 until dots.size) {
                    val q = dots[j]
                    val dist = hypot(p.x - q.x, p.y - q.y)
                    if (dist < link) {
                        val la = (0.2f * (1f - dist / link) * 255).toInt()
                        stroke.color = (la shl 24) or 0x0000F2FF
                        c.drawLine(p.x, p.y, q.x, q.y, stroke)
                    }
                }
            }

            // Vignette
            fill.shader = vignette
            c.drawRect(0f, 0f, wf, hf, fill)
            fill.shader = null

            // Card glow
            val pulse = 0.45f + 0.55f * ((sin(t * 0.5f) + 1f) / 2f)
            val corner = 34f * d
            rect.set(cardL, cardT, cardL + cardW, cardT + cardH)
            for (k in 1..6) {
                stroke.color = Color.argb((22 * pulse).toInt(), 0, 242, 255)
                stroke.strokeWidth = k * 7f * d
                c.drawRoundRect(rect, corner, corner, stroke)
            }
            // Card body + border
            fill.color = Color.argb(215, 6, 6, 22)
            c.drawRoundRect(rect, corner, corner, fill)
            stroke.shader = cardBorder
            stroke.color = Color.WHITE
            stroke.strokeWidth = 2f * d
            c.drawRoundRect(rect, corner, corner, stroke)
            stroke.shader = null

            // Corner brackets
            stroke.color = Color.argb(190, 0, 242, 255)
            stroke.strokeWidth = 2f * d
            stroke.strokeCap = Paint.Cap.ROUND
            stroke.strokeJoin = Paint.Join.ROUND
            val inset = 18f * d
            val len = 20f * d
            val l = cardL + inset
            val r = cardL + cardW - inset
            val tp = cardT + inset
            val bt = cardT + cardH - inset
            path.reset()
            path.moveTo(l, tp + len); path.lineTo(l, tp); path.lineTo(l + len, tp)
            path.moveTo(r - len, tp); path.lineTo(r, tp); path.lineTo(r, tp + len)
            path.moveTo(l, bt - len); path.lineTo(l, bt); path.lineTo(l + len, bt)
            path.moveTo(r - len, bt); path.lineTo(r, bt); path.lineTo(r, bt - len)
            c.drawPath(path, stroke)

            val mid = wf / 2f

            // WELCOME TO
            text.style = Paint.Style.FILL
            text.typeface = medium
            text.letterSpacing = 0.5f
            text.textSize = ws
            text.color = muted
            c.drawText("WELCOME TO", mid, welcomeY, text)

            // ARODHAN with glow
            val f = (sin(t * 0.35f) + 1f) / 2f
            val glow = Color.rgb(lerp(0, 255, f), lerp(242, 0, f), lerp(255, 204, f))
            text.typeface = black
            text.letterSpacing = 0.08f
            text.textSize = ts
            text.style = Paint.Style.FILL_AND_STROKE
            val widths = floatArrayOf(ts * 0.16f, ts * 0.09f, ts * 0.04f)
            val alphas = intArrayOf(22, 36, 60)
            for (k in 0 until 3) {
                text.strokeWidth = widths[k]
                text.color = (alphas[k] shl 24) or (glow and 0x00FFFFFF)
                c.drawText("ARODHAN", mid, titleY, text)
            }
            text.style = Paint.Style.FILL
            text.color = Color.rgb(lerp(233, 255, f), lerp(253, 240, f), lerp(255, 251, f))
            c.drawText("ARODHAN", mid, titleY, text)

            // Line + scanning dot
            stroke.shader = lineGrad
            stroke.color = Color.WHITE
            stroke.strokeWidth = 3f * d
            c.drawLine(lineL, lineY, lineR, lineY, stroke)
            stroke.shader = null
            val sx = lineL + (lineR - lineL) * ((1f - cos(t * piF / 3.6f)) / 2f)
            fill.color = Color.argb(70, 0, 242, 255)
            c.drawCircle(sx, lineY, 12f * d, fill)
            fill.color = Color.WHITE
            c.drawCircle(sx, lineY, 5f * d, fill)

            // Tagline
            text.typeface = medium
            text.letterSpacing = 0.3f
            text.textSize = gs
            text.textAlign = Paint.Align.LEFT
            val words = arrayOf("CREATE", "LEARN", "CONQUER")
            val ww = FloatArray(3) { text.measureText(words[it]) }
            val gap = gs * 0.9f
            val dr = gs * 0.14f
            val total = ww[0] + ww[1] + ww[2] + 4 * gap + 4 * dr
            var x = mid - total / 2f
            val active = (t / 2f).toInt() % 3
            for (i in 0 until 3) {
                text.color = if (i == active) Color.WHITE else dim
                c.drawText(words[i], x, tagY, text)
                x += ww[i]
                if (i < 2) {
                    x += gap + dr
                    fill.color = pink
                    c.drawCircle(x, tagY - gs * 0.35f, dr, fill)
                    x += dr + gap
                }
            }
            text.textAlign = Paint.Align.CENTER
        }
    }
}
