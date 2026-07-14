package com.example.data

import android.content.Context
import com.example.data.database.AppDatabase
import com.example.data.database.AppSetting
import com.example.data.database.CastHistoryItem
import com.example.data.dlna.DlnaControlClient
import com.example.data.dlna.DlnaDevice
import com.example.data.dlna.DlnaScanner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.InetAddress

class MediaRepository(private val context: Context) {
    private val TAG = "MediaRepository"

    private val database = AppDatabase.getDatabase(context)
    private val historyDao = database.castHistoryDao()
    private val settingDao = database.appSettingDao()

    private val scanner = DlnaScanner(context)

    // History and settings from Room
    val allHistory: Flow<List<CastHistoryItem>> = historyDao.getAllHistory()
    
    val savedTvIpFlow: Flow<String?> = settingDao.getSettingFlow(KEY_TV_IP)
        .map { it?.value }
        .distinctUntilChanged()

    val savedTvFriendlyNameFlow: Flow<String?> = settingDao.getSettingFlow(KEY_TV_FRIENDLY_NAME)
        .map { it?.value }
        .distinctUntilChanged()

    // SSDP Discovery states
    val devices: StateFlow<List<DlnaDevice>> = scanner.devices
    val isScanning: StateFlow<Boolean> = scanner.isScanning

    // Connection states
    private val _selectedDevice = MutableStateFlow<DlnaDevice?>(null)
    val selectedDevice: StateFlow<DlnaDevice?> = _selectedDevice.asStateFlow()

    private val _isTvConnected = MutableStateFlow(false)
    val isTvConnected: StateFlow<Boolean> = _isTvConnected.asStateFlow()

    // Remote playback states
    private val _activeCastUrl = MutableStateFlow<String?>(null)
    val activeCastUrl: StateFlow<String?> = _activeCastUrl.asStateFlow()

    private val _playbackPosition = MutableStateFlow<DlnaControlClient.PlaybackPosition?>(null)
    val playbackPosition: StateFlow<DlnaControlClient.PlaybackPosition?> = _playbackPosition.asStateFlow()

    private val _remoteVolume = MutableStateFlow(80)
    val remoteVolume: StateFlow<Int> = _remoteVolume.asStateFlow()

    private var pollingJob: Job? = null
    private var pingJob: Job? = null
    private val repositoryScope = CoroutineScope(Dispatchers.IO)

    init {
        // Automatically start checking the connection for the saved IP address
        startPingJob()
        
        // Listen to changes in saved IP to trigger scanner search or update connection status
        repositoryScope.launch {
            savedTvIpFlow.collect { ip ->
                if (ip != null) {
                    LogManager.i(TAG, "Saved IP loaded: $ip. Initializing target ping.")
                    checkAndReconnectTv(ip)
                } else {
                    _isTvConnected.value = false
                    _selectedDevice.value = null
                }
            }
        }
    }

    fun startScanning() {
        scanner.startScan()
    }

    suspend fun saveTvIp(ip: String) {
        withContext(Dispatchers.IO) {
            LogManager.i(TAG, "Saving TV IP in Room: $ip")
            settingDao.insertSetting(AppSetting(KEY_TV_IP, ip))
            _isTvConnected.value = false
            checkAndReconnectTv(ip)
        }
    }

    suspend fun saveTvFriendlyName(name: String) {
        withContext(Dispatchers.IO) {
            settingDao.insertSetting(AppSetting(KEY_TV_FRIENDLY_NAME, name))
        }
    }

    private fun startPingJob() {
        pingJob?.cancel()
        pingJob = repositoryScope.launch {
            while (true) {
                delay(10000) // Check connection status every 10 seconds
                val ip = savedTvIpFlow.firstOrNull()
                if (ip != null) {
                    checkAndReconnectTv(ip)
                }
            }
        }
    }

    private suspend fun checkAndReconnectTv(ip: String) {
        withContext(Dispatchers.IO) {
            try {
                // Perform quick ping
                val address = InetAddress.getByName(ip)
                val reachable = address.isReachable(2000)
                
                // If reachable, update connected state and search if the device details exist in found list
                if (reachable) {
                    _isTvConnected.value = true
                    LogManager.i(TAG, "Ping TV at $ip: SUCCESS")
                    
                    val currentList = devices.value
                    val matchingDevice = currentList.find { it.ip == ip }
                    if (matchingDevice != null) {
                        if (_selectedDevice.value?.id != matchingDevice.id) {
                            _selectedDevice.value = matchingDevice
                            saveTvFriendlyName(matchingDevice.friendlyName)
                            LogManager.i(TAG, "TV device details resolved dynamically: ${matchingDevice.friendlyName}")
                        }
                    } else {
                        // If not in scanner list, we construct a generic representation using default paths
                        if (_selectedDevice.value == null || _selectedDevice.value?.ip != ip) {
                            val savedFriendly = settingDao.getSettingValue(KEY_TV_FRIENDLY_NAME) ?: "Телевизор ($ip)"
                            val genericDevice = DlnaDevice(
                                id = "generic:$ip",
                                ip = ip,
                                port = 8080,
                                friendlyName = savedFriendly,
                                location = "http://$ip:8080/description.xml",
                                avTransportControlUrl = "http://$ip:8080/upnp/control/AVTransport1",
                                renderingControlUrl = "http://$ip:8080/upnp/control/RenderingControl1"
                            )
                            _selectedDevice.value = genericDevice
                        }
                    }
                } else {
                    if (_isTvConnected.value) {
                        _isTvConnected.value = false
                        LogManager.e(TAG, "Ping TV at $ip: FAILED. Device became unreachable.")
                    }
                }
            } catch (e: Exception) {
                _isTvConnected.value = false
                LogManager.e(TAG, "Error checking TV connection for $ip", e)
            }
        }
    }

    fun selectDevice(device: DlnaDevice?) {
        _selectedDevice.value = device
        if (device != null) {
            repositoryScope.launch {
                saveTvIp(device.ip)
                saveTvFriendlyName(device.friendlyName)
            }
            _isTvConnected.value = true
            LogManager.i(TAG, "Manually selected device: ${device.friendlyName} (${device.ip})")
        } else {
            repositoryScope.launch {
                settingDao.insertSetting(AppSetting(KEY_TV_IP, ""))
                settingDao.insertSetting(AppSetting(KEY_TV_FRIENDLY_NAME, ""))
            }
            _isTvConnected.value = false
            _activeCastUrl.value = null
            stopPositionPolling()
            LogManager.i(TAG, "Device deselected")
        }
    }

    // Database History Actions
    suspend fun addHistoryItem(url: String, customName: String = "") {
        withContext(Dispatchers.IO) {
            val name = customName.ifEmpty { 
                if (url.length > 35) url.substring(0, 32) + "..." else url 
            }
            historyDao.insertHistoryItem(CastHistoryItem(url = url, name = name))
            LogManager.i(TAG, "Added item to history: Name='$name', URL='$url'")
        }
    }

    suspend fun deleteHistoryItem(id: Int) {
        withContext(Dispatchers.IO) {
            historyDao.deleteHistoryItemById(id)
            LogManager.i(TAG, "Deleted history item: ID=$id")
        }
    }

    suspend fun clearHistory() {
        withContext(Dispatchers.IO) {
            historyDao.clearHistory()
            LogManager.i(TAG, "Cleared casting history database")
        }
    }

    suspend fun updateHistoryName(id: Int, name: String) {
        withContext(Dispatchers.IO) {
            historyDao.updateHistoryItemName(id, name)
            LogManager.i(TAG, "Updated history item ID=$id name to: $name")
        }
    }

    // DLNA Playback control actions
    suspend fun castMedia(mediaUrl: String): Boolean {
        val device = _selectedDevice.value
        val controlUrl = device?.avTransportControlUrl
        if (controlUrl == null) {
            LogManager.e(TAG, "Cast media failed: No target device or AVTransport URL defined.")
            return false
        }

        return withContext(Dispatchers.IO) {
            LogManager.i(TAG, "Casting media URL: $mediaUrl to ${device.friendlyName}")
            addHistoryItem(mediaUrl) // save to history automatically

            val setUriSuccess = DlnaControlClient.setAVTransportURI(controlUrl, mediaUrl)
            if (setUriSuccess) {
                delay(1000) // Small delay for TVs to digest the URL
                val playSuccess = DlnaControlClient.play(controlUrl)
                if (playSuccess) {
                    _activeCastUrl.value = mediaUrl
                    startPositionPolling(controlUrl)
                    true
                } else {
                    LogManager.e(TAG, "SetAVTransportURI succeeded, but PLAY action failed")
                    false
                }
            } else {
                LogManager.e(TAG, "SetAVTransportURI failed")
                false
            }
        }
    }

    suspend fun playMedia(): Boolean {
        val controlUrl = _selectedDevice.value?.avTransportControlUrl ?: return false
        return withContext(Dispatchers.IO) {
            val success = DlnaControlClient.play(controlUrl)
            if (success) {
                startPositionPolling(controlUrl)
            }
            success
        }
    }

    suspend fun pauseMedia(): Boolean {
        val controlUrl = _selectedDevice.value?.avTransportControlUrl ?: return false
        return withContext(Dispatchers.IO) {
            val success = DlnaControlClient.pause(controlUrl)
            if (success) {
                stopPositionPolling()
            }
            success
        }
    }

    suspend fun stopMedia(): Boolean {
        val controlUrl = _selectedDevice.value?.avTransportControlUrl ?: return false
        return withContext(Dispatchers.IO) {
            val success = DlnaControlClient.stop(controlUrl)
            if (success) {
                _activeCastUrl.value = null
                _playbackPosition.value = null
                stopPositionPolling()
            }
            success
        }
    }

    suspend fun seekMedia(seconds: Int): Boolean {
        val controlUrl = _selectedDevice.value?.avTransportControlUrl ?: return false
        return withContext(Dispatchers.IO) {
            val success = DlnaControlClient.seek(controlUrl, seconds)
            if (success) {
                // Force poll position immediately to update UI
                val newPos = DlnaControlClient.getPositionInfo(controlUrl)
                if (newPos != null) {
                    _playbackPosition.value = newPos
                }
            }
            success
        }
    }

    suspend fun setVolume(volume: Int): Boolean {
        _remoteVolume.value = volume
        val renderingUrl = _selectedDevice.value?.renderingControlUrl ?: return false
        return withContext(Dispatchers.IO) {
            DlnaControlClient.setVolume(renderingUrl, volume)
        }
    }

    // Polling background loop
    private fun startPositionPolling(controlUrl: String) {
        pollingJob?.cancel()
        pollingJob = repositoryScope.launch {
            while (true) {
                val position = DlnaControlClient.getPositionInfo(controlUrl)
                if (position != null) {
                    _playbackPosition.value = position
                }
                delay(2000) // Poll every 2 seconds
            }
        }
    }

    private fun stopPositionPolling() {
        pollingJob?.cancel()
        pollingJob = null
    }

    companion object {
        private const val KEY_TV_IP = "settings_tv_ip"
        private const val KEY_TV_FRIENDLY_NAME = "settings_tv_friendly_name"
    }
}
