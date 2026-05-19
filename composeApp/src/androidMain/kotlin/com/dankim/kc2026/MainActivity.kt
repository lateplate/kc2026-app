package com.dankim.kc2026

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.ui.Modifier

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContent {
      Box(Modifier.safeDrawingPadding()) {
        TodoApp(serverUrl = "http://10.0.2.2:$SERVER_PORT/")
      }
    }
  }
}
