package com.dankim.kc2026

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import de.jensklingenberg.ktorfit.Ktorfit
import de.jensklingenberg.ktorfit.http.*
import de.jensklingenberg.ktorfit.http.Headers
import io.ktor.client.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json

// Ktorfit turns this interface into a working HTTP client at compile time (via KSP),
// similar to how Retrofit works on Android. Each annotation maps to an HTTP verb and path.
// Every call is a suspend function — no callbacks, no manual threading.
internal interface TodoApi {
  @GET("api/todos")
  suspend fun getTodos(): List<TodoItem>

  @Headers("Content-Type: application/json")
  @POST("api/todos")
  suspend fun createTodo(@Body request: CreateTodoRequest): TodoItem

  @Headers("Content-Type: application/json")
  @PUT("api/todos/{id}")
  suspend fun updateTodo(@Path("id") id: Int, @Body request: UpdateTodoRequest): TodoItem

  @DELETE("api/todos/{id}")
  suspend fun deleteTodo(@Path("id") id: Int)
}

// Scoped to the composition lifetime — the HttpClient is created once and closed
// when the composable leaves the tree. In an Android app with a ViewModel you'd
// manage the client lifecycle there instead.
@Composable
internal fun rememberTodoApi(serverUrl: String): TodoApi {
  val client = rememberHttpClient()
  return remember(client) {
    Ktorfit.Builder()
      .httpClient(client)
      .baseUrl(serverUrl)
      .build()
      .createTodoApi()
  }
}

@Composable
private fun rememberHttpClient(): HttpClient {
  val json = remember {
    Json {
      ignoreUnknownKeys = true
      explicitNulls = false
    }
  }
  val client = remember {
    HttpClient {
      install(ContentNegotiation) { json(json) }
      defaultRequest {
        contentType(ContentType.Application.Json)
        accept(ContentType.Application.Json)
      }
    }
  }
  DisposableEffect(client) { onDispose { client.close() } }
  return client
}
