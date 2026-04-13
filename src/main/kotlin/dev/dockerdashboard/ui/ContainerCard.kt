package dev.dockerdashboard.ui

import androidx.compose.runtime.Composable
import com.jakewharton.mosaic.layout.DrawScope
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
import com.jakewharton.mosaic.ui.Row
import com.jakewharton.mosaic.ui.Text
import com.jakewharton.mosaic.ui.TextStyle
import dev.dockerdashboard.model.ActiveOperation
import dev.dockerdashboard.model.ContainerInfo
import dev.dockerdashboard.model.ContainerState
import dev.dockerdashboard.model.StatsSnapshot

@Composable
fun ContainerCard(
    container: ContainerInfo,
    isSelected: Boolean,
    isMultiSelected: Boolean = false,
    cardWidth: Int,
    activeOperation: ActiveOperation? = null,
    statsHistory: List<StatsSnapshot> = emptyList(),
    modifier: Modifier = Modifier,
) {
    val theme = LocalTheme.current
    val isUpdating = activeOperation != null && activeOperation.containerName == container.name

    val borderColor = when {
        isUpdating -> theme.warning
        isMultiSelected -> theme.multiSelect
        isSelected -> theme.borderSelected
        container.updateAvailable -> theme.updateAvailable
        else -> theme.border
    }
    val stateColor = when (container.state) {
        ContainerState.RUNNING -> theme.statusRunning
        ContainerState.PAUSED -> theme.statusPaused
        ContainerState.EXITED, ContainerState.DEAD -> theme.statusStopped
        ContainerState.RESTARTING -> theme.statusRestarting
        ContainerState.CREATED -> theme.statusCreated
        else -> theme.textSecondary
    }

    val spinnerChar = rememberSpinner(isUpdating)

    Box(
        modifier = modifier
            .width(cardWidth)
            .height(CARD_HEIGHT)
            .drawBehind { drawBorder(borderColor) }
            .padding(left = 2, right = 2, top = 1, bottom = 1)
    ) {
        Column {
            // Container name + health icon + update arrow
            val nameText = buildAnnotatedString {
                val healthIcon = container.healthStatus
                if (healthIcon != null) {
                    val healthColor = when (healthIcon) {
                        "healthy" -> theme.healthHealthy
                        "unhealthy" -> theme.healthUnhealthy
                        "starting" -> theme.healthStarting
                        else -> theme.textSecondary
                    }
                    pushStyle(SpanStyle(color = healthColor))
                    append("\u2665")
                    pop()
                    append(" ")
                }
                val maxNameLen = cardWidth - 4 - (if (container.updateAvailable) 2 else 0) - (if (healthIcon != null) 2 else 0)
                append(container.name.take(maxNameLen))
                if (container.updateAvailable) {
                    append(" ")
                    pushStyle(SpanStyle(color = theme.updateAvailable, textStyle = TextStyle.Bold))
                    append("\u2191")
                    pop()
                }
            }
            Text(
                nameText,
                color = if (isSelected) theme.textPrimary else theme.textSecondary,
                textStyle = TextStyle.Bold,
            )

            val contentWidth = cardWidth - 4
            Text(container.image.take(contentWidth))

            if (isUpdating) {
                val phaseText = when (activeOperation) {
                    is ActiveOperation.Stopping -> "Stopping\u2026"
                    is ActiveOperation.Pulling -> "Pulling image\u2026"
                    is ActiveOperation.Creating -> "Creating\u2026"
                    is ActiveOperation.Starting -> "Starting\u2026"
                    else -> "Working\u2026"
                }
                val line = "$spinnerChar $phaseText"
                val padding = ((contentWidth - line.length) / 2).coerceAtLeast(0)

                Text("")
                Text(
                    buildAnnotatedString {
                        append(" ".repeat(padding))
                        pushStyle(SpanStyle(color = theme.warning, textStyle = TextStyle.Bold))
                        append("$spinnerChar ")
                        pop()
                        pushStyle(SpanStyle(color = theme.textPrimary))
                        append(phaseText)
                        pop()
                    }
                )
            } else {
                if (isLatestTag(container.image) && container.localDigest != null) {
                    val versionText = buildAnnotatedString {
                        pushStyle(SpanStyle(color = theme.versionLocal))
                        append(shortDigest(container.localDigest))
                        pop()
                        if (container.updateAvailable && container.remoteDigest != null) {
                            pushStyle(SpanStyle(color = theme.updateAvailable))
                            append(" \u2192 ")
                            pop()
                            pushStyle(SpanStyle(color = theme.versionRemote))
                            append(shortDigest(container.remoteDigest))
                            pop()
                        }
                    }
                    Text(versionText)
                }

                Text(container.status.take(cardWidth - 4), color = stateColor)

                if (container.ports.isNotEmpty()) {
                    Text(container.ports.take(cardWidth - 4), color = theme.border)
                }

                // Resources with sparklines on selected card
                if (container.state == ContainerState.RUNNING && container.memoryLimitMb > 0) {
                    if (isSelected && statsHistory.size >= 2) {
                        Row {
                            Text("CPU ", color = theme.textSecondary)
                            Sparkline(
                                values = statsHistory.map { it.cpuPercent },
                                maxValue = 100.0,
                                width = 10,
                                color = theme.sparklineCpu,
                            )
                            Text(String.format(" %.1f%%", container.cpuPercent), color = theme.textPrimary)
                        }
                        Row {
                            Text("MEM ", color = theme.textSecondary)
                            Sparkline(
                                values = statsHistory.map { it.memoryUsageMb },
                                maxValue = container.memoryLimitMb,
                                width = 10,
                                color = theme.sparklineMem,
                            )
                            Text(String.format(" %.0f/%.0fMB", container.memoryUsageMb, container.memoryLimitMb), color = theme.textPrimary)
                        }
                    } else {
                        val resourceText = buildAnnotatedString {
                            pushStyle(SpanStyle(color = theme.textSecondary))
                            append("CPU: ")
                            pop()
                            pushStyle(SpanStyle(color = theme.textPrimary))
                            append(String.format("%.1f%%", container.cpuPercent))
                            pop()
                            append("  ")
                            pushStyle(SpanStyle(color = theme.textSecondary))
                            append("MEM: ")
                            pop()
                            pushStyle(SpanStyle(color = theme.textPrimary))
                            append(String.format("%.0f/%.0fMB", container.memoryUsageMb, container.memoryLimitMb))
                            pop()
                        }
                        Text(resourceText)
                    }
                }

                // Volumes on stopped containers
                if (container.state != ContainerState.RUNNING && container.volumes.isNotEmpty()) {
                    val maxVols = if (container.ports.isEmpty()) 2 else 1
                    for (vol in container.volumes.take(maxVols)) {
                        val src = vol.source.let { s ->
                            if (s.length > contentWidth / 2 - 1) "\u2026" + s.takeLast(contentWidth / 2 - 2) else s
                        }
                        val dst = vol.destination.let { d ->
                            if (d.length > contentWidth / 2 - 1) "\u2026" + d.takeLast(contentWidth / 2 - 2) else d
                        }
                        Text(
                            buildAnnotatedString {
                                pushStyle(SpanStyle(color = theme.border))
                                append("$src \u2192 $dst")
                                pop()
                            }
                        )
                    }
                }
            }
        }
    }
}

private fun DrawScope.drawBorder(color: Color) {
    val w = width
    val h = height
    if (w < 2 || h < 2) return
    drawText(0, 0, "\u250c", foreground = color)
    drawText(0, w - 1, "\u2510", foreground = color)
    drawText(h - 1, 0, "\u2514", foreground = color)
    drawText(h - 1, w - 1, "\u2518", foreground = color)
    for (col in 1 until w - 1) {
        drawText(0, col, "\u2500", foreground = color)
        drawText(h - 1, col, "\u2500", foreground = color)
    }
    for (row in 1 until h - 1) {
        drawText(row, 0, "\u2502", foreground = color)
        drawText(row, w - 1, "\u2502", foreground = color)
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
