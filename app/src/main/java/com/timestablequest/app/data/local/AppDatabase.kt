package com.timestablequest.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration

@Database(
    entities = [
        CalculationEntity::class,
        PracticeSessionEntity::class,
        QuestionEntity::class,
        FactProgressEntity::class,
        ReviewAttemptEntity::class,
    ],
    version = AppDatabase.VERSION,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun calculationDao(): CalculationDao
    abstract fun practiceDao(): PracticeDao

    companion object {
        const val VERSION = 1
        private const val NAME = "timestablequest.db"

        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, NAME)
                .addMigrations(*Migrations.ALL)
                // No destructive fallback: a missing migration must fail loudly during testing
                // instead of silently erasing a child's progress.
                .build()
    }
}

/**
 * Schema history is exported by Room into app/schemas/<db class>/<version>.json and committed.
 * Version 1 is the first released schema, so there are no migrations yet. Every future version bump
 * must add an explicit [Migration] here (or a Room AutoMigration) and commit the new schema JSON.
 */
object Migrations {
    val ALL: Array<Migration> = emptyArray()
}
