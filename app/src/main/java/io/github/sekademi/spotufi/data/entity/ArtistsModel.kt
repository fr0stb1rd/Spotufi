package io.github.sekademi.spotufi.data.entity

data class ArtistsModel(
    val name : String,
    val coverUri : String,
    // Real Spotify artist id — open the EXACT artist (so "RAM" doesn't resolve to
    // "Rammstein" via a fuzzy name search). Empty when unknown.
    val id : String = "",
){
    constructor() : this("", "", "")
}
