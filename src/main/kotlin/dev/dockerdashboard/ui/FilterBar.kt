package dev.dockerdashboard.ui

import androidx.compose.runtime.Composable
import com.jakewharton.mosaic.layout.background
import com.jakewharton.mosaic.layout.fillMaxWidth
import com.jakewharton.mosaic.layout.padding
import com.jakewharton.mosaic.modifier.Modifier
import com.jakewharton.mosaic.text.SpanStyle
import com.jakewharton.mosaic.text.buildAnnotatedString
import com.jakewharton.mosaic.ui.Color
import com.jakewharton.mosaic.ui.Row
import com.jakewharton.mosaic.ui.Text
import com.jakewharton.mosaic.ui.TextStyle

@Composable
fun FilterBar(
    filterText: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth().background(Color(20, 40, 60)).padding(horizontal = 1),
    ) {
        Text(
            buildAnnotatedString {
                pushStyle(SpanStyle(color = Color(80, 200, 255), textStyle = TextStyle.Bold))
                append("/ ")
                pop()
                pushStyle(SpanStyle(color = Color.White))
                append(filterText)
                pop()
                pushStyle(SpanStyle(color = Color(80, 200, 255)))
                append("\u258f")
                pop()
            },
        )
    }
}
