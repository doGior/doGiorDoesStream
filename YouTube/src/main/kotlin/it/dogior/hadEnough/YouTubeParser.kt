package it.dogior.hadEnough

import com.lagradost.api.Log
import com.lagradost.cloudstream3.Episode
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.LoadResponse.Companion.addDuration
import com.lagradost.cloudstream3.MovieLoadResponse
import com.lagradost.cloudstream3.MovieSearchResponse
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.TvSeriesLoadResponse
import com.lagradost.cloudstream3.TvSeriesSearchResponse
import com.lagradost.cloudstream3.TvType
import org.schabi.newpipe.extractor.InfoItem.InfoType
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.channel.ChannelInfo
import org.schabi.newpipe.extractor.channel.tabs.ChannelTabInfo
import org.schabi.newpipe.extractor.exceptions.ContentNotAvailableException
import org.schabi.newpipe.extractor.exceptions.ExtractionException
import org.schabi.newpipe.extractor.linkhandler.SearchQueryHandler
import org.schabi.newpipe.extractor.playlist.PlaylistInfo
import org.schabi.newpipe.extractor.search.SearchInfo
import org.schabi.newpipe.extractor.services.youtube.extractors.YoutubeTrendingExtractor
import org.schabi.newpipe.extractor.stream.StreamInfo

class YouTubeParser(private val apiName: String) {
    fun getTrendingVideoUrls(): List<SearchResponse> {
        val service = ServiceList.YouTube

//        Log.d("YouTubeParser", "Localization: ${service.localization}")
        val kiosks = service.kioskList
        val extractor = kiosks.defaultKioskExtractor as YoutubeTrendingExtractor
        extractor.fetchPage()

        val videos = extractor.initialPage.items.mapNotNull {
            if (!it.isShortFormContent) {
                videoToSearchResponse(it.url)
            } else {
                null
            }
        }
        return videos
    }

    fun search(
        query: String,
        contentFilter: String = "videos",
    ): List<SearchResponse> {
        val handlerFactory = ServiceList.YouTube.searchQHFactory

        val searchHandler = handlerFactory.fromQuery(
            query,
            listOf(contentFilter),
            null
        )
        Log.d("YouTubeParser", "Content filters: ${handlerFactory.availableContentFilter.toList()}")

        val searchInfo = SearchInfo.getInfo(ServiceList.YouTube, SearchQueryHandler(searchHandler))

        val resultSize = searchInfo.relatedItems.size
        Log.d("YouTubeParser", "Result size: $resultSize")
        if (resultSize <= 0) {
            return emptyList()
        }
        val pageResults = searchInfo.relatedItems.mapNotNull {
            Log.d("YouTubeParser", "Related: ${it.name}, type: ${it.infoType}")
            when (it.infoType) {
                InfoType.PLAYLIST, InfoType.CHANNEL -> {
                    TvSeriesSearchResponse(
                        name = it.name,
                        url = it.url,
                        posterUrl = it.thumbnails[0].url,
                        apiName = apiName
                    )
                }

                InfoType.STREAM -> {
                    MovieSearchResponse(
                        name = it.name,
                        url = it.url,
                        posterUrl = it.thumbnails[0].url,
                        apiName = apiName
                    )
                }

                else -> {
                    null
                }
            }
        }
        return pageResults
    }

    private fun videoToSearchResponse(videoUrl: String): SearchResponse? {
        try {
            val videoInfo = StreamInfo.getInfo(videoUrl)
            return MovieSearchResponse(
                name = videoInfo.name,
                url = videoUrl,
                posterUrl = videoInfo.thumbnails[0].url,
                apiName = apiName
            )
        } catch (e: ContentNotAvailableException) {
            return null
        } catch (e: ExtractionException){
            Log.d("YouTubeParser", "Error getting video info")
            throw e
        }
    }

    fun videoToLoadResponse(videoUrl: String): LoadResponse {
        val videoInfo = StreamInfo.getInfo(videoUrl)
        val views = "Views: ${videoInfo.viewCount}"
        val likes = "Likes: ${videoInfo.likeCount}"
        val length = videoInfo.duration
        return MovieLoadResponse(
            name = videoInfo.name,
            url = videoUrl,
            dataUrl = videoUrl,
            posterUrl = videoInfo.thumbnails[0].url,
            plot = videoInfo.description.content,
            type = TvType.Others,
            tags = listOf(videoInfo.uploaderName, views, likes),
            apiName = apiName
        ).apply {
            addDuration(length.toInt().toString())
        }
    }

    fun channelToLoadResponse(url: String): LoadResponse {
        val channelInfo = ChannelInfo.getInfo(url)
        val tags = mutableListOf("Subscribers: ${channelInfo.subscriberCount}")
        tags.addAll(channelInfo.tags)
        return TvSeriesLoadResponse(
            name = channelInfo.name,
            url = url,
            posterUrl = channelInfo.avatars[0].url,
            backgroundPosterUrl = channelInfo.banners[0].url,
            plot = channelInfo.description,
            type = TvType.Others,
            tags = tags,
            episodes = getChannelVideos(channelInfo),
            apiName = apiName
        )
    }

    private fun getChannelVideos(channel: ChannelInfo): List<Episode> {
        val tabsLinkHandlers = channel.tabs
        val tabs = tabsLinkHandlers.map { ChannelTabInfo.getInfo(ServiceList.YouTube, it) }
        val videoTab = tabs.first { it.name == "videos" }
        val videos = videoTab.relatedItems.mapNotNull {
            Episode(
                data = it.url,
                name = it.name,
                posterUrl = it.thumbnails[0].url
            )
        }
        return videos.reversed()
    }

    fun playlistToLoadResponse(url: String): LoadResponse {
        val playlistInfo = PlaylistInfo.getInfo(url)
        val tags = mutableListOf("Channel: ${playlistInfo.uploaderName}")
        return TvSeriesLoadResponse(
            name = playlistInfo.name,
            url = url,
            posterUrl = playlistInfo.thumbnails[0].url,
            backgroundPosterUrl = playlistInfo.banners[0].url,
            plot = playlistInfo.description.content,
            type = TvType.Others,
            tags = tags,
            episodes = getPlaylistVideos(playlistInfo),
            apiName = apiName
        )
    }

    private fun getPlaylistVideos(playlist: PlaylistInfo): List<Episode> {
        val videos = playlist.relatedItems.mapNotNull {
            Episode(
                data = it.url,
                name = it.name,
                posterUrl = it.thumbnails[0].url
            )
        }
        return videos
    }
}