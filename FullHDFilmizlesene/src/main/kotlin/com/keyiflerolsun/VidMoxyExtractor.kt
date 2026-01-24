package com.keyiflerolsun

import com.lagradost.api.Log
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.getAndUnpack
import com.lagradost.cloudstream3.utils.newExtractorLink

open class VidMoxy : ExtractorApi() {
    override val name = "VidMoxy"
    override val mainUrl = "https://vidmoxy.com"
    override val requiresReferer = true

    private val TAG = "kraptor_VidMoxy"

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        Log.i(TAG, "URL: $url")
        val response = app.get(url, referer = referer ?: "")
        val videoReq = response.document

        val script = videoReq.select("script:containsData(eval)")

        script.forEach { got ->
            val am = got.data()
            val unpack = getAndUnpack(am)
            Log.i(TAG, "unpack: $unpack")
            if (unpack.contains("eval(function")) {
                val tekrar = getAndUnpack(unpack)
                Log.i(TAG, "tekrar: $tekrar")
                val rawFileMatch = Regex("""file":"(.*?)"""").find(tekrar)
                val rawFile = rawFileMatch?.groupValues?.get(1)

                if (rawFile != null) {
                    val decodedUrl = rawFile.replace(Regex("""\\+x([0-9a-fA-F]{2})""")) {
                        it.groupValues[1].toInt(16).toChar().toString()
                    }
                    callback.invoke(newExtractorLink(
                        this.name,
                        this.name,
                        decodedUrl,
                        type = ExtractorLinkType.M3U8,
                        {
                            this.referer = "${mainUrl}/"
                        }
                    ))
                }
            }
        }
    }
}