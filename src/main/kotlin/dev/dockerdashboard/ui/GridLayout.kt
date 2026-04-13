package dev.dockerdashboard.ui

import dev.dockerdashboard.model.ContainerInfo

sealed interface VisualRow {
    data class Header(val groupName: String, val containerCount: Int) : VisualRow
    data class Cards(val flatStartIndex: Int, val count: Int) : VisualRow
}

const val GROUP_HEADER_HEIGHT = 1

fun visualRowHeight(row: VisualRow): Int = when (row) {
    is VisualRow.Header -> GROUP_HEADER_HEIGHT
    is VisualRow.Cards -> CARD_HEIGHT
}

data class GridLayout(
    val visualRows: List<VisualRow>,
    val orderedContainers: List<ContainerInfo>,
    val columns: Int,
)

fun buildGridLayout(
    containers: List<ContainerInfo>,
    columns: Int,
): GridLayout {
    if (containers.isEmpty()) return GridLayout(emptyList(), emptyList(), columns)

    val hasCompose = containers.any { it.composeProject != null }

    if (!hasCompose) {
        val rows = mutableListOf<VisualRow>()
        var idx = 0
        while (idx < containers.size) {
            val count = minOf(columns, containers.size - idx)
            rows.add(VisualRow.Cards(flatStartIndex = idx, count = count))
            idx += count
        }
        return GridLayout(rows, containers, columns)
    }

    val grouped = containers.groupBy { it.composeProject ?: "" }
    val composeGroups = grouped.filter { it.key.isNotEmpty() }.toSortedMap()
    val ungrouped = grouped[""] ?: emptyList()

    val orderedGroups = mutableListOf<Pair<String, List<ContainerInfo>>>()
    for ((name, group) in composeGroups) {
        orderedGroups.add(name to group)
    }
    if (ungrouped.isNotEmpty()) {
        orderedGroups.add("Other" to ungrouped)
    }

    val orderedContainers = mutableListOf<ContainerInfo>()
    val rows = mutableListOf<VisualRow>()
    for ((groupName, groupContainers) in orderedGroups) {
        rows.add(VisualRow.Header(groupName, groupContainers.size))
        for (chunk in groupContainers.chunked(columns)) {
            rows.add(VisualRow.Cards(
                flatStartIndex = orderedContainers.size,
                count = chunk.size,
            ))
            orderedContainers.addAll(chunk)
        }
    }

    return GridLayout(rows, orderedContainers, columns)
}

fun GridLayout.visualRowIndexOf(flatIndex: Int): Int {
    for ((i, vr) in visualRows.withIndex()) {
        if (vr is VisualRow.Cards &&
            flatIndex >= vr.flatStartIndex &&
            flatIndex < vr.flatStartIndex + vr.count
        ) return i
    }
    return -1
}

private fun GridLayout.adjacentCardsRow(fromVrIdx: Int, delta: Int): Pair<Int, VisualRow.Cards>? {
    var idx = fromVrIdx + delta
    while (idx in visualRows.indices) {
        val vr = visualRows[idx]
        if (vr is VisualRow.Cards) return idx to vr
        idx += delta
    }
    return null
}

fun GridLayout.moveIndex(currentIndex: Int, direction: Direction): Int {
    if (orderedContainers.isEmpty()) return 0
    val currentVrIdx = visualRowIndexOf(currentIndex)
    if (currentVrIdx < 0) return 0
    val currentRow = visualRows[currentVrIdx] as VisualRow.Cards
    val col = currentIndex - currentRow.flatStartIndex

    return when (direction) {
        Direction.LEFT -> {
            if (col > 0) currentIndex - 1 else currentIndex
        }
        Direction.RIGHT -> {
            if (col < currentRow.count - 1) currentIndex + 1 else currentIndex
        }
        Direction.UP -> {
            val prev = adjacentCardsRow(currentVrIdx, -1) ?: return currentIndex
            val (_, prevRow) = prev
            val targetCol = col.coerceAtMost(prevRow.count - 1)
            prevRow.flatStartIndex + targetCol
        }
        Direction.DOWN -> {
            val next = adjacentCardsRow(currentVrIdx, +1) ?: return currentIndex
            val (_, nextRow) = next
            val targetCol = col.coerceAtMost(nextRow.count - 1)
            nextRow.flatStartIndex + targetCol
        }
    }
}

fun GridLayout.visibleRowRange(scrollOffset: Int, availableHeight: Int): IntRange {
    val start = scrollOffset.coerceIn(0, (visualRows.size - 1).coerceAtLeast(0))
    var heightUsed = 0
    var end = start
    while (end < visualRows.size && heightUsed + visualRowHeight(visualRows[end]) <= availableHeight) {
        heightUsed += visualRowHeight(visualRows[end])
        end++
    }
    return start until end
}

fun GridLayout.scrollToReveal(
    flatIndex: Int,
    currentScroll: Int,
    availableHeight: Int,
): Int {
    val targetVrIdx = visualRowIndexOf(flatIndex)
    if (targetVrIdx < 0) return currentScroll

    val visible = visibleRowRange(currentScroll, availableHeight)

    if (targetVrIdx in visible) return currentScroll

    if (targetVrIdx < currentScroll) {
        // Scrolling up: show the target row, include its group header if present
        var newScroll = targetVrIdx
        if (newScroll > 0 && visualRows[newScroll - 1] is VisualRow.Header) {
            newScroll -= 1
        }
        return newScroll
    }

    // Scrolling down: advance scroll until targetVrIdx fits in the viewport
    var scroll = currentScroll
    while (scroll < visualRows.size) {
        val range = visibleRowRange(scroll, availableHeight)
        if (targetVrIdx in range) return scroll
        scroll++
    }
    return scroll.coerceAtMost((visualRows.size - 1).coerceAtLeast(0))
}
