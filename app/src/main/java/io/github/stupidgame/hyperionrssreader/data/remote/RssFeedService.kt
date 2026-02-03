package io.github.stupidgame.hyperionrssreader.data.remote

import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.net.URL

class RssFeedService(
    private val okHttpClient: OkHttpClient,
    private val rssFeedParser: RssFeedParser
) {

    @Throws(IOException::class)
    fun fetchRssFeed(feedUrl: String): RssFeed {
        // まずコンテンツを文字列として取得する
        val (content, _) = fetchContent(feedUrl)

        return try {
            // そのままRSSとしてパースを試みる
            rssFeedParser.parse(content.byteInputStream(), feedUrl)
        } catch (e: Exception) {
            // パースに失敗した場合（HTMLだった場合など）、HTML内からRSSリンクを探す
            val discoveredUrl = findRssLink(content, feedUrl)
            
            if (discoveredUrl != null && discoveredUrl != feedUrl) {
                // RSSリンクが見つかったら、そのURLで再度取得・パースを試みる
                val (newContent, _) = fetchContent(discoveredUrl)
                try {
                    rssFeedParser.parse(newContent.byteInputStream(), discoveredUrl)
                } catch (e2: Exception) {
                    // 見つけたURLでもダメなら、最初のエラーを投げる（もしくはe2でもいいが、大元の原因の方が分かりやすい場合も）
                    throw e
                }
            } else {
                // RSSリンクが見つからなければ、元のエラーを投げる
                throw e
            }
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

    private fun findRssLink(html: String, baseUrl: String): String? {
        // <link>タグを探す正規表現
        val linkTagRegex = "<link\\s+([^>]+)>".toRegex(RegexOption.IGNORE_CASE)
        val matches = linkTagRegex.findAll(html)
        
        for (match in matches) {
            val attributes = match.groupValues[1]
            // type="application/rss+xml" が含まれているか確認
            if (attributes.contains("application/rss+xml", ignoreCase = true)) {
                // href属性の値を抽出
                val hrefRegex = "href=[\"']([^\"']+)[\"']".toRegex(RegexOption.IGNORE_CASE)
                val hrefMatch = hrefRegex.find(attributes)
                
                if (hrefMatch != null) {
                    val link = hrefMatch.groupValues[1]
                    // 相対URLを絶対URLに変換
                    return try {
                        URL(URL(baseUrl), link).toString()
                    } catch (e: Exception) {
                        null
                    }
                }
            }
        }
        return null
    }
}