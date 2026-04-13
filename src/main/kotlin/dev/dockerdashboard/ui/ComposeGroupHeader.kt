package dev.dockerdashboard.ui

import androidx.compose.runtime.Composable
import com.jakewharton.mosaic.layout.fillMaxWidth
import com.jakewharton.mosaic.layout.padding
import com.jakewharton.mosaic.modifier.Modifier
import com.jakewharton.mosaic.text.SpanStyle
import com.jakewharton.mosaic.text.buildAnnotatedString
import com.jakewharton.mosaic.ui.Row
import com.jakewharton.mosaic.ui.Text
import com.jakewharton.mosaic.ui.TextStyle

@Composable
fun ComposeGroupHeader(
    projectName: String,
    containerCount: Int,
    modifier: Modifier = Modifier,
) {
    val theme = LocalTheme.current
    Row(modifier = modifier.fillMaxWidth().padding(horizontal = 1)) {
        Text(
            buildAnnotatedString {
                pushStyle(SpanStyle(color = theme.accent, textStyle = TextStyle.Bold))
                append("\u25a0 ")
                append(projectName)
                pop()
                pushStyle(SpanStyle(color = theme.textMuted))
                append(" ($containerCount)")
                pop()
            },
        )
    }
}
