package com.palettemuse.di

import android.content.Context
import androidx.room.Room
import com.palettemuse.core.ColorAnalyzer
import com.palettemuse.core.ColorMatcher
import com.palettemuse.core.ColorNamer
import com.palettemuse.core.PosterRenderer
import com.palettemuse.data.local.AppDatabase
import com.palettemuse.data.local.ColorPaletteDao
import com.palettemuse.data.local.PhotoDao
import com.palettemuse.data.local.ProjectDao
import com.palettemuse.data.local.ThemeDao
import com.palettemuse.data.repository.ProjectRepository
import com.palettemuse.data.repository.ThemeRepository
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
        )
            .fallbackToDestructiveMigration()
            .build()
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

    @Provides
    fun provideThemeDao(database: AppDatabase): ThemeDao = database.themeDao()

    @Provides
    fun providePhotoDao(database: AppDatabase): PhotoDao = database.photoDao()

    @Provides
    @Singleton
    fun provideThemeRepository(
        themeDao: ThemeDao,
        photoDao: PhotoDao,
        colorMatcher: ColorMatcher,
        colorNamer: ColorNamer
    ): ThemeRepository = ThemeRepository(themeDao, photoDao, colorMatcher, colorNamer)
}
