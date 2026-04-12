package dev.dockerdashboard.model

data class VolumeMount(
    val source: String,
    val destination: String,
    val mode: String,
)

data class ContainerInfo(
    val id: String,
    val name: String,
    val image: String,
    val imageId: String,
    val status: String,
    val state: ContainerState,
    val ports: String,
    val cpuPercent: Double = 0.0,
    val memoryUsageMb: Double = 0.0,
    val memoryLimitMb: Double = 0.0,
    val updateAvailable: Boolean = false,
    val localDigest: String? = null,
    val remoteDigest: String? = null,
    val created: Long = 0L,
    val healthStatus: String? = null,
    val volumes: List<VolumeMount> = emptyList(),
    val composeProject: String? = null,
    val composeService: String? = null,
    val labels: Map<String, String> = emptyMap(),
    val env: List<String> = emptyList(),
    val networks: List<String> = emptyList(),
    val restartPolicy: String? = null,
    val command: String? = null,
    val exitCode: Int? = null,
)

enum class ContainerState(val display: String) {
    RUNNING("Running"),
    PAUSED("Paused"),
    RESTARTING("Restarting"),
    EXITED("Exited"),
    DEAD("Dead"),
    CREATED("Created"),
    REMOVING("Removing"),
    UNKNOWN("Unknown");

    companion object {
        fun fromString(s: String): ContainerState = when (s.lowercase()) {
            "running" -> RUNNING
            "paused" -> PAUSED
            "restarting" -> RESTARTING
            "exited" -> EXITED
            "dead" -> DEAD
            "created" -> CREATED
            "removing" -> REMOVING
            else -> UNKNOWN
        }
    }
}

sealed interface ActiveOperation {
    val containerName: String
    data class Pulling(override val containerName: String) : ActiveOperation
    data class Restarting(override val containerName: String) : ActiveOperation
    data class Stopping(override val containerName: String) : ActiveOperation
    data class Starting(override val containerName: String) : ActiveOperation
    data class Creating(override val containerName: String) : ActiveOperation
    data class Pruning(override val containerName: String = "") : ActiveOperation
    data class BulkStopping(val current: Int, val total: Int, override val containerName: String = "") : ActiveOperation
    data class BulkStarting(val current: Int, val total: Int, override val containerName: String = "") : ActiveOperation
    data class BulkPulling(val current: Int, val total: Int, override val containerName: String = "") : ActiveOperation
}
