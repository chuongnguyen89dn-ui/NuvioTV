package com.nuvio.tv.core.player

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.util.Base64
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.atomic.AtomicReference

internal class Av01PlaybackResolver(private val context: Context) {
    suspend fun resolve(pseudoUrl: String): String = withTimeout(25_000L) {
        val id = pseudoUrl.removePrefix("av01:").trim().toLongOrNull()
            ?: error("Invalid AV01 id: $pseudoUrl")
        val pageUrl = "https://www.av01.media/en/video/$id/"
        val captured = captureManifestUrl(pageUrl)
        val manifest = fetchAndRewriteManifest(captured, pageUrl)
        "data:application/vnd.apple.mpegurl;base64," +
            Base64.encodeToString(manifest.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
    }

    @SuppressLint("SetJavaScriptEnabled")
    private suspend fun captureManifestUrl(pageUrl: String): String =
        withContext(Dispatchers.Main.immediate) {
            val result = AtomicReference<String?>(null)
            val webView = WebView(context.applicationContext)
            try {
                webView.settings.javaScriptEnabled = true
                webView.settings.domStorageEnabled = true
                webView.settings.mediaPlaybackRequiresUserGesture = false
                webView.settings.userAgentString =
                    "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 " +
                        "(KHTML, like Gecko) Chrome/151 Mobile Safari/537.36"

                webView.webViewClient = object : WebViewClient() {
                    override fun shouldInterceptRequest(
                        view: WebView,
                        request: WebResourceRequest
                    ): android.webkit.WebResourceResponse? {
                        val url = request.url.toString()
                        if (url.contains("sv3-v1-a1.m3u8", true) &&
                            url.contains("access_token=", true)) {
                            result.compareAndSet(null, url)
                        }
                        return null
                    }
                }
                webView.loadUrl(pageUrl)
                repeat(80) {
                    if (result.get() != null) return@withContext result.get()!!
                    if (it == 10 || it == 20 || it == 35) {
                        webView.evaluateJavascript(
                            "(function(){try{" +
                                "var v=document.querySelector('video');" +
                                "if(v){v.muted=true;v.play().catch(function(){});}" +
                                "var b=document.querySelector('[aria-label*=\"play\" i],[class*=\"play\" i],[id*=\"play\" i]');" +
                                "if(b){b.click();}" +
                                "}catch(e){}})();",
                            null
                        )
                    }
                    kotlinx.coroutines.delay(250)
                }
                error("AV01 did not expose a signed sv3 manifest")
            } finally {
                webView.stopLoading()
                webView.webViewClient = null
                webView.destroy()
            }
        }

    private suspend fun fetchAndRewriteManifest(manifestUrl: String, referer: String): String =
        withContext(Dispatchers.IO) {
            val connection = (URL(manifestUrl).openConnection() as HttpURLConnection).apply {
                connectTimeout = 10_000
                readTimeout = 15_000
                requestMethod = "GET"
                setRequestProperty("Referer", referer)
                setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/151 Mobile Safari/537.36")
                setRequestProperty("Accept", "application/vnd.apple.mpegurl,application/x-mpegURL,*/*")
            }
            try {
                if (connection.responseCode !in 200..299) {
                    error("AV01 manifest HTTP \${connection.responseCode}")
                }
                val text = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                rewriteManifest(text, manifestUrl)
            } finally {
                connection.disconnect()
            }
        }

    private fun rewriteManifest(text: String, baseUrl: String): String {
        val base = Uri.parse(baseUrl)
        val token = base.getQueryParameter("access_token")
            ?: error("AV01 manifest has no access_token")
        val ro = base.getQueryParameter("ro")

        fun signed(raw: String): String {
            val absolute = if (raw.startsWith("http://") || raw.startsWith("https://")) {
                Uri.parse(raw)
            } else {
                val rawPath = raw.substringBefore('?')
                val parent = base.path.orEmpty().substringBeforeLast('/')
                base.buildUpon().path(parent + "/" + rawPath.trimStart('/'))
                    .encodedQuery(raw.substringAfter('?', "")).build()
            }
            if (!absolute.host.orEmpty().endsWith("iw01.xyz")) return absolute.toString()
            val builder = absolute.buildUpon().clearQuery()
                .appendQueryParameter("access_token", token)
            if (!ro.isNullOrBlank()) builder.appendQueryParameter("ro", ro)
            return builder.build().toString()
        }

        return text.lineSequence().joinToString("\n") { original ->
            var line = original.replace(Regex("""URI="([^"]+)"""")) { m ->
                "URI=\"" + signed(m.groupValues[1]) + "\""
            }
            if (line.isNotBlank() && !line.trimStart().startsWith("#")) {
                line = signed(line.trim())
            }
            line
        } + "\n"
    }
}
