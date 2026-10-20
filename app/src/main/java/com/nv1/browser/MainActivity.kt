package com.nv1.browser

import android.annotation.SuppressLint
import android.app.DownloadManager
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.webkit.URLUtil
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

const val HOME_URL = "https://duckduckgo.com"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            NV1Theme {
                BrowserScreen(startUrl = HOME_URL)
            }
        }
    }
}

private val NV1DarkColors = darkColorScheme(
    primary = Color(0xFF10B981),
    onPrimary = Color(0xFF022C22),
    background = Color(0xFF09090B),
    onBackground = Color(0xFFFAFAFA),
    surface = Color(0xFF18181B),
    onSurface = Color(0xFFFAFAFA),
    surfaceVariant = Color(0xFF27272A),
    onSurfaceVariant = Color(0xFFA1A1AA),
)

@Composable
fun NV1Theme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = NV1DarkColors, content = content)
}

fun normalizeInput(raw: String): String {
    val input = raw.trim()
    if (input.isEmpty()) return HOME_URL
    if (input.startsWith("http://") || input.startsWith("https://")) return input
    val looksLikeHost = !input.contains(' ') && input.contains('.')
    return if (looksLikeHost) {
        "https://$input"
    } else {
        "https://duckduckgo.com/?q=${Uri.encode(input)}"
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun BrowserScreen(startUrl: String) {
    val webViewRef = remember { arrayOfNulls<WebView>(1) }
    val focusManager = LocalFocusManager.current

    var addressText by remember { mutableStateOf(startUrl) }
    var isEditing by remember { mutableStateOf(false) }
    var progress by remember { mutableIntStateOf(0) }
    var canGoBack by remember { mutableStateOf(false) }
    var canGoForward by remember { mutableStateOf(false) }

    val isSecure = addressText.startsWith("https://")

    BackHandler(enabled = canGoBack) {
        webViewRef[0]?.goBack()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
    ) {
        // Barre d'adresse
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = addressText,
                onValueChange = { addressText = it },
                modifier = Modifier
                    .weight(1f)
                    .onFocusChanged { isEditing = it.isFocused },
                singleLine = true,
                shape = RoundedCornerShape(24.dp),
                placeholder = { Text("Rechercher ou saisir une adresse") },
                leadingIcon = {
                    Icon(
                        imageVector = if (isSecure) Icons.Filled.Lock else Icons.Filled.Search,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = {
                    val url = normalizeInput(addressText)
                    addressText = url
                    focusManager.clearFocus()
                    webViewRef[0]?.loadUrl(url)
                })
            )
        }

        // Barre de progression
        if (progress in 1..99) {
            LinearProgressIndicator(
                progress = { progress / 100f },
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surface
            )
        }

        // Zone de la page web
        AndroidView(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            factory = { ctx ->
                WebView(ctx).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.loadWithOverviewMode = true
                    settings.useWideViewPort = true
                    settings.builtInZoomControls = true
                    settings.displayZoomControls = false
                    settings.mediaPlaybackRequiresUserGesture = false

                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(
                            view: WebView?,
                            request: WebResourceRequest?
                        ): Boolean = false

                        override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                            progress = 1
                            canGoBack = view?.canGoBack() ?: false
                            canGoForward = view?.canGoForward() ?: false
                            if (!isEditing && url != null) {
                                addressText = url
                            }
                        }

                        override fun onPageFinished(view: WebView?, url: String?) {
                            progress = 100
                            canGoBack = view?.canGoBack() ?: false
                            canGoForward = view?.canGoForward() ?: false
                        }
                    }

                    webChromeClient = object : WebChromeClient() {
                        override fun onProgressChanged(view: WebView?, newProgress: Int) {
                            progress = newProgress
                        }
                    }

                    setDownloadListener { url, userAgent, contentDisposition, mimeType, _ ->
                        val fileName = URLUtil.guessFileName(url, contentDisposition, mimeType)
                        val request = DownloadManager.Request(Uri.parse(url)).apply {
                            setMimeType(mimeType)
                            addRequestHeader("User-Agent", userAgent)
                            setTitle(fileName)
                            setNotificationVisibility(
                                DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED
                            )
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                setDestinationInExternalPublicDir(
                                    Environment.DIRECTORY_DOWNLOADS,
                                    fileName
                                )
                            }
                        }
                        val manager = ctx.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                        manager.enqueue(request)
                        Toast.makeText(ctx, "Téléchargement : $fileName", Toast.LENGTH_SHORT).show()
                    }

                    webViewRef[0] = this
                    loadUrl(startUrl)
                }
            }
        )

        // Barre de navigation
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = { webViewRef[0]?.goBack() },
                enabled = canGoBack
            ) {
                Icon(imageVector = Icons.Filled.ArrowBack, contentDescription = "Retour")
            }
            IconButton(
                onClick = { webViewRef[0]?.goForward() },
                enabled = canGoForward
            ) {
                Icon(imageVector = Icons.Filled.ArrowForward, contentDescription = "Avancer")
            }
            IconButton(onClick = { webViewRef[0]?.reload() }) {
                Icon(imageVector = Icons.Filled.Refresh, contentDescription = "Actualiser")
            }
            IconButton(onClick = {
                addressText = startUrl
                webViewRef[0]?.loadUrl(startUrl)
            }) {
                Icon(imageVector = Icons.Filled.Home, contentDescription = "Accueil")
            }
        }
    }
}
