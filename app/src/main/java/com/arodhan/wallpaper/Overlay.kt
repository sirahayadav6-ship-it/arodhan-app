package com.arodhan.wallpaper

import android.content.res.AssetManager
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.SweepGradient
import android.graphics.Typeface
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/** Clock (5 styles), ARODHAN banner, vignette and dimmer, drawn on top of every wallpaper. */
class Overlay(d: Float, private val th: Theme, private val am: AssetManager) : Gfx(d) {

    private var w = 0
    private var h = 0
    private var text = "ARODHAN"

    private val anchor = Path()
    private val cal = Calendar.getInstance()
    private val dateFmt = SimpleDateFormat("EEE, d MMM", Locale.ENGLISH)
    private var dateStr = ""
    private var ampm = "AM"
    private var lastSecInt = -1

    private var digitFace: Typeface = Typeface.create("sans-serif-black", Typeface.NORMAL)
    private var textFace: Typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    private var lightFace: Typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
    private var thinFace: Typeface = Typeface.create("sans-serif-thin", Typeface.NORMAL)
    private var bannerFace: Typeface = Typeface.create("sans-serif-black", Typeface.NORMAL)

    private var vigShader: Shader? = null
    private var lineShader: Shader? = null
    private var borderShader: Shader? = null
    private var sweepShader: SweepGradient? = null
    private var textShader: LinearGradient? = null
    private var ckCx = 0f
    private var ckDs = 0f
    private var ckY0 = 0f
    private var land = false
    private var cardL = 0f
    private var cardT = 0f
    private var cardW = 0f
    private var cardH = 0f
    private var bannerTs = 0f
    private var bannerTw = 0f

    private val cyan = th.acc[0]
    private val pink = th.acc[2]

    private val HW = arrayOf("twelve", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine", "ten", "eleven")
    private val SMALL = arrayOf(
        "", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine", "ten", "eleven", "twelve",
        "thirteen", "fourteen", "fifteen", "sixteen", "seventeen", "eighteen", "nineteen"
    )
    private val TENS = arrayOf("", "", "twenty", "thirty", "forty", "fifty")

    init {
        anchor.addCircle(0f, 0f, 7f, Path.Direction.CW)
        anchor.moveTo(0f, 7f); anchor.lineTo(0f, 56f)
        anchor.moveTo(-13f, 20f); anchor.lineTo(13f, 20f)
        anchor.moveTo(-30f, 38f); anchor.quadTo(-30f, 62f, 0f, 66f); anchor.quadTo(30f, 62f, 30f, 38f)
        anchor.moveTo(-30f, 38f); anchor.lineTo(-36f, 45f)
        anchor.moveTo(-30f, 38f); anchor.lineTo(-23f, 43f)
        anchor.moveTo(30f, 38f); anchor.lineTo(36f, 45f)
        anchor.moveTo(30f, 38f); anchor.lineTo(23f, 43f)
        // optional custom fonts: app/src/main/assets/fonts/orbitron.ttf and digits.ttf
        try { bannerFace = Typeface.createFromAsset(am, "fonts/orbitron.ttf") } catch (t: Throwable) { }
        try { digitFace = Typeface.createFromAsset(am, "fonts/digits.ttf") } catch (t: Throwable) { }
    }

    fun resize(width: Int, height: Int, bannerText: String) {
        w = width; h = height; text = bannerText.trim().uppercase(Locale.ENGLISH)
        val wf = w.toFloat(); val hf = h.toFloat()
        land = w > h
        val vr = sqrt(wf * wf + hf * hf) * .55f
        vigShader = RadialGradient(
            wf / 2f, hf / 2f, vr,
            intArrayOf(Color.argb(0, 2, 2, 12), Color.argb(0, 2, 2, 12), Color.argb(153, 2, 2, 12)),
            floatArrayOf(0f, min(wf, hf) * .35f / vr, 1f), Shader.TileMode.CLAMP
        )
        ckCx = if (land) wf * .17f else wf / 2f
        ckDs = if (land) hf * .17f else min(wf * .17f, hf * .095f)
        ckY0 = if (land) hf * .16f else hf * .03f
        val lw = ckDs * .9f
        val a = ckDs * .0105f
        val yl = ckY0 + 80f * a + ckDs * .95f + ckDs * .98f + ckDs * .26f
        lineShader = LinearGradient(
            ckCx - lw / 2f, yl, ckCx + lw / 2f, yl,
            Color.rgb(cyan[0], cyan[1], cyan[2]), Color.rgb(pink[0], pink[1], pink[2]), Shader.TileMode.CLAMP
        )

        cardW = if (land) wf * .48f else wf * .76f
        cardL = if (land) wf * .46f else wf * .12f
        val shown = if (text.isEmpty()) "ARODHAN" else text
        tp.typeface = bannerFace
        tp.letterSpacing = .16f
        tp.textSize = 100f
        val tw100 = tp.measureText(shown)
        bannerTs = min(cardW * .8f / tw100 * 100f, hf * .045f)
        tp.textSize = bannerTs
        bannerTw = tp.measureText(shown)
        cardH = bannerTs * 2.2f
        cardT = hf * .85f - cardH
        val cx = cardL + cardW / 2f
        val cy = cardT + cardH / 2f
        borderShader = LinearGradient(
            cardL, cardT, cardL + cardW, cardT + cardH,
            intArrayOf(argbF(cyan, .55f), Color.argb(140, 138, 43, 226), argbF(pink, .55f)),
            null, Shader.TileMode.CLAMP
        )
        sweepShader = SweepGradient(
            cx, cy,
            intArrayOf(
                argbF(cyan, 0f), argbF(cyan, 0f), Color.rgb(cyan[0], cyan[1], cyan[2]),
                Color.WHITE, Color.rgb(pink[0], pink[1], pink[2]), argbF(pink, 0f)
            ),
            floatArrayOf(0f, .52f, .68f, .74f, .84f, 1f)
        )
        textShader = LinearGradient(
            0f, 0f, bannerTw * 2.5f, 0f,
            intArrayOf(
                Color.rgb(cyan[0], cyan[1], cyan[2]), Color.WHITE, Color.rgb(pink[0], pink[1], pink[2]),
                Color.rgb(cyan[0], cyan[1], cyan[2]), Color.WHITE
            ),
            null, Shader.TileMode.MIRROR
        )
    }

    fun draw(c: Canvas, t: Float, clockStyle: Int, showBanner: Boolean, dim: Int) {
        if (w == 0 || h == 0) return
        add(false)
        try { if (clockStyle in 0..3) clock(c, clockStyle) } catch (e: Throwable) { }
        try {
            p.style = Paint.Style.FILL; p.shader = vigShader; p.color = Color.WHITE
            c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), p); p.shader = null
        } catch (e: Throwable) { }
        try { if (showBanner) banner(c, t) } catch (e: Throwable) { }
        if (dim > 0) {
            p.style = Paint.Style.FILL; p.shader = null
            p.color = Color.argb((dim * 255 / 100).coerceIn(0, 200), 0, 0, 0)
            c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), p)
        }
    }

    private fun minuteWords(m: Int): String {
        if (m == 0) return "o'clock"
        if (m < 20) return (if (m < 10) "oh " else "") + SMALL[m]
        return TENS[m / 10] + (if (m % 10 != 0) " " + SMALL[m % 10] else "")
    }

    private fun clock(c: Canvas, style: Int) {
        cal.timeInMillis = System.currentTimeMillis()
        val hr = cal.get(Calendar.HOUR).let { if (it == 0) 12 else it }
        val mn = cal.get(Calendar.MINUTE)
        val secInt = cal.get(Calendar.SECOND)
        val sec = secInt + cal.get(Calendar.MILLISECOND) / 1000f
        if (secInt != lastSecInt) {
            lastSecInt = secInt
            dateStr = dateFmt.format(cal.time).uppercase(Locale.ENGLISH)
            ampm = if (cal.get(Calendar.AM_PM) == Calendar.AM) "AM" else "PM"
        }
        when (style) {
            0 -> stack(c, hr, mn, sec)
            1 -> words(c, hr, mn)
            2 -> ring(c, hr, mn, sec)
            else -> minimal(c, hr, mn)
        }
    }

    private fun glowText(c: Canvas, s: String, x: Float, y: Float, col: IntArray, w1: Float, w2: Float) {
        tp.style = Paint.Style.FILL_AND_STROKE
        tp.strokeWidth = w1; tp.color = argbF(col, .1f); c.drawText(s, x, y, tp)
        tp.strokeWidth = w2; tp.color = argbF(col, .22f); c.drawText(s, x, y, tp)
        tp.style = Paint.Style.FILL
    }

    private fun stack(c: Canvas, hr: Int, mn: Int, sec: Float) {
        val ds = ckDs; val cx = ckCx; val y0 = ckY0
        val a = ds * .0105f
        val hStr = hr.toString().padStart(2, '0')
        val mStr = mn.toString().padStart(2, '0')
        c.save(); c.translate(cx, y0 + 8f * a); c.scale(a, a)
        p.style = Paint.Style.STROKE; p.strokeCap = Paint.Cap.ROUND; p.strokeJoin = Paint.Join.ROUND
        p.strokeWidth = 9f; p.color = argbF(cyan, .16f); c.drawPath(anchor, p)
        p.strokeWidth = 3.2f; p.color = argbF(cyan, 1f); c.drawPath(anchor, p)
        c.restore()

        val yh = y0 + 80f * a + ds * .95f
        val ym = yh + ds * .98f
        tp.typeface = digitFace; tp.textSize = ds; tp.letterSpacing = 0f; tp.textAlign = Paint.Align.CENTER; tp.shader = null
        tp.style = Paint.Style.FILL; tp.color = Color.argb(90, 0, 20, 40); c.drawText(hStr, cx, yh, tp)
        tp.style = Paint.Style.STROKE
        tp.strokeWidth = d * 9f; tp.color = argbF(cyan, .12f); c.drawText(hStr, cx, yh, tp)
        tp.strokeWidth = d * 5f; tp.color = argbF(cyan, .22f); c.drawText(hStr, cx, yh, tp)
        tp.strokeWidth = d * 1.8f; tp.color = argbF(cyan, 1f); c.drawText(hStr, cx, yh, tp)
        glowText(c, mStr, cx, ym, pink, d * 14f, d * 7f)
        tp.color = Color.WHITE; c.drawText(mStr, cx, ym, tp)

        val lw = ds * .9f
        val yl = ym + ds * .26f
        val x0 = cx - lw / 2f
        p.style = Paint.Style.STROKE; p.strokeWidth = d * 2.4f
        p.shader = lineShader; p.color = Color.WHITE
        c.drawLine(x0, yl, x0 + lw, yl, p)
        p.shader = null
        val dx = x0 + lw * (sec / 60f)
        p.style = Paint.Style.FILL
        p.color = argbF(cyan, .35f); c.drawCircle(dx, yl, d * 7f, p)
        p.color = Color.WHITE; c.drawCircle(dx, yl, d * 3.4f, p)
        dateLine(c, cx, yl + ds * .22f, ds * .11f)
    }

    private fun dateLine(c: Canvas, cx: Float, y: Float, size: Float) {
        tp.typeface = textFace; tp.textSize = size; tp.letterSpacing = .4f
        tp.style = Paint.Style.FILL; tp.color = Color.rgb(180, 189, 224); tp.textAlign = Paint.Align.CENTER
        c.drawText("$dateStr  \u00B7  $ampm", cx, y, tp)
        tp.letterSpacing = 0f
    }

    private fun words(c: Canvas, hr: Int, mn: Int) {
        val ds = ckDs; val cx = ckCx
        val l1 = HW[hr % 12]
        val l2 = minuteWords(mn)
        val maxW = if (land) w * .3f else w * .86f
        var fs = ds * .66f
        tp.typeface = lightFace; tp.textSize = fs; tp.letterSpacing = 0f
        val w1 = tp.measureText(l1)
        tp.typeface = textFace
        val w2 = tp.measureText(l2)
        val wide = if (w1 > w2) w1 else w2
        if (wide > maxW) fs = fs * maxW / wide
        tp.textAlign = Paint.Align.CENTER; tp.textSize = fs
        val y1 = ckY0 + fs * 1.0f
        val y2 = y1 + fs * 1.1f
        tp.typeface = lightFace; tp.style = Paint.Style.FILL; tp.color = Color.WHITE
        c.drawText(l1, cx, y1, tp)
        tp.typeface = textFace
        glowText(c, l2, cx, y2, cyan, d * 12f, d * 6f)
        tp.color = Color.rgb(cyan[0], cyan[1], cyan[2]); c.drawText(l2, cx, y2, tp)
        dateLine(c, cx, y2 + fs * .6f, ds * .11f)
    }

    private fun ring(c: Canvas, hr: Int, mn: Int, sec: Float) {
        val ds = ckDs; val cx = ckCx
        val r = ds * 1.05f
        val cy = ckY0 + r + ds * .1f
        rf.set(cx - r, cy - r, cx + r, cy + r)
        p.style = Paint.Style.STROKE; p.strokeCap = Paint.Cap.ROUND
        p.strokeWidth = d * 4f; p.color = Color.argb(30, 255, 255, 255); c.drawOval(rf, p)
        for (i in 0 until 12) {
            val a = i * 0.5236f
            val r1 = r - d * 10f; val r2 = r - d * 4f
            p.strokeWidth = d * (if (i % 3 == 0) 2.2f else 1f)
            p.color = argbF(cyan, if (i % 3 == 0) .9f else .4f)
            c.drawLine(cx + sin(a) * r1, cy - cos(a) * r1, cx + sin(a) * r2, cy - cos(a) * r2, p)
        }
        p.strokeWidth = d * 7f; p.color = argbF(cyan, .15f); c.drawArc(rf, -90f, sec * 6f, false, p)
        p.strokeWidth = d * 3.4f; p.color = argbF(cyan, 1f); c.drawArc(rf, -90f, sec * 6f, false, p)
        val ha = sec * .10472f
        p.style = Paint.Style.FILL
        p.color = argbF(pink, .4f); c.drawCircle(cx + sin(ha) * r, cy - cos(ha) * r, d * 9f, p)
        p.color = Color.WHITE; c.drawCircle(cx + sin(ha) * r, cy - cos(ha) * r, d * 4f, p)
        val ts = "$hr:" + mn.toString().padStart(2, '0')
        tp.typeface = digitFace; tp.textSize = r * .62f; tp.letterSpacing = 0f; tp.textAlign = Paint.Align.CENTER
        glowText(c, ts, cx, cy + r * .2f, cyan, d * 10f, d * 5f)
        tp.color = Color.WHITE; c.drawText(ts, cx, cy + r * .2f, tp)
        tp.typeface = textFace; tp.textSize = r * .17f; tp.letterSpacing = .3f; tp.color = argbF(pink, 1f)
        c.drawText(ampm, cx, cy + r * .5f, tp)
        tp.letterSpacing = 0f
        dateLine(c, cx, cy + r + ds * .3f, ds * .11f)
    }

    private fun minimal(c: Canvas, hr: Int, mn: Int) {
        val ds = ckDs; val cx = ckCx
        val ts = "$hr:" + mn.toString().padStart(2, '0')
        tp.typeface = thinFace; tp.textSize = ds * 1.05f; tp.letterSpacing = 0f; tp.textAlign = Paint.Align.CENTER
        tp.style = Paint.Style.FILL; tp.color = Color.WHITE
        val y = ckY0 + ds * 1.1f
        c.drawText(ts, cx, y, tp)
        tp.typeface = textFace; tp.textSize = ds * .13f; tp.letterSpacing = .35f; tp.color = argbF(cyan, 1f)
        c.drawText(ampm, cx, y + ds * .32f, tp)
        tp.letterSpacing = 0f
        dateLine(c, cx, y + ds * .62f, ds * .11f)
    }

    private fun banner(c: Canvas, t: Float) {
        val shown = if (text.isEmpty()) "ARODHAN" else text
        val cx = cardL + cardW / 2f
        val cy = cardT + cardH / 2f
        val rad = d * 22f
        rf.set(cardL, cardT, cardL + cardW, cardT + cardH)
        p.style = Paint.Style.STROKE
        for (k in 1..4) {
            p.strokeWidth = d * k * 6f
            p.color = argbF(cyan, .055f)
            c.drawRoundRect(rf, rad, rad, p)
        }
        p.style = Paint.Style.FILL; p.color = Color.argb(205, 6, 6, 24)
        c.drawRoundRect(rf, rad, rad, p)
        p.style = Paint.Style.STROKE
        p.strokeWidth = d * 2f
        p.shader = borderShader; p.color = Color.WHITE
        c.drawRoundRect(rf, rad, rad, p)
        val sw = sweepShader
        if (sw != null) {
            mat.setRotate((t / 3.2f) * 360f, cx, cy)
            sw.setLocalMatrix(mat)
            p.shader = sw
            p.strokeWidth = d * 2.6f
            c.drawRoundRect(rf, rad, rad, p)
        }
        p.shader = null

        tp.typeface = bannerFace
        tp.textSize = bannerTs
        tp.letterSpacing = .16f
        tp.textAlign = Paint.Align.CENTER
        val by = cy + bannerTs * .10f
        tp.shader = null
        tp.style = Paint.Style.FILL_AND_STROKE
        tp.strokeWidth = d * 8f; tp.color = argbF(cyan, .11f); c.drawText(shown, cx, by, tp)
        tp.strokeWidth = d * 4f; tp.color = argbF(pink, .18f); c.drawText(shown, cx, by, tp)
        val ts = textShader
        if (ts != null) {
            mat.setTranslate(cx - bannerTw / 2f - ((t * bannerTw * .6f) % (bannerTw * 5f)), 0f)
            ts.setLocalMatrix(mat)
            tp.shader = ts
        }
        tp.style = Paint.Style.FILL
        tp.color = Color.WHITE
        c.drawText(shown, cx, by, tp)
        tp.shader = null
        tp.letterSpacing = 0f
    }
}
