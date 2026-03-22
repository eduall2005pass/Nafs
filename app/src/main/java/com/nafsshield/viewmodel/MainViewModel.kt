package com.nafsshield.viewmodel

import android.app.Application
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.nafsshield.data.model.*
import com.nafsshield.data.repository.NafsRepository
import com.nafsshield.service.MasterService
import com.nafsshield.service.NafsVpnService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val repository = NafsRepository.getInstance(app)

    val blockedApps     = repository.allBlockedApps
    val keywords        = repository.allKeywords
    val allowedBrowsers = repository.allAllowedBrowsers
    val recentLogs      = repository.recentLogs
    val schedules       = repository.allSchedules

    private val _todayCount = MutableLiveData(0)
    val todayCount: LiveData<Int> = _todayCount

    private val _installedApps = MutableLiveData<List<AppInfo>>(emptyList())
    val installedApps: LiveData<List<AppInfo>> = _installedApps

    val isGuardRunning get() = MasterService.isRunning
    val isVpnRunning   get() = NafsVpnService.isRunning

    init {
        loadStats()
        loadInstalledApps()
    }

    // Blocked Apps
    fun blockApp(packageName: String, appName: String) =
        viewModelScope.launch(Dispatchers.IO) {
            repository.blockApp(BlockedApp(packageName, appName))
        }

    fun unblockApp(app: BlockedApp) =
        viewModelScope.launch(Dispatchers.IO) { repository.unblockApp(app) }

    // Keywords
    fun addKeyword(word: String) {
        if (word.isBlank()) return
        viewModelScope.launch(Dispatchers.IO) {
            repository.addKeyword(Keyword(word = word.trim()))
        }
    }

    fun removeKeyword(keyword: Keyword) =
        viewModelScope.launch(Dispatchers.IO) { repository.removeKeyword(keyword) }

    fun toggleKeyword(keyword: Keyword, isActive: Boolean) =
        viewModelScope.launch(Dispatchers.IO) {
            repository.toggleKeyword(keyword.id, isActive)
        }

    // Browsers
    fun allowBrowser(packageName: String, name: String) =
        viewModelScope.launch(Dispatchers.IO) {
            repository.allowBrowser(AllowedBrowser(packageName, name))
        }

    fun removeBrowser(browser: AllowedBrowser) =
        viewModelScope.launch(Dispatchers.IO) { repository.removeBrowser(browser) }
    
    // Schedules
    fun addSchedule(schedule: Schedule) =
        viewModelScope.launch(Dispatchers.IO) {
            repository.addSchedule(schedule)
        }
    
    fun deleteSchedule(schedule: Schedule) =
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteSchedule(schedule)
        }
    
    fun toggleSchedule(id: Int, isActive: Boolean) =
        viewModelScope.launch(Dispatchers.IO) {
            repository.toggleSchedule(id, isActive)
        }

    // Stats
    fun loadStats() = viewModelScope.launch(Dispatchers.IO) {
        _todayCount.postValue(repository.todayBlockCount())
    }

    // Installed apps for picker
    fun loadInstalledApps() = viewModelScope.launch(Dispatchers.IO) {
        val pm = getApplication<Application>().packageManager
        val myPackage = getApplication<Application>().packageName
        val blocked = repository.allBlockedApps.value?.map { it.packageName }?.toSet() ?: emptySet()
        
        val list = pm.getInstalledApplications(PackageManager.GET_META_DATA)
            .filter { 
                // Filter out system apps
                it.flags and ApplicationInfo.FLAG_SYSTEM == 0 &&
                // Filter out NafsShield itself
                it.packageName != myPackage &&
                // Filter out already blocked apps
                it.packageName !in blocked
            }
            .map { AppInfo(it.packageName, pm.getApplicationLabel(it).toString()) }
            .sortedBy { it.appName }
        _installedApps.postValue(list)
    }
}

data class AppInfo(val packageName: String, val appName: String)
