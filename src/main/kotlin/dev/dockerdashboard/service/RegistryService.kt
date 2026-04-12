package dev.dockerdashboard.service

import dev.dockerdashboard.model.ContainerInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import java.net.HttpURLConnection
import java.net.URI

class RegistryService {

    private data class UpdateCheckResult(
        val updateAvailable: Boolean,
        val localDigest: String?,
        val remoteDigest: String?,
    )

    suspend fun checkForUpdates(
        containers: List<ContainerInfo>,
        dockerService: DockerService,
    ): List<ContainerInfo> = coroutineScope {
        containers.map { container ->
            async(Dispatchers.IO) {
                try {
                    val result = checkUpdate(container, dockerService)
                    container.copy(
                        updateAvailable = result.updateAvailable,
                        localDigest = result.localDigest,
                        remoteDigest = result.remoteDigest,
                    )
                } catch (_: Exception) {
                    container
                }
            }
        }.awaitAll()
    }

    private fun checkUpdate(container: ContainerInfo, dockerService: DockerService): UpdateCheckResult {
        val image = container.image
        if (image.startsWith("sha256:")) return UpdateCheckResult(false, null, null)

        val (registry, repo, tag) = parseImageReference(image)
            ?: return UpdateCheckResult(false, null, null)

        val isLatest = tag == "latest"

        val localDigests = dockerService.getLocalImageDigests(container.imageId)
        if (localDigests.isEmpty()) return UpdateCheckResult(false, null, null)

        val localDigest = localDigests.firstOrNull()
            ?.substringAfter("@", "")
            ?.takeIf { it.isNotEmpty() }

        val remoteDigest = fetchRemoteDigest(registry, repo, tag)
            ?: return UpdateCheckResult(false, localDigest.takeIf { isLatest }, null)

        val updateAvailable = localDigests.none { it.contains(remoteDigest) }

        return UpdateCheckResult(
            updateAvailable = updateAvailable,
            localDigest = localDigest.takeIf { isLatest },
            remoteDigest = remoteDigest.takeIf { isLatest && updateAvailable },
        )
    }

    private fun fetchRemoteDigest(registry: String, repo: String, tag: String): String? {
        val manifestUrl = "https://$registry/v2/$repo/manifests/$tag"

        // Phase 1: Unauthenticated HEAD — some registries allow this
        val conn = URI(manifestUrl).toURL().openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "HEAD"
            conn.setRequestProperty("Accept", MANIFEST_ACCEPT)
            conn.connectTimeout = 5000
            conn.readTimeout = 5000
            conn.instanceFollowRedirects = true
            conn.connect()

            if (conn.responseCode == 200) {
                return conn.getHeaderField("Docker-Content-Digest")
            }
            if (conn.responseCode != 401) return null

            // Phase 2: Parse WWW-Authenticate challenge and fetch token
            val wwwAuth = conn.getHeaderField("WWW-Authenticate") ?: return null
            val token = getAuthTokenFromChallenge(wwwAuth) ?: return null

            // Phase 3: Retry with Bearer token
            return fetchDigestWithToken(manifestUrl, token)
        } finally {
            conn.disconnect()
        }
    }

    private fun fetchDigestWithToken(manifestUrl: String, token: String): String? {
        val conn = URI(manifestUrl).toURL().openConnection() as HttpURLConnection
        return try {
            conn.requestMethod = "HEAD"
            conn.setRequestProperty("Authorization", "Bearer $token")
            conn.setRequestProperty("Accept", MANIFEST_ACCEPT)
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

    private fun getAuthTokenFromChallenge(wwwAuthenticate: String): String? {
        if (!wwwAuthenticate.startsWith("Bearer ", ignoreCase = true)) return null

        val params = wwwAuthenticate.substring(7)
        val paramRegex = """(\w+)="([^"]*)"""".toRegex()
        val paramMap = paramRegex.findAll(params).associate { it.groupValues[1] to it.groupValues[2] }

        val realm = paramMap["realm"] ?: return null
        val queryParts = mutableListOf<String>()
        paramMap["service"]?.let { queryParts.add("service=$it") }
        paramMap["scope"]?.let { queryParts.add("scope=$it") }

        val separator = if ("?" in realm) "&" else "?"
        val tokenUrl = if (queryParts.isNotEmpty()) {
            "$realm$separator${queryParts.joinToString("&")}"
        } else {
            realm
        }

        val conn = URI(tokenUrl).toURL().openConnection() as HttpURLConnection
        return try {
            conn.connectTimeout = 5000
            conn.readTimeout = 5000
            conn.connect()
            if (conn.responseCode == 200) {
                val body = conn.inputStream.bufferedReader().readText()
                val tokenRegex = """"(?:token|access_token)"\s*:\s*"([^"]+)"""".toRegex()
                tokenRegex.find(body)?.groupValues?.get(1)
            } else {
                null
            }
        } finally {
            conn.disconnect()
        }
    }

    companion object {
        private val MANIFEST_ACCEPT = listOf(
            "application/vnd.docker.distribution.manifest.v2+json",
            "application/vnd.oci.image.manifest.v1+json",
            "application/vnd.docker.distribution.manifest.list.v2+json",
            "application/vnd.oci.image.index.v1+json",
        ).joinToString(", ")

        /**
         * Parse a Docker image reference into (registry, repo, tag).
         * Returns null for unparseable references.
         */
        fun parseImageReference(image: String): Triple<String, String, String>? {
            val (ref, tag) = run {
                val lastColon = image.lastIndexOf(':')
                val lastSlash = image.lastIndexOf('/')
                if (lastColon > lastSlash && lastColon > 0) {
                    image.substring(0, lastColon) to image.substring(lastColon + 1)
                } else {
                    image to "latest"
                }
            }

            val parts = ref.split("/")
            return when {
                // Official Docker Hub images: "nginx", "nginx:latest"
                parts.size == 1 -> Triple("registry-1.docker.io", "library/${parts[0]}", tag)
                // Docker Hub user images: "user/repo" (no dot in first segment)
                parts.size == 2 && !parts[0].contains(".") ->
                    Triple("registry-1.docker.io", ref, tag)
                // Explicit registry: "lscr.io/linuxserver/plex", "ghcr.io/org/repo"
                parts[0].contains(".") -> {
                    val repo = parts.drop(1).joinToString("/")
                    Triple(parts[0], repo, tag)
                }
                else -> null
            }
        }
    }
}
