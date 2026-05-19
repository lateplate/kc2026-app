# KotlinConf 2026 Todo App

A full-stack Kotlin todo app spanning browser (Kotlin/Wasm + Compose Multiplatform), server (Ktor), and database (Exposed + SQLite). Built as the companion app for Dan Kim's KotlinConf 2026 talk, *"Finally Full-Stack Kotlin: Building a Real App with Wasm, Compose, Ktor, and Exposed"*.

The app is intentionally straightforward — a todo list with CRUD — so the stack, not the domain logic, is what's interesting.

A `CLAUDE.md` file is included for AI-assisted development. If you're using Claude (or another LLM), point it at this repo and `CLAUDE.md` for a faster onramp — it covers architecture, platform-specific gotchas, common tasks, and the reasoning behind key decisions in more depth than this README.

## Tech stack

| Layer | Technology |
|---|---|
| Frontend | Kotlin/Wasm + Compose Multiplatform 1.11.0 |
| Server | Ktor 3.5.0 (Netty) |
| Database | Exposed 1.3.0 + SQLite |
| HTTP client | Ktorfit 2.7.3 (KSP-generated from annotated interface) |
| Serialization | kotlinx.serialization 1.11.0 |
| Kotlin | 2.3.21 |

## Modules

```
composeApp/   — Compose Multiplatform frontend (Wasm, JVM desktop, Android, iOS targets)
server/       — Ktor JVM backend
shared/       — Data models and constants shared between client and server
iosApp/       — Xcode project wrapping the Compose framework for iOS
```

All modules share the package namespace `com.dankim.kc2026`.

## Architecture

```
Browser (Wasm)
Android / Desktop / iOS
        |
        | HTTP JSON (Ktorfit + kotlinx.serialization)
        |
Ktor server :8080
  ├── /           → serves Wasm bundle as static files
  └── /api/todos  → CRUD REST API
        |
        | Exposed (DAO or DSL)
        |
SQLite  data/kc2026.db
```

The `shared` module sits on both sides of the HTTP boundary. `TodoItem`, `CreateTodoRequest`, and `UpdateTodoRequest` are defined once, serialized with kotlinx.serialization, and used verbatim on both the server (Ktor) and client (Ktorfit).

The frontend follows a strict stateful/stateless split: `TodoApp` owns all state and makes all API calls; `TodoScreen` and its children are fully stateless composables that receive data and emit callbacks.

The server's database layer (`db/TodoSchema.kt`) exposes both Exposed styles — DAO entity (`TodoItemEntity`) and DSL extensions (`ResultRow.toTodoItem()`) — against the same table definition. The routes show both approaches side by side, one active and one commented out.

## Prerequisites

- JDK 11+
- Android SDK (`compileSdk 36`, `minSdk 24`) if building for Android
- Xcode if building for iOS
- IntelliJ IDEA (or Android Studio for Android)

No separate Node.js install needed — the Kotlin Gradle plugin manages its own JS toolchain.

## Running in IntelliJ IDEA

The browser experience is two run configs: the server (which also serves the Wasm bundle as static files) and the Wasm compiler in watch mode.

### Server

**Run > Edit Configurations > + > Gradle**

| Field | Value |
|---|---|
| Name | `Server` |
| Run | `:server:run` |
| Working directory | `$PROJECT_DIR$` |

The working directory must be the project root — the server resolves the Wasm bundle path and the SQLite database path relative to it.

To seed ~50 demo todos on startup (wipes existing data first): add `DEMO=true` to **Environment variables**.

### Wasm

**Run > Edit Configurations > + > Gradle**

| Field | Value |
|---|---|
| Name | `Wasm` |
| Run | `:composeApp:wasmJsBrowserDevelopmentExecutableDistribution` |
| Arguments | `--continuous` |

This compiles the Wasm bundle into `composeApp/build/dist/wasmJs/developmentExecutable/` and rebuilds on every source change. There is no separate dev server — Ktor serves the output on port 8080. Reload the browser to pick up changes.

Open `http://localhost:8080` with both configs running.

### Desktop

**Run > Edit Configurations > + > Gradle**

| Field | Value |
|---|---|
| Name | `Desktop` |
| Run | `:composeApp:run` |

Requires the server to be running separately.

### Android

Requires the **Android plugin** (Preferences > Plugins > "Android" by JetBrains) or **Android Studio**.

**Run > Edit Configurations > + > Android App**

| Field | Value |
|---|---|
| Name | `Android` |
| Module | `kc2026.composeApp` |

The Android app connects to the server at `10.0.2.2:8080` instead of `localhost` — `10.0.2.2` is the Android emulator's alias for the host machine's loopback interface. For a physical device, change the URL in `androidMain/MainActivity.kt` to the host machine's LAN IP.

### iOS

Open `iosApp/iosApp.xcodeproj` in Xcode. Select a simulator or connected device and press Run. Xcode triggers the Kotlin/Native build automatically via a build phase script — no separate Gradle run config needed. The server must be running; the simulator reaches it at `localhost:8080`.

## Project structure

```
composeApp/src/
  commonMain/     — shared UI (TodoApp, TodoScreen, TodoApi) and resources
  webMain/        — Wasm/browser entry point + HTML shell (maps to wasmJs Gradle target)
  jvmMain/        — desktop entry point
  androidMain/    — Android Activity + network security config
  iosMain/        — iOS entry point (produces ComposeApp.framework for Xcode)

server/src/main/kotlin/com/dankim/kc2026/
  Server.kt       — Ktor app, routes, CORS, JSON config, dbQuery helper, demo seeding
  db/
    Database.kt   — SQLite connection, WAL mode, schema creation
    TodoSchema.kt — TodoItems table, TodoItemEntity (DAO), ResultRow/InsertStatement extensions (DSL)

shared/src/commonMain/kotlin/com/dankim/kc2026/
  TodoModels.kt   — TodoItem, CreateTodoRequest, UpdateTodoRequest
  Constants.kt    — SERVER_PORT = 8080
```

## API

Base path: `/api/todos`

| Method | Path | Body | Description |
|---|---|---|---|
| GET | `/api/todos` | — | All todos, newest first |
| POST | `/api/todos` | `CreateTodoRequest` | Create a todo |
| PUT | `/api/todos/{id}` | `UpdateTodoRequest` | Update title and/or done |
| DELETE | `/api/todos/{id}` | — | Delete a todo |

`UpdateTodoRequest.title` is optional — a done-only toggle doesn't need to resend the title.

## Database

SQLite at `data/kc2026.db` (created on first run, gitignored). Exposed creates the schema automatically via `SchemaUtils.create` — no migration tooling. For schema changes, delete the file and let it recreate. WAL mode is enabled for concurrent read performance.

## Build

```bash
./gradlew build -x test
```

There are no tests in the project; `-x test` skips the (empty) test task.
