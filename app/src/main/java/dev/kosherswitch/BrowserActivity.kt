package dev.kosherswitch

import android.annotation.SuppressLint
import android.app.Activity
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.util.Base64
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.webkit.JavascriptInterface
import android.webkit.ServiceWorkerClient
import android.webkit.ServiceWorkerController
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ProgressBar
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import java.io.ByteArrayInputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

/**
 * The Kosher Browser: every picture is hidden until it's checked on the phone, and any picture with a
 * person in it stays covered (like kosher computer filters). SafeSearch is always on, videos don't play,
 * and the phone's family filter still applies underneath.
 */
class BrowserActivity : Activity() {
    private lateinit var web: WebView
    private lateinit var address: EditText
    private lateinit var progress: ProgressBar
    private val checker = Executors.newFixedThreadPool(2)
    /** Web pictures that passed the check; the page may only show these (or checked inline pictures). */
    private val approved: MutableSet<String> = java.util.Collections.newSetFromMap(java.util.concurrent.ConcurrentHashMap())

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Theme.load(this)
        Theme.blueBars(this)
        checker.execute { PictureCheck.prepare(this) }

        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(Theme.NAVY)
            val p = Theme.dp(context, 8)
            setPadding(p, p, p, p)
        }
        fun icon(glyph: String, click: () -> Unit) = android.widget.TextView(this).apply {
            text = glyph
            textSize = 24f
            setTextColor(0xFFFFFFFF.toInt())
            gravity = Gravity.CENTER
            val p = Theme.dp(context, 10)
            setPadding(p, 0, p, 0)
            setOnClickListener { Theme.haptic(it); click() }
        }
        address = EditText(this).apply {
            hint = getString(R.string.browser_hint)
            setSingleLine()
            imeOptions = EditorInfo.IME_ACTION_GO
            setTextColor(0xFFFFFFFF.toInt())
            setHintTextColor(0x99FFFFFF.toInt())
            textSize = 15f
            background = Theme.rounded(context, 0x33FFFFFF, 22)
            val p = Theme.dp(context, 14)
            setPadding(p, Theme.dp(context, 9), p, Theme.dp(context, 9))
            setSelectAllOnFocus(true)
            setOnEditorActionListener { _, id, e ->
                if (id == EditorInfo.IME_ACTION_GO || e?.keyCode == KeyEvent.KEYCODE_ENTER) { go(text.toString()); true } else false
            }
        }
        bar.addView(icon("‹") { if (web.canGoBack()) web.goBack() else finish() })
        bar.addView(address, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
            marginStart = Theme.dp(this@BrowserActivity, 4); marginEnd = Theme.dp(this@BrowserActivity, 4)
        })
        bar.addView(icon("↻") { web.reload() })
        progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            progressTintList = android.content.res.ColorStateList.valueOf(Theme.GOLD)
        }

        web = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            settings.mediaPlaybackRequiresUserGesture = true
            settings.setSupportMultipleWindows(false)
            settings.safeBrowsingEnabled = true
            addJavascriptInterface(Pictures(), "KosherPictures")
            webViewClient = Client()
            // Before any of the page's own code runs, in every frame: hide pictures until they're checked.
            if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT))
                WebViewCompat.addDocumentStartJavaScript(this, COVER_SCRIPT, setOf("*"))
            webChromeClient = object : android.webkit.WebChromeClient() {
                override fun onProgressChanged(view: WebView, p: Int) {
                    this@BrowserActivity.progress.progress = p
                    this@BrowserActivity.progress.visibility = if (p >= 100) View.INVISIBLE else View.VISIBLE
                }
            }
        }
        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(bar)
            addView(progress, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, Theme.dp(this@BrowserActivity, 3)))
            addView(web, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        })
        // Pages with a service worker fetch their pictures through it, so it gets the same check.
        ServiceWorkerController.getInstance().setServiceWorkerClient(object : ServiceWorkerClient() {
            override fun shouldInterceptRequest(request: WebResourceRequest) = intercept(request)
        })
        web.loadUrl(HOME)
    }

    override fun onBackPressed() {
        if (web.canGoBack()) web.goBack() else super.onBackPressed()
    }

    override fun onDestroy() {
        web.destroy()
        checker.shutdownNow()
        super.onDestroy()
    }

    /** A web address, or words to search for (always with SafeSearch). */
    private fun go(input: String) {
        val t = input.trim()
        if (t.isEmpty()) return
        val url = if (t.contains(' ') || !t.contains('.')) "https://www.google.com/search?q=${Uri.encode(t)}&safe=active"
            else if (t.startsWith("http")) t else "https://$t"
        web.loadUrl(safeSearch(url))
        address.clearFocus()
        getSystemService(android.view.inputmethod.InputMethodManager::class.java).hideSoftInputFromWindow(address.windowToken, 0)
        web.requestFocus()
    }

    private inner class Client : WebViewClient() {
        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
            val url = request.url.toString()
            if (!url.startsWith("http")) return true            // no opening other apps
            val safe = safeSearch(url)
            if (safe != url) { view.loadUrl(safe); return true }
            return false
        }

        override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
            address.setText(Uri.parse(url).host ?: url)
            view.evaluateJavascript(COVER_SCRIPT, null)
        }

        override fun onPageCommitVisible(view: WebView, url: String) { view.evaluateJavascript(COVER_SCRIPT, null) }
        override fun onPageFinished(view: WebView, url: String) { view.evaluateJavascript(COVER_SCRIPT, null) }

        override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest) = intercept(request)
    }

    /** Pictures are fetched and checked here before the page can show them; videos never load. */
    private fun intercept(request: WebResourceRequest): WebResourceResponse? {
        if (request.isForMainFrame) return null
        val url = request.url.toString()
        val accept = request.requestHeaders["Accept"].orEmpty()
        val path = request.url.path.orEmpty().lowercase()
        if (accept.startsWith("video") || accept.startsWith("audio") || MEDIA.any { path.endsWith(it) }) return blank()
        val isImage = accept.startsWith("image") || PICTURES.any { path.endsWith(it) }
        if (!isImage || request.method != "GET") return null
        if (path.endsWith(".svg")) return null                // drawings and icons
        return runCatching {
            val (bytes, type) = download(url, request.requestHeaders)
            if (!PictureCheck.safe(bytes)) blank()
            else { approved += url; WebResourceResponse(type, null, ByteArrayInputStream(bytes)) }
        }.getOrElse { blank() }
    }

    private fun download(url: String, headers: Map<String, String>): Pair<ByteArray, String> {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 8000; readTimeout = 12000; instanceFollowRedirects = true
            headers.forEach { (k, v) -> if (k != "Accept-Encoding") setRequestProperty(k, v) }
            if (headers.keys.none { it.equals("User-Agent", true) }) setRequestProperty("User-Agent", web.settings.userAgentString)
        }
        val bytes = conn.inputStream.use { it.readBytes() }
        require(bytes.size <= 12_000_000)
        return bytes to (conn.contentType?.substringBefore(';') ?: "image/*")
    }

    /** The page asks here before showing any picture. */
    private inner class Pictures {
        /** Pictures written straight into the page (like Google's thumbnails). */
        @JavascriptInterface
        fun check(id: String, dataUrl: String) = later(id) {
            val b64 = dataUrl.substringAfter("base64,", "")
            b64.isNotEmpty() && PictureCheck.safe(Base64.decode(b64, Base64.DEFAULT))
        }

        /** Whether this web picture already passed on its way in. */
        @JavascriptInterface
        fun passed(url: String) = url in approved

        /** A web picture that reached the page some other way (cache, service worker): check it now. */
        @JavascriptInterface
        fun checkUrl(id: String, url: String) = later(id) {
            url.startsWith("http") && PictureCheck.safe(download(url, emptyMap()).first).also { if (it) approved += url }
        }

        private fun later(id: String, test: () -> Boolean) {
            if (!id.matches(Regex("k\\d+"))) return
            checker.execute {
                val ok = runCatching(test).getOrDefault(false)
                runOnUiThread { if (!isDestroyed) web.evaluateJavascript("window.__ksDone&&__ksDone('$id',$ok)", null) }
            }
        }
    }

    private fun blank() = WebResourceResponse("image/gif", null, ByteArrayInputStream(TRANSPARENT_GIF))

    companion object {
        const val HOME = "https://www.google.com/?safe=active"
        private val PICTURES = listOf(".jpg", ".jpeg", ".png", ".webp", ".gif", ".avif", ".bmp", ".heic")
        private val MEDIA = listOf(".mp4", ".webm", ".m3u8", ".mov", ".mp3", ".m4a", ".ogg", ".ts", ".mpd")
        private val TRANSPARENT_GIF = Base64.decode("R0lGODlhAQABAIAAAAAAAP///yH5BAEAAAAALAAAAAABAAEAAAIBRAA7", Base64.DEFAULT)

        /** SafeSearch on every search engine. */
        fun safeSearch(url: String): String {
            val u = Uri.parse(url)
            val host = u.host.orEmpty()
            fun with(key: String, value: String) =
                if (u.getQueryParameter(key) == value) url else u.buildUpon().clearQuery().apply {
                    u.queryParameterNames.filter { it != key }.forEach { n -> u.getQueryParameters(n).forEach { v -> appendQueryParameter(n, v) } }
                    appendQueryParameter(key, value)
                }.build().toString()
            return when {
                host.contains("google.") && (u.path?.startsWith("/search") == true || u.path == "/") -> with("safe", "active")
                host.contains("bing.com") -> with("adlt", "strict")
                host.contains("duckduckgo.com") -> with("kp", "1")
                host.contains("yahoo.com") && u.path?.contains("search") == true -> with("vm", "r")
                else -> url
            }
        }

        /**
         * Hides every picture until it's been checked, covers the ones with people, and removes
         * background pictures and videos. Runs again on every page change (safe to repeat).
         */
        private val COVER_SCRIPT = """
            (function(){
              if (window.__ksObs) return;
              // Covered pictures show the Kosher Switch seal on navy instead of a blank box; pictures still
              // being checked show a faint seal. The picture itself is pushed out of its own frame.
              function seal(label, a){
                var s = '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 160 170"><g opacity="' + a + '">' +
                  '<circle cx="80" cy="70" r="54" fill="#0b2a5b" stroke="#e0b64a" stroke-width="6"/>' +
                  '<circle cx="80" cy="70" r="42" fill="none" stroke="#e0b64a" stroke-width="1.5" stroke-dasharray="2 4"/>' +
                  '<text x="80" y="82" text-anchor="middle" font-family="serif" font-weight="700" font-size="34" fill="#f3d27e">כשר</text></g>' +
                  (label ? '<text x="80" y="158" text-anchor="middle" font-family="sans-serif" font-weight="600" font-size="15" fill="#f3d27e">' + label + '</text>' : '') +
                  '</svg>';
                return 'url("data:image/svg+xml;charset=utf-8,' + encodeURIComponent(s) + '")';
              }
              var tile = 'visibility:visible!important;opacity:1!important;color:transparent!important;font-size:0!important;object-position:-9999px -9999px!important;border-radius:10px!important;' +
                'background-color:#0e2350!important;background-repeat:no-repeat!important;background-position:center!important;background-size:min(70%,150px) auto!important;';
              var css = 'input[type=image],svg image{visibility:hidden!important}' +
                'img:not([data-ks=ok]):not([data-ks=no]){' + tile + 'background-image:' + seal('', 0.25) + '!important}' +
                'img[data-ks=no]{' + tile + 'background-image:' + seal(/^(he|iw)/i.test(navigator.language || '') ? '\u05ea\u05de\u05d5\u05e0\u05d4 \u05de\u05d5\u05e1\u05ea\u05e8\u05ea' : 'Picture covered', 1) + '!important}' +
                '*,*::before,*::after{background-image:none!important}' +
                'video,audio,embed,object,iframe[src*="youtube"],iframe[src*="vimeo"],iframe[src*="tiktok"]{display:none!important}';
              var style;
              function ensureStyle(){
                if (style && style.isConnected) return;
                var root = document.head || document.documentElement; if (!root) return;
                style = document.createElement('style'); style.id = 'ks-style'; style.textContent = css; root.appendChild(style);
              }
              var n = 0, pending = {};
              window.__ksDone = function(id, ok){
                var p = pending[id]; delete pending[id];
                if (p && p.img.__ksSrc === p.src) p.img.setAttribute('data-ks', ok ? 'ok' : 'no');
              };
              function canvasCheck(id, img){
                var go = function(){
                  try {
                    var w = img.naturalWidth, h = img.naturalHeight, k = Math.min(1, 480 / Math.max(w, h, 1));
                    var c = document.createElement('canvas'); c.width = Math.max(1, w * k | 0); c.height = Math.max(1, h * k | 0);
                    c.getContext('2d').drawImage(img, 0, 0, c.width, c.height);
                    KosherPictures.check(id, c.toDataURL('image/jpeg', 0.85));
                  } catch(e) { __ksDone(id, false); }
                };
                if (img.complete && img.naturalWidth) go(); else img.addEventListener('load', go, {once:true});
              }
              // Every picture stays hidden until the phone has checked exactly what it shows.
              function look(img){
                var src = img.currentSrc || img.src || '';
                if (!src || img.__ksSrc === src) return;
                img.__ksSrc = src; img.setAttribute('data-ks', 'wait');
                if (src.indexOf('data:image/svg') === 0 || /^https?:[^?#]*\.svg([?#]|$)/i.test(src)) { img.setAttribute('data-ks', 'ok'); return; }
                var id = 'k' + (++n); pending[id] = {img: img, src: src};
                try {
                  if (src.indexOf('data:') === 0) KosherPictures.check(id, src);
                  else if (/^https?:/.test(src)) { if (KosherPictures.passed(src)) __ksDone(id, true); else KosherPictures.checkUrl(id, src); }
                  else if (src.indexOf('blob:') === 0) canvasCheck(id, img);
                  else __ksDone(id, false);
                } catch(e) { __ksDone(id, false); }
              }
              function scan(root){ if (root.tagName === 'IMG') look(root); if (root.querySelectorAll) root.querySelectorAll('img').forEach(look); }
              window.__ksObs = new MutationObserver(function(list){
                ensureStyle();
                list.forEach(function(m){
                  if (m.type === 'attributes') { if (m.target.tagName === 'IMG') look(m.target); }
                  else m.addedNodes.forEach(function(x){ if (x.nodeType === 1) scan(x); });
                });
              });
              window.__ksObs.observe(document, {childList:true, subtree:true, attributes:true, attributeFilter:['src','srcset']});
              // A picture's real source (from srcset) is only known once it loads.
              document.addEventListener('load', function(e){ if (e.target.tagName === 'IMG') look(e.target); }, true);
              ensureStyle(); scan(document);
            })();
        """.trimIndent()
    }
}
