package com.kerimmkirac

import android.content.Context
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin

@CloudstreamPlugin
class WebDramaTurkeyPlugin: Plugin() {
    override fun load(context: Context) {
        val sharedPref = context.getSharedPreferences("DomainListesi", Context.MODE_PRIVATE)
        registerMainAPI(WebDramaTurkey(sharedPref))
        registerExtractorAPI(VkExtractor())
        registerExtractorAPI(VkCom())
        registerExtractorAPI(WebDramaTurkeyExtractor())
        registerExtractorAPI(WebDramauns())
        registerExtractorAPI(WebDramaPlayerP2P())
    }
}