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

