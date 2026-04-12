package dev.dockerdashboard.ui

import androidx.compose.runtime.Composable
import com.jakewharton.mosaic.LocalTerminalState
import com.jakewharton.mosaic.layout.KeyEvent
import com.jakewharton.mosaic.layout.onKeyEvent
import com.jakewharton.mosaic.layout.width
import com.jakewharton.mosaic.modifier.Modifier
import com.jakewharton.mosaic.ui.Box
import com.jakewharton.mosaic.ui.Color
import com.jakewharton.mosaic.ui.Column
import com.jakewharton.mosaic.ui.Text
import dev.dockerdashboard.model.ContainerState
import dev.dockerdashboard.model.DashboardState
import kotlin.math.max

enum class Direction { UP, DOWN, LEFT, RIGHT }

sealed interface UiAction {
    data class Move(val direction: Direction) : UiAction
    data object ToggleStartStop : UiAction
    data object PullAndRestart : UiAction
    data object Confirm : UiAction
    data object Cancel : UiAction
    data object Quit : UiAction
}

@Composable
fun DashboardApp(
    state: DashboardState,
    maxVisibleRows: Int,
    onAction: (UiAction) -> Unit,
) {
    val terminal = LocalTerminalState.current
    val termWidth = terminal.size.columns

    val columns = max(1, termWidth / MIN_CARD_WIDTH)
    val cardWidth = termWidth / columns

    val containers = state.containers
    val totalRows = if (containers.isNotEmpty()) (containers.size + columns - 1) / columns else 0
    val scrollOffset = state.scrollOffset.coerceIn(0, (totalRows - maxVisibleRows).coerceAtLeast(0))

    Box(
        modifier = Modifier
            .width(termWidth)
            .onKeyEvent { event -> handleKeyEvent(event, onAction) },
    ) {
        Column(modifier = Modifier.width(termWidth)) {
            // Top status bar
            TopStatusBar(
                containerCount = containers.size,
                runningCount = containers.count { it.state == ContainerState.RUNNING },
                isConnected = state.isConnected,
                lastRefresh = state.lastRefresh,
            )

            if (containers.isEmpty() && state.isConnected) {
                Text(
                    "  No containers found.",
                    color = Color(140, 140, 140),
                )
            } else if (!state.isConnected) {
                Text(
                    "  Cannot connect to Docker. Is the Docker daemon running?",
                    color = Color.Red,
                )
            } else {
                // Scroll indicator: above
                if (scrollOffset > 0) {
                    Text("  ▲ ${scrollOffset} more row(s) above", color = Color(140, 140, 140))
                }

                // Container grid
                ContainerGrid(
                    containers = containers,
                    selectedIndex = state.selectedIndex,
                    columns = columns,
                    cardWidth = cardWidth,
                    scrollOffset = scrollOffset,
                    maxVisibleRows = maxVisibleRows,
                )

                // Scroll indicator: below
                val rowsBelow = totalRows - scrollOffset - maxVisibleRows
                if (rowsBelow > 0) {
                    Text("  ▼ ${rowsBelow} more row(s) below", color = Color(140, 140, 140))
                }
            }

            // Bottom bar
            val pendingMessage = state.pendingConfirmation?.message
            if (pendingMessage != null) {
                ConfirmBar(pendingMessage)
            } else {
                BottomActionBar(
                    activeOperation = state.activeOperation,
                    errorMessage = state.errorMessage,
                )
            }
        }
    }
}

private fun handleKeyEvent(event: KeyEvent, onAction: (UiAction) -> Unit): Boolean {
    when (event.key) {
        "ArrowUp", "k" -> onAction(UiAction.Move(Direction.UP))
        "ArrowDown", "j" -> onAction(UiAction.Move(Direction.DOWN))
        "ArrowLeft", "h" -> onAction(UiAction.Move(Direction.LEFT))
        "ArrowRight", "l" -> onAction(UiAction.Move(Direction.RIGHT))
        "u", "U" -> onAction(UiAction.PullAndRestart)
        "s", "S" -> onAction(UiAction.ToggleStartStop)
        "y", "Y" -> onAction(UiAction.Confirm)
        "n", "N" -> onAction(UiAction.Cancel)
        "q", "Q" -> onAction(UiAction.Quit)
        else -> return false
    }
    return true
}
