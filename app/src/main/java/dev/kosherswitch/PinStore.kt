package dev.kosherswitch

import android.content.Context
import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/** Stores only a salted PBKDF2 hash of the code, never the code itself. */
object PinStore {
    private const val KEY_HASH = "pin_hash"
    private const val KEY_SALT = "pin_salt"
    private const val ITERATIONS = 60_000
    const val MIN_LENGTH = 4

    fun isSet(ctx: Context) = prefs(ctx).contains(KEY_HASH)

    fun set(ctx: Context, pin: String) {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        prefs(ctx).edit()
            .putString(KEY_SALT, encode(salt))
            .putString(KEY_HASH, encode(hash(pin, salt)))
            .apply()
    }

    fun verify(ctx: Context, pin: String): Boolean {
        val salt = prefs(ctx).getString(KEY_SALT, null)?.let(::decode) ?: return false
        val expected = prefs(ctx).getString(KEY_HASH, null)?.let(::decode) ?: return false
        return MessageDigest.isEqual(hash(pin, salt), expected)
    }

    private fun hash(pin: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(pin.toCharArray(), salt, ITERATIONS, 256)
        return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
    }

    private fun encode(b: ByteArray) = Base64.encodeToString(b, Base64.NO_WRAP)
    private fun decode(s: String) = Base64.decode(s, Base64.NO_WRAP)
}
