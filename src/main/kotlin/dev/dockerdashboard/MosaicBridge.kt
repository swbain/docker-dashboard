@file:Suppress("INVISIBLE_REFERENCE", "INVISIBLE_MEMBER")

package dev.dockerdashboard

import androidx.compose.runtime.Composable
import com.jakewharton.mosaic.AnsiRendering
import com.jakewharton.mosaic.runMosaicComposition
import com.jakewharton.mosaic.terminal.Terminal

suspend fun runDashboardComposition(
    terminal: Terminal,
    content: @Composable () -> Unit,
) {
    val rendering = AnsiRendering(terminal.capabilities)
    runMosaicComposition(terminal, rendering, content)
}
