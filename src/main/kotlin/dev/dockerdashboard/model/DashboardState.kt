package dev.dockerdashboard.model

data class DashboardState(
    val containers: List<ContainerInfo> = emptyList(),
    val selectedIndex: Int = 0,
    val activeOperation: ActiveOperation? = null,
    val errorMessage: String? = null,
    val lastRefresh: String = "",
    val isConnected: Boolean = false,
    val pendingConfirmation: PendingConfirmation? = null,
    val scrollOffset: Int = 0,
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
}
