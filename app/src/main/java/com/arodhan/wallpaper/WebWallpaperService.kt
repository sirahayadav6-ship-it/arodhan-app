package com.arodhan.wallpaper

import android.graphics.Canvas
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.service.wallpaper.WallpaperService
import android.view.SurfaceHolder
import android.view.View
import android.view.View.MeasureSpec
import android.webkit.WebView
import android.webkit.WebViewClient

/**
 * Live wallpaper that renders assets/index.html inside a WebView
 * and copies the WebView onto the wallpaper surface ~30 times a second.
 */
class WebWallpaperService : WallpaperService() {

    override fun onCreateEngine(): Engine = WebEngine()

    inner class WebEngine : Engine() {
        private val handler = Handler(Looper.getMainLooper())
        private var webView: WebView? = null
        private var visible = false

        private val frameLoop = object : Runnable {
            override fun run() {
                drawFrame()
                if (visible) handler.postDelayed(this, FRAME_MS)
            }
        }

        override fun onCreate(surfaceHolder: SurfaceHolder) {
            super.onCreate(surfaceHolder)
            webView = WebView(this@WebWallpaperService).apply {
                setBackgroundColor(Color.BLACK)
                isVerticalScrollBarEnabled = false
                isHorizontalScrollBarEnabled = false
                overScrollMode = View.OVER_SCROLL_NEVER
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.allowFileAccess = true
                settings.mediaPlaybackRequiresUserGesture = false
                webViewClient = WebViewClient()
                loadUrl("file:///android_asset/index.html")
            }
        }

        override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            super.onSurfaceChanged(holder, format, width, height)
            webView?.apply {
                measure(
                    MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY)
                )
                layout(0, 0, width, height)
                dispatchWindowVisibilityChanged(View.VISIBLE)
                onWindowFocusChanged(true)
            }
            drawFrame()
        }

        override fun onVisibilityChanged(isVisible: Boolean) {
            visible = isVisible
            val wv = webView ?: return
            if (isVisible) {
                wv.onResume()
                wv.resumeTimers()
                handler.removeCallbacks(frameLoop)
                handler.post(frameLoop)
            } else {
                handler.removeCallbacks(frameLoop)
                wv.onPause()
            }
        }

        override fun onSurfaceDestroyed(holder: SurfaceHolder) {
            visible = false
            handler.removeCallbacks(frameLoop)
            super.onSurfaceDestroyed(holder)
        }

        override fun onDestroy() {
            visible = false
            handler.removeCallbacks(frameLoop)
            webView?.destroy()
            webView = null
            super.onDestroy()
        }

        private fun drawFrame() {
            val wv = webView ?: return
            val holder = surfaceHolder ?: return
            var canvas: Canvas? = null
            try {
                canvas = try {
                    holder.lockHardwareCanvas()
                } catch (e: Exception) {
                    holder.lockCanvas()
                }
                canvas?.let { wv.draw(it) }
            } catch (e: Exception) {
                // surface not ready yet, skip this frame
            } finally {
                if (canvas != null) {
                    try { holder.unlockCanvasAndPost(canvas) } catch (e: Exception) { }
                }
            }
        }
    }

    companion object {
        private const val FRAME_MS = 33L
    }
}
