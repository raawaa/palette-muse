# Palette Muse 实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 实现 Palette Muse Android App — 时尚色彩发现个人工具，含相机取色、穿搭分析、作品画廊、海报导出功能。

**Architecture:** Single Activity + Jetpack Compose + MVVM + Hilt DI + Room 本地存储。单模块项目，按功能分包（ui/home, ui/capture, ui/analyze, ui/export, camera, core, data）。

**Tech Stack:** Jetpack Compose + Material 3 / Hilt / Room / CameraX / Palette / Coil / Compose Navigation / Kotlin Coroutines+Flow

## Global Constraints

- Min SDK: 26 (Android 8.0), Target SDK: 36 (Android 16)
- Package: `com.palettemuse`
- Compose BOM: `2026.06.00`（或更新）
- Kotlin 2.3.x（随模板），Compose Compiler Gradle Plugin（Kotlin 2.0+ 内置）
- Hilt: `2.55`（最新稳定版）
- Room: `2.7.x`
- CameraX: `1.5.x`
- Palette: `1.0.0`
- Coil: `3.x`（Compose 原生）
- 所有字符串使用 `string.xml` 资源，硬编码文本禁止
- 无需网络权限，完全本地运行

---

## Phase 1: 项目脚手架

### Task 1.1: 创建 Android 项目

**Files:**
- Create: 整个 Android 项目结构（通过 `android create` 命令生成）

- [ ] **Step 1: 使用 Android CLI 创建项目**

```bash
cd /Users/yuwenjie/Code/palette-muse
android create empty-activity --name="Palette Muse" --output=./
```

Run: 见上述命令
Expected: 项目创建成功，生成 `app/build.gradle.kts`、`gradle/libs.versions.toml`、`MainActivity.kt` 等文件

- [ ] **Step 2: 验证生成的文件结构**

```bash
find . -type f -not -path '*/build/*' -not -path '*/gradle/wrapper/*' -not -path '*/.gradle/*' | sort
```

Expected: `app/src/main/java/com/example/palettemuse/MainActivity.kt` 等模板文件存在

- [ ] **Step 3: 清理临时文件并初始化 git**

```bash
rm -rf docs/ 2>/dev/null; mkdir -p docs/superpowers/specs docs/superpowers/plans
# 将之前的设计文档和计划文档移回
cp /dev/null/.claude/projects/-Users-yuwenjie-Code-palette-muse/tmp_spec.md docs/superpowers/specs/2026-06-26-palette-muse-design.md 2>/dev/null || true
git init
git add -A
git commit -m "chore: initial project scaffold from android CLI template"
```

- [ ] **Step 4: 验证项目可编译**

```bash
./gradlew assembleDebug --no-daemon 2>&1 | tail -5
```

Expected: BUILD SUCCESSFUL

---

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

### Task 1.3: 创建 Hilt Application 和基础 Theme

**Files:**
- Create: `app/src/main/java/com/palettemuse/PaletteMuseApp.kt`
- Modify: `app/src/main/java/com/palettemuse/theme/Theme.kt` — 设计系统颜色和字体
- Modify: `app/src/main/java/com/palettemuse/theme/Color.kt` — 自定义色板
- Modify: `app/src/main/java/com/palettemuse/theme/Type.kt` — Playfair Display + Plus Jakarta Sans 字体
- Modify: `app/src/main/res/values/themes.xml`

- [ ] **Step 1: 创建 Hilt Application**

`app/src/main/java/com/palettemuse/PaletteMuseApp.kt`:

```kotlin
package com.palettemuse

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class PaletteMuseApp : Application()
```

- [ ] **Step 2: 更新 Theme.kt**

编辑 `app/src/main/java/com/palettemuse/theme/Theme.kt`：

```kotlin
package com.palettemuse.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// Rose Gold / Aura Aesthetic palette
val RoseGold = Color(0xFFB76E79)
val RoseGoldDark = Color(0xFF8A4853)
val SoftLavender = Color(0xFFE6E6FA)
val PearlWhite = Color(0xFFFDFBF7)
val InkBlack = Color(0xFF1A1A1A)
val WarmGray = Color(0xFF524345)
val OffWhite = Color(0xFFFCF9F8)
val SurfaceDim = Color(0xFFDCD9D9)
val SurfaceLow = Color(0xFFF6F3F2)
val SurfaceContainer = Color(0xFFF0EDED)

private val LightColorScheme = lightColorScheme(
    primary = RoseGold,
    onPrimary = Color.White,
    primaryContainer = RoseGoldDark,
    onPrimaryContainer = Color.White,
    secondary = SoftLavender,
    onSecondary = InkBlack,
    secondaryContainer = SoftLavender,
    onSecondaryContainer = Color(0xFF626374),
    tertiary = Color(0xFF5C5C59),
    onTertiary = Color.White,
    background = PearlWhite,
    onBackground = InkBlack,
    surface = PearlWhite,
    onSurface = InkBlack,
    surfaceVariant = OffWhite,
    onSurfaceVariant = WarmGray,
    outline = Color(0xFF857374),
    outlineVariant = Color(0xFFD7C1C3),
    error = Color(0xFFBA1A1A),
    onError = Color.White,
    inverseSurface = Color(0xFF313030),
    inverseOnSurface = Color(0xFFF3F0EF),
)

@Composable
fun PaletteMuseTheme(
    content: @Composable () -> Unit
) {
    val colorScheme = LightColorScheme
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.surface.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = true
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
```

- [ ] **Step 3: 更新 Color.kt**

`app/src/main/java/com/palettemuse/theme/Color.kt` — 保留设计系统颜色常量（如上面已定义在 Theme.kt 中，Color.kt 可为空或有额外注释），确保文件存在即可：

```kotlin
package com.palettemuse.theme

// 颜色定义在 Theme.kt 中，此文件保留用于未来扩展
```

- [ ] **Step 4: 更新 Type.kt**

`app/src/main/java/com/palettemuse/theme/Type.kt`：

```kotlin
package com.palettemuse.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val Typography = Typography(
    displayLarge = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Bold,
        fontSize = 40.sp,
        lineHeight = 48.sp,
        letterSpacing = (-0.02).sp,
    ),
    headlineLarge = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 32.sp,
        lineHeight = 40.sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 28.sp,
        lineHeight = 36.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        lineHeight = 24.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.1.sp,
    ),
)
```

- [ ] **Step 5: 创建 styles.xml**

`app/src/main/res/values/themes.xml`：

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <style name="Theme.PaletteMuse" parent="android:Theme.Material.Light.NoActionBar" />
</resources>
```

- [ ] **Step 6: 创建间距常量化 Dimens**

`app/src/main/java/com/palettemuse/ui/theme/Dimens.kt`：

```kotlin
package com.palettemuse.ui.theme

import androidx.compose.ui.unit.dp

/**
 * Stitch Aura Aesthetic 设计系统间距与圆角常量
 * container-margin: 20px | gutter: 12px | stack: 8/16/32/64
 */
object Dimens {
    // Spacing
    val containerMargin = 20.dp
    val gutter = 12.dp
    val stackSm = 8.dp
    val stackMd = 16.dp
    val stackLg = 32.dp
    val stackXl = 64.dp

    // Shapes — Hyper-Soft (minimum 24-32px pill)
    val pillShape = 28.dp
    val cardCorner = 28.dp
    val buttonCorner = 28.dp
    val chipCorner = 20.dp
    val imageCorner = 16.dp
    val fullRound = 9999.dp
}
```

- [ ] **Step 7: 验证编译**

```bash
./gradlew assembleDebug --no-daemon 2>&1 | tail -10
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 8: Commit**

```bash
git add -A
git commit -m "feat: add Hilt Application, theme, design system colors, and spacing constants"
```

---

### Task 1.4: 创建 Hilt DI 模块

**Files:**
- Create: `app/src/main/java/com/palettemuse/di/AppModule.kt`

- [ ] **Step 1: 创建 AppModule**

`app/src/main/java/com/palettemuse/di/AppModule.kt`：

```kotlin
package com.palettemuse.di

import android.content.Context
import androidx.room.Room
import com.palettemuse.core.ColorAnalyzer
import com.palettemuse.core.ColorMatcher
import com.palettemuse.core.ColorNamer
import com.palettemuse.core.PosterRenderer
import com.palettemuse.data.local.AppDatabase
import com.palettemuse.data.local.ColorPaletteDao
import com.palettemuse.data.local.ProjectDao
import com.palettemuse.data.repository.ProjectRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "palette_muse.db"
        ).build()
    }

    @Provides
    fun provideProjectDao(database: AppDatabase): ProjectDao {
        return database.projectDao()
    }

    @Provides
    fun provideColorPaletteDao(database: AppDatabase): ColorPaletteDao {
        return database.colorPaletteDao()
    }

    @Provides
    @Singleton
    fun provideProjectRepository(
        projectDao: ProjectDao,
        colorPaletteDao: ColorPaletteDao
    ): ProjectRepository {
        return ProjectRepository(projectDao, colorPaletteDao)
    }

    @Provides
    @Singleton
    fun provideColorAnalyzer(): ColorAnalyzer {
        return ColorAnalyzer()
    }

    @Provides
    @Singleton
    fun provideColorNamer(): ColorNamer {
        return ColorNamer()
    }

    @Provides
    @Singleton
    fun provideColorMatcher(): ColorMatcher {
        return ColorMatcher()
    }

    @Provides
    @Singleton
    fun providePosterRenderer(): PosterRenderer {
        return PosterRenderer()
    }
}
```

- [ ] **Step 2: 验证编译**

```bash
./gradlew assembleDebug --no-daemon 2>&1 | tail -10
```

Expected: BUILD SUCCESSFUL（因为引用的类还没写，但 Hilt 模块编译时可能只会报 warning。如果是 error，先注释掉对应的 provides）

- [ ] **Step 3: Commit**

```bash
git add -A
git commit -m "feat: add Hilt DI module with Room, Camera, Core providers"
```

---

### Task 1.5: 创建 Room 数据层

**Files:**
- Create: `app/src/main/java/com/palettemuse/data/model/ProjectEntity.kt`
- Create: `app/src/main/java/com/palettemuse/data/model/ColorPaletteEntity.kt`
- Create: `app/src/main/java/com/palettemuse/data/local/Converters.kt`
- Create: `app/src/main/java/com/palettemuse/data/local/ProjectDao.kt`
- Create: `app/src/main/java/com/palettemuse/data/local/ColorPaletteDao.kt`
- Create: `app/src/main/java/com/palettemuse/data/local/AppDatabase.kt`
- Create: `app/src/main/java/com/palettemuse/data/repository/ProjectRepository.kt`

- [ ] **Step 1: 创建数据模型枚举和实体**

`app/src/main/java/com/palettemuse/data/model/ProjectEntity.kt`：

```kotlin
package com.palettemuse.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

enum class ProjectType { CAPTURE, OUTFIT }

@Entity(tableName = "projects")
data class ProjectEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val title: String,
    val createdAt: Long = System.currentTimeMillis(),
    val imagePath: String,
    val type: ProjectType
)
```

`app/src/main/java/com/palettemuse/data/model/ColorPaletteEntity.kt`：

```kotlin
package com.palettemuse.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

enum class ColorRole { PRIMARY, SECONDARY, ACCENT }

@Entity(
    tableName = "color_palettes",
    foreignKeys = [ForeignKey(
        entity = ProjectEntity::class,
        parentColumns = ["id"],
        childColumns = ["projectId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("projectId")]
)
data class ColorPaletteEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val projectId: String,
    val role: ColorRole,
    val hexColor: String,
    val semanticName: String,
    val matchPercentage: Int? = null
)
```

- [ ] **Step 2: 创建 Room type converters**

`app/src/main/java/com/palettemuse/data/local/Converters.kt`：

```kotlin
package com.palettemuse.data.local

import androidx.room.TypeConverter
import com.palettemuse.data.model.ColorRole
import com.palettemuse.data.model.ProjectType

class Converters {
    @TypeConverter
    fun fromProjectType(value: ProjectType): String = value.name

    @TypeConverter
    fun toProjectType(value: String): ProjectType = ProjectType.valueOf(value)

    @TypeConverter
    fun fromColorRole(value: ColorRole): String = value.name

    @TypeConverter
    fun toColorRole(value: String): ColorRole = ColorRole.valueOf(value)
}
```

- [ ] **Step 3: 创建 DAOs**

`app/src/main/java/com/palettemuse/data/local/ProjectDao.kt`：

```kotlin
package com.palettemuse.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.palettemuse.data.model.ProjectEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ProjectDao {
    @Query("SELECT * FROM projects ORDER BY createdAt DESC")
    fun getAllProjects(): Flow<List<ProjectEntity>>

    @Query("SELECT * FROM projects WHERE id = :id")
    suspend fun getProject(id: String): ProjectEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(project: ProjectEntity)

    @Update
    suspend fun update(project: ProjectEntity)

    @Delete
    suspend fun delete(project: ProjectEntity)

    @Query("DELETE FROM projects WHERE id = :id")
    suspend fun deleteById(id: String)
}
```

`app/src/main/java/com/palettemuse/data/local/ColorPaletteDao.kt`：

```kotlin
package com.palettemuse.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.palettemuse.data.model.ColorPaletteEntity

@Dao
interface ColorPaletteDao {
    @Query("SELECT * FROM color_palettes WHERE projectId = :projectId ORDER BY role ASC")
    suspend fun getPalettesForProject(projectId: String): List<ColorPaletteEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(palettes: List<ColorPaletteEntity>)

    @Query("DELETE FROM color_palettes WHERE projectId = :projectId")
    suspend fun deleteByProjectId(projectId: String)
}
```

- [ ] **Step 4: 创建 AppDatabase**

`app/src/main/java/com/palettemuse/data/local/AppDatabase.kt`：

```kotlin
package com.palettemuse.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.palettemuse.data.model.ColorPaletteEntity
import com.palettemuse.data.model.ProjectEntity

@Database(
    entities = [ProjectEntity::class, ColorPaletteEntity::class],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun projectDao(): ProjectDao
    abstract fun colorPaletteDao(): ColorPaletteDao
}
```

- [ ] **Step 5: 创建 Repository**

`app/src/main/java/com/palettemuse/data/repository/ProjectRepository.kt`：

```kotlin
package com.palettemuse.data.repository

import com.palettemuse.data.local.ColorPaletteDao
import com.palettemuse.data.local.ProjectDao
import com.palettemuse.data.model.ColorPaletteEntity
import com.palettemuse.data.model.ProjectEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProjectRepository @Inject constructor(
    private val projectDao: ProjectDao,
    private val colorPaletteDao: ColorPaletteDao
) {
    fun getAllProjects(): Flow<List<ProjectEntity>> = projectDao.getAllProjects()

    suspend fun getProject(id: String): ProjectEntity? = projectDao.getProject(id)

    suspend fun getPalettesForProject(projectId: String): List<ColorPaletteEntity> =
        colorPaletteDao.getPalettesForProject(projectId)

    suspend fun saveProject(project: ProjectEntity, palettes: List<ColorPaletteEntity>) {
        projectDao.insert(project)
        colorPaletteDao.insertAll(palettes)
    }

    suspend fun deleteProject(id: String) {
        projectDao.deleteById(id)
    }
}
```

- [ ] **Step 6: 验证编译**

```bash
./gradlew assembleDebug --no-daemon 2>&1 | tail -15
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 7: Commit**

```bash
git add -A
git commit -m "feat: add Room database, entities, DAOs, and repository"
```

---

### Task 1.6: 创建 Navigation 和占位屏幕

**Files:**
- Modify: `app/src/main/java/com/palettemuse/MainActivity.kt` — 使用 Hilt + Compose
- Create: `app/src/main/java/com/palettemuse/ui/navigation/NavGraph.kt`
- Create: `app/src/main/java/com/palettemuse/ui/home/HomeScreen.kt` — 占位
- Create: `app/src/main/java/com/palettemuse/ui/capture/CaptureScreen.kt` — 占位
- Create: `app/src/main/java/com/palettemuse/ui/analyze/AnalyzeScreen.kt` — 占位
- Create: `app/src/main/java/com/palettemuse/ui/export/ExportScreen.kt` — 占位

- [ ] **Step 1: 更新 MainActivity**

`app/src/main/java/com/palettemuse/MainActivity.kt`：

```kotlin
package com.palettemuse

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.palettemuse.theme.PaletteMuseTheme
import com.palettemuse.ui.navigation.PaletteMuseNavGraph
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PaletteMuseTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    PaletteMuseNavGraph()
                }
            }
        }
    }
}
```

- [ ] **Step 2: 创建 Navigation graph**

`app/src/main/java/com/palettemuse/ui/navigation/NavGraph.kt`：

```kotlin
package com.palettemuse.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation3.NavGraph
import androidx.navigation3.NavHost
import androidx.navigation3.compose.ComposableScene
import androidx.navigation3.compose.rememberViewModelStoreNavEntryDecorator
import com.palettemuse.ui.analyze.AnalyzeScreen
import com.palettemuse.ui.capture.CaptureScreen
import com.palettemuse.ui.export.ExportScreen
import com.palettemuse.ui.home.HomeScreen

object Routes {
    const val HOME = "home"
    const val CAPTURE = "capture"
    const val ANALYZE = "analyze/{projectId}"
    const val EXPORT = "export/{projectId}"

    fun analyze(projectId: String) = "analyze/$projectId"
    fun export(projectId: String) = "export/$projectId"
}

@Composable
fun PaletteMuseNavGraph() {
    NavHost(
        startDestination = Routes.HOME,
        entryDecorators = listOf(rememberViewModelStoreNavEntryDecorator())
    ) {
        scene(Routes.HOME) {
            HomeScreen(
                onNavigateToCapture = { navigate(Routes.CAPTURE) },
                onNavigateToAnalyze = { projectId -> navigate(Routes.analyze(projectId)) }
            )
        }
        scene(Routes.CAPTURE) {
            CaptureScreen(
                onNavigateToAnalyze = { projectId ->
                    navigate(Routes.analyze(projectId)) { popUpTo(Routes.HOME) }
                },
                onBack = { popBackStack() }
            )
        }
        scene(Routes.ANALYZE) { backStackEntry ->
            val projectId = backStackEntry.arguments?.getString("projectId") ?: return@scene
            AnalyzeScreen(
                projectId = projectId,
                onNavigateToExport = { navigate(Routes.export(projectId)) },
                onBack = { popBackStack() }
            )
        }
        scene(Routes.EXPORT) { backStackEntry ->
            val projectId = backStackEntry.arguments?.getString("projectId") ?: return@scene
            ExportScreen(
                projectId = projectId,
                onBack = { popBackStack() }
            )
        }
    }
}
```

- [ ] **Step 3: 创建占位屏幕**

`app/src/main/java/com/palettemuse/ui/home/HomeScreen.kt`：

```kotlin
package com.palettemuse.ui.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

@Composable
fun HomeScreen(
    onNavigateToCapture: () -> Unit,
    onNavigateToAnalyze: (String) -> Unit
) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("Home - 作品画廊")
    }
}
```

`app/src/main/java/com/palettemuse/ui/capture/CaptureScreen.kt`：

```kotlin
package com.palettemuse.ui.capture

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

@Composable
fun CaptureScreen(
    onNavigateToAnalyze: (String) -> Unit,
    onBack: () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("Capture - 相机取色")
    }
}
```

`app/src/main/java/com/palettemuse/ui/analyze/AnalyzeScreen.kt`：

```kotlin
package com.palettemuse.ui.analyze

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

@Composable
fun AnalyzeScreen(
    projectId: String,
    onNavigateToExport: () -> Unit,
    onBack: () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("Analyze - 穿搭分析 ($projectId)")
    }
}
```

`app/src/main/java/com/palettemuse/ui/export/ExportScreen.kt`：

```kotlin
package com.palettemuse.ui.export

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

@Composable
fun ExportScreen(
    projectId: String,
    onBack: () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("Export - 海报导出 ($projectId)")
    }
}
```

- [ ] **Step 4: 删除旧的 Navigation.kt 和 NavigationKeys.kt（如果存在）**

```bash
rm -f app/src/main/java/com/palettemuse/Navigation.kt
rm -f app/src/main/java/com/palettemuse/NavigationKeys.kt
```

- [ ] **Step 5: 更新 string resources**

`app/src/main/res/values/strings.xml`：

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="app_name">Palette Muse</string>
    <string name="home_title">我的作品集</string>
    <string name="capture_title">色彩捕捉</string>
    <string name="analyze_title">穿搭色彩分析</string>
    <string name="export_title">海报导出</string>
    <string name="empty_gallery">拍摄你的第一个色彩灵感</string>
    <string name="share">共享</string>
    <string name="save_poster">保存海报</string>
</resources>
```

- [ ] **Step 6: 验证编译和导航**

```bash
./gradlew assembleDebug --no-daemon 2>&1 | tail -10
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 7: Commit**

```bash
git add -A
git commit -m "feat: add navigation graph with placeholder screens"
```

---

## Phase 2: 核心色彩引擎

### Task 2.1: ColorAnalyzer — 取色算法

**Files:**
- Create: `app/src/main/java/com/palettemuse/core/ColorAnalyzer.kt`

- [ ] **Step 1: 实现 ColorAnalyzer**

`app/src/main/java/com/palettemuse/core/ColorAnalyzer.kt`：

```kotlin
package com.palettemuse.core

import android.graphics.Bitmap
import androidx.palette.graphics.Palette
import com.palettemuse.data.model.ColorPaletteEntity
import com.palettemuse.data.model.ColorRole
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ColorAnalyzer @Inject constructor() {

    data class AnalysisResult(
        val primaryHex: String?,
        val secondaryHex: String?,
        val accentHex: String?,
        val primarySwatch: Palette.Swatch?,
        val secondarySwatch: Palette.Swatch?,
        val accentSwatch: Palette.Swatch?
    )

    suspend fun analyze(bitmap: Bitmap): AnalysisResult = withContext(Dispatchers.Default) {
        val palette = Palette.from(bitmap)
            .maximumColorCount(12)
            .clearFilters()
            .resizeBitmapArea(96 * 96)
            .generate()

        AnalysisResult(
            primaryHex = palette.vibrantSwatch?.rgb?.toHex(),
            secondaryHex = palette.lightVibrantSwatch?.rgb?.toHex(),
            accentHex = palette.mutedSwatch?.rgb?.toHex(),
            primarySwatch = palette.vibrantSwatch,
            secondarySwatch = palette.lightVibrantSwatch,
            accentSwatch = palette.mutedSwatch
        )
    }

    suspend fun analyzeToEntities(
        bitmap: Bitmap,
        projectId: String
    ): List<ColorPaletteEntity> = withContext(Dispatchers.Default) {
        val result = analyze(bitmap)
        listOfNotNull(
            result.primaryHex?.let { hex ->
                ColorPaletteEntity(
                    id = UUID.randomUUID().toString(),
                    projectId = projectId,
                    role = ColorRole.PRIMARY,
                    hexColor = hex,
                    semanticName = "Vibrant"
                )
            },
            result.secondaryHex?.let { hex ->
                ColorPaletteEntity(
                    id = UUID.randomUUID().toString(),
                    projectId = projectId,
                    role = ColorRole.SECONDARY,
                    hexColor = hex,
                    semanticName = "Light"
                )
            },
            result.accentHex?.let { hex ->
                ColorPaletteEntity(
                    id = UUID.randomUUID().toString(),
                    projectId = projectId,
                    role = ColorRole.ACCENT,
                    hexColor = hex,
                    semanticName = "Muted"
                )
            }
        )
    }

    private fun Int.toHex(): String {
        return "#%06X".format(this and 0xFFFFFF)
    }
}
```

- [ ] **Step 2: 验证编译**

```bash
./gradlew assembleDebug --no-daemon 2>&1 | tail -10
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add -A
git commit -m "feat: add ColorAnalyzer with Palette API color extraction"
```

---

### Task 2.2: ColorNamer — 语义命名

**Files:**
- Create: `app/src/main/java/com/palettemuse/core/ColorNamer.kt`

- [ ] **Step 1: 实现 ColorNamer**

`app/src/main/java/com/palettemuse/core/ColorNamer.kt`：

```kotlin
package com.palettemuse.core

import android.graphics.Color
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ColorNamer @Inject constructor() {

    private data class HueRange(val start: Float, val end: Float, val name: String)
    private data class MoodWord(val name: String)

    private val hueRanges = listOf(
        HueRange(0f, 15f, "Red"),
        HueRange(15f, 45f, "Orange"),
        HueRange(45f, 75f, "Yellow"),
        HueRange(75f, 165f, "Green"),
        HueRange(165f, 255f, "Blue"),
        HueRange(255f, 345f, "Purple"),
        HueRange(345f, 360f, "Red"),
    )

    private val moodWords = mapOf(
        "Red" to listOf("Dusty", "Crimson", "Warm", "Ruby", "Faded", "Deep", "Vintage"),
        "Orange" to listOf("Golden", "Warm", "Amber", "Sunset", "Terracotta", "Copper", "Sandy"),
        "Yellow" to listOf("Pale", "Warm", "Golden", "Soft", "Butter", "Honey", "Sunlit"),
        "Green" to listOf("Sage", "Moss", "Olive", "Forest", "Mint", "Emerald", "Pine"),
        "Blue" to listOf("Urban", "Steel", "Misty", "Slate", "Ocean", "Denim", "Sky"),
        "Purple" to listOf("Dusty", "Soft", "Lavender", "Mauve", "Plum", "Lilac", "Heather"),
    )

    private val baseNames = listOf(
        "Sand", "Mist", "Stone", "Clay", "Dawn", "Dusk", "Fog",
        "Shadow", "Bloom", "Petal", "Ash", "Smoke", "Cloud", "Slate"
    )

    fun nameColor(hexColor: String): String {
        val rgb = Color.parseColor(hexColor)
        val hue = FloatArray(3).also { Color.colorToHSV(rgb, it) }[0]
        val saturation = FloatArray(3).also { Color.colorToHSV(rgb, it) }[1]
        val value = FloatArray(3).also { Color.colorToHSV(rgb, it) }[2]

        val hueName = hueRanges.first { hue in it.start..it.end }.name
        val moodList = moodWords[hueName] ?: moodWords.values.flatten()
        val mood = moodList[(hue.toInt() + saturation.toInt()) % moodList.size]

        val baseName = if (value < 0.3f || saturation < 0.15f) {
            baseNames[(hue.toInt() + (saturation * 10).toInt()) % baseNames.size]
        } else {
            hueName
        }

        return "$mood $baseName"
    }
}
```

- [ ] **Step 2: 验证编译**

```bash
./gradlew assembleDebug --no-daemon 2>&1 | tail -10
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add -A
git commit -m "feat: add ColorNamer with semantic color naming"
```

---

### Task 2.3: ColorMatcher — 实时色彩匹配

**Files:**
- Create: `app/src/main/java/com/palettemuse/core/ColorMatcher.kt`

- [ ] **Step 1: 实现 ColorMatcher**

`app/src/main/java/com/palettemuse/core/ColorMatcher.kt`：

```kotlin
package com.palettemuse.core

import android.graphics.Color
import kotlin.math.abs
import kotlin.math.sqrt
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ColorMatcher @Inject constructor() {

    /**
     * 计算 CIELAB 色差 ΔE 并转为匹配百分比
     */
    fun matchPercentage(targetHex: String, sampleHex: String): Int {
        val targetRgb = Color.parseColor(targetHex)
        val sampleRgb = Color.parseColor(sampleHex)

        val targetLab = rgbToLab(targetRgb)
        val sampleLab = rgbToLab(sampleRgb)

        val deltaE = sqrt(
            (targetLab[0] - sampleLab[0]).let { it * it } +
            (targetLab[1] - sampleLab[1]).let { it * it } +
            (targetLab[2] - sampleLab[2]).let { it * it }
        )

        return (100.0 - deltaE * 2.5).toInt().coerceIn(0, 100)
    }

    /**
     * 从 Bitmap 中心区域提取平均色
     */
    fun extractCenterAverageColor(pixels: IntArray, width: Int, height: Int): String {
        val centerX = width / 2
        val centerY = height / 2
        val sampleSize = minOf(width, height) / 4

        var r = 0L
        var g = 0L
        var b = 0L
        var count = 0

        for (y in (centerY - sampleSize) until (centerY + sampleSize)) {
            for (x in (centerX - sampleSize) until (centerX + sampleSize)) {
                if (y in 0 until height && x in 0 until width) {
                    val pixel = pixels[y * width + x]
                    r += Color.red(pixel)
                    g += Color.green(pixel)
                    b += Color.blue(pixel)
                    count++
                }
            }
        }

        if (count == 0) return "#808080"

        val hex = "#%02X%02X%02X".format(
            (r / count).toInt(),
            (g / count).toInt(),
            (b / count).toInt()
        )
        return hex
    }

    private fun rgbToLab(rgb: Int): DoubleArray {
        val r = srgbLinearize(Color.red(rgb) / 255.0)
        val g = srgbLinearize(Color.green(rgb) / 255.0)
        val b = srgbLinearize(Color.blue(rgb) / 255.0)

        // D65 参考白点
        val x = 0.4124564 * r + 0.3575761 * g + 0.1804375 * b
        val y = 0.2126729 * r + 0.7151522 * g + 0.0721750 * b
        val z = 0.0193339 * r + 0.1191920 * g + 0.9503041 * b

        val xn = x / 0.95047
        val yn = y / 1.0
        val zn = z / 1.08883

        return doubleArrayOf(
            116.0 * labF(yn) - 16,
            500.0 * (labF(xn) - labF(yn)),
            200.0 * (labF(yn) - labF(zn))
        )
    }

    private fun srgbLinearize(c: Double): Double {
        return if (c <= 0.04045) c / 12.92
        else Math.pow((c + 0.055) / 1.055, 2.4)
    }

    private fun labF(t: Double): Double {
        return if (t > 0.008856) Math.pow(t, 1.0 / 3.0)
        else (903.3 * t + 16) / 116.0
    }
}
```

- [ ] **Step 2: 验证编译**

```bash
./gradlew assembleDebug --no-daemon 2>&1 | tail -10
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add -A
git commit -m "feat: add ColorMatcher with CIELAB delta-E color matching"
```

---

### Task 2.4: PosterRenderer — 海报合成

**Files:**
- Create: `app/src/main/java/com/palettemuse/core/PosterRenderer.kt`

- [ ] **Step 1: 实现 PosterRenderer**

`app/src/main/java/com/palettemuse/core/PosterRenderer.kt`：

```kotlin
package com.palettemuse.core

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PosterRenderer @Inject constructor() {

    data class PosterConfig(
        val title: String = "Moodboard Color Harmony",
        val subtitle: String = "curated with Palette Muse",
        val primaryColor: Int = Color.GRAY,
        val secondaryColor: Int = Color.LTGRAY,
        val accentColor: Int = Color.DKGRAY
    )

    fun render(
        photo: Bitmap,
        config: PosterConfig,
        width: Int = 1080,
        height: Int = 1920
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Background
        canvas.drawColor(Color.parseColor("#FDFBF7"))

        // Photo section (top 60%)
        val photoRect = RectF(40f, 40f, (width - 40).toFloat(), (height * 0.58).toFloat())
        canvas.drawBitmap(photo, null, photoRect, Paint(Paint.FILTER_BITMAP_FLAG))

        // Palette swatches bar
        val swatchHeight = 80f
        val swatchY = photoRect.bottom + 60f
        val swatchWidth = (width - 120f) / 3f

        drawSwatch(canvas, 40f, swatchY, swatchWidth, swatchHeight, config.primaryColor)
        drawSwatch(canvas, 40f + swatchWidth + 20f, swatchY, swatchWidth, swatchHeight, config.secondaryColor)
        drawSwatch(canvas, 40f + (swatchWidth + 20f) * 2, swatchY, swatchWidth, swatchHeight, config.accentColor)

        // Title
        val titlePaint = Paint().apply {
            color = Color.parseColor("#1A1A1A")
            textSize = 64f
            typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
            isAntiAlias = true
        }
        canvas.drawText(config.title, 40f, swatchY + swatchHeight + 120f, titlePaint)

        // Subtitle
        val subtitlePaint = Paint().apply {
            color = Color.parseColor("#857374")
            textSize = 32f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
            isAntiAlias = true
        }
        canvas.drawText(config.subtitle, 40f, swatchY + swatchHeight + 180f, subtitlePaint)

        return bitmap
    }

    private fun drawSwatch(canvas: Canvas, left: Float, top: Float, width: Float, height: Float, color: Int) {
        val paint = Paint().apply {
            this.color = color
            isAntiAlias = true
            setShadowLayer(12f, 0f, 4f, Color.argb(30, 0, 0, 0))
        }
        val rect = RectF(left, top, left + width, top + height)
        canvas.drawRoundRect(rect, 24f, 24f, paint)
    }

    /**
     * 保存海报到相册并返回分享 URI
     */
    fun saveToGallery(context: Context, bitmap: Bitmap): Uri? {
        val filename = "PaletteMuse_${System.currentTimeMillis()}.png"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val contentValues = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, filename)
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/PaletteMuse")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }

            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                ?: return null

            resolver.openOutputStream(uri)?.use { outputStream ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
            }

            contentValues.clear()
            contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, contentValues, null, null)

            return uri
        } else {
            // Android 9-: 写入外部存储
            val picturesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
            val paletteDir = File(picturesDir, "PaletteMuse")
            paletteDir.mkdirs()
            val file = File(paletteDir, filename)
            FileOutputStream(file).use { fos ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos)
            }
            return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        }
    }
}
```

- [ ] **Step 2: 验证编译**

```bash
./gradlew assembleDebug --no-daemon 2>&1 | tail -10
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add -A
git commit -m "feat: add PosterRenderer with Canvas poster compositing and gallery save"
```

---

## Phase 3: 相机模块

### Task 3.1: CameraManager — CameraX 封装

**Files:**
- Create: `app/src/main/java/com/palettemuse/camera/CameraManager.kt`

- [ ] **Step 1: 实现 CameraManager**

`app/src/main/java/com/palettemuse/camera/CameraManager.kt`：

```kotlin
package com.palettemuse.camera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import java.nio.ByteBuffer
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CameraManager @Inject constructor() {

    private var imageCapture: ImageCapture? = null
    private var imageAnalysis: ImageAnalysis? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private val executor: ExecutorService = Executors.newSingleThreadExecutor()

    interface FrameAnalyzer {
        fun analyze(pixels: IntArray, width: Int, height: Int)
    }

    fun startCamera(
        context: Context,
        lifecycleOwner: LifecycleOwner,
        previewView: PreviewView,
        lensFacing: Int = CameraSelector.LENS_FACING_BACK,
        frameAnalyzer: FrameAnalyzer? = null
    ) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            cameraProvider = cameraProviderFuture.get()

            val cameraSelector = CameraSelector.Builder()
                .requireLensFacing(lensFacing)
                .build()

            // Preview
            val preview = Preview.Builder()
                .build()
                .also { it.setSurfaceProvider(previewView.surfaceProvider) }

            // ImageCapture
            imageCapture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .build()

            // ImageAnalysis (用于实时帧分析)
            if (frameAnalyzer != null) {
                imageAnalysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                    .setTargetResolution(android.util.Size(640, 480))
                    .build()
                    .also { analysis ->
                        analysis.setAnalyzer(executor) { imageProxy ->
                            analyzeFrame(imageProxy, frameAnalyzer)
                        }
                    }
            }

            cameraProvider?.unbindAll()
            val useCases = mutableListOf(preview, imageCapture)
            if (imageAnalysis != null) useCases.add(imageAnalysis!!)
            cameraProvider?.bindToLifecycle(lifecycleOwner, cameraSelector, *useCases.toTypedArray())

        }, ContextCompat.getMainExecutor(context))
    }

    fun takePhoto(
        context: Context,
        onPhotoTaken: (Bitmap) -> Unit,
        onError: (Exception) -> Unit
    ) {
        val capture = imageCapture ?: return

        val outputOptions = ImageCapture.OutputFileOptions.Builder(
            context.contentResolver,
            android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            android.content.ContentValues().apply {
                put(android.provider.MediaStore.Images.Media.DISPLAY_NAME, "capture_${System.currentTimeMillis()}.jpg")
                put(android.provider.MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            }
        ).build()

        capture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    val uri = output.savedUri
                    if (uri != null) {
                        val bitmap = context.contentResolver.openInputStream(uri)?.use { input ->
                            android.graphics.BitmapFactory.decodeStream(input)
                        }
                        if (bitmap != null) {
                            onPhotoTaken(bitmap)
                        }
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    onError(exception)
                }
            }
        )
    }

    fun flipCamera(
        context: Context,
        lifecycleOwner: LifecycleOwner,
        previewView: PreviewView,
        currentFacing: Int,
        frameAnalyzer: FrameAnalyzer?
    ): Int {
        val newFacing = if (currentFacing == CameraSelector.LENS_FACING_BACK)
            CameraSelector.LENS_FACING_FRONT else CameraSelector.LENS_FACING_BACK
        startCamera(context, lifecycleOwner, previewView, newFacing, frameAnalyzer)
        return newFacing
    }

    fun cleanup() {
        cameraProvider?.unbindAll()
        executor.shutdown()
    }

    private fun analyzeFrame(imageProxy: ImageProxy, analyzer: FrameAnalyzer) {
        // 由于设置了 OUTPUT_IMAGE_FORMAT_RGBA_8888，第一平面 = [A,R,G,B,A,R,G,B,...]
        val buffer = imageProxy.planes[0].buffer ?: run {
            imageProxy.close(); return
        }
        val width = imageProxy.width
        val height = imageProxy.height
        val pixels = IntArray(width * height)
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)

        for (i in 0 until width * height) {
            val offset = i * 4
            val r = bytes[offset + 1].toInt() and 0xFF
            val g = bytes[offset + 2].toInt() and 0xFF
            val b = bytes[offset + 3].toInt() and 0xFF
            pixels[i] = android.graphics.Color.rgb(r, g, b)
        }

        analyzer.analyze(pixels, width, height)
        imageProxy.close()
    }
}
```

- [ ] **Step 2: 验证编译**

```bash
./gradlew assembleDebug --no-daemon 2>&1 | tail -10
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add -A
git commit -m "feat: add CameraManager with CameraX preview, capture, and frame analysis"
```

---

### Task 3.2: CaptureScreen UI + ViewModel

**Files:**
- Modify: `app/src/main/java/com/palettemuse/ui/capture/CaptureScreen.kt`
- Create: `app/src/main/java/com/palettemuse/ui/capture/CaptureViewModel.kt`

- [ ] **Step 1: 实现 CaptureViewModel**

`app/src/main/java/com/palettemuse/ui/capture/CaptureViewModel.kt`：

```kotlin
package com.palettemuse.ui.capture

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.camera.core.CameraSelector
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.palettemuse.core.ColorAnalyzer
import com.palettemuse.core.ColorMatcher
import com.palettemuse.core.ColorNamer
import com.palettemuse.data.model.ColorPaletteEntity
import com.palettemuse.data.model.ColorRole
import com.palettemuse.data.repository.ProjectRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import javax.inject.Inject

data class CaptureUiState(
    val matchPercentage: Int = 0,
    val targetColor: String = "#B76E79",
    val targetColorName: String = "Rose Gold",
    val capturedSwatches: List<CapturedSwatch> = emptyList(),
    val lensFacing: Int = CameraSelector.LENS_FACING_BACK,
    val isAnalyzing: Boolean = false
)

data class CapturedSwatch(
    val hexColor: String,
    val semanticName: String,
    val matchPercentage: Int
)

@HiltViewModel
class CaptureViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val projectRepository: ProjectRepository,
    private val colorAnalyzer: ColorAnalyzer,
    private val colorNamer: ColorNamer,
    private val colorMatcher: ColorMatcher
) : ViewModel() {

    private val _uiState = MutableStateFlow(CaptureUiState())
    val uiState: StateFlow<CaptureUiState> = _uiState.asStateFlow()

    // 由 CameraManager 每帧调用
    fun onFrameAnalyzed(pixels: IntArray, width: Int, height: Int) {
        val sampleHex = colorMatcher.extractCenterAverageColor(pixels, width, height)
        val match = colorMatcher.matchPercentage(_uiState.value.targetColor, sampleHex)
        _uiState.value = _uiState.value.copy(matchPercentage = match)
    }

    fun capturePhoto(photoBitmap: Bitmap, onSaved: (String) -> Unit) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isAnalyzing = true)

            // 保存图片到 App 内部存储
            val imagePath = saveImageToInternalStorage(photoBitmap)

            // 分析颜色
            val result = colorAnalyzer.analyze(photoBitmap)
            val targetMatch = colorMatcher.matchPercentage(
                _uiState.value.targetColor,
                result.primaryHex ?: "#808080"
            )

            val projectId = UUID.randomUUID().toString()
            val palettes = listOfNotNull(
                result.primaryHex?.let {
                    ColorPaletteEntity(
                        id = UUID.randomUUID().toString(),
                        projectId = projectId,
                        role = ColorRole.PRIMARY,
                        hexColor = it,
                        semanticName = colorNamer.nameColor(it),
                        matchPercentage = targetMatch
                    )
                },
                result.secondaryHex?.let {
                    ColorPaletteEntity(
                        id = UUID.randomUUID().toString(),
                        projectId = projectId,
                        role = ColorRole.SECONDARY,
                        hexColor = it,
                        semanticName = colorNamer.nameColor(it)
                    )
                },
                result.accentHex?.let {
                    ColorPaletteEntity(
                        id = UUID.randomUUID().toString(),
                        projectId = projectId,
                        role = ColorRole.ACCENT,
                        hexColor = it,
                        semanticName = colorNamer.nameColor(it)
                    )
                }
            )

            // 添加到临时色样列表
            val primaryHex = result.primaryHex ?: "#808080"
            val swatch = CapturedSwatch(
                hexColor = primaryHex,
                semanticName = colorNamer.nameColor(primaryHex),
                matchPercentage = targetMatch
            )
            _uiState.value = _uiState.value.copy(
                capturedSwatches = _uiState.value.capturedSwatches + swatch,
                isAnalyzing = false
            )

            onSaved(projectId)
        }
    }

    fun flipCamera() {
        _uiState.value = _uiState.value.copy(
            lensFacing = if (_uiState.value.lensFacing == CameraSelector.LENS_FACING_BACK)
                CameraSelector.LENS_FACING_FRONT else CameraSelector.LENS_FACING_BACK
        )
    }

    private fun saveImageToInternalStorage(bitmap: Bitmap): String {
        val file = File(context.filesDir, "captures")
        file.mkdirs()
        val imageFile = File(file, "capture_${System.currentTimeMillis()}.jpg")
        FileOutputStream(imageFile).use { fos ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, fos)
        }
        return imageFile.absolutePath
    }
}
```

- [ ] **Step 2: 实现 CaptureScreen**

`app/src/main/java/com/palettemuse/ui/capture/CaptureScreen.kt`：

```kotlin
package com.palettemuse.ui.capture

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFloatingActionButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.palettemuse.camera.CameraManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CaptureScreen(
    onNavigateToAnalyze: (String) -> Unit,
    onBack: () -> Unit,
    viewModel: CaptureViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var hasCameraPermission by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasCameraPermission = granted }

    LaunchedEffect(Unit) {
        hasCameraPermission = ContextCompat.checkSelfPermission(
            context, Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "目标: ${uiState.targetColorName}",
                        fontWeight = FontWeight.SemiBold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        },
        floatingActionButton = {
            LargeFloatingActionButton(
                onClick = { /* 拍摄 - 通过 CameraManager 触发 */ },
                shape = CircleShape
            ) {
                if (uiState.isAnalyzing) {
                    CircularProgressIndicator(color = Color.White)
                } else {
                    Text("📸", fontSize = 28.sp)
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Camera preview
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                if (hasCameraPermission) {
                    CameraPreview(
                        cameraManager = remember { CameraManager() },
                        lensFacing = uiState.lensFacing,
                        onFrameAnalyzed = { pixels, width, height ->
                            viewModel.onFrameAnalyzed(pixels, width, height)
                        }
                    )
                } else {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("需要相机权限", color = Color.Gray)
                    }
                }

                // Match percentage overlay
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 16.dp)
                        .background(
                            Color.Black.copy(alpha = 0.6f),
                            RoundedCornerShape(20.dp)
                        )
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = "${uiState.matchPercentage}% MATCH",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                }

                // Camera controls
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(16.dp)
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { /* tune */ }) {
                        Icon(Icons.Default.Tune, "微调", tint = Color.White)
                    }

                    Spacer(modifier = Modifier.width(48.dp))

                    IconButton(onClick = { viewModel.flipCamera() }) {
                        Icon(Icons.Default.Cameraswitch, "翻转", tint = Color.White)
                    }
                }
            }

            // Captured swatches
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text(
                    text = "已捕捉 (${uiState.capturedSwatches.size})",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    color = Color.Gray
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    uiState.capturedSwatches.forEach { swatch ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(CircleShape)
                                    .background(Color(android.graphics.Color.parseColor(swatch.hexColor)))
                                    .border(2.dp, Color.White, CircleShape)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "${swatch.matchPercentage}%",
                                fontSize = 11.sp,
                                color = Color.Gray
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CameraPreview(
    cameraManager: CameraManager,
    lensFacing: Int,
    onFrameAnalyzed: (IntArray, Int, Int) -> Unit
) {
    val context = LocalContext.current

    DisposableEffect(lensFacing) {
        onDispose { cameraManager.cleanup() }
    }

    AndroidView(
        factory = { ctx ->
            PreviewView(ctx).also { previewView ->
                cameraManager.startCamera(
                    context = ctx,
                    lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current,
                    previewView = previewView,
                    lensFacing = lensFacing,
                    frameAnalyzer = object : CameraManager.FrameAnalyzer {
                        override fun analyze(pixels: IntArray, width: Int, height: Int) {
                            onFrameAnalyzed(pixels, width, height)
                        }
                    }
                )
            }
        },
        modifier = Modifier.fillMaxSize()
    )
}
```

- [ ] **Step 3: 验证编译**

```bash
./gradlew assembleDebug --no-daemon 2>&1 | tail -15
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add -A
git commit -m "feat: add CaptureScreen with camera preview, real-time matching, and color capture"
```

---

## Phase 4: 数据分析页

### Task 4.1: AnalyzeScreen + ViewModel

**Files:**
- Modify: `app/src/main/java/com/palettemuse/ui/analyze/AnalyzeScreen.kt`
- Create: `app/src/main/java/com/palettemuse/ui/analyze/AnalyzeViewModel.kt`

- [ ] **Step 1: 实现 AnalyzeViewModel**

`app/src/main/java/com/palettemuse/ui/analyze/AnalyzeViewModel.kt`：

```kotlin
package com.palettemuse.ui.analyze

import android.graphics.BitmapFactory
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.palettemuse.data.model.ColorPaletteEntity
import com.palettemuse.data.model.ProjectEntity
import com.palettemuse.data.repository.ProjectRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AnalyzeUiState(
    val project: ProjectEntity? = null,
    val palettes: List<ColorPaletteEntity> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null
)

@HiltViewModel
class AnalyzeViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val projectRepository: ProjectRepository
) : ViewModel() {

    private val projectId: String = savedStateHandle.get<String>("projectId") ?: ""

    private val _uiState = MutableStateFlow(AnalyzeUiState())
    val uiState: StateFlow<AnalyzeUiState> = _uiState.asStateFlow()

    init {
        loadProject()
    }

    private fun loadProject() {
        viewModelScope.launch {
            try {
                val project = projectRepository.getProject(projectId)
                val palettes = projectRepository.getPalettesForProject(projectId)
                _uiState.value = AnalyzeUiState(
                    project = project,
                    palettes = palettes,
                    isLoading = false
                )
            } catch (e: Exception) {
                _uiState.value = AnalyzeUiState(isLoading = false, error = e.message)
            }
        }
    }
}
```

- [ ] **Step 2: 实现 AnalyzeScreen**

`app/src/main/java/com/palettemuse/ui/analyze/AnalyzeScreen.kt`：

```kotlin
package com.palettemuse.ui.analyze

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalyzeScreen(
    projectId: String,
    onNavigateToExport: () -> Unit,
    onBack: () -> Unit,
    viewModel: AnalyzeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("穿搭色彩分析") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { padding ->
        if (uiState.isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (uiState.error != null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("加载失败: ${uiState.error}", color = Color.Gray)
            }
        } else {
            val project = uiState.project ?: return@Scaffold
            val palettes = uiState.palettes

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
            ) {
                // Photo
                val bitmap = BitmapFactory.decodeFile(project.imagePath)
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "穿搭照片",
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(3f / 4f)
                            .clip(RoundedCornerShape(28.dp)),
                        contentScale = ContentScale.Crop
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Color tags
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    palettes.forEach { palette ->
                        Box(
                            modifier = Modifier
                                .background(
                                    Color(android.graphics.Color.parseColor(palette.hexColor)),
                                    RoundedCornerShape(20.dp)
                                )
                                .padding(horizontal = 16.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = palette.semanticName.uppercase(),
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Color palette section
                Text(
                    text = "Color Palette Extracted",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 18.sp
                )

                Spacer(modifier = Modifier.height(12.dp))

                palettes.forEach { palette ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(28.dp))
                                .background(Color(android.graphics.Color.parseColor(palette.hexColor)))
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = palette.role.name,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp
                            )
                            Text(
                                text = palette.semanticName,
                                color = Color.Gray,
                                fontSize = 12.sp
                            )
                        }
                        Spacer(modifier = Modifier.weight(1f))
                        Text(
                            text = palette.hexColor,
                            color = Color.Gray,
                            fontSize = 12.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // CTA
                Button(
                    onClick = onNavigateToExport,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(28.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFB76E79)
                    )
                ) {
                    Text(
                        text = "开始色彩探索 →",
                        modifier = Modifier.padding(vertical = 4.dp),
                        fontSize = 16.sp
                    )
                }
            }
        }
    }
}
```

- [ ] **Step 3: 验证编译**

```bash
./gradlew assembleDebug --no-daemon 2>&1 | tail -15
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add -A
git commit -m "feat: add AnalyzeScreen with photo view, color palette display, and semantic naming"
```

---

## Phase 5: 作品集与导出

### Task 5.1: HomeScreen + HomeViewModel

**Files:**
- Modify: `app/src/main/java/com/palettemuse/ui/home/HomeScreen.kt`
- Create: `app/src/main/java/com/palettemuse/ui/home/HomeViewModel.kt`

- [ ] **Step 1: 实现 HomeViewModel**

`app/src/main/java/com/palettemuse/ui/home/HomeViewModel.kt`：

```kotlin
package com.palettemuse.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.palettemuse.data.model.ProjectEntity
import com.palettemuse.data.repository.ProjectRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeUiState(
    val projects: List<ProjectEntity> = emptyList(),
    val isLoading: Boolean = true
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val projectRepository: ProjectRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            projectRepository.getAllProjects().collect { projects ->
                _uiState.value = HomeUiState(projects = projects, isLoading = false)
            }
        }
    }

    fun deleteProject(projectId: String) {
        viewModelScope.launch {
            projectRepository.deleteProject(projectId)
        }
    }
}
```

- [ ] **Step 2: 实现 HomeScreen**

`app/src/main/java/com/palettemuse/ui/home/HomeScreen.kt`：

```kotlin
package com.palettemuse.ui.home

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigateToCapture: () -> Unit,
    onNavigateToAnalyze: (String) -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("ChromaMuse", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = { /* menu */ }) {
                        Icon(Icons.Default.Menu, contentDescription = "菜单")
                    }
                    IconButton(onClick = { /* profile */ }) {
                        Icon(Icons.Default.Person, contentDescription = "个人")
                    }
                }
            )
        },
        floatingActionButton = {
            LargeFloatingActionButton(
                onClick = onNavigateToCapture,
                shape = RoundedCornerShape(28.dp),
                containerColor = Color(0xFFB76E79)
            ) {
                Icon(Icons.Default.Add, contentDescription = "新建", tint = Color.White)
            }
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = true,
                    onClick = { },
                    icon = { Text("🏠") },
                    label = { Text("首页") }
                )
                NavigationBarItem(
                    selected = false,
                    onClick = { onNavigateToCapture() },
                    icon = { Text("📷") },
                    label = { Text("相机") }
                )
                NavigationBarItem(
                    selected = false,
                    onClick = { },
                    icon = { Text("👤") },
                    label = { Text("个人") }
                )
            }
        }
    ) { padding ->
        if (uiState.isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("加载中...")
            }
        } else if (uiState.projects.isEmpty()) {
            // 空状态
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("🎨", fontSize = 64.sp)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "我的作品集",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 24.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "拍摄你的第一个色彩灵感",
                        color = Color.Gray,
                        fontSize = 16.sp
                    )
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(uiState.projects, key = { it.id }) { project ->
                    ProjectCard(
                        project = project,
                        onClick = { onNavigateToAnalyze(project.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun ProjectCard(
    project: com.palettemuse.data.model.ProjectEntity,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(28.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column {
            val bitmap = BitmapFactory.decodeFile(project.imagePath)
            if (bitmap != null) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = project.title,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .background(Color(0xFFF0EDED)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("🖼", fontSize = 32.sp)
                }
            }

            Text(
                text = project.title,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
```

- [ ] **Step 3: 验证编译**

```bash
./gradlew assembleDebug --no-daemon 2>&1 | tail -15
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add -A
git commit -m "feat: add HomeScreen with project gallery grid and empty state"
```

---

### Task 5.2: ExportScreen + ExportViewModel

**Files:**
- Modify: `app/src/main/java/com/palettemuse/ui/export/ExportScreen.kt`
- Create: `app/src/main/java/com/palettemuse/ui/export/ExportViewModel.kt`

- [ ] **Step 1: 实现 ExportViewModel**

`app/src/main/java/com/palettemuse/ui/export/ExportViewModel.kt`：

```kotlin
package com.palettemuse.ui.export

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.palettemuse.core.PosterRenderer
import com.palettemuse.data.repository.ProjectRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ExportUiState(
    val previewBitmap: Bitmap? = null,
    val isLoading: Boolean = true,
    val exportSuccess: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class ExportViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val projectRepository: ProjectRepository,
    private val posterRenderer: PosterRenderer
) : ViewModel() {

    private val projectId: String = savedStateHandle.get<String>("projectId") ?: ""

    private val _uiState = MutableStateFlow(ExportUiState())
    val uiState: StateFlow<ExportUiState> = _uiState.asStateFlow()

    init {
        generatePreview()
    }

    private fun generatePreview() {
        viewModelScope.launch {
            try {
                val project = projectRepository.getProject(projectId)
                val palettes = projectRepository.getPalettesForProject(projectId)

                if (project == null) {
                    _uiState.value = ExportUiState(isLoading = false, error = "项目不存在")
                    return@launch
                }

                val photo = BitmapFactory.decodeFile(project.imagePath) ?: run {
                    _uiState.value = ExportUiState(isLoading = false, error = "图片加载失败")
                    return@launch
                }

                val config = PosterRenderer.PosterConfig(
                    title = project.title.ifEmpty { "Moodboard Color Harmony" },
                    primaryColor = palettes.firstOrNull()?.hexColor?.let {
                        try { Color.parseColor(it) } catch (_: Exception) { Color.GRAY }
                    } ?: Color.GRAY,
                    secondaryColor = palettes.getOrNull(1)?.hexColor?.let {
                        try { Color.parseColor(it) } catch (_: Exception) { Color.LTGRAY }
                    } ?: Color.LTGRAY,
                    accentColor = palettes.getOrNull(2)?.hexColor?.let {
                        try { Color.parseColor(it) } catch (_: Exception) { Color.DKGRAY }
                    } ?: Color.DKGRAY
                )

                val preview = posterRenderer.render(photo, config)
                _uiState.value = ExportUiState(previewBitmap = preview, isLoading = false)
            } catch (e: Exception) {
                _uiState.value = ExportUiState(isLoading = false, error = e.message)
            }
        }
    }

    fun savePoster(context: Context) {
        viewModelScope.launch {
            val bitmap = _uiState.value.previewBitmap ?: return@launch
            val uri = posterRenderer.saveToGallery(context, bitmap)
            _uiState.value = _uiState.value.copy(exportSuccess = uri != null)
        }
    }
}
```

- [ ] **Step 2: 实现 ExportScreen**

`app/src/main/java/com/palettemuse/ui/export/ExportScreen.kt`：

```kotlin
package com.palettemuse.ui.export

import android.content.Intent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportScreen(
    projectId: String,
    onBack: () -> Unit,
    viewModel: ExportViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Moodboard 海报导出") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { padding ->
        if (uiState.isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (uiState.error != null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("生成失败: ${uiState.error}", color = Color.Gray)
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Preview
                uiState.previewBitmap?.let { bitmap ->
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "海报预览",
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(500.dp),
                        contentScale = ContentScale.Fit
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = "Moodboard Color Harmony",
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Share button
                OutlinedButton(
                    onClick = {
                        uiState.previewBitmap?.let { bitmap ->
                            val file = File(context.cacheDir, "poster_share.png")
                            file.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
                            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "image/png"
                                putExtra(Intent.EXTRA_STREAM, uri)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(Intent.createChooser(shareIntent, "共享海报"))
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(28.dp)
                ) {
                    Text("📤 共享", modifier = Modifier.padding(vertical = 4.dp), fontSize = 16.sp)
                }

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = { viewModel.savePoster(context) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(28.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB76E79))
                ) {
                    Text(
                        "💾 保存海报",
                        modifier = Modifier.padding(vertical = 4.dp),
                        fontSize = 16.sp
                    )
                }

                if (uiState.exportSuccess) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "✓ 已保存到相册",
                        color = Color(0xFF4CAF50),
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}
```

- [ ] **Step 3: 添加 FileProvider 配置**

创建 `app/src/main/res/xml/file_paths.xml`：

```xml
<?xml version="1.0" encoding="utf-8"?>
<paths>
    <cache-path name="cache" path="/" />
</paths>
```

并在 `AndroidManifest.xml` 的 `<application>` 内添加：

```xml
<provider
    android:name="androidx.core.content.FileProvider"
    android:authorities="${applicationId}.fileprovider"
    android:exported="false"
    android:grantUriPermissions="true">
    <meta-data
        android:name="android.support.FILE_PROVIDER_PATHS"
        android:resource="@xml/file_paths" />
</provider>
```

- [ ] **Step 4: 验证编译和完整导航流程**

```bash
./gradlew assembleDebug --no-daemon 2>&1 | tail -15
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "feat: add ExportScreen with poster preview, share, and save to gallery"
```

---

## Phase 6: 视觉打磨

### Task 6.1: 设计系统主题化 + Glassmorphism + 自定义阴影

**Files:**
- Modify: `app/src/main/java/com/palettemuse/theme/Theme.kt` — 完整设计系统，含 glassmorphism 和阴影
- Modify: `app/src/main/java/com/palettemuse/theme/Color.kt` — 添加玻璃质感色
- Modify: 所有 Screen — 确保使用 Material 3 语义色

- [ ] **Step 1: 强化主题（确保所有屏幕使用 MaterialTheme.colorScheme 语义色）**

审核所有 Screen 中的硬编码颜色，替换为 `MaterialTheme.colorScheme.primary`、`MaterialTheme.colorScheme.surface` 等。确保：
- TopAppBar 使用 `MaterialTheme.colorScheme.surface`
- FAB 使用 `MaterialTheme.colorScheme.primary`
- 卡片使用 `MaterialTheme.colorScheme.surface` + `elevation`
- 背景使用 `MaterialTheme.colorScheme.background`

- [ ] **Step 2: 实现 Glassmorphism 浮层效果（对应 Stitch Floating Layer）**

在 `Theme.kt` 中添加 glassmorphism 辅助函数：

```kotlin
// Glassmorphism: 20px backdrop-blur + 60% white fill + 0.5px white border
val GlassBackground = Brush.verticalGradient(
    colors = listOf(Color.White.copy(alpha = 0.6f), Color.White.copy(alpha = 0.4f))
)

// 用于 TopAppBar / BottomBar / FAB 的 glassmorphic 背景
// Android 12+ 可使用 RenderEffect.createBlurEffect() 实现真实模糊
// 降级方案: 半透明白色 + 微妙渐变
```

应用到 TopAppBar、BottomBar、FAB 的 background 中，用 `Modifier.background(GlassBackground, shape)` 替代纯色背景。

- [ ] **Step 3: 实现自定义软阴影（对应 Stitch Shadow 规范）**

设计要求: 主色/中性色 5-8% 透明度 + blur 20-30px + 长偏移，"禁止黑色阴影"。

```kotlin
// 在 Theme.kt 中添加自定义阴影颜色
val AmbientShadowColor = Color(0xFFB76E79).copy(alpha = 0.06f)  // 主色 6% 透明度
val SpotShadowColor = Color(0xFFB76E79).copy(alpha = 0.08f)     // 主色 8% 透明度

// Card 使用:
Card(
    elevation = CardDefaults.cardElevation(
        defaultElevation = 4.dp,
        ambientShadowColor = AmbientShadowColor,
        spotShadowColor = SpotShadowColor
    )
)
```

- [ ] **Step 4: 使用 Dimens 全局圆角常量**

将所有 Screen 中的 `RoundedCornerShape(28.dp)` 硬编码替换为 `Dimens.pillShape` / `Dimens.cardCorner` 等常量引用，确保一致性。

- [ ] **Step 5: 添加边缘到边缘（Edge-to-Edge）适配**

确保 `MainActivity` 已有 `enableEdgeToEdge()`，且所有 Scaffold 正确使用 `contentWindowInsets` 参数（Navigation 3 自动处理）。

- [ ] **Step 6: 添加转场动画**

在 `NavGraph.kt` 中添加场景过渡动画：

```kotlin
ComposableScene(
    transitionIn = { it.slideInHorizontally { it } + it.fadeIn() },
    transitionOut = { it.slideOutHorizontally { -it / 3 } + it.fadeOut() }
) { }
```

- [ ] **Step 7: 验证编译**

```bash
./gradlew assembleDebug --no-daemon 2>&1 | tail -10
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 8: Commit**

```bash
git add -A
git commit -m "style: apply Stitch design system theming, glassmorphism, soft shadows, and Dimens constants"
```

---

## Phase 1 — 完整文件清单（汇总）

创建的项目文件：

```
app/src/main/java/com/palettemuse/
 ├── PaletteMuseApp.kt
 ├── MainActivity.kt
 ├── di/
 │   └── AppModule.kt
 ├── data/
 │   ├── model/
 │   │   ├── ProjectEntity.kt
 │   │   └── ColorPaletteEntity.kt
 │   ├── local/
 │   │   ├── AppDatabase.kt
 │   │   ├── Converters.kt
 │   │   ├── ProjectDao.kt
 │   │   └── ColorPaletteDao.kt
 │   └── repository/
 │       └── ProjectRepository.kt
 ├── ui/
 │   ├── navigation/
 │   │   └── NavGraph.kt
 │   ├── home/
 │   │   └── HomeScreen.kt (占位)
 │   ├── capture/
 │   │   └── CaptureScreen.kt (占位)
 │   ├── analyze/
 │   │   └── AnalyzeScreen.kt (占位)
 │   ├── export/
 │   │   └── ExportScreen.kt (占位)
 │   └── theme/
 │       ├── Color.kt
 │       ├── Theme.kt
 │       └── Type.kt
 ├── camera/
 │   └── CameraManager.kt (占位/空)
 └── core/
     ├── ColorAnalyzer.kt (占位/空)
     ├── ColorNamer.kt (占位/空)
     ├── ColorMatcher.kt (占位/空)
     └── PosterRenderer.kt (占位/空)
```

修改的模板文件：
- `gradle/libs.versions.toml` — 添加 Hilt/Room/CameraX/Palette/Coil 依赖
- `app/build.gradle.kts` — 应用插件、添加依赖、更新 minSdk/package
- `settings.gradle.kts` — 注册新插件
- `app/src/main/AndroidManifest.xml` — 权限、Application class、FileProvider
- `app/src/main/res/values/strings.xml` — 字符串资源
- `app/src/main/res/values/themes.xml` — App 主题
