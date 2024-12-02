
// use an integer for version numbers
version = 0

cloudstream {
    // All of these properties are optional, you can safely remove them

    description = "Work In Progress | Videos and playlists from YouTube"
    authors = listOf("doGior")

    /**
     * Status int as the following:
     * 0: Down
     * 1: Ok
     * 2: Slow
     * 3: Beta only
     * */
    status = 2

    tvTypes = listOf("Others")

    requiresResources = false

    iconUrl = "https://www.youtube.com/s/desktop/711fd789/img/logos/favicon_144x144.png"
}

dependencies {
    implementation("com.github.teamnewpipe:NewPipeExtractor:v0.24.2")
}