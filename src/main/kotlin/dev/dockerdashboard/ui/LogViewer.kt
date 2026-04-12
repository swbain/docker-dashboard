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
import com.jakewharton.mosaic.ui.Color
import com.jakewharton.mosaic.ui.Column
import com.jakewharton.mosaic.ui.Row
import com.jakewharton.mosaic.ui.Text
import com.jakewharton.mosaic.ui.TextStyle

private val BAR_BG = Color(30, 30, 30)
private val ACCENT = Color(80, 200, 255)

@Composable
fun LogViewer(
    logLines: List<String>,
    containerName: String,
    scrollOffset: Int,
    termWidth: Int,
    termHeight: Int,
) {
    Column(modifier = Modifier.width(termWidth)) {
        // Header bar
        Row(
            modifier = Modifier.fillMaxWidth().background(BAR_BG).padding(horizontal = 1),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                buildAnnotatedString {
                    pushStyle(SpanStyle(color = ACCENT, textStyle = TextStyle.Bold))
                    append(containerName)
                    pop()
                    pushStyle(SpanStyle(color = Color(160, 160, 160)))
                    append("  Logs")
                    pop()
                },
            )
            Text(
                buildAnnotatedString {
                    pushStyle(SpanStyle(color = Color(140, 140, 140)))
                    append("${logLines.size} lines")
                    pop()
                },
            )
        }

        // Log content area: termHeight minus 2 for header and footer bars
        val contentHeight = (termHeight - 2).coerceAtLeast(1)
        val safeOffset = scrollOffset.coerceIn(0, (logLines.size - 1).coerceAtLeast(0))

        for (i in 0 until contentHeight) {
            val lineIndex = safeOffset + i
            if (lineIndex < logLines.size) {
                val line = logLines[lineIndex]
                val truncated = if (line.length > termWidth) line.take(termWidth) else line
                Text(truncated, color = Color(200, 200, 200))
            } else {
                Text("~", color = Color(60, 60, 60))
            }
        }

        // Footer bar
        Row(
            modifier = Modifier.fillMaxWidth().background(BAR_BG).padding(horizontal = 1),
        ) {
            val hints = buildAnnotatedString {
                pushStyle(SpanStyle(color = ACCENT, textStyle = TextStyle.Bold))
                append("\u2191\u2193/jk")
                pop()
                pushStyle(SpanStyle(color = Color(160, 160, 160)))
                append(" scroll  ")
                pop()

                pushStyle(SpanStyle(color = ACCENT, textStyle = TextStyle.Bold))
                append("r")
                pop()
                pushStyle(SpanStyle(color = Color(160, 160, 160)))
                append(" refresh  ")
                pop()

                pushStyle(SpanStyle(color = ACCENT, textStyle = TextStyle.Bold))
                append("Esc")
                pop()
                pushStyle(SpanStyle(color = Color(160, 160, 160)))
                append(" back")
                pop()
            }
            Text(hints)
        }
    }
}
