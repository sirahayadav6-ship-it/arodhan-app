package com.arodhan.wallpaper

import android.app.Activity
import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.parseColor("#050510"))
            setPadding(64, 64, 64, 64)
        }

        val title = TextView(this).apply {
            text = "ARODHAN"
            textSize = 40f
            typeface = Typeface.DEFAULT_BOLD
            letterSpacing = 0.15f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
        }

        val sub = TextView(this).apply {
            text = "Live wallpaper"
            textSize = 16f
            setTextColor(Color.parseColor("#8e98bd"))
            gravity = Gravity.CENTER
            setPadding(0, 16, 0, 64)
        }

        val button = Button(this).apply {
            text = "Set as wallpaper"
            setOnClickListener { openWallpaperPreview() }
        }

        root.addView(title)
        root.addView(sub)
        root.addView(button)
        setContentView(root)
    }

    private fun openWallpaperPreview() {
        try {
            val intent = Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER).apply {
                putExtra(
                    WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,
                    ComponentName(this@MainActivity, WebWallpaperService::class.java)
                )
            }
            startActivity(intent)
        } catch (e: Exception) {
            startActivity(Intent(WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER))
        }
    }
}
