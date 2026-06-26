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
