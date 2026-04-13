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
        // Flat grid with scroll
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
        // Grouped grid — render groups with headers, apply scroll to card rows
        val grouped = containers.groupBy { it.composeProject ?: "" }
        val composeGroups = grouped.filter { it.key.isNotEmpty() }.toSortedMap()
        val ungrouped = grouped[""] ?: emptyList()

        // Build ordered list: (projectName, containers) pairs
        val orderedGroups = mutableListOf<Pair<String, List<ContainerInfo>>>()
        for ((name, group) in composeGroups) {
            orderedGroups.add(name to group)
        }
        if (ungrouped.isNotEmpty()) {
            orderedGroups.add("Other" to ungrouped)
        }

        // Render with flat index tracking, skip card rows before scrollOffset
        var flatIndex = 0
        var cardRowsSeen = 0
        var cardRowsRendered = 0
        Column(modifier = modifier) {
            for ((groupName, groupContainers) in orderedGroups) {
                val groupRows = groupContainers.chunked(columns)

                // Check if any of this group's rows are visible
                val groupStartRow = cardRowsSeen
                val groupEndRow = cardRowsSeen + groupRows.size

                if (groupEndRow > scrollOffset && cardRowsRendered < maxVisibleRows) {
                    // Show header if first visible row of this group
                    ComposeGroupHeader(
                        projectName = groupName,
                        containerCount = groupContainers.size,
                    )
                }

                for (rowContainers in groupRows) {
                    if (cardRowsSeen >= scrollOffset && cardRowsRendered < maxVisibleRows) {
                        Row {
                            for ((i, container) in rowContainers.withIndex()) {
                                ContainerCard(
                                    container = container,
                                    isSelected = (flatIndex + i) == selectedIndex,
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
                        cardRowsRendered++
                    }
                    flatIndex += rowContainers.size
                    cardRowsSeen++
                }
            }
        }
    }
}
