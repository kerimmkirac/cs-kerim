package com.kerimmkirac

import com.lagradost.api.Log
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.newExtractorLink
import java.net.URI
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

open class WebDramauns : ExtractorApi() {
    override var name = "WebDramaTurkey"
    override var mainUrl = "https://webdrama.upns.online"
    override var requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val baseurl = getBaseUrl(url)
        val id = url.substringAfter("id=").substringBefore("&")
        val videoApiUrl = "$baseurl/api/v1/video?id=$id&w=1920&h=1080&r=webdramaturkey2.com"

        val headers = mapOf(
            "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:147.0) Gecko/20100101 Firefox/147.0",
            "Referer" to "$baseurl/",
            "Accept" to "*/*"
        )

        try {
            val response = app.get(videoApiUrl, headers = headers)
            val encoded = response.text.trim().replace("\"", "")

            if (encoded.isNotEmpty() && !encoded.startsWith("<")) {
                val key = "kiemtienmua911ca"
                val decryptedText = AesHelper.decryptAESCBC(encoded, key)

                if (decryptedText.isNotEmpty()) {
                    val sourceUrl = Regex("""(?:"source":\s*")([^"]+)""").find(decryptedText)
                        ?.groupValues?.get(1)
                        ?.replace("\\/", "/") ?: ""

                    if (sourceUrl.isNotEmpty()) {
                        val link = newExtractorLink(
                            source = this.name,
                            name = this.name,
                            url = sourceUrl,
                            type = ExtractorLinkType.M3U8
                        ) {
                            this.referer = "$baseurl/"
                            this.quality = Qualities.P1080.value
                        }
                        callback.invoke(link)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("VidstackLog", "Hata: ${e.message}")
        }
    }

    private fun getBaseUrl(url: String): String {
        return try {
            val uri = URI(url)
            "${uri.scheme}://${uri.host}"
        } catch (e: Exception) {
            "https://webdrama.upns.online"
        }
    }
}

object AesHelper {
    private const val CBC_TRANSFORMATION = "AES/CBC/PKCS5Padding"

    fun decryptAESCBC(inputHex: String, key: String): String {
        val cipher = Cipher.getInstance(CBC_TRANSFORMATION)
        val secretKey = SecretKeySpec(key.toByteArray(Charsets.UTF_8), "AES")
        val iv = IvParameterSpec(ByteArray(16))
        cipher.init(Cipher.DECRYPT_MODE, secretKey, iv)
        val decryptedBytes = cipher.doFinal(inputHex.hexToByteArray())
        return String(decryptedBytes, Charsets.UTF_8)
    }

    private fun String.hexToByteArray(): ByteArray {
        val len = length
        val data = ByteArray(len / 2)
        var i = 0
        while (i < len) {
            data[i / 2] = ((Character.digit(this[i], 16) shl 4) + Character.digit(this[i + 1], 16)).toByte()
            i += 2
        }
        return data
    }
}

class WebDramaPlayerP2P : WebDramauns() {
    override var name = "WebDramaPlayerP2P"
    override var mainUrl = "https://webdrama.playerp2p.com"
}