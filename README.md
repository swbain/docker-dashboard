# Docker Dashboard

A terminal-based Docker container dashboard built with Kotlin and [Mosaic](https://github.com/JakeWharton/mosaic) (Jetpack Compose for the terminal). Monitor, start, stop, and update your containers without leaving the command line.

```
┌Docker Dashboard  ● Connected                          3/5 running  14:32:07┐

┌──────────────────────────────────┐  ┌──────────────────────────────────┐
│ nginx-proxy                      │  │ postgres-db                      │
│ nginx:latest           [UPDATE]  │  │ postgres:16                      │
│ Up 3 hours                       │  │ Up 2 days                        │
│ 0.0.0.0:80->80/tcp               │  │ 0.0.0.0:5432->5432/tcp           │
│ CPU: 0.3%  MEM: 12/256MB        │  │ CPU: 1.2%  MEM: 85/512MB        │
└──────────────────────────────────┘  └──────────────────────────────────┘

 arrows navigate  u update  s start/stop  q quit
```

## Features

- **Live container grid** -- displays all Docker containers (running and stopped) in a responsive card layout that adapts to terminal width
- **Real-time stats** -- CPU and memory usage for running containers, refreshed every 5 seconds
- **Image update detection** -- checks Docker Hub every 60 seconds and flags containers with available image updates
- **Container management** -- start, stop, and pull-and-recreate containers directly from the dashboard
- **Keyboard-driven** -- vim-style navigation with confirmation prompts for destructive operations
- **Scrollable viewport** -- handles more containers than fit on screen with scroll indicators

## Prerequisites

- **JDK 17+**
- **Docker** -- the Docker daemon must be running and accessible

## Build & Run

```bash
./gradlew build          # compile + assemble
./gradlew run            # run the dashboard
./gradlew run -q         # run without Gradle output noise (recommended)
```

## Key Bindings

| Key              | Action                              |
|------------------|-------------------------------------|
| `arrows` / `hjkl` | Navigate between containers       |
| `s`              | Start or stop the selected container |
| `u`              | Pull latest image and recreate container |
| `y` / `n`        | Confirm or cancel a pending action  |
| `q`              | Quit                                |

Destructive operations (stop, pull-and-recreate) require `y`/`n` confirmation. Starting a stopped container runs immediately.

## Project Structure

```
src/main/kotlin/dev/dockerdashboard/
├── Main.kt                  # Entrypoint, state management, coroutine loops
├── model/
│   └── ContainerInfo.kt     # Data models (ContainerInfo, ContainerState, ActiveOperation)
├── service/
│   ├── DockerService.kt     # Docker client wrapper (list, stats, start/stop, recreate)
│   └── RegistryService.kt   # Docker Hub update checker (digest comparison)
└── ui/
    ├── DashboardApp.kt      # Root composable, keyboard handling
    ├── ContainerGrid.kt     # Responsive grid layout
    ├── ContainerCard.kt     # Individual container card with border drawing
    └── StatusBar.kt         # Top bar (connection/counts) and bottom bar (hints/errors)
```

## Tech Stack

- **[Kotlin](https://kotlinlang.org/)** 2.1.0
- **[Mosaic](https://github.com/JakeWharton/mosaic)** 0.18.0 -- Jetpack Compose runtime for terminal UIs
- **[docker-java](https://github.com/docker-java/docker-java)** 3.4.1 -- Docker Engine API client
- **[Kotlin Coroutines](https://github.com/Kotlin/kotlinx.coroutines)** 1.10.1
- **Gradle** 8.12

## License

MIT
