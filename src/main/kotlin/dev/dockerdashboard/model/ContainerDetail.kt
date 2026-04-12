package dev.dockerdashboard.model

data class ContainerDetail(
    val id: String,
    val name: String,
    val image: String,
    val status: String,
    val created: String,
    val command: String,
    val entrypoint: String,
    val restartPolicy: String,
    val env: List<String>,
    val networks: List<String>,
    val volumes: List<VolumeMount>,
    val labels: Map<String, String>,
    val exitCode: Int?,
)
