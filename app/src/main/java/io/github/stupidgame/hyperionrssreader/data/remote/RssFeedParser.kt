package io.github.stupidgame.hyperionrssreader.data.remote

import com.prof18.rssparser.RssParserBuilder
import com.prof18.rssparser.model.RssChannel as LibRssChannel
import com.prof18.rssparser.model.RssItem as LibRssItem

// Keep existing data classes
data class RssFeed(
    val url: String,
    val channel: RssChannel,
)

data class RssChannel(
    val title: String,
    val link: String,
    val description: String,
    val items: List<RssItem>,
)

data class RssItem(
    val title: String,
    val link: String,
    val pubDate: String,
    val description: String,
)

class RssFeedParser {

    private val parser = RssParserBuilder().build()

    suspend fun parse(feedUrl: String): RssFeed {
        val libChannel = parser.getRssChannel(feedUrl)
        return RssFeed(
            url = feedUrl,
            channel = mapChannel(libChannel)
        )
    }

    private fun mapChannel(libChannel: LibRssChannel): RssChannel {
        return RssChannel(
            title = libChannel.title ?: "",
            link = libChannel.link ?: "",
            description = libChannel.description ?: "",
            items = libChannel.items.map { mapItem(it) }
        )
    }

    private fun mapItem(libItem: LibRssItem): RssItem {
        return RssItem(
            title = libItem.title ?: "",
            link = libItem.link ?: "",
            pubDate = libItem.pubDate ?: "",
            description = libItem.content ?: libItem.description ?: ""
        )
    }
}