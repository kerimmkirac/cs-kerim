
version = 1


cloudstream {
    authors     = listOf("kerimmkirac")

    language    = "tr"
    description = "Film izle, ⚡ En yeni çıkan yabancı vizyon filmleri 4KFilmizleme ile Online 1080p kalite veya Ultra Full HD 4K Film izle."

    /**
     * Status int as the following:
     * 0: Down
     * 1: Ok
     * 2: Slow
     * 3: Beta only
    **/
    status  = 1 // will be 3 if unspecified
    tvTypes = listOf("Movie")
    iconUrl = "https://www.4kfilmizleme.net/wp-content/uploads/2022/09/4kfilmizlemefavicon-1.png"
}