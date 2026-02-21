package com.kerimmkirac

import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.*
import android.util.Log

open class WebDramaTurkeyExtractor : ExtractorApi() {
    override val name = "WebDramaTurkey"
    override val mainUrl = "https://dtpasn.asia"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val TAG = "AYZEN_DEBUG"
        val videoId = url.substringAfter("dtpasn.asia/video/").substringBefore("?").substringBefore("/")

        Log.d(TAG, "Extractor Baslatildi ID: $videoId")

        val iframeResponse = app.get(url, referer = referer)
        val fireplayerCookie = iframeResponse.cookies["fireplayer_player"] ?: "6qgq1bmrp7gisci61s2p7edgrr"

        Log.d(TAG, "Cookie Alindi: $fireplayerCookie")

        val response = app.post(
            url = "https://dtpasn.asia/player/index.php?data=$videoId&do=getVideo",
            referer = url,
            headers = mapOf(
                "X-Requested-With" to "XMLHttpRequest",
                "Content-Type" to "application/x-www-form-urlencoded; charset=UTF-8",
                "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/138.0.0.0 Safari/537.36",
                "Accept" to "*/*",
                "Origin" to "https://dtpasn.asia",
                "Cookie" to "fireplayer_player=$fireplayerCookie"
            )
        )

        Log.d(TAG, "API Yaniti: ${response.text.take(150)}")
        val webDrama = response.parsedSafe<WebDramaResponse>()

        webDrama?.videoSource?.let { videoSource ->
            Log.d(TAG, "WDT 1 Eklendi: $videoSource")
            callback.invoke(
                newExtractorLink(
                    "WDT 1",
                    "WDT 1",
                    videoSource,
                    type = if (videoSource.contains(".m3u8") || videoSource.contains(".txt")) ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO
                ) {
                    this.referer = "https://dtpasn.asia/"
                    this.headers = mapOf(
                        "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36",
                        "Cookie" to "fireplayer_player=$fireplayerCookie",
                        "Origin" to "https://dtpasn.asia"
                    )
                }
            )
        }

        webDrama?.securedLink?.let { securedLink ->
            Log.d(TAG, "WDT 2 Eklendi: $securedLink")
            callback.invoke(
                newExtractorLink(
                    "WDT 2",
                    "WDT 2",
                    securedLink,
                    type = ExtractorLinkType.M3U8
                ) {
                    this.referer = "https://dtpasn.asia/"
                    this.headers = mapOf(
                        "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36",
                        "Cookie" to "fireplayer_player=$fireplayerCookie",
                        "Origin" to "https://dtpasn.asia"
                    )
                }
            )
        }
    }

    data class WebDramaResponse(
        val videoSource: String? = null,
        val securedLink: String? = null
    )
}