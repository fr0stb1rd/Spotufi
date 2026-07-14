package io.github.sekademi.spotufi.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import io.github.sekademi.spotufi.R

/**
 * The genuine Spotify fonts, extracted from the official app.
 * SpotifyMixUI — body / UI text. SpotifyMixUITitle — headings (heavier display cut).
 * Medium/SemiBold don't exist as cuts, so they map to the nearest available weight.
 */
val SpotifyMix = FontFamily(
    Font(R.font.spotify_mix_ui_regular, weight = FontWeight.Normal),
    Font(R.font.spotify_mix_ui_regular, weight = FontWeight.Medium),
    Font(R.font.spotify_mix_ui_bold, weight = FontWeight.SemiBold),
    Font(R.font.spotify_mix_ui_bold, weight = FontWeight.Bold),
)

val SpotifyMixTitle = FontFamily(
    Font(R.font.spotify_mix_ui_title_bold, weight = FontWeight.Bold),
    Font(R.font.spotify_mix_ui_title_extrabold, weight = FontWeight.Black),
)

// Kept as an alias so existing references (Theme.kt etc.) don't break.
val Montserrat = SpotifyMix

private val base = TextStyle(fontFamily = SpotifyMix)
private val title = TextStyle(fontFamily = SpotifyMixTitle, fontWeight = FontWeight.Bold)

val Typography = Typography(
    displayLarge = title, displayMedium = title, displaySmall = title,
    headlineLarge = title, headlineMedium = title, headlineSmall = title,
    titleLarge = base.copy(fontWeight = FontWeight.Bold),
    titleMedium = base.copy(fontWeight = FontWeight.Bold),
    titleSmall = base.copy(fontWeight = FontWeight.Bold),
    bodyLarge = base.copy(fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = base, bodySmall = base,
    labelLarge = base, labelMedium = base, labelSmall = base,
)
