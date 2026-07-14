package io.github.sekademi.spotufi.data.entity

data class SongsModel(
    val id : Int,
    val title : String,
    val album : String,
    val singer : String,
    val coverUri : String,
    val url : String,
    val spotifyTrackId : String = "",
    val explicit : Boolean = false,
    val durationMs : Int = 0,
    val artistIds : String = "",
){
    constructor() : this(-1 ,"" ,"" ,"" ,"" ,"", "")
}
