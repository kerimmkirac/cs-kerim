version = 2


cloudstream {
    authors     = listOf("kerimmkirac")

    language    = "tr"
    description = "Her Dizi Bir Macera, Her An Bir Keşif!"

    /**
     * Status int as the following:
     * 0: Down
     * 1: Ok
     * 2: Slow
     * 3: Beta only
    **/
    status  = 1 // will be 3 if unspecified
    tvTypes = listOf("TvSeries", "Movie")
    iconUrl = "https://dizifun2.com/images/data/favicon.png"
}