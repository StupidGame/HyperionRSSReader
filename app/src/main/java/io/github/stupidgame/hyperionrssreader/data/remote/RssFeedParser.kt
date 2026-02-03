package io.github.stupidgame.hyperionrssreader.data.remote

import android.text.Html
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.InputStream

data class RssFeed(
    val url: String,
    val channel: RssChannel
)

data class RssChannel(
    val title: String,
    val link: String,
    val description: String,
    val items: List<RssItem>
)

data class RssItem(
    val title: String,
    val link: String,
    val pubDate: String,
    val description: String
)

class RssFeedParser {

    fun parse(inputStream: InputStream, feedUrl: String): RssFeed {
        val parserFactory = XmlPullParserFactory.newInstance()
        parserFactory.isNamespaceAware = false
        val parser = parserFactory.newPullParser()
        parser.setInput(inputStream, null)

        var eventType = parser.eventType
        var channel: RssChannel? = null
        val items = mutableListOf<RssItem>()

        var title: String? = null
        var link: String? = null
        var description: String? = null
        var pubDate: String? = null

        var inItem = false

        while (eventType != XmlPullParser.END_DOCUMENT) {
            val tagName = parser.name

            when (eventType) {
                XmlPullParser.START_TAG -> {
                    when (tagName) {
                        "channel" -> {
                            channel = RssChannel("", "", "", emptyList())
                        }
                        "item" -> {
                            inItem = true
                        }
                        "title" -> {
                            val text = decode(parser.nextText())
                            if (inItem) title = text else if (channel != null) channel = channel.copy(title = text)
                        }
                        "link" -> {
                            if (inItem) link = parser.nextText() else if (channel != null) channel = channel.copy(link = parser.nextText())
                        }
                        "description" -> {
                            val text = decode(parser.nextText())
                            if (inItem) description = text else if (channel != null) channel = channel.copy(description = text)
                        }
                        "pubDate" -> {
                            if (inItem) pubDate = parser.nextText()
                        }
                    }
                }
                XmlPullParser.END_TAG -> {
                    when (tagName) {
                        "item" -> {
                            if (title != null && link != null && pubDate != null && description != null) {
                                items.add(RssItem(title, link, pubDate, description))
                            }
                            title = null
                            link = null
                            pubDate = null
                            description = null
                            inItem = false
                        }
                        "channel" -> {
                            channel = channel?.copy(items = items.toList())
                        }
                    }
                }
            }
            eventType = parser.next()
        }

        return RssFeed(feedUrl, channel ?: throw IllegalStateException("RSS Channel not found"))
    }
    
    private fun decode(text: String): String {
        return Html.fromHtml(text, Html.FROM_HTML_MODE_LEGACY).toString()
    }
}