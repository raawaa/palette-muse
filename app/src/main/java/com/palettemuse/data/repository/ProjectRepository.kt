package com.palettemuse.data.repository

import com.palettemuse.data.local.ColorPaletteDao
import com.palettemuse.data.local.ProjectDao
import javax.inject.Inject

class ProjectRepository @Inject constructor(
    private val projectDao: ProjectDao,
    private val colorPaletteDao: ColorPaletteDao
)
