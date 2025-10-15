
version = 3


cloudstream {
    authors     = listOf("kerimmkirac")

    language    = "tr"
    description = "Film Ekseni ⚡ Vizyonda ki, en güncel ve en yeni filmleri full hd kalitesinde türkçe dublaj ve altyazı seçenekleriyle 1080p olarak izleyebileceğiniz adresiniz."

    /**
     * Status int as the following:
     * 0: Down
     * 1: Ok
     * 2: Slow
     * 3: Beta only
    **/
    status  = 1 // will be 3 if unspecified
    tvTypes = listOf("Movie")
    iconUrl = "https://t3.gstatic.com/faviconV2?client=SOCIAL&type=FAVICON&fallback_opts=TYPE,SIZE,URL&url=https://filmekseni.net/&size=128"
}
