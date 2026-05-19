package com.dankim.kc2026

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kc2026.composeapp.generated.resources.Res
import kc2026.composeapp.generated.resources.kodee_munich
import org.jetbrains.compose.resources.painterResource

@Composable
@Preview
fun TodoScreenPreview() {
  TodoScreen(
    todos = listOf(
      TodoItem(id = 1, title = "Set up Ktor server", done = true, createdAt = 0),
      TodoItem(id = 2, title = "Add Exposed database layer", done = true, createdAt = 0),
      TodoItem(id = 3, title = "Build Compose Wasm UI", done = false, createdAt = 0),
      TodoItem(id = 4, title = "Wire up full-stack CRUD", done = false, createdAt = 0),
      TodoItem(id = 5, title = "Present at KotlinConf 2026", done = false, createdAt = 0),
    ),
    listState = rememberLazyListState(),
    onAddClick = {},
    onToggle = {},
    onEdit = {},
    onDelete = {},
  )
}

// Stateless — receives everything it needs as parameters, emits events via callbacks.
// TodoApp owns all state; this composable just renders it.
@Composable
fun TodoScreen(
  todos: List<TodoItem>,
  listState: LazyListState,
  onAddClick: () -> Unit,
  onToggle: (TodoItem) -> Unit,
  onEdit: (TodoItem) -> Unit,
  onDelete: (TodoItem) -> Unit,
) {
  MaterialTheme {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
      Box(modifier = Modifier.fillMaxSize()) {
        Column(
          modifier = Modifier
            .align(Alignment.TopCenter)
            .widthIn(max = 640.dp)
            .fillMaxWidth()
            .fillMaxHeight()
            .padding(horizontal = 24.dp, vertical = 28.dp),
        ) {
          TodoHeader(todos = todos, onAddClick = onAddClick,)
          Spacer(modifier = Modifier.height(12.dp))
          HorizontalDivider()
          Spacer(modifier = Modifier.height(4.dp))
          TodoList(
            modifier = Modifier.weight(1f),
            todos = todos,
            listState = listState,
            onToggle = onToggle,
            onEdit = onEdit,
            onDelete = onDelete,
          )
        }
      }
    }
  }
}

@Composable
private fun TodoHeader(
  todos: List<TodoItem>,
  onAddClick: () -> Unit,
) {
  val done = todos.count { it.done }
  val pending = todos.size - done

  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.spacedBy(12.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Column(modifier = Modifier.weight(1f)) {
      Text(
        text = "KotlinConf 2026 Todos",
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.SemiBold,
      )
      Text(
        text = "${todos.size} items · $done complete · $pending incomplete",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
    Column(horizontalAlignment = Alignment.End) {
      Image(
        painter = painterResource(Res.drawable.kodee_munich),
        contentDescription = "Kodee with German flag",
        modifier = Modifier
          .width(64.dp)
          .height(58.dp)
          .graphicsLayer { rotationZ = -12f },
      )
      Button(onClick = onAddClick) { Text("Add") }
    }
  }
}

@Composable
private fun TodoList(
  modifier: Modifier = Modifier,
  todos: List<TodoItem>,
  listState: LazyListState,
  onToggle: (TodoItem) -> Unit,
  onEdit: (TodoItem) -> Unit,
  onDelete: (TodoItem) -> Unit,
) {
  // key = { it.id } gives Compose a stable identity per item so it can animate
  // insertions/removals correctly and avoid recomposing items that didn't change.
  LazyColumn(state = listState, modifier = modifier) {
    items(todos, key = { it.id }) { todo ->
      TodoRow(
        todo = todo,
        onToggle = { onToggle(todo) },
        onEdit = { onEdit(todo) },
        onDelete = { onDelete(todo) },
      )
      HorizontalDivider()
    }
  }
}

@Composable
private fun TodoRow(
  todo: TodoItem,
  onToggle: () -> Unit,
  onEdit: () -> Unit,
  onDelete: () -> Unit,
) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .padding(vertical = 4.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Checkbox(checked = todo.done, onCheckedChange = { onToggle() })
    TodoTitleText(todo = todo, onClick = onEdit, modifier = Modifier.weight(1f))
    TextButton(onClick = onDelete) { Text("Delete") }
  }
}

@Composable
private fun TodoTitleText(todo: TodoItem, onClick: () -> Unit, modifier: Modifier = Modifier) {
  val style = if (todo.done) {
    TextStyle(textDecoration = TextDecoration.LineThrough)
  } else {
    TextStyle(textDecoration = TextDecoration.Underline)
  }
  val color = if (todo.done) {
    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
  } else {
    MaterialTheme.colorScheme.primary
  }

  Text(
    text = todo.title,
    modifier = modifier
      .padding(horizontal = 8.dp)
      .clickable(
        indication = ripple(color = MaterialTheme.colorScheme.primary),
        interactionSource = remember { MutableInteractionSource() },
        onClick = onClick,
      ),
    style = style,
    color = color,
  )
}

@Composable
fun AddTodoDialog(onDismiss: () -> Unit, onAdd: (String) -> Unit) {
  TodoInputDialog(
    title = "New Todo",
    placeholder = "What needs to be done?",
    confirmLabel = "Add",
    onDismiss = onDismiss,
    onConfirm = onAdd,
  )
}

@Composable
fun EditTodoDialog(todo: TodoItem, onDismiss: () -> Unit, onUpdate: (String) -> Unit) {
  TodoInputDialog(
    title = "Update Todo",
    confirmLabel = "Update",
    initialValue = TextFieldValue(todo.title, selection = TextRange(todo.title.length)),
    onDismiss = onDismiss,
    onConfirm = onUpdate,
  )
}

@Composable
private fun TodoInputDialog(
  title: String,
  confirmLabel: String,
  initialValue: TextFieldValue = TextFieldValue(""),
  placeholder: String? = null,
  onDismiss: () -> Unit,
  onConfirm: (String) -> Unit,
) {
  var field by remember { mutableStateOf(initialValue) }
  val focusRequester = remember { FocusRequester() }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(title) },
    text = {
      TextField(
        value = field,
        onValueChange = { field = it },
        placeholder = placeholder?.let { { Text(it) } },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { if (field.text.isNotBlank()) onConfirm(field.text) }),
        modifier = Modifier
          .fillMaxWidth()
          .focusRequester(focusRequester)
          .onKeyEvent {
            if (it.key == Key.Escape) {
              onDismiss(); true
            } else false
          },
      )
    },
    confirmButton = {
      TextButton(onClick = { onConfirm(field.text) }, enabled = field.text.isNotBlank()) {
        Text(confirmLabel)
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) { Text("Cancel") }
    },
  )

  // Request focus after the first composition so the keyboard appears immediately.
  LaunchedEffect(Unit) { focusRequester.requestFocus() }
}
