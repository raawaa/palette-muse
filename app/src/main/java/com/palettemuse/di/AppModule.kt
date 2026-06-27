package com.palettemuse.di

import android.content.Context
import androidx.room.Room
import com.palettemuse.core.ColorAnalyzer
import com.palettemuse.core.ColorMatcher
import com.palettemuse.core.ColorNamer
import com.palettemuse.core.PosterRenderer
import com.palettemuse.data.local.AppDatabase
import com.palettemuse.data.local.PhotoDao
import com.palettemuse.data.local.ThemeDao
import com.palettemuse.data.repository.InternalPhotoStorage
import com.palettemuse.data.repository.PhotoStorage
import dagger.Binds
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
            .addMigrations(AppDatabase.MIGRATION_2_3)
            .build()
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
}

@Module
@InstallIn(SingletonComponent::class)
abstract class PhotoStorageModule {
    @Binds
    @Singleton
    abstract fun bindPhotoStorage(impl: InternalPhotoStorage): PhotoStorage
}
