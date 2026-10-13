package com.arenaph.live

import android.Manifest
import android.annotation.SuppressLint
import android.app.PictureInPictureParams
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.util.Rational
import android.webkit.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.webkit.WebViewAssetLoader

class MainActivity : AppCompatActivity() {
    lateinit var web: WebView
    private val reqCap = 42
    companion object { @Volatile var instance: MainActivity? = null }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        instance = this
        web = WebView(this)
        setContentView(web)
        val loader = WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(this)).build()
        web.settings.apply {
            javaScriptEnabled = true; domStorageEnabled = true
            mediaPlaybackRequiresUserGesture = false
        }
        web.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(v: WebView, r: WebResourceRequest) = loader.shouldInterceptRequest(r.url)
        }
        web.webChromeClient = object : WebChromeClient() {
            override fun onPermissionRequest(r: PermissionRequest) { runOnUiThread { r.grant(r.resources) } }
        }
        web.addJavascriptInterface(Bridge(), "ArenaNative")
        val perms = arrayListOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= 33) perms.add(Manifest.permission.POST_NOTIFICATIONS)
        ActivityCompat.requestPermissions(this, perms.toTypedArray(), 1)
        web.loadUrl("https://appassets.androidplatform.net/assets/index.html")
    }

    inner class Bridge {
        @JavascriptInterface fun available(): Boolean = true
        @JavascriptInterface fun startCapture() {
            runOnUiThread {
                val mpm = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                @Suppress("DEPRECATION")
                startActivityForResult(mpm.createScreenCaptureIntent(), reqCap)
            }
        }
        @JavascriptInterface fun stopCapture() { stopService(Intent(this@MainActivity, CaptureService::class.java)) }
        @JavascriptInterface fun minimize() { runOnUiThread { goPip() } }
    }

    private fun goPip() {
        try {
            if (Build.VERSION.SDK_INT >= 26)
                enterPictureInPictureMode(PictureInPictureParams.Builder().setAspectRatio(Rational(16, 9)).build())
            else moveTaskToBack(true)
        } catch (e: Exception) { moveTaskToBack(true) }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(rq: Int, rs: Int, d: Intent?) {
        super.onActivityResult(rq, rs, d)
        if (rq == reqCap) {
            if (rs == RESULT_OK && d != null) {
                val i = Intent(this, CaptureService::class.java).putExtra("code", rs).putExtra("data", d)
                if (Build.VERSION.SDK_INT >= 26) startForegroundService(i) else startService(i)
            } else web.evaluateJavascript("window.__nsDenied&&window.__nsDenied()", null)
        }
    }

    override fun onUserLeaveHint() { if (CaptureService.running) goPip() }
    override fun onPause() { super.onPause(); web.resumeTimers() }
    override fun onStop() { super.onStop(); web.resumeTimers() }
}
