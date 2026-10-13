package com.arenaph.live

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.*
import android.util.Base64
import java.io.ByteArrayOutputStream
import java.util.concurrent.atomic.AtomicBoolean

/** Captures the whole phone screen (MediaProjection) and streams JPEG frames into the web app. */
class CaptureService : Service() {
    companion object { @Volatile var running = false }
    private var mp: MediaProjection? = null
    private var vd: VirtualDisplay? = null
    private var ir: ImageReader? = null
    private var ht: HandlerThread? = null
    private var handler: Handler? = null
    private val busy = AtomicBoolean(false)
    private var last = 0L
    private var cw = 0; private var chh = 0

    override fun onBind(i: Intent?): IBinder? = null

    override fun onStartCommand(i: Intent?, f: Int, id: Int): Int {
        if (i == null) return START_NOT_STICKY
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(NotificationChannel("cap", "Live capture", NotificationManager.IMPORTANCE_LOW))
        val n = Notification.Builder(this, "cap").setContentTitle("ArenaPH Live")
            .setContentText("Your screen is being shared").setSmallIcon(android.R.drawable.presence_video_online).build()
        if (Build.VERSION.SDK_INT >= 29) startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        else startForeground(1, n)
        val code = i.getIntExtra("code", 0)
        @Suppress("DEPRECATION") val data = i.getParcelableExtra<Intent>("data") ?: return START_NOT_STICKY
        val mpm = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        mp = mpm.getMediaProjection(code, data)
        ht = HandlerThread("cap").also { it.start() }
        handler = Handler(ht!!.looper)
        mp!!.registerCallback(object : MediaProjection.Callback() { override fun onStop() { stopSelf() } }, handler)
        running = true
        val (w, h) = target(); setup(w, h)
        handler!!.postDelayed(object : Runnable {
            override fun run() {
                if (!running) return
                val (nw, nh) = target()
                if (nw != cw || nh != chh) setup(nw, nh)   // follow rotation (portrait <-> landscape)
                handler?.postDelayed(this, 1000)
            }
        }, 1000)
        return START_NOT_STICKY
    }

    private fun target(): Pair<Int, Int> {
        val dm = Resources.getSystem().displayMetrics
        val s = 960f / maxOf(dm.widthPixels, dm.heightPixels)
        return Pair(((dm.widthPixels * s).toInt()) and 1.inv(), ((dm.heightPixels * s).toInt()) and 1.inv())
    }

    private fun setup(w: Int, h: Int) {
        cw = w; chh = h
        val old = ir
        ir = ImageReader.newInstance(w, h, PixelFormat.RGBA_8888, 2)
        ir!!.setOnImageAvailableListener({ onFrame(it) }, handler)
        if (vd == null) vd = mp!!.createVirtualDisplay("arena", w, h, 160, DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, ir!!.surface, null, handler)
        else { vd!!.resize(w, h, 160); vd!!.surface = ir!!.surface }
        old?.close()
    }

    private fun onFrame(r: ImageReader) {
        val img = try { r.acquireLatestImage() } catch (e: Exception) { null } ?: return
        try {
            val now = System.currentTimeMillis()
            if (now - last < 90 || busy.get()) return
            last = now
            val p = img.planes[0]
            val bmp = Bitmap.createBitmap(p.rowStride / p.pixelStride, img.height, Bitmap.Config.ARGB_8888)
            bmp.copyPixelsFromBuffer(p.buffer)
            val crop = Bitmap.createBitmap(bmp, 0, 0, img.width, img.height)
            val out = ByteArrayOutputStream()
            crop.compress(Bitmap.CompressFormat.JPEG, 55, out)
            bmp.recycle(); if (crop !== bmp) crop.recycle()
            val b64 = Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
            val a = MainActivity.instance ?: return
            busy.set(true)
            a.runOnUiThread { a.web.evaluateJavascript("window.__nsFrame&&window.__nsFrame('$b64')") { busy.set(false) } }
        } catch (e: Exception) { busy.set(false) } finally { img.close() }
    }

    override fun onDestroy() {
        running = false
        try { vd?.release() } catch (e: Exception) {}
        try { ir?.close() } catch (e: Exception) {}
        try { mp?.stop() } catch (e: Exception) {}
        ht?.quitSafely()
        super.onDestroy()
    }
}
