package com.sarjdefteri.app.tesla

import android.net.Uri
import android.util.Base64
import com.sarjdefteri.app.data.settings.SettingsRepository
import java.security.MessageDigest
import java.security.SecureRandom
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject

/**
 * Tesla Fleet API entegrasyonu.
 *
 * Enerji / maliyet ilkeleri:
 *  - Araç ASLA uyandırılmaz (wake_up çağrısı yok). Önce /vehicles/{id} ile durum okunur (aracı uyandırmaz);
 *    yalnızca "online" ise /vehicle_data çağrılır. Uykudaysa [TeslaResult.VehicleAsleep] döner.
 *  - Sorgular olay tetiklidir (Bluetooth bağlantısı/ayrılması, şarj takibi) ve sınırlı sayıdadır.
 */
class TeslaRepository(
    private val settings: SettingsRepository,
    private val secure: SecureStore,
    private val api: FleetApiClient = FleetApiClient(),
) {
    private val refreshMutex = Mutex()

    val scopes = "openid offline_access vehicle_device_data vehicle_location"

    fun hasTokens(): Boolean = secure.get(KEY_ACCESS) != null
    fun hasClientSecret(): Boolean = secure.get(KEY_CLIENT_SECRET) != null
    fun saveClientSecret(secret: String) = secure.put(KEY_CLIENT_SECRET, secret.trim().ifEmpty { null })

    /** Otomasyon yalnızca Pro'da ve araç seçiliyken çalışır. */
    suspend fun isReady(): Boolean {
        val s = settings.current()
        return s.isPro && hasTokens() && s.teslaVehicleId.isNotEmpty()
    }

    // ---------- OAuth 2.0 (Authorization Code + PKCE) ----------

    /** Tarayıcıda açılacak yetkilendirme adresini üretir ve PKCE doğrulayıcısını saklar. */
    suspend fun buildAuthorizeUrl(): String? {
        val s = settings.current()
        if (s.teslaClientId.isBlank()) return null
        val verifier = randomUrlSafe(64)
        val state = randomUrlSafe(24)
        secure.put(KEY_PKCE_VERIFIER, verifier)
        secure.put(KEY_OAUTH_STATE, state)
        val challenge = Base64.encodeToString(
            MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII)),
            Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING,
        )
        return Uri.parse("${FleetApiClient.AUTH_BASE}/authorize").buildUpon()
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter("client_id", s.teslaClientId)
            .appendQueryParameter("redirect_uri", s.teslaRedirectUri)
            .appendQueryParameter("scope", scopes)
            .appendQueryParameter("state", state)
            .appendQueryParameter("code_challenge", challenge)
            .appendQueryParameter("code_challenge_method", "S256")
            .appendQueryParameter("locale", "tr-TR")
            .appendQueryParameter("prompt_missing_scopes", "true")
            .build().toString()
    }

    /** Yönlendirme adresinden (deep link veya kullanıcının yapıştırdığı URL) kodu alıp token'a çevirir. */
    suspend fun handleRedirect(redirect: String): TeslaResult<Unit> {
        val uri = runCatching { Uri.parse(redirect.trim()) }.getOrNull()
        val code = uri?.getQueryParameter("code") ?: redirect.trim().takeIf { !it.contains("://") && it.isNotEmpty() }
            ?: return TeslaResult.Error("Adreste 'code' parametresi bulunamadı")
        uri?.getQueryParameter("error")?.let { return TeslaResult.Error("Tesla: $it") }
        val expectedState = secure.get(KEY_OAUTH_STATE)
        val state = uri?.getQueryParameter("state")
        if (state != null && expectedState != null && state != expectedState) return TeslaResult.Error("Geçersiz state (güvenlik kontrolü)")

        val s = settings.current()
        val form = buildMap {
            put("grant_type", "authorization_code")
            put("client_id", s.teslaClientId)
            secure.get(KEY_CLIENT_SECRET)?.let { put("client_secret", it) }
            put("code", code)
            put("redirect_uri", s.teslaRedirectUri)
            put("audience", s.teslaRegion.apiBase)
            secure.get(KEY_PKCE_VERIFIER)?.let { put("code_verifier", it) }
        }
        val res = api.postForm(FleetApiClient.TOKEN_URL, form)
        if (res.code !in 200..299) return TeslaResult.Error(parseError(res), res.code)
        storeTokens(JSONObject(res.body))
        secure.clear(KEY_PKCE_VERIFIER, KEY_OAUTH_STATE)
        return TeslaResult.Ok(Unit)
    }

    /** Başka bir araçtan alınmış token'ları elle girme seçeneği. */
    fun saveManualTokens(access: String, refresh: String?) {
        secure.put(KEY_ACCESS, access.trim())
        secure.put(KEY_REFRESH, refresh?.trim()?.ifEmpty { null })
        secure.put(KEY_EXPIRES, (System.currentTimeMillis() + 3600_000L).toString())
    }

    private fun storeTokens(json: JSONObject) {
        secure.put(KEY_ACCESS, json.getString("access_token"))
        json.optString("refresh_token").takeIf { it.isNotEmpty() }?.let { secure.put(KEY_REFRESH, it) }
        val expiresIn = json.optLong("expires_in", 28_800L)
        secure.put(KEY_EXPIRES, (System.currentTimeMillis() + expiresIn * 1000L).toString())
    }

    private suspend fun validAccessToken(forceRefresh: Boolean = false): String? = refreshMutex.withLock {
        val access = secure.get(KEY_ACCESS) ?: return null
        val expires = secure.get(KEY_EXPIRES)?.toLongOrNull() ?: 0L
        if (!forceRefresh && System.currentTimeMillis() < expires - 120_000L) return access
        val refresh = secure.get(KEY_REFRESH) ?: return access
        val res = api.postForm(
            FleetApiClient.TOKEN_URL,
            mapOf("grant_type" to "refresh_token", "client_id" to settings.current().teslaClientId, "refresh_token" to refresh),
        )
        if (res.code in 200..299) {
            storeTokens(JSONObject(res.body)); secure.get(KEY_ACCESS)
        } else if (res.code == 400 || res.code == 401) {
            null // refresh token geçersiz → kullanıcı yeniden giriş yapmalı
        } else access
    }

    private suspend fun authedGet(path: String): FleetApiClient.HttpResponse? {
        val base = settings.current().teslaRegion.apiBase
        var token = validAccessToken() ?: return null
        var res = api.get(base + path, token)
        if (res.code == 401) {
            token = validAccessToken(forceRefresh = true) ?: return res
            res = api.get(base + path, token)
        }
        return res
    }

    // ---------- Veri ----------

    suspend fun listVehicles(): TeslaResult<List<TeslaVehicle>> {
        val res = authedGet("/api/1/vehicles") ?: return TeslaResult.NotConfigured
        if (res.code !in 200..299) return TeslaResult.Error(parseError(res), res.code)
        val arr = JSONObject(res.body).optJSONArray("response") ?: return TeslaResult.Ok(emptyList())
        return TeslaResult.Ok((0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            TeslaVehicle(
                id = o.opt("id")?.toString() ?: o.optString("id_s"),
                vin = o.optString("vin"),
                displayName = o.optString("display_name").ifEmpty { "Tesla" },
                state = o.optString("state"),
            )
        })
    }

    /** Aracın bağlantı durumunu okur. Bu çağrı aracı uyandırmaz. */
    suspend fun vehicleState(): TeslaResult<String> {
        val id = settings.current().teslaVehicleId.ifEmpty { return TeslaResult.NotConfigured }
        val res = authedGet("/api/1/vehicles/$id") ?: return TeslaResult.NotConfigured
        if (res.code !in 200..299) return TeslaResult.Error(parseError(res), res.code)
        return TeslaResult.Ok(JSONObject(res.body).optJSONObject("response")?.optString("state") ?: "unknown")
    }

    /**
     * Araç çevrimiçiyse anlık veriyi okur; uykudaysa uyandırmadan [TeslaResult.VehicleAsleep] döner.
     */
    suspend fun snapshotIfOnline(includeLocation: Boolean = false): TeslaResult<VehicleSnapshot> {
        when (val st = vehicleState()) {
            is TeslaResult.Ok -> if (st.value != "online") return TeslaResult.VehicleAsleep
            is TeslaResult.Error -> return st
            else -> return TeslaResult.NotConfigured
        }
        val id = settings.current().teslaVehicleId
        val endpoints = if (includeLocation) "charge_state;vehicle_state;drive_state;location_data" else "charge_state;vehicle_state"
        val res = authedGet("/api/1/vehicles/$id/vehicle_data?endpoints=" + FleetApiClient.enc(endpoints))
            ?: return TeslaResult.NotConfigured
        if (res.code == 408) return TeslaResult.VehicleAsleep
        if (res.code !in 200..299) return TeslaResult.Error(parseError(res), res.code)
        return TeslaResult.Ok(parseSnapshot(JSONObject(res.body).getJSONObject("response")))
    }

    /**
     * Uygulamanın bölgeye kaydı (tek seferlik). Tesla, uygulama alan adında
     * /.well-known/appspecific/com.tesla.3p.public-key.pem dosyasının yayınlanmasını şart koşar.
     */
    suspend fun registerPartnerAccount(domain: String): TeslaResult<Unit> {
        val s = settings.current()
        val secret = secure.get(KEY_CLIENT_SECRET) ?: return TeslaResult.Error("Client secret gerekli")
        val tokenRes = api.postForm(
            FleetApiClient.TOKEN_URL,
            mapOf(
                "grant_type" to "client_credentials", "client_id" to s.teslaClientId, "client_secret" to secret,
                "scope" to scopes, "audience" to s.teslaRegion.apiBase,
            ),
        )
        if (tokenRes.code !in 200..299) return TeslaResult.Error(parseError(tokenRes), tokenRes.code)
        val partnerToken = JSONObject(tokenRes.body).getString("access_token")
        val res = api.postJson("${s.teslaRegion.apiBase}/api/1/partner_accounts", partnerToken, JSONObject().put("domain", domain.trim()))
        if (res.code !in 200..299) return TeslaResult.Error(parseError(res), res.code)
        settings.setPartnerDomain(domain)
        return TeslaResult.Ok(Unit)
    }

    suspend fun disconnect() {
        secure.clear(KEY_ACCESS, KEY_REFRESH, KEY_EXPIRES, KEY_PKCE_VERIFIER, KEY_OAUTH_STATE)
        settings.clearTesla()
    }

    fun forgetClientSecret() = secure.clear(KEY_CLIENT_SECRET)

    private fun parseError(res: FleetApiClient.HttpResponse): String {
        if (res.code == -1) return "Ağ hatası: ${res.body}"
        val msg = runCatching {
            val o = JSONObject(res.body)
            o.optString("error_description").ifEmpty { o.optString("error") }.ifEmpty { o.optString("message") }
        }.getOrNull().orEmpty()
        val hint = when (res.code) {
            401 -> " (yetki yok / token süresi doldu)"
            403 -> " (kapsam izni eksik)"
            412 -> " (uygulama bu bölgeye kayıtlı değil: partner kaydı yapın)"
            421 -> " (yanlış bölge seçildi)"
            429 -> " (istek limiti aşıldı)"
            else -> ""
        }
        return "HTTP ${res.code}: ${msg.ifEmpty { res.body.take(160) }}$hint"
    }

    companion object {
        private const val KEY_ACCESS = "tesla_access_token"
        private const val KEY_REFRESH = "tesla_refresh_token"
        private const val KEY_EXPIRES = "tesla_expires_at"
        private const val KEY_CLIENT_SECRET = "tesla_client_secret"
        private const val KEY_PKCE_VERIFIER = "tesla_pkce_verifier"
        private const val KEY_OAUTH_STATE = "tesla_oauth_state"
        private const val MILES_TO_KM = 1.609344

        private fun randomUrlSafe(bytes: Int): String {
            val b = ByteArray(bytes).also { SecureRandom().nextBytes(it) }
            return Base64.encodeToString(b, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
        }

        fun parseSnapshot(r: JSONObject): VehicleSnapshot {
            val charge = r.optJSONObject("charge_state")
            val vehicle = r.optJSONObject("vehicle_state")
            val drive = r.optJSONObject("drive_state")
            fun JSONObject?.dbl(k: String): Double? = this?.takeIf { it.has(k) && !it.isNull(k) }?.optDouble(k)?.takeIf { !it.isNaN() }
            fun JSONObject?.str(k: String): String? = this?.takeIf { it.has(k) && !it.isNull(k) }?.optString(k)?.ifEmpty { null }
            return VehicleSnapshot(
                fetchedAt = System.currentTimeMillis(),
                odometerKm = vehicle.dbl("odometer")?.let { Math.round(it * MILES_TO_KM * 10) / 10.0 },
                batteryLevel = charge.dbl("battery_level"),
                chargingState = charge.str("charging_state"),
                chargeEnergyAddedKwh = charge.dbl("charge_energy_added"),
                chargerPowerKw = charge.dbl("charger_power"),
                minutesToFullCharge = charge.dbl("time_to_full_charge")?.let { (it * 60).toInt() },
                fastChargerPresent = charge?.takeIf { it.has("fast_charger_present") }?.optBoolean("fast_charger_present"),
                fastChargerBrand = charge.str("fast_charger_brand"),
                latitude = drive.dbl("latitude"),
                longitude = drive.dbl("longitude"),
                activeRouteDestination = drive.str("active_route_destination"),
                activeRouteMinutesToArrival = drive.dbl("active_route_minutes_to_arrival"),
                shiftState = drive.str("shift_state"),
            )
        }
    }
}
