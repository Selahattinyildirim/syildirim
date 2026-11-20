package com.syildirim.teslacharge.tesla

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Tesla Fleet API için küçük HTTP istemcisi (harici bağımlılık yok).
 * Dokümantasyon: https://developer.tesla.com/docs/fleet-api
 */
class FleetApiClient {

    data class HttpResponse(val code: Int, val body: String)

    suspend fun get(url: String, accessToken: String): HttpResponse = request("GET", url, accessToken, null, null)

    suspend fun postJson(url: String, accessToken: String, json: JSONObject): HttpResponse =
        request("POST", url, accessToken, json.toString(), "application/json")

    suspend fun postForm(url: String, form: Map<String, String>): HttpResponse =
        request("POST", url, null, form.entries.joinToString("&") { (k, v) -> "${enc(k)}=${enc(v)}" },
            "application/x-www-form-urlencoded")

    private suspend fun request(method: String, url: String, token: String?, body: String?, contentType: String?): HttpResponse =
        withContext(Dispatchers.IO) {
            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = method
                connectTimeout = 15_000
                readTimeout = 30_000
                setRequestProperty("Accept", "application/json")
                setRequestProperty("User-Agent", "TeslaChargeTracker-Android/1.0")
                token?.let { setRequestProperty("Authorization", "Bearer $it") }
                if (body != null) {
                    doOutput = true
                    setRequestProperty("Content-Type", contentType)
                }
            }
            try {
                if (body != null) conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
                val code = conn.responseCode
                val stream = if (code in 200..299) conn.inputStream else conn.errorStream
                val text = stream?.bufferedReader()?.use { it.readText() } ?: ""
                HttpResponse(code, text)
            } catch (e: IOException) {
                HttpResponse(-1, e.message ?: "ağ hatası")
            } finally {
                conn.disconnect()
            }
        }

    companion object {
        const val AUTH_BASE = "https://auth.tesla.com/oauth2/v3"
        const val TOKEN_URL = "https://fleet-auth.prd.vn.cloud.tesla.com/oauth2/v3/token"
        fun enc(s: String): String = URLEncoder.encode(s, "UTF-8")
    }
}
