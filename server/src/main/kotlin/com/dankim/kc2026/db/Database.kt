package com.dankim.kc2026.db

import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.io.File
import org.jetbrains.exposed.v1.jdbc.Database as ExposedDatabase

object Database {
  private const val DB_PATH = "data/kc2026.db"

  fun init() {
    File("data").mkdirs()
    ExposedDatabase.connect("jdbc:sqlite:$DB_PATH", "org.sqlite.JDBC")
    // Creates the table if it doesn't exist; no-ops if it already does.
    // Not a migration tool — for schema changes you'd use Flyway or Liquibase.
    transaction { SchemaUtils.create(TodoItems) }
  }
}

