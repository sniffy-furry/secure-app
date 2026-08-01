plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
    id("com.android.application")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
    id("app.cash.sqldelight")
}

kotlin {
    androidTarget {
        compilations.all {
            kotlinOptions.jvmTarget = "17"
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation(compose.components.resources)

            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
            implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
            implementation("app.cash.sqldelight:runtime:2.0.2")
            implementation("app.cash.sqldelight:coroutines-extensions:2.0.2")

            // Ed25519 signing, key generation, and Blake2b hashing via libsodium bindings.
            // Chosen because it's one of the few crypto libs with real Kotlin Multiplatform
            // support (needed later for Noise handshake + Double Ratchet in Phase 2).
            implementation("com.ionspin.kotlin:multiplatform-crypto-libsodium-bindings:0.9.5")
        }

        androidMain.dependencies {
            implementation("app.cash.sqldelight:android-driver:2.0.2")
            // SQLCipher support for encrypted-at-rest SQLite on Android.
            implementation("net.zetetic:android-database-sqlcipher:4.5.6")
            implementation("androidx.sqlite:sqlite:2.4.0")
            implementation("androidx.activity:activity-compose:1.9.2")
            implementation("androidx.core:core-ktx:1.13.1")
            implementation("androidx.security:security-crypto:1.1.0-alpha06")
            implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
        }
    }
}

android {
    namespace = "com.nulchat"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.nulchat"
        minSdk = 26 // required for reasonable crypto + background behavior
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0-phase1"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

sqldelight {
    databases {
        create("NulChatDatabase") {
            packageName.set("com.nulchat.db")
        }
    }
}
