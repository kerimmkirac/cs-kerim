package com.kerimmkirac

import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.newExtractorLink

open class VidMolyExtractor : ExtractorApi() {
    override val name = "VidMoly"
    override val mainUrl = "https://vidmoly.net"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val initialUrl = url.replace("vidmoly.to", "vidmoly.net")
        val headers = mapOf(
            "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:143.0) Gecko/20100101 Firefox/143.0",
            "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
        )

        val response = app.get(initialUrl, headers = headers)
        val html = response.text
        val finalUrl = response.url

        val masterRegex = Regex("""file\s*:\s*["'](https?://[^"']+?master\.m3u8[^"']*?)["']""")
        val m3u8Url = masterRegex.find(html)?.groupValues?.get(1)

        m3u8Url?.let { link ->
            callback(
                newExtractorLink(
                    source = name,
                    name = name,
                    url = link,
                    type = ExtractorLinkType.M3U8
                ) {
                    this.referer = finalUrl
                    this.quality = Qualities.Unknown.value
                }
            )
        }
    }
}

class VidmolyME : VidMolyExtractor() {
    override var mainUrl = "https://vidmoly.me"
    override val name = "VidMoly"
}

class VidmolyTO : VidMolyExtractor() {
    override var mainUrl = "https://vidmoly.to"
    override val name = "VidMoly"
}