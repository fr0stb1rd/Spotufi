package io.github.sekademi.spotufi.data.api

import android.content.Context

/**
 * Stores the logged-in Spotify `sp_dc` cookie used to mint web access tokens.
 *
 * Set it once (e.g. from a settings screen) with [setSpDc]. To get yours: log in
 * at open.spotify.com, open devtools → Application → Cookies → copy `sp_dc`.
 */
object SpotifySession {
    private const val PREFS = "spotify_session"
    private const val KEY_SP_DC = "sp_dc"

    // Optional compile-time default so the app has data on first launch.
    // Leave blank to require runtime configuration.
    private const val DEFAULT_SP_DC = ""

    fun spDc(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return prefs.getString(KEY_SP_DC, null)?.takeIf { it.isNotBlank() } ?: DEFAULT_SP_DC
    }

    fun setSpDc(context: Context, value: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_SP_DC, value.trim())
            .apply()
    }
}
