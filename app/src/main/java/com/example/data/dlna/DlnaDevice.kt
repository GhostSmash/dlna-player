package com.example.data.dlna

data class DlnaDevice(
    val id: String, // USN or UUID
    val ip: String,
    val port: Int,
    val friendlyName: String,
    val location: String, // description.xml URL
    val avTransportControlUrl: String? = null,
    val renderingControlUrl: String? = null,
    val lastSeen: Long = System.currentTimeMillis()
)
