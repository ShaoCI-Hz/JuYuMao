package com.hezi.juyumao.di

import android.content.Context
import com.hezi.juyumao.data.local.db.JuYuMaoDatabase
import com.hezi.juyumao.data.local.db.dao.PlaylistDao
import com.hezi.juyumao.data.local.db.dao.ServerDao
import com.hezi.juyumao.data.local.db.dao.SmartPlaylistDao
import com.hezi.juyumao.data.local.db.dao.SongDao
import com.hezi.juyumao.data.local.datastore.SettingsDataStore
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
    fun provideDatabase(@ApplicationContext context: Context): JuYuMaoDatabase {
        return JuYuMaoDatabase.create(context)
    }

    @Provides
    @Singleton
    fun provideSongDao(database: JuYuMaoDatabase): SongDao {
        return database.songDao()
    }

    @Provides
    @Singleton
    fun provideServerDao(database: JuYuMaoDatabase): ServerDao {
        return database.serverDao()
    }

    @Provides
    @Singleton
    fun providePlaylistDao(database: JuYuMaoDatabase): PlaylistDao {
        return database.playlistDao()
    }

    @Provides
    @Singleton
    fun provideSmartPlaylistDao(database: JuYuMaoDatabase): SmartPlaylistDao {
        return database.smartPlaylistDao()
    }

    @Provides
    @Singleton
    fun provideSettingsDataStore(@ApplicationContext context: Context): SettingsDataStore {
        return SettingsDataStore(context)
    }
}
