package com.dankim.kc2026

import kotlinx.serialization.Serializable

// Shared between client and server — one definition, no duplication.
// @Serializable is from kotlinx.serialization; Ktor uses it on the server for JSON
// responses, and Ktorfit uses it on the client to deserialize those same responses.
@Serializable
data class TodoItem(
  val id: Int,
  val title: String,
  val done: Boolean,
  val createdAt: Long,
)

// Separate request types rather than reusing TodoItem — keeps the API contract
// explicit about what callers can and can't set (e.g. id and createdAt are server-assigned).
@Serializable
data class CreateTodoRequest(val title: String)

// title is nullable so a toggle (done-only update) doesn't need to resend the title.
// explicitNulls = false in the JSON config means null fields are omitted from the wire.
@Serializable
data class UpdateTodoRequest(val done: Boolean, val title: String? = null)
