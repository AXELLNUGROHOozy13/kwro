package com.kawrexam.student.ui

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.net.http.SslError
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.CookieManager
import android.webkit.DownloadListener
import android.webkit.GeolocationPermissions
import android.webkit.JsResult
import android.webkit.PermissionRequest
import android.webkit.RenderProcessGoneDetail
import android.webkit.SslErrorHandler
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import com.kawrexam.student.MainViewModel
import kotlinx.coroutines.delay
import java.io.ByteArrayInputStream

/*
 * Exam browser: the "Ujian Web" mode. The exam is an external https site shown in a WebView INSIDE KawRexam, so
 * everything that protects a normal exam (screen pinning, the network lock that cuts every other app, FLAG_SECURE,
 * leave detection, penalties, supervisor PIN, token) applies unchanged. This file only adds the browser itself and
 * keeps the student on the sites the teacher allowed.
 */

/** Which addresses the student may open. The start site and its subdomains are always allowed. */
internal class HostPolicy(val startUrl: String, extra: List<String>) {
    val startHost: String = Uri.parse(startUrl).host?.lowercase().orEmpty()
    private val hosts: List<String> = (listOf(startHost) + extra.map { it.trim().lowercase() }).filter { it.isNotEmpty() }.distinct()

    fun hostAllowed(host: String?): Boolean {
        val h = host?.lowercase()?.trimEnd('.') ?: return false
        return hosts.any { h == it || h.endsWith(".$it") }
    }

    /** A page the student navigates to (links, redirects, forms). https only, no user-info tricks, host on the list. */
    fun allowsPage(uri: Uri): Boolean {
        if (uri.toString().equals("about:blank", ignoreCase = true)) return true
        if (uri.scheme?.lowercase() != "https" || uri.userInfo != null) return false
        return hostAllowed(uri.host)
    }

    /** Images, scripts, fonts, frames: any https (or inline data / blob) so CDNs keep working; plain http, file and content never. */
    fun allowsResource(uri: Uri): Boolean = when (uri.scheme?.lowercase()) {
        "https", "data", "blob", "about" -> uri.userInfo == null
        else -> false
    }
}

private class BrowserHooks(
    val onBlocked: (String) -> Unit,
    val onNotice: (String) -> Unit,
    val onPage: (WebView, String?) -> Unit,
    val onProgress: (Int) -> Unit,
    val onCrash: () -> Unit,
    val dialogOpened: () -> Unit,
    val dialogClosed: () -> Unit,
)

// Copy / cut / paste / context menu / text selection are blocked everywhere except inside text fields, where the student still types.
private const val LOCK_JS = """(function(){
if(window.__kxLock)return;window.__kxLock=1;
var st=document.createElement('style');
st.textContent='*{-webkit-user-select:none!important;user-select:none!important;-webkit-touch-callout:none!important}input,textarea,[contenteditable="true"]{-webkit-user-select:text!important;user-select:text!important}';
(document.head||document.documentElement).appendChild(st);
function field(e){var t=e&&e.target;return !!t&&(t.tagName==='INPUT'||t.tagName==='TEXTAREA'||t.isContentEditable);}
['copy','cut','paste','contextmenu','dragstart','drop'].forEach(function(n){document.addEventListener(n,function(e){e.preventDefault();e.stopPropagation();},true);});
document.addEventListener('selectstart',function(e){if(!field(e)){e.preventDefault();}},true);
})();"""

@Suppress("DEPRECATION")
@SuppressLint("SetJavaScriptEnabled", "ClickableViewAccessibility")
private fun buildWebView(ctx: Context, policy: HostPolicy, lockClipboard: Boolean, hooks: BrowserHooks): WebView {
    val main = Handler(Looper.getMainLooper())
    val web = WebView(ctx)
    web.setBackgroundColor(android.graphics.Color.WHITE)
    web.importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS // no password-manager popups
    web.overScrollMode = View.OVER_SCROLL_NEVER
    if (lockClipboard) { web.isLongClickable = false; web.setOnLongClickListener { true }; web.isHapticFeedbackEnabled = false }

    val s = web.settings
    s.javaScriptEnabled = true
    s.domStorageEnabled = true
    s.allowFileAccess = false
    s.allowContentAccess = false
    s.allowFileAccessFromFileURLs = false
    s.allowUniversalAccessFromFileURLs = false
    s.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
    s.setSupportMultipleWindows(false)
    s.javaScriptCanOpenWindowsAutomatically = false
    s.setGeolocationEnabled(false)
    s.mediaPlaybackRequiresUserGesture = true
    s.setSupportZoom(true); s.builtInZoomControls = true; s.displayZoomControls = false
    s.safeBrowsingEnabled = true
    s.userAgentString = s.userAgentString + " KawRexam/1.0" // lets an exam site insist on the exam browser
    CookieManager.getInstance().setAcceptCookie(true)
    CookieManager.getInstance().setAcceptThirdPartyCookies(web, true) // LMS logins often rely on them

    web.setDownloadListener(DownloadListener { _, _, _, _, _ -> main.post { hooks.onNotice("Unduhan tidak diizinkan saat ujian.") } })

    web.webViewClient = object : WebViewClient() {
        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
            if (policy.allowsPage(request.url)) return false
            hooks.onBlocked(request.url.host ?: request.url.scheme ?: "alamat tidak dikenal")
            return true
        }

        // Second line of defence: shouldOverrideUrlLoading is not called for every kind of navigation (form POSTs, for one).
        override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
            val ok = if (request.isForMainFrame) policy.allowsPage(request.url) else policy.allowsResource(request.url)
            if (ok) return null
            if (request.isForMainFrame) main.post { hooks.onBlocked(request.url.host ?: request.url.scheme ?: "alamat tidak dikenal") }
            return WebResourceResponse("text/plain", "utf-8", 403, "Forbidden", emptyMap(), ByteArrayInputStream(ByteArray(0)))
        }

        override fun onPageStarted(view: WebView, url: String?, favicon: android.graphics.Bitmap?) { hooks.onPage(view, url) }

        override fun onPageFinished(view: WebView, url: String?) {
            hooks.onPage(view, url)
            if (lockClipboard) view.evaluateJavascript(LOCK_JS, null)
        }

        override fun doUpdateVisitedHistory(view: WebView, url: String?, isReload: Boolean) { hooks.onPage(view, url) }

        override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
            handler.cancel() // never proceed past a bad certificate
            hooks.onNotice("Sertifikat keamanan situs tidak valid. Hubungi pengawas.")
        }

        override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
            if (request.isForMainFrame) hooks.onNotice("Halaman tidak bisa dimuat. Periksa koneksi, lalu tekan Muat ulang.")
        }

        override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
            hooks.onCrash() // the WebView is unusable after this: the screen builds a fresh one
            return true
        }
    }

    web.webChromeClient = object : WebChromeClient() {
        override fun onProgressChanged(view: WebView, newProgress: Int) { hooks.onProgress(newProgress) }

        // No camera, microphone, location, file picker or popups for the exam site.
        override fun onPermissionRequest(request: PermissionRequest) { request.deny() }
        override fun onGeolocationPermissionsShowPrompt(origin: String, callback: GeolocationPermissions.Callback) { callback.invoke(origin, false, false) }
        override fun onShowFileChooser(webView: WebView, callback: ValueCallback<Array<Uri>>, params: FileChooserParams): Boolean {
            callback.onReceiveValue(null)
            hooks.onNotice("Unggah berkas tidak tersedia di ujian ini.")
            return true
        }
        override fun onCreateWindow(view: WebView, isDialog: Boolean, isUserGesture: Boolean, resultMsg: android.os.Message): Boolean = false

        // JavaScript dialogs are real windows that take focus: register them as the app's own so they are not read as leaving the exam.
        override fun onJsAlert(view: WebView, url: String?, message: String?, result: JsResult): Boolean { jsDialog(ctx, hooks, message.orEmpty(), result, false); return true }
        override fun onJsConfirm(view: WebView, url: String?, message: String?, result: JsResult): Boolean { jsDialog(ctx, hooks, message.orEmpty(), result, true); return true }
        override fun onJsPrompt(view: WebView, url: String?, message: String?, defaultValue: String?, result: android.webkit.JsPromptResult): Boolean { result.cancel(); return true }
        override fun onJsBeforeUnload(view: WebView, url: String?, message: String?, result: JsResult): Boolean { result.confirm(); return true }
    }
    return web
}

private fun jsDialog(ctx: Context, hooks: BrowserHooks, message: String, result: JsResult, confirmable: Boolean) {
    hooks.dialogOpened()
    var answered = false
    val builder = AlertDialog.Builder(ctx).setMessage(message).setCancelable(false)
        .setPositiveButton("OK") { _, _ -> answered = true; result.confirm() }
    if (confirmable) builder.setNegativeButton("Batal") { _, _ -> answered = true; result.cancel() }
    val dialog = builder.create()
    dialog.window?.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
    dialog.setOnDismissListener { hooks.dialogClosed(); if (!answered) result.cancel() }
    dialog.show()
}

/** Leaves nothing behind on a shared phone: logins, storage, cache and history of the exam site are wiped. */
private fun wipe(web: WebView) {
    runCatching {
        (web.parent as? ViewGroup)?.removeView(web)
        web.stopLoading(); web.loadUrl("about:blank"); web.clearHistory(); web.clearCache(true); web.clearFormData()
        CookieManager.getInstance().removeAllCookies(null); CookieManager.getInstance().flush()
        WebStorage.getInstance().deleteAllData()
        web.destroy()
    }
}

@Composable
fun ExamBrowserScreen(vm: MainViewModel) {
    val s = vm.session ?: return
    val start = s.webUrl ?: return
    val ctx = LocalContext.current
    val policy = remember(s.sessionId) { HostPolicy(start, s.webAllowedHosts) }

    var host by remember(s.sessionId) { mutableStateOf(policy.startHost) }
    var secure by remember(s.sessionId) { mutableStateOf(true) }
    var progress by remember(s.sessionId) { mutableIntStateOf(0) }
    var canBack by remember(s.sessionId) { mutableStateOf(false) }
    var canForward by remember(s.sessionId) { mutableStateOf(false) }
    var notice by remember(s.sessionId) { mutableStateOf<String?>(null) }
    var rebuild by remember(s.sessionId) { mutableIntStateOf(0) }
    var confirm by remember { mutableStateOf(false) }
    var finishPin by remember { mutableStateOf("") }

    val hooks = remember(s.sessionId) {
        BrowserHooks(
            onBlocked = { h -> notice = "Situs $h tidak diizinkan di ujian ini." },
            onNotice = { notice = it },
            onPage = { w, url ->
                url?.let { u -> Uri.parse(u).host?.let { h -> host = h }; secure = u.startsWith("https://") }
                canBack = w.canGoBack(); canForward = w.canGoForward()
            },
            onProgress = { progress = it },
            onCrash = { rebuild++ },
            dialogOpened = { vm.dialogOpened() },
            dialogClosed = { vm.dialogClosed() },
        )
    }
    val web = remember(s.sessionId, rebuild) { buildWebView(ctx, policy, s.webLockClipboard, hooks) }

    DisposableEffect(web) { onDispose { wipe(web) } }
    LaunchedEffect(web) {
        if (s.webLockClipboard && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            runCatching { (ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).clearPrimaryClip() }
        }
        web.loadUrl(start)
    }
    LaunchedEffect(notice) { if (notice != null) { delay(4000); notice = null } }
    LightStatusBarIcons()

    // The back button walks the page history; it never leaves the exam.
    BackHandler(enabled = true) {
        when {
            confirm -> { confirm = false; finishPin = "" }
            web.canGoBack() -> web.goBack()
        }
    }

    val left = (vm.deadlineMs - vm.nowMs).coerceAtLeast(0)
    Box(Modifier.fillMaxSize().background(Ex.Canvas)) {
        Column(Modifier.fillMaxSize().imePadding()) {
            BrowserTopBar(s.title, host, secure, mmss(left), timerLevel(left))
            Box(Modifier.weight(1f).fillMaxWidth()) {
                key(rebuild) { AndroidView(factory = { web }, modifier = Modifier.fillMaxSize()) }
                if (progress in 1..99) {
                    Box(Modifier.fillMaxWidth().height(3.dp)) { Box(Modifier.fillMaxWidth(progress / 100f).fillMaxHeight().background(Ex.CyanGrad)) }
                }
                notice?.let { NoticeBanner(it, Modifier.align(Alignment.BottomCenter)) }
            }
            BrowserBottomBar(
                canBack = canBack, canForward = canForward,
                onBack = { web.goBack() }, onForward = { web.goForward() }, onReload = { web.reload() },
                onFinish = { confirm = true },
            )
        }

        if (confirm) {
            WebFinishModal(
                pin = finishPin, onPin = { finishPin = it },
                onConfirm = { val p = finishPin; confirm = false; finishPin = ""; vm.submit(p) },
                onBack = { confirm = false; finishPin = "" },
            )
        }
        ExamOverlays(vm)
    }
    ExamDialogs(vm)
}

/** The header is dark navy, so the status-bar icons must be light while the exam browser is open. */
@Composable
private fun LightStatusBarIcons() {
    val view = LocalView.current
    DisposableEffect(Unit) {
        val window = (view.context as? Activity)?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        val previous = controller?.isAppearanceLightStatusBars
        controller?.isAppearanceLightStatusBars = false
        onDispose { if (controller != null && previous != null) controller.isAppearanceLightStatusBars = previous }
    }
}

@Composable
private fun BrowserTopBar(title: String, host: String, secure: Boolean, timer: String, level: Int) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(bottomStart = 26.dp, bottomEnd = 26.dp)).background(Ex.Navy)
            .statusBarsPadding().padding(start = 14.dp, end = 14.dp, top = 10.dp, bottom = 14.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            Modifier.size(40.dp).clip(RoundedCornerShape(13.dp)).background(Brush.linearGradient(listOf(Ex.Cyan, Color(0xFFBDF4FF)))),
            contentAlignment = Alignment.Center,
        ) { Text("K", color = Ex.Navy, fontWeight = FontWeight.ExtraBold, fontSize = 19.sp) }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            // The address the student is really on: a fake look-alike site would show here.
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                Icon24(Ic.Lock, if (secure) Ex.Mint else Ex.Amber, 13.dp)
                Text(host, color = Ex.Muted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        TimerChip(timer, level)
    }
}

@Composable
private fun BrowserBottomBar(canBack: Boolean, canForward: Boolean, onBack: () -> Unit, onForward: () -> Unit, onReload: () -> Unit, onFinish: () -> Unit) {
    val shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp)
    Row(
        Modifier.fillMaxWidth().shadow(16.dp, shape, ambientColor = Color(0x3300AFCB), spotColor = Color(0x3300AFCB))
            .clip(shape).background(Color.White).navigationBarsPadding().padding(start = 14.dp, end = 14.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        SquareButton(Ic.Back, dark = false, onClick = onBack, enabled = canBack)
        SquareButton(Ic.Arrow, dark = false, onClick = onForward, enabled = canForward)
        SquareButton(Ic.Refresh, dark = false, onClick = onReload)
        ExButton("Selesai", onFinish, Modifier.weight(1f), BtnKind.Primary, trailing = Ic.Check)
    }
}

@Composable
private fun NoticeBanner(text: String, modifier: Modifier) {
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier.padding(12.dp).fillMaxWidth().clip(shape).background(Ex.AmberBg).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon24(Ic.Alert, Ex.Amber, 18.dp)
        Text(text, color = Ex.Ink, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, modifier = Modifier.weight(1f))
    }
}

/** Same rule as a normal exam: finishing needs the supervisor PIN. (Time running out submits by itself.) */
@Composable
private fun WebFinishModal(pin: String, onPin: (String) -> Unit, onConfirm: () -> Unit, onBack: () -> Unit) {
    Box(Modifier.fillMaxSize().background(Color(0xB3061C2A)).imePadding().tapOnly(onBack), contentAlignment = Alignment.Center) {
        val shape = RoundedCornerShape(32.dp)
        Column(
            Modifier.padding(20.dp).fillMaxWidth().heightIn(max = 560.dp).clip(shape).background(Color.White).tapOnly { }
                .verticalScroll(rememberScrollState()).padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                Modifier.size(72.dp).shadow(22.dp, CircleShape, ambientColor = Ex.Cyan, spotColor = Ex.Cyan).clip(CircleShape)
                    .background(Brush.linearGradient(listOf(Ex.Cyan, Color(0xFF9DEFFF)))),
                contentAlignment = Alignment.Center,
            ) { Icon24(Ic.Shield, Ex.Navy, 34.dp, filled = true, onTint = Ex.Cyan) }
            Text("Selesai mengerjakan?", color = Ex.Ink, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp, textAlign = TextAlign.Center)
            Text(
                "Pastikan jawabanmu sudah dikirim di halaman ujian (misalnya tombol Kirim atau Submit). Setelah selesai, kamu tidak bisa membuka ujian ini lagi.",
                color = Ex.Soft, fontSize = 15.sp, textAlign = TextAlign.Center,
            )
            Text("Masukkan PIN dari pengawas untuk mengumpulkan.", color = Ex.Ink, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, textAlign = TextAlign.Center)
            PinField(pin, onPin, "PIN pengawas")
            ExButton("Ya, Selesaikan", onConfirm, Modifier.fillMaxWidth(), BtnKind.Primary, enabled = pin.isNotEmpty(), size = 56)
            ExButton("Kembali Mengerjakan", onBack, Modifier.fillMaxWidth(), BtnKind.Ghost, leading = Ic.Back, size = 52)
        }
    }
}
