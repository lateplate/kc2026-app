plugins {
  alias(libs.plugins.kotlinJvm)
  alias(libs.plugins.kotlinSerialization)
  alias(libs.plugins.ktor)
  application
}

group = "com.dankim.kc2026"
version = "1.0.0"
application {
  mainClass.set("com.dankim.kc2026.ServerKt")

  val isDevelopment: Boolean = project.ext.has("development")
  applicationDefaultJvmArgs = listOf("-Dio.ktor.development=$isDevelopment")
}

// Run task defaults to the server/ subproject dir — set to root so relative
// paths like composeApp/build/... resolve correctly.
tasks.named<JavaExec>("run") {
  workingDir = rootProject.projectDir
}

dependencies {
  implementation(projects.shared)
  implementation(libs.logback)
  implementation(libs.exposed.core)
  implementation(libs.exposed.dao)
  implementation(libs.exposed.jdbc)
  implementation(libs.sqlite.jdbc)
  implementation(libs.ktor.server.cors)
  implementation(libs.ktor.server.callLogging)
  implementation(libs.ktor.serverCore)
  implementation(libs.ktor.serverNetty)
  implementation(libs.ktor.server.contentNegotiation)
  implementation(libs.ktor.serialization.kotlinxJson)
  testImplementation(libs.ktor.serverTestHost)
  testImplementation(libs.kotlin.testJunit)
}
