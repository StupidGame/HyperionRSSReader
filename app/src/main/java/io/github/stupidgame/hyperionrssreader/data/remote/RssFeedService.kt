package io.github.stupidgame.hyperionrssreader.data.remote

import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import java.io.IOException
import java.net.URL

data class RssCandidate(
    val title: String,
    val url: String
)

class RssFeedService(
    private val okHttpClient: OkHttpClient,
    private val rssFeedParser: RssFeedParser
) {

    suspend fun fetchRssFeed(feedUrl: String): RssFeed {
        return rssFeedParser.parse(feedUrl)
    }

    suspend fun getRssCandidates(url: String): List<RssCandidate> {
        return try {
            rssFeedParser.parse(url)
            listOf(RssCandidate(url, url))
        } catch (e: Exception) {
            try {
                val (content, _) = fetchContent(url)
                findRssCandidates(content, url)
            } catch (io: IOException) {
                emptyList()
            }
        }
    }

    private fun fetchContent(url: String): Pair<String, String?> {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/58.0.3029.110 Safari/537.36")
            .build()

        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Unexpected code ${response.code}")
            val body = response.body ?: throw IOException("Response body is null")
            return body.string() to response.header("Content-Type")
        }
    }

    private fun findRssCandidates(html: String, baseUrl: String): List<RssCandidate> {
        val candidates = mutableListOf<RssCandidate>()
        val document = Jsoup.parse(html, baseUrl)

        document.select("link[type='application/rss+xml'], link[type='application/atom+xml']").forEach { element ->
            val href = element.attr("abs:href")
            if (href.isNotEmpty()) {
                val title = element.attr("title").ifEmpty { href }
                candidates.add(RssCandidate(title, href))
            }
        }

        document.select("a[href]").forEach { element ->
            val href = element.attr("abs:href")
            if (href.contains("rss", ignoreCase = true) || href.contains("feed", ignoreCase = true) || href.endsWith(".xml", ignoreCase = true)) {
                if (candidates.none { it.url == href }) {
                    val title = element.text().ifEmpty { href }
                    candidates.add(RssCandidate(title, href))
                }
            }
        }

        return candidates.distinctBy { it.url }
    }
}