package com.kerimmkirac

import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.*
import android.util.Log
import com.lagradost.cloudstream3.network.WebViewResolver

open class VkExtractor : ExtractorApi() {
    override val name = "Vk"
    override val mainUrl = "https://vkvideo.ru"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        Log.d("VkVideo", "URL: $url")
        val commonUserAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/133.0.0.0 Safari/537.36"

        val headers = mapOf(
            "User-Agent" to commonUserAgent,
            "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
            "Referer" to "https://vk.com/",
        )

        var response = app.get(url, headers = headers)

        if (response.text.contains("hash429") || response.text.contains("challenge.html")) {
            Log.d("VkVideo", "Challenge Fallback Devrede...")
            response = app.get(url, interceptor = WebViewResolver(Regex(".*video_ext\\.php.*")), headers = headers)
        }

        val resText = response.text
        val currentCookies = response.cookies

        val listUrl = listOf("url", "dash_sep", "hls")

        listUrl.forEach { linkType ->
            if (linkType == "url") {
                val regex = Regex("\"url([0-9]+)\":\"([^\"]*)\"", RegexOption.IGNORE_CASE)
                regex.findAll(resText).forEach { urlMatch ->
                    val video = urlMatch.groupValues[2].replace("\\", "")
                    val quality = urlMatch.groupValues[1]
                    callback.invoke(
                        newExtractorLink(
                            this.name,
                            this.name,
                            video,
                            ExtractorLinkType.VIDEO
                        ) {
                            this.referer = "https://vk.com/"
                            this.quality = getQualityFromName(quality)
                            // ExoPlayer için headerları buraya ekliyoruz
                            this.headers = mapOf(
                                "User-Agent" to commonUserAgent,
                                "Referer" to "https://vk.com/"
                            )
                        }
                    )
                }
            } else {
                val regex = Regex("\"$linkType\":\"([^\"]*)\"", RegexOption.IGNORE_CASE)
                val video = regex.find(resText)?.groupValues?.getOrNull(1)?.replace("\\", "")

                if (video != null) {
                    val typeName = if (linkType.contains("hls")) "HLS" else "Dash"
                    callback.invoke(
                        newExtractorLink(
                            "${this.name} $typeName",
                            "${this.name} $typeName",
                            video,
                            if (linkType.contains("dash")) ExtractorLinkType.DASH else ExtractorLinkType.M3U8
                        ) {
                            this.referer = "https://vk.com/"
                            // 400 hatasını önlemek için User-Agent şart
                            this.headers = mapOf(
                                "User-Agent" to commonUserAgent,
                                "Referer" to "https://vk.com/"
                            )
                        }
                    )
                }
            }
        }
        Log.d("VkVideo", "Done")
    }
}

class VkCom : VkExtractor() {
    override var mainUrl = "https://vk.com"
}