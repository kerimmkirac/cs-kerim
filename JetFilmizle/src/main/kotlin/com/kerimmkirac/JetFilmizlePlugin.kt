package com.kerimmkirac


import android.content.Context
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin

@CloudstreamPlugin
class JetFilmizlePlugin: Plugin() {
    override fun load(context: Context) {
        registerMainAPI(JetFilmizle())
        registerExtractorAPI(PixelDrain())
        registerExtractorAPI(JetTv())
    }
}