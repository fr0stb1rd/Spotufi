package io.github.sekademi.spotufi.ui.components

import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.TextNode

data class LinkSpan(
    val start: Int,
    val end: Int,
    val uri: String,
    val displayText: String,
)

data class ParsedBiography(
    val text: String,
    val links: List<LinkSpan>,
)

object BiographyParser {

    fun parse(html: String): ParsedBiography {
        val doc = Jsoup.parse(html)
        val sb = StringBuilder()
        val links = mutableListOf<LinkSpan>()

        fun walk(nodes: List<org.jsoup.nodes.Node>) {
            for (node in nodes) {
                when (node) {
                    is TextNode -> sb.append(node.text())
                    is Element -> when (node.tagName()) {
                        "a" -> {
                            val start = sb.length
                            val text = node.text()
                            sb.append(text)
                            val href = node.attr("abs:href").ifBlank { node.attr("href") }
                            if (href.isNotBlank()) {
                                links.add(
                                    LinkSpan(
                                        start = start,
                                        end = sb.length,
                                        uri = href,
                                        displayText = text,
                                    ),
                                )
                            }
                        }
                        "br" -> sb.append('\n')
                        else -> walk(node.childNodes())
                    }
                }
            }
        }

        walk(doc.body().childNodes())

        return ParsedBiography(
            text = sb.toString(),
            links = links,
        )
    }
}
