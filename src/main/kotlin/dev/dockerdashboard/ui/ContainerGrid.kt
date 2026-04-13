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
    selectedContainerIds: Set<String> = emptySet(),
    modifier: Modifier = Modifier,
) {
    val hasComposeContainers = containers.any { it.composeProject != null }

    if (!hasComposeContainers) {
        // Original flat grid (no grouping)
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
                            isMultiSelected = container.id in selectedContainerIds,
                            cardWidth = cardWidth,
                            activeOperation = activeOperation,
                            statsHistory = statsHistory[container.id] ?: emptyList(),
                        )
                    }
                    repeat(columns - rowContainers.size) {
                        Spacer(modifier = Modifier.width(cardWidth))
                    }
                }
            }
        }
    } else {
        // Grouped grid by compose project
        val grouped = containers.groupBy { it.composeProject ?: "" }
        val composeGroups = grouped.filter { it.key.isNotEmpty() }.toSortedMap()
        val ungrouped = grouped[""] ?: emptyList()

        var flatIndex = 0
        Column(modifier = modifier) {
            for ((projectName, groupContainers) in composeGroups) {
                ComposeGroupHeader(
                    projectName = projectName,
                    containerCount = groupContainers.size,
                )
                val rows = groupContainers.chunked(columns)
                for (rowContainers in rows) {
                    Row {
                        for (container in rowContainers) {
                            ContainerCard(
                                container = container,
                                isSelected = flatIndex == selectedIndex,
                                isMultiSelected = container.id in selectedContainerIds,
                                cardWidth = cardWidth,
                                activeOperation = activeOperation,
                                statsHistory = statsHistory[container.id] ?: emptyList(),
                            )
                            flatIndex++
                        }
                        repeat(columns - rowContainers.size) {
                            Spacer(modifier = Modifier.width(cardWidth))
                        }
                    }
                }
            }
            if (ungrouped.isNotEmpty()) {
                ComposeGroupHeader(
                    projectName = "Other",
                    containerCount = ungrouped.size,
                )
                val rows = ungrouped.chunked(columns)
                for (rowContainers in rows) {
                    Row {
                        for (container in rowContainers) {
                            ContainerCard(
                                container = container,
                                isSelected = flatIndex == selectedIndex,
                                isMultiSelected = container.id in selectedContainerIds,
                                cardWidth = cardWidth,
                                activeOperation = activeOperation,
                                statsHistory = statsHistory[container.id] ?: emptyList(),
                            )
                            flatIndex++
                        }
                        repeat(columns - rowContainers.size) {
                            Spacer(modifier = Modifier.width(cardWidth))
                        }
                    }
                }
            }
        }
    }
}
