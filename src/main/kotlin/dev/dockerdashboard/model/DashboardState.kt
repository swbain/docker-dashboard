package dev.dockerdashboard.model

enum class ViewMode { GRID, DETAIL, LOGS }
enum class SortMode { NAME, STATE, CPU, MEMORY, CREATED }
enum class StateFilter { ALL, RUNNING, STOPPED }

data class StatsSnapshot(
    val cpuPercent: Double,
    val memoryUsageMb: Double,
    val timestamp: Long,
)

data class DashboardState(
    val containers: List<ContainerInfo> = emptyList(),
    val selectedIndex: Int = 0,
    val activeOperation: ActiveOperation? = null,
    val errorMessage: String? = null,

    val isConnected: Boolean = false,
    val isInitialLoading: Boolean = true,
    val pendingConfirmation: PendingConfirmation? = null,
    val scrollOffset: Int = 0, // Visual-row index into GridLayout.visualRows

    val viewMode: ViewMode = ViewMode.GRID,
    val filterText: String = "",
    val stateFilter: StateFilter = StateFilter.ALL,
    val sortMode: SortMode = SortMode.NAME,
    val selectedContainerIds: Set<String> = emptySet(),
    val statsHistory: Map<String, List<StatsSnapshot>> = emptyMap(),
    val logLines: List<String> = emptyList(),
    val logScrollOffset: Int = 0,
    val logContainerId: String? = null,
    val detailContainerId: String? = null,
    val detailScrollOffset: Int = 0,
    val isSearchMode: Boolean = false,
    val themeName: String = "Dark",
)

sealed interface PendingConfirmation {
    val message: String

    data class StopContainer(
        val containerId: String,
        val containerName: String,
    ) : PendingConfirmation {
        override val message = "Stop $containerName?"
    }

    data class PullAndRestart(
        val containerId: String,
        val containerName: String,
    ) : PendingConfirmation {
        override val message = "Pull update and restart $containerName?"
    }

    data class BulkStop(
        val containerIds: Set<String>,
    ) : PendingConfirmation {
        override val message = "Stop ${containerIds.size} selected containers?"
    }

    data class BulkUpdate(
        val containerIds: Set<String>,
    ) : PendingConfirmation {
        override val message = "Pull and restart ${containerIds.size} selected containers?"
    }

    data class PruneImages(
        val dummy: Unit = Unit,
    ) : PendingConfirmation {
        override val message = "Prune unused images?"
    }

    data class ShellExec(
        val containerId: String,
        val containerName: String,
    ) : PendingConfirmation {
        override val message = "Shell into $containerName?"
    }
}
