package com.shortvideotracker.core.engine

import java.security.MessageDigest

/**
 * One-way, per-install salted hashing so stored identifiers cannot be linked to real video ids
 * or correlated across devices. Raw identifiers never reach the database.
 */
class IdentifierHasher(private val installSalt: String) {

    init {
        require(installSalt.length >= 16) { "install salt must be at least 16 characters" }
    }

    fun hash(vararg parts: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(installSalt.toByteArray(Charsets.UTF_8))
        for (part in parts) {
            digest.update(0)
            digest.update(part.toByteArray(Charsets.UTF_8))
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
