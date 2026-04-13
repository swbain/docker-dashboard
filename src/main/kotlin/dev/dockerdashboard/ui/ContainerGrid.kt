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
    layout: GridLayout,
    containers: List<ContainerInfo>,
    selectedIndex: Int,
    cardWidth: Int,
    scrollOffset: Int,
    availableHeight: Int,
    activeOperation: ActiveOperation?,
    statsHistory: Map<String, List<StatsSnapshot>> = emptyMap(),
    selectedContainerIds: Set<String> = emptySet(),
    modifier: Modifier = Modifier,
) {
    val visibleRange = layout.visibleRowRange(scrollOffset, availableHeight)

    Column(modifier = modifier) {
        for (vrIdx in visibleRange) {
            when (val vr = layout.visualRows[vrIdx]) {
                is VisualRow.Header -> {
                    ComposeGroupHeader(
                        projectName = vr.groupName,
                        containerCount = vr.containerCount,
                    )
                }
                is VisualRow.Cards -> {
                    Row {
                        for (i in 0 until vr.count) {
                            val flatIndex = vr.flatStartIndex + i
                            val container = containers[flatIndex]
                            ContainerCard(
                                container = container,
                                isSelected = flatIndex == selectedIndex,
                                isMultiSelected = container.id in selectedContainerIds,
                                cardWidth = cardWidth,
                                activeOperation = activeOperation,
                                statsHistory = statsHistory[container.id] ?: emptyList(),
                            )
                        }
                        repeat(layout.columns - vr.count) {
                            Spacer(modifier = Modifier.width(cardWidth))
                        }
                    }
                }
            }
        }
    }
}
