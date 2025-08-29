package com.mp.web_searching

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.annotation.Keep
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.dark.plugins.api.ComposableBlock
import com.dark.plugins.api.PluginApi
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONObject
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.net.URLDecoder
import java.util.concurrent.TimeUnit

class WebPlugin(context: Context) : PluginApi(context) {

    // ------------------ Networking ------------------

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(12, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .callTimeout(15, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    // ------------------ JSON ------------------

    private val moshi: Moshi by lazy {
        Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    }

    // ------------------ Scope / lifecycle ------------------

    private val job = SupervisorJob()
    private val ioScope = CoroutineScope(job + Dispatchers.IO)

    override fun onCreate(data: Any) {
        super.onCreate(data)
        Log.d(TAG, "onCreate called")
    }

    override fun onDestroy() {
        super.onDestroy()
        job.cancel() // cancel any in-flight work
        Log.d(TAG, "onDestroy: scope cancelled")
    }

    // ------------------ UI State ------------------

    private sealed interface UiState {
        data object Idle : UiState
        data class Loading(val label: String) : UiState
        data class Success(val message: String) : UiState
        data class Error(val message: String) : UiState
    }

    private val _uiState = MutableStateFlow<UiState>(UiState.Idle)
    private val uiState = _uiState.asStateFlow()

    private fun setLoading(label: String) = _uiState.update { UiState.Loading(label) }
    private fun setSuccess(msg: String) = _uiState.update { UiState.Success(msg) }
    private fun setError(msg: String) = _uiState.update { UiState.Error(msg) }

    // ------------------ Compose UI ------------------

    @Keep
    @Composable
    override fun AppContent() {
        val state by uiState.collectAsState()

        Surface(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("Web Plugin", style = MaterialTheme.typography.titleLarge)

                // Quick test buttons (optional helpers)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            // Quick sample search to demonstrate UI reactivity
                            ioScope.launch {
                                val json = searchWeb("android compose performance", limit = 5)
                                setSuccess(json)
                            }
                            setLoading("Searching…")
                        }
                    ) { Text("Sample Search") }

                    Button(
                        onClick = {
                            ioScope.launch {
                                val res = fetchAndSummarize("https://developer.android.com/jetpack/compose")
                                setSuccess(res)
                            }
                            setLoading("Fetching page…")
                        }
                    ) { Text("Sample Fetch") }
                }

                // Status card
                when (val s = state) {
                    UiState.Idle -> AssistCard("Idle", "Call a tool (searchWeb/fetchPage) to see results here.")
                    is UiState.Loading -> LoadingCard(s.label)
                    is UiState.Success -> ResultCard("Success", s.message)
                    is UiState.Error -> ErrorCard("Error", s.message)
                }
            }
        }
    }

    @Composable
    private fun AssistCard(title: String, body: String) {
        ElevatedCard(
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
        ) {
            Column(Modifier.padding(16.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.size(8.dp))
                Text(body, maxLines = 10, overflow = TextOverflow.Ellipsis)
            }
        }
    }

    @Composable
    private fun LoadingCard(label: String) {
        ElevatedCard(
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(12.dp))
                Text(label)
            }
        }
    }

    @Composable
    private fun ResultCard(title: String, body: String) =
        AssistCard(title, body)

    @Composable
    private fun ErrorCard(title: String, body: String) =
        AssistCard(title, body)

    @Keep
    override fun content(): ComposableBlock = { AppContent() }

    // ------------------ Tool entrypoint ------------------

    @Keep
    override fun runTool(
        context: Context,
        toolName: String,
        args: JSONObject,
        callback: (result: Any) -> Unit
    ) {
        super.runTool(context, toolName, args, callback)
        when (toolName) {
            "searchWeb" -> {
                val query = args.optString("query").orEmpty()
                val limit = args.optInt("limit", 5).coerceIn(1, 25)
                Log.d(TAG, "runTool(searchWeb): q='$query', limit=$limit")

                setLoading("Searching web…")
                ioScope.launch {
                    try {
                        val json = withTimeout(15_000L) { searchWeb(query, limit) }
                        setSuccess(json)
                        callback(json)
                    } catch (t: Throwable) {
                        val msg = "searchWeb failed: ${t.message ?: t::class.java.simpleName}"
                        Log.w(TAG, msg, t)
                        setError(msg)
                        callback(errorJson("searchWeb", msg))
                    }
                }
            }

            "fetchPage" -> {
                val url = args.optString("url").orEmpty()
                Log.d(TAG, "runTool(fetchPage): url='$url'")

                setLoading("Fetching page…")
                ioScope.launch {
                    try {
                        val res = withTimeout(15_000L) { fetchAndSummarize(url) }
                        setSuccess(res)
                        callback(res)
                    } catch (t: Throwable) {
                        val msg = "fetchPage failed: ${t.message ?: t::class.java.simpleName}"
                        Log.w(TAG, msg, t)
                        setError(msg)
                        callback(errorJson("fetchPage", msg))
                    }
                }
            }

            else -> {
                val msg = "Unknown tool: $toolName"
                Log.w(TAG, msg)
                setError(msg)
                callback(errorJson(toolName, msg))
            }
        }
    }

    // ------------------ Data Models ------------------

    @JsonClass(generateAdapter = true)
    data class SearchItem(
        val title: String,
        val url: String,
        val snippet: String
    )

    @JsonClass(generateAdapter = true)
    data class SearchResponse(
        val query: String,
        val results: List<SearchItem>,
        val source: String = "duckduckgo_html",
        val elapsed_ms: Long
    )

    // ------------------ Public functions used by tools ------------------

    /**
     * Simple, reliable web search via DuckDuckGo's lightweight HTML.
     */
    suspend fun searchWeb(query: String, limit: Int = 5): String = withContext(Dispatchers.IO) {
        require(query.isNotBlank()) { "Query must not be blank" }

        val started = System.currentTimeMillis()
        Log.d(TAG, "searchWeb() q='$query', limit=$limit")

        val url = "https://duckduckgo.com/html/".toUri()
            .buildUpon()
            .appendQueryParameter("q", query)
            .appendQueryParameter("kl", "in-en") // locale hint (India/English)
            .appendQueryParameter("ia", "web")
            .build()
            .toString()

        val req = Request.Builder()
            .url(url)
            .header("User-Agent", UA)
            .header("Accept-Language", "en-US,en;q=0.9")
            .get()
            .build()

        val body = client.newCall(req).execute().use { resp ->
            ensureSuccess(resp)
            val str = resp.body?.string().orEmpty()
            // Avoid extreme memory use on unexpected responses
            str.take(MAX_HTML_CHARS)
        }

        val doc: Document = Jsoup.parse(body)
        // DDG HTML varies — try a few structures
        val resultEls = doc.select(
            "div.result, div.results_links, div.result__body, article[data-nrn]"
        )

        val items = mutableListOf<SearchItem>()
        for (el in resultEls) {
            if (items.size >= limit) break

            val titleEl = el.selectFirst(
                "a.result__a, a.result__title, a[data-testid=ResultTitle], h2 a"
            ) ?: continue

            val rawHref = titleEl.attr("href").trim()
            val title = titleEl.text().trim()
            val snippet = el.selectFirst(
                "a.result__snippet, div.result__snippet, div.result__snippet.js-result-snippet, .result__snippet, p"
            )?.text()?.trim().orEmpty()

            val cleanedUrl = cleanDuckDuckGoUrl(rawHref)
            if (title.isNotEmpty() && cleanedUrl.isNotEmpty()) {
                items += SearchItem(
                    title = title,
                    url = cleanedUrl,
                    snippet = snippet.take(400)
                )
            }
        }

        val elapsed = System.currentTimeMillis() - started
        val adapter = moshi.adapter(SearchResponse::class.java)
        val json = adapter.toJson(
            SearchResponse(
                query = query,
                results = items,
                elapsed_ms = elapsed
            )
        )
        Log.d(TAG, "searchWeb() parsed ${items.size} results in ${elapsed}ms")
        json
    }

    /**
     * Fetch a page and return a short plaintext summary (very basic).
     */
    suspend fun fetchAndSummarize(url: String, maxChars: Int = 1200): String = withContext(Dispatchers.IO) {
        require(url.startsWith("http")) { "URL must start with http/https" }

        val req = Request.Builder()
            .url(url)
            .header("User-Agent", UA)
            .header("Accept-Language", "en-US,en;q=0.9")
            .get()
            .build()

        val html = client.newCall(req).execute().use { resp ->
            ensureSuccess(resp)
            resp.body?.string().orEmpty().take(MAX_HTML_CHARS)
        }

        val doc = Jsoup.parse(html)
        val main = doc.selectFirst("article, main") ?: doc.body()
        val text = main.text().trim()
        val compact = text.replace(Regex("\\s+"), " ").take(maxChars)

        """{"url":"${escape(url)}","summary":"${escape(compact)}"}"""
    }

    // ------------------ Helpers ------------------

    private fun cleanDuckDuckGoUrl(raw: String): String = try {
        val uri = Uri.parse(raw)
        // DDG often wraps external links like: /l/?uddg=<encodedUrl>&rut=...
        if (uri.path?.startsWith("/l/") == true) {
            val uddg = uri.getQueryParameter("uddg")
            if (!uddg.isNullOrBlank()) URLDecoder.decode(uddg, "UTF-8") else raw
        } else {
            // If it already looks like a full URL, keep; else try to prefix
            when {
                raw.startsWith("http") -> raw
                raw.startsWith("//") -> "https:$raw"
                else -> raw
            }
        }
    } catch (_: Exception) {
        raw
    }

    private fun ensureSuccess(resp: Response) {
        if (!resp.isSuccessful) {
            throw IllegalStateException("HTTP ${resp.code}: ${resp.message}")
        }
    }

    private fun escape(s: String): String =
        s.replace("\\", "\\\\").replace("\"", "\\\"")

    private fun errorJson(tool: String, message: String): String =
        """{"tool":"${escape(tool)}","error":"${escape(message)}"}"""

    companion object {
        private const val TAG = "WebPlugin"
        private const val UA =
            "Mozilla/5.0 (Linux; Android 13; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36"
        private const val MAX_HTML_CHARS = 500_000 // defensive bound
    }
}
