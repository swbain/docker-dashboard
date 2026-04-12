package dev.dockerdashboard.ui

import androidx.compose.runtime.Composable
import com.jakewharton.mosaic.layout.DrawScope
import com.jakewharton.mosaic.layout.DrawStyle
import com.jakewharton.mosaic.layout.drawBehind
import com.jakewharton.mosaic.layout.height
import com.jakewharton.mosaic.layout.padding
import com.jakewharton.mosaic.layout.width
import com.jakewharton.mosaic.modifier.Modifier
import com.jakewharton.mosaic.text.SpanStyle
import com.jakewharton.mosaic.text.buildAnnotatedString
import com.jakewharton.mosaic.ui.Box
import com.jakewharton.mosaic.ui.Color
import com.jakewharton.mosaic.ui.Column
import com.jakewharton.mosaic.ui.Text
import com.jakewharton.mosaic.ui.TextStyle
import dev.dockerdashboard.model.ContainerInfo
import dev.dockerdashboard.model.ContainerState

private val GRAY = Color(100, 100, 100)
private val LIGHT_GRAY = Color(160, 160, 160)

@Composable
fun ContainerCard(
    container: ContainerInfo,
    isSelected: Boolean,
    cardWidth: Int,
    modifier: Modifier = Modifier,
) {
    val borderColor = when {
        isSelected -> Color.Cyan
        container.updateAvailable -> Color.Yellow
        else -> GRAY
    }
    val stateColor = when (container.state) {
        ContainerState.RUNNING -> Color.Green
        ContainerState.PAUSED -> Color.Yellow
        ContainerState.EXITED, ContainerState.DEAD -> Color.Red
        ContainerState.RESTARTING -> Color.Cyan
        ContainerState.CREATED -> Color.Blue
        else -> LIGHT_GRAY
    }

    Box(
        modifier = modifier
            .width(cardWidth)
            .height(CARD_HEIGHT)
            .drawBehind { drawBorder(borderColor) }
            .padding(left = 2, right = 2, top = 1, bottom = 1)
    ) {
        Column {
            // Container name + update arrow
            val nameText = buildAnnotatedString {
                append(container.name.take(cardWidth - if (container.updateAvailable) 6 else 4))
                if (container.updateAvailable) {
                    append(" ")
                    pushStyle(SpanStyle(color = Color.Yellow, textStyle = TextStyle.Bold))
                    append("↑")
                    pop()
                }
            }
            Text(
                nameText,
                color = if (isSelected) Color.White else LIGHT_GRAY,
                textStyle = TextStyle.Bold,
            )

            // Image
            val contentWidth = cardWidth - 4
            Text(container.image.take(contentWidth))

            // Version digest for "latest" tagged images
            if (isLatestTag(container.image) && container.localDigest != null) {
                val versionText = buildAnnotatedString {
                    pushStyle(SpanStyle(color = Color(100, 180, 255)))
                    append(shortDigest(container.localDigest))
                    pop()
                    if (container.updateAvailable && container.remoteDigest != null) {
                        pushStyle(SpanStyle(color = Color.Yellow))
                        append(" \u2192 ")
                        pop()
                        pushStyle(SpanStyle(color = Color.Green))
                        append(shortDigest(container.remoteDigest))
                        pop()
                    }
                }
                Text(versionText)
            }

            // Status
            Text(container.status.take(cardWidth - 4), color = stateColor)

            // Ports
            if (container.ports.isNotEmpty()) {
                Text(container.ports.take(cardWidth - 4), color = GRAY)
            }

            // Resources (running containers only)
            if (container.state == ContainerState.RUNNING && container.memoryLimitMb > 0) {
                val resourceText = buildAnnotatedString {
                    pushStyle(SpanStyle(color = LIGHT_GRAY))
                    append("CPU: ")
                    pop()
                    pushStyle(SpanStyle(color = Color.White))
                    append(String.format("%.1f%%", container.cpuPercent))
                    pop()
                    append("  ")
                    pushStyle(SpanStyle(color = LIGHT_GRAY))
                    append("MEM: ")
                    pop()
                    pushStyle(SpanStyle(color = Color.White))
                    append(
                        String.format(
                            "%.0f/%.0fMB",
                            container.memoryUsageMb,
                            container.memoryLimitMb
                        )
                    )
                    pop()
                }
                Text(resourceText)
            }
        }
    }
}

private fun DrawScope.drawBorder(color: Color) {
    val w = width
    val h = height
    if (w < 2 || h < 2) return

    // Top-left corner
    drawText(0, 0, "┌", foreground = color)
    // Top-right corner
    drawText(0, w - 1, "┐", foreground = color)
    // Bottom-left corner
    drawText(h - 1, 0, "└", foreground = color)
    // Bottom-right corner
    drawText(h - 1, w - 1, "┘", foreground = color)

    // Top and bottom edges
    for (col in 1 until w - 1) {
        drawText(0, col, "─", foreground = color)
        drawText(h - 1, col, "─", foreground = color)
    }

    // Left and right edges
    for (row in 1 until h - 1) {
        drawText(row, 0, "│", foreground = color)
        drawText(row, w - 1, "│", foreground = color)
    }
}

private fun isLatestTag(image: String): Boolean {
    val lastColon = image.lastIndexOf(':')
    val lastSlash = image.lastIndexOf('/')
    if (lastColon <= lastSlash || lastColon <= 0) return true
    return image.substring(lastColon + 1) == "latest"
}

private fun shortDigest(digest: String): String {
    return digest.substringAfter("sha256:", digest).take(8)
}

const val CARD_HEIGHT = 9
const val MIN_CARD_WIDTH = 36
