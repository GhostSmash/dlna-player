package com.example.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object LogManager {
    private val _logs = MutableStateFlow<List<LogEntry>>(emptyList())
    val logs: StateFlow<List<LogEntry>> = _logs.asStateFlow()

    private val dateFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())

    data class LogEntry(
        val timestamp: String,
        val tag: String,
        val message: String,
        val isError: Boolean = false
    )

    fun i(tag: String, message: String) {
        addLog(tag, message, false)
        android.util.Log.i(tag, message)
    }

    fun e(tag: String, message: String, throwable: Throwable? = null) {
        val fullMessage = if (throwable != null) "$message\n${throwable.stackTraceToString()}" else message
        addLog(tag, fullMessage, true)
        android.util.Log.e(tag, fullMessage, throwable)
    }

    @Synchronized
    private fun addLog(tag: String, message: String, isError: Boolean) {
        val entry = LogEntry(
            timestamp = dateFormat.format(Date()),
            tag = tag,
            message = message,
            isError = isError
        )
        val current = _logs.value.toMutableList()
        current.add(0, entry) // Newest logs first
        if (current.size > 200) {
            current.removeAt(current.lastIndex)
        }
        _logs.value = current
    }

    fun clear() {
        _logs.value = emptyList()
    }
}
