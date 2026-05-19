package com.dankim.kc2026

import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.*
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.launch

@Composable
fun TodoApp(serverUrl: String = "http://localhost:$SERVER_PORT/") {
  val api = rememberTodoApi(serverUrl)
  val scope = rememberCoroutineScope {
    CoroutineExceptionHandler { _, t -> println("ERROR: ${t::class.simpleName}: ${t.message}") }
  }

  var todos by remember { mutableStateOf<List<TodoItem>>(emptyList()) }
  var addingTodo by remember { mutableStateOf(false) }
  var editingTodo by remember { mutableStateOf<TodoItem?>(null) }
  val listState = rememberLazyListState()
  var scrollToTopTrigger by remember { mutableStateOf(0) }

  LaunchedEffect(Unit) { todos = api.getTodos() }

  LaunchedEffect(scrollToTopTrigger) {
    if (scrollToTopTrigger > 0) listState.scrollToItem(0)
  }

  fun toggle(todo: TodoItem) = scope.launch {
    api.updateTodo(todo.id, UpdateTodoRequest(done = !todo.done))
    todos = api.getTodos()
  }

  fun delete(todo: TodoItem) = scope.launch {
    api.deleteTodo(todo.id)
    todos = api.getTodos()
  }

  TodoScreen(
    todos = todos,
    listState = listState,
    onAddClick = { addingTodo = true },
    onToggle = { todo -> toggle(todo) },
    onEdit = { todo -> editingTodo = todo },
    onDelete = { todo -> delete(todo) },
  )

  if (addingTodo) {
    AddTodoDialog(
      onDismiss = { addingTodo = false },
      onAdd = { title ->
        addingTodo = false
        scope.launch {
          api.createTodo(CreateTodoRequest(title))
          todos = api.getTodos()
          scrollToTopTrigger++
        }
      },
    )
  }

  editingTodo?.let { todo ->
    EditTodoDialog(
      todo = todo,
      onDismiss = { editingTodo = null },
      onUpdate = { newTitle ->
        editingTodo = null
        scope.launch {
          api.updateTodo(todo.id, UpdateTodoRequest(done = todo.done, title = newTitle))
          todos = api.getTodos()
        }
      },
    )
  }
}
