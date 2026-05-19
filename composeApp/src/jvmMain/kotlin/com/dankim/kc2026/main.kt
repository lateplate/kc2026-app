package com.dankim.kc2026

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application

fun main() = application {
  Window(onCloseRequest = ::exitApplication, title = "KC2026") {
    TodoApp()
  }
}
