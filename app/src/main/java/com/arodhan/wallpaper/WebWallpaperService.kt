package com.arodhan.wallpaper

import android.app.WallpaperManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.graphics.Canvas
import android.graphics.Color
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.BatteryManager
import android.os.Bundle
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.service.wallpaper.WallpaperService
import android.view.MotionEvent
import android.view.SurfaceHolder
import java.util.Calendar
import kotlin.math.max

class WebWallpaperService : WallpaperService() {

    override fun onCreateEngine(): Engine = ArodhanEngine()

    inner class ArodhanEngine : Engine(), SensorEventListener {
        private val ctx: Context = this@WebWallpaperService
        private val handler = Handler(Looper.getMainLooper())
        private val prefs: SharedPreferences = Config.prefs(ctx)
        private var cfg: Config = Config.load(ctx)
        private var cfgDirty = false
        private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> cfgDirty = true }

        private var wall: Wall? = null
        private var overlay: Overlay? = null
        private var fx: TouchFx? = null
        private var lastBuzz = 0L
        private var lastMoveFx = 0L
        private var curWall = -1
        private var curTheme = -1
        private var curText = ""
        private var w = 0
        private var h = 0

        private var tt = 0f
        private var tReal = 0f
        private var lastMs = SystemClock.uptimeMillis()
        private var visible = false
        private var useHw = true
        private var errors = 0

        private var tiltX = 0f
        private var tiltY = 0f
        private var sensors: SensorManager? = null
        private var saver = false
        private var lastBatMs = 0L

        private val loop = object : Runnable {
            override fun run() {
                val st = SystemClock.uptimeMillis()
                drawFrame()
                if (visible) {
                    val fps = if (saver) 15 else cfg.fps.coerceIn(10, 60)
                    val spent = SystemClock.uptimeMillis() - st
                    handler.postDelayed(this, max(1L, 1000L / fps - spent))
                }
            }
        }

        override fun onCreate(surfaceHolder: SurfaceHolder) {
            super.onCreate(surfaceHolder)
            setTouchEventsEnabled(true)
            prefs.registerOnSharedPreferenceChangeListener(listener)
            sensors = ctx.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        }

        override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            super.onSurfaceChanged(holder, format, width, height)
            w = width; h = height
            try {
                wall?.resize(w, h)
                overlay?.resize(w, h, cfg.text)
            } catch (e: Throwable) { errors++ }
            drawFrame()
        }

        override fun onVisibilityChanged(isVisible: Boolean) {
            visible = isVisible
            handler.removeCallbacks(loop)
            if (isVisible) {
                lastMs = SystemClock.uptimeMillis()
                cfgDirty = true
                startSensor()
                handler.post(loop)
            } else {
                stopSensor()
            }
        }

        override fun onSurfaceDestroyed(holder: SurfaceHolder) {
            visible = false
            handler.removeCallbacks(loop)
            stopSensor()
            super.onSurfaceDestroyed(holder)
        }

        override fun onDestroy() {
            visible = false
            handler.removeCallbacks(loop)
            stopSensor()
            try { prefs.unregisterOnSharedPreferenceChangeListener(listener) } catch (e: Throwable) { }
            super.onDestroy()
        }

        // ---------------- tilt (parallax) ----------------
        private fun startSensor() {
            try {
                if (cfg.parallax) {
                    val sm = sensors ?: return
                    val s = sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) ?: return
                    sm.registerListener(this, s, SensorManager.SENSOR_DELAY_GAME)
                }
            } catch (e: Throwable) { }
        }

        private fun stopSensor() {
            try { sensors?.unregisterListener(this) } catch (e: Throwable) { }
            tiltX = 0f; tiltY = 0f
        }

        override fun onSensorChanged(ev: SensorEvent) {
            val x = (-ev.values[0] / 9.8f).coerceIn(-1f, 1f)
            val y = (ev.values[1] / 9.8f).coerceIn(-1f, 1f)
            tiltX += (x - tiltX) * .08f
            tiltY += (y - tiltY) * .08f
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) { }

        // ---------------- touch energy ----------------
        private fun spawn(x: Float, y: Float, big: Boolean) {
            if (!cfg.touch) return
            fx?.burst(x, y, tReal, big)
            if (big && cfg.haptic) buzz()
        }

        private fun buzz() {
            val now = SystemClock.uptimeMillis()
            if (now - lastBuzz < 80L) return
            lastBuzz = now
            try {
                val v = ctx.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator ?: return
                if (Build.VERSION.SDK_INT >= 26) {
                    v.vibrate(VibrationEffect.createOneShot(22L, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    v.vibrate(22L)
                }
            } catch (e: Throwable) { }
        }

        override fun onTouchEvent(event: MotionEvent) {
            super.onTouchEvent(event)
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                    val i = event.actionIndex
                    spawn(event.getX(i), event.getY(i), true)
                }
                MotionEvent.ACTION_MOVE -> {
                    val now = SystemClock.uptimeMillis()
                    if (now - lastMoveFx > 70L) {
                        lastMoveFx = now
                        spawn(event.x, event.y, false)
                    }
                }
                else -> { }
            }
        }

        override fun onCommand(action: String?, x: Int, y: Int, z: Int, extras: Bundle?, resultRequested: Boolean): Bundle? {
            if (action == WallpaperManager.COMMAND_TAP || action == WallpaperManager.COMMAND_SECONDARY_TAP || action == WallpaperManager.COMMAND_DROP) {
                spawn(x.toFloat(), y.toFloat(), true)
            }
            return super.onCommand(action, x, y, z, extras, resultRequested)
        }

        // ---------------- helpers ----------------
        private fun pickWall(): Int {
            val n = Names.walls.size
            val id = when (cfg.mode) {
                1 -> ((System.currentTimeMillis() / 60000L / max(1, cfg.rotateMin)) % n).toInt()
                2 -> {
                    val hr = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
                    if (hr in 6..17) cfg.dayWall else cfg.nightWall
                }
                else -> cfg.wall
            }
            return id.coerceIn(0, n - 1)
        }

        private fun checkBattery(now: Long) {
            if (now - lastBatMs < 30000L) return
            lastBatMs = now
            try {
                val bi = ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
                var low = false
                if (bi != null) {
                    val level = bi.getIntExtra(BatteryManager.EXTRA_LEVEL, 100)
                    val scale = bi.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
                    val status = bi.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                    val charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
                    val pct = if (scale > 0) level * 100 / scale else 100
                    low = pct <= 20 && !charging
                }
                val pm = ctx.getSystemService(Context.POWER_SERVICE) as? PowerManager
                val ps = pm?.isPowerSaveMode ?: false
                saver = cfg.autoSaver && (low || ps)
            } catch (e: Throwable) { saver = false }
        }

        private fun ensure(now: Long) {
            if (cfgDirty) {
                cfg = Config.load(ctx)
                cfgDirty = false
                if (visible) { stopSensor(); startSensor() }
            }
            checkBattery(now)
            val id = pickWall()
            val ti = cfg.theme.coerceIn(0, Themes.all.size - 1)
            val d = resources.displayMetrics.density
            if (wall == null || overlay == null || id != curWall || ti != curTheme) {
                val th = Themes.all[ti]
                val nw = makeWall(id, d, th, assets)
                val no = Overlay(d, th, assets)
                if (w > 0 && h > 0) {
                    nw.resize(w, h)
                    no.resize(w, h, cfg.text)
                }
                wall = nw; overlay = no
                fx = TouchFx(d, th)
                curWall = id; curTheme = ti; curText = cfg.text
            } else if (cfg.text != curText) {
                if (w > 0 && h > 0) overlay?.resize(w, h, cfg.text)
                curText = cfg.text
            }
        }

        private fun drawFrame() {
            val holder = surfaceHolder ?: return
            val now = SystemClock.uptimeMillis()
            try { ensure(now) } catch (e: Throwable) { errors++ }
            val wl = wall
            val ov = overlay
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
                    canvas.drawColor(Color.rgb(4, 6, 24))
                    val dtReal = (now - lastMs).coerceIn(0L, 50L) / 1000f
                    lastMs = now
                    tReal += dtReal
                    val dts = dtReal * cfg.speed
                    tt += dts
                    val fancy = cfg.energy >= 1 && !saver
                    val tx = if (cfg.parallax) tiltX else 0f
                    val ty = if (cfg.parallax) tiltY else 0f
                    if (wl != null) { try { wl.draw(canvas, tt, dts, tx, ty, fancy) } catch (e: Throwable) { errors++ } }
                    if (ov != null) { try { ov.draw(canvas, tReal, cfg.clock, cfg.banner, cfg.dim) } catch (e: Throwable) { errors++ } }
                    if (cfg.touch) { try { fx?.draw(canvas, tReal) } catch (e: Throwable) { errors++ } }
                }
            } catch (e: Throwable) {
                errors++
                if (errors > 20) useHw = false
            } finally {
                if (canvas != null) {
                    try { holder.unlockCanvasAndPost(canvas) } catch (e: Throwable) { }
                }
            }
        }
    }
}
