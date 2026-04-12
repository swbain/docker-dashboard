package dev.dockerdashboard

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.dockerdashboard.model.ActiveOperation
import dev.dockerdashboard.model.ContainerState
import dev.dockerdashboard.model.DashboardState
import dev.dockerdashboard.model.PendingConfirmation
import dev.dockerdashboard.service.DockerService
import dev.dockerdashboard.service.RegistryService
import dev.dockerdashboard.ui.Direction
import dev.dockerdashboard.ui.UiAction
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlin.system.exitProcess

class DashboardStore(
    private val dockerService: DockerService,
    private val registryService: RegistryService,
    private val scope: CoroutineScope,
) {
    var state by mutableStateOf(DashboardState())
        private set

    private var errorClearJob: Job? = null

    fun start() {
        startContainerRefreshLoop()
        startUpdateCheckLoop()
    }

    fun dispatch(action: UiAction, columns: Int, maxVisibleRows: Int) {
        when (action) {
            is UiAction.Move -> reduceMove(action.direction, columns, maxVisibleRows)
            is UiAction.ToggleStartStop -> handleToggleStartStop()
            is UiAction.PullAndRestart -> handlePullAndRestart()
            is UiAction.Confirm -> handleConfirm()
            is UiAction.Cancel -> state = state.copy(pendingConfirmation = null)
            is UiAction.Quit -> {
                dockerService.close()
                exitProcess(0)
            }
        }
    }

    private fun reduceMove(direction: Direction, columns: Int, maxVisibleRows: Int) {
        val s = state
        val count = s.containers.size
        if (count == 0) return
        val newIndex = when (direction) {
            Direction.UP -> (s.selectedIndex - columns).coerceAtLeast(0)
            Direction.DOWN -> (s.selectedIndex + columns).coerceAtMost(count - 1)
            Direction.LEFT -> (s.selectedIndex - 1).coerceAtLeast(0)
            Direction.RIGHT -> (s.selectedIndex + 1).coerceAtMost(count - 1)
        }
        val selectedRow = newIndex / columns
        val newScroll = when {
            selectedRow < s.scrollOffset -> selectedRow
            selectedRow >= s.scrollOffset + maxVisibleRows -> selectedRow - maxVisibleRows + 1
            else -> s.scrollOffset
        }
        state = s.copy(selectedIndex = newIndex, scrollOffset = newScroll)
    }

    private fun handleToggleStartStop() {
        val s = state
        val container = s.containers.getOrNull(s.selectedIndex) ?: return
        if (s.activeOperation != null) return

        when (container.state) {
            ContainerState.RUNNING -> {
                state = s.copy(
                    pendingConfirmation = PendingConfirmation.StopContainer(
                        containerId = container.id,
                        containerName = container.name,
                    ),
                )
            }
            ContainerState.EXITED, ContainerState.CREATED -> {
                scope.launch(Dispatchers.IO) {
                    try {
                        state = state.copy(
                            activeOperation = ActiveOperation.Starting(container.name),
                        )
                        dockerService.startContainer(container.id)
                    } catch (e: Exception) {
                        setError("Start failed: ${e.message?.take(50)}")
                    } finally {
                        state = state.copy(activeOperation = null)
                    }
                }
            }
            else -> {}
        }
    }

    private fun handlePullAndRestart() {
        val s = state
        val container = s.containers.getOrNull(s.selectedIndex) ?: return
        if (s.activeOperation != null) return

        state = s.copy(
            pendingConfirmation = PendingConfirmation.PullAndRestart(
                containerId = container.id,
                containerName = container.name,
            ),
        )
    }

    private fun handleConfirm() {
        val pending = state.pendingConfirmation ?: return
        state = state.copy(pendingConfirmation = null)

        when (pending) {
            is PendingConfirmation.StopContainer -> {
                scope.launch(Dispatchers.IO) {
                    try {
                        state = state.copy(
                            activeOperation = ActiveOperation.Stopping(pending.containerName),
                        )
                        dockerService.stopContainer(pending.containerId)
                    } catch (e: Exception) {
                        setError("Stop failed: ${e.message?.take(50)}")
                    } finally {
                        state = state.copy(activeOperation = null)
                    }
                }
            }
            is PendingConfirmation.PullAndRestart -> {
                scope.launch(Dispatchers.IO) {
                    try {
                        state = state.copy(
                            activeOperation = ActiveOperation.Pulling(pending.containerName),
                        )
                        dockerService.recreateContainer(pending.containerId)
                    } catch (e: Exception) {
                        setError("Update failed: ${e.message?.take(50)}")
                    } finally {
                        state = state.copy(activeOperation = null)
                    }
                }
            }
        }
    }

    private fun startContainerRefreshLoop() {
        scope.launch {
            while (isActive) {
                try {
                    val fetched = dockerService.listAllContainers()
                    val withStats = try {
                        dockerService.fetchStats(fetched)
                    } catch (_: Exception) {
                        fetched
                    }
                    state = state.let { s ->
                        val newIndex = if (s.selectedIndex >= withStats.size && withStats.isNotEmpty()) {
                            withStats.size - 1
                        } else {
                            s.selectedIndex
                        }
                        s.copy(
                            containers = withStats,
                            isConnected = true,
                            lastRefresh = LocalTime.now().format(
                                DateTimeFormatter.ofPattern("HH:mm:ss"),
                            ),
                            selectedIndex = newIndex,
                        )
                    }
                } catch (e: Exception) {
                    state = state.copy(
                        isConnected = false,
                        errorMessage = "Docker error: ${e.message?.take(60)}",
                    )
                    scheduleErrorClear()
                }
                delay(5_000)
            }
        }
    }

    private fun startUpdateCheckLoop() {
        scope.launch {
            delay(3_000)
            while (isActive) {
                try {
                    val current = state.containers
                    if (current.isNotEmpty()) {
                        val updated = registryService.checkForUpdates(current, dockerService)
                        state = state.copy(containers = updated)
                    }
                } catch (_: Exception) {}
                delay(60_000)
            }
        }
    }

    private fun setError(message: String) {
        state = state.copy(errorMessage = message)
        scheduleErrorClear()
    }

    private fun scheduleErrorClear() {
        errorClearJob?.cancel()
        errorClearJob = scope.launch {
            delay(5_000)
            state = state.copy(errorMessage = null)
        }
    }
}
