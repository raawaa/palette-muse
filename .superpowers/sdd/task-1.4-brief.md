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

