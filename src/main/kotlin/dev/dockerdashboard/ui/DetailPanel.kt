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

private val HEADER_BG = Color(30, 30, 30)
private val SECTION_COLOR = Color(80, 200, 255)
private val LABEL_COLOR = Color(160, 160, 160)
private val VALUE_COLOR = Color.White
private val SENSITIVE_PATTERNS = listOf("PASSWORD", "SECRET", "KEY", "TOKEN")

@Composable
fun DetailPanel(
    detail: ContainerDetail,
    scrollOffset: Int,
    termWidth: Int,
    termHeight: Int,
) {
    val contentLines = buildContentLines(detail)
    val visibleContentHeight = termHeight - 3
    val maxScroll = (contentLines.size - visibleContentHeight).coerceAtLeast(0)
    val effectiveScroll = scrollOffset.coerceIn(0, maxScroll)
    val visibleLines = contentLines.drop(effectiveScroll).take(visibleContentHeight)

    Column(modifier = Modifier.width(termWidth)) {
        // Header bar
        Row(
            modifier = Modifier.fillMaxWidth().background(HEADER_BG).padding(horizontal = 1),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                buildAnnotatedString {
                    pushStyle(SpanStyle(color = SECTION_COLOR, textStyle = TextStyle.Bold))
                    append("Container Detail")
                    pop()
                },
            )
            Text(
                buildAnnotatedString {
                    pushStyle(SpanStyle(color = VALUE_COLOR, textStyle = TextStyle.Bold))
                    append(detail.name)
                    pop()
                },
            )
        }

        // Scroll up indicator
        if (effectiveScroll > 0) {
            Text("  \u25b2 scroll up for more", color = LABEL_COLOR)
        } else {
            Text("")
        }

        // Content area
        for (line in visibleLines) {
            line()
        }

        // Pad remaining lines so footer stays at bottom
        val pad = visibleContentHeight - visibleLines.size
        repeat(pad.coerceAtLeast(0)) {
            Text("")
        }

        // Scroll down indicator
        if (effectiveScroll < maxScroll) {
            Text("  \u25bc scroll down for more", color = LABEL_COLOR)
        } else {
            Text("")
        }

        // Footer bar
        Row(
            modifier = Modifier.fillMaxWidth().background(HEADER_BG).padding(horizontal = 1),
        ) {
            Text(
                buildAnnotatedString {
                    pushStyle(SpanStyle(color = SECTION_COLOR, textStyle = TextStyle.Bold))
                    append("\u2191\u2193/jk")
                    pop()
                    pushStyle(SpanStyle(color = LABEL_COLOR))
                    append(" scroll  ")
                    pop()
                    pushStyle(SpanStyle(color = SECTION_COLOR, textStyle = TextStyle.Bold))
                    append("Esc")
                    pop()
                    pushStyle(SpanStyle(color = LABEL_COLOR))
                    append(" back")
                    pop()
                },
            )
        }
    }
}

private fun buildContentLines(detail: ContainerDetail): List<@Composable () -> Unit> {
    val lines = mutableListOf<@Composable () -> Unit>()

    lines.add { SectionHeader("Overview") }
    lines.add { LabelValue("  Name", detail.name) }
    lines.add { LabelValue("  Image", detail.image) }
    lines.add { LabelValue("  ID", detail.id.take(12)) }
    lines.add { LabelValue("  Status", detail.status) }
    lines.add { LabelValue("  Created", detail.created) }
    if (detail.exitCode != null) {
        val code = detail.exitCode
        lines.add { LabelValue("  Exit Code", code.toString()) }
    }
    lines.add { Text("") }

    lines.add { SectionHeader("Config") }
    lines.add { LabelValue("  Command", detail.command.ifEmpty { "-" }) }
    lines.add { LabelValue("  Entrypoint", detail.entrypoint.ifEmpty { "-" }) }
    lines.add { LabelValue("  Restart Policy", detail.restartPolicy.ifEmpty { "-" }) }
    lines.add { Text("") }

    lines.add { SectionHeader("Environment") }
    if (detail.env.isEmpty()) {
        lines.add { Text("  (none)", color = LABEL_COLOR) }
    } else {
        for (envVar in detail.env) {
            val masked = maskSensitiveEnv(envVar)
            lines.add { Text("  $masked", color = VALUE_COLOR) }
        }
    }
    lines.add { Text("") }

    lines.add { SectionHeader("Networks") }
    if (detail.networks.isEmpty()) {
        lines.add { Text("  (none)", color = LABEL_COLOR) }
    } else {
        for (network in detail.networks) {
            lines.add { Text("  $network", color = VALUE_COLOR) }
        }
    }
    lines.add { Text("") }

    lines.add { SectionHeader("Volumes") }
    if (detail.volumes.isEmpty()) {
        lines.add { Text("  (none)", color = LABEL_COLOR) }
    } else {
        for (vol in detail.volumes) {
            val volText = "${vol.source} \u2192 ${vol.destination} (${vol.mode})"
            lines.add { Text("  $volText", color = VALUE_COLOR) }
        }
    }
    lines.add { Text("") }

    lines.add { SectionHeader("Labels") }
    if (detail.labels.isEmpty()) {
        lines.add { Text("  (none)", color = LABEL_COLOR) }
    } else {
        for ((key, value) in detail.labels) {
            lines.add { LabelValue("  $key", value) }
        }
    }

    return lines
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        buildAnnotatedString {
            pushStyle(SpanStyle(color = SECTION_COLOR, textStyle = TextStyle.Bold))
            append("\u2500\u2500 $title \u2500\u2500")
            pop()
        },
    )
}

@Composable
private fun LabelValue(label: String, value: String) {
    Text(
        buildAnnotatedString {
            pushStyle(SpanStyle(color = LABEL_COLOR))
            append("$label: ")
            pop()
            pushStyle(SpanStyle(color = VALUE_COLOR))
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
