package io.github.sekademi.spotufi.di

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.ui.graphics.Color
import androidx.palette.graphics.Palette
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.request.target
import coil3.toBitmap

class Palette {

    fun extractFirstColorFromImageUrl(context: Context, imageUrl: String, onColorExtracted: (Color) -> Unit) {
        val request = ImageRequest.Builder(context)
            .data(imageUrl)
            .allowHardware(false)
            .target(
                onSuccess = { result ->
                    val bitmap = result.toBitmap()
                    Palette.from(bitmap).generate { palette ->
                        val dominantColor = palette?.darkVibrantSwatch?.rgb
                        dominantColor?.let {
                            val argbColor = Color(it or (0xFF shl 24))
                            onColorExtracted(argbColor)
                        }
                    }
                }
            )
            .build()
        context.imageLoader.enqueue(request)
    }

    fun extractSecondColorFromCoverUrl(context: Context, imageUrl: String, onColorExtracted: (Color) -> Unit) {
        val request = ImageRequest.Builder(context)
            .data(imageUrl)
            .allowHardware(false)
            .target(
                onSuccess = { result ->
                    val bitmap = result.toBitmap()
                    val scaledBitmap = Bitmap.createScaledBitmap(bitmap, 50, 50, true)
                    Palette.from(scaledBitmap).generate { palette ->
                        val lightVibrantColor = palette?.mutedSwatch?.rgb
                        lightVibrantColor?.let {
                            val argbColor = Color(it or (0xFF shl 24))
                            onColorExtracted(argbColor)
                        }
                    }
                }
            )
            .build()
        context.imageLoader.enqueue(request)
    }
}
