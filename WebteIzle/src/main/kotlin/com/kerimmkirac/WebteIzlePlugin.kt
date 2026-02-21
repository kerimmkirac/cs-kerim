package com.kerimmkirac

import com.lagradost.cloudstream3.extractors.FileMoon
import com.lagradost.cloudstream3.extractors.Vidmoly
import com.lagradost.cloudstream3.extractors.Vidmolyme
import com.lagradost.cloudstream3.plugins.BasePlugin
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin

@CloudstreamPlugin
class WebteIzlePlugin: BasePlugin() {
    override fun load() {
        registerMainAPI(WebteIzle())
        registerExtractorAPI(com.kerimmkirac.FileMoon())
        registerExtractorAPI(Bysezoxexe())
        registerExtractorAPI(FilemoonV2())
        registerExtractorAPI(FileMoonIn())
        registerExtractorAPI(FileMoonSx())
        registerExtractorAPI(VidMolyExtractor())
        registerExtractorAPI(VidmolyTO())
        registerExtractorAPI(VidmolyME())
    }
}