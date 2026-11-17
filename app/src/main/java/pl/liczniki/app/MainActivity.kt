package pl.liczniki.app

import android.app.Activity
import android.os.Bundle
import android.graphics.Color
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient

class MainActivity : Activity() {
    private val prefs by lazy { getSharedPreferences("liczniki_saved_data", MODE_PRIVATE) }

    inner class StorageBridge {
        @JavascriptInterface
        fun load(): String = prefs.getString("state", "") ?: ""

        @JavascriptInterface
        fun save(json: String) {
            // commit() completes before returning, so even an immediate app close
            // cannot interrupt the write of the current counter values.
            prefs.edit().putString("state", json).commit()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(0, 27, 66)
        window.navigationBarColor = Color.rgb(0, 27, 66)
        val web = WebView(this)
        web.settings.javaScriptEnabled = true
        web.settings.domStorageEnabled = true
        web.addJavascriptInterface(StorageBridge(), "AndroidStorage")
        web.webViewClient = WebViewClient()
        web.webChromeClient = WebChromeClient()
        web.setBackgroundColor(Color.rgb(0, 32, 78))
        setContentView(web)
        web.loadUrl("file:///android_asset/index.html")
    }
}
