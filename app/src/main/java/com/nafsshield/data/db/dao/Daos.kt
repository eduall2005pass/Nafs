package com.nafsshield.data.db.dao

import androidx.lifecycle.LiveData
import androidx.room.*
import com.nafsshield.data.model.*

@Dao
interface BlockedAppDao {
    @Query("SELECT packageName FROM blocked_apps")
    suspend fun getBlockedPackages(): List<String>

    @Query("SELECT * FROM blocked_apps WHERE isActive = 1 ORDER BY appName ASC")
    fun getAllActive(): LiveData<List<BlockedApp>>

    @Query("SELECT packageName FROM blocked_apps WHERE isActive = 1")
    suspend fun getAllActivePackageNames(): List<String>

    @Query("SELECT COUNT(*) > 0 FROM blocked_apps WHERE packageName = :pkg AND isActive = 1")
    suspend fun isBlocked(pkg: String): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(app: BlockedApp)

    @Delete
    suspend fun delete(app: BlockedApp)
}

@Dao
interface KeywordDao {
    // সব keyword দেখাও (active + inactive) — UI toggle এর জন্য
    @Query("SELECT * FROM keywords ORDER BY word ASC")
    fun getAll(): LiveData<List<Keyword>>

    // Service cache এর জন্য শুধু active words
    @Query("SELECT word FROM keywords WHERE isActive = 1")
    suspend fun getAllActiveWords(): List<String>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(keyword: Keyword)

    @Delete
    suspend fun delete(keyword: Keyword)

    // Toggle: isActive update করা
    @Query("UPDATE keywords SET isActive = :active WHERE id = :id")
    suspend fun setActive(id: Int, active: Boolean)
}

@Dao
interface AllowedBrowserDao {
    @Query("SELECT * FROM allowed_browsers ORDER BY browserName ASC")
    fun getAll(): LiveData<List<AllowedBrowser>>

    @Query("SELECT packageName FROM allowed_browsers")
    suspend fun getAllPackageNames(): List<String>

    @Query("SELECT COUNT(*) > 0 FROM allowed_browsers WHERE packageName = :pkg")
    suspend fun isAllowed(pkg: String): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(browser: AllowedBrowser)

    @Delete
    suspend fun delete(browser: AllowedBrowser)
}

@Dao
interface BlockLogDao {
    @Query("SELECT * FROM block_logs WHERE date(timestamp/1000,'unixepoch') >= :fromDate ORDER BY timestamp DESC")
    suspend fun getLogsAfterDate(fromDate: String): List<com.nafsshield.data.model.BlockLog>

    @Query("SELECT * FROM block_logs ORDER BY timestamp DESC LIMIT 50")
    fun getRecent(): LiveData<List<BlockLog>>

    @Query("SELECT COUNT(*) FROM block_logs WHERE timestamp > :since")
    suspend fun countSince(since: Long): Int

    @Insert
    suspend fun insert(log: BlockLog)

    @Query("DELETE FROM block_logs WHERE timestamp < :before")
    suspend fun deleteOlderThan(before: Long)
}

@Dao
interface ScheduleDao {
    @Query("SELECT * FROM schedules ORDER BY name ASC")
    fun getAll(): LiveData<List<Schedule>>
    
    @Query("SELECT * FROM schedules WHERE isActive = 1")
    suspend fun getAllActive(): List<Schedule>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(schedule: Schedule)
    
    @Update
    suspend fun update(schedule: Schedule)
    
    @Delete
    suspend fun delete(schedule: Schedule)
    
    @Query("UPDATE schedules SET isActive = :active WHERE id = :id")
    suspend fun setActive(id: Int, active: Boolean)
}
