package com.lagradost

import com.lagradost.api.Log
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.Qualities
import java.io.InputStream

class CanaliTV : MainAPI() {
    override var lang = "it"
    override var mainUrl =
        "https://raw.githubusercontent.com/Free-TV/IPTV/refs/heads/master/playlists/playlist_italy.m3u8"
    override var name = "Canali TV"
    override val hasMainPage = true
    override val hasQuickSearch = true
    override val hasDownloadSupport = false
    override val supportedTypes = setOf(TvType.Live)

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {
        val data = IptvPlaylistParser().parseM3U(app.get(mainUrl).text)
        return HomePageResponse(data.items.groupBy { it.attributes["group-title"] }.map { group ->
            val title = "Canali TV"
            val show = group.value.map { channel ->
                val streamurl = channel.url.toString()

                val channelname = channel.title.toString()
                val posterurl = channel.attributes["tvg-logo"].toString()
                LiveSearchResponse(
                    channelname,
                    dataToString(Data(streamurl, channelname, posterurl)),
                    this@CanaliTV.name,
                    TvType.Live,
                    posterurl,
                    lang = channel.attributes["group-title"]
                )
            }
            HomePageList(
                title,
                show,
                isHorizontalImages = true
            )
        })
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val data = IptvPlaylistParser().parseM3U(app.get(mainUrl).text)

        return data.items.filter {
            it.attributes["tvg-id"]?.contains(query) ?: false ||
                    it.title?.lowercase()?.contains(query.lowercase()) ?: false
        }.map { channel ->
            val streamurl = channel.url.toString()
            val channelname = channel.title ?: channel.attributes["tvg-id"].toString()
            val posterurl = channel.attributes["tvg-logo"].toString()
            LiveSearchResponse(
                channelname,
                dataToString(Data(streamurl, channelname, posterurl)),
                this@CanaliTV.name,
                TvType.Live,
                posterurl,
            )
        }
    }

    override suspend fun quickSearch(query: String): List<SearchResponse> {
        return search(query)
    }

    override suspend fun load(url: String): LoadResponse {
        Log.d("LOAD", "Load url: $url")
        val data = stringToData(url)
        return LiveStreamLoadResponse(
            data.title,
            data.url,
            this.name,
            url,
            data.poster,
        )
    }

    data class Data(
        val url: String,
        val title: String,
        val poster: String
    )

    private fun dataToString(data: Data): String {
        return "${data.url}@${data.title}€${data.poster}"
    }

    private fun stringToData(string: String): Data {
        val url = string.substringAfterLast('/').substringBeforeLast('@')
        val title = string.substringAfterLast('@').substringBeforeLast('€')
        val posterUrl = string.substringAfterLast('€')
        return Data(url, title, posterUrl)
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val loadData = parseJson<Data>(data)
        callback.invoke(
            ExtractorLink(
                this.name,
                loadData.title,
                loadData.url,
                "",
                Qualities.Unknown.value,
                isM3u8 = true
            )
        )
        return true
    }
}


data class Playlist(
    val items: List<TVChannel> = emptyList(),
)

data class TVChannel(
    val title: String? = null,
    val attributes: Map<String, String> = emptyMap(),
    val headers: Map<String, String> = emptyMap(),
    val url: String? = null,
    val userAgent: String? = null,
)


class IptvPlaylistParser {


    /**
     * Parse M3U8 string into [Playlist]
     *
     * @param content M3U8 content string.
     * @throws PlaylistParserException if an error occurs.
     */
    fun parseM3U(content: String): Playlist {
        return parseM3U(content.byteInputStream())
    }

    /**
     * Parse M3U8 content [InputStream] into [Playlist]
     *
     * @param input Stream of input data.
     * @throws PlaylistParserException if an error occurs.
     */
    @Throws(PlaylistParserException::class)
    fun parseM3U(input: InputStream): Playlist {
        val reader = input.bufferedReader()

        if (!reader.readLine().isExtendedM3u()) {
            throw PlaylistParserException.InvalidHeader()
        }

        val playlistItems: MutableList<TVChannel> = mutableListOf()
        var currentIndex = 0

        var line: String? = reader.readLine()

        while (line != null) {
            if (line.isNotEmpty()) {
                if (line.startsWith(EXT_INF)) {
                    val title = line.getTitle()
                    val attributes = line.getAttributes()
                    playlistItems.add(TVChannel(title, attributes))
                } else if (line.startsWith(EXT_VLC_OPT)) {
                    val item = playlistItems[currentIndex]
                    val userAgent = line.getTagValue("http-user-agent")
                    val referrer = line.getTagValue("http-referrer")
                    val headers = if (referrer != null) {
                        item.headers + mapOf("referrer" to referrer)
                    } else item.headers
                    playlistItems[currentIndex] =
                        item.copy(userAgent = userAgent, headers = headers)
                } else {
                    if (!line.startsWith("#")) {
                        val item = playlistItems[currentIndex]
                        val url = line.getUrl()
                        val userAgent = line.getUrlParameter("user-agent")
                        val referrer = line.getUrlParameter("referer")
                        val urlHeaders = if (referrer != null) {
                            item.headers + mapOf("referrer" to referrer)
                        } else item.headers
                        playlistItems[currentIndex] =
                            item.copy(
                                url = url,
                                headers = item.headers + urlHeaders,
                                userAgent = userAgent
                            )
                        currentIndex++
                    }
                }
            }

            line = reader.readLine()
        }
        return Playlist(playlistItems)
    }

    /**
     * Replace "" (quotes) from given string.
     */
    private fun String.replaceQuotesAndTrim(): String {
        return replace("\"", "").trim()
    }

    /**
     * Check if given content is valid M3U8 playlist.
     */
    private fun String.isExtendedM3u(): Boolean = startsWith(EXT_M3U)

    /**
     * Get title of media.
     *
     * Example:-
     *
     * Input:
     * ```
     * #EXTINF:-1 tvg-id="1234" group-title="Kids" tvg-logo="url/to/logo", Title
     * ```
     * Result: Title
     */
    private fun String.getTitle(): String? {
        return split(",").lastOrNull()?.replaceQuotesAndTrim()
    }

    /**
     * Get media url.
     *
     * Example:-
     *
     * Input:
     * ```
     * https://example.com/sample.m3u8|user-agent="Custom"
     * ```
     * Result: https://example.com/sample.m3u8
     */
    private fun String.getUrl(): String? {
        return split("|").firstOrNull()?.replaceQuotesAndTrim()
    }


    /**
     * Get url parameter with key.
     *
     * Example:-
     *
     * Input:
     * ```
     * http://192.54.104.122:8080/d/abcdef/video.mp4|User-Agent=Mozilla&Referer=CustomReferrer
     * ```
     * If given key is `user-agent`, then
     *
     * Result: Mozilla
     */
    private fun String.getUrlParameter(key: String): String? {
        val urlRegex = Regex("^(.*)\\|", RegexOption.IGNORE_CASE)
        val keyRegex = Regex("$key=(\\w[^&]*)", RegexOption.IGNORE_CASE)
        val paramsString = replace(urlRegex, "").replaceQuotesAndTrim()
        return keyRegex.find(paramsString)?.groups?.get(1)?.value
    }

    /**
     * Get attributes from `#EXTINF` tag as Map<String, String>.
     *
     * Example:-
     *
     * Input:
     * ```
     * #EXTINF:-1 tvg-id="1234" group-title="Kids" tvg-logo="url/to/logo", Title
     * ```
     * Result will be equivalent to kotlin map:
     * ```Kotlin
     * mapOf(
     *   "tvg-id" to "1234",
     *   "group-title" to "Kids",
     *   "tvg-logo" to "url/to/logo"
     *)
     * ```
     */
    private fun String.getAttributes(): Map<String, String> {
        val extInfRegex = Regex("(#EXTINF:.?[0-9]+)", RegexOption.IGNORE_CASE)
        val attributesString = replace(extInfRegex, "").replaceQuotesAndTrim().split(",").first()
        return attributesString.split(Regex("\\s")).mapNotNull {
            val pair = it.split("=")
            if (pair.size == 2) pair.first() to pair.last()
                .replaceQuotesAndTrim() else null
        }.toMap()
    }

    /**
     * Get value from a tag.
     *
     * Example:-
     *
     * Input:
     * ```
     * #EXTVLCOPT:http-referrer=http://example.com/
     * ```
     * Result: http://example.com/
     */
    private fun String.getTagValue(key: String): String? {
        val keyRegex = Regex("$key=(.*)", RegexOption.IGNORE_CASE)
        return keyRegex.find(this)?.groups?.get(1)?.value?.replaceQuotesAndTrim()
    }

    companion object {
        const val EXT_M3U = "#EXTM3U"
        const val EXT_INF = "#EXTINF"
        const val EXT_VLC_OPT = "#EXTVLCOPT"
    }

}

/**
 * Exception thrown when an error occurs while parsing playlist.
 */
sealed class PlaylistParserException(message: String) : Exception(message) {

    /**
     * Exception thrown if given file content is not valid.
     */
    class InvalidHeader :
        PlaylistParserException("Invalid file header. Header doesn't start with #EXTM3U")

}