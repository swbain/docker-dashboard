package dev.dockerdashboard.service

import dev.dockerdashboard.model.ContainerInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URI

class RegistryService {

    suspend fun checkForUpdates(
        containers: List<ContainerInfo>,
        dockerService: DockerService,
    ): List<ContainerInfo> = coroutineScope {
        containers.map { container ->
            async(Dispatchers.IO) {
                try {
                    val updateAvailable = isUpdateAvailable(container, dockerService)
                    container.copy(updateAvailable = updateAvailable)
                } catch (_: Exception) {
                    container
                }
            }
        }.awaitAll()
    }

    private fun isUpdateAvailable(container: ContainerInfo, dockerService: DockerService): Boolean {
        val image = container.image
        if (image.startsWith("sha256:")) return false

        val parsed = parseDockerHubImage(image) ?: return false
        val (namespace, repo, tag) = parsed

        val localDigests = dockerService.getLocalImageDigests(container.imageId)
        if (localDigests.isEmpty()) return false

        val remoteDigest = fetchRemoteDigest(namespace, repo, tag) ?: return false

        // Compare: local RepoDigests are like "nginx@sha256:abc..."
        return localDigests.none { it.contains(remoteDigest) }
    }

    private fun fetchRemoteDigest(namespace: String, repo: String, tag: String): String? {
        val token = getAuthToken(namespace, repo) ?: return null
        val url = URI("https://registry-1.docker.io/v2/$namespace/$repo/manifests/$tag").toURL()
        val conn = url.openConnection() as HttpURLConnection
        return try {
            conn.requestMethod = "HEAD"
            conn.setRequestProperty("Authorization", "Bearer $token")
            conn.setRequestProperty(
                "Accept",
                "application/vnd.docker.distribution.manifest.v2+json, " +
                    "application/vnd.oci.image.manifest.v1+json"
            )
            conn.connectTimeout = 5000
            conn.readTimeout = 5000
            conn.instanceFollowRedirects = true
            conn.connect()
            if (conn.responseCode == 200) {
                conn.getHeaderField("Docker-Content-Digest")
            } else {
                null
            }
        } finally {
            conn.disconnect()
        }
    }

    private fun getAuthToken(namespace: String, repo: String): String? {
        val url = URI(
            "https://auth.docker.io/token?service=registry.docker.io&scope=repository:$namespace/$repo:pull"
        ).toURL()
        val conn = url.openConnection() as HttpURLConnection
        return try {
            conn.connectTimeout = 5000
            conn.readTimeout = 5000
            conn.connect()
            if (conn.responseCode == 200) {
                val body = conn.inputStream.bufferedReader().readText()
                // Simple JSON parsing — extract "token":"..." without a JSON library
                val tokenRegex = """"token"\s*:\s*"([^"]+)"""".toRegex()
                tokenRegex.find(body)?.groupValues?.get(1)
            } else {
                null
            }
        } finally {
            conn.disconnect()
        }
    }

    companion object {
        /**
         * Parse a Docker image reference into (namespace, repo, tag) for Docker Hub images.
         * Returns null for non-Docker Hub images.
         */
        fun parseDockerHubImage(image: String): Triple<String, String, String>? {
            // Skip images with explicit non-Docker Hub registries
            val parts = image.split("/")
            if (parts.size >= 2 && parts[0].contains(".")) {
                // Has a registry prefix like ghcr.io, quay.io, etc.
                return null
            }

            val (ref, tag) = run {
                val lastColon = image.lastIndexOf(':')
                val lastSlash = image.lastIndexOf('/')
                if (lastColon > lastSlash && lastColon > 0) {
                    image.substring(0, lastColon) to image.substring(lastColon + 1)
                } else {
                    image to "latest"
                }
            }

            return when {
                // Official images: "nginx", "nginx:latest"
                !ref.contains("/") -> Triple("library", ref, tag)
                // User images: "user/repo", "user/repo:tag"
                ref.count { it == '/' } == 1 -> {
                    val (namespace, repo) = ref.split("/")
                    Triple(namespace, repo, tag)
                }
                else -> null
            }
        }
    }
}
