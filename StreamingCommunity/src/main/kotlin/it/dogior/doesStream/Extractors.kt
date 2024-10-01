package it.dogior.doesStream


import android.util.Log
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.Qualities
import okhttp3.HttpUrl.Companion.toHttpUrl

class StreamingCommunityExtractor : ExtractorApi() {
    override val mainUrl = StreamingCommunity.mainUrl
    override val name = StreamingCommunity.name
    override val requiresReferer = false

    override suspend fun getUrl(
            url: String,
            referer: String?,
            subtitleCallback: (SubtitleFile) -> Unit,
            callback: (ExtractorLink) -> Unit
    ) {
        val TAG = "StreamingCommunityExtractor:getUrl"
        Log.d(TAG,"REFERER: $referer  URL: $url")

        if (url.isNotEmpty()) {
            val playlistUrl = getPlaylistLink(url)
            callback.invoke(
                ExtractorLink(
                    source = "Vixcloud",
                    name = "Streaming Community ITA",
                    url = playlistUrl,
                    referer = referer!!,
                    isM3u8 = true,
                    quality = Qualities.Unknown.value
                )
            )
        }

    }

    suspend fun getPlaylistLink(url: String): String {
        val TAG = "StreamingCommunityExtractor:getPlaylistLink"

        Log.d(TAG, url)
        val iframeUrl = app.get(url).document
            .select("iframe").attr("src")

        Log.w(TAG, "IFRAME URL: $iframeUrl")
        val headers = mapOf(
            "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8",
            "Host" to iframeUrl.toHttpUrl().host,
            "Sec-Fetch-Dest" to "iframe",
            "Sec-Fetch-Mode" to "navigate",
            "Sec-Fetch-Site" to "cross-site",
        )

        val iframe = app.get(iframeUrl, headers = headers).document
        Log.d(TAG, "TEST: ${iframe.body()}")

        val script =
            iframe.selectFirst("script:containsData(masterPlaylist)")!!.data().replace("\\", "")
                .replace("\n", "").replace("\t", "")
//        val windowVideo = script.substringAfter("= ").substringBefore(";")
        val windowStreams = script.substringAfter("window.streams = ")
            .substringBefore(";        window.masterPlaylist = ")
        val windowMasterPlaylist = script.substringAfter("window.masterPlaylist = ")
            .substringBefore("        window.canPlayFHD")
//        val windowCanPlayFHD = script.substringAfter("window.canPlayFHD = ")
        Log.d(TAG, "SCRIPT: $script")

        val servers = parseJson<List<Server>>(windowStreams)
        Log.d(TAG, "Server List: $servers")

        // Hopefully different streams will have the same format errors
        val mP = windowMasterPlaylist
            .replace("params", "'params'")
            .replace("url", "'url'")
            .replace("'", "\"")
            .replace(" ", "")
            .replace(",}", "}")
        Log.d(TAG, "windowMasterPlaylist: $mP")

        val masterPlaylist = parseJson<MasterPlaylist>(mP)
        Log.d(TAG, "MasterPlaylist Obj: $masterPlaylist")

        val masterPlaylistUrl = "${masterPlaylist.url}&token=${masterPlaylist.params.token}&expires=${masterPlaylist.params.expires}&h=1"
        return masterPlaylistUrl
    }

}