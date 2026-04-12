package dev.dockerdashboard.ui

import androidx.compose.runtime.Composable
import com.jakewharton.mosaic.layout.width
import com.jakewharton.mosaic.modifier.Modifier
import com.jakewharton.mosaic.ui.Column
import com.jakewharton.mosaic.ui.Row
import com.jakewharton.mosaic.ui.Spacer
import dev.dockerdashboard.model.ActiveOperation
import dev.dockerdashboard.model.ContainerInfo
import dev.dockerdashboard.model.StatsSnapshot

@Composable
fun ContainerGrid(
    containers: List<ContainerInfo>,
    selectedIndex: Int,
    columns: Int,
    cardWidth: Int,
    scrollOffset: Int,
    maxVisibleRows: Int,
    activeOperation: ActiveOperation?,
    statsHistory: Map<String, List<StatsSnapshot>> = emptyMap(),
    modifier: Modifier = Modifier,
) {
    val allRows = containers.chunked(columns)
    val visibleRows = allRows.drop(scrollOffset).take(maxVisibleRows)
    Column(modifier = modifier) {
        for ((rowIndex, rowContainers) in visibleRows.withIndex()) {
            Row {
                for ((colIndex, container) in rowContainers.withIndex()) {
                    val flatIndex = (rowIndex + scrollOffset) * columns + colIndex
                    ContainerCard(
                        container = container,
                        isSelected = flatIndex == selectedIndex,
                        cardWidth = cardWidth,
                        activeOperation = activeOperation,
                        statsHistory = statsHistory[container.id] ?: emptyList(),
                    )
                }
                // Fill remaining columns with spacers
                repeat(columns - rowContainers.size) {
                    Spacer(modifier = Modifier.width(cardWidth))
                }
            }
        }
    }
}
