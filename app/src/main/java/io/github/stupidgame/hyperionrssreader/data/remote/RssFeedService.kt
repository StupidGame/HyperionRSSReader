package io.github.stupidgame.hyperionrssreader.data.remote

import okhttp3.OkHttpClient
import okhttp3.Request
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

    @Throws(IOException::class)
    fun fetchRssFeed(feedUrl: String): RssFeed {
        // コンテンツ取得
        val (content, contentType) = fetchContent(feedUrl)
        
        // そのままRSSとしてパースを試みる
        return try {
             rssFeedParser.parse(content.byteInputStream(), feedUrl)
        } catch (e: Exception) {
            val discoveredCandidates = findRssCandidates(content, feedUrl)
            val bestCandidate = discoveredCandidates.firstOrNull()
            
            if (bestCandidate != null && bestCandidate.url != feedUrl) {
                val (newContent, _) = fetchContent(bestCandidate.url)
                try {
                    rssFeedParser.parse(newContent.byteInputStream(), bestCandidate.url)
                } catch (e2: Exception) {
                    throw e
                }
            } else {
                throw e
            }
        }
    }

    // URLからRSSの候補を探すメソッド
    fun getRssCandidates(url: String): List<RssCandidate> {
        val (content, contentType) = try {
             fetchContent(url)
        } catch (e: Exception) {
            return emptyList()
        }

        // まず、そのURL自体がRSSかどうかチェック
        try {
            rssFeedParser.parse(content.byteInputStream(), url)
            // パース成功なら、そのURL自体が唯一の候補
            return listOf(RssCandidate("Direct Feed", url))
        } catch (e: Exception) {
            // 失敗ならHTMLとしてリンク探索
            return findRssCandidates(content, url)
        }
    }

    private fun fetchContent(url: String): Pair<String, String?> {
        val request = Request.Builder()
            .url(url)
            .build()

        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Unexpected code ${response.code}")
            val body = response.body ?: throw IOException("Response body is null")
            return body.string() to response.header("Content-Type")
        }
    }

    private fun findRssCandidates(html: String, baseUrl: String): List<RssCandidate> {
        val candidates = mutableListOf<RssCandidate>()
        
        // <link>タグを探す正規表現
        val linkTagRegex = "<link\\s+([^>]+)>".toRegex(RegexOption.IGNORE_CASE)
        val matches = linkTagRegex.findAll(html)
        
        for (match in matches) {
            val attributes = match.groupValues[1]
            // RSSまたはAtomを検出
            if (attributes.contains("application/rss+xml", ignoreCase = true) || 
                attributes.contains("application/atom+xml", ignoreCase = true)) {
                
                val hrefRegex = "href=[\"']([^\"']+)[\"']".toRegex(RegexOption.IGNORE_CASE)
                val titleRegex = "title=[\"']([^\"']+)[\"']".toRegex(RegexOption.IGNORE_CASE)
                
                val hrefMatch = hrefRegex.find(attributes)
                val titleMatch = titleRegex.find(attributes)
                
                if (hrefMatch != null) {
                    val link = hrefMatch.groupValues[1]
                    val title = titleMatch?.groupValues?.get(1) ?: "Unknown Feed"
                    
                    try {
                        // 相対パスを絶対パスに変換
                        val absoluteUrl = URL(URL(baseUrl), link).toString()
                        candidates.add(RssCandidate(title, absoluteUrl))
                    } catch (e: Exception) {
                        // ignore invalid URL
                    }
                }
            }
        }
        
        // <a>タグからのリンク探索も追加（RSSアイコンなどからのリンク用）
        // 単純にhrefが .rss, .xml, .atom で終わるもの、またはURLに 'rss', 'feed' が含まれるものを探す
        // これは精度を下げる可能性もあるが、<link>タグがないサイトに対応するため
        val aTagRegex = "<a\\s+([^>]+)>".toRegex(RegexOption.IGNORE_CASE)
        val aMatches = aTagRegex.findAll(html)
        
        for (match in aMatches) {
            val attributes = match.groupValues[1]
            val hrefRegex = "href=[\"']([^\"']+)[\"']".toRegex(RegexOption.IGNORE_CASE)
            val hrefMatch = hrefRegex.find(attributes)
            
            if (hrefMatch != null) {
                val link = hrefMatch.groupValues[1]
                if (link.contains("rss", ignoreCase = true) || 
                    link.contains("feed", ignoreCase = true) || 
                    link.endsWith(".xml", ignoreCase = true)) {
                    
                    try {
                        val absoluteUrl = URL(URL(baseUrl), link).toString()
                        // 既に候補にあるかチェック
                        if (candidates.none { it.url == absoluteUrl }) {
                            // タイトルがない場合はURLの一部を使うか、仮のタイトル
                            val title = "Possible Feed ($link)"
                            candidates.add(RssCandidate(title, absoluteUrl))
                        }
                    } catch (e: Exception) {
                    }
                }
            }
        }

        return candidates.distinctBy { it.url }
    }
}