package dev.dockerdashboard

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.dockerdashboard.model.ActiveOperation
import dev.dockerdashboard.model.ContainerInfo
import dev.dockerdashboard.model.ContainerState
import dev.dockerdashboard.model.DashboardState
import dev.dockerdashboard.model.PendingConfirmation
import dev.dockerdashboard.model.SortMode
import dev.dockerdashboard.model.StateFilter
import dev.dockerdashboard.model.StatsSnapshot
import dev.dockerdashboard.model.ViewMode
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

import kotlin.system.exitProcess

data class ExecRequest(val containerId: String, val containerName: String)

class DashboardStore(
    private val dockerService: DockerService,
    private val registryService: RegistryService,
    private val scope: CoroutineScope,
    private val onExecRequest: ((ExecRequest) -> Unit)? = null,
) {
    var state by mutableStateOf(DashboardState())
        private set

    val displayContainers: List<ContainerInfo>
        get() {
            var list = state.containers

            // Apply state filter
            list = when (state.stateFilter) {
                StateFilter.ALL -> list
                StateFilter.RUNNING -> list.filter { it.state == ContainerState.RUNNING }
                StateFilter.STOPPED -> list.filter { it.state != ContainerState.RUNNING }
            }

            // Apply text filter
            if (state.filterText.isNotBlank()) {
                val query = state.filterText.lowercase()
                list = list.filter {
                    it.name.lowercase().contains(query) || it.image.lowercase().contains(query)
                }
            }

            // Apply sort
            list = when (state.sortMode) {
                SortMode.NAME -> list.sortedBy { it.name.lowercase() }
                SortMode.STATE -> list.sortedBy { it.state.ordinal }
                SortMode.CPU -> list.sortedByDescending { it.cpuPercent }
                SortMode.MEMORY -> list.sortedByDescending { it.memoryUsageMb }
                SortMode.CREATED -> list.sortedByDescending { it.created }
            }

            return list
        }

    private data class UpdateInfo(
        val updateAvailable: Boolean,
        val localDigest: String?,
        val remoteDigest: String?,
    )

    @Volatile
    private var updateInfoMap: Map<String, UpdateInfo> = emptyMap()
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
            is UiAction.ViewLogs -> handleViewLogs()
            is UiAction.ViewDetail -> handleViewDetail()
            is UiAction.BackToGrid -> handleBackToGrid()
            is UiAction.ToggleSelect -> handleToggleSelect()
            is UiAction.TypeFilterChar -> handleTypeFilterChar(action.char)
            is UiAction.StartSearch -> handleStartSearch()
            is UiAction.CancelSearch -> handleCancelSearch()
            is UiAction.CycleStateFilter -> handleCycleStateFilter()
            is UiAction.CycleSortMode -> handleCycleSortMode()
            is UiAction.ShellExec -> handleShellExec()
            is UiAction.PruneImages -> handlePruneImages()
            is UiAction.ScrollUp -> handleScrollUp()
            is UiAction.ScrollDown -> handleScrollDown()
        }
    }

    private fun reduceMove(direction: Direction, columns: Int, maxVisibleRows: Int) {
        val s = state
        val count = displayContainers.size
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
        val container = displayContainers.getOrNull(s.selectedIndex) ?: return
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
        val container = displayContainers.getOrNull(s.selectedIndex) ?: return
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
                        val name = pending.containerName

                        state = state.copy(
                            activeOperation = ActiveOperation.Stopping(name),
                        )
                        val params = dockerService.inspectAndStop(pending.containerId)

                        state = state.copy(
                            activeOperation = ActiveOperation.Pulling(name),
                        )
                        dockerService.pullImage(params.image)

                        state = state.copy(
                            activeOperation = ActiveOperation.Creating(name),
                        )
                        val newId = dockerService.recreateFromParams(params)

                        state = state.copy(
                            activeOperation = ActiveOperation.Starting(name),
                        )
                        dockerService.startContainer(newId)
                    } catch (e: Exception) {
                        setError("Update failed: ${e.message?.take(50)}")
                    } finally {
                        state = state.copy(activeOperation = null)
                    }
                }
            }
            is PendingConfirmation.BulkStop -> { /* implemented by bulk ops teammate */ }
            is PendingConfirmation.BulkUpdate -> { /* implemented by bulk ops teammate */ }
            is PendingConfirmation.PruneImages -> { /* implemented by image cleanup teammate */ }
        }
    }

    // --- Skeleton handlers for new actions (implemented by feature teammates) ---

    private fun handleViewLogs() {
        // Teammate A implements
    }

    private fun handleViewDetail() {
        // Teammate B implements
    }

    private fun handleBackToGrid() {
        state = state.copy(
            viewMode = ViewMode.GRID,
            logLines = emptyList(),
            logContainerId = null,
            logScrollOffset = 0,
            detailContainerId = null,
            detailScrollOffset = 0,
        )
    }

    private fun handleToggleSelect() {
        // Teammate I implements
    }

    private fun handleTypeFilterChar(char: Char) {
        // Teammate F implements
    }

    private fun handleStartSearch() {
        // Teammate F implements
    }

    private fun handleCancelSearch() {
        // Teammate F implements
    }

    private fun handleCycleStateFilter() {
        // Teammate F implements
    }

    private fun handleCycleSortMode() {
        // Teammate G implements
    }

    private fun handleShellExec() {
        // Teammate E implements
    }

    private fun handlePruneImages() {
        // Teammate J implements
    }

    private fun handleScrollUp() {
        val s = state
        when (s.viewMode) {
            ViewMode.LOGS -> state = s.copy(logScrollOffset = (s.logScrollOffset - 1).coerceAtLeast(0))
            ViewMode.DETAIL -> state = s.copy(detailScrollOffset = (s.detailScrollOffset - 1).coerceAtLeast(0))
            else -> {}
        }
    }

    private fun handleScrollDown() {
        val s = state
        when (s.viewMode) {
            ViewMode.LOGS -> state = s.copy(logScrollOffset = s.logScrollOffset + 1)
            ViewMode.DETAIL -> state = s.copy(detailScrollOffset = s.detailScrollOffset + 1)
            else -> {}
        }
    }

    // --- Data loops ---

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
                        val knownInfo = updateInfoMap
                        val merged = withStats.map { c ->
                            val info = knownInfo[c.id]
                            c.copy(
                                updateAvailable = info?.updateAvailable ?: false,
                                localDigest = info?.localDigest,
                                remoteDigest = info?.remoteDigest,
                            )
                        }

                        // Append stats snapshots to rolling history (max 30 per container)
                        val now = System.currentTimeMillis()
                        val newHistory = s.statsHistory.toMutableMap()
                        for (c in merged) {
                            if (c.state == ContainerState.RUNNING && c.memoryLimitMb > 0) {
                                val snap = StatsSnapshot(c.cpuPercent, c.memoryUsageMb, now)
                                val existing = newHistory[c.id] ?: emptyList()
                                newHistory[c.id] = (existing + snap).takeLast(30)
                            }
                        }

                        s.copy(
                            containers = merged,
                            isConnected = true,
                            isInitialLoading = false,
                            selectedIndex = newIndex,
                            statsHistory = newHistory,
                        )
                    }
                } catch (e: Exception) {
                    state = state.copy(
                        isConnected = false,
                        isInitialLoading = false,
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
                        val checked = registryService.checkForUpdates(current, dockerService)
                        updateInfoMap = checked.associate { c ->
                            c.id to UpdateInfo(c.updateAvailable, c.localDigest, c.remoteDigest)
                        }
                        state = state.let { s ->
                            s.copy(containers = s.containers.map { c ->
                                val info = updateInfoMap[c.id]
                                c.copy(
                                    updateAvailable = info?.updateAvailable ?: false,
                                    localDigest = info?.localDigest,
                                    remoteDigest = info?.remoteDigest,
                                )
                            })
                        }
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
