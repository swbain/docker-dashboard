package dev.dockerdashboard.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import com.jakewharton.mosaic.LocalTerminalState
import com.jakewharton.mosaic.layout.KeyEvent
import com.jakewharton.mosaic.layout.height
import com.jakewharton.mosaic.layout.onKeyEvent
import com.jakewharton.mosaic.layout.width
import com.jakewharton.mosaic.modifier.Modifier
import com.jakewharton.mosaic.text.SpanStyle
import com.jakewharton.mosaic.text.buildAnnotatedString
import com.jakewharton.mosaic.ui.Alignment
import com.jakewharton.mosaic.ui.Box
import com.jakewharton.mosaic.ui.Column
import com.jakewharton.mosaic.ui.Text
import com.jakewharton.mosaic.ui.TextStyle
import dev.dockerdashboard.model.ContainerDetail
import dev.dockerdashboard.model.ContainerInfo
import dev.dockerdashboard.model.ContainerState
import dev.dockerdashboard.model.DashboardState
import dev.dockerdashboard.model.ViewMode

enum class Direction { UP, DOWN, LEFT, RIGHT }

sealed interface UiAction {
    data class Move(val direction: Direction) : UiAction
    data object ToggleStartStop : UiAction
    data object PullAndRestart : UiAction
    data object Confirm : UiAction
    data object Cancel : UiAction
    data object Quit : UiAction
    data object ViewLogs : UiAction
    data object ViewDetail : UiAction
    data object BackToGrid : UiAction
    data object ToggleSelect : UiAction
    data class TypeFilterChar(val char: Char) : UiAction
    data object StartSearch : UiAction
    data object CancelSearch : UiAction
    data object CycleStateFilter : UiAction
    data object CycleSortMode : UiAction
    data object ShellExec : UiAction
    data object PruneImages : UiAction
    data object CycleTheme : UiAction
    data object ScrollUp : UiAction
    data object ScrollDown : UiAction
}

@Composable
fun DashboardApp(
    state: DashboardState,
    displayContainers: List<ContainerInfo>,
    detailData: ContainerDetail?,
    columns: Int,
    availableHeight: Int,
    onAction: (UiAction) -> Unit,
) {
    val theme = allThemes.find { it.name == state.themeName } ?: DarkTheme
    CompositionLocalProvider(LocalTheme provides theme) {
        val terminal = LocalTerminalState.current
        val termWidth = terminal.size.columns
        val termHeight = terminal.size.rows

        if (state.isInitialLoading) {
            val spinner = rememberSpinner(true)
            Box(
                modifier = Modifier
                    .width(termWidth)
                    .height(termHeight)
                    .onKeyEvent { event -> handleKeyEvent(event, state, onAction) },
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        buildAnnotatedString {
                            pushStyle(SpanStyle(color = theme.accent, textStyle = TextStyle.Bold))
                            append("Docker Dashboard")
                            pop()
                        },
                    )
                    Text("")
                    Text(
                        buildAnnotatedString {
                            pushStyle(SpanStyle(color = theme.warning))
                            append("$spinner")
                            pop()
                            pushStyle(SpanStyle(color = theme.textSecondary))
                            append(" Connecting to Docker...")
                            pop()
                        },
                    )
                }
            }
            return@CompositionLocalProvider
        }

        val cardWidth = termWidth / columns

        when (state.viewMode) {
            ViewMode.GRID -> {
                val containers = displayContainers
                val layout = buildGridLayout(containers, columns)
                val scrollOffset = state.scrollOffset.coerceIn(0, (layout.visualRows.size - 1).coerceAtLeast(0))
                val filterBarHeight = if (state.isSearchMode || state.filterText.isNotEmpty()) 1 else 0
                val gridAvailableHeight = availableHeight - filterBarHeight

                Box(
                    modifier = Modifier
                        .width(termWidth)
                        .onKeyEvent { event -> handleKeyEvent(event, state, onAction) },
                ) {
                    Column(modifier = Modifier.width(termWidth)) {
                        TopStatusBar(
                            containerCount = state.containers.size,
                            runningCount = state.containers.count { it.state == ContainerState.RUNNING },
                            isConnected = state.isConnected,
                            isInitialLoading = false,
                            sortMode = state.sortMode,
                            stateFilter = state.stateFilter,
                            filterText = state.filterText,
                        )

                        if (filterBarHeight > 0) {
                            FilterBar(filterText = state.filterText)
                        }

                        when {
                            !state.isConnected -> {
                                Text(
                                    "  Cannot connect to Docker. Is the Docker daemon running?",
                                    color = theme.error,
                                )
                            }
                            containers.isEmpty() -> {
                                Text(
                                    "  No containers found.",
                                    color = theme.textMuted,
                                )
                            }
                            else -> {
                                val cardRowsAbove = if (scrollOffset > 0) {
                                    layout.visualRows.take(scrollOffset).count { it is VisualRow.Cards }
                                } else 0
                                if (cardRowsAbove > 0) {
                                    Text("  \u25b2 $cardRowsAbove more row(s) above", color = theme.textMuted)
                                }

                                ContainerGrid(
                                    layout = layout,
                                    containers = containers,
                                    selectedIndex = state.selectedIndex,
                                    cardWidth = cardWidth,
                                    scrollOffset = scrollOffset,
                                    availableHeight = gridAvailableHeight,
                                    activeOperation = state.activeOperation,
                                    statsHistory = state.statsHistory,
                                    selectedContainerIds = state.selectedContainerIds,
                                )

                                val visibleRange = layout.visibleRowRange(scrollOffset, gridAvailableHeight)
                                val visibleEnd = if (visibleRange.isEmpty()) scrollOffset else visibleRange.last + 1
                                val cardRowsBelow = layout.visualRows.drop(visibleEnd)
                                    .count { it is VisualRow.Cards }
                                if (cardRowsBelow > 0) {
                                    Text("  \u25bc $cardRowsBelow more row(s) below", color = theme.textMuted)
                                }
                            }
                        }

                        val pendingMessage = state.pendingConfirmation?.message
                        if (pendingMessage != null) {
                            ConfirmBar(pendingMessage)
                        } else {
                            BottomActionBar(
                                activeOperation = state.activeOperation,
                                errorMessage = state.errorMessage,
                                selectedCount = state.selectedContainerIds.size,
                            )
                        }
                    }
                }
            }
            ViewMode.DETAIL -> {
                Box(
                    modifier = Modifier
                        .width(termWidth)
                        .height(termHeight)
                        .onKeyEvent { event -> handleKeyEvent(event, state, onAction) },
                ) {
                    if (detailData != null) {
                        DetailPanel(
                            detail = detailData,
                            scrollOffset = state.detailScrollOffset,
                            termWidth = termWidth,
                            termHeight = termHeight,
                        )
                    } else {
                        Text("Loading detail...", color = theme.textMuted)
                    }
                }
            }
            ViewMode.LOGS -> {
                Box(
                    modifier = Modifier
                        .width(termWidth)
                        .height(termHeight)
                        .onKeyEvent { event -> handleKeyEvent(event, state, onAction) },
                ) {
                    LogViewer(
                        logLines = state.logLines,
                        containerName = state.containers.find { it.id == state.logContainerId }?.name ?: "",
                        scrollOffset = state.logScrollOffset,
                        termWidth = termWidth,
                        termHeight = termHeight,
                    )
                }
            }
        }
    }
}

private fun handleKeyEvent(event: KeyEvent, state: DashboardState, onAction: (UiAction) -> Unit): Boolean {
    if (event.key == "q" || event.key == "Q") {
        onAction(UiAction.Quit)
        return true
    }

    if (state.pendingConfirmation != null) {
        when (event.key) {
            "y", "Y" -> { onAction(UiAction.Confirm); return true }
            "n", "N" -> { onAction(UiAction.Cancel); return true }
            else -> return false
        }
    }

    when (state.viewMode) {
        ViewMode.GRID -> {
            if (state.isSearchMode) {
                when (event.key) {
                    "Escape" -> { onAction(UiAction.CancelSearch); return true }
                    "Backspace" -> { onAction(UiAction.TypeFilterChar('\b')); return true }
                    else -> {
                        if (event.key.length == 1) {
                            onAction(UiAction.TypeFilterChar(event.key[0]))
                            return true
                        }
                        return false
                    }
                }
            }

            when (event.key) {
                "ArrowUp", "k" -> onAction(UiAction.Move(Direction.UP))
                "ArrowDown", "j" -> onAction(UiAction.Move(Direction.DOWN))
                "ArrowLeft", "h" -> onAction(UiAction.Move(Direction.LEFT))
                "ArrowRight" -> onAction(UiAction.Move(Direction.RIGHT))
                "u", "U" -> onAction(UiAction.PullAndRestart)
                "s", "S" -> onAction(UiAction.ToggleStartStop)
                "y", "Y" -> onAction(UiAction.Confirm)
                "n", "N" -> onAction(UiAction.Cancel)
                "l" -> onAction(UiAction.ViewLogs)
                "d", "Enter" -> onAction(UiAction.ViewDetail)
                " " -> onAction(UiAction.ToggleSelect)
                "/" -> onAction(UiAction.StartSearch)
                "f" -> onAction(UiAction.CycleStateFilter)
                "o" -> onAction(UiAction.CycleSortMode)
                "e" -> onAction(UiAction.ShellExec)
                "p" -> onAction(UiAction.PruneImages)
                "t" -> onAction(UiAction.CycleTheme)
                else -> return false
            }
        }
        ViewMode.DETAIL -> {
            when (event.key) {
                "Escape" -> onAction(UiAction.BackToGrid)
                "ArrowUp", "k" -> onAction(UiAction.ScrollUp)
                "ArrowDown", "j" -> onAction(UiAction.ScrollDown)
                else -> return false
            }
        }
        ViewMode.LOGS -> {
            when (event.key) {
                "Escape" -> onAction(UiAction.BackToGrid)
                "ArrowUp", "k" -> onAction(UiAction.ScrollUp)
                "ArrowDown", "j" -> onAction(UiAction.ScrollDown)
                "r" -> onAction(UiAction.ViewLogs)
                else -> return false
            }
        }
    }
    return true
}
