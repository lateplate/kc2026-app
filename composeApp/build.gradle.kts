import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
  alias(libs.plugins.kotlinMultiplatform)
  alias(libs.plugins.androidApplication)
  alias(libs.plugins.ksp)
  alias(libs.plugins.composeMultiplatform)
  alias(libs.plugins.composeCompiler)
  alias(libs.plugins.ktorfit)
}

kotlin {
  jvmToolchain(11)

  jvm()
  androidTarget()

  listOf(iosArm64(), iosSimulatorArm64()).forEach { iosTarget ->
    iosTarget.binaries.framework {
      baseName = "ComposeApp"
      isStatic = true
    }
  }

  @OptIn(ExperimentalWasmDsl::class)
  wasmJs {
    browser {
      commonWebpackConfig {
        devServer =
          devServer?.copy(port = 8081) ?: org.jetbrains.kotlin.gradle.targets.js.webpack.KotlinWebpackConfig.DevServer(
            port = 8081
          )
      }
    }
    binaries.executable()
  }

  sourceSets {
    commonMain.dependencies {
      implementation(libs.compose.runtime)
      implementation(libs.compose.foundation)
      implementation(libs.compose.material3)
      implementation(libs.compose.ui)
      implementation(libs.compose.components.resources)
      implementation(libs.compose.uiToolingPreview)
      implementation(libs.androidx.lifecycle.viewmodelCompose)
      implementation(libs.androidx.lifecycle.runtimeCompose)
      implementation(libs.ktor.client.core)
      implementation(libs.ktor.client.contentNegotiation)
      implementation(libs.ktor.serialization.kotlinxJson)
      implementation(libs.ktorfit.lib.light)
      implementation(projects.shared)
    }
    jvmMain.dependencies {
      implementation(compose.desktop.currentOs)
      implementation(libs.kotlinx.coroutinesSwing)
      implementation(libs.ktor.client.okhttp)
    }
    androidMain.dependencies {
      implementation(libs.androidx.activityCompose)
      implementation(libs.ktor.client.okhttp)
    }
    wasmJsMain.dependencies {
      implementation(libs.ktor.client.js)
    }
    iosMain.dependencies {
      implementation(libs.ktor.client.darwin)
    }
  }
}

android {
  namespace = "com.dankim.kc2026"
  compileSdk = libs.versions.android.compileSdk.get().toInt()

  defaultConfig {
    applicationId = "com.dankim.kc2026"
    minSdk = libs.versions.android.minSdk.get().toInt()
    targetSdk = libs.versions.android.compileSdk.get().toInt()
    versionCode = 1
    versionName = "1.0.0"
  }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }

  lint {
    disable += "MissingApplicationIcon"
    abortOnError = false
    checkReleaseBuilds = false
    checkDependencies = false
  }
}

ktorfit {
  compilerPluginVersion.set("-")
}

compose.desktop {
  application {
    mainClass = "com.dankim.kc2026.MainKt"

    nativeDistributions {
      targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
      packageName = "com.dankim.kc2026"
      packageVersion = "1.0.0"
    }
  }
}
