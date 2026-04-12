# 12-Feature Parallel Implementation Plan

## Context

The Docker Dashboard is a Kotlin/Mosaic TUI with container grid, start/stop/update operations, CPU/memory stats, and registry update checks. We're adding 12 features in parallel using worktree-isolated agents to maximize throughput while keeping merge conflicts manageable.

**Core challenge:** Almost every feature touches the same 6 files (ContainerInfo, DashboardState, DashboardStore, DashboardApp, ContainerCard, StatusBar). Naive parallelism would create unresolvable merge conflicts.

**Strategy — Claude Code Agent Team (`CLAUDE_CODE_EXPERIMENTAL_AGENT_TEAMS=1` is enabled):**
- **Team Lead (me):** Builds the shared foundation on main, creates teammate agents for each feature, coordinates via shared task list, merges completed branches back into main.
- **Teammates (11 total across 3 batches):** Each teammate builds one feature on its own worktree branch. Teammates can message each other and the lead for coordination. The shared task list tracks progress.
- **Phased batches** are still needed because later features (filtering, sorting, compose grouping) depend on earlier infrastructure being merged into main.

This works because conflicts are **additive** (adding fields, adding `when` branches, adding key handlers) and the lead has full context to resolve them.

---

## Phase 0: Foundation — Team Lead Builds on `main`

Build all shared model/state/service infrastructure that multiple features need. No behavioral changes — the dashboard works identically after this phase. Every new field has a default value.

### 0a. Model extensions — `model/ContainerInfo.kt`

Add fields to `ContainerInfo`:
```
healthStatus: String? = null          // "healthy", "unhealthy", "starting", null
volumes: List<VolumeMount> = emptyList()
composeProject: String? = null        // from com.docker.compose.project label
composeService: String? = null        // from com.docker.compose.service label
labels: Map<String, String> = emptyMap()
env: List<String> = emptyList()
networks: List<String> = emptyList()
restartPolicy: String? = null
command: String? = null
exitCode: Int? = null
```

Add `VolumeMount` data class (source, destination, mode).

Add `ActiveOperation` variants: `Pruning`, `BulkStopping(current, total)`, `BulkStarting(current, total)`, `BulkPulling(current, total)`.

### 0b. State extensions — `model/DashboardState.kt`

Add enums:
- `ViewMode { GRID, DETAIL, LOGS }`
- `SortMode { NAME, STATE, CPU, MEMORY, CREATED }`
- `StateFilter { ALL, RUNNING, STOPPED }`

Add `StatsSnapshot` data class (cpuPercent, memoryUsageMb, timestamp).

Add fields to `DashboardState`:
```
viewMode: ViewMode = ViewMode.GRID
filterText: String = ""
stateFilter: StateFilter = StateFilter.ALL
sortMode: SortMode = SortMode.NAME
selectedContainerIds: Set<String> = emptySet()
statsHistory: Map<String, List<StatsSnapshot>> = emptyMap()
logLines: List<String> = emptyList()
logScrollOffset: Int = 0
logContainerId: String? = null
detailContainerId: String? = null
detailScrollOffset: Int = 0
isSearchMode: Boolean = false
```

Add `PendingConfirmation` variants: `BulkStop`, `BulkUpdate`, `PruneImages`.

### 0c. UiAction extensions — `ui/DashboardApp.kt`

Add to `UiAction` sealed interface:
```
ViewLogs, ViewDetail, BackToGrid, ToggleSelect, TypeFilterChar(char),
StartSearch, CancelSearch, CycleStateFilter, CycleSortMode, ShellExec, PruneImages,
ScrollUp, ScrollDown
```

Add view-mode-aware key handling:
```kotlin
private fun handleKeyEvent(event: KeyEvent, state: DashboardState, onAction: (UiAction) -> Unit): Boolean {
    // Global: q quits from any view
    // GRID mode: existing keys + new keys (l, d/Enter, Space, /, f, o, e, p)
    // DETAIL mode: up/down scroll, Esc back
    // LOGS mode: up/down scroll, Esc back
}
```

Add view mode routing in `DashboardApp`:
```kotlin
when (state.viewMode) {
    ViewMode.GRID -> { /* existing grid rendering */ }
    ViewMode.DETAIL -> { DetailPanel(...) }  // placeholder
    ViewMode.LOGS -> { LogViewer(...) }      // placeholder
}
```

### 0d. Store extensions — `DashboardStore.kt`

Add `displayContainers` computed property — applies stateFilter, filterText, and sortMode to `state.containers`. All UI references switch from `state.containers` to `store.displayContainers` (or pass it down).

Add stats history tracking in container refresh loop — after fetching stats, append `StatsSnapshot` to rolling history (max 30 per container).

Add skeleton `dispatch` cases for all new `UiAction` types (each delegates to a `TODO()` handler or no-op).

Enrich `listAllContainers()` to extract from the docker-java list response:
- `container.labels` → `labels`, `composeProject`, `composeService`
- `container.mounts` → `volumes` as `VolumeMount` list
- Parse health from `container.status` string (contains "(healthy)" etc.)

### 0e. DockerService method stubs

Add method signatures (implementations come in feature branches):
- `getContainerLogs(containerId, tail=200): List<String>`
- `inspectContainerDetail(containerId): ContainerDetail` (for detail panel)
- `pruneImages(): PruneResult`

### 0f. Main.kt restructuring for Shell/Exec

Wrap `runMosaicBlocking` in a loop to support suspending the TUI for `docker exec`:
```kotlin
fun main() {
    var execRequest: ExecRequest? = null
    while (true) {
        runMosaicBlocking { ... /* set execRequest when ShellExec triggered */ }
        val req = execRequest ?: break
        execRequest = null
        ProcessBuilder("docker", "exec", "-it", req.containerId, "/bin/sh")
            .inheritIO().start().waitFor()
    }
}
```

---

## Phase 1: Team Lead Creates 5 Teammates in Parallel

Each teammate gets a worktree branched from main (after Phase 0 commit). Each primarily creates **new files** and makes small additive changes to shared files. Lead monitors the shared task list and merges branches as teammates complete.

### Teammate A: Container Logs Viewer (`feature/logs-viewer`)
- **New file:** `ui/LogViewer.kt` — full-screen log viewer with scrollable content, container name header
- **Modify:** `DashboardStore.kt` — implement `handleViewLogs()` (fetches logs via `dockerService.getContainerLogs()`, sets viewMode=LOGS)
- **Modify:** `DockerService.kt` — implement `getContainerLogs()` using `client.logContainerCmd()` with tail + stdout/stderr
- **Key binding:** `l` → ViewLogs (grid mode only)
- **Log view keys:** up/down/j/k scroll, `r` refresh, Esc/q back to grid
- **Card height:** No change. CARD_HEIGHT stays 9.

### Teammate B: Container Detail Panel (`feature/detail-panel`)
- **New files:** `ui/DetailPanel.kt` — full-screen scrollable detail view; `model/ContainerDetail.kt` — rich model for on-demand inspect data
- **Modify:** `DashboardStore.kt` — implement `handleViewDetail()` (calls `dockerService.inspectContainerDetail()`, sets viewMode=DETAIL)
- **Modify:** `DockerService.kt` — implement `inspectContainerDetail()` using `client.inspectContainerCmd()` to get env, networks, restart policy, command, exit code, full volume info
- **Sections:** Overview (name, image, ID, status, uptime), Config (command, entrypoint, restart policy), Environment, Networks, Volumes, Labels
- **Key binding:** `d` or `Enter` → ViewDetail (grid mode only)
- **Detail view keys:** up/down/j/k scroll, Esc back to grid

### Teammate C: Resource Graphs / Sparklines (`feature/sparklines`)
- **New file:** `ui/Sparkline.kt` — composable that renders a `List<Double>` as unicode block characters (`▁▂▃▄▅▆▇█`)
- **Modify:** `ContainerCard.kt` — on the **selected** card only, replace the numeric "CPU: X% MEM: X/YMB" line with two inline sparklines + current values. Unselected cards keep the existing numeric display.
- Uses `state.statsHistory[container.id]` data accumulated by Phase 0 foundation
- **No new key bindings**

### Teammate D: Health Status + Volume Display (`feature/health-volumes`)
- **Modify:** `DockerService.kt` — ensure `listAllContainers()` populates `healthStatus` (parse from status string) and `volumes` (from `container.mounts`)
- **Modify:** `ContainerCard.kt`:
  - Health: prepend icon to name line — `♥` green (healthy), `♥` red (unhealthy), `♥` yellow (starting). No icon if no healthcheck.
  - Volumes: on **stopped containers only** (which have 3-4 empty content lines), show first 1-2 volume mounts abbreviated (`/host → /dest`). Running containers show volumes in detail panel only.
- **No new key bindings.** CARD_HEIGHT stays 9.

### Teammate E: Container Shell / Exec (`feature/shell-exec`)
- **Modify:** `DashboardStore.kt` — implement `handleShellExec()` that sets an exec request flag and triggers Mosaic exit
- **Modify:** `Main.kt` — the loop from Phase 0f handles the exec: exit Mosaic, run `docker exec -it <id> /bin/sh` with inherited IO, re-enter Mosaic
- **Key binding:** `e` → ShellExec (grid mode, running containers only)
- **Confirmation:** show "Shell into <name>? [y]/[n]" since it suspends the dashboard

---

## Phase 1 Merge — Lead Integrates Branches

Lead merges branches into main **one at a time**, in this order:
1. Teammate A (Logs) — establishes the ViewMode.LOGS pattern
2. Teammate B (Detail) — follows the same ViewMode pattern, easy to merge alongside Logs
3. Teammate C (Sparklines) — only touches ContainerCard, minimal conflicts
4. Teammate D (Health+Volumes) — also touches ContainerCard but different sections
5. Teammate E (Shell/Exec) — touches Main.kt uniquely

Expected conflicts: Teammates A+B both add view mode branches (additive, keep both). Teammates C+D both modify ContainerCard (different sections, resolvable). All teammates add dispatch cases to DashboardStore (additive `when` branches, keep all).

---

## Phase 2: Lead Creates 4 Teammates in Parallel

Branch from main after Phase 1 merge. These modify core interaction patterns.

### Teammate F: Filtering / Search (`feature/filtering`)
- **New file:** `ui/FilterBar.kt` — text input bar shown above the grid when search mode is active
- **Modify:** `DashboardStore.kt` — implement `handleStartSearch()`, `handleTypeFilterChar()`, `handleCancelSearch()`, `handleCycleStateFilter()`. Wire `displayContainers` (from Phase 0) into all UI paths.
- **Modify:** `DashboardApp.kt` — render FilterBar when `isSearchMode`, change grid to use `displayContainers`. In search mode, letter keys type into filter instead of triggering actions.
- **Modify:** `StatusBar.kt` — show active filter indicators in top bar
- **Key bindings:** `/` → StartSearch, `f` → CycleStateFilter, Esc → CancelSearch (when in search mode)

### Teammate G: Sorting Options (`feature/sorting`)
- **Modify:** `DashboardStore.kt` — implement `handleCycleSortMode()` cycling NAME→STATE→CPU→MEMORY→CREATED→NAME
- **Modify:** `StatusBar.kt` — show current sort mode in top bar (e.g., "↕ CPU")
- **Key binding:** `o` → CycleSortMode
- Relies on `displayContainers` computed property from Phase 0 which already applies sort

### Teammate H: Docker Compose Grouping (`feature/compose-grouping`)
- **New file:** `ui/ComposeGroupHeader.kt` — styled section header composable
- **Modify:** `ContainerGrid.kt` — group `containers` by `composeProject`. For each group, render a `ComposeGroupHeader` then that group's cards in rows. Containers with null project go to "Other" group (only shown if compose containers exist).
- **Modify:** `DashboardStore.kt` — navigation (`reduceMove`) must account for visual header lines consuming vertical space in scroll calculations
- **Modify:** `Main.kt`/`DashboardApp.kt` — `maxVisibleRows` calculation may need adjustment for header lines
- Grouping is a **no-op** when no containers have compose labels (backward compatible)

### Teammate I: Bulk Operations (`feature/bulk-operations`)
- **Modify:** `DashboardStore.kt` — implement `handleToggleSelect()` (Space toggles container in/out of `selectedContainerIds`). When set is non-empty, `handleToggleStartStop()` and `handlePullAndRestart()` dispatch bulk confirmations. Implement bulk confirm handlers that process containers sequentially with progress.
- **Modify:** `ContainerCard.kt` — show selection indicator (filled checkbox or highlight) on selected cards, magenta border for multi-selected
- **Modify:** `StatusBar.kt` — show "N selected" count when multi-select active, context-appropriate hints
- **Key binding:** `Space` → ToggleSelect, `Esc` clears selection (when no other modal is active)
- Design: cursor (`selectedIndex`) and multi-selection (`selectedContainerIds`) are independent. Cursor moves freely; Space toggles the card under cursor.

---

## Phase 2 Merge — Lead Integrates Branches

Merge order:
1. Teammate G (Sorting) — smallest change, cleanest merge
2. Teammate F (Filtering) — depends on same `displayContainers`, no conflict with sorting
3. Teammate I (Bulk Ops) — modifies card + store action handlers
4. Teammate H (Compose Grouping) — riskiest, modifies grid layout; goes last so it integrates with final container list flow

---

## Phase 3: Lead Creates 2 Teammates in Parallel

### Teammate J: Image Cleanup (`feature/image-cleanup`)
- **Modify:** `DockerService.kt` — implement `pruneImages()` using `client.pruneImagesCmd()`, return count + space freed
- **Modify:** `DashboardStore.kt` — implement `handlePruneImages()` with confirmation flow and result display in status bar
- **Key binding:** `p` → PruneImages (with confirmation "[y]/[n]")

### Teammate K: Color Themes (`feature/color-themes`)
- **New file:** `ui/Theme.kt` — `Theme` data class with named color slots (accent, border, borderSelected, textPrimary, textSecondary, statusRunning, statusStopped, etc.). `CompositionLocal` for current theme. Built-in themes: Dark (current), Light, Solarized.
- **Modify:** Every UI file — replace all hardcoded `Color(...)` values with `LocalTheme.current.xxx` references
- **Modify:** `DashboardState.kt` — add `themeName` field
- **Modify:** `DashboardStore.kt` — add `handleCycleTheme()` handler
- **Key binding:** `t` → CycleTheme
- **Why last:** Touches every file's colors. Doing it after all other features means one clean sweep instead of every parallel branch needing to use theme references.

---

## Key Bindings Summary (Grid Mode)

| Key | Action | Feature |
|-----|--------|---------|
| arrows/hjkl | Navigate | Existing |
| s | Start/Stop | Existing |
| u | Pull & Restart | Existing |
| y/n | Confirm/Cancel | Existing |
| q | Quit | Existing |
| l | View Logs | #1 |
| d / Enter | View Detail | #3 |
| e | Shell/Exec | #11 |
| / | Start Search | #2 |
| f | Cycle State Filter | #2 |
| o | Cycle Sort | #6 |
| Space | Toggle Select | #5 |
| p | Prune Images | #12 |
| t | Cycle Theme | #15 |

---

## Card Layout Decision

**CARD_HEIGHT stays at 9.** No height increase needed.

- **Health:** Icon prepended to name line (same line, no extra row)
- **Volumes:** Shown on stopped containers only (which have 3-4 unused content rows)
- **Sparklines:** Shown on selected card only, replacing the numeric CPU/MEM line
- **Compose project:** Shown via group headers above the grid, not on cards

---

## Verification Plan

After each phase merge:
1. `./gradlew build` — must compile cleanly
2. `./gradlew run -q` — manual TUI verification:
   - Phase 0: Dashboard works identically to before (no visible changes)
   - Phase 1: Test `l` (logs), `d` (detail), sparklines on selected card, health icons, volume display on stopped containers, `e` (shell exec)
   - Phase 2: Test `/` (search), `f` (filter), `o` (sort), Space (multi-select) + bulk ops, compose grouping with compose-managed containers
   - Phase 3: Test `p` (prune), `t` (theme cycling)
3. End-to-end: exercise all 12 features with a mix of running/stopped/compose containers

---

## Parallelism Summary

```
TEAM LEAD           TEAMMATES (worktree isolation)
────────────        ───────────────────────────────
Phase 0: build      
foundation on main  
        │           
        ├──create──→ Teammate A (logs)     ─┐
        ├──create──→ Teammate B (detail)   ─┤
        ├──create──→ Teammate C (spark)    ─┤ Phase 1: 5 parallel
        ├──create──→ Teammate D (health)   ─┤
        ├──create──→ Teammate E (shell)    ─┘
        │            (shared task list + messaging)
merge 5 branches    
        │           
        ├──create──→ Teammate F (filter)   ─┐
        ├──create──→ Teammate G (sort)     ─┤ Phase 2: 4 parallel
        ├──create──→ Teammate H (compose)  ─┤
        ├──create──→ Teammate I (bulk)     ─┘
        │            (shared task list + messaging)
merge 4 branches    
        │           
        ├──create──→ Teammate J (prune)    ─┐ Phase 3: 2 parallel
        ├──create──→ Teammate K (themes)   ─┘
        │           
merge 2 branches    
        │           
final verification  
```

**Total: 11 teammates across 3 parallel batches, coordinated via Agent Teams.**
**Team Lead: builds foundation, creates teammates, monitors shared task list, merges all branches, resolves conflicts, runs verification.**
