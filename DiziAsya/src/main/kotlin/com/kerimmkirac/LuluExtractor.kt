package com.kerimmkirac

import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.INFER_TYPE
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.newExtractorLink

open class LuluuExtractor : ExtractorApi() {
    override var name = "LuluStream"
    override var mainUrl = "https://luluvdo.com"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val filecode = url.substringAfterLast("/")
        val postUrl = "$mainUrl/dl"

        val post = app.post(
            postUrl,
            data = mapOf(
                "op" to "embed",
                "file_code" to filecode,
                "auto" to "1",
                "referer" to (referer ?: "")
            )
        ).document

        val script = post.selectFirst("script:containsData(vplayer)")?.data()
        if (script != null) {
            val match = Regex("""file:"(.*?)"""").find(script)
            val link = match?.groupValues?.get(1)

            if (link != null) {
                callback.invoke(
                    newExtractorLink(
                        name,
                        name,
                        link,
                        INFER_TYPE
                    ) {
                        this.referer = mainUrl
                        this.quality = Qualities.P1080.value
                    }
                )
            }
        }
    }
}
