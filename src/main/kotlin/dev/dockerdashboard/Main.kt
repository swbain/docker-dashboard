package dev.dockerdashboard

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.jakewharton.mosaic.LocalTerminalState
import com.jakewharton.mosaic.runMosaicBlocking
import dev.dockerdashboard.model.ActiveOperation
import dev.dockerdashboard.model.ContainerInfo
import dev.dockerdashboard.model.ContainerState
import dev.dockerdashboard.service.DockerService
import dev.dockerdashboard.service.RegistryService
import dev.dockerdashboard.ui.DashboardApp
import dev.dockerdashboard.ui.Direction
import dev.dockerdashboard.ui.CARD_HEIGHT
import dev.dockerdashboard.ui.MIN_CARD_WIDTH
import dev.dockerdashboard.ui.UiAction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlin.math.max
import kotlin.system.exitProcess

fun main() {
    runMosaicBlocking {
        val dockerService = remember { DockerService() }
        val registryService = remember { RegistryService() }

        val containers = remember { mutableStateListOf<ContainerInfo>() }
        var selectedIndex by remember { mutableIntStateOf(0) }
        var activeOperation by remember { mutableStateOf<ActiveOperation?>(null) }
        var errorMessage by remember { mutableStateOf<String?>(null) }
        var lastRefresh by remember { mutableStateOf("") }
        var isConnected by remember { mutableStateOf(false) }
        var pendingConfirm by remember { mutableStateOf<String?>(null) }
        var pendingAction by remember { mutableStateOf<(() -> Unit)?>(null) }
        var scrollOffset by remember { mutableIntStateOf(0) }

        val scope = rememberCoroutineScope()
        val terminal = LocalTerminalState.current
        val columns = max(1, terminal.size.columns / MIN_CARD_WIDTH)
        val availableHeight = terminal.size.rows - 2 // top bar + bottom bar
        val maxVisibleRows = max(1, availableHeight / CARD_HEIGHT)

        // Container refresh loop (every 5s)
        LaunchedEffect(Unit) {
            while (isActive) {
                try {
                    val fetched = dockerService.listAllContainers()
                    val withStats = try {
                        dockerService.fetchStats(fetched)
                    } catch (_: Exception) {
                        fetched
                    }
                    containers.clear()
                    containers.addAll(withStats)
                    isConnected = true
                    lastRefresh = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"))
                    if (selectedIndex >= containers.size && containers.isNotEmpty()) {
                        selectedIndex = containers.size - 1
                    }
                    // Clamp scroll offset if containers were removed
                    val totalRows = (containers.size + columns - 1).coerceAtLeast(1) / columns
                    if (scrollOffset > (totalRows - maxVisibleRows).coerceAtLeast(0)) {
                        scrollOffset = (totalRows - maxVisibleRows).coerceAtLeast(0)
                    }
                } catch (e: Exception) {
                    isConnected = false
                    errorMessage = "Docker error: ${e.message?.take(60)}"
                }
                delay(5_000)
            }
        }

        // Update check loop (every 60s)
        LaunchedEffect(Unit) {
            delay(3_000)
            while (isActive) {
                try {
                    if (containers.isNotEmpty()) {
                        val updated = registryService.checkForUpdates(
                            containers.toList(), dockerService
                        )
                        containers.clear()
                        containers.addAll(updated)
                    }
                } catch (_: Exception) {}
                delay(60_000)
            }
        }

        // Error auto-clear
        LaunchedEffect(errorMessage) {
            if (errorMessage != null) {
                delay(5_000)
                errorMessage = null
            }
        }

        fun handleAction(action: UiAction) {
            when (action) {
                is UiAction.Move -> {
                    val count = containers.size
                    if (count == 0) return
                    selectedIndex = when (action.direction) {
                        Direction.UP -> (selectedIndex - columns).coerceAtLeast(0)
                        Direction.DOWN -> (selectedIndex + columns).coerceAtMost(count - 1)
                        Direction.LEFT -> (selectedIndex - 1).coerceAtLeast(0)
                        Direction.RIGHT -> (selectedIndex + 1).coerceAtMost(count - 1)
                    }
                    // Keep selected row in viewport
                    val selectedRow = selectedIndex / columns
                    if (selectedRow < scrollOffset) scrollOffset = selectedRow
                    if (selectedRow >= scrollOffset + maxVisibleRows) {
                        scrollOffset = selectedRow - maxVisibleRows + 1
                    }
                }

                is UiAction.ToggleStartStop -> {
                    val container = containers.getOrNull(selectedIndex) ?: return
                    if (activeOperation != null) return
                    when (container.state) {
                        ContainerState.RUNNING -> {
                            pendingConfirm = "Stop ${container.name}?"
                            pendingAction = {
                                scope.launch(Dispatchers.IO) {
                                    try {
                                        activeOperation =
                                            ActiveOperation.Stopping(container.name)
                                        pendingConfirm = null
                                        dockerService.stopContainer(container.id)
                                    } catch (e: Exception) {
                                        errorMessage =
                                            "Stop failed: ${e.message?.take(50)}"
                                    } finally {
                                        activeOperation = null
                                    }
                                }
                            }
                        }
                        ContainerState.EXITED, ContainerState.CREATED -> {
                            scope.launch(Dispatchers.IO) {
                                try {
                                    activeOperation =
                                        ActiveOperation.Starting(container.name)
                                    dockerService.startContainer(container.id)
                                } catch (e: Exception) {
                                    errorMessage =
                                        "Start failed: ${e.message?.take(50)}"
                                } finally {
                                    activeOperation = null
                                }
                            }
                        }
                        else -> {}
                    }
                }

                is UiAction.PullAndRestart -> {
                    val container = containers.getOrNull(selectedIndex) ?: return
                    if (activeOperation != null) return
                    pendingConfirm = "Pull update and restart ${container.name}?"
                    pendingAction = {
                        scope.launch(Dispatchers.IO) {
                            try {
                                activeOperation =
                                    ActiveOperation.Pulling(container.name)
                                pendingConfirm = null
                                dockerService.recreateContainer(container.id)
                            } catch (e: Exception) {
                                errorMessage =
                                    "Update failed: ${e.message?.take(50)}"
                            } finally {
                                activeOperation = null
                            }
                        }
                    }
                }

                is UiAction.Confirm -> {
                    pendingAction?.invoke()
                    pendingAction = null
                }

                is UiAction.Cancel -> {
                    pendingConfirm = null
                    pendingAction = null
                }

                is UiAction.Quit -> {
                    dockerService.close()
                    exitProcess(0)
                }
            }
        }

        DashboardApp(
            containers = containers,
            selectedIndex = selectedIndex,
            activeOperation = activeOperation,
            errorMessage = errorMessage,
            lastRefresh = lastRefresh,
            isConnected = isConnected,
            pendingConfirm = pendingConfirm,
            scrollOffset = scrollOffset,
            maxVisibleRows = maxVisibleRows,
            onAction = ::handleAction,
        )

        LaunchedEffect(Unit) { awaitCancellation() }
    }
}
