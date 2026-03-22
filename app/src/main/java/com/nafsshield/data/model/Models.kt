package com.nafsshield.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "blocked_apps")
data class BlockedApp(
    @PrimaryKey val packageName: String,
    val appName: String,
    val blockedAt: Long = System.currentTimeMillis(),
    val isActive: Boolean = true
)

@Entity(tableName = "keywords")
data class Keyword(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val word: String,
    val addedAt: Long = System.currentTimeMillis(),
    val isActive: Boolean = true,
    val isCaseSensitive: Boolean = false
)

@Entity(tableName = "allowed_browsers")
data class AllowedBrowser(
    @PrimaryKey val packageName: String,
    val browserName: String
)

@Entity(tableName = "schedules")
data class Schedule(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val startHour: Int,     // 0-23
    val startMinute: Int,   // 0-59
    val endHour: Int,       // 0-23
    val endMinute: Int,     // 0-59
    val daysOfWeek: String, // Comma-separated: "0,1,2,3,4,5,6" (Sun-Sat)
    val isActive: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "block_logs")
data class BlockLog(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val blockedPackage: String,
    val reason: BlockReason,
    val triggeredKeyword: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)

enum class BlockReason {
    APP_BLOCKED, BROWSER_NOT_ALLOWED, KEYWORD_FOUND, DNS_BLOCKED
}
