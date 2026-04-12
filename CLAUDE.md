# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

A terminal-based Docker container dashboard built with Kotlin and [Mosaic](https://github.com/JakeWharton/mosaic) (Compose-based TUI framework). It displays running/stopped containers in a grid, shows CPU/memory stats, checks Docker Hub for image updates, and allows start/stop/pull-and-recreate operations — all from the terminal.

## Build & Run

```bash
./gradlew build          # compile + assemble
./gradlew run            # run the dashboard (requires Docker daemon running)
./gradlew run -q         # run without Gradle output noise (cleaner TUI)
```

Gradle 8.12, Kotlin 2.1.0, JVM toolchain 17. No test suite exists yet.

## Architecture

**Entrypoint:** `Main.kt` — `runMosaicBlocking` hosts the Compose tree. All application state (containers list, selection index, active operation, errors) lives as Compose `mutableState` here. Two `LaunchedEffect` coroutine loops drive data:
- Container refresh loop (5s interval) — calls `DockerService.listAllContainers()` + `fetchStats()`
- Update check loop (60s interval) — calls `RegistryService.checkForUpdates()`

**Services (`service/`):**
- `DockerService` — wraps `docker-java` client. Handles container listing, one-shot stats collection (CPU/memory via `CountDownLatch` callback pattern), start/stop, and `recreateContainer` (stop → remove → pull → create with same config → reconnect networks → start).
- `RegistryService` — checks Docker Hub for image updates by comparing local `RepoDigests` against remote manifest digests. Uses raw `HttpURLConnection` + regex for token parsing (no JSON library). Only supports Docker Hub images.

**UI (`ui/`):**
- `DashboardApp` — root composable, owns keyboard event handling and layout. Key bindings: arrows/hjkl navigate, `s` start/stop, `u` pull-and-restart, `y`/`n` confirm/cancel, `q` quit.
- `ContainerGrid` — lays out cards in rows based on terminal width.
- `ContainerCard` — bordered box showing name, image, status, ports, CPU/memory. `MIN_CARD_WIDTH = 36`, `CARD_HEIGHT = 8`. Border drawn via `drawBehind` with box-drawing characters.
- `StatusBar` — top bar (connection status, counts) and bottom bar (keybind hints, operation progress, errors, confirmation prompt).

**Model (`model/`):**
- `ContainerInfo` — data class for container state, stats, and update availability.
- `ContainerState` — enum mapping Docker state strings.
- `ActiveOperation` — sealed interface for in-progress operations shown in the status bar.

## Key Design Decisions

- **No JSON library** — `RegistryService` parses Docker Hub auth tokens with regex to avoid adding a dependency.
- **Destructive operations require confirmation** — stop and pull-and-restart show a `[y]/[n]` confirm bar. Start does not.
- **Single-operation lock** — only one `ActiveOperation` can run at a time (guarded by null check).
- **Stats are best-effort** — fetched in parallel with 2s timeout per container; failures silently return the container without stats.
