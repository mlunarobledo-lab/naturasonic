package com.naturasonic.app.di

import android.content.Context
import androidx.room.Room
import com.naturasonic.app.data.local.AppDatabase
import com.naturasonic.app.data.local.dao.AlertEventDao
import com.naturasonic.app.data.local.dao.AudioProfileDao
import com.naturasonic.app.data.local.dao.TranscriptionDao
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
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "naturasonic.db"
        ).build()

    @Provides
    fun provideAudioProfileDao(db: AppDatabase): AudioProfileDao = db.audioProfileDao()

    @Provides
    fun provideTranscriptionDao(db: AppDatabase): TranscriptionDao = db.transcriptionDao()

    @Provides
    fun provideAlertEventDao(db: AppDatabase): AlertEventDao = db.alertEventDao()
}
