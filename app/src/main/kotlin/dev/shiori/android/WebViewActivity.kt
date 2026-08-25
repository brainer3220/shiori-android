package dev.shiori.android

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import android.widget.ImageButton
import android.widget.TextView
import com.google.android.material.button.MaterialButtonToggleGroup

class WebViewActivity : Activity() {

    private lateinit var webView: WebView

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val url = intent.getStringExtra(EXTRA_URL) ?: run {
            finish()
            return
        }

        setContentView(R.layout.activity_webview)

        webView = findViewById(R.id.webview_content)
        webView.settings.javaScriptEnabled = true

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.webview_root)) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(bars.left, bars.top, bars.right, 0)
            insets
        }
        webView.webViewClient = object : WebViewClient() {
            override fun doUpdateVisitedHistory(view: WebView, url: String, isReload: Boolean) {
                updateNavButtons()
            }
        }
        if (savedInstanceState == null) {
            webView.loadUrl(url)
        } else {
            webView.restoreState(savedInstanceState)
        }

        findViewById<ImageButton>(R.id.webview_close_button).setOnClickListener { finish() }
        findViewById<ImageButton>(R.id.webview_back_button).setOnClickListener {
            if (webView.canGoBack()) webView.goBack()
        }
        findViewById<ImageButton>(R.id.webview_forward_button).setOnClickListener {
            if (webView.canGoForward()) webView.goForward()
        }
        findViewById<ImageButton>(R.id.webview_archive_button).setOnClickListener {
            // TODO: wire archive action to API
        }
        findViewById<ImageButton>(R.id.webview_star_button).setOnClickListener {
            // TODO: wire favorite action to API
        }
        findViewById<ImageButton>(R.id.webview_more_button).setOnClickListener {
            // TODO: overflow menu (tags, edit, delete)
        }

        val toggle = findViewById<MaterialButtonToggleGroup>(R.id.webview_view_toggle)
        val notionPlaceholder = findViewById<TextView>(R.id.webview_notion_placeholder)
        toggle.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            val showNotion = checkedId == R.id.webview_tab_notion
            webView.visibility = if (showNotion) View.INVISIBLE else View.VISIBLE
            notionPlaceholder.visibility = if (showNotion) View.VISIBLE else View.GONE
            }
    }

    private fun updateNavButtons() {
        findViewById<ImageButton>(R.id.webview_back_button).let {
            it.isEnabled = webView.canGoBack()
            it.alpha = if (it.isEnabled) 1f else 0.4f
        }
        findViewById<ImageButton>(R.id.webview_forward_button).let {
            it.isEnabled = webView.canGoForward()
            it.alpha = if (it.isEnabled) 1f else 0.4f
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        if (this::webView.isInitialized) {
            webView.saveState(outState)
        }
    }

    override fun onDestroy() {
        if (this::webView.isInitialized) {
            webView.destroy()
        }
        super.onDestroy()
    }

    companion object {
        private const val EXTRA_URL = "extra_url"

        fun start(context: Context, url: String) {
            context.startActivity(
                Intent(context, WebViewActivity::class.java).putExtra(EXTRA_URL, url),
            )
        }
    }
}
