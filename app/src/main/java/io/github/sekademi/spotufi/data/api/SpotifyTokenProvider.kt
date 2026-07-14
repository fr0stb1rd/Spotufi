package io.github.sekademi.spotufi.data.api

import android.content.Context
import android.util.Log
import com.metrolist.spotify.Spotify
import com.metrolist.spotify.SpotifyAuth
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Ensures the [Spotify] singleton holds a valid web access token before any
 * metadata call. Mirrors how Meld authenticates: a logged-in `sp_dc` cookie is
 * exchanged for a short-lived bearer token via [SpotifyAuth.fetchAccessToken].
 *
 * The `sp_dc` cookie is read from [SpotifySession]; paste yours there (or via
 * the in-app settings hook) to enable real Spotify data. Tokens are cached
 * until ~1 minute before expiry and refreshed on demand.
 */
object SpotifyTokenProvider {
    private const val TAG = "SpotifyTokenProvider"
    private val mutex = Mutex()
    private var expiresAtMs: Long = 0L

    suspend fun ensureToken(context: Context): Boolean = mutex.withLock {
        val now = System.currentTimeMillis()
        if (Spotify.accessToken != null && now < expiresAtMs - 60_000L) {
            return@withLock true
        }
        val spDc = SpotifySession.spDc(context)
        if (spDc.isBlank()) {
            Log.w(TAG, "No sp_dc cookie set — Spotify data unavailable")
            return@withLock false
        }
        SpotifyAuth.fetchAccessToken(spDc).fold(
            onSuccess = { token ->
                Spotify.accessToken = token.accessToken
                expiresAtMs = token.accessTokenExpirationTimestampMs
                Log.d(TAG, "Spotify token refreshed")
                true
            },
            onFailure = {
                Log.e(TAG, "Failed to fetch Spotify token", it)
                false
            },
        )
    }
}
