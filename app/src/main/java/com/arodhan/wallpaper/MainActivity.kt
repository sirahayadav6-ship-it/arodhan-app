package com.arodhan.wallpaper

import android.app.Activity
import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.SystemClock
import android.text.Editable
import android.text.InputFilter
import android.text.TextWatcher
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView

/** Live preview: draws exactly what the wallpaper draws, using the saved settings. */
class PreviewView(ctx: Context) : View(ctx) {
    private var wall: Wall? = null
    private var overlay: Overlay? = null
    private var fx: TouchFx? = null
    private var lastFx = 0L
    private var curWall = -1
    private var curTheme = -1
    private var curText = ""
    private var tt = 0f
    private var last = SystemClock.uptimeMillis()

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        super.onSizeChanged(w, h, ow, oh)
        curWall = -1
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        val cfg = Config.load(context)
        if (!cfg.touch) return true
        val a = e.actionMasked
        if (a == MotionEvent.ACTION_DOWN) {
            fx?.burst(e.x, e.y, tt, true)
            if (cfg.haptic) performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        } else if (a == MotionEvent.ACTION_MOVE) {
            val now = SystemClock.uptimeMillis()
            if (now - lastFx > 70L) {
                lastFx = now
                fx?.burst(e.x, e.y, tt, false)
            }
        }
        return true
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width
        val h = height
        if (w > 0 && h > 0) {
            try {
                val cfg = Config.load(context)
                val d = resources.displayMetrics.density
                val ti = cfg.theme.coerceIn(0, Themes.all.size - 1)
                val id = cfg.wall.coerceIn(0, Names.walls.size - 1)
                if (wall == null || id != curWall || ti != curTheme) {
                    val th = Themes.all[ti]
                    val nw = makeWall(id, d, th, context.assets)
                    nw.resize(w, h)
                    val no = Overlay(d, th, context.assets)
                    no.resize(w, h, cfg.text)
                    wall = nw; overlay = no
                    fx = TouchFx(d, th)
                    curWall = id; curTheme = ti; curText = cfg.text
                } else if (cfg.text != curText) {
                    overlay?.resize(w, h, cfg.text)
                    curText = cfg.text
                }
                val now = SystemClock.uptimeMillis()
                val dt = ((now - last).coerceIn(0L, 50L)) / 1000f
                last = now
                tt += dt * cfg.speed
                canvas.drawColor(Color.rgb(4, 6, 24))
                wall?.draw(canvas, tt, dt * cfg.speed, 0f, 0f, cfg.energy >= 1)
                overlay?.draw(canvas, tt, cfg.clock, cfg.banner, cfg.dim)
                if (cfg.touch) fx?.draw(canvas, tt)
            } catch (e: Throwable) {
                canvas.drawColor(Color.rgb(4, 6, 24))
            }
        }
        postInvalidateOnAnimation()
    }
}

class MainActivity : Activity() {

    private lateinit var prefs: SharedPreferences

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun pill(sel: Boolean): GradientDrawable {
        val g = GradientDrawable()
        g.cornerRadius = dp(20).toFloat()
        g.setColor(Color.parseColor(if (sel) "#0B6A73" else "#12142B"))
        g.setStroke(dp(1), Color.parseColor(if (sel) "#00F2FF" else "#2A2D52"))
        return g
    }

    private fun header(parent: ViewGroup, s: String) {
        val tv = TextView(this)
        tv.text = s
        tv.textSize = 16f
        tv.typeface = Typeface.DEFAULT_BOLD
        tv.setTextColor(Color.parseColor("#00F2FF"))
        tv.setPadding(0, dp(18), 0, dp(8))
        parent.addView(tv)
    }

    private fun note(parent: ViewGroup, s: String) {
        val tv = TextView(this)
        tv.text = s
        tv.textSize = 12f
        tv.setTextColor(Color.parseColor("#8E98BD"))
        tv.setPadding(0, dp(4), 0, dp(4))
        parent.addView(tv)
    }

    private fun chips(parent: ViewGroup, names: Array<String>, initial: Int, onPick: (Int) -> Unit) {
        val hs = HorizontalScrollView(this)
        hs.isHorizontalScrollBarEnabled = false
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        val tvs = ArrayList<TextView>()
        for (i in names.indices) {
            val tv = TextView(this)
            tv.text = names[i]
            tv.textSize = 14f
            tv.setTextColor(Color.WHITE)
            tv.setPadding(dp(14), dp(9), dp(14), dp(9))
            tv.setOnClickListener {
                for (j in tvs.indices) tvs[j].background = pill(j == i)
                onPick(i)
            }
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            lp.setMargins(0, 0, dp(8), 0)
            row.addView(tv, lp)
            tvs.add(tv)
        }
        for (j in tvs.indices) tvs[j].background = pill(j == initial)
        hs.addView(row)
        parent.addView(hs)
    }

    private fun seek(parent: ViewGroup, label: String, min: Int, max: Int, value: Int, fmt: (Int) -> String, onChange: (Int) -> Unit) {
        val lbl = TextView(this)
        lbl.textSize = 14f
        lbl.setTextColor(Color.WHITE)
        lbl.text = label + ": " + fmt(value)
        lbl.setPadding(0, dp(8), 0, 0)
        val sb = SeekBar(this)
        sb.max = max - min
        sb.progress = value - min
        sb.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, progress: Int, fromUser: Boolean) {
                val v = progress + min
                lbl.text = label + ": " + fmt(v)
                if (fromUser) onChange(v)
            }
            override fun onStartTrackingTouch(s: SeekBar?) { }
            override fun onStopTrackingTouch(s: SeekBar?) { }
        })
        parent.addView(lbl)
        parent.addView(sb)
    }

    private fun toggle(parent: ViewGroup, label: String, on: Boolean, onChange: (Boolean) -> Unit) {
        val sw = Switch(this)
        sw.text = label
        sw.textSize = 14f
        sw.setTextColor(Color.WHITE)
        sw.isChecked = on
        sw.setPadding(0, dp(8), 0, dp(8))
        sw.setOnCheckedChangeListener { _, checked -> onChange(checked) }
        parent.addView(sw)
    }

    private fun putInt(k: String, v: Int) { prefs.edit().putInt(k, v).apply() }
    private fun putBool(k: String, v: Boolean) { prefs.edit().putBoolean(k, v).apply() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Config.prefs(this)
        val cfg = Config.load(this)

        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setBackgroundColor(Color.parseColor("#050510"))

        val preview = PreviewView(this)
        val ph = (resources.displayMetrics.heightPixels * 0.36f).toInt()
        root.addView(preview, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ph))

        val sv = ScrollView(this)
        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        col.setPadding(dp(16), dp(4), dp(16), dp(40))
        sv.addView(col)
        root.addView(sv, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        // --- set wallpaper button ---
        val setBtn = Button(this)
        setBtn.text = "Set as wallpaper"
        setBtn.setOnClickListener { openWallpaperPreview() }
        val blp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        blp.setMargins(0, dp(12), 0, 0)
        col.addView(setBtn, blp)

        header(col, "Wallpaper")
        chips(col, Names.walls, cfg.wall) { putInt("wall", it) }
        note(col, "Photo Live needs your picture saved as photo.jpg in app/src/main/assets (otherwise it shows Nebula).")

        header(col, "Colour theme")
        chips(col, Array(Themes.all.size) { Themes.all[it].name }, cfg.theme) { putInt("theme", it) }

        header(col, "Clock")
        chips(col, Names.clocks, cfg.clock) { putInt("clock", it) }

        header(col, "Banner text")
        toggle(col, "Show banner", cfg.banner) { putBool("banner", it) }
        val et = EditText(this)
        et.setText(cfg.text)
        et.setTextColor(Color.WHITE)
        et.setHintTextColor(Color.GRAY)
        et.hint = "ARODHAN"
        et.filters = arrayOf(InputFilter.LengthFilter(40))
        et.setSingleLine(true)
        et.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) { }
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) { }
            override fun afterTextChanged(s: Editable?) {
                prefs.edit().putString("text", s?.toString()?.trim() ?: "").apply()
            }
        })
        col.addView(et)

        header(col, "Look and feel")
        seek(col, "Animation speed", 25, 250, (cfg.speed * 100f).toInt(), { "x" + (it / 100f) }) { putInt("speed", it) }
        seek(col, "Dim screen", 0, 60, cfg.dim, { "$it%" }) { putInt("dim", it) }
        header(col, "Energy effects")
        chips(col, arrayOf("Low", "Medium", "High"), cfg.energy) { putInt("energy", it) }
        note(col, "Low removes lightning, streams, rays and orbit rings (saves battery).")
        toggle(col, "Tilt parallax (moves with your tablet)", cfg.parallax) { putBool("parallax", it) }
        header(col, "Touch")
        toggle(col, "Energy burst where you touch", cfg.touch) { putBool("touch", it) }
        toggle(col, "Vibrate on touch", cfg.haptic) { putBool("haptic", it) }
        note(col, "Try it on the preview at the top. On the home screen it works only if your launcher passes touches to the wallpaper (most pass taps).")

        header(col, "Smoothness (FPS)")
        val fpsList = intArrayOf(15, 20, 30, 45, 60)
        var fi = fpsList.indexOf(cfg.fps)
        if (fi < 0) fi = 2
        chips(col, Array(fpsList.size) { fpsList[it].toString() }, fi) { putInt("fps", fpsList[it]) }
        toggle(col, "Auto battery saver (below 20% or power-saving mode)", cfg.autoSaver) { putBool("saver", it) }

        header(col, "Wallpaper schedule")
        chips(col, arrayOf("Fixed", "Auto-rotate", "Day / Night"), cfg.mode) { putInt("mode", it) }
        note(col, "Fixed uses the wallpaper chosen above.")
        note(col, "Auto-rotate: change every")
        val mins = intArrayOf(1, 5, 15, 60)
        var mi = mins.indexOf(cfg.rotateMin)
        if (mi < 0) mi = 1
        chips(col, arrayOf("1 min", "5 min", "15 min", "1 hour"), mi) { putInt("rotate", mins[it]) }
        note(col, "Day wallpaper (6 AM to 6 PM)")
        chips(col, Names.walls, cfg.dayWall) { putInt("dayWall", it) }
        note(col, "Night wallpaper")
        chips(col, Names.walls, cfg.nightWall) { putInt("nightWall", it) }

        val reset = Button(this)
        reset.text = "Reset everything to default"
        reset.setOnClickListener {
            prefs.edit().clear().apply()
            recreate()
        }
        val rlp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        rlp.setMargins(0, dp(24), 0, 0)
        col.addView(reset, rlp)

        setContentView(root)
    }

    private fun openWallpaperPreview() {
        try {
            val intent = Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER)
            intent.putExtra(
                WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,
                ComponentName(this, WebWallpaperService::class.java)
            )
            startActivity(intent)
        } catch (e: Exception) {
            startActivity(Intent(WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER))
        }
    }
}
