package com.naturasonic.app.di

import android.content.Context
import androidx.room.Room
import androidx.work.WorkManager
import com.naturasonic.app.data.local.AppDatabase
import com.naturasonic.app.data.local.dao.AlertEventDao
import com.naturasonic.app.data.local.dao.AudioProfileDao
import com.naturasonic.app.data.local.dao.AudiogramDao
import com.naturasonic.app.data.local.dao.TranscriptionDao
import com.naturasonic.app.data.local.dao.VoiceMetricsDao
import com.naturasonic.app.sync.CloudSyncApi
import com.naturasonic.app.sync.StubCloudSyncApi
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
        )
            .addMigrations(AppDatabase.MIGRATION_1_2, AppDatabase.MIGRATION_2_3, AppDatabase.MIGRATION_3_4)
            .build()

    @Provides
    fun provideAudioProfileDao(db: AppDatabase): AudioProfileDao = db.audioProfileDao()

    @Provides
    fun provideTranscriptionDao(db: AppDatabase): TranscriptionDao = db.transcriptionDao()

    @Provides
    fun provideAlertEventDao(db: AppDatabase): AlertEventDao = db.alertEventDao()

    @Provides
    fun provideAudiogramDao(db: AppDatabase): AudiogramDao = db.audiogramDao()

    @Provides
    fun provideVoiceMetricsDao(db: AppDatabase): VoiceMetricsDao = db.voiceMetricsDao()

    @Provides
    @Singleton
    fun provideCloudSyncApi(stub: StubCloudSyncApi): CloudSyncApi = stub

    @Provides
    @Singleton
    fun provideWorkManager(@ApplicationContext context: Context): WorkManager =
        WorkManager.getInstance(context)
}
