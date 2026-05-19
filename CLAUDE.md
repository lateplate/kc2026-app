# kc2026 — Claude Context

## What this project is

A full-stack Kotlin todo app: Kotlin/Wasm + Compose Multiplatform frontend, Ktor server, Exposed + SQLite database. Built by Dan Kim as the companion app for his KotlinConf 2026 talk. The domain is intentionally minimal — the interesting parts are the stack and how the layers connect, not the application logic.

## Prerequisites

- **JDK 11+** (project toolchain targets JVM 11)
- **Android SDK** with `compileSdk 36` and `minSdk 24` if working on Android
- **Xcode** if working on iOS
- IntelliJ IDEA (or Android Studio for Android work)

No separate Node.js or npm install needed — Kotlin's Gradle plugin manages its own JS toolchain (Yarn/webpack) inside the build directory.

## Modules

- `composeApp` — Compose Multiplatform frontend. Targets: `wasmJs` (primary browser target), `jvm` (desktop), `android`, `iosArm64`, `iosSimulatorArm64`.
- `server` — Ktor JVM backend. Serves the Wasm bundle as static files and exposes the REST API.
- `shared` — `TodoItem`, `CreateTodoRequest`, `UpdateTodoRequest`, and `SERVER_PORT`. Compiled for both JVM (server) and all `composeApp` targets (client).
- `iosApp` — Xcode project wrapping the `ComposeApp` Kotlin/Native framework for iOS.

Package namespace: `com.dankim.kc2026` everywhere.

## Architecture: how the layers connect

```
Browser (Wasm)
Android / Desktop / iOS
        |
        | HTTP (Ktorfit-generated client, kotlinx.serialization JSON)
        |
Ktor server (:8080)
  ├── static files → serves Wasm bundle from composeApp/build/dist/wasmJs/developmentExecutable/
  └── /api/todos   → CRUD routes
        |
        | Exposed (DAO or DSL)
        |
SQLite (data/kc2026.db)
```

The `shared` module sits across both sides — `TodoItem`, `CreateTodoRequest`, and `UpdateTodoRequest` are defined once, serialized with kotlinx.serialization, and used verbatim on the server (Ktor) and client (Ktorfit).

## Key files

### Frontend (`composeApp`)

**`commonMain/TodoApp.kt`** — the stateful runtime for all platforms. Owns all `remember` state: the todos list, whether the add dialog is open, which item is being edited, scroll position trigger. Makes all API calls via `TodoApi`. Every platform entry point calls `TodoApp()`.

**`commonMain/TodoScreen.kt`** — fully stateless. Receives everything it needs as parameters, emits user actions via callbacks. No coroutines, no API calls. Contains `TodoScreen`, `TodoHeader`, `TodoList`, `TodoRow`, `AddTodoDialog`, `EditTodoDialog`, and `TodoInputDialog`. This is the architectural pattern: stateful container (`TodoApp`) + stateless presentation (`TodoScreen`).

**`commonMain/TodoApi.kt`** — Ktorfit interface. KSP reads the annotations at compile time and generates a concrete implementation (the actual `HttpClient` calls). The generated class is accessed via `Ktorfit.Builder()...build().createTodoApi()`. If you add a method to `TodoApi`, you must trigger a Gradle build before the generated code exists — the IDE will show errors until then. `rememberTodoApi()` creates and manages the `HttpClient` lifecycle scoped to the Compose composition.

**Platform entry points:**
- `webMain/main.kt` — Wasm. Calls `TodoApp()` via `ComposeViewport`. Source lives in `src/webMain/` even though the Gradle target is `wasmJs` — this source set name is a project convention, not a Kotlin standard.
- `jvmMain/main.kt` — Desktop. Wraps `TodoApp()` in a `Window`.
- `androidMain/MainActivity.kt` — Android. Calls `TodoApp(serverUrl = "http://10.0.2.2:$SERVER_PORT/")`. The URL is overridden here because Android emulators cannot reach `localhost` on the host machine — `10.0.2.2` is the emulator's alias for the host loopback. Physical Android devices need the host machine's actual LAN IP instead.
- `iosMain/main.kt` — iOS. Calls `TodoApp()` with the default `localhost:8080` URL. Works on simulator (which shares the host network); physical devices need a LAN IP change here too.

**`androidMain/res/xml/network_security_config.xml`** — explicitly permits cleartext HTTP to `localhost` and `10.0.2.2`. Android blocks unencrypted HTTP by default from API 28+. If the server URL changes (e.g., a real LAN IP), add that domain here or the requests will be silently dropped.

**Platform-specific HTTP engines (all wired via `composeApp/build.gradle.kts` source set dependencies):**
- JVM (desktop): `ktor-client-okhttp`
- Android: `ktor-client-okhttp`
- Wasm: `ktor-client-js` (uses the browser's `fetch` API)
- iOS: `ktor-client-darwin`

The `TodoApi` interface and `rememberTodoApi()` are in `commonMain` — they work identically across platforms because Ktor abstracts the engine.

### Server (`server`)

**`Server.kt`**

- `main()` starts Netty on `SERVER_PORT` (8080).
- `Application.module()` installs plugins (CallLogging, CORS, ContentNegotiation) and registers routes.
- CORS is open (`anyHost()`) — fine for local dev, lock it down before any public deployment.
- JSON is configured with `ignoreUnknownKeys = true` and `explicitNulls = false`. These match the client config — null fields are omitted from the wire, and both sides tolerate unknown fields.
- `staticFiles("/", ...)` serves the Wasm build output. The path is relative to the server's working directory, which must be the project root.
- `dbQuery {}` is the coroutine bridge for all database access. It wraps `suspendTransaction` (Exposed's coroutine transaction) inside `withContext(Dispatchers.IO)`. Both are required: `withContext(Dispatchers.IO)` moves blocking JDBC onto a thread pool sized for I/O so Netty's event loop stays free; `suspendTransaction` provides the Exposed transaction context. Every Exposed call must happen inside `dbQuery {}` — calling Exposed outside a transaction will throw at runtime.
- Demo data: if `DEMO=true` env var is set, all existing todos are deleted and ~50 items from `server/src/main/resources/todos.txt` are seeded on startup (Bavaria/Munich themed). The server picks 50 at random.

**Routes under `/api/todos`:**

| Method | Path | What it does |
|---|---|---|
| GET | `/api/todos` | Returns all todos ordered by `createdAt` DESC |
| POST | `/api/todos` | Creates a todo, returns the created item |
| PUT | `/api/todos/{id}` | Updates `done` and optionally `title` |
| DELETE | `/api/todos/{id}` | Deletes by id, returns 204 or 404 |

**`db/Database.kt`** — connects to SQLite, enables WAL mode via raw JDBC (before Exposed initializes, so it can't be done through the Exposed API), then runs `SchemaUtils.create(TodoItems)` to create the table if it doesn't exist. Not a migration tool — schema changes require dropping and recreating the DB.

**`db/TodoSchema.kt`** — three things in one file:
1. `TodoItems` — the Exposed `IntIdTable`. This is the single source of truth for the schema. Both DAO and DSL use it.
2. `TodoItemEntity` — DAO-style entity. Property reads/writes are tracked by Exposed and flushed inside the transaction.
3. `ResultRow.toTodoItem()` and `InsertStatement.toTodoItem()` — DSL-style mappers. These produce the same `TodoItem` output the DAO entity does, so the route handlers are identical regardless of which Exposed style is active.

**DAO vs DSL — why both exist:**  
The routes contain two implementations side by side: one using the Exposed DAO entity (`TodoItemEntity`), one using the DSL (`TodoItems.selectAll()`, `TodoItems.insert {}`, etc.). One is active; the other is commented out. This is deliberate — the codebase is designed to show both Exposed styles against the same schema. The DAO and DSL are not interchangeable in style but produce identical results. To switch a route from DAO to DSL (or vice versa), comment one block and uncomment the other.

### Shared (`shared`)

- `TodoModels.kt` — `@Serializable` data classes. `UpdateTodoRequest.title` is nullable (`String? = null`) so clients can send done-only updates without resending the title. With `explicitNulls = false`, the null field is simply omitted from the JSON payload.
- `Constants.kt` — `SERVER_PORT = 8080`. Used by `TodoApp` as the default server URL base and by `Server.kt`.

## Development workflow

**Wasm (browser):**
1. Run the **Server** run config (Gradle `:server:run`, working dir = project root)
2. Run the **Wasm** run config (Gradle `:composeApp:wasmJsBrowserDevelopmentExecutableDistribution --continuous`)
3. Open `http://localhost:8080`
4. After a Kotlin change, Gradle rebuilds the Wasm bundle automatically; reload the browser to pick it up

**Desktop:** run the **Desktop** run config (`:composeApp:run`). Server must be running separately.

**Android:** run the **Android App** run config. Server must be running on the host machine; the emulator reaches it via `10.0.2.2:8080`.

**iOS:** run from Xcode. Xcode triggers the Kotlin/Native build via a build phase script. The resulting framework is `ComposeApp.framework` (static). Server must be running; simulator reaches it via `localhost:8080`.

## IntelliJ run configurations

### Server (Gradle)
- Task: `:server:run`
- Working directory: `$PROJECT_DIR$` — required. The static file path and SQLite path are both relative to this.
- Environment variable: `DEMO=true` to wipe the database and seed demo data on startup.

### Wasm (Gradle)
- Task: `:composeApp:wasmJsBrowserDevelopmentExecutableDistribution`
- Arguments: `--continuous`
- No port — Ktor serves the output. Reload the browser after Gradle finishes a rebuild.

### Desktop (Gradle)
- Task: `:composeApp:run`

### Android (Android App)
- Requires the Android plugin (Preferences > Plugins > "Android" by JetBrains) or Android Studio.
- Module: `kc2026.composeApp`

### iOS
- Open `iosApp/iosApp.xcodeproj` in Xcode. No separate Gradle run config — Xcode invokes Gradle via a build phase script.

## Common tasks

**Adding a field to `TodoItem`:**
1. Add to `TodoItem` in `shared/TodoModels.kt`
2. Add column to `TodoItems` in `db/TodoSchema.kt`
3. Update `TodoItemEntity` properties and `toTodoItem()` in `db/TodoSchema.kt`
4. Update DSL mappers (`ResultRow.toTodoItem()`, `InsertStatement.toTodoItem()`) in `db/TodoSchema.kt`
5. Delete `data/kc2026.db` — no migrations, schema is recreated on next server start
6. Update any route handlers in `Server.kt` that construct or read the affected fields
7. Update client-side usage in `composeApp` if the field is displayed or sent

**Adding a new API endpoint:**
1. Add a method to `TodoApi` interface in `composeApp/TodoApi.kt` with the appropriate Ktorfit annotation
2. Trigger a Gradle build — KSP regenerates the implementation
3. Add the route to `Server.kt` inside `route("/api/todos") { ... }` (or a new `route` block)
4. Add request/response types to `shared/TodoModels.kt` if needed

**Adding a new Compose screen or component:**
Follow the existing pattern: stateless composable that receives all data and callbacks as parameters, stateful logic lives in `TodoApp.kt` or a new equivalent container.

## Expected Gradle warnings

These appear on every build and are benign:

- `The 'ksp' configuration is deprecated in KMP projects. Use 'kspJvm' instead.` — comes from the Ktorfit KSP configuration; harmless.
- `The 'org.jetbrains.kotlin.multiplatform' plugin deprecated compatibility with com.android.application.` — the KMP+Android app in one module is deprecated starting AGP 9. The project still builds and runs; migration would separate Android into its own module.
- `Configuration 'wasmJsNpmAggregated' was resolved during configuration time.` — a Kotlin/Wasm plugin issue; no action needed.

## No tests

There are no tests in this project. The standard build command skips them: `./gradlew build -x test`.

## Versions

| | |
|---|---|
| Kotlin | 2.3.21 |
| KSP | 2.3.8 (independent versioning from Kotlin as of 2.3.x) |
| Compose Multiplatform | 1.11.0 (dropped `iosX64` / Intel iOS simulator in this release) |
| Ktor | 3.3.3 |
| Exposed | 1.1.1 |
| AGP | 9.0.1 |

## Wasm output

Built to: `composeApp/build/dist/wasmJs/developmentExecutable/`

Key files in the bundle:
- `index.html` — HTML shell (from `src/webMain/resources/`), shows a spinner while the Wasm runtime loads
- `composeApp.js` — webpack entry point
- `kc2026-composeApp.wasm` — the compiled Kotlin/Wasm binary (~24 MB in dev mode)
- `skiko.wasm` — Skia rendering engine (~8 MB)
