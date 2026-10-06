package com.arodhan.wallpaper

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color

class Theme(
    val name: String,
    val bg: IntArray,            // 3 background colours (top, middle, bottom)
    val acc: Array<IntArray>,    // 5 accent colours
    val light: Array<IntArray>,  // 3 colours for lattice lights (60% / 20% / 20%)
    val cloud: Array<IntArray>   // 6 nebula colours
)

object Themes {
    val all: Array<Theme> = arrayOf(
        Theme(
            "Neon",
            intArrayOf(Color.rgb(4, 10, 56), Color.rgb(29, 10, 92), Color.rgb(90, 16, 104)),
            arrayOf(intArrayOf(0, 242, 255), intArrayOf(255, 150, 50), intArrayOf(255, 0, 190), intArrayOf(110, 255, 150), intArrayOf(255, 230, 90)),
            arrayOf(intArrayOf(255, 40, 40), intArrayOf(60, 130, 255), intArrayOf(255, 255, 255)),
            arrayOf(intArrayOf(255, 0, 170), intArrayOf(255, 120, 30), intArrayOf(30, 130, 255), intArrayOf(140, 60, 255), intArrayOf(20, 90, 235), intArrayOf(255, 70, 140))
        ),
        Theme(
            "Inferno",
            intArrayOf(Color.rgb(26, 4, 6), Color.rgb(78, 10, 12), Color.rgb(150, 36, 10)),
            arrayOf(intArrayOf(255, 70, 30), intArrayOf(255, 160, 40), intArrayOf(255, 225, 90), intArrayOf(255, 90, 120), intArrayOf(255, 255, 210)),
            arrayOf(intArrayOf(255, 60, 30), intArrayOf(255, 170, 40), intArrayOf(255, 255, 255)),
            arrayOf(intArrayOf(255, 40, 20), intArrayOf(255, 130, 20), intArrayOf(200, 20, 60), intArrayOf(255, 190, 40), intArrayOf(160, 20, 20), intArrayOf(255, 90, 60))
        ),
        Theme(
            "Aurora",
            intArrayOf(Color.rgb(2, 16, 30), Color.rgb(6, 52, 62), Color.rgb(34, 22, 96)),
            arrayOf(intArrayOf(60, 255, 170), intArrayOf(0, 220, 230), intArrayOf(160, 90, 255), intArrayOf(255, 100, 200), intArrayOf(200, 255, 120)),
            arrayOf(intArrayOf(60, 255, 170), intArrayOf(90, 160, 255), intArrayOf(255, 255, 255)),
            arrayOf(intArrayOf(0, 220, 160), intArrayOf(0, 180, 230), intArrayOf(130, 70, 255), intArrayOf(40, 255, 190), intArrayOf(20, 120, 200), intArrayOf(220, 80, 220))
        ),
        Theme(
            "Ocean",
            intArrayOf(Color.rgb(2, 8, 30), Color.rgb(4, 40, 92), Color.rgb(10, 90, 132)),
            arrayOf(intArrayOf(0, 200, 255), intArrayOf(60, 120, 255), intArrayOf(0, 255, 200), intArrayOf(160, 220, 255), intArrayOf(255, 255, 255)),
            arrayOf(intArrayOf(60, 160, 255), intArrayOf(0, 255, 220), intArrayOf(255, 255, 255)),
            arrayOf(intArrayOf(0, 160, 255), intArrayOf(0, 220, 200), intArrayOf(40, 90, 255), intArrayOf(100, 160, 255), intArrayOf(10, 70, 200), intArrayOf(0, 200, 160))
        ),
        Theme(
            "Sunset",
            intArrayOf(Color.rgb(30, 10, 62), Color.rgb(120, 20, 92), Color.rgb(230, 90, 70)),
            arrayOf(intArrayOf(255, 120, 80), intArrayOf(255, 60, 160), intArrayOf(255, 200, 90), intArrayOf(140, 90, 255), intArrayOf(255, 240, 200)),
            arrayOf(intArrayOf(255, 90, 100), intArrayOf(150, 90, 255), intArrayOf(255, 255, 255)),
            arrayOf(intArrayOf(255, 70, 120), intArrayOf(255, 130, 60), intArrayOf(150, 70, 255), intArrayOf(255, 190, 80), intArrayOf(200, 40, 140), intArrayOf(255, 100, 90))
        )
    )
}

object Names {
    val walls = arrayOf(
        "Lattice", "Tesseract", "Deep Ocean", "Nebula Space", "Neon Horizon",
        "Galaxy Spiral", "Matrix Rain", "Aurora", "Photo Live"
    )
    val clocks = arrayOf("Stack", "Words", "Ring", "Minimal", "Off")
}

class Config(
    val wall: Int, val clock: Int, val theme: Int, val speed: Float, val energy: Int, val fps: Int,
    val text: String, val banner: Boolean, val mode: Int, val rotateMin: Int,
    val dayWall: Int, val nightWall: Int, val autoSaver: Boolean, val parallax: Boolean, val dim: Int,
    val touch: Boolean, val haptic: Boolean
) {
    companion object {
        const val NAME = "arodhan_cfg"

        fun prefs(ctx: Context): SharedPreferences = ctx.getSharedPreferences(NAME, Context.MODE_PRIVATE)

        fun load(ctx: Context): Config {
            val s = prefs(ctx)
            return Config(
                wall = s.getInt("wall", 0),
                clock = s.getInt("clock", 0),
                theme = s.getInt("theme", 0),
                speed = s.getInt("speed", 100) / 100f,
                energy = s.getInt("energy", 2),
                fps = s.getInt("fps", 30),
                text = s.getString("text", "ARODHAN") ?: "ARODHAN",
                banner = s.getBoolean("banner", true),
                mode = s.getInt("mode", 0),
                rotateMin = s.getInt("rotate", 5),
                dayWall = s.getInt("dayWall", 0),
                nightWall = s.getInt("nightWall", 3),
                autoSaver = s.getBoolean("saver", true),
                parallax = s.getBoolean("parallax", true),
                dim = s.getInt("dim", 0),
                touch = s.getBoolean("touch", true),
                haptic = s.getBoolean("haptic", true)
            )
        }
    }
}
