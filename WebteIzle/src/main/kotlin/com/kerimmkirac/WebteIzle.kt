package com.kerimmkirac

import com.lagradost.api.Log
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.network.CloudflareKiller
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import okhttp3.FormBody
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.Jsoup
import org.jsoup.nodes.Element

class WebteIzle : MainAPI() {
    override var mainUrl              = "https://webteizle3.xyz"
    override var name                 = "WebteIzle"
    override val hasMainPage          = true
    override var lang                 = "tr"
    override val hasQuickSearch       = false
    override val supportedTypes       = setOf(TvType.Movie)

    // ! CloudFlare bypass
    override var sequentialMainPage = true        // * https://recloudstream.github.io/dokka/-cloudstream/com.lagradost.cloudstream3/-main-a-p-i/index.html#-2049735995%2FProperties%2F101969414
    override var sequentialMainPageDelay       = 50L  // ? 0.05 saniye
    override var sequentialMainPageScrollDelay = 50L  // ? 0.05 saniye
    private val client = OkHttpClient()


    // ! CloudFlare v2
    private val cloudflareKiller by lazy { CloudflareKiller() }
    private val interceptor      by lazy { CloudflareInterceptor(cloudflareKiller) }

    class CloudflareInterceptor(private val cloudflareKiller: CloudflareKiller): Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val request  = chain.request()
            val response = chain.proceed(request)
            val doc      = Jsoup.parse(response.peekBody(1024 * 1024).string())

            if (doc.text().contains("Just a moment")) {
                return cloudflareKiller.intercept(chain)
            }

            return response
        }
    }

    override val mainPage = mainPageOf(
        "${mainUrl}/film-izle/"                   to "Güncel",
        "${mainUrl}/yeni-filmler/"                to "Yeni",
        "${mainUrl}/tavsiye-filmler/"             to "Tavsiye",
        "${mainUrl}/filtre/SAYFA?tur=Aile"        to "Aile",
        "${mainUrl}/filtre/SAYFA?tur=Aksiyon"     to "Aksiyon",
        "${mainUrl}/filtre/SAYFA?tur=Animasyon"   to "Animasyon",
        "${mainUrl}/filtre/SAYFA?tur=Belgesel"    to "Belgesel",
        "${mainUrl}/filtre/SAYFA?tur=Bilim-Kurgu" to "Bilim Kurgu",
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val url      = if ("SAYFA" in request.data) request.data.replace("SAYFA", "$page") else "${request.data}$page"
        val document = app.get(url, interceptor = interceptor).document
        val home     = document.select("div.card.golgever").mapNotNull { it.toSearchResult() }

        return newHomePageResponse(request.name, home)
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val title     = this.selectFirst("div.filmname")?.text() ?: return null
        val href      = fixUrlNull(this.selectFirst("a")?.attr("href")) ?: return null
        val posterUrl = fixUrlNull(this.selectFirst("img")?.attr("data-src"))
        val score     = this.selectFirst("span.imdb")?.text()?.replace(",",".")?.toDoubleOrNull()
        val year      = this.selectFirst("span.year")?.text()?.toIntOrNull()

        return newMovieSearchResponse(title, href, TvType.Movie) {
            this.posterUrl = posterUrl
            this.score     = Score.from10(score)
            this.year      = year
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val formBody = FormBody.Builder()
            .add("q", query)
            .build()

        val request = Request.Builder()
            .url("${mainUrl}/ajax/arama.asp")
            .header("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
            .header("X-Requested-With", "XMLHttpRequest")
            .header("Referer", "${mainUrl}/")
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:137.0) Gecko/20100101 Firefox/137.0")
            .post(formBody)
            .build()

        return client.newCall(request).execute().use { response ->
            val responseText = response.body?.string() ?: ""
            Log.d("kraptor_Webdeizle", "document = $responseText")

            val searchResults = mutableListOf<SearchResponse>()

            try {
                val aramaResponse = parseJson<Arama>(responseText)

                // Eğer response başarılıysa
                if (aramaResponse.status == "success") {
                    val results = aramaResponse.results

                    results.filmler.results.forEach { item ->
                        item.title?.let { title ->
                            searchResults.add(
                                newMovieSearchResponse(
                                    name = "$title-${item.description}",
                                    url = "${mainUrl}${item.url}",
                                    type = TvType.Movie,
                                    initializer = {
                                        posterUrl = "${mainUrl}${item.image}"
                                        quality = SearchQuality.HD
                                        this.score = Score.from10(item.imdb)
                                    }
                                )
                            )
                        }
                    }

                    results.diziler.results.forEach { item ->
                        item.title?.let { title ->
                            val finalUrl = if (item.url?.startsWith("http") == true) {
                                item.url
                            } else {
                                "${mainUrl}${item.url}"
                            }

                            val finalImage = if (item.image?.startsWith("http") == true) {
                                item.image
                            } else {
                                "${mainUrl}${item.image}"
                            }

                            searchResults.add(
                                newTvSeriesSearchResponse(
                                    name = "$title-${item.description}",
                                    url = finalUrl,
                                    type = TvType.TvSeries,
                                    initializer = {
                                        posterUrl = finalImage
                                        quality = SearchQuality.HD
                                        this.score = Score.from10(item.imdb)
                                    }
                                )
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("kraptor_Webdeizle", "JSON Parse Error: ${e.message}")
            }
            searchResults
        }
    }


    override suspend fun quickSearch(query: String): List<SearchResponse> = search(query)

    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url).document

        val title       = document.selectFirst("[property='og:title']")?.attr("content")?.substringBefore(" izle") ?: return null
        val poster      = fixUrlNull(document.selectFirst("div.card img")?.attr("data-src"))
        val year        = document.selectXpath("//td[contains(text(), 'Vizyon')]/following-sibling::td").text().trim().split(" ").last().toIntOrNull()
        val description = document.selectFirst("blockquote")?.text()?.trim()
        val tags        = document.selectXpath("//a[@itemgroup='genre']").map { it.text() }
        val rating      = document.selectFirst("div.detail")?.text()?.replace(",",".")?.trim()?.toDoubleOrNull()
        val duration    = document.selectXpath("//td[contains(text(), 'Süre')]/following-sibling::td").text().trim().split(" ").first().toIntOrNull()
        val trailer     = document.selectFirst("button#fragman")?.attr("data-ytid")
        val actors      = document.selectXpath("//div[@data-tab='oyuncular']//a").map {
            Actor(it.selectFirst("span")!!.text().trim(), fixUrlNull(it.selectFirst("img")!!.attr("data-src")))
        }

        return newMovieLoadResponse(title, url, TvType.Movie, url) {
            this.posterUrl = poster
            this.year      = year
            this.plot      = description
            this.tags      = tags
            this.score     = Score.from10(rating)
            this.duration  = duration
            addTrailer("https://www.youtube.com/embed/${trailer}")
            addActors(actors)
        }
    }

    override suspend fun loadLinks(data: String, isCasting: Boolean, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit): Boolean {
        Log.d("WBTI", "data » $data")





        val document = app.get(data).document

        val filmId  = document.selectFirst("button#wip")?.attr("data-id") ?: return false
        Log.d("WBTI", "filmId » $filmId")

        val dilList = mutableListOf<String>()
        if (document.selectFirst("div.golge a[href*=dublaj]")?.attr("src") != null) {
            dilList.add("0")
        }

        if (document.selectFirst("div.golge a[href*=altyazi]")?.attr("src") != null) {
            dilList.add("1")
        }

        dilList.forEach {
            val dilAd = if (it == "0") "Dublaj" else "Altyazı"


            val playerApi = app.post(
                "${mainUrl}/ajax/dataAlternatif3.asp",
                headers = mapOf("X-Requested-With" to "XMLHttpRequest"),
                data    = mapOf(



                    "filmid" to filmId,
                    "dil"    to it,
                    "s"      to "",
                    "b"      to "",
                    "bot"    to "0"
                )
            ).text

            val playerData = AppUtils.tryParseJson<DataAlternatif>(playerApi) ?: return@forEach

            for (thisEmbed in playerData.data) {
                val embedApi = app.post(


                    "${mainUrl}/ajax/dataEmbed.asp",
                    headers = mapOf("X-Requested-With" to "XMLHttpRequest"),
                    data    = mapOf("id" to thisEmbed.id.toString())
                ).document

                var iframe = fixUrlNull(embedApi.selectFirst("iframe")?.attr("src"))

                if (iframe == null) {
                    val scriptSource = embedApi.html()
                    val matchResult  = Regex("""(vidmoly|okru|filemoon)\('([\d\w]+)','""").find(scriptSource)

                    if (matchResult == null) {
                        Log.d("WBTI", "scriptSource » $scriptSource")
                    } else {
                        val platform = matchResult.groupValues[1]
                        val vidId    = matchResult.groupValues[2]

                        iframe       = when(platform) {
                            "vidmoly"  -> "https://vidmoly.to/embed-${vidId}.html"
                            "okru"     -> "https://odnoklassniki.ru/videoembed/${vidId}"
                            "filemoon" -> "https://filemoon.sx/e/${vidId}"
                            else       -> null
                        }
                    }
                } else if (iframe.contains(mainUrl)) {
                    Log.d("WBTI", "iframe » $iframe")
                    val iSource = app.get(iframe, referer=data).text

                    val encoded  = Regex("""file": "([^"]+)""").find(iSource)?.groupValues?.get(1) ?: continue
                    val bytes    = encoded.split("\\x").filter { str -> str.isNotEmpty() }.map { char -> char.toInt(16).toByte() }.toByteArray()
                    val m3uLink = String(bytes, Charsets.UTF_8)
                    Log.d("WBTI", "m3uLink » $m3uLink")

                    val trackStr = Regex("""tracks = \[([^]]+)""").find(iSource)?.groupValues?.get(1)
                    if (trackStr != null) {
                        val tracks:List<Track> = jacksonObjectMapper().readValue("[${trackStr}]")

                        for (track in tracks) {
                            if (track.file == null || track.label == null) continue
                            if (track.label.contains("Forced")) continue

                            subtitleCallback.invoke(
                                newSubtitleFile(
                                    lang = track.label.replace("\\u0131", "ı").replace("\\u0130", "İ").replace("\\u00fc", "ü").replace("\\u00e7", "ç"),
                                    url  = fixUrl(track.file).replace("\\", "")
                                )
                            )
                        }
                    }
                    callback.invoke(
                        newExtractorLink(
                            source  = "$dilAd - ${this.name}",
                            name    = "$dilAd - ${this.name}",
                            url     = m3uLink,
                            type = ExtractorLinkType.M3U8
                        ) {
                            headers = mapOf("Referer" to "${mainUrl}/") // "Referer" ayarı burada yapılabilir
                            quality = getQualityFromName("1440p")
                        }
                    )
                    continue
                } else if (iframe.contains("playerjs-three.vercel.app") || iframe.contains("cstkcstk.github.io")) {
                    val decoded = iframe.substringAfter("&v=").let { query ->
                        val hexString = query.replace("\\x", "")
                        val bytes     = hexString.chunked(2).map { chunk -> chunk.toInt(16).toByte() }.toByteArray()

                        bytes.toString(Charsets.UTF_8)
                    }
                    callback.invoke(
                        newExtractorLink(
                            source  = "$dilAd - ${this.name}",
                            name    = "$dilAd - ${this.name}",
                            url     = fixUrl(decoded),
                            type = ExtractorLinkType.M3U8
                        ) {
                            headers = mapOf("Referer" to "${mainUrl}/") // "Referer" ayarı burada yapılabilir
                            quality = getQualityFromName(Qualities.Unknown.value.toString())
                        }
                    )
                }
                if (iframe != null) {
                    Log.d("WBTI", "iframe » $iframe")
                    loadExtractor(iframe, "${mainUrl}/", subtitleCallback,callback)
                }
            }
        }
        return true
    }
}