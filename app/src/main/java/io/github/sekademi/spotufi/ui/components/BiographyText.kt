package io.github.sekademi.spotufi.ui.components

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import io.github.sekademi.spotufi.ui.theme.SpotifyGreen
import io.github.sekademi.spotufi.ui.theme.SpotifyMix

@Composable
fun BiographyText(
    html: String,
    onLinkClick: (uri: String, displayText: String) -> Unit,
    modifier: Modifier = Modifier,
    textColor: Color = Color.Gray,
    fontSize: TextUnit = 13.sp,
    linkColor: Color = SpotifyGreen,
) {
    val parsed = remember(html) { BiographyParser.parse(html) }

    if (parsed.links.isEmpty()) {
        Text(
            text = parsed.text,
            color = textColor,
            fontSize = fontSize,
            modifier = modifier,
        )
        return
    }

    val annotatedString = remember(parsed) {
        buildAnnotatedString {
            append(parsed.text)
            for (link in parsed.links) {
                addStyle(
                    SpanStyle(
                        color = linkColor,
                        textDecoration = TextDecoration.Underline,
                    ),
                    link.start,
                    link.end,
                )
                addStringAnnotation("link", link.uri, link.start, link.end)
            }
        }
    }

    var textLayoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }

    Text(
        text = annotatedString,
        style = TextStyle(
            color = textColor,
            fontSize = fontSize,
            fontFamily = SpotifyMix,
        ),
        modifier = modifier.pointerInput(textLayoutResult) {
            detectTapGestures { offset ->
                textLayoutResult?.let { layout ->
                    val charOffset = layout.getOffsetForPosition(offset)
                    annotatedString.getStringAnnotations("link", charOffset, charOffset)
                        .firstOrNull()
                        ?.let { annotation ->
                            val uri = annotation.item
                            val displayText = parsed.links
                                .firstOrNull { it.uri == uri }
                                ?.displayText
                                ?: ""
                            onLinkClick(uri, displayText)
                        }
                }
            }
        },
        onTextLayout = { textLayoutResult = it },
    )
}
