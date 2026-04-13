package dev.dockerdashboard

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import com.jakewharton.mosaic.LocalTerminalState
import com.jakewharton.mosaic.tty.Tty
import com.jakewharton.mosaic.tty.terminal.asTerminalIn
import dev.dockerdashboard.service.DockerService
import dev.dockerdashboard.service.RegistryService
import dev.dockerdashboard.ui.DashboardApp
import dev.dockerdashboard.ui.MIN_CARD_WIDTH
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlin.math.max

fun main() {
    val tty = Tty.tryBind() ?: run {
        System.err.println("Not running in an interactive terminal.")
        return
    }

    var execRequest: ExecRequest? = null

    try {
        while (true) {
            runBlocking {
                val terminal = tty.asTerminalIn(this)
                terminal.use {
                    runDashboardComposition(terminal) {
                        val keepAliveJob = remember { Job() }
                        val dockerService = remember { DockerService() }
                        val registryService = remember { RegistryService() }
                        val scope = rememberCoroutineScope()
                        val store = remember {
                            DashboardStore(dockerService, registryService, scope) { req ->
                                execRequest = req
                                scope.cancel()
                                keepAliveJob.cancel()
                            }
                        }

                        LaunchedEffect(Unit) { store.start() }

                        val termState = LocalTerminalState.current
                        val columns = max(1, termState.size.columns / MIN_CARD_WIDTH)
                        val availableHeight = termState.size.rows - 2

                        DashboardApp(
                            state = store.state,
                            displayContainers = store.displayContainers,
                            detailData = store.detailData,
                            columns = columns,
                            availableHeight = availableHeight,
                            onAction = { action -> store.dispatch(action, columns, availableHeight) },
                        )

                        LaunchedEffect(Unit) { keepAliveJob.join() }
                    }
                }
            }

            val req = execRequest ?: break
            execRequest = null
            ProcessBuilder("docker", "exec", "-it", req.containerId, "/bin/sh")
                .inheritIO().start().waitFor()
        }
    } finally {
        tty.close()
    }
}
