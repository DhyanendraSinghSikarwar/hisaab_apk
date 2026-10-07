package com.hisaab.app.ui.news

import android.content.Intent
import android.net.Uri
import android.util.Xml
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.hisaab.app.ui.components.HCard
import com.hisaab.app.ui.components.Segmented
import com.hisaab.app.ui.theme.Hx
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import java.net.HttpURLConnection
import java.net.URL
import java.time.Duration
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

/** A headline from a public feed. Tapping it opens the article in the browser. */
data class Headline(val title: String, val link: String, val published: ZonedDateTime?)

/** Moneycontrol's public RSS feeds. Only the feed itself is downloaded; nothing about the user is sent. */
enum class NewsFeed(val label: String, val url: String) {
    MARKETS("Markets", "https://www.moneycontrol.com/rss/marketreports.xml"),
    LATEST("Latest", "https://www.moneycontrol.com/rss/latestnews.xml"),
    BUSINESS("Business", "https://www.moneycontrol.com/rss/business.xml"),
    STOCKS("Stocks", "https://www.moneycontrol.com/rss/buzzingstocks.xml"),
}

data class NewsState(
    val feed: NewsFeed = NewsFeed.MARKETS,
    val items: List<Headline> = emptyList(),
    val loading: Boolean = false,
    val failed: Boolean = false,
)

/** Fetches a feed and keeps it for 10 minutes, so switching tabs or coming back is instant. */
@Singleton
class NewsRepository @Inject constructor() {
    private val cache = HashMap<NewsFeed, Pair<Long, List<Headline>>>()

    suspend fun load(feed: NewsFeed, force: Boolean): List<Headline> {
        val hit = cache[feed]
        if (!force && hit != null && System.currentTimeMillis() - hit.first < 10 * 60_000) return hit.second
        val items = withContext(Dispatchers.IO) { parse(fetch(feed.url)) }
        cache[feed] = System.currentTimeMillis() to items
        return items
    }

    private fun fetch(url: String): ByteArray {
        val c = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000; readTimeout = 15_000
            setRequestProperty("User-Agent", "Mozilla/5.0 (Android) Artha")
        }
        try {
            if (c.responseCode !in 200..299) error("HTTP ${c.responseCode}")
            return c.inputStream.use { it.readBytes() }
        } finally {
            c.disconnect()
        }
    }

    private fun parse(bytes: ByteArray): List<Headline> {
        val p = Xml.newPullParser().apply { setInput(bytes.inputStream(), null) }
        val out = ArrayList<Headline>()
        var title: String? = null; var link: String? = null; var date: String? = null
        var inItem = false; var tag: String? = null
        while (p.next() != XmlPullParser.END_DOCUMENT) {
            when (p.eventType) {
                XmlPullParser.START_TAG -> {
                    tag = p.name
                    if (p.name == "item") { inItem = true; title = null; link = null; date = null }
                }
                XmlPullParser.TEXT, XmlPullParser.CDSECT -> if (inItem) when (tag) {
                    "title" -> title = (title.orEmpty() + p.text).trim()
                    "link" -> link = (link.orEmpty() + p.text).trim()
                    "pubDate" -> date = (date.orEmpty() + p.text).trim()
                }
                XmlPullParser.END_TAG -> {
                    if (p.name == "item") {
                        inItem = false
                        val t = title?.let(::clean)
                        if (!t.isNullOrBlank() && !link.isNullOrBlank()) out += Headline(t, link!!, date?.let(::parseDate))
                    }
                    tag = null
                }
            }
        }
        return out.distinctBy { it.title }.take(60)
    }

    private fun clean(s: String) = s.replace(Regex("<[^>]+>"), "").replace("&amp;", "&").replace("&quot;", "\"")
        .replace("&#39;", "'").replace("&apos;", "'").replace(Regex("\\s+"), " ").trim()

    private fun parseDate(s: String): ZonedDateTime? = runCatching { ZonedDateTime.parse(s, DateTimeFormatter.RFC_1123_DATE_TIME) }.getOrNull()
}

@HiltViewModel
class NewsViewModel @Inject constructor(private val repo: NewsRepository) : ViewModel() {
    private val _state = MutableStateFlow(NewsState())
    val state = _state.asStateFlow()

    init { load(false) }

    fun select(feed: NewsFeed) { if (feed != _state.value.feed) { _state.update { it.copy(feed = feed, items = emptyList()) }; load(false) } }

    fun load(force: Boolean) = viewModelScope.launch {
        val feed = _state.value.feed
        _state.update { it.copy(loading = true, failed = false) }
        val items = runCatching { repo.load(feed, force) }.getOrNull()
        _state.update { s -> if (s.feed != feed) s else s.copy(items = items ?: s.items, loading = false, failed = items == null) }
    }
}

/** Market headlines from Moneycontrol. A tap opens the story in the browser, outside Artha. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewsRoute(onBack: () -> Unit, vm: NewsViewModel = hiltViewModel()) {
    val s by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    fun open(h: Headline) = runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(h.link)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                colors = com.hisaab.app.ui.theme.clearTopBar(),
                title = {
                    Column {
                        Text("Market news")
                        Text("Moneycontrol", fontSize = 12.sp, color = Hx.text2)
                    }
                },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            )
        },
    ) { inner ->
        Column(Modifier.padding(top = inner.calculateTopPadding()).fillMaxSize()) {
            Segmented(
                NewsFeed.entries.map { it.label }, NewsFeed.entries.indexOf(s.feed), { vm.select(NewsFeed.entries[it]) },
                Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            PullToRefreshBox(isRefreshing = s.loading && s.items.isNotEmpty(), onRefresh = { vm.load(true) }, modifier = Modifier.fillMaxSize()) {
                AnimatedContent(
                    targetState = when { s.items.isNotEmpty() -> 0; s.failed -> 1; else -> 2 },
                    transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "news",
                ) { mode ->
                    when (mode) {
                        0 -> LazyColumn(
                            Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(
                                start = 16.dp, end = 16.dp, top = 4.dp,
                                bottom = 24.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding(),
                            ),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            itemsIndexed(s.items, key = { _, h -> h.link }) { i, h -> HeadlineCard(h, lead = i == 0) { open(h) } }
                            item {
                                Text(
                                    "Headlines from Moneycontrol's public feed. Stories open in your browser.",
                                    fontSize = 11.sp, color = Hx.text2, modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                                )
                            }
                        }
                        1 -> Column(Modifier.fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                            Icon(Icons.Filled.CloudOff, null, tint = Hx.text2, modifier = Modifier.size(40.dp))
                            Spacer(Modifier.height(12.dp))
                            Text("Couldn't load headlines", style = MaterialTheme.typography.titleMedium)
                            Text("Check your connection and try again.", color = Hx.text2, fontSize = 13.sp)
                            TextButton(onClick = { vm.load(true) }) { Text("Try again") }
                        }
                        else -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            items(6) { SkeletonCard() }
                        }
                    }
                }
            }
        }
    }
}

/** One headline: an accent rule, the title, and how long ago it was published. The first one is set larger. */
@Composable
private fun HeadlineCard(h: Headline, lead: Boolean, onClick: () -> Unit) {
    HCard(onClick = onClick, padding = 14.dp) {
        Row(verticalAlignment = Alignment.Top) {
            Box(Modifier.padding(top = 4.dp).width(3.dp).height(if (lead) 40.dp else 28.dp).clip(CircleShape).background(Hx.accent))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    h.title, fontSize = if (lead) 17.sp else 15.sp, fontWeight = if (lead) FontWeight.Bold else FontWeight.SemiBold,
                    maxLines = 3, overflow = TextOverflow.Ellipsis, lineHeight = if (lead) 23.sp else 20.sp,
                )
                Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(listOfNotNull("Moneycontrol", h.published?.let(::ago)).joinToString(" · "), fontSize = 12.sp, color = Hx.text2, modifier = Modifier.weight(1f))
                    Icon(Icons.AutoMirrored.Filled.OpenInNew, "Open in browser", tint = Hx.text2, modifier = Modifier.size(14.dp))
                }
            }
        }
    }
}

@Composable
private fun SkeletonCard() {
    HCard(padding = 14.dp) {
        Box(Modifier.fillMaxWidth(0.9f).height(14.dp).clip(RoundedCornerShape(4.dp)).background(Hx.surface2))
        Spacer(Modifier.height(8.dp))
        Box(Modifier.fillMaxWidth(0.6f).height(14.dp).clip(RoundedCornerShape(4.dp)).background(Hx.surface2))
        Spacer(Modifier.height(10.dp))
        Box(Modifier.width(90.dp).height(10.dp).clip(RoundedCornerShape(4.dp)).background(Hx.surface2))
    }
}

private fun ago(t: ZonedDateTime): String {
    val d = Duration.between(t.toInstant(), java.time.Instant.now())
    return when {
        d.toMinutes() < 1 -> "just now"
        d.toMinutes() < 60 -> "${d.toMinutes()}m ago"
        d.toHours() < 24 -> "${d.toHours()}h ago"
        else -> "${d.toDays()}d ago"
    }
}
