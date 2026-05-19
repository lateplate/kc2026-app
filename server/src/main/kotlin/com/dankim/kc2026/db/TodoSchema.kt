package com.dankim.kc2026.db

import com.dankim.kc2026.TodoItem
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.statements.InsertStatement
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.dao.id.IntIdTable
import org.jetbrains.exposed.v1.dao.IntEntity
import org.jetbrains.exposed.v1.dao.IntEntityClass

// Table definition — shared by both the DAO and DSL layers.
// IntIdTable gives us an auto-increment integer primary key named "id" for free.
object TodoItems : IntIdTable("todo_items") {
  val title = varchar("title", 255)
  val done = bool("done").default(false)
  val createdAt = long("created_at")
}

// ── DAO layer ────────────────────────────────────────────────────────────────
// Entity class maps each row to a Kotlin object. Exposed tracks property
// mutations and flushes them inside a transaction automatically.

class TodoItemEntity(id: EntityID<Int>) : IntEntity(id) {
  companion object : IntEntityClass<TodoItemEntity>(TodoItems)

  var title by TodoItems.title
  var done by TodoItems.done
  var createdAt by TodoItems.createdAt

  fun toTodoItem() = TodoItem(id.value, title, done, createdAt)
}

// ── DSL layer ────────────────────────────────────────────────────────────────
// ResultRow is the raw row type returned by DSL queries (selectAll, etc.).
// This extension maps it to the shared model the same way the entity does,
// so the API layer stays identical regardless of which Exposed style is active.

fun ResultRow.toTodoItem() = TodoItem(
  id = this[TodoItems.id].value,
  title = this[TodoItems.title],
  done = this[TodoItems.done],
  createdAt = this[TodoItems.createdAt],
)

fun InsertStatement<*>.toTodoItem() = TodoItem(
  id = this[TodoItems.id].value,
  title = this[TodoItems.title],
  done = this[TodoItems.done],
  createdAt = this[TodoItems.createdAt],
)
