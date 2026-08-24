package com.naturasonic.app.di

import android.content.Context
import androidx.room.Room
import androidx.work.WorkManager
import com.naturasonic.app.data.local.AppDatabase
import com.naturasonic.app.data.local.dao.AlertEventDao
import com.naturasonic.app.data.local.dao.AudioProfileDao
import com.naturasonic.app.data.local.dao.AudiogramDao
import com.naturasonic.app.data.local.dao.TranscriptionDao
import com.naturasonic.app.data.local.dao.DosimetrySampleDao
import com.naturasonic.app.data.local.dao.VoiceMetricsDao
import com.naturasonic.app.security.DatabaseEncryptionMigrator
import com.naturasonic.app.security.KeyStoreManager
import com.naturasonic.app.sync.CloudSyncApi
import com.naturasonic.app.sync.StubCloudSyncApi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context,
        keyStoreManager: KeyStoreManager
    ): AppDatabase {
        System.loadLibrary("sqlcipher")

        val passphrase = keyStoreManager.getOrCreateDatabasePassphrase()

        if (!DatabaseEncryptionMigrator.canDecryptDatabase(context, passphrase)) {
            DatabaseEncryptionMigrator.handleUndecryptableDatabase(context)
        } else {
            DatabaseEncryptionMigrator.migrateIfNeeded(context, passphrase)
        }

        val factory = SupportOpenHelperFactory(passphrase)

        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "naturasonic.db"
        )
            .openHelperFactory(factory)
            .addMigrations(
                AppDatabase.MIGRATION_1_2,
                AppDatabase.MIGRATION_2_3,
                AppDatabase.MIGRATION_3_4,
                AppDatabase.MIGRATION_4_5
            )
            .build()
    }

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
    fun provideDosimetrySampleDao(db: AppDatabase): DosimetrySampleDao = db.dosimetrySampleDao()

    @Provides
    @Singleton
    fun provideCloudSyncApi(stub: StubCloudSyncApi): CloudSyncApi = stub

    @Provides
    @Singleton
    fun provideWorkManager(@ApplicationContext context: Context): WorkManager =
        WorkManager.getInstance(context)
}
