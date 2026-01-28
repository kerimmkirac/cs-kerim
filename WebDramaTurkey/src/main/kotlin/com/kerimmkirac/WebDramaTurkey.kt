
package com.kerimmkirac

import com.lagradost.api.Log
import org.jsoup.nodes.Element
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer

class WebDramaTurkey : MainAPI() {
    override var mainUrl              = "https://webdramaturkey2.com"
    override var name                 = "WebDramaTurkey"
    override val hasMainPage          = true
    override var lang                 = "tr"
    override val hasQuickSearch       = false
    override val supportedTypes       = setOf(TvType.AsianDrama)
    override val mainPage = mainPageOf(
        "${mainUrl}/" to "Son Bölümler",
        "${mainUrl}/diziler"             to "Diziler",
        "${mainUrl}/filmler"             to "Filmler",
        
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val document = app.get("${request.data}${if (request.data == mainUrl + "/") "" else "?page=$page"}").document
        
        val home = if (request.data == mainUrl + "/") {
           
            document.select("div.col.sonyuklemeler").mapNotNull { it.toLatestEpisodeResult() }
        } else {
           
            document.select("div.col").mapNotNull { it.toMainPageResult() }
        }

        return newHomePageResponse(request.name, home)
    }

    private fun Element.toMainPageResult(): SearchResponse? {
        
        val title = this.selectFirst("a.list-title")?.text() ?: return null
        
        val href = fixUrlNull(this.selectFirst("a")?.attr("href")) ?: return null
        val posterUrl = fixUrlNull(this.selectFirst("div.media.media-cover")?.attr("data-src"))
        
        val tvType = if (href.contains("/film/")) {
            TvType.Movie
        } else if (href.contains("/anime/")) {
            TvType.Anime
        } else {
            TvType.AsianDrama
        }
        return newMovieSearchResponse(title, href, type = tvType) {
            this.posterUrl = posterUrl
        }
    }
    private fun Element.toLatestEpisodeResult(): SearchResponse? {
        val title = this.selectFirst("div.list-title")?.text() ?: return null
        
        val originalHref = this.selectFirst("a")?.attr("href") ?: return null
        
        
        val href = fixUrlNull(originalHref.replace(Regex("/\\d+-sezon/\\d+-bolum$"), "/"))
        
        
        var posterUrl: String? = null
        
        
        this.selectFirst("div.media.media-episode")?.attr("style")?.let { styleAttr ->
            val decodedStyle = styleAttr.replace("&quot;", "\"")
            val regex = """url\("([^"]+)"\)""".toRegex()
            posterUrl = regex.find(decodedStyle)?.groupValues?.get(1)
        }
        
        
        if (posterUrl.isNullOrEmpty()) {
            posterUrl = this.selectFirst("div.media.media-episode")?.attr("data-src")
        }
        
        
        if (posterUrl.isNullOrEmpty()) {
            this.selectFirst("div.media.media-episode")?.attr("style")?.let { style ->
                val urlRegex = """https://[^"'\s)]+\.(jpg|jpeg|png|webp)""".toRegex(RegexOption.IGNORE_CASE)
                posterUrl = urlRegex.find(style)?.value
            }
        }
        
        val episodeInfo = this.selectFirst("div.list-category")?.text() ?: ""
        
        
        val fullTitle = "$title - $episodeInfo"
        
        val tvType = if (href?.contains("/film/") == true) {
            TvType.Movie
        } else if (href?.contains("/anime/") == true) {
            TvType.Anime
        } else {
            TvType.AsianDrama
        }
        
        return newMovieSearchResponse(fullTitle, href ?: return null, type = tvType) {
            this.posterUrl = fixUrlNull(posterUrl)
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val document = app.get("${mainUrl}/arama/${query}").document

        return document.select("div.col").mapNotNull { it.toSearchResult() }
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val title     = this.selectFirst("a.list-title")?.text() ?: return null
        val href      = fixUrlNull(this.selectFirst("a")?.attr("href")) ?: return null
        val posterUrl = fixUrlNull(this.selectFirst("div.media.media-cover")?.attr("data-src"))
        val tvType    = if (href.contains("/film/")) {
            TvType.Movie
        } else if (href.contains("/anime/")){
            TvType.Anime
        }
        else {
            TvType.AsianDrama
        }

        return newTvSeriesSearchResponse(title, href, type = tvType) { this.posterUrl = posterUrl }
    }

    override suspend fun quickSearch(query: String): List<SearchResponse> = search(query)

    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url).document

        val title           = document.selectFirst("h1")?.text()?.trim() ?: return null
        val poster          = fixUrlNull(document.selectFirst("div.media.media-cover")?.attr("data-src"))
        val description     = document.selectFirst("div.text-content")?.text()?.trim()
        val movDescription  = document.selectFirst("div.video-attr:nth-child(4) > div:nth-child(2)")?.text()?.trim()
        val year            = document.selectFirst("div.featured-attr:nth-child(1) > div:nth-child(2)")?.text()?.trim()?.toIntOrNull()
        val movYear         = document.selectFirst("div.video-attr:nth-child(3) > div:nth-child(2)")?.text()?.trim()?.toIntOrNull()
        val tags            = document.select("div.categories a").map { it.text() }
        val movTags         = document.select("div.category a").map { it.text() }
        val actors          = document.select("span.valor a").map { Actor(it.text()) }
        val recommendations = document.select("div.col").mapNotNull { it.toRecommendationResult() }
        val trailer         = Regex("""embed\/(.*)\?rel""").find(document.html())?.groupValues?.get(1)?.let { "https://www.youtube.com/embed/$it" }
        val bolumler        = document.select("div.episodes a").map { bolum ->
            val bHref       = fixUrlNull(bolum.attr("href"))
            val bNum        = bolum.selectFirst("div.episode")?.text()?.substringBefore(".")?.toIntOrNull()
            val bSeason     = bHref?.substringBefore("-sezon")?.substringAfterLast("/")?.toIntOrNull()
            newEpisode(bHref, {
                this.episode = bNum
                this.season  = bSeason
                this.name    = "Bölüm"
            })
        }
        return if (url.contains("/film/")) {
         newMovieLoadResponse(title, url, TvType.Movie, url) {
            this.posterUrl       = poster
            this.plot            = movDescription
            this.year            = movYear
            this.tags            = movTags
            this.recommendations = recommendations
            addActors(actors)
            addTrailer(trailer)
        }
    } else if (url.contains("/anime/")) {
            newAnimeLoadResponse(title, url, TvType.Anime, true) {
                this.posterUrl       = poster
                this.plot            = description
                this.year            = year
                this.tags            = tags
                this.recommendations = recommendations
                this.episodes        = mutableMapOf(DubStatus.Subbed to bolumler)
                addTrailer(trailer)
            }
    }else {
            newTvSeriesLoadResponse(title, url, TvType.AsianDrama, bolumler) {
                this.posterUrl       = poster
                this.plot            = description
                this.year            = year
                this.tags            = tags
                this.recommendations = recommendations
                addTrailer(trailer)
            }
        }
    }

    private fun Element.toRecommendationResult(): SearchResponse? {
        val title     = this.selectFirst("a.list-title")?.text() ?: return null
        val href      = fixUrlNull(this.selectFirst("a")?.attr("href")) ?: return null
        val posterUrl = fixUrlNull(this.selectFirst("div.media.media-cover")?.attr("data-src"))
        val tvType    = if (href.contains("/film/")) {
            TvType.Movie
        } else if (href.contains("/anime/")){
            TvType.Anime
        }
        else {
            TvType.AsianDrama
        }

        return newMovieSearchResponse(title, href, type = tvType) { this.posterUrl = posterUrl }
    }


    override suspend fun loadLinks(
    data: String, 
    isCasting: Boolean, 
    subtitleCallback: (SubtitleFile) -> Unit, 
    callback: (ExtractorLink) -> Unit
): Boolean {
    val document = app.get(data).document
    
    val embedIds = document.select("[data-embed]")
        .mapNotNull { it.attr("data-embed") }
        .distinct()
    
    for (id in embedIds) {
        val response = app.post(
            url = "$mainUrl/ajax/embed",
            referer = data,
            headers = mapOf(
                "X-Requested-With" to "XMLHttpRequest",
                "Content-Type" to "application/x-www-form-urlencoded; charset=UTF-8",
                "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:137.0) Gecko/20100101 Firefox/137.0"
            ),
            data = mapOf("id" to id)
        ).document
        
        val iframe = response.selectFirst("iframe")?.attr("src").toString()
        val iframeGet = app.get(iframe, referer = data).document
        val iframeSon = iframeGet.selectFirst("iframe")?.attr("src")?.substringBeforeLast("#").toString()
        
        Log.d("kraptor_$name", "iframeSon = $iframeSon")
        
        
        if (iframeSon.contains("dtpasn.asia/video/")) {
            handleWebDrama(iframeSon, data, callback)
        } else {
            loadExtractor(iframeSon, "$mainUrl/", subtitleCallback, callback)
        }
    }
    return true
}

private suspend fun handleWebDrama(iframeSon: String, referer: String, callback: (ExtractorLink) -> Unit) {
    try {
        
        val videoId = iframeSon.substringAfter("dtpasn.asia/video/")
        Log.d("kerimmkirac", "WebDrama videoId = $videoId")
        
        
        val iframeResponse = app.get(
            url = iframeSon,
            referer = referer
        )
        
        
        val cookies = iframeResponse.cookies
        val fireplayerCookie = cookies["fireplayer_player"] ?: "6qgq1bmrp7gisci61s2p7edgrr"
        
        Log.d("kerimmkirac", "WebDrama cookie = $fireplayerCookie")
        
        
        val response = app.post(
            url = "https://dtpasn.asia/player/index.php?data=$videoId&do=getVideo",
            referer = iframeSon,
            headers = mapOf(
                "X-Requested-With" to "XMLHttpRequest",
                "Content-Type" to "application/x-www-form-urlencoded; charset=UTF-8",
                "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/138.0.0.0 Safari/537.36",
                "Accept" to "*/*",
                "Origin" to "https://dtpasn.asia",
                "Sec-Fetch-Site" to "same-origin",
                "Sec-Fetch-Mode" to "cors",
                "Sec-Fetch-Dest" to "empty",
                "Cookie" to "fireplayer_player=$fireplayerCookie"
            )
        )
        
        val jsonResponse = response.parsedSafe<WebDramaResponse>()
        
        jsonResponse?.let { webDrama ->
            
            webDrama.videoSource?.let { videoSource ->
                callback.invoke(
                    newExtractorLink(
                        "WDT 1",
                        "WDT 1",
                        videoSource,
                        type = if (videoSource.contains(".m3u8") || videoSource.contains(".txt")) ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO
                    ) {
                        this.referer = "https://dtpasn.asia/"
                        this.headers = mapOf(
                            "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/138.0.0.0 Safari/537.36",
                            "Accept" to "*/*",
                            "Sec-Ch-Ua" to "\"Not)A;Brand\";v=\"8\", \"Chromium\";v=\"138\", \"Brave\";v=\"138\"",
                            "Sec-Ch-Ua-Mobile" to "?0",
                            "Sec-Ch-Ua-Platform" to "\"Windows\"",
                            "Sec-Fetch-Site" to "same-origin",
                            "Sec-Fetch-Mode" to "cors",
                            "Sec-Fetch-Dest" to "empty",
                            "Accept-Language" to "tr-TR,tr;q=0.6",
                            "Accept-Encoding" to "gzip, deflate, br, zstd",
                            "Cookie" to "fireplayer_player=$fireplayerCookie"
                        )
                    }
                )
            }
            
            
            webDrama.securedLink?.let { securedLink ->
                callback.invoke(
                    newExtractorLink(
                        "WDT 2",
                        "WDT 2",
                        securedLink,
                        type = ExtractorLinkType.M3U8
                    ) {
                        this.referer = "https://dtpasn.asia/"
                        this.headers = mapOf(
                            "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/138.0.0.0 Safari/537.36",
                            "Accept" to "*/*",
                            "Sec-Ch-Ua" to "\"Not)A;Brand\";v=\"8\", \"Chromium\";v=\"138\", \"Brave\";v=\"138\"",
                            "Sec-Ch-Ua-Mobile" to "?0",
                            "Sec-Ch-Ua-Platform" to "\"Windows\"",
                            "Sec-Fetch-Site" to "same-origin",
                            "Sec-Fetch-Mode" to "cors",
                            "Sec-Fetch-Dest" to "empty",
                            "Accept-Language" to "tr-TR,tr;q=0.6",
                            "Accept-Encoding" to "gzip, deflate, br, zstd",
                            "Cookie" to "fireplayer_player=$fireplayerCookie"
                        )
                    }
                )
            }
        }
        
    } catch (e: Exception) {
        Log.e("kerimmkirac", "WebDrama error: ${e.message}")
    }
}


data class WebDramaResponse(
    val hls: Boolean? = null,
    val videoImage: String? = null,
    val videoSource: String? = null,
    val securedLink: String? = null,
    val downloadLinks: List<String>? = null,
    val attachmentLinks: List<String>? = null,
    val ck: String? = null
)}
