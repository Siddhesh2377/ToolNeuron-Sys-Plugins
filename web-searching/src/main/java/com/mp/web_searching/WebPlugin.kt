package com.mp.web_searching

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.annotation.Keep
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
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
    private val searchAdapter by lazy { moshi.adapter(SearchResponse::class.java) }
    private val pageAdapter by lazy { moshi.adapter(PageSummary::class.java) }

    // ------------------ Scope / lifecycle ------------------

    private val job = SupervisorJob()
    private val ioScope = CoroutineScope(job + Dispatchers.IO)

    override fun onDestroy() {
        super.onDestroy()
        job.cancel()
        Log.d(TAG, "onDestroy: scope cancelled")
    }

    // ------------------ UI State ------------------

    private sealed interface UiState {
        data object Idle : UiState
        data class Loading(val label: String) : UiState
        data class SearchSuccess(val data: SearchResponse) : UiState
        data class FetchSuccess(val data: PageSummary) : UiState
        data class Error(val message: String) : UiState
    }

    private val _uiState = MutableStateFlow<UiState>(UiState.Idle)
    private val uiState = _uiState.asStateFlow()

    private fun setLoading(label: String) = _uiState.update { UiState.Loading(label) }
    private fun setSearchSuccess(data: SearchResponse) = _uiState.update { UiState.SearchSuccess(data) }
    private fun setFetchSuccess(data: PageSummary) = _uiState.update { UiState.FetchSuccess(data) }
    private fun setError(msg: String) = _uiState.update { UiState.Error(msg) }

    // ------------------ Compose UI ------------------

    @Keep
    @Composable
    override fun AppContent() {
        val state by uiState.collectAsState()
        val ctx = LocalContext.current

        Surface(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {

                // STATUS CHIP (replaces title)
                StatusChip(state)

                // CONTENT AREA
                when (val s = state) {
                    UiState.Idle -> AssistCard(
                        "Ready",
                        "Call a tool (searchWeb / fetchPage) to populate results here."
                    )

                    is UiState.Loading -> LoadingCard(s.label)

                    is UiState.SearchSuccess -> {
                        ResultMetaCard(
                            "Search complete",
                            "“${s.data.query}” • ${s.data.results.size} results • ${s.data.elapsed_ms} ms"
                        )
                        SearchResultsList(
                            results = s.data.results,
                            onOpen = { url -> openLink(ctx, url) }
                        )
                    }

                    is UiState.FetchSuccess -> {
                        ResultMetaCard("Page fetched", s.data.url)
                        PageSummaryCard(s.data.summary)
                    }

                    is UiState.Error -> ErrorCard("Error", s.message)
                }
            }
        }
    }

    @Composable
    private fun StatusChip(state: UiState) {
        val (label, tone) = when (state) {
            UiState.Idle -> "Idle" to ChipTone.Neutral
            is UiState.Loading -> "Searching…" to ChipTone.Info
            is UiState.SearchSuccess -> "Search complete" to ChipTone.Success
            is UiState.FetchSuccess -> "Fetch complete" to ChipTone.Success
            is UiState.Error -> "Error" to ChipTone.Error
        }
        Surface(
            color = when (tone) {
                ChipTone.Neutral -> MaterialTheme.colorScheme.surfaceVariant
                ChipTone.Info -> MaterialTheme.colorScheme.primaryContainer
                ChipTone.Success -> MaterialTheme.colorScheme.tertiaryContainer
                ChipTone.Error -> MaterialTheme.colorScheme.errorContainer
            },
            contentColor = when (tone) {
                ChipTone.Neutral -> MaterialTheme.colorScheme.onSurfaceVariant
                ChipTone.Info -> MaterialTheme.colorScheme.onPrimaryContainer
                ChipTone.Success -> MaterialTheme.colorScheme.onTertiaryContainer
                ChipTone.Error -> MaterialTheme.colorScheme.onErrorContainer
            },
            shape = RoundedCornerShape(50),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
            )
        }
    }

    private enum class ChipTone { Neutral, Info, Success, Error }

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
    private fun ResultMetaCard(title: String, subtitle: String) {
        ElevatedCard(
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
        ) {
            Column(Modifier.padding(16.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.size(4.dp))
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }

    @Composable
    private fun SearchResultsList(results: List<SearchItem>, onOpen: (String) -> Unit) {
        if (results.isEmpty()) {
            AssistCard("No results", "Try a different query.")
            return
        }
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            results.forEach { item ->
                ElevatedCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onOpen(item.url) }
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Text(
                            item.title.ifBlank { item.url },
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(Modifier.height(6.dp))
                        if (item.snippet.isNotBlank()) {
                            Text(
                                item.snippet,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 4,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(Modifier.height(8.dp))
                        }
                        Text(
                            item.url.hostOrSelf(),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
        }
    }

    @Composable
    private fun PageSummaryCard(summary: String) {
        ElevatedCard(
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
        ) {
            Text(
                text = summary,
                modifier = Modifier.padding(16.dp),
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }

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

                setLoading("Searching…")
                ioScope.launch {
                    try {
                        val json = withTimeout(15_000L) { searchWeb(query, limit) }
                        // parse → UI state shows “Search complete”
                        searchAdapter.fromJson(json)?.let { setSearchSuccess(it) }
                            ?: run { setError("Parse error") }
                        callback(json) // keep raw json for caller
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
                        pageAdapter.fromJson(res)?.let { setFetchSuccess(it) }
                            ?: run { setError("Parse error") }
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

    @JsonClass(generateAdapter = true)
    data class PageSummary(
        val url: String,
        val summary: String
    )

    // ------------------ Public functions used by tools ------------------

    suspend fun searchWeb(query: String, limit: Int = 5): String = withContext(Dispatchers.IO) {
        require(query.isNotBlank()) { "Query must not be blank" }

        val started = System.currentTimeMillis()
        Log.d(TAG, "searchWeb() q='$query', limit=$limit")

        val url = "https://duckduckgo.com/html/".toUri()
            .buildUpon()
            .appendQueryParameter("q", query)
            .appendQueryParameter("kl", "in-en")
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
            str.take(MAX_HTML_CHARS)
        }

        val doc: Document = Jsoup.parse(body)
        val resultEls = doc.select("div.result, div.results_links, div.result__body, article[data-nrn]")

        val items = mutableListOf<SearchItem>()
        for (el in resultEls) {
            if (items.size >= limit) break

            val titleEl = el.selectFirst("a.result__a, a.result__title, a[data-testid=ResultTitle], h2 a") ?: continue
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
        searchAdapter.toJson(SearchResponse(query = query, results = items, elapsed_ms = elapsed))
    }

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
            resp.body.string().orEmpty().take(MAX_HTML_CHARS)
        }

        val doc = Jsoup.parse(html)
        val main = doc.selectFirst("article, main") ?: doc.body()
        val text = main.text().trim()
        val compact = text.replace(Regex("\\s+"), " ").take(maxChars)

        pageAdapter.toJson(PageSummary(url = url, summary = compact))
    }

    // ------------------ Helpers ------------------

    private fun cleanDuckDuckGoUrl(raw: String): String = try {
        val uri = raw.toUri()
        if (uri.path?.startsWith("/l/") == true) {
            val uddg = uri.getQueryParameter("uddg")
            if (!uddg.isNullOrBlank()) java.net.URLDecoder.decode(uddg, "UTF-8") else raw
        } else {
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

    private fun errorJson(tool: String, message: String): String =
        """{"tool":"${tool.replace("\"", "\\\"")}","error":"${message.replace("\"", "\\\"")}"}"""

    private fun openLink(ctx: Context, url: String) {
        try {
            ctx.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: Throwable) {
            // ignore
        }
    }

    private fun String.hostOrSelf(): String = try {
        val u = this.toUri()
        (u.host ?: this).removePrefix("www.")
    } catch (_: Exception) { this }

    companion object {
        private const val TAG = "WebPlugin"
        private const val UA =
            "Mozilla/5.0 (Linux; Android 13; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36"
        private const val MAX_HTML_CHARS = 500_000
    }
}
