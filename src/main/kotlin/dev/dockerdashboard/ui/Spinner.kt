package dev.dockerdashboard.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay

private val SPINNER_FRAMES = charArrayOf('⠋', '⠙', '⠹', '⠸', '⠼', '⠴', '⠦', '⠧', '⠇', '⠏')

@Composable
fun rememberSpinner(active: Boolean): Char {
    var index by remember { mutableStateOf(0) }

    LaunchedEffect(active) {
        if (active) {
            while (true) {
                delay(100)
                index = (index + 1) % SPINNER_FRAMES.size
            }
        } else {
            index = 0
        }
    }

    return SPINNER_FRAMES[index]
}
