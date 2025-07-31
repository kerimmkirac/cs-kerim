
version = 1


cloudstream {
    authors     = listOf("kerimmkirac")

    language    = "tr"
    description = "Son çıkan en yeni filmleri türkçe dublaj ve türkçe altyazılı dil seçenekleriyle Full HD (1080p) kalitelerinde film izle."

    /**
     * Status int as the following:
     * 0: Down
     * 1: Ok
     * 2: Slow
     * 3: Beta only
    **/
    status  = 1 // will be 3 if unspecified
    tvTypes = listOf("Movie", "TvSeries")
    iconUrl = "https://encrypted-tbn1.gstatic.com/images?q=tbn:ANd9GcRbK_Pe3vpG-yUVt1NvckDpaD6COLDitImrBUnHfAVA4fawhijE"
}