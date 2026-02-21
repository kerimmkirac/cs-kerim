package com.kerimmkirac

import android.content.Context
import com.lagradost.cloudstream3.plugins.Plugin
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin

@CloudstreamPlugin
class SetFilmIzlePlugin: Plugin() {
    override fun load(context: Context) {
        val sharedPref = context.getSharedPreferences("DomainListesi", Context.MODE_PRIVATE)
        registerMainAPI(SetFilmIzle(sharedPref))
        registerExtractorAPI(SetPlay())
        registerExtractorAPI(SetPrime())
        registerExtractorAPI(ExPlay())
    }
}