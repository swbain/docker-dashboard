package dev.dockerdashboard.service

import com.github.dockerjava.api.DockerClient
import com.github.dockerjava.api.async.ResultCallback
import com.github.dockerjava.api.model.ContainerNetwork
import com.github.dockerjava.api.model.ExposedPort
import com.github.dockerjava.api.model.HostConfig
import com.github.dockerjava.api.model.Statistics
import com.github.dockerjava.core.DefaultDockerClientConfig
import com.github.dockerjava.core.DockerClientImpl
import com.github.dockerjava.transport.DockerHttpClient
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient
import dev.dockerdashboard.model.ContainerInfo
import dev.dockerdashboard.model.ContainerState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.Closeable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class DockerService : Closeable {

    private val config = DefaultDockerClientConfig.createDefaultConfigBuilder().build()

    private val httpClient: DockerHttpClient = ApacheDockerHttpClient.Builder()
        .dockerHost(config.dockerHost)
        .sslConfig(config.sslConfig)
        .build()

    private val client: DockerClient = DockerClientImpl.getInstance(config, httpClient)

    suspend fun listAllContainers(): List<ContainerInfo> = withContext(Dispatchers.IO) {
        val containers = client.listContainersCmd()
            .withShowAll(true)
            .withShowSize(false)
            .exec()

        containers.map { container ->
            val name = container.names
                ?.firstOrNull()
                ?.removePrefix("/")
                ?: container.id.take(12)

            val ports = container.ports
                ?.filter { it.publicPort != null }
                ?.joinToString(", ") { "${it.publicPort}->${it.privatePort}/${it.type}" }
                ?: ""

            ContainerInfo(
                id = container.id,
                name = name,
                image = container.image ?: "unknown",
                imageId = container.imageId ?: "",
                status = container.status ?: "unknown",
                state = ContainerState.fromString(container.state ?: "unknown"),
                ports = ports,
                created = container.created ?: 0L,
            )
        }
    }

    suspend fun fetchStats(containers: List<ContainerInfo>): List<ContainerInfo> = coroutineScope {
        containers.map { container ->
            async(Dispatchers.IO) {
                if (container.state != ContainerState.RUNNING) return@async container
                val stats = withTimeoutOrNull(2000L) { getOneShotStats(container.id) }
                    ?: return@async container
                container.copy(
                    cpuPercent = calculateCpuPercent(stats),
                    memoryUsageMb = (stats.memoryStats?.usage ?: 0L) / 1_048_576.0,
                    memoryLimitMb = (stats.memoryStats?.limit ?: 0L) / 1_048_576.0,
                )
            }
        }.awaitAll()
    }

    private fun getOneShotStats(containerId: String): Statistics {
        val latch = CountDownLatch(1)
        var result: Statistics? = null
        client.statsCmd(containerId).withNoStream(true)
            .exec(object : ResultCallback<Statistics> {
                override fun onStart(closeable: Closeable?) {}
                override fun onNext(stats: Statistics) { result = stats; latch.countDown() }
                override fun onError(throwable: Throwable) { latch.countDown() }
                override fun onComplete() { latch.countDown() }
                override fun close() {}
            })
        latch.await(3, TimeUnit.SECONDS)
        return result ?: throw RuntimeException("No stats received")
    }

    private fun calculateCpuPercent(stats: Statistics): Double {
        val cpuStats = stats.cpuStats ?: return 0.0
        val preCpuStats = stats.preCpuStats ?: return 0.0
        val cpuDelta = (cpuStats.cpuUsage?.totalUsage ?: 0L) -
            (preCpuStats.cpuUsage?.totalUsage ?: 0L)
        val systemDelta = (cpuStats.systemCpuUsage ?: 0L) -
            (preCpuStats.systemCpuUsage ?: 0L)
        if (systemDelta <= 0 || cpuDelta <= 0) return 0.0
        val numCpus = cpuStats.onlineCpus ?: cpuStats.cpuUsage?.percpuUsage?.size?.toLong() ?: 1L
        return (cpuDelta.toDouble() / systemDelta.toDouble()) * numCpus * 100.0
    }

    suspend fun pullImage(image: String): Unit = withContext(Dispatchers.IO) {
        val (repo, tag) = parseImageRef(image)
        client.pullImageCmd(repo)
            .withTag(tag)
            .start()
            .awaitCompletion(5, TimeUnit.MINUTES)
    }

    suspend fun stopContainer(containerId: String): Unit = withContext(Dispatchers.IO) {
        client.stopContainerCmd(containerId).withTimeout(10).exec()
    }

    suspend fun startContainer(containerId: String): Unit = withContext(Dispatchers.IO) {
        client.startContainerCmd(containerId).exec()
    }

    suspend fun inspectAndStop(containerId: String): RecreateParams = withContext(Dispatchers.IO) {
        val inspect = client.inspectContainerCmd(containerId).exec()
        val config = inspect.config ?: throw RuntimeException("No config for container $containerId")
        val hostConfig = inspect.hostConfig
        val name = inspect.name?.removePrefix("/") ?: throw RuntimeException("No name for container")
        val image = config.image ?: throw RuntimeException("No image for container")

        try { client.stopContainerCmd(containerId).withTimeout(10).exec() } catch (_: Exception) {}
        client.removeContainerCmd(containerId).withForce(true).exec()

        RecreateParams(
            name = name,
            image = image,
            env = config.env,
            labels = config.labels,
            exposedPorts = config.exposedPorts,
            entrypoint = config.entrypoint,
            cmd = config.cmd,
            hostConfig = hostConfig,
            networks = inspect.networkSettings?.networks,
        )
    }

    suspend fun recreateFromParams(params: RecreateParams): String = withContext(Dispatchers.IO) {
        val createCmd = client.createContainerCmd(params.image).withName(params.name)

        params.env?.let { createCmd.withEnv(*it) }
        params.labels?.let { createCmd.withLabels(it) }
        params.exposedPorts?.let { createCmd.withExposedPorts(*it) }
        params.entrypoint?.let { createCmd.withEntrypoint(*it) }
        params.cmd?.let { createCmd.withCmd(*it) }
        params.hostConfig?.let { createCmd.withHostConfig(it) }

        val newContainer = createCmd.exec()

        params.networks?.forEach { (networkName, network) ->
            if (networkName != "bridge" && networkName != "host" && networkName != "none") {
                val networkId = network.networkID ?: return@forEach
                try {
                    client.connectToNetworkCmd()
                        .withContainerId(newContainer.id)
                        .withNetworkId(networkId)
                        .exec()
                } catch (_: Exception) {}
            }
        }

        newContainer.id
    }

    fun getLocalImageDigests(imageId: String): List<String> {
        return try {
            val inspect = client.inspectImageCmd(imageId).exec()
            inspect.repoDigests?.toList() ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    override fun close() {
        client.close()
    }

    companion object {
        fun parseImageRef(image: String): Pair<String, String> {
            val lastColon = image.lastIndexOf(':')
            val lastSlash = image.lastIndexOf('/')
            return if (lastColon > lastSlash && lastColon > 0) {
                image.substring(0, lastColon) to image.substring(lastColon + 1)
            } else {
                image to "latest"
            }
        }
    }
}

data class RecreateParams(
    val name: String,
    val image: String,
    val env: Array<String>?,
    val labels: Map<String, String>?,
    val exposedPorts: Array<ExposedPort>?,
    val entrypoint: Array<String>?,
    val cmd: Array<String>?,
    val hostConfig: HostConfig?,
    val networks: Map<String, ContainerNetwork>?,
)
