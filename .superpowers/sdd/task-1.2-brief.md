### Task 1.2: 配置依赖和重命名包

**Files:**
- Modify: `gradle/libs.versions.toml` — 添加 Hilt、Room、CameraX、Palette、Coil 依赖
- Modify: `app/build.gradle.kts` — 应用 Hilt 插件、添加所有依赖、更新 minSdk/namespace
- Modify: `settings.gradle.kts` — Android 14+ 兼容性声明
- Modify: `gradle.properties` — 启用 kapt（Hilt 需要）

- [ ] **Step 1: 更新 version catalog**

编辑 `gradle/libs.versions.toml`，在 `[versions]` 块末尾添加：

```toml
# Palette Muse specific
hilt = "2.55"
hiltNavigationCompose = "1.2.0"
room = "2.7.1"
camerax = "1.5.0"
palette = "1.0.0"
coil = "3.2.0"
```

在 `[libraries]` 块末尾添加：

```toml
# Hilt
hilt-android = { module = "com.google.dagger:hilt-android", version.ref = "hilt" }
hilt-compiler = { module = "com.google.dagger:hilt-compiler", version.ref = "hilt" }
hilt-navigation-compose = { module = "androidx.hilt:hilt-navigation-compose", version.ref = "hiltNavigationCompose" }

# Room
room-runtime = { module = "androidx.room:room-runtime", version.ref = "room" }
room-ktx = { module = "androidx.room:room-ktx", version.ref = "room" }
room-compiler = { module = "androidx.room:room-compiler", version.ref = "room" }

# CameraX
camerax-core = { module = "androidx.camera:camera-core", version.ref = "camerax" }
camerax-camera2 = { module = "androidx.camera:camera-camera2", version.ref = "camerax" }
camerax-lifecycle = { module = "androidx.camera:camera-lifecycle", version.ref = "camerax" }
camerax-view = { module = "androidx.camera:camera-view", version.ref = "camerax" }

# Palette
palette-ktx = { module = "androidx.palette:palette-ktx", version.ref = "palette" }

# Coil
coil-compose = { module = "io.coil-kt.coil3:coil-compose", version.ref = "coil" }
```

在 `[plugins]` 块末尾添加：

```toml
hilt-android = { id = "com.google.dagger.hilt.android", version.ref = "hilt" }
ksp = { id = "com.google.devtools.ksp", version = "2.3.20-1.0.1" }
```

更新 Compose BOM 为最新版：

```toml
androidxComposeBom = "2026.06.00"
```

- [ ] **Step 2: 更新 app/build.gradle.kts**

编辑 `app/build.gradle.kts`：

```kotlin
plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.compose.compiler)
  alias(libs.plugins.kotlin.serialization)
  alias(libs.plugins.hilt.android)       // 新增
  alias(libs.plugins.ksp)                // 新增
}

android {
    namespace = "com.palettemuse"                // 修改
    compileSdk = 36

    defaultConfig {
        applicationId = "com.palettemuse"        // 修改
        minSdk = 26                              // 从 24 改为 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
    }

    // ... 其余保持模板设置不变
}

dependencies {
  val composeBom = platform(libs.androidx.compose.bom)
  implementation(composeBom)
  androidTestImplementation(composeBom)

  // Core Android dependencies (模板自带)
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.activity.compose)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.viewmodel.compose)

  // Compose (模板自带)
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.compose.material3)
  debugImplementation(libs.androidx.compose.ui.tooling)
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  debugImplementation(libs.androidx.compose.ui.test.manifest)

  // Test (模板自带)
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
  androidTestImplementation(libs.androidx.test.core)
  androidTestImplementation(libs.androidx.test.ext.junit)
  androidTestImplementation(libs.androidx.test.runner)
  androidTestImplementation(libs.androidx.test.espresso.core)

  // Navigation 3 (模板自带)
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

  // Palette
  implementation(libs.palette.ktx)

  // Coil
  implementation(libs.coil.compose)
}
```

- [ ] **Step 3: 更新 settings.gradle.kts**

确保启用了 AGP 9 需要的配置：

```kotlin
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.hilt.android) apply false  // 新增
    alias(libs.plugins.ksp) apply false           // 新增
}
```

- [ ] **Step 4: 重命名 package 目录**

```bash
# 创建正确的包目录
mkdir -p app/src/main/java/com/palettemuse/theme
mkdir -p app/src/main/java/com/palettemuse/data
mkdir -p app/src/main/java/com/palettemuse/di
mkdir -p app/src/main/java/com/palettemuse/ui

# 移动模板文件到新包
mv app/src/main/java/com/example/palettemuse/MainActivity.kt app/src/main/java/com/palettemuse/MainActivity.kt
mv app/src/main/java/com/example/palettemuse/Navigation.kt app/src/main/java/com/palettemuse/Navigation.kt 2>/dev/null || true
mv app/src/main/java/com/example/palettemuse/NavigationKeys.kt app/src/main/java/com/palettemuse/NavigationKeys.kt 2>/dev/null || true
mv app/src/main/java/com/example/palettemuse/theme/* app/src/main/java/com/palettemuse/theme/
mv app/src/main/java/com/example/palettemuse/data/* app/src/main/java/com/palettemuse/data/
rm -rf app/src/main/java/com/example

# 更新 AndroidManifest.xml 中的 package
sed -i '' 's/com.example.palettemuse/com.palettemuse/g' app/src/main/AndroidManifest.xml
```

- [ ] **Step 5: 更新 AndroidManifest.xml**

编辑 `app/src/main/AndroidManifest.xml`：

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <uses-permission android:name="android.permission.CAMERA" />
    <uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE"
        android:maxSdkVersion="28" />

    <uses-feature android:name="android.hardware.camera" android:required="true" />

    <application
        android:name=".PaletteMuseApp"
        android:allowBackup="true"
        android:icon="@mipmap/ic_launcher"
        android:label="@string/app_name"
        android:roundIcon="@mipmap/ic_launcher_round"
        android:supportsRtl="true"
        android:theme="@style/Theme.PaletteMuse">

        <activity
            android:name=".MainActivity"
            android:exported="true"
            android:theme="@style/Theme.PaletteMuse">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>

</manifest>
```

- [ ] **Step 6: 验证编译**

```bash
./gradlew assembleDebug --no-daemon 2>&1 | tail -10
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 7: Commit**

```bash
git add -A
git commit -m "chore: add Hilt, Room, CameraX, Palette, Coil dependencies and configure package"
```

---

