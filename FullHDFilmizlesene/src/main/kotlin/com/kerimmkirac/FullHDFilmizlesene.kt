package com.kerimmkirac

import android.util.Base64
import com.lagradost.api.Log
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import kotlin.collections.iterator


class FullHDFilmizlesene : MainAPI() {
    override var mainUrl = "https://www.fullhdfilmizlesene.tv"
    override var name = "FullHDFilmizlesene"
    override val hasMainPage = true
    override var lang = "tr"
    override val hasQuickSearch = false
    override val hasDownloadSupport = false
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)

    override val mainPage = mainPageOf(
        "${mainUrl}/yeni-filmler" to "Son Eklenenler",
        "${mainUrl}/filmizle/aile-filmleri-hdf-izle" to "Aile Filmleri",
        "${mainUrl}/filmizle/aksiyon-filmleri-hdf-izle" to "Aksiyon Filmleri",
        "${mainUrl}/filmizle/animasyon-filmleri-izle" to "Animasyon Filmler",
        
        "${mainUrl}/filmizle/bilim-kurgu-filmleri-izle-2" to "Bilim Kurgu Filmleri",
        "${mainUrl}/filmizle/bluray-filmler-izle" to "Blu Ray Filmler",
        "${mainUrl}/filmizle/cizgi-filmler-fhd-izle" to "Çizgi Filmler",
        "${mainUrl}/filmizle/dram-filmleri-hd-izle" to "Dram Filmleri",
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val url = if (page == 1) request.data else "${request.data.removeSuffix("/")}/$page"
        val doc = app.get(url).document
        val isLatest = request.name == "Son Eklenenler"
        val results = doc.select("li.film").mapNotNull { it.toSearchResult(isLatest) }
        return newHomePageResponse(request.name, results, hasNext = doc.selectFirst("a.ileri") != null)
    }

    private fun Element.toSearchResult(isLatest: Boolean = false): SearchResponse? {
        val title = selectFirst(if (isLatest) "h2.film-tt span.film-title" else "span.film-title")?.text() ?: return null
        val href  = fixUrlNull(selectFirst(if (isLatest) "a.tt" else "a")?.attr("href")) ?: return null
        val img   = selectFirst(if (isLatest) "img.afis" else "img")
        val poster = fixUrlNull(img?.attr("data-src")?.takeIf { it.isNotBlank() } ?: img?.attr("src"))

        val filmCount = selectFirst("span.film-cnt")?.text()?.trim()
        val isSeries  = !filmCount.isNullOrBlank() || href.contains("/serifilm/")
        val name = if (!filmCount.isNullOrBlank()) "$title [$filmCount]" else title

        return if (isSeries) {
            newTvSeriesSearchResponse(name, href, TvType.TvSeries) { this.posterUrl = poster }
        } else {
            newMovieSearchResponse(name, href, TvType.Movie) {
                this.posterUrl = poster
                this.score = Score.from10(selectFirst("span.imdb")?.text())
            }
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val doc = app.get("${mainUrl}/arama/${query}").document
        return doc.select("li.film").mapNotNull { it.toSearchResult() }
    }

    override suspend fun quickSearch(query: String): List<SearchResponse> = search(query)

    override suspend fun load(url: String): LoadResponse? {
        val doc = app.get(url).document
        val isSeries = url.contains("/serifilm/")

        val titleRaw = doc.selectFirst("div.izle-titles")?.text()?.trim()
            ?: doc.selectFirst("h1.blok-baslik-seri")?.text()?.replace(" izle", "")?.trim()
            ?: doc.selectFirst("title")?.text()?.split("-")?.firstOrNull()?.trim() ?: return null

        val filmCount = doc.selectFirst("div.seri-ust-content p")?.text()?.let { Regex("""\d+""").find(it)?.value }
        val displayTitle = if (filmCount != null) "$titleRaw [$filmCount]" else titleRaw

        val poster = fixUrlNull(doc.selectFirst("img.ic-poster, div.poster img, div img")?.attr("data-src"))
        val description = doc.select("div.seri-ust-content p, div.ozet-ic > p").eachText().joinToString("\n\n").ifBlank { "Açıklama bulunamadı." }
        val year = doc.selectFirst("div.dd a.category")?.text()?.trim()?.split(" ")?.firstOrNull()?.toIntOrNull()
        val rating = doc.selectFirst("div.puanx-puan")?.text()?.trim()?.split(" ")?.lastOrNull()

        val recommendations = doc.select("div.benzerlist li.film").mapNotNull { el ->
            val name = el.selectFirst(".film-title")?.text() ?: return@mapNotNull null
            val href = el.selectFirst("a.tt")?.attr("href")?.let { if (it.startsWith("http")) it else fixUrl(it) } ?: return@mapNotNull null
            val img = el.selectFirst("source, img")?.let { it.attr("data-srcset").ifBlank { it.attr("src") } }
            val pstr = fixUrlNull(img?.split(",")?.firstOrNull()?.trim()?.split(" ")?.firstOrNull())

            newMovieSearchResponse(name, href, TvType.Movie) { this.posterUrl = pstr }
        }

        if (isSeries) {
            val episodes = doc.select("ul.list li.film").mapNotNull {
                val eHref = fixUrlNull(it.selectFirst("a.tt")?.attr("href")) ?: return@mapNotNull null
                val eName = it.selectFirst("span.film-title")?.text() ?: it.selectFirst("a.tt")?.text() ?: "Film"
                val eImg = fixUrlNull(it.selectFirst("img")?.let { img -> img.attr("data-src").ifBlank { img.attr("src") } })

                newEpisode(eHref) {
                    this.name = eName.trim()
                    this.posterUrl = eImg
                    this.description = it.select("div.seri-alt-content p").eachText().joinToString("\n\n")
                }
            }
            return newTvSeriesLoadResponse(displayTitle, url, TvType.TvSeries, episodes) {
                this.posterUrl = poster; this.plot = description; this.score = Score.from10(rating); this.recommendations = recommendations
            }
        }

        return newMovieLoadResponse(displayTitle, url, TvType.Movie, url) {
            this.posterUrl = poster; this.year = year; this.plot = description; this.score = Score.from10(rating); this.recommendations = recommendations
        }
    }

    override suspend fun loadLinks(data: String, isCasting: Boolean, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit): Boolean {
        Log.d("FHD", "loadLinks Başlatıldı - Data: $data")
        val document = app.get(data).document
        val videoLinks = getVideoLinks(document)
        Log.d("FHD", "getVideoLinks Sonucu: $videoLinks")

        if (videoLinks.isEmpty()) return false

        for (videoMap in videoLinks) {
            for ((key, value) in videoMap) {
                val individualLinks = value.split(",")
                for (link in individualLinks) {
                    val videoUrl = fixUrlNull(link.trim()) ?: continue
                    Log.d("FHD", "İşlenen Link ($key): $videoUrl")
                    if (videoUrl.contains("turbo.imgz.me")) {
                        loadExtractor("${key}||${videoUrl}", "${mainUrl}/", subtitleCallback, callback)
                    } else {
                        loadExtractor(videoUrl, "${mainUrl}/", subtitleCallback, callback)
                    }
                }
            }
        }
        return true
    }

    private fun getVideoLinks(document: Document): List<Map<String, String>> {
        val scriptContent = document.select("script").map { it.data() }.firstOrNull { it.contains("scx =") } ?: return emptyList()
        val scxData =Regex("""scx\s*=\s*(\{.*?\});""").find(scriptContent)?.groupValues?.get(1) ?: return emptyList()

        return try {
            val scxMap: SCXData = jacksonObjectMapper().readValue(scxData)
            val keys = listOf("atom", "advid", "advidprox", "proton", "fast", "fastly", "tr", "en")
            val linkList = mutableListOf<Map<String, String>>()

            for (key in keys) {
                val t = when (key) {
                    "atom" -> scxMap.atom?.sx?.t
                    "advid" -> scxMap.advid?.sx?.t
                    "advidprox" -> scxMap.advidprox?.sx?.t
                    "proton" -> scxMap.proton?.sx?.t
                    "fast" -> scxMap.fast?.sx?.t
                    "fastly" -> scxMap.fastly?.sx?.t
                    "tr" -> scxMap.tr?.sx?.t
                    "en" -> scxMap.en?.sx?.t
                    else -> null
                }
                if (t != null) {
                    when (t) {
                        is List<*> -> {
                            val links = t.filterIsInstance<String>().map { atob(rtt(it)).trim() }
                            linkList.add(mapOf(key to links.joinToString(",")))
                        }
                        is Map<*, *> -> {
                            val links = t.mapValues { (_, v) -> if (v is String) atob(rtt(v)).trim() else "" }
                            val safeLinks = links.mapKeys { (k, _) -> k?.toString() ?: "Unknown" }
                            linkList.add(safeLinks)
                        }
                    }
                }
            }
            linkList
        } catch (e: Exception) {
            Log.e("FHD", "JSON Parse Hatası: ${e.message}")
            emptyList()
        }
    }

    private fun atob(s: String): String = String(Base64.decode(s, Base64.DEFAULT))

    private fun rtt(s: String): String {
        return s.map { c ->
            when (c) {
                in 'a'..'z' -> ((c - 'a' + 13) % 26 + 'a'.code).toChar()
                in 'A'..'Z' -> ((c - 'A' + 13) % 26 + 'A'.code).toChar()
                else -> c
            }
        }.joinToString("")
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class SCXData(
        @JsonProperty("atom") val atom: AtomData? = null,
        @JsonProperty("advid") val advid: AtomData? = null,
        @JsonProperty("advidprox") val advidprox: AtomData? = null,
        @JsonProperty("proton") val proton: AtomData? = null,
        @JsonProperty("fast") val fast: AtomData? = null,
        @JsonProperty("fastly") val fastly: AtomData? = null,
        @JsonProperty("tr") val tr: AtomData? = null,
        @JsonProperty("en") val en: AtomData? = null,
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class AtomData(@JsonProperty("sx") var sx: SXData)

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class SXData(@JsonProperty("t") var t: Any)
}