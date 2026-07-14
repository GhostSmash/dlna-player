package com.example.data.dlna

import com.example.data.LogManager
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

object DlnaControlClient {
    private const val TAG = "DlnaControl"

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .writeTimeout(5, TimeUnit.SECONDS)
        .build()

    data class PlaybackPosition(
        val relTimeSeconds: Int,
        val durationSeconds: Int,
        val relTimeString: String,
        val durationString: String
    )

    private fun postSoap(controlUrl: String, serviceType: String, action: String, bodyContent: String): String? {
        val soapBody = """
            <?xml version="1.0" encoding="utf-8"?>
            <s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/">
                <s:Body>
                    <u:$action xmlns:u="$serviceType">
                        $bodyContent
                    </u:$action>
                </s:Body>
            </s:Envelope>
        """.trimIndent()

        val mediaType = "text/xml; charset=\"utf-8\"".toMediaType()
        val requestBody = soapBody.toRequestBody(mediaType)

        val soapActionHeader = "\"$serviceType#$action\""
        val request = Request.Builder()
            .url(controlUrl)
            .post(requestBody)
            .header("SOAPACTION", soapActionHeader)
            .header("Content-Type", "text/xml; charset=\"utf-8\"")
            .header("Connection", "close")
            .build()

        LogManager.i(TAG, "Sending SOAP Action: $action to $controlUrl\nHeader: $soapActionHeader\nBody: $soapBody")

        return try {
            okHttpClient.newCall(request).execute().use { response ->
                val bodyStr = response.body?.string()
                if (response.isSuccessful) {
                    LogManager.i(TAG, "SOAP Action $action SUCCEEDED. Response:\n$bodyStr")
                    bodyStr
                } else {
                    LogManager.e(TAG, "SOAP Action $action FAILED with code: ${response.code}\nResponse:\n$bodyStr")
                    null
                }
            }
        } catch (e: Exception) {
            LogManager.e(TAG, "Error performing SOAP Action $action", e)
            null
        }
    }

    fun setAVTransportURI(controlUrl: String, mediaUrl: String): Boolean {
        // Escaped DIDL-Lite metadata works wonders for modern Smart TVs to display proper video info
        val metadata = """
            <DIDL-Lite xmlns="urn:schemas-upnp-org:metadata-1-0/DIDL-Lite/" xmlns:upnp="urn:schemas-upnp-org:metadata-1-0/upnp/" xmlns:dc="http://purl.org/dc/elements/1.1/">
                <item id="0" parentID="0" restricted="1">
                    <dc:title>DLNA Stream</dc:title>
                    <upnp:class>object.item.videoItem.movie</upnp:class>
                    <res protocolInfo="http-get:*:video/mp4:*">$mediaUrl</res>
                </item>
            </DIDL-Lite>
        """.trimIndent()
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")

        val body = """
            <InstanceID>0</InstanceID>
            <CurrentURI>$mediaUrl</CurrentURI>
            <CurrentURIMetaData>$metadata</CurrentURIMetaData>
        """.trimIndent()

        val response = postSoap(
            controlUrl = controlUrl,
            serviceType = "urn:schemas-upnp-org:service:AVTransport:1",
            action = "SetAVTransportURI",
            bodyContent = body
        )
        return response != null
    }

    fun play(controlUrl: String): Boolean {
        val body = """
            <InstanceID>0</InstanceID>
            <Speed>1</Speed>
        """.trimIndent()
        val response = postSoap(
            controlUrl = controlUrl,
            serviceType = "urn:schemas-upnp-org:service:AVTransport:1",
            action = "Play",
            bodyContent = body
        )
        return response != null
    }

    fun pause(controlUrl: String): Boolean {
        val body = "<InstanceID>0</InstanceID>"
        val response = postSoap(
            controlUrl = controlUrl,
            serviceType = "urn:schemas-upnp-org:service:AVTransport:1",
            action = "Pause",
            bodyContent = body
        )
        return response != null
    }

    fun stop(controlUrl: String): Boolean {
        val body = "<InstanceID>0</InstanceID>"
        val response = postSoap(
            controlUrl = controlUrl,
            serviceType = "urn:schemas-upnp-org:service:AVTransport:1",
            action = "Stop",
            bodyContent = body
        )
        return response != null
    }

    fun seek(controlUrl: String, seconds: Int): Boolean {
        val timeString = formatSecondsToTimeString(seconds)
        val body = """
            <InstanceID>0</InstanceID>
            <Unit>REL_TIME</Unit>
            <Target>$timeString</Target>
        """.trimIndent()
        val response = postSoap(
            controlUrl = controlUrl,
            serviceType = "urn:schemas-upnp-org:service:AVTransport:1",
            action = "Seek",
            bodyContent = body
        )
        return response != null
    }

    fun setVolume(renderingControlUrl: String, volume: Int): Boolean {
        val body = """
            <InstanceID>0</InstanceID>
            <Channel>Master</Channel>
            <DesiredVolume>$volume</DesiredVolume>
        """.trimIndent()
        val response = postSoap(
            controlUrl = renderingControlUrl,
            serviceType = "urn:schemas-upnp-org:service:RenderingControl:1",
            action = "SetVolume",
            bodyContent = body
        )
        return response != null
    }

    fun getPositionInfo(controlUrl: String): PlaybackPosition? {
        val body = "<InstanceID>0</InstanceID>"
        val xmlResponse = postSoap(
            controlUrl = controlUrl,
            serviceType = "urn:schemas-upnp-org:service:AVTransport:1",
            action = "GetPositionInfo",
            bodyContent = body
        ) ?: return null

        return try {
            val durationStr = extractTag(xmlResponse, "TrackDuration") ?: "00:00:00"
            val relTimeStr = extractTag(xmlResponse, "RelTime") ?: "00:00:00"

            val durationSeconds = parseTimeStringToSeconds(durationStr)
            val relTimeSeconds = parseTimeStringToSeconds(relTimeStr)

            PlaybackPosition(
                relTimeSeconds = relTimeSeconds,
                durationSeconds = durationSeconds,
                relTimeString = relTimeStr,
                durationString = durationStr
            )
        } catch (e: Exception) {
            LogManager.e(TAG, "Error parsing PositionInfo", e)
            null
        }
    }

    fun getVolume(renderingControlUrl: String): Int {
        val body = """
            <InstanceID>0</InstanceID>
            <Channel>Master</Channel>
        """.trimIndent()
        val xmlResponse = postSoap(
            controlUrl = renderingControlUrl,
            serviceType = "urn:schemas-upnp-org:service:RenderingControl:1",
            action = "GetVolume",
            bodyContent = body
        ) ?: return -1

        return try {
            val volumeStr = extractTag(xmlResponse, "CurrentVolume")
            volumeStr?.toIntOrNull() ?: -1
        } catch (e: Exception) {
            LogManager.e(TAG, "Error parsing Volume response", e)
            -1
        }
    }

    private fun extractTag(xml: String, tag: String): String? {
        val regex = "<$tag>(.*?)</$tag>".toRegex(RegexOption.IGNORE_CASE)
        return regex.find(xml)?.groupValues?.get(1)?.trim()
    }

    fun formatSecondsToTimeString(seconds: Int): String {
        val h = seconds / 3600
        val m = (seconds % 3600) / 60
        val s = seconds % 60
        return String.format("%02d:%02d:%02d", h, m, s)
    }

    fun parseTimeStringToSeconds(timeStr: String): Int {
        val parts = timeStr.split(":")
        if (parts.size != 3) return 0
        val h = parts[0].toIntOrNull() ?: 0
        val m = parts[1].toIntOrNull() ?: 0
        val s = parts[2].toIntOrNull() ?: 0
        return h * 3600 + m * 60 + s
    }
}
