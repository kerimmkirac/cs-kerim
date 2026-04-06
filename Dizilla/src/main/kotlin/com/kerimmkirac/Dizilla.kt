
package com.kerimmkirac

import android.content.SharedPreferences
import android.util.Base64
import com.lagradost.api.Log
import org.jsoup.nodes.Element
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.module.kotlin.readValue
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.network.CloudflareKiller
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response
import org.json.JSONObject
import org.jsoup.Jsoup
import kotlin.coroutines.cancellation.CancellationException
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

class Dizilla(sharedPref: SharedPreferences? = null) : MainAPI() {
    override var mainUrl = getDomain(sharedPref, "Dizilla")
    override var name = "Dizilla"
    override val hasMainPage = true
    override var lang = "tr"
    override val hasQuickSearch = true
    override val supportedTypes = setOf(TvType.TvSeries)
    private val cloudflareKiller by lazy { CloudflareKiller() }
    private val interceptor by lazy { CloudflareInterceptor(cloudflareKiller) }

    class CloudflareInterceptor(private val cloudflareKiller: CloudflareKiller) : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val request = chain.request()
            val response = try {
                chain.proceed(request)
            } catch (e: Exception) {
                Log.d("DIZILLA", "İstek sırasında hata: ${e.message}")
                throw e
            }

            
            val contentType = response.header("Content-Type")
            if (contentType != null && contentType.contains("text/html")) {
                val bodyString = response.peekBody(1024 * 1024).string()
                if (bodyString.contains("Just a moment") || bodyString.contains("DDoS protection")) {
                    Log.d("DIZILLA", "Cloudflare Challenge algılandı, Killer devreye giriyor.")
                    return cloudflareKiller.intercept(chain)
                }
            }

            return response
        }
    }

    companion object {
        private const val CACHE_DURATION = 5 * 60 * 1000L
        private const val LAST_UPDATE_KEY = "last_domain_update"
        private fun getDomain(sharedPref: SharedPreferences?, providerName: String): String {
            return runBlocking {
                try {
                    val lastUpdate = sharedPref?.getLong(LAST_UPDATE_KEY, 0L) ?: 0L
                    val now = System.currentTimeMillis()
                    if (now - lastUpdate < CACHE_DURATION) {
                        return@runBlocking sharedPref?.getString(providerName, "") ?: ""
                    }
                    val domainListesi =
                        app.get("https://raw.githubusercontent.com/Kraptor123/domainListesi/refs/heads/main/eklenti_domainleri.txt").text
                    sharedPref?.edit()?.apply {
                        domainListesi.split("|").filter { it.isNotBlank() }.forEach { item ->
                            val parts = item.trim().split(":", limit = 2)
                            if (parts.size == 2) {
                                putString(parts[0].trim(), parts[1].trim())
                            }
                        }
                        putLong(LAST_UPDATE_KEY, now)
                        apply()
                    }
                    sharedPref?.getString(providerName, "") ?: ""
                } catch (e: Exception) {
                    sharedPref?.getString(providerName, "") ?: ""
                }
            }
        }
    }

    private val dizillaHeaders = mapOf(
        "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Safari/537.36",
        "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8",
        "Connection" to "keep-alive",
        "Referer" to "${mainUrl}/"
    )

    override val mainPage = mainPageOf(
        "${mainUrl}/tum-bolumler" to "Yeni Eklenen Bölümler",
        "26" to "Kore Dizileri",
        "yeni" to "Yeni Diziler",

        
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        Log.d("DIZILLA", "Ana sayfa isteği: ${request.name}, Sayfa: $page")

        if (request.data.contains("tum-bolumler")) {
            val document = app.get(request.data, interceptor = interceptor).document
            val home = document.select("div.col-span-3 a").mapNotNull { it.sonBolumler() }
            return newHomePageResponse(request.name, home, false)
        }

        val raw = try {
            val apiUrl = when {
                request.name.contains("Dublaj Diziler") ->
                    "${mainUrl}/api/bg/getepisodesonbrandbetweendate?curPage=$page&curLength=100&languageId=2&typeId=0&minDate=1900-01-06&maxDate=2026-01-07"

                request.name.contains("Yeni Diziler") ->
                    "${mainUrl}/api/bg/findSeries?releaseYearStart=1900&releaseYearEnd=2026&imdbPointMin=5&imdbPointMax=10&categoryIdsComma=&countryIdsComma=&orderType=date_desc&languageId=-1&currentPage=$page&currentPageCount=24&queryStr=&categorySlugsComma=&countryCodesComma="

                request.name.contains("Anime") ->
                    "${mainUrl}/api/bg/findSeries?releaseYearStart=1900&releaseYearEnd=2026&imdbPointMin=5&imdbPointMax=10&categoryIdsComma=17&countryIdsComma=12&orderType=date_desc&languageId=-1&currentPage=$page&currentPageCount=24&queryStr=&categorySlugsComma=&countryCodesComma="
                request.name.contains("Dizileri") || request.name.contains("Yerli") ->
                    "${mainUrl}/api/bg/findSeries?releaseYearStart=1900&releaseYearEnd=2026&imdbPointMin=5&imdbPointMax=10&categoryIdsComma=&countryIdsComma=${request.data}&orderType=date_desc&languageId=-1&currentPage=$page&currentPageCount=24&queryStr=&categorySlugsComma=&countryCodesComma="
                else ->
                    "${mainUrl}/api/bg/findSeries?releaseYearStart=1900&releaseYearEnd=2026&imdbPointMin=5&imdbPointMax=10&categoryIdsComma=${request.data}&countryIdsComma=&orderType=date_desc&languageId=-1&currentPage=${page}&currentPageCount=24&queryStr=&categorySlugsComma=&countryCodesComma="
            }
            Log.d("DIZILLA", "API İsteği Gönderiliyor: $apiUrl")
            app.post(apiUrl, referer = "${mainUrl}/", headers = dizillaHeaders, interceptor = interceptor).text
        } catch (e: Exception) {
            Log.d("DIZILLA", "Hata: ${e.message}")
            return newHomePageResponse(request.name, emptyList())
        }

        val responseJson = JSONObject(raw)
        val encryptedData = responseJson.optString("response")
        if (encryptedData.isNullOrBlank()) return newHomePageResponse(request.name, emptyList())

        val decoded = decryptDizillaResponse(encryptedData) ?: return newHomePageResponse(request.name, emptyList())
        val rootNode = mapper.readTree(decoded)

        val itemsNode = when {
            rootNode.isArray -> rootNode
            rootNode.has("items") -> rootNode.get("items")
            rootNode.has("result") -> rootNode.get("result")
            rootNode.has("data") -> rootNode.get("data")
            else -> null
        }

        if (itemsNode == null || !itemsNode.isArray) {
            Log.d("DIZILLA", "Ayrıştırılabilir liste bulunamadı.")
            return newHomePageResponse(request.name, emptyList())
        }

        val items: List<DizillaJson> = mapper.convertValue(itemsNode, object : TypeReference<List<DizillaJson>>() {})
        val home = items.mapNotNull { item ->
            val slug = item.usedSlug ?: item.episodeUsedSlug ?: item.seriesUsedSlug ?: return@mapNotNull null
            newTvSeriesSearchResponse(item.infotitle ?: "Bilinmiyor", fixUrl("$mainUrl/$slug"), TvType.TvSeries) {
                this.posterUrl = dizillaPoster(item.infoposter)
                this.score = Score.from10(item.infopuan?.toString())
            }
        }

        return newHomePageResponse(request.name, home)
    }



    private fun Element.sonBolumler(): SearchResponse? {
        val name = this.selectFirst("h2")?.text() ?: return null
        val episodeInfo = this.selectFirst("div.opacity-80")?.text() ?: ""
        val posterUrl = fixUrlNull(this.selectFirst("div.image img")?.attr("src"))
        val epDocUrl = fixUrlNull(this.attr("href")) ?: return null

        return newTvSeriesSearchResponse("$name $episodeInfo".trim(), epDocUrl, TvType.TvSeries) {
            this.posterUrl = posterUrl
        }
    }


    override suspend fun search(query: String): List<SearchResponse> {
        Log.d("DIZILLA", "Arama yapılıyor: $query")
        return try {
            val response = app.post("${mainUrl}/api/bg/searchcontent?searchterm=$query", headers = dizillaHeaders, interceptor = interceptor)
            val jsonResponse = JSONObject(response.text)
            val decodedSearch = decryptDizillaResponse(jsonResponse.optString("response")) ?: return emptyList()
            val contentJson: SearchData = mapper.readValue(decodedSearch)

            contentJson.result?.map {
                newTvSeriesSearchResponse(it.title.toString(), fixUrl(it.slug.toString()), TvType.TvSeries) {
                    this.posterUrl = dizillaPoster(it.poster)
                    this.score = Score.from10(it.puan.toString())
                }
            } ?: emptyList()
        } catch (e: Exception) {
            Log.d("DIZILLA", "Arama hatası: ${e.message}")
            emptyList()
        }
    }

    override suspend fun quickSearch(query: String): List<SearchResponse> = search(query)

    override suspend fun load(url: String): LoadResponse? {
        Log.d("DIZILLA", "Yükleniyor: $url")
        val ilkIstek = app.get(url, interceptor = interceptor).document
        val document = if (url.contains("-sezon-") || url.contains("-bolum-")) {
            val anaHref = ilkIstek.select("nav ol li a[href*=/dizi/]").attr("href")
            if (anaHref.isNotBlank()) {
                val fixedAnaHref = fixUrl(anaHref)
                Log.d("DIZILLA", "Bölümden Diziye Yönlendiriliyor: $fixedAnaHref")
                app.get(fixedAnaHref, interceptor = interceptor).document
            } else {
                ilkIstek
            }
        } else {
            ilkIstek
        }

        val title = document.selectFirst("div.poster.poster h2")?.ownText() ?: return null
        val poster = dizillaPoster(document.selectFirst("div.w-full.page-top.relative img")?.attr("src"))
        val description = document.selectFirst("div.w-full.px-10")?.text()
        val rating = document.selectFirst("span.flex-col span.text-white.text-sm")?.text()

        val episodesList = mutableListOf<Episode>()
        document.select("div.flex.items-center.flex-wrap.gap-2.mb-4 a").forEach { sezon ->
            val sezonHref = fixUrl(sezon.attr("href"))
            val sDoc = app.get(sezonHref, interceptor = interceptor).document
            val sNum = sezonHref.substringBefore("-sezon").substringAfterLast("-").toIntOrNull()

            sDoc.select("div.episodes div.cursor-pointer").forEach { bolum ->
                val aTag = bolum.selectFirst("a")
                val epLinks = bolum.select("a")
                val epLink = if (epLinks.isNotEmpty()) epLinks.last()?.attr("href") else null

                if (epLink != null) {
                    episodesList.add(newEpisode(fixUrl(epLink)) {
                        this.name = aTag?.ownText()?.substringAfter(".")?.trim()
                        this.season = sNum
                        this.episode = aTag?.ownText()?.substringBefore(".")?.toIntOrNull()
                    })
                }
            }
        }
        val finalUrl = document.selectFirst("link[rel=canonical]")?.attr("href") ?: url

        return newTvSeriesLoadResponse(title, finalUrl, TvType.TvSeries, episodesList) {
            this.posterUrl = poster
            this.plot = description
            this.score = Score.from10(rating)
            addActors(document.select("div.global-box h5").map { Actor(it.ownText()) })
        }
    }

    override suspend fun loadLinks(data: String, isCasting: Boolean, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit): Boolean {
        Log.d("DIZILLA", "Linkler çekiliyor: $data")
        val response = app.get(data, interceptor = interceptor).text
        val script = Jsoup.parse(response).selectFirst("script#__NEXT_DATA__")?.data() ?: return false

        val secureData = mapper.readTree(script).get("props")?.get("pageProps")?.get("secureData")?.asText() ?: return false
        val decoded = decryptDizillaResponse(secureData) ?: return false

        val sourceContent = mapper.readTree(decoded).get("RelatedResults")?.get("getEpisodeSources")?.get("result")?.get(0)?.get("source_content")?.asText() ?: return false
        val iframe = Jsoup.parse(sourceContent).selectFirst("iframe")?.attr("src")?.replace("sn.dplayer74.site", "sn.hotlinger.com") ?: return false

        return loadExtractor(fixUrl(iframe), "${mainUrl}/", subtitleCallback, callback)
    }
}

@JsonIgnoreProperties(ignoreUnknown = true)
data class DizillaJson(
    @JsonProperty("original_title") val infotitle: String?,
    @JsonProperty("poster_url") val infoposter: String?,
    @JsonProperty("used_slug") val usedSlug: String?,
    @JsonProperty("episode_used_slug") val episodeUsedSlug: String?,
    @JsonProperty("series_used_slug") val seriesUsedSlug: String?,
    @JsonProperty("imdb_point") val infopuan: Double?
)

private const val privateAESKey = "9bYMCNQiWsXIYFWYAu7EkdsSbmGBTyUI"

private fun decryptDizillaResponse(response: String): String? {
    if (response.isBlank()) return null
    return try {
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(privateAESKey.toByteArray(), "AES"), IvParameterSpec(ByteArray(16)))
        String(cipher.doFinal(Base64.decode(response, Base64.DEFAULT)))
    } catch (e: Exception) {
        Log.d("DIZILLA", "AES deşifre hatası.")
        null
    }
}

fun dizillaPoster(poster: String?): String? {
    if (poster == null) return null
    return poster.replace("images-macellan-online.cdn.ampproject.org/i/s/", "")
        .replace("file.dizilla.club", "file.macellan.online")
        .replace("images.dizilla.club", "images.macellan.online")
        .replace("/f/f/", "/630/910/")
}
