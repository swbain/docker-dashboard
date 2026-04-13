package dev.dockerdashboard.ui

import androidx.compose.runtime.Composable
import com.jakewharton.mosaic.layout.background
import com.jakewharton.mosaic.layout.fillMaxWidth
import com.jakewharton.mosaic.layout.padding
import com.jakewharton.mosaic.layout.width
import com.jakewharton.mosaic.modifier.Modifier
import com.jakewharton.mosaic.text.SpanStyle
import com.jakewharton.mosaic.text.buildAnnotatedString
import com.jakewharton.mosaic.ui.Arrangement
import com.jakewharton.mosaic.ui.Column
import com.jakewharton.mosaic.ui.Row
import com.jakewharton.mosaic.ui.Text
import com.jakewharton.mosaic.ui.TextStyle

@Composable
fun LogViewer(
    logLines: List<String>,
    containerName: String,
    scrollOffset: Int,
    termWidth: Int,
    termHeight: Int,
) {
    val theme = LocalTheme.current
    Column(modifier = com.jakewharton.mosaic.modifier.Modifier.width(termWidth)) {
        Row(
            modifier = Modifier.fillMaxWidth().background(theme.barBackground).padding(horizontal = 1),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                buildAnnotatedString {
                    pushStyle(SpanStyle(color = theme.accent, textStyle = TextStyle.Bold))
                    append(containerName)
                    pop()
                    pushStyle(SpanStyle(color = theme.textSecondary))
                    append("  Logs")
                    pop()
                },
            )
            Text(
                buildAnnotatedString {
                    pushStyle(SpanStyle(color = theme.textMuted))
                    append("${logLines.size} lines")
                    pop()
                },
            )
        }

        val contentHeight = (termHeight - 2).coerceAtLeast(1)
        val safeOffset = scrollOffset.coerceIn(0, (logLines.size - 1).coerceAtLeast(0))

        for (i in 0 until contentHeight) {
            val lineIndex = safeOffset + i
            if (lineIndex < logLines.size) {
                val line = logLines[lineIndex]
                val truncated = if (line.length > termWidth) line.take(termWidth) else line
                Text(truncated, color = theme.textSecondary)
            } else {
                Text("~", color = theme.border)
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().background(theme.barBackground).padding(horizontal = 1),
        ) {
            val hints = buildAnnotatedString {
                pushStyle(SpanStyle(color = theme.accent, textStyle = TextStyle.Bold))
                append("\u2191\u2193/jk")
                pop()
                pushStyle(SpanStyle(color = theme.textSecondary))
                append(" scroll  ")
                pop()
                pushStyle(SpanStyle(color = theme.accent, textStyle = TextStyle.Bold))
                append("r")
                pop()
                pushStyle(SpanStyle(color = theme.textSecondary))
                append(" refresh  ")
                pop()
                pushStyle(SpanStyle(color = theme.accent, textStyle = TextStyle.Bold))
                append("Esc")
                pop()
                pushStyle(SpanStyle(color = theme.textSecondary))
                append(" back")
                pop()
            }
            Text(hints)
        }
    }
}
