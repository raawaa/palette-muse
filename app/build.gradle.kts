plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.compose.compiler)
  alias(libs.plugins.kotlin.serialization)
  alias(libs.plugins.hilt.android)
  alias(libs.plugins.ksp)
}

android {
    // ── Instrumented test gating ──────────────────────────────────────────────────
    /*
     * Device-targeted instrumented test execution via palette.targetDevice property.
     *   device (default):
     *     ./gradlew :app:connectedDebugAndroidTest
     *       → runs ColorAnalyzerTest, DebugFrameDumperTest
     *       → SKIPS Compose UI tests (CaptureConfirmSheetTest, LowConfidenceHintTest,
     *         ThemeDetailScreenTest), which hang on certain OEM ROMs due to the
     *         Espresso/Compose idling-resource ↔ Choreographer interaction (#27).
     *   emulator:
     *     ./gradlew :app:connectedDebugAndroidTest -Ppalette.targetDevice=emulator
     *       → runs all androidTest classes including the three Compose UI tests.
     *   any other value is REJECTED — a typo must not silently fall back to device
     *   mode and re-introduce the hang. See issue #27 / the #28 triage comment.
     */
    namespace = "com.palettemuse"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.palettemuse"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        // Compose UI tests hang on certain OEM ROMs (#27). Default to "device"
        // mode (gating on) but REJECT any value other than {"device","emulator"}
        // — a typo must not silently fall back to "device" and re-introduce
        // the hang. Runner version: androidx.test:runner:1.7.0
        // (gradle/libs.versions.toml → androidxTestRunner).
        val targetDevice = project.findProperty("palette.targetDevice")?.toString() ?: "device"
        require(targetDevice in setOf("device", "emulator")) {
            "palette.targetDevice must be 'device' or 'emulator' (got '$targetDevice'). " +
                "See comment at app/build.gradle.kts:9-24."
        }
        logger.lifecycle("palette.targetDevice=$targetDevice — instrumented gating ${
            if (targetDevice == "device") "ON (skip Compose UI tests)" else "OFF (run all)"
        }")
        if (targetDevice == "device") {
            testInstrumentationRunnerArguments["notClass"] =
                "com.palettemuse.ui.capture.CaptureConfirmSheetTest," +
                    "com.palettemuse.ui.capture.LowConfidenceHintTest," +
                    "com.palettemuse.ui.theme.ThemeDetailScreenTest"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
      compose = true
      aidl = false
      buildConfig = false
      shaders = false
    }

    packaging {
      resources {
        excludes += "/META-INF/{AL2.0,LGPL2.1}"
      }
    }
}

kotlin {
    jvmToolchain(17)
}

// Room: export schemas for migration tests
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

// Mirror Room schemas into the androidTest assets so MigrationTestHelper can load them.
android {
    sourceSets.named("androidTest").configure {
        assets.srcDirs("$projectDir/schemas")
    }
}

dependencies {
  val composeBom = platform(libs.androidx.compose.bom)
  implementation(composeBom)
  androidTestImplementation(composeBom)

  // Core Android dependencies
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.activity.compose)

  // Arch Components
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.viewmodel.compose)

  // Compose
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.material.icons.extended)
  // Tooling
  debugImplementation(libs.androidx.compose.ui.tooling)
  // Instrumented tests
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  debugImplementation(libs.androidx.compose.ui.test.manifest)

  // Local tests: jUnit, coroutines, Android runner
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)

  // Instrumented tests: jUnit rules and runners
  androidTestImplementation(libs.androidx.test.core)
  androidTestImplementation(libs.androidx.test.ext.junit)
  androidTestImplementation(libs.androidx.test.runner)
  androidTestImplementation(libs.androidx.test.espresso.core)
  androidTestImplementation(libs.room.testing)
  // Direct dep for runTest / advanceUntilIdle / StandardTestDispatcher
  // (transitive pull from androidx.test only surfaces a BOM constraint).
  androidTestImplementation(libs.kotlinx.coroutines.test)

  // Navigation
  implementation(libs.androidx.navigation3.ui)
  implementation(libs.androidx.navigation3.runtime)
  implementation(libs.androidx.lifecycle.viewmodel.navigation3)

  // ===== Palette Muse specific =====
  // Hilt
  implementation(libs.hilt.android)
  ksp(libs.hilt.compiler)
  implementation(libs.hilt.navigation.compose)

  // Room
  implementation(libs.room.runtime)
  implementation(libs.room.ktx)
  ksp(libs.room.compiler)

  // CameraX
  implementation(libs.camerax.core)
  implementation(libs.camerax.camera2)
  implementation(libs.camerax.lifecycle)
  implementation(libs.camerax.view)

  // Lottie
  implementation(libs.lottie.compose)

  // Coil
  implementation(libs.coil.compose)
}
