// ! Bu araç @Kraptor123 tarafından | @kekikanime için yazılmıştır.
package com.kraptor

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.BasePlugin

@CloudstreamPlugin
class WebDramaTurkeyPlugin: BasePlugin() {
    override fun load() {
        registerMainAPI(WebDramaTurkey())
    }
}