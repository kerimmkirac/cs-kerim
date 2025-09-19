
package com.kerimmkirac

import android.util.Log
import org.jsoup.nodes.Element
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import org.json.JSONObject
import java.net.URI

class DiziYo : MainAPI() {
    override var mainUrl              = "https://www.diziyo14.net"
    override var name                 = "DiziYo"
    override val hasMainPage          = true
    override var lang                 = "tr"
    override val hasQuickSearch       = false
    override val supportedTypes       = setOf(TvType.TvSeries, TvType.Movie, TvType.Anime)

    override val mainPage = mainPageOf(
        "${mainUrl}/filmdil/turkce-dublaj-film-izle/?sf_paged="       to "Turkce Dublaj Filmler",
        "${mainUrl}/filmdil/turkce-altyazi-film-izle/?sf_paged="      to "Turkce Altyazili Filmler",
        "${mainUrl}/dil/turkce-altyazi-dizi-izle/page"                to "Turkce Altyazili Diziler",
        "${mainUrl}/dil/turkce-dublaj-dizi-izle/page"                 to "Turkce Dublaj Diziler",
        "${mainUrl}/tur/netflix-orijinal-dizileri-izle/?sf_paged="    to "Netflix Dizileri",
        "${mainUrl}/dil/turkce-altyazi-anime-izle/page"               to "Anime",
        "${mainUrl}/dil/yerli-dizi-izle/page"                         to "Yerli Diziler",
        "${mainUrl}/dil/turkce-altyazi-asya-dizi-izle/page"           to "Asya Dizileri",
        
    )

    private fun buildPaginatedUrl(baseUrl: String, page: Int): String {
        val uri = URI(baseUrl)

        // Query parametrelerini kontrol et
        val queryParams = uri.query?.split("&")?.associate {
            val parts = it.split("=")
            parts.first() to parts.drop(1).joinToString("=")
        }?.toMutableMap() ?: mutableMapOf()

        return if (queryParams.containsKey("sf_paged")) {
            // SF_PAGED parametreli URL
            queryParams["sf_paged"] = page.toString()
            val newQuery = queryParams.entries.joinToString("&") { "${it.key}=${it.value}" }
            "${uri.scheme}://${uri.host}${uri.path}?$newQuery"
        } else {
            // PAGE parametreli URL
            val path = uri.path.trimEnd('/')
            val newPath = "$path$page/"
            "${uri.scheme}://${uri.host}$newPath${uri.query?.let { "?$it" } ?: ""}"
        }
    }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val nextPageUrl = buildPaginatedUrl(request.data, page)
        val document = app.get(nextPageUrl).document
        val home     = document.select("div.items article").mapNotNull { it.toMainPageResult() }

        return newHomePageResponse(request.name, home)
    }

    private fun Element.toMainPageResult(): SearchResponse? {
        val title     = this.selectFirst("div.data")?.text() ?: return null
        val titleTemiz = title
            .replace(" (Anime)","")
            .replace("(Türkçe Dublaj)","\uD83C\uDDF9\uD83C\uDDF7")
            .replace("Türkçe Dublaj izle","\uD83C\uDDF9\uD83C\uDDF7")
            .replace("Türkçe Dublaj","\uD83C\uDDF9\uD83C\uDDF7")
            .replace("(Asya Dizi)","⛩\uFE0F")
            .replace("izle","")
        val href      = fixUrlNull(this.selectFirst("a")?.attr("href")) ?: return null
        val posterUrl = fixUrlNull(
            this.selectFirst("img")?.let { img ->
                img.attr("data-wpfc-original-src").takeIf { it.isNotBlank() }
                    ?: img.attr("src").takeIf { it.isNotBlank() }
            }
        )
        return newMovieSearchResponse(titleTemiz, href, TvType.Movie) { this.posterUrl = posterUrl }
    }




    override suspend fun search(query: String): List<SearchResponse> {
        val document = app.get("${mainUrl}/?s=${query}").document
        return document.select("div.result-item article").mapNotNull { it.toSearchResult() }
    }


    private fun Element.toSearchResult(): SearchResponse? {
        val title     = this.selectFirst("div.title")?.text() ?: return null
        val titleTemiz = title
            .replace(" (Anime)","")
            .replace("(Türkçe Dublaj)","\uD83C\uDDF9\uD83C\uDDF7")
            .replace("Türkçe Dublaj izle","\uD83C\uDDF9\uD83C\uDDF7")
            .replace("Türkçe Dublaj","\uD83C\uDDF9\uD83C\uDDF7")
            .replace("(Asya Dizi)","⛩\uFE0F")
            .replace("izle","")
        val href      = fixUrlNull(this.selectFirst("a")?.attr("href")) ?: return null
        val posterUrl = fixUrlNull(
            this.selectFirst("img")?.let { img ->
                img.attr("data-wpfc-original-src").takeIf { it.isNotBlank() }
                    ?: img.attr("src").takeIf { it.isNotBlank() }
            }
        )

        return newMovieSearchResponse(titleTemiz, href, TvType.Movie) { this.posterUrl = posterUrl }
    }


    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url).document

        val title = document.selectFirst("div.data h1")?.text()?.trim() ?: return null
        val titleTemiz = title
            .replace(" (Anime)","")
            .replace("(Türkçe Dublaj)","\uD83C\uDDF9\uD83C\uDDF7")
            .replace("Türkçe Dublaj izle","\uD83C\uDDF9\uD83C\uDDF7")
            .replace("Türkçe Dublaj","\uD83C\uDDF9\uD83C\uDDF7")
            .replace("(Asya Dizi)","⛩\uFE0F")
            .replace("izle","")
        val poster = fixUrlNull(document.selectFirst("div.poster img")?.attr("data-wpfc-original-src"))
        val description = document.selectFirst("div.wp-content p")?.text()?.trim()
        val year = document.selectFirst("div.extra span.date")?.text()?.trim()?.toIntOrNull()
        val tags = document.select("div.sgeneros a").map { it.text() }
        val rating = document.selectFirst("div.extra:nth-child(5) > b:nth-child(2) > font:nth-child(2)")?.text()?.trim()
        val duration =
            document.selectFirst("div.extra span.runtime")?.text()?.split(" ")?.first()?.trim()?.toIntOrNull()
        val actors = document.select("div.persons").map { Actor(it.text()) }
        val trailerId = document.selectFirst("#trailer div.embed iframe")?.attr("data-wpfc-original-src")?.substringAfterLast("#")
                ?.substringBefore("?")
        Log.d("kerimmkirac_$name","trailerid = $trailerId")
        val trailer = "https://www.youtube.com/embed/$trailerId"
        Log.d("kerimmkirac_$name","trailer = $trailer")

        val tvType = if (title.contains("(Anime)", ignoreCase = true)) {
            TvType.Anime
        } else if (document.selectFirst("div#episodes h2")?.text()?.contains(Regex("Sezon ve Bölümler")) == true) {
            TvType.TvSeries
        } else {
            TvType.Movie
        }

        return if (tvType == TvType.TvSeries) {
            val episodes      = document.select("div#episodes ul li").map { bolumler ->
                val bolumHref = bolumler.selectFirst("a")?.attr("href")
                val sezon     = bolumHref?.substringBefore("-sezon")?.substringAfterLast("-")?.toIntOrNull()
                val bolum     = bolumHref?.substringBefore("-bolum")?.substringAfterLast("-")?.toIntOrNull()
                newEpisode(fixUrlNull(bolumHref)) {
                    this.name = bolumler.selectFirst("div.episodiotitle a")?.text()
                    this.posterUrl = bolumler.selectFirst("img")?.attr("data-wpfc-original-src")
                    this.season  = sezon
                    this.episode = bolum
                }
            }
            newTvSeriesLoadResponse(titleTemiz, url, TvType.TvSeries, episodes) {
                this.posterUrl = poster
                this.plot = description
                this.year = year
                this.tags = tags
                this.score = Score.from10(rating)
                addActors(actors)
                addTrailer(trailer)

            }
        } else if (tvType == TvType.Anime) {
            val episodes      = document.select("div#episodes ul li").map { bolumler ->
                val bolumHref = bolumler.selectFirst("a")?.attr("href")
                val sezon     = bolumHref?.substringBefore("-sezon")?.substringAfterLast("-")?.toIntOrNull()
                val bolum     = bolumHref?.substringBefore("-bolum")?.substringAfterLast("-")?.toIntOrNull()
                newEpisode(fixUrlNull(bolumHref)) {
                    this.name = bolumler.selectFirst("div.episodiotitle a")?.text()
                    this.posterUrl = bolumler.selectFirst("img")?.attr("data-wpfc-original-src")
                    this.season  = sezon
                    this.episode = bolum
                }
            }.let { list ->
                mutableMapOf(DubStatus.Subbed to list)
            }
            newAnimeLoadResponse(titleTemiz, url, TvType.Anime, true) {
                this.posterUrl = poster
                this.episodes = episodes
                this.plot = description
                this.year = year
                this.tags = tags
                this.score = Score.from10(rating)
                addTrailer(trailer)

            }
        } else {
            newMovieLoadResponse(titleTemiz, url, TvType.Movie, url) {
                this.posterUrl = poster
                this.plot = description
                this.year = year
                this.tags = tags
                this.score = Score.from10(rating)
                this.duration = duration
                addActors(actors)
                addTrailer(trailer)
            }
        }
    }

    override suspend fun loadLinks(data: String, isCasting: Boolean, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit): Boolean {
        val isteklink = data
        val istekdata = app.get(isteklink).document
        val regex = Regex("postid-(\\d+)")
        val match = regex.find(istekdata.toString())
        val id = match?.groupValues?.get(1)
        val ajaxLink = "https://www.diziyo14.net/wp-admin/admin-ajax.php"
        val postData = mapOf(
            "action" to "doo_player_ajax",
            "post" to "$id",
            "nume" to "DiziYO",
            "type" to "tv"
        )
        val bakalim = app.post(url = ajaxLink, data = postData).document
        val iframeRegex = Regex("""<iframe[^>]+src="([^"]+)"""")
        val iframeMatch = iframeRegex.find(bakalim.toString())
        val linkimiz = iframeMatch?.groupValues?.get(1)
        val hash = linkimiz?.substringAfter("video/")
        Log.d("dzyo", "hash = $hash")
        Log.d("dzyo", "linkimiz = $linkimiz")
        val tamLink = "$linkimiz?do=getVideo"
        val postNew = mapOf(
            "hash" to hash!!,
            "r" to "https://www.diziyo14.net/"
        )
        var headers = mapOf("Referer" to "$linkimiz,", "X-Requested-With" to "XMLHttpRequest")
        val m3u8 = app.post(url = tamLink, data = postNew, headers = headers, referer = linkimiz).document
        val jsonText = m3u8.select("body").text()

// JSON'ı parse et
        val jsonObject = JSONObject(jsonText)
        val videoSources = jsonObject.getJSONArray("videoSources")
        val firstSource = videoSources.getJSONObject(0)
        val masterLink = firstSource.getString("file")
        val msheader = mapOf("Accept" to "*/*", "Origin" to "https://www.dzyco.site", "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:101.0) Gecko/20100101 Firefox/101.0")
        val masterOriginal = app.get(url = masterLink, referer = "https://www.dzyco.site/", headers = msheader).toString()
        val baseUrl = masterLink.substringBefore("/cdn/")
        val lines = masterOriginal.lines()

        val streamInfos = mutableListOf<Pair<String, String>>() // Pair<resolution, .txt yolu>

        for (i in lines.indices) {
            val line = lines[i]
            if (line.startsWith("#EXT-X-STREAM-INF") && i + 1 < lines.size) {
                val resolutionMatch = Regex("RESOLUTION=(\\d+x\\d+)").find(line)
                val resolution = resolutionMatch?.groupValues?.get(1) ?: "Bilinmeyen"

                val nextLine = lines[i + 1]
                if (nextLine.endsWith(".txt")) {
                    streamInfos.add(Pair(resolution, nextLine))
                }
            }
        }
        streamInfos.forEach { (resolution, relativePath) ->
            val url = "$baseUrl$relativePath"
            callback.invoke(
                newExtractorLink(
                    source  = "diziyo",
                    name    = "diziyo",
                    url     = url,
                    type    = ExtractorLinkType.M3U8
                ) {
                    headers = mapOf("Referer" to "https://www.dzyco.site/")
                    quality = when (resolution) {
                        "1920x1080" -> Qualities.P1080.value
                        "1280x720"  -> Qualities.P720.value
                        "842x480", "854x480" -> Qualities.P480.value
                        "640x360"   -> Qualities.P360.value
                        "426x240"   -> Qualities.P240.value
                        else        -> Qualities.Unknown.value
                    }
                }
            )
        }

        return true
    }
}