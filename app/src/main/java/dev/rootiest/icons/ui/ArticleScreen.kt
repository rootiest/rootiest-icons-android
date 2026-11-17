package dev.rootiest.icons.ui

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.util.Base64
import android.view.ContextThemeWrapper
import android.webkit.JavascriptInterface
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.net.toUri
import dev.rootiest.icons.IconsViewModel
import dev.rootiest.icons.R
import dev.rootiest.icons.core.NamedBytes
import dev.rootiest.icons.data.SITE
import dev.rootiest.icons.render.FOLDER
import dev.rootiest.icons.render.saveToDownloads
import dev.rootiest.icons.ui.theme.Mocha
import dev.rootiest.icons.ui.theme.palette

const val ARTICLE = "$SITE/svg/"

// What the page's downloads and copies go through: its <a download> with a blob: URL is read as a data: URL and handed
// to RootiestApp.save, and navigator.clipboard.writeText to RootiestApp.copy (a WebView has neither on its own).
private const val HOOKS = """(function () {
  if (window.__rootiestHooks) return;
  window.__rootiestHooks = true;
  var click = HTMLAnchorElement.prototype.click;
  HTMLAnchorElement.prototype.click = function () {
    var a = this;
    if (a.download && a.href.indexOf('blob:') === 0) {
      fetch(a.href).then(function (r) { return r.blob(); }).then(function (b) {
        var fr = new FileReader();
        fr.onload = function () { RootiestApp.save(a.download, fr.result); };
        fr.readAsDataURL(b);
      });
      return;
    }
    return click.call(this);
  };
  if (navigator.clipboard) {
    navigator.clipboard.writeText = function (text) { RootiestApp.copy(String(text)); return Promise.resolve(); };
  }
})();"""

// The bridge the hooks call.
private class Bridge(private val context: Context) {
    @JavascriptInterface
    fun save(name: String, dataUrl: String) {
        val clean = name.replace(Regex("[\\\\/]"), "-").ifEmpty { "drawing" }
        val message = runCatching {
            saveToDownloads(context, listOf(NamedBytes(clean, Base64.decode(dataUrl.substringAfter(','), Base64.DEFAULT))))
            "Saved $clean to Downloads/$FOLDER"
        }.getOrElse { "Could not save: ${it.message}" }
        android.os.Handler(context.mainLooper).post { Toast.makeText(context, message, Toast.LENGTH_LONG).show() }
    }

    @JavascriptInterface
    fun copy(text: String) {
        android.os.Handler(context.mainLooper).post {
            context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("svg", text))
        }
    }
}

// Drawn with Math, the site's interactive article on SVG, in the app: the page in a WebView, light or dark as the app
// is. Its links home come back to the catalogue; links elsewhere open in the browser.
@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun ArticleScreen(vm: IconsViewModel) {
    val context = LocalContext.current
    fun browse(url: String) = runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri())) }
    val dark = palette === Mocha
    var failed by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }
    var canGoBack by remember { mutableStateOf(false) }
    // a WebView themed as the app, so the page's prefers-color-scheme follows the app's appearance, not the phone's
    val web = remember(dark) {
        val themed = ContextThemeWrapper(context, if (dark) android.R.style.Theme_Material_NoActionBar else android.R.style.Theme_Material_Light_NoActionBar)
        WebView(themed).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            setBackgroundColor(android.graphics.Color.TRANSPARENT)
            addJavascriptInterface(Bridge(context.applicationContext), "RootiestApp")
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                    val url = request.url.toString()
                    if (url.startsWith(ARTICLE)) return false
                    if (url == SITE || url == "$SITE/" || url.startsWith("$SITE/#") || url == "$SITE/index.html") vm.back()
                    else browse(url)
                    return true
                }

                override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                    loading = true
                }

                override fun onPageFinished(view: WebView, url: String?) {
                    loading = false
                    canGoBack = view.canGoBack()
                    view.evaluateJavascript(HOOKS, null)
                }

                override fun doUpdateVisitedHistory(view: WebView, url: String?, isReload: Boolean) {
                    canGoBack = view.canGoBack()
                }

                override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                    if (request.isForMainFrame) failed = error.description?.toString() ?: "The page could not be loaded"
                }
            }
            loadUrl(ARTICLE)
        }
    }
    DisposableEffect(web) { onDispose { web.destroy() } }
    BackHandler(enabled = canGoBack && failed == null) { web.goBack() }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Drawn with Math") },
                navigationIcon = { IconButton(onClick = vm::back) { Icon(painterResource(R.drawable.ic_tabler_arrow_left), contentDescription = "Back") } },
                actions = {
                    IconButton(onClick = { browse(web.url?.takeIf { it.startsWith(ARTICLE) } ?: ARTICLE) }) {
                        Icon(painterResource(R.drawable.ic_tabler_external_link), contentDescription = "Open in the browser")
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            val why = failed
            if (why != null) {
                Column(
                    Modifier.fillMaxSize().padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically), horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("Drawn with Math could not be loaded.", style = MaterialTheme.typography.titleMedium)
                    Text(why, color = palette.Subtext0, textAlign = TextAlign.Center)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { failed = null; web.loadUrl(ARTICLE) }) { Text("Try again") }
                        OutlinedButton(onClick = { browse(ARTICLE) }) { Text("Open in browser") }
                    }
                }
            } else {
                AndroidView({ web }, Modifier.fillMaxSize())
                if (loading) LinearProgressIndicator(Modifier.fillMaxWidth().align(Alignment.TopCenter))
            }
        }
    }
}
