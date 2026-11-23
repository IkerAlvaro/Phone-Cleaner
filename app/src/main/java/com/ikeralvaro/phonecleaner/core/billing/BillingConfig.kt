package com.ikeralvaro.phonecleaner.core.billing

import android.util.Base64
import java.security.KeyFactory
import java.security.Signature
import java.security.spec.X509EncodedKeySpec

/** Configuración de Google Play Billing. */
object BillingConfig {
    /**
     * Clave pública de licencia en base64 (Play Console > Monetizar > Configuración de monetización).
     * Si está vacía no se verifica la firma de las compras en el móvil (solo la comprobación de Google Play).
     */
    const val PLAY_LICENSE_KEY: String = ""

    /** Verifica la firma de una compra con [PLAY_LICENSE_KEY]. Sin clave configurada se acepta. */
    fun verify(signedData: String, signature: String, publicKey: String = PLAY_LICENSE_KEY): Boolean {
        if (publicKey.isBlank()) return true
        if (signature.isBlank()) return false
        return try {
            val key = KeyFactory.getInstance("RSA")
                .generatePublic(X509EncodedKeySpec(Base64.decode(publicKey, Base64.DEFAULT)))
            Signature.getInstance("SHA1withRSA").run {
                initVerify(key)
                update(signedData.toByteArray(Charsets.UTF_8))
                verify(Base64.decode(signature, Base64.DEFAULT))
            }
        } catch (_: Exception) {
            false
        }
    }
}
