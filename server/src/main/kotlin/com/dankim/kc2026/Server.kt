package com.dankim.kc2026

import com.dankim.kc2026.db.Database
import com.dankim.kc2026.db.TodoItemEntity
import com.dankim.kc2026.db.TodoItems
import com.dankim.kc2026.db.toTodoItem
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.http.content.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.calllogging.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.jdbc.deleteAll
import org.jetbrains.exposed.v1.jdbc.insert
// DSL imports — uncomment these when switching routes to the DSL style below:
// import org.jetbrains.exposed.v1.core.eq          // top-level infix eq for where/deleteWhere
// import org.jetbrains.exposed.v1.jdbc.deleteWhere
// import org.jetbrains.exposed.v1.jdbc.insert
// import org.jetbrains.exposed.v1.jdbc.selectAll
// import org.jetbrains.exposed.v1.jdbc.update
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.slf4j.event.Level
import java.io.File

fun main() {
  embeddedServer(Netty, port = SERVER_PORT, host = "0.0.0.0", module = Application::module)
    .start(wait = true)
}

fun Application.module() {
  Database.init()

  if (System.getenv("DEMO") == "true") {
    transaction {
      TodoItems.deleteAll()

      // ── DAO ──────────────────────────────────────────────────────────────
      loadDemoData().shuffled().take(50).forEach { title ->
        TodoItemEntity.new {
          this.title = title
          this.done = false
          this.createdAt = System.currentTimeMillis()
        }
      }

      // ── DSL ──────────────────────────────────────────────────────────────
      // loadDemoData().shuffled().take(50).forEach { title ->
      //   TodoItems.insert {
      //     it[TodoItems.title] = title
      //     it[done] = false
      //     it[createdAt] = System.currentTimeMillis()
      //   }
      // }
    }
  }

  install(CallLogging) {
    level = Level.INFO
  }

  // Open CORS for local dev. Lock this down to your actual domain in production.
  install(CORS) {
    anyHost()
    allowMethod(HttpMethod.Get)
    allowMethod(HttpMethod.Post)
    allowMethod(HttpMethod.Put)
    allowMethod(HttpMethod.Delete)
    allowHeader(HttpHeaders.ContentType)
  }

  // Without this, Ktor doesn't know how to serialize/deserialize JSON.
  // ignoreUnknownKeys lets the client add fields without breaking the server.
  // explicitNulls = false omits null fields from responses (matches the client config).
  install(ContentNegotiation) {
    json(Json {
      ignoreUnknownKeys = true
      explicitNulls = false
    })
  }

  routing {
    // Serves the Wasm app from the Gradle build output. Fine for a demo — in production
    // you'd copy the dist into server resources or serve from a CDN.
    staticFiles("/", File("composeApp/build/dist/wasmJs/developmentExecutable"))

    route("/api/todos") {
      get {
        // ── DAO ──────────────────────────────────────────────────────────
        val todos = dbQuery {
          TodoItemEntity.all()
            .orderBy(TodoItems.createdAt to SortOrder.DESC)
            .map { it.toTodoItem() }
        }
        // ── DSL ──────────────────────────────────────────────────────────
        // val todos = dbQuery {
        //   TodoItems.selectAll()
        //     .orderBy(TodoItems.createdAt to SortOrder.DESC)
        //     .map { it.toTodoItem() }
        // }
        call.respond(todos)
      }
      post {
        val request = call.receive<CreateTodoRequest>()
        // ── DAO ──────────────────────────────────────────────────────────
         val todo = dbQuery {
           TodoItemEntity.new {
             title = request.title
             done = false
             createdAt = System.currentTimeMillis()
           }.toTodoItem()
         }
        // ── DSL ──────────────────────────────────────────────────────────
//        val todo = dbQuery {
//          TodoItems.insert {
//            it[title] = request.title
//            it[done] = false
//            it[createdAt] = System.currentTimeMillis()
//          }.toTodoItem()
//        }
        call.respond(HttpStatusCode.Created, todo)
      }
      put("/{id}") {
        val id = call.parameters["id"]?.toIntOrNull()
          ?: return@put call.respond(HttpStatusCode.BadRequest)
        val request = call.receive<UpdateTodoRequest>()
        // ── DAO ──────────────────────────────────────────────────────────
        val todo = dbQuery {
          TodoItemEntity.findById(id)?.apply {
            done = request.done
            request.title?.let { title = it }
          }?.toTodoItem()
        }
        // ── DSL ──────────────────────────────────────────────────────────
        // val todo = dbQuery {
        //   val count = TodoItems.update({ TodoItems.id eq id }) {
        //     it[done] = request.done
        //     request.title?.let { t -> it[title] = t }
        //   }
        //   if (count == 0) null
        //   else TodoItems.selectAll().where { TodoItems.id eq id }.single().toTodoItem()
        // }
        if (todo != null) call.respond(todo)
        else call.respond(HttpStatusCode.NotFound)
      }
      delete("/{id}") {
        val id = call.parameters["id"]?.toIntOrNull()
          ?: return@delete call.respond(HttpStatusCode.BadRequest)
        // ── DAO ──────────────────────────────────────────────────────────
        val deleted = dbQuery {
          TodoItemEntity.findById(id)?.delete() != null
        }
        // ── DSL ──────────────────────────────────────────────────────────
        // val deleted = dbQuery {
        //   TodoItems.deleteWhere { TodoItems.id eq id } > 0
        // }
        if (deleted) call.respond(HttpStatusCode.NoContent)
        else call.respond(HttpStatusCode.NotFound)
      }
    }
  }
}

// Exposed DB bridge

// JDBC is blocking — it occupies a thread for the duration of every call.
// withContext(Dispatchers.IO) is what actually solves this: it moves work onto a thread
// pool sized for blocking I/O, keeping Netty's event loop free for other requests.
// suspendTransaction is Exposed's coroutine-aware transaction wrapper — it lets you call
// other suspend functions inside a transaction, but does NOT make JDBC non-blocking.
// Both are required: withContext for thread management, suspendTransaction for the transaction.
suspend fun <T> dbQuery(block: suspend () -> T): T =
  withContext(Dispatchers.IO) { suspendTransaction { block() } }

private fun loadDemoData(): List<String> =
  object {}::class.java.classLoader
    .getResourceAsStream("todos.txt")!!
    .bufferedReader()
    .readLines()
    .filter { it.isNotBlank() }
