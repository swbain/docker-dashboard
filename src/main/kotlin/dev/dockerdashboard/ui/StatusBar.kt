package dev.dockerdashboard.ui

import androidx.compose.runtime.Composable
import com.jakewharton.mosaic.layout.background
import com.jakewharton.mosaic.layout.fillMaxWidth
import com.jakewharton.mosaic.layout.padding
import com.jakewharton.mosaic.modifier.Modifier
import com.jakewharton.mosaic.text.SpanStyle
import com.jakewharton.mosaic.text.buildAnnotatedString
import com.jakewharton.mosaic.ui.Arrangement
import com.jakewharton.mosaic.ui.Color
import com.jakewharton.mosaic.ui.Row
import com.jakewharton.mosaic.ui.Text
import com.jakewharton.mosaic.ui.TextStyle
import dev.dockerdashboard.model.ActiveOperation
import dev.dockerdashboard.model.SortMode
import dev.dockerdashboard.model.StateFilter

private val BAR_BG = Color(30, 30, 30)
private val ACCENT = Color(80, 200, 255)

@Composable
fun TopStatusBar(
    containerCount: Int,
    runningCount: Int,
    isConnected: Boolean,
    isInitialLoading: Boolean,
    sortMode: SortMode = SortMode.NAME,
    stateFilter: StateFilter = StateFilter.ALL,
    filterText: String = "",
    modifier: Modifier = Modifier,
) {
    val spinner = rememberSpinner(isInitialLoading)

    Row(
        modifier = modifier.fillMaxWidth().background(BAR_BG).padding(horizontal = 1),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        val title = buildAnnotatedString {
            pushStyle(SpanStyle(color = ACCENT, textStyle = TextStyle.Bold))
            append("Docker Dashboard")
            pop()
            append("  ")
            when {
                isInitialLoading -> {
                    pushStyle(SpanStyle(color = Color.Yellow))
                    append("$spinner")
                    pop()
                    append(" Connecting...")
                }
                isConnected -> {
                    pushStyle(SpanStyle(color = Color.Green))
                    append("\u25cf")
                    pop()
                    append(" Connected")
                }
                else -> {
                    pushStyle(SpanStyle(color = Color.Red))
                    append("\u25cf")
                    pop()
                    append(" Disconnected")
                }
            }
        }
        Text(title)

        val stats = buildAnnotatedString {
            pushStyle(SpanStyle(color = Color.White))
            append("$runningCount")
            pop()
            pushStyle(SpanStyle(color = Color(140, 140, 140)))
            append("/$containerCount running")
            pop()
            if (sortMode != SortMode.NAME) {
                append("  ")
                pushStyle(SpanStyle(color = Color.Yellow))
                append("\u2195 ${sortMode.name}")
                pop()
            }
            if (stateFilter != StateFilter.ALL) {
                append("  ")
                pushStyle(SpanStyle(color = Color.Yellow))
                append("${stateFilter.name}")
                pop()
            }
            if (filterText.isNotEmpty()) {
                append("  ")
                pushStyle(SpanStyle(color = ACCENT))
                append("filter: $filterText")
                pop()
            }
        }
        Text(stats)
    }
}

@Composable
fun BottomActionBar(
    activeOperation: ActiveOperation?,
    errorMessage: String?,
    selectedCount: Int = 0,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth().background(BAR_BG).padding(horizontal = 1),
    ) {
        when {
            errorMessage != null -> {
                Text(errorMessage, color = Color.Red)
            }
            activeOperation != null -> {
                val msg = when (activeOperation) {
                    is ActiveOperation.Pulling -> "Pulling image for ${activeOperation.containerName}..."
                    is ActiveOperation.Restarting -> "Restarting ${activeOperation.containerName}..."
                    is ActiveOperation.Stopping -> "Stopping ${activeOperation.containerName}..."
                    is ActiveOperation.Starting -> "Starting ${activeOperation.containerName}..."
                    is ActiveOperation.Creating -> "Creating container for ${activeOperation.containerName}..."
                    is ActiveOperation.Pruning -> "Pruning unused images..."
                    is ActiveOperation.BulkStopping -> "Stopping ${activeOperation.containerName} (${activeOperation.current}/${activeOperation.total})..."
                    is ActiveOperation.BulkStarting -> "Starting ${activeOperation.containerName} (${activeOperation.current}/${activeOperation.total})..."
                    is ActiveOperation.BulkPulling -> "Pulling ${activeOperation.containerName} (${activeOperation.current}/${activeOperation.total})..."
                }
                Text(msg, color = Color.Yellow)
            }
            else -> {
                val hints = buildAnnotatedString {
                    if (selectedCount > 0) {
                        pushStyle(SpanStyle(color = Color.Magenta, textStyle = TextStyle.Bold))
                        append("$selectedCount selected  ")
                        pop()
                    }

                    pushStyle(SpanStyle(color = ACCENT, textStyle = TextStyle.Bold))
                    append("arrows")
                    pop()
                    pushStyle(SpanStyle(color = Color(160, 160, 160)))
                    append(" navigate  ")
                    pop()

                    pushStyle(SpanStyle(color = ACCENT, textStyle = TextStyle.Bold))
                    append("u")
                    pop()
                    pushStyle(SpanStyle(color = Color(160, 160, 160)))
                    append(" update  ")
                    pop()

                    pushStyle(SpanStyle(color = ACCENT, textStyle = TextStyle.Bold))
                    append("s")
                    pop()
                    pushStyle(SpanStyle(color = Color(160, 160, 160)))
                    append(" start/stop  ")
                    pop()

                    pushStyle(SpanStyle(color = ACCENT, textStyle = TextStyle.Bold))
                    append("l")
                    pop()
                    pushStyle(SpanStyle(color = Color(160, 160, 160)))
                    append(" logs  ")
                    pop()

                    pushStyle(SpanStyle(color = ACCENT, textStyle = TextStyle.Bold))
                    append("d")
                    pop()
                    pushStyle(SpanStyle(color = Color(160, 160, 160)))
                    append(" detail  ")
                    pop()

                    pushStyle(SpanStyle(color = ACCENT, textStyle = TextStyle.Bold))
                    append("e")
                    pop()
                    pushStyle(SpanStyle(color = Color(160, 160, 160)))
                    append(" shell  ")
                    pop()

                    pushStyle(SpanStyle(color = ACCENT, textStyle = TextStyle.Bold))
                    append("/")
                    pop()
                    pushStyle(SpanStyle(color = Color(160, 160, 160)))
                    append(" search  ")
                    pop()

                    pushStyle(SpanStyle(color = ACCENT, textStyle = TextStyle.Bold))
                    append("o")
                    pop()
                    pushStyle(SpanStyle(color = Color(160, 160, 160)))
                    append(" sort  ")
                    pop()

                    pushStyle(SpanStyle(color = ACCENT, textStyle = TextStyle.Bold))
                    append("q")
                    pop()
                    pushStyle(SpanStyle(color = Color(160, 160, 160)))
                    append(" quit")
                    pop()
                }
                Text(hints)
            }
        }
    }
}

@Composable
fun ConfirmBar(
    message: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth().background(Color(60, 30, 0)).padding(horizontal = 1),
    ) {
        val confirmText = buildAnnotatedString {
            pushStyle(SpanStyle(color = Color.Yellow, textStyle = TextStyle.Bold))
            append(message)
            pop()
            append("  ")
            pushStyle(SpanStyle(color = Color.White, textStyle = TextStyle.Bold))
            append("[y]")
            pop()
            pushStyle(SpanStyle(color = Color(160, 160, 160)))
            append(" confirm  ")
            pop()
            pushStyle(SpanStyle(color = Color.White, textStyle = TextStyle.Bold))
            append("[n]")
            pop()
            pushStyle(SpanStyle(color = Color(160, 160, 160)))
            append(" cancel")
            pop()
        }
        Text(confirmText)
    }
}
