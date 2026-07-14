package io.github.sekademi.spotufi.data.entity

data class AlbumsModel(
    val id : Int,
    val artists : String,
    val coverUri : String,
    val name : String,
    val time : String,
    // Release kind from Spotify: "album", "single", "compilation"... Empty when unknown.
    val type : String = ""
){
    constructor() : this( -1,"" ,"" ,"", "")
}
