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
import dev.dockerdashboard.model.ContainerDetail

private val SENSITIVE_PATTERNS = listOf("PASSWORD", "SECRET", "KEY", "TOKEN")

@Composable
fun DetailPanel(
    detail: ContainerDetail,
    scrollOffset: Int,
    termWidth: Int,
    termHeight: Int,
) {
    val theme = LocalTheme.current
    val contentLines = buildContentLines(detail, theme)
    val visibleContentHeight = termHeight - 3
    val maxScroll = (contentLines.size - visibleContentHeight).coerceAtLeast(0)
    val effectiveScroll = scrollOffset.coerceIn(0, maxScroll)
    val visibleLines = contentLines.drop(effectiveScroll).take(visibleContentHeight)

    Column(modifier = Modifier.width(termWidth)) {
        Row(
            modifier = Modifier.fillMaxWidth().background(theme.barBackground).padding(horizontal = 1),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                buildAnnotatedString {
                    pushStyle(SpanStyle(color = theme.accent, textStyle = TextStyle.Bold))
                    append("Container Detail")
                    pop()
                },
            )
            Text(
                buildAnnotatedString {
                    pushStyle(SpanStyle(color = theme.textPrimary, textStyle = TextStyle.Bold))
                    append(detail.name)
                    pop()
                },
            )
        }

        if (effectiveScroll > 0) {
            Text("  \u25b2 scroll up for more", color = theme.textSecondary)
        } else {
            Text("")
        }

        for (line in visibleLines) {
            line()
        }

        val pad = visibleContentHeight - visibleLines.size
        repeat(pad.coerceAtLeast(0)) { Text("") }

        if (effectiveScroll < maxScroll) {
            Text("  \u25bc scroll down for more", color = theme.textSecondary)
        } else {
            Text("")
        }

        Row(
            modifier = Modifier.fillMaxWidth().background(theme.barBackground).padding(horizontal = 1),
        ) {
            Text(
                buildAnnotatedString {
                    pushStyle(SpanStyle(color = theme.accent, textStyle = TextStyle.Bold))
                    append("\u2191\u2193/jk")
                    pop()
                    pushStyle(SpanStyle(color = theme.textSecondary))
                    append(" scroll  ")
                    pop()
                    pushStyle(SpanStyle(color = theme.accent, textStyle = TextStyle.Bold))
                    append("Esc")
                    pop()
                    pushStyle(SpanStyle(color = theme.textSecondary))
                    append(" back")
                    pop()
                },
            )
        }
    }
}

private fun buildContentLines(detail: ContainerDetail, theme: Theme): List<@Composable () -> Unit> {
    val lines = mutableListOf<@Composable () -> Unit>()

    lines.add { SectionHeader("Overview", theme) }
    lines.add { LabelValue("  Name", detail.name, theme) }
    lines.add { LabelValue("  Image", detail.image, theme) }
    lines.add { LabelValue("  ID", detail.id.take(12), theme) }
    lines.add { LabelValue("  Status", detail.status, theme) }
    lines.add { LabelValue("  Created", detail.created, theme) }
    if (detail.exitCode != null) {
        val code = detail.exitCode
        lines.add { LabelValue("  Exit Code", code.toString(), theme) }
    }
    lines.add { Text("") }

    lines.add { SectionHeader("Config", theme) }
    lines.add { LabelValue("  Command", detail.command.ifEmpty { "-" }, theme) }
    lines.add { LabelValue("  Entrypoint", detail.entrypoint.ifEmpty { "-" }, theme) }
    lines.add { LabelValue("  Restart Policy", detail.restartPolicy.ifEmpty { "-" }, theme) }
    lines.add { Text("") }

    lines.add { SectionHeader("Environment", theme) }
    if (detail.env.isEmpty()) {
        lines.add { Text("  (none)", color = theme.textSecondary) }
    } else {
        for (envVar in detail.env) {
            val masked = maskSensitiveEnv(envVar)
            lines.add { Text("  $masked", color = theme.textPrimary) }
        }
    }
    lines.add { Text("") }

    lines.add { SectionHeader("Networks", theme) }
    if (detail.networks.isEmpty()) {
        lines.add { Text("  (none)", color = theme.textSecondary) }
    } else {
        for (network in detail.networks) {
            lines.add { Text("  $network", color = theme.textPrimary) }
        }
    }
    lines.add { Text("") }

    lines.add { SectionHeader("Volumes", theme) }
    if (detail.volumes.isEmpty()) {
        lines.add { Text("  (none)", color = theme.textSecondary) }
    } else {
        for (vol in detail.volumes) {
            val volText = "${vol.source} \u2192 ${vol.destination} (${vol.mode})"
            lines.add { Text("  $volText", color = theme.textPrimary) }
        }
    }
    lines.add { Text("") }

    lines.add { SectionHeader("Labels", theme) }
    if (detail.labels.isEmpty()) {
        lines.add { Text("  (none)", color = theme.textSecondary) }
    } else {
        for ((key, value) in detail.labels) {
            lines.add { LabelValue("  $key", value, theme) }
        }
    }

    return lines
}

@Composable
private fun SectionHeader(title: String, theme: Theme) {
    Text(
        buildAnnotatedString {
            pushStyle(SpanStyle(color = theme.accent, textStyle = TextStyle.Bold))
            append("\u2500\u2500 $title \u2500\u2500")
            pop()
        },
    )
}

@Composable
private fun LabelValue(label: String, value: String, theme: Theme) {
    Text(
        buildAnnotatedString {
            pushStyle(SpanStyle(color = theme.textSecondary))
            append("$label: ")
            pop()
            pushStyle(SpanStyle(color = theme.textPrimary))
            append(value)
            pop()
        },
    )
}

private fun maskSensitiveEnv(envVar: String): String {
    val eqIndex = envVar.indexOf('=')
    if (eqIndex < 0) return envVar
    val key = envVar.substring(0, eqIndex).uppercase()
    return if (SENSITIVE_PATTERNS.any { key.contains(it) }) {
        "${envVar.substring(0, eqIndex)}=****"
    } else {
        envVar
    }
}
