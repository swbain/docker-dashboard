package dev.dockerdashboard.ui

import androidx.compose.runtime.Composable
import com.jakewharton.mosaic.layout.background
import com.jakewharton.mosaic.layout.fillMaxWidth
import com.jakewharton.mosaic.layout.padding
import com.jakewharton.mosaic.modifier.Modifier
import com.jakewharton.mosaic.text.SpanStyle
import com.jakewharton.mosaic.text.buildAnnotatedString
import com.jakewharton.mosaic.ui.Arrangement
import com.jakewharton.mosaic.ui.Row
import com.jakewharton.mosaic.ui.Text
import com.jakewharton.mosaic.ui.TextStyle
import dev.dockerdashboard.model.ActiveOperation
import dev.dockerdashboard.model.SortMode
import dev.dockerdashboard.model.StateFilter

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
    val theme = LocalTheme.current
    val spinner = rememberSpinner(isInitialLoading)

    Row(
        modifier = modifier.fillMaxWidth().background(theme.barBackground).padding(horizontal = 1),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        val title = buildAnnotatedString {
            pushStyle(SpanStyle(color = theme.accent, textStyle = TextStyle.Bold))
            append("Docker Dashboard")
            pop()
            append("  ")
            when {
                isInitialLoading -> {
                    pushStyle(SpanStyle(color = theme.warning))
                    append("$spinner")
                    pop()
                    append(" Connecting...")
                }
                isConnected -> {
                    pushStyle(SpanStyle(color = theme.statusRunning))
                    append("\u25cf")
                    pop()
                    append(" Connected")
                }
                else -> {
                    pushStyle(SpanStyle(color = theme.error))
                    append("\u25cf")
                    pop()
                    append(" Disconnected")
                }
            }
        }
        Text(title)

        val stats = buildAnnotatedString {
            pushStyle(SpanStyle(color = theme.textPrimary))
            append("$runningCount")
            pop()
            pushStyle(SpanStyle(color = theme.textMuted))
            append("/$containerCount running")
            pop()
            if (sortMode != SortMode.NAME) {
                append("  ")
                pushStyle(SpanStyle(color = theme.warning))
                append("\u2195 ${sortMode.name}")
                pop()
            }
            if (stateFilter != StateFilter.ALL) {
                append("  ")
                pushStyle(SpanStyle(color = theme.warning))
                append("${stateFilter.name}")
                pop()
            }
            if (filterText.isNotEmpty()) {
                append("  ")
                pushStyle(SpanStyle(color = theme.accent))
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
    val theme = LocalTheme.current
    Row(
        modifier = modifier.fillMaxWidth().background(theme.barBackground).padding(horizontal = 1),
    ) {
        when {
            errorMessage != null -> {
                Text(errorMessage, color = theme.error)
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
                Text(msg, color = theme.warning)
            }
            else -> {
                val hints = buildAnnotatedString {
                    if (selectedCount > 0) {
                        pushStyle(SpanStyle(color = theme.multiSelect, textStyle = TextStyle.Bold))
                        append("$selectedCount selected  ")
                        pop()
                    }
                    fun hint(key: String, label: String) {
                        pushStyle(SpanStyle(color = theme.accent, textStyle = TextStyle.Bold))
                        append(key)
                        pop()
                        pushStyle(SpanStyle(color = theme.textSecondary))
                        append(" $label  ")
                        pop()
                    }
                    hint("arrows", "navigate")
                    hint("u", "update")
                    hint("s", "start/stop")
                    hint("l", "logs")
                    hint("d", "detail")
                    hint("e", "shell")
                    hint("/", "search")
                    hint("o", "sort")
                    hint("p", "prune")
                    hint("t", "theme")
                    hint("q", "quit")
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
    val theme = LocalTheme.current
    Row(
        modifier = modifier.fillMaxWidth().background(theme.confirmBackground).padding(horizontal = 1),
    ) {
        val confirmText = buildAnnotatedString {
            pushStyle(SpanStyle(color = theme.warning, textStyle = TextStyle.Bold))
            append(message)
            pop()
            append("  ")
            pushStyle(SpanStyle(color = theme.textPrimary, textStyle = TextStyle.Bold))
            append("[y]")
            pop()
            pushStyle(SpanStyle(color = theme.textSecondary))
            append(" confirm  ")
            pop()
            pushStyle(SpanStyle(color = theme.textPrimary, textStyle = TextStyle.Bold))
            append("[n]")
            pop()
            pushStyle(SpanStyle(color = theme.textSecondary))
            append(" cancel")
            pop()
        }
        Text(confirmText)
    }
}
