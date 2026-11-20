package com.syildirim.teslacharge.core

import java.security.MessageDigest

/**
 * Tesla araçları BLE (telefon anahtarı) için "S" + SHA1(VIN)'in ilk 8 baytı (hex) + "C" biçiminde
 * yerel ad yayınlar (ör. S1a2b3c4d5e6f7a8bC). Bu, telefon anahtarı bağlantısını tahmin etmek için
 * kullanılan deneysel bir sezgidir; Tesla uygulamasının BLE oturumuna doğrudan erişim mümkün değildir.
 */
object TeslaBleName {
    private val pattern = Regex("^S[0-9a-f]{16}[CDRP]$")

    fun looksLikeTesla(name: String?): Boolean = name != null && pattern.matches(name)

    fun forVin(vin: String): String {
        val digest = MessageDigest.getInstance("SHA-1").digest(vin.trim().uppercase().toByteArray())
        return "S" + digest.take(8).joinToString("") { "%02x".format(it) } + "C"
    }
}
