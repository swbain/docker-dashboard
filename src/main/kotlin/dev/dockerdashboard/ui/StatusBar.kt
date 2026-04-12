package dev.dockerdashboard.ui

import androidx.compose.runtime.Composable
import com.jakewharton.mosaic.layout.background
import com.jakewharton.mosaic.layout.fillMaxWidth
import com.jakewharton.mosaic.layout.padding
import com.jakewharton.mosaic.modifier.Modifier
import com.jakewharton.mosaic.text.SpanStyle
import com.jakewharton.mosaic.text.buildAnnotatedString
import com.jakewharton.mosaic.ui.Arrangement
import com.jakewharton.mosaic.ui.Color
import com.jakewharton.mosaic.ui.Row
import com.jakewharton.mosaic.ui.Text
import com.jakewharton.mosaic.ui.TextStyle
import dev.dockerdashboard.model.ActiveOperation

private val BAR_BG = Color(30, 30, 30)
private val ACCENT = Color(80, 200, 255)

@Composable
fun TopStatusBar(
    containerCount: Int,
    runningCount: Int,
    isConnected: Boolean,
    isInitialLoading: Boolean,
    lastRefresh: String,
    modifier: Modifier = Modifier,
) {
    val spinner = rememberSpinner(isInitialLoading)

    Row(
        modifier = modifier.fillMaxWidth().background(BAR_BG).padding(horizontal = 1),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        val title = buildAnnotatedString {
            pushStyle(SpanStyle(color = ACCENT, textStyle = TextStyle.Bold))
            append("Docker Dashboard")
            pop()
            append("  ")
            when {
                isInitialLoading -> {
                    pushStyle(SpanStyle(color = Color.Yellow))
                    append("$spinner")
                    pop()
                    append(" Connecting...")
                }
                isConnected -> {
                    pushStyle(SpanStyle(color = Color.Green))
                    append("●")
                    pop()
                    append(" Connected")
                }
                else -> {
                    pushStyle(SpanStyle(color = Color.Red))
                    append("●")
                    pop()
                    append(" Disconnected")
                }
            }
        }
        Text(title)

        val stats = buildAnnotatedString {
            pushStyle(SpanStyle(color = Color.White))
            append("$runningCount")
            pop()
            pushStyle(SpanStyle(color = Color(140, 140, 140)))
            append("/$containerCount running")
            pop()
            if (lastRefresh.isNotEmpty()) {
                append("  $lastRefresh")
            }
        }
        Text(stats)
    }
}

@Composable
fun BottomActionBar(
    activeOperation: ActiveOperation?,
    errorMessage: String?,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth().background(BAR_BG).padding(horizontal = 1),
    ) {
        when {
            errorMessage != null -> {
                Text(errorMessage, color = Color.Red)
            }
            activeOperation != null -> {
                val msg = when (activeOperation) {
                    is ActiveOperation.Pulling -> "Pulling image for ${activeOperation.containerName}..."
                    is ActiveOperation.Restarting -> "Restarting ${activeOperation.containerName}..."
                    is ActiveOperation.Stopping -> "Stopping ${activeOperation.containerName}..."
                    is ActiveOperation.Starting -> "Starting ${activeOperation.containerName}..."
                    is ActiveOperation.Creating -> "Creating container for ${activeOperation.containerName}..."
                }
                Text(msg, color = Color.Yellow)
            }
            else -> {
                val hints = buildAnnotatedString {
                    pushStyle(SpanStyle(color = ACCENT, textStyle = TextStyle.Bold))
                    append("arrows")
                    pop()
                    pushStyle(SpanStyle(color = Color(160, 160, 160)))
                    append(" navigate  ")
                    pop()

                    pushStyle(SpanStyle(color = ACCENT, textStyle = TextStyle.Bold))
                    append("u")
                    pop()
                    pushStyle(SpanStyle(color = Color(160, 160, 160)))
                    append(" update  ")
                    pop()

                    pushStyle(SpanStyle(color = ACCENT, textStyle = TextStyle.Bold))
                    append("s")
                    pop()
                    pushStyle(SpanStyle(color = Color(160, 160, 160)))
                    append(" start/stop  ")
                    pop()

                    pushStyle(SpanStyle(color = ACCENT, textStyle = TextStyle.Bold))
                    append("q")
                    pop()
                    pushStyle(SpanStyle(color = Color(160, 160, 160)))
                    append(" quit")
                    pop()
                }
                Text(hints)
            }
        }
    }
}

@Composable
fun ConfirmBar(
    message: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth().background(Color(60, 30, 0)).padding(horizontal = 1),
    ) {
        val confirmText = buildAnnotatedString {
            pushStyle(SpanStyle(color = Color.Yellow, textStyle = TextStyle.Bold))
            append(message)
            pop()
            append("  ")
            pushStyle(SpanStyle(color = Color.White, textStyle = TextStyle.Bold))
            append("[y]")
            pop()
            pushStyle(SpanStyle(color = Color(160, 160, 160)))
            append(" confirm  ")
            pop()
            pushStyle(SpanStyle(color = Color.White, textStyle = TextStyle.Bold))
            append("[n]")
            pop()
            pushStyle(SpanStyle(color = Color(160, 160, 160)))
            append(" cancel")
            pop()
        }
        Text(confirmText)
    }
}
