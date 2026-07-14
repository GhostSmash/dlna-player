package com.example.data.dlna

import android.content.Context
import android.net.wifi.WifiManager
import com.example.data.LogManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.URL
import java.util.concurrent.TimeUnit

class DlnaScanner(private val context: Context) {
    private val TAG = "DlnaScanner"

    private val _devices = MutableStateFlow<List<DlnaDevice>>(emptyList())
    val devices: StateFlow<List<DlnaDevice>> = _devices.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(3, TimeUnit.SECONDS)
        .build()

    private var multicastLock: WifiManager.MulticastLock? = null

    init {
        try {
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            multicastLock = wifiManager.createMulticastLock("DlnaMulticastLock").apply {
                setReferenceCounted(false)
            }
        } catch (e: Exception) {
            LogManager.e(TAG, "Failed to initialize MulticastLock", e)
        }
    }

    fun startScan() {
        if (_isScanning.value) return
        _isScanning.value = true
        LogManager.i(TAG, "SSDP Network Scan STARTED")

        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Safely acquire Multicast Lock
                multicastLock?.let {
                    if (!it.isHeld) {
                        it.acquire()
                        LogManager.i(TAG, "MulticastLock acquired successfully")
                    }
                }

                val foundDevices = mutableMapOf<String, DlnaDevice>()

                DatagramSocket().use { socket ->
                    socket.soTimeout = 4000 // 4 seconds timeout for responses

                    // SSDP Discovery Packets targeting AVTransport & ssdp:all
                    val mSearchAll = createMSearchMessage("ssdp:all")
                    val mSearchAV = createMSearchMessage("urn:schemas-upnp-org:service:AVTransport:1")

                    val group = InetAddress.getByName("239.255.255.250")
                    val port = 1900

                    // Send discovery packets multiple times to prevent UDP packet loss on Wi-Fi
                    sendPacket(socket, mSearchAll, group, port)
                    sendPacket(socket, mSearchAV, group, port)

                    val buffer = ByteArray(4096)
                    val startTime = System.currentTimeMillis()

                    while (System.currentTimeMillis() - startTime < 6000) {
                        try {
                            val packet = DatagramPacket(buffer, buffer.size)
                            socket.receive(packet)

                            val response = String(packet.data, 0, packet.length, Charsets.UTF_8)
                            val senderAddress = packet.address.hostAddress ?: ""
                            val senderPort = packet.port

                            LogManager.i(TAG, "SSDP Response received from $senderAddress:$senderPort:\n$response")

                            val location = extractHeaderValue(response, "LOCATION")
                            val usn = extractHeaderValue(response, "USN") ?: "uuid:$senderAddress"

                            if (location != null && !foundDevices.containsKey(usn)) {
                                LogManager.i(TAG, "Discovered location: $location for USN: $usn. Fetching description...")
                                launch(Dispatchers.IO) {
                                    val device = fetchDeviceDescription(location, usn, senderAddress)
                                    if (device != null) {
                                        synchronized(foundDevices) {
                                            foundDevices[usn] = device
                                            _devices.value = foundDevices.values.toList().sortedBy { it.friendlyName }
                                        }
                                        LogManager.i(TAG, "Successfully added device: ${device.friendlyName} (${device.ip})")
                                    }
                                }
                            }
                        } catch (e: java.net.SocketTimeoutException) {
                            // Timeout is normal, break or continue if we have time
                            break
                        } catch (e: Exception) {
                            LogManager.e(TAG, "Error receiving SSDP packet", e)
                        }
                    }
                }
            } catch (e: Exception) {
                LogManager.e(TAG, "Exception during SSDP Scan", e)
            } finally {
                // Safely release multicast lock
                try {
                    multicastLock?.let {
                        if (it.isHeld) {
                            it.release()
                            LogManager.i(TAG, "MulticastLock released")
                        }
                    }
                } catch (e: Exception) {
                    LogManager.e(TAG, "Error releasing MulticastLock", e)
                }
                _isScanning.value = false
                LogManager.i(TAG, "SSDP Network Scan FINISHED. Found ${_devices.value.size} devices.")
            }
        }
    }

    private fun sendPacket(socket: DatagramSocket, message: String, group: InetAddress, port: Int) {
        try {
            val bytes = message.toByteArray(Charsets.UTF_8)
            val packet = DatagramPacket(bytes, bytes.size, group, port)
            socket.send(packet)
            LogManager.i(TAG, "SSDP M-SEARCH Datagram sent to ${group.hostAddress}:$port")
        } catch (e: Exception) {
            LogManager.e(TAG, "Error sending SSDP packet", e)
        }
    }

    private fun fetchDeviceDescription(location: String, usn: String, defaultIp: String): DlnaDevice? {
        val request = Request.Builder()
            .url(location)
            .header("User-Agent", "Android/DLNA-Remote")
            .build()

        return try {
            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    LogManager.e(TAG, "Failed to fetch description from $location: HTTP ${response.code}")
                    return null
                }
                val body = response.body?.string() ?: ""
                LogManager.i(TAG, "Description XML fetched successfully from $location")

                val parsedUrl = URL(location)
                val hostIp = parsedUrl.host ?: defaultIp
                val hostPort = if (parsedUrl.port != -1) parsedUrl.port else parsedUrl.defaultPort

                val friendlyName = extractTag(body, "friendlyName") ?: "Smart TV ($hostIp)"
                
                // Extract service control paths
                val avTransportPath = extractServiceControlUrl(body, "urn:schemas-upnp-org:service:AVTransport:1")
                val renderingControlPath = extractServiceControlUrl(body, "urn:schemas-upnp-org:service:RenderingControl:1")

                val resolvedAvTransport = avTransportPath?.let { resolveUrl(location, it) }
                val resolvedRendering = renderingControlPath?.let { resolveUrl(location, it) }

                LogManager.i(TAG, "Parsed device info:\nFriendlyName: $friendlyName\nAVTransport Control URL: $resolvedAvTransport\nRenderingControl Control URL: $resolvedRendering")

                DlnaDevice(
                    id = usn,
                    ip = hostIp,
                    port = hostPort,
                    friendlyName = friendlyName,
                    location = location,
                    avTransportControlUrl = resolvedAvTransport,
                    renderingControlUrl = resolvedRendering
                )
            }
        } catch (e: Exception) {
            LogManager.e(TAG, "Exception fetching description XML from $location", e)
            null
        }
    }

    private fun resolveUrl(baseUrlStr: String, relativeUrlStr: String): String {
        return if (relativeUrlStr.startsWith("http://") || relativeUrlStr.startsWith("https://")) {
            relativeUrlStr
        } else {
            try {
                val baseUrl = URL(baseUrlStr)
                val basePortStr = if (baseUrl.port != -1) ":${baseUrl.port}" else ""
                val resolvedPath = if (relativeUrlStr.startsWith("/")) relativeUrlStr else "/$relativeUrlStr"
                "${baseUrl.protocol}://${baseUrl.host}$basePortStr$resolvedPath"
            } catch (e: Exception) {
                relativeUrlStr
            }
        }
    }

    private fun extractServiceControlUrl(xml: String, serviceType: String): String? {
        val serviceRegex = "<service>(.*?)</service>".toRegex(setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
        serviceRegex.findAll(xml).forEach { match ->
            val serviceBlock = match.groupValues[1]
            val type = extractTag(serviceBlock, "serviceType")
            if (type != null && type.contains(serviceType, ignoreCase = true)) {
                val control = extractTag(serviceBlock, "controlURL")
                if (control != null) return control
            }
        }
        return null
    }

    private fun extractTag(xml: String, tag: String): String? {
        val regex = "<$tag>(.*?)</$tag>".toRegex(RegexOption.IGNORE_CASE)
        return regex.find(xml)?.groupValues?.get(1)?.trim()
    }

    private fun extractHeaderValue(response: String, headerName: String): String? {
        val lines = response.split("\r\n", "\n")
        for (line in lines) {
            if (line.startsWith(headerName, ignoreCase = true)) {
                val parts = line.split(":", limit = 2)
                if (parts.size == 2) {
                    return parts[1].trim()
                }
            }
        }
        return null
    }

    private fun createMSearchMessage(searchTarget: String): String {
        return """
            M-SEARCH * HTTP/1.1
            HOST: 239.255.255.250:1900
            MAN: "ssdp:discover"
            MX: 3
            ST: $searchTarget
            
        """.trimIndent().replace("\n", "\r\n")
    }
}
