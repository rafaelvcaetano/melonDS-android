package me.magnum.melonds.ui.cheats.ui.item

import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow

/** Displays text with case-insensitive occurrences of [query] visually highlighted. */
@Composable
fun HighlightedText(
    text: String,
    query: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colors.onSurface,
    style: TextStyle = MaterialTheme.typography.body1,
    maxLines: Int = Int.MAX_VALUE,
) {
    val highlightStyle = SpanStyle(
        background = MaterialTheme.colors.secondary.copy(alpha = 0.25f),
        fontWeight = FontWeight.SemiBold,
    )
    val annotatedText = buildAnnotatedString {
        if (query.isBlank()) {
            append(text)
        } else {
            var start = 0
            while (start < text.length) {
                val matchStart = text.indexOf(query, startIndex = start, ignoreCase = true)
                if (matchStart < 0) {
                    append(text.substring(start))
                    break
                }
                append(text.substring(start, matchStart))
                pushStyle(highlightStyle)
                append(text.substring(matchStart, matchStart + query.length))
                pop()
                start = matchStart + query.length
            }
        }
    }

    Text(
        text = annotatedText,
        modifier = modifier,
        color = color,
        style = style,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
    )
}
