package com.kerimmkirac

import com.lagradost.cloudstream3.ErrorLoadingException
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.*
import java.net.URI

open class SetPlay : ExtractorApi() {
    override val name            = "SetPlay"
    override val mainUrl         = "https://setplay.shop"
    override val requiresReferer = true

    override suspend fun getUrl(url: String, referer: String?, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit) {
        val iSource = app.get(url, referer = referer ?: "").text

        val videoUrl    = Regex("""videoUrl":"([^",]+)""").find(iSource)?.groupValues?.get(1) ?: throw ErrorLoadingException("videoUrl not found")
        val videoServer = Regex("""videoServer":"([^",]+)""").find(iSource)?.groupValues?.get(1) ?: throw ErrorLoadingException("videoServer not found")
        val m3uLink     = "${mainUrl}${videoUrl.replace("\\", "")}?s=${videoServer}"

        val partKey = try {
            val uri = URI(url)
            uri.query?.split("&")
                ?.find { it.startsWith("partKey=") }
                ?.substringAfter("partKey=") ?: ""
        } catch (e: Exception) {
            ""
        }

        val displayName = when {
            partKey.contains("turkcedublaj", ignoreCase = true) -> "${this.name} - Dublaj"
            partKey.contains("turkcealtyazi", ignoreCase = true) -> "${this.name} - Altyazı"
            else -> this.name
        }

        callback.invoke(
            newExtractorLink(
                source  = displayName,
                name    = displayName,
                url     = m3uLink,
                type    = ExtractorLinkType.M3U8
            ) {
                headers = mapOf("Referer" to url)
                quality = Qualities.Unknown.value
            }
        )
    }
}