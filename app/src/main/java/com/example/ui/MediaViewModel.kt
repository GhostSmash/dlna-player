package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.LogManager
import com.example.data.MediaRepository
import com.example.data.database.CastHistoryItem
import com.example.data.dlna.DlnaControlClient
import com.example.data.dlna.DlnaDevice
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MediaViewModel(application: Application) : AndroidViewModel(application) {
    private val TAG = "MediaViewModel"
    private val repository = MediaRepository(application)

    // UI Tab Navigation State
    private val _activeTab = MutableStateFlow("remote")
    val activeTab: StateFlow<String> = _activeTab.asStateFlow()

    // Remote Form States
    private val _remoteUrl = MutableStateFlow("")
    val remoteUrl: StateFlow<String> = _remoteUrl.asStateFlow()

    // Local player state (for the preview player)
    private val _isLocalPlaying = MutableStateFlow(false)
    val isLocalPlaying: StateFlow<Boolean> = _isLocalPlaying.asStateFlow()

    private val _isLocalMuted = MutableStateFlow(false)
    val isLocalMuted: StateFlow<Boolean> = _isLocalMuted.asStateFlow()

    private val _localVolume = MutableStateFlow(80)
    val localVolume: StateFlow<Int> = _localVolume.asStateFlow()

    // Parser Form States
    private val _parserUrl = MutableStateFlow("")
    val parserUrl: StateFlow<String> = _parserUrl.asStateFlow()

    private val _isParsing = MutableStateFlow(false)
    val isParsing: StateFlow<Boolean> = _isParsing.asStateFlow()

    private val _currentParseStepIndex = MutableStateFlow(-1)
    val currentParseStepIndex: StateFlow<Int> = _currentParseStepIndex.asStateFlow()

    private val _parsedStreamUrl = MutableStateFlow<String?>(null)
    val parsedStreamUrl: StateFlow<String?> = _parsedStreamUrl.asStateFlow()

    // Settings Form States
    private val _settingsTvIp = MutableStateFlow("")
    val settingsTvIp: StateFlow<String> = _settingsTvIp.asStateFlow()

    // Flow definitions from Room Database
    val castHistory: StateFlow<List<CastHistoryItem>> = repository.allHistory
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val savedTvIp: StateFlow<String?> = repository.savedTvIpFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val savedTvFriendlyName: StateFlow<String?> = repository.savedTvFriendlyNameFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // SSDP Scanner values
    val dlnaDevices: StateFlow<List<DlnaDevice>> = repository.devices
    val isScanning: StateFlow<Boolean> = repository.isScanning

    // TV Connection State
    val selectedDevice: StateFlow<DlnaDevice?> = repository.selectedDevice
    val isTvConnected: StateFlow<Boolean> = repository.isTvConnected

    // Real-time playback status
    val activeCastUrl: StateFlow<String?> = repository.activeCastUrl
    val remotePlaybackPosition: StateFlow<DlnaControlClient.PlaybackPosition?> = repository.playbackPosition
    val remoteVolume: StateFlow<Int> = repository.remoteVolume

    // Event Messages (Toast-like)
    private val _toastEvent = MutableSharedFlow<ToastMessage>()
    val toastEvent: SharedFlow<ToastMessage> = _toastEvent.asSharedFlow()

    // System Log Flow
    val logs: StateFlow<List<LogManager.LogEntry>> = LogManager.logs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    data class ToastMessage(val text: String, val isError: Boolean = false)

    init {
        // Collect saved IP to prepopulate input field
        viewModelScope.launch {
            savedTvIp.collect { ip ->
                if (ip != null && _settingsTvIp.value.isEmpty()) {
                    _settingsTvIp.value = ip
                }
            }
        }

        // Auto trigger scanner on start
        startScanner()
    }

    fun setActiveTab(tab: String) {
        _activeTab.value = tab
    }

    fun setRemoteUrl(url: String) {
        _remoteUrl.value = url
    }

    fun setParserUrl(url: String) {
        _parserUrl.value = url
    }

    fun setSettingsTvIp(ip: String) {
        // Strip non-characters, keep dots and digits for formatting
        val formatted = ip.filter { it.isDigit() || it == '.' }
        _settingsTvIp.value = formatted
    }

    fun showToast(message: String, isError: Boolean = false) {
        viewModelScope.launch {
            _toastEvent.emit(ToastMessage(message, isError))
        }
    }

    // Media Control Functions
    fun startScanner() {
        LogManager.i(TAG, "Triggered manual SSDP Scanner scan from UI")
        repository.startScanning()
    }

    fun selectDevice(device: DlnaDevice?) {
        repository.selectDevice(device)
        if (device != null) {
            _settingsTvIp.value = device.ip
            showToast("Подключено к ${device.friendlyName}")
        } else {
            showToast("ТВ отключён")
        }
    }

    fun saveTvIpManual() {
        val ip = _settingsTvIp.value.trim()
        if (!isValidIpAddress(ip)) {
            showToast("Введите корректный IP-адрес", isError = true)
            return
        }
        viewModelScope.launch {
            repository.saveTvIp(ip)
            showToast("IP-адрес сохранён")
        }
    }

    fun clearTvIpManual() {
        viewModelScope.launch {
            repository.selectDevice(null)
            _settingsTvIp.value = ""
            showToast("Настройки сброшены")
        }
    }

    // Playback control wrappers
    fun toggleLocalPlay() {
        _isLocalPlaying.value = !_isLocalPlaying.value
        LogManager.i(TAG, "Local player play state toggled: ${_isLocalPlaying.value}")
    }

    fun toggleLocalMute() {
        _isLocalMuted.value = !_isLocalMuted.value
    }

    fun setLocalVolume(vol: Int) {
        _localVolume.value = vol
    }

    fun castMedia() {
        val url = _remoteUrl.value.trim()
        if (url.isEmpty() || !isValidUrl(url)) {
            showToast("Введите действительную ссылку", isError = true)
            return
        }
        if (!isTvConnected.value) {
            showToast("Укажите IP-адрес ТВ в настройках", isError = true)
            return
        }

        viewModelScope.launch {
            showToast("Трансляция запускается...")
            val success = repository.castMedia(url)
            if (success) {
                showToast("Трансляция начата на ${selectedDevice.value?.friendlyName ?: _settingsTvIp.value}")
            } else {
                showToast("Ошибка отправки потока на ТВ", isError = true)
            }
        }
    }

    fun playRemote() {
        viewModelScope.launch {
            val success = repository.playMedia()
            if (success) {
                showToast("Воспроизведение")
            } else {
                showToast("Ошибка воспроизведения", isError = true)
            }
        }
    }

    fun pauseRemote() {
        viewModelScope.launch {
            val success = repository.pauseMedia()
            if (success) {
                showToast("Пауза")
            } else {
                showToast("Ошибка паузы", isError = true)
            }
        }
    }

    fun stopRemote() {
        viewModelScope.launch {
            val success = repository.stopMedia()
            if (success) {
                showToast("Трансляция остановлена")
            } else {
                showToast("Ошибка остановки", isError = true)
            }
        }
    }

    fun seekRemote(seconds: Int) {
        viewModelScope.launch {
            val success = repository.seekMedia(seconds)
            if (!success) {
                showToast("Ошибка перемотки", isError = true)
            }
        }
    }

    fun setRemoteVolume(vol: Int) {
        viewModelScope.launch {
            repository.setVolume(vol)
        }
    }

    // History Actions
    fun deleteHistoryItem(id: Int) {
        viewModelScope.launch {
            repository.deleteHistoryItem(id)
            showToast("Удалено из истории")
        }
    }

    fun clearAllHistory() {
        viewModelScope.launch {
            repository.clearHistory()
            showToast("История очищена")
        }
    }

    fun renameHistoryItem(id: Int, newName: String) {
        if (newName.isBlank()) return
        viewModelScope.launch {
            repository.updateHistoryName(id, newName.trim())
            showToast("Название обновлено")
        }
    }

    // Diagnostics Logs
    fun clearLogs() {
        LogManager.clear()
        showToast("Логи отчищены")
    }

    // Simulated parser algorithm matching webpage's custom animation
    fun startParsing() {
        val pageUrl = _parserUrl.value.trim()
        if (pageUrl.isEmpty() || !isValidUrl(pageUrl)) {
            showToast("Введите ссылку для парсинга", isError = true)
            return
        }

        if (_isParsing.value) return

        _isParsing.value = true
        _parsedStreamUrl.value = null
        _currentParseStepIndex.value = 0

        viewModelScope.launch(Dispatchers.Default) {
            val stepsCount = 5
            for (step in 0 until stepsCount) {
                _currentParseStepIndex.value = step
                LogManager.i(TAG, "Parser step ${step + 1}: Executing background stream capture simulation...")
                // Simulate processing delays
                val delayTime = if (step == stepsCount - 1) 1200L else (800L + (Math.random() * 600L).toLong())
                delay(delayTime)
            }

            // Successfully captured mock/proxy stream URL based on current timestamp
            val timestamp = System.currentTimeMillis() / 1000
            val capturedStream = "https://stream.proxy.dlna.app/$timestamp/video.mp4"

            withContext(Dispatchers.Main) {
                _parsedStreamUrl.value = capturedStream
                _remoteUrl.value = capturedStream
                _isParsing.value = false
                _currentParseStepIndex.value = 5 // complete
                showToast("Поток захвачен и подставлен в Пульт")
                LogManager.i(TAG, "Parser pipeline complete. Extracted Stream: $capturedStream")
            }
        }
    }

    // Utilities
    private fun isValidUrl(url: String): Boolean {
        return try {
            val lower = url.lowercase()
            lower.startsWith("http://") || lower.startsWith("https://")
        } catch (e: Exception) {
            false
        }
    }

    private fun isValidIpAddress(ip: String): Boolean {
        val parts = ip.split(".")
        if (parts.size != 4) return false
        return parts.all {
            val value = it.toIntOrNull()
            value != null && value in 0..255 && it == value.toString()
        }
    }
}
