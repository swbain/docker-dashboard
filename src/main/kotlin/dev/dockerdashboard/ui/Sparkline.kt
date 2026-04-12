package dev.dockerdashboard.ui

import androidx.compose.runtime.Composable
import com.jakewharton.mosaic.text.SpanStyle
import com.jakewharton.mosaic.text.buildAnnotatedString
import com.jakewharton.mosaic.ui.Color
import com.jakewharton.mosaic.ui.Text

private val BLOCKS = charArrayOf('\u2581', '\u2582', '\u2583', '\u2584', '\u2585', '\u2586', '\u2587', '\u2588')

@Composable
fun Sparkline(
    values: List<Double>,
    maxValue: Double = values.maxOrNull() ?: 1.0,
    width: Int = values.size,
    color: Color = Color.Green,
) {
    val effectiveMax = if (maxValue <= 0.0) 1.0 else maxValue
    // Take last `width` values, or pad with zeros
    val display = if (values.size >= width) {
        values.takeLast(width)
    } else {
        List(width - values.size) { 0.0 } + values
    }

    val text = buildAnnotatedString {
        pushStyle(SpanStyle(color = color))
        for (v in display) {
            val normalized = (v / effectiveMax).coerceIn(0.0, 1.0)
            val index = (normalized * (BLOCKS.size - 1)).toInt().coerceIn(0, BLOCKS.size - 1)
            append(BLOCKS[index])
        }
        pop()
    }
    Text(text)
}
