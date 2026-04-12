package dev.dockerdashboard

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import com.jakewharton.mosaic.LocalTerminalState
import com.jakewharton.mosaic.runMosaicBlocking
import dev.dockerdashboard.service.DockerService
import dev.dockerdashboard.service.RegistryService
import dev.dockerdashboard.ui.CARD_HEIGHT
import dev.dockerdashboard.ui.DashboardApp
import dev.dockerdashboard.ui.MIN_CARD_WIDTH
import kotlinx.coroutines.Job
import kotlin.math.max

fun main() {
    var execRequest: ExecRequest? = null
    while (true) {
        runMosaicBlocking {
            val keepAliveJob = remember { Job() }
            val dockerService = remember { DockerService() }
            val registryService = remember { RegistryService() }
            val scope = rememberCoroutineScope()
            val store = remember {
                DashboardStore(dockerService, registryService, scope) { req ->
                    execRequest = req
                    keepAliveJob.cancel()
                }
            }

            LaunchedEffect(Unit) { store.start() }

            val terminal = LocalTerminalState.current
            val columns = max(1, terminal.size.columns / MIN_CARD_WIDTH)
            val availableHeight = terminal.size.rows - 2
            val maxVisibleRows = max(1, availableHeight / CARD_HEIGHT)

            DashboardApp(
                state = store.state,
                displayContainers = store.displayContainers,
                detailData = store.detailData,
                maxVisibleRows = maxVisibleRows,
                onAction = { action -> store.dispatch(action, columns, maxVisibleRows) },
            )

            LaunchedEffect(Unit) { keepAliveJob.join() }
        }
        val req = execRequest ?: break
        execRequest = null
        ProcessBuilder("docker", "exec", "-it", req.containerId, "/bin/sh")
            .inheritIO().start().waitFor()
    }
}
