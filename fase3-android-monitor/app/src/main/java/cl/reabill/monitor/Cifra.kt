package cl.reabill.monitor

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

object Cifra {
    private const val ALIAS = "billmon_nube"

    private fun llave(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build()
        )
        return gen.generateKey()
    }

    fun cifrar(texto: String): String {
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, llave())
        return Base64.encodeToString(c.iv + c.doFinal(texto.toByteArray(Charsets.UTF_8)), Base64.NO_WRAP)
    }

    fun descifrar(b64: String): String? = try {
        val todo = Base64.decode(b64, Base64.NO_WRAP)
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, llave(), GCMParameterSpec(128, todo, 0, 12))
        String(c.doFinal(todo, 12, todo.size - 12), Charsets.UTF_8)
    } catch (e: Exception) {
        android.util.Log.w("BILLMON", "No se pudo descifrar la clave guardada: ${e.javaClass.simpleName}")
        null
    }
}
