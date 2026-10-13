package com.animekompi.app

import android.annotation.SuppressLint
import android.app.DownloadManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.webkit.*
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.navigation.NavigationBarView
import java.io.ByteArrayInputStream

class MainActivity : AppCompatActivity() {
    companion object {
        const val BASE = "https://v9.animekompi.sbs"
        const val SITE_HOST = "animekompi.sbs"
    }

    // Ubah path tab di sini jika struktur URL situs berbeda
    private val tabUrls = mapOf(
        R.id.nav_home to "$BASE/",
        R.id.nav_list to "$BASE/anime/",
        R.id.nav_schedule to "$BASE/schedule/"
    )

    private lateinit var web: WebView
    private lateinit var rv: RecyclerView
    private lateinit var empty: TextView
    private lateinit var progress: ProgressBar
    private lateinit var etSearch: EditText
    private lateinit var nav: NavigationBarView
    private lateinit var dm: DownloadManager
    private lateinit var adapter: DownloadAdapter
    private val handler = Handler(Looper.getMainLooper())
    private var customView: View? = null
    private var customCb: WebChromeClient.CustomViewCallback? = null

    private val poll = object : Runnable {
        override fun run() { refreshDownloads(); handler.postDelayed(this, 1000) }
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        dm = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        web = findViewById(R.id.web); rv = findViewById(R.id.rvDownloads)
        empty = findViewById(R.id.emptyText); progress = findViewById(R.id.progress)
        etSearch = findViewById(R.id.etSearch); nav = findViewById(R.id.nav)

        val tablet = resources.configuration.smallestScreenWidthDp >= 600
        rv.layoutManager = GridLayoutManager(this, if (tablet) 2 else 1)
        adapter = DownloadAdapter(::openFile, ::deleteDownload)
        rv.adapter = adapter

        web.settings.apply {
            javaScriptEnabled = true; domStorageEnabled = true
            mediaPlaybackRequiresUserGesture = false
            setSupportMultipleWindows(true)
            javaScriptCanOpenWindowsAutomatically = false
        }
        web.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(v: WebView, r: WebResourceRequest): WebResourceResponse? =
                if (AdBlocker.isAd(r.url.toString()))
                    WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream(ByteArray(0)))
                else null

            override fun shouldOverrideUrlLoading(v: WebView, r: WebResourceRequest): Boolean {
                val host = r.url.host ?: return true
                if (host.endsWith(SITE_HOST)) return false
                if (AdBlocker.isAd(r.url.toString())) return true
                if (r.hasGesture()) { // link eksternal (mis. host download) dibuka di browser
                    try { startActivity(Intent(Intent.ACTION_VIEW, r.url)) } catch (_: Exception) {}
                }
                return true // redirect otomatis tanpa klik = iklan, diblokir
            }

            override fun onPageStarted(v: WebView, url: String, f: Bitmap?) { progress.visibility = View.VISIBLE }
            override fun onPageFinished(v: WebView, url: String) {
                progress.visibility = View.GONE
                v.evaluateJavascript(AdBlocker.injectJs, null)
                v.postDelayed({ v.evaluateJavascript(AdBlocker.injectJs, null) }, 1500)
            }
        }
        web.webChromeClient = object : WebChromeClient() {
            override fun onCreateWindow(v: WebView, d: Boolean, g: Boolean, m: android.os.Message?) = false // blok popup
            override fun onProgressChanged(v: WebView, p: Int) { progress.progress = p }
            override fun onShowCustomView(view: View, cb: CustomViewCallback) {
                customView = view; customCb = cb
                (window.decorView as FrameLayout).addView(view, ViewGroup.LayoutParams(-1, -1))
                requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                WindowCompat.getInsetsController(window, view).hide(WindowInsetsCompat.Type.systemBars())
            }
            override fun onHideCustomView() { exitFullscreen() }
        }
        web.setDownloadListener { url, ua, cd, mime, _ -> enqueue(url, ua, cd, mime) }

        etSearch.setOnEditorActionListener { v, id, _ ->
            if (id == EditorInfo.IME_ACTION_SEARCH) {
                web.loadUrl("$BASE/?s=" + Uri.encode(v.text.toString().trim()))
                (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(v.windowToken, 0)
                true
            } else false
        }

        nav.setOnItemSelectedListener { show(it.itemId); true }
        nav.setOnItemReselectedListener { tabUrls[it.itemId]?.let(web::loadUrl) }
        show(R.id.nav_home)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                when {
                    customView != null -> exitFullscreen()
                    web.visibility == View.VISIBLE && web.canGoBack() -> web.goBack()
                    nav.selectedItemId != R.id.nav_home -> nav.selectedItemId = R.id.nav_home
                    else -> finish()
                }
            }
        })
    }

    private fun show(id: Int) {
        val dl = id == R.id.nav_downloads
        web.visibility = if (dl) View.GONE else View.VISIBLE
        rv.visibility = if (dl) View.VISIBLE else View.GONE
        etSearch.visibility = if (id == R.id.nav_search) View.VISIBLE else View.GONE
        handler.removeCallbacks(poll)
        if (dl) poll.run() else empty.visibility = View.GONE
        when (id) {
            R.id.nav_search -> { if (web.url == null) web.loadUrl("$BASE/?s="); etSearch.requestFocus() }
            else -> tabUrls[id]?.let { if (web.url != it) web.loadUrl(it) }
        }
    }

    private fun exitFullscreen() {
        customView?.let { (window.decorView as FrameLayout).removeView(it) }
        customCb?.onCustomViewHidden(); customView = null; customCb = null
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        WindowCompat.getInsetsController(window, window.decorView).show(WindowInsetsCompat.Type.systemBars())
    }

    // ---- Download manager ----
    private fun enqueue(url: String, ua: String?, cd: String?, mime: String?) {
        val name = URLUtil.guessFileName(url, cd, mime)
        val req = DownloadManager.Request(Uri.parse(url))
            .setTitle(name)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalFilesDir(this, Environment.DIRECTORY_DOWNLOADS, name)
        mime?.let { req.setMimeType(it) }
        ua?.let { req.addRequestHeader("User-Agent", it) }
        CookieManager.getInstance().getCookie(url)?.let { req.addRequestHeader("Cookie", it) }
        req.addRequestHeader("Referer", BASE)
        dm.enqueue(req)
        Toast.makeText(this, "Mengunduh: $name", Toast.LENGTH_SHORT).show()
    }

    private fun refreshDownloads() {
        val list = mutableListOf<DlItem>()
        dm.query(DownloadManager.Query())?.use { c ->
            while (c.moveToNext()) {
                list += DlItem(
                    c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_ID)),
                    c.getString(c.getColumnIndexOrThrow(DownloadManager.COLUMN_TITLE)) ?: "File",
                    c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)),
                    c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)),
                    c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
                )
            }
        }
        adapter.items = list.reversed()
        empty.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun openFile(i: DlItem) {
        val uri = dm.getUriForDownloadedFile(i.id) ?: return
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, dm.getMimeTypeForDownloadedFile(i.id) ?: "video/*")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        try { startActivity(intent) } catch (_: ActivityNotFoundException) {
            Toast.makeText(this, "Tidak ada aplikasi untuk membuka file ini", Toast.LENGTH_SHORT).show()
        }
    }

    private fun deleteDownload(i: DlItem) { dm.remove(i.id); refreshDownloads() }

    override fun onResume() { super.onResume(); web.onResume(); if (nav.selectedItemId == R.id.nav_downloads) poll.run() }
    override fun onPause() { super.onPause(); web.onPause(); handler.removeCallbacks(poll) }
    override fun onDestroy() { web.destroy(); super.onDestroy() }
}
