package com.nafsshield.data.repository

import android.content.Context
import com.nafsshield.data.db.NafsShieldDatabase
import com.nafsshield.data.model.*

class NafsRepository private constructor(context: Context) {

    private val db          = NafsShieldDatabase.getInstance(context)
    private val appDao      = db.blockedAppDao()
    private val keywordDao  = db.keywordDao()
    private val browserDao  = db.allowedBrowserDao()
    private val logDao      = db.blockLogDao()
    private val scheduleDao = db.scheduleDao()

    // Blocked Apps
    val allBlockedApps = appDao.getAllActive()
    suspend fun blockApp(app: BlockedApp)   = appDao.insert(app)
    suspend fun unblockApp(app: BlockedApp) = appDao.delete(app)
    suspend fun isAppBlocked(pkg: String)   = appDao.isBlocked(pkg)
    suspend fun getBlockedPackages()        = appDao.getAllActivePackageNames()

    // Keywords — getAll() so UI can toggle active/inactive
    val allKeywords = keywordDao.getAll()
    suspend fun addKeyword(keyword: Keyword)            = keywordDao.insert(keyword)
    suspend fun removeKeyword(keyword: Keyword)         = keywordDao.delete(keyword)
    suspend fun toggleKeyword(id: Int, active: Boolean) = keywordDao.setActive(id, active)
    suspend fun getActiveKeywords()                     = keywordDao.getAllActiveWords()

    // Allowed Browsers
    val allAllowedBrowsers = browserDao.getAll()
    suspend fun allowBrowser(b: AllowedBrowser)  = browserDao.insert(b)
    suspend fun removeBrowser(b: AllowedBrowser) = browserDao.delete(b)
    suspend fun isBrowserAllowed(pkg: String)    = browserDao.isAllowed(pkg)
    suspend fun getAllowedBrowserPackages()       = browserDao.getAllPackageNames()
    
    // Schedules
    val allSchedules = scheduleDao.getAll()
    suspend fun addSchedule(schedule: Schedule)           = scheduleDao.insert(schedule)
    suspend fun deleteSchedule(schedule: Schedule)        = scheduleDao.delete(schedule)
    suspend fun toggleSchedule(id: Int, active: Boolean)  = scheduleDao.setActive(id, active)
    suspend fun getActiveSchedules()                      = scheduleDao.getAllActive()

    // Block Logs
    val recentLogs = logDao.getRecent()
    suspend fun logBlock(log: BlockLog) = logDao.insert(log)

    suspend fun todayBlockCount(): Int {
        val midnight = System.currentTimeMillis() - (System.currentTimeMillis() % 86_400_000L)
        return logDao.countSince(midnight)
    }

    suspend fun cleanOldLogs() {
        val oneWeekAgo = System.currentTimeMillis() - (7L * 24 * 60 * 60 * 1000)
        logDao.deleteOlderThan(oneWeekAgo)
    }

    companion object {
        @Volatile private var INSTANCE: NafsRepository? = null
        fun getInstance(context: Context): NafsRepository =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: NafsRepository(context).also { INSTANCE = it }
            }
    }
}
