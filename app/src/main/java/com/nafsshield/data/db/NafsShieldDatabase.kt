package com.nafsshield.data.db

import android.content.Context
import androidx.room.*
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.room.migration.Migration
import com.nafsshield.data.db.dao.*
import com.nafsshield.data.model.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [BlockedApp::class, Keyword::class, AllowedBrowser::class, BlockLog::class, Schedule::class],
    version = 2,
    exportSchema = false
)
abstract class NafsShieldDatabase : RoomDatabase() {

    abstract fun blockedAppDao(): BlockedAppDao
    abstract fun keywordDao(): KeywordDao
    abstract fun allowedBrowserDao(): AllowedBrowserDao
    abstract fun blockLogDao(): BlockLogDao
    abstract fun scheduleDao(): ScheduleDao

    companion object {
        @Volatile private var INSTANCE: NafsShieldDatabase? = null
        
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS schedules (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        name TEXT NOT NULL,
                        startHour INTEGER NOT NULL,
                        startMinute INTEGER NOT NULL,
                        endHour INTEGER NOT NULL,
                        endMinute INTEGER NOT NULL,
                        daysOfWeek TEXT NOT NULL,
                        isActive INTEGER NOT NULL DEFAULT 1,
                        createdAt INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }

        fun getInstance(context: Context): NafsShieldDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    NafsShieldDatabase::class.java,
                    "nafsshield.db"
                )
                .addMigrations(MIGRATION_1_2)
                .addCallback(object : Callback() {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        super.onCreate(db)
                        INSTANCE?.let { database ->
                            CoroutineScope(Dispatchers.IO).launch {
                                // Default: Firefox + Brave allowed
                                database.allowedBrowserDao().insert(
                                    AllowedBrowser("org.mozilla.firefox", "Firefox")
                                )
                                database.allowedBrowserDao().insert(
                                    AllowedBrowser("com.brave.browser", "Brave")
                                )
                            }
                        }
                    }
                })
                .build()
                .also { INSTANCE = it }
            }
    }
}
