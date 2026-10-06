package com.arodhan.wallpaper

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin

/** Energy burst where you touch: flash, vibrating rings, jagged energy spikes and flying sparks in random colours. */
class TouchFx(d: Float, private val th: Theme) : Gfx(d) {

    private class B(
        val x: Float, val y: Float, val t0: Float, val big: Boolean, val c1: Int, val c2: Int,
        val sa: FloatArray, val sl: FloatArray, val sj: FloatArray, val pa: FloatArray, val pv: FloatArray
    )

    private val LIFE = 1.6f
    private val pal: Array<IntArray> = arrayOf(
        th.acc[0], th.acc[1], th.acc[2], th.acc[3], th.acc[4], th.light[0], th.light[1], th.light[2]
    )
    private val gs = Array(pal.size) { glow(pal[it], 0.9f) }
    private val list = ArrayList<B>()

    fun burst(x: Float, y: Float, t: Float, big: Boolean) {
        val ns = if (big) 12 else 5
        val np = if (big) 32 else 8
        val c1 = rnd.nextInt(pal.size)
        var c2 = rnd.nextInt(pal.size)
        if (c2 == c1) c2 = (c2 + 3) % pal.size
        val sa = FloatArray(ns) { rnd.nextFloat() * 6.2832f }
        val sl = FloatArray(ns) { .4f + rnd.nextFloat() * .6f }
        val sj = FloatArray(ns * 4) { rnd.nextFloat() * 2f - 1f }
        val pa = FloatArray(np) { rnd.nextFloat() * 6.2832f }
        val pv = FloatArray(np) { .3f + rnd.nextFloat() * .9f }
        list.add(B(x, y, t, big, c1, c2, sa, sl, sj, pa, pv))
        while (list.size > 14) list.removeAt(0)
    }

    fun draw(c: Canvas, t: Float) {
        if (list.isEmpty()) return
        list.removeAll { t - it.t0 > LIFE }
        if (list.isEmpty()) return
        add(true)
        for (b in list) {
            val age = t - b.t0
            if (age < 0f) continue
            val fade = (1f - age / LIFE).coerceIn(0f, 1f)
            val sc = if (b.big) 1f else .55f

            // flash
            spr(c, gs[b.c1], b.x, b.y, d * (26f + 150f * age) * sc, (1f - age / .7f).coerceIn(0f, 1f))
            spr(c, gs[7], b.x, b.y, d * (14f + 60f * age) * sc, (1f - age / .4f).coerceIn(0f, 1f))

            // vibrating rings
            p.style = Paint.Style.STROKE
            for (r in 0 until 3) {
                val rr = d * (age * 620f * sc - r * 46f)
                if (rr > 0f) {
                    val wob = sin(age * 60f + r) * d * 3f
                    p.strokeWidth = d * (1.5f + 4f * fade)
                    p.color = argbF(if (r == 1) pal[b.c2] else pal[b.c1], fade * (.7f - r * .15f))
                    c.drawCircle(b.x, b.y, rr + wob, p)
                }
            }

            // jagged energy spikes
            p.strokeCap = Paint.Cap.ROUND
            val reach = 1f - exp(-age * 6f)
            for (i in b.sa.indices) {
                val a = b.sa[i]
                val len = d * (60f + b.sl[i] * 260f) * sc * reach
                val ca = cos(a)
                val sa = sin(a)
                var px = b.x
                var py = b.y
                for (s in 1..4) {
                    val f = s / 4f
                    val j = b.sj[i * 4 + s - 1] * d * 12f * f
                    val nx = b.x + ca * len * f - sa * j
                    val ny = b.y + sa * len * f + ca * j
                    p.strokeWidth = d * (2.6f * fade + .6f)
                    p.color = argbF(if (s % 2 == 0) pal[b.c2] else pal[b.c1], fade * .9f)
                    c.drawLine(px, py, nx, ny, p)
                    p.strokeWidth = d * .9f
                    p.color = Color.argb((fade * .8f * 255f).toInt().coerceIn(0, 255), 255, 255, 255)
                    c.drawLine(px, py, nx, ny, p)
                    px = nx; py = ny
                }
            }

            // flying sparks
            for (i in b.pa.indices) {
                val dist = b.pv[i] * d * (1f - exp(-age * 3.2f)) * 260f * sc
                val x = b.x + cos(b.pa[i]) * dist
                val y = b.y + sin(b.pa[i]) * dist + age * age * d * 40f
                val tx = b.x + cos(b.pa[i]) * dist * .88f
                val ty = b.y + sin(b.pa[i]) * dist * .88f + age * age * d * 40f * .88f
                p.strokeWidth = d * 1.4f
                p.color = argbF(pal[(b.c1 + i) % pal.size], fade * .5f)
                c.drawLine(tx, ty, x, y, p)
                spr(c, gs[(b.c1 + i) % pal.size], x, y, d * (2.5f * fade + .8f) * 3.2f, fade)
            }
        }
        add(false)
    }
}
