package com.nuvio.tv.core.player

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.util.concurrent.atomic.AtomicReference

internal class Av01PlaybackResolver(private val context: Context) {
    suspend fun resolve(streamUrl: String): String = withTimeout(30_000L) {
        val id = streamUrl.removePrefix("av01:").trim().toLongOrNull()
            ?: error("Invalid AV01 stream: " + streamUrl)
        captureSignedSv3Manifest(id)
    }

    @SuppressLint("SetJavaScriptEnabled")
    private suspend fun captureSignedSv3Manifest(id: Long): String =
        withContext(Dispatchers.Main.immediate) {
            val captured = AtomicReference<String?>(null)
            val pageUrl = "https://www.av01.media/en/video/" + id + "/"
            val webView = WebView(context.applicationContext)
            try {
                CookieManager.getInstance().setAcceptCookie(true)
                @Suppress("DEPRECATION")
                CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)
                webView.settings.javaScriptEnabled = true
                webView.settings.domStorageEnabled = true
                webView.settings.mediaPlaybackRequiresUserGesture = false
                webView.settings.loadsImagesAutomatically = false
                webView.settings.userAgentString =
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
                        "AppleWebKit/537.36 (KHTML, like Gecko) " +
                        "Chrome/151.0.0.0 Safari/537.36"

                webView.webViewClient = object : WebViewClient() {
                    override fun shouldInterceptRequest(
                        view: WebView,
                        request: WebResourceRequest
                    ): android.webkit.WebResourceResponse? {
                        val url = request.url.toString()
                        if (url.contains("sv3-v1-a1.m3u8", true) &&
                            url.contains("access_token=", true)) {
                            if (captured.compareAndSet(null, url)) Log.i("Av01Resolver", "captured sv3 for id=" + id + " host=" + request.url.host)
                        }
                        return null
                    }

                    @Suppress("DEPRECATION")
                    override fun shouldInterceptRequest(
                        view: WebView,
                        url: String
                    ): android.webkit.WebResourceResponse? {
                        if (url.contains("sv3-v1-a1.m3u8", true) &&
                            url.contains("access_token=", true)) {
                            captured.compareAndSet(null, url)
                        }
                        return null
                    }
                }

                webView.loadUrl(pageUrl)

                repeat(100) { tick ->
                    captured.get()?.let { return@withContext it }

                    if (tick == 8 || tick == 20 || tick == 35 || tick == 50) {
                        webView.evaluateJavascript(
                            """
                            (function() {
                              try {
                                var v = document.querySelector('video');
                                if (v) {
                                  v.muted = true;
                                  var p = v.play();
                                  if (p && p.catch) p.catch(function(){});
                                }
                                var b = document.querySelector(
                                  '[aria-label*="play" i],' +
                                  '[title*="play" i],' +
                                  '[class*="play" i],' +
                                  '[id*="play" i]'
                                );
                                if (b) b.click();
                              } catch (e) {}
                            })();
                            """.trimIndent(),
                            null
                        )
                    }
                    delay(250)
                }
                error("AV01 signed sv3 manifest was not captured")
            } finally {
                webView.stopLoading()
                webView.webViewClient = null
                webView.destroy()
            }
        }
}
