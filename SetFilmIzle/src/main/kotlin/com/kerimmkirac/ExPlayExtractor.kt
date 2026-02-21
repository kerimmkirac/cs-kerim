package com.kerimmkirac

import android.util.Log
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.*

open class ExPlay : ExtractorApi() {
    override val name            = "ExPlay"
    override val mainUrl         = "https://fastplay.mom"
    override val requiresReferer = true
    private val TAG = "ExPlay_Log"

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val cleanUrl = url.substringBefore("?partKey=")
        val partKey  = url.substringAfter("?partKey=", "").uppercase()

        val isFastPlay = cleanUrl.contains("fastplay")
        val targetReferer = if (isFastPlay) "https://fastplay.mom/" else "https://setplay.shop/"
        val targetOrigin  = if (isFastPlay) "https://fastplay.mom" else "https://setplay.shop"

        val headersMap = mapOf(
            "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/133.0.0.0 Safari/537.36",
            "Accept" to "*/*",
            "Origin" to targetOrigin,
            "Referer" to targetReferer,
            "Sec-Fetch-Dest" to "empty",
            "Sec-Fetch-Mode" to "cors",
            "Sec-Fetch-Site" to "cross-site"
        )

        try {
            var masterUrl: String = if (isFastPlay) {
                val videoId = cleanUrl.split("/").last { it.isNotEmpty() }
                "https://fastplay.mom/manifests/$videoId/master.txt"
            } else {
                val response = app.get(cleanUrl, referer = referer)
                val iSource  = response.text
                val vUrl    = Regex("""videoUrl":"([^",]+)""").find(iSource)?.groupValues?.get(1)?.replace("\\", "")
                val vServer = Regex("""videoServer":"([^",]+)""").find(iSource)?.groupValues?.get(1)
                if (vUrl != null) "https://explay.store$vUrl?s=$vServer" else return
            }

            // PNG -> TS değişimini m3u8'i player çekmeden önce sunucu tarafında halletmek için
            // URL'ye sahte bir parametre ekleyelim ki cache varsa patlasın
            val finalUrl = if (masterUrl.contains("?")) "$masterUrl&type=m3u8" else "$masterUrl?type=m3u8"

            Log.d(TAG, "Giden URL: $finalUrl")

            // Base64 yerine doğrudan URL veriyoruz ama initializer içinde headers'ı çakıyoruz
            callback.invoke(
                newExtractorLink(
                    source = this.name,
                    name   = "${this.name} $partKey",
                    url    = finalUrl,
                    type   = ExtractorLinkType.M3U8
                ) {
                    this.referer = targetReferer
                    this.headers = headersMap // Bu kısım .ts dosyaları için hayati önemde
                }
            )

        } catch (e: Exception) {
            Log.e(TAG, "Hata: ${e.message}")
        }
    }
}