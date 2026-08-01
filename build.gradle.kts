plugins {
    // Versions are centralized here; applied per-module with `apply false`
    kotlin("multiplatform") version "2.0.20" apply false
    kotlin("plugin.serialization") version "2.0.20" apply false
    id("com.android.application") version "8.5.2" apply false
    id("org.jetbrains.compose") version "1.6.11" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.20" apply false
    id("app.cash.sqldelight") version "2.0.2" apply false
}
