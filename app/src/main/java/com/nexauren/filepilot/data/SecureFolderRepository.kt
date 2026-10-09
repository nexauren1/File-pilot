package com.nexauren.filepilot.data

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.security.SecureRandom
import java.security.spec.PBEKeySpec
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.CipherOutputStream
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

data class SecureVaultItem(
    val id: String,
    val name: String,
    val mimeType: String?,
    val sizeBytes: Long,
    val addedAt: Long,
)

data class SecureMoveResult(
    val item: SecureVaultItem,
    val sourceRemoved: Boolean,
)

/**
 * Stores the Safe Folder in app-private storage and encrypts both file contents and
 * the item index with AES-GCM. The key is derived from the user's PIN and a random salt.
 * Forgetting the PIN means the encrypted contents cannot be recovered.
 */
object SecureFolderRepository {
    private const val PREFS = "filepilot_safe_folder"
    private const val SALT_KEY = "pin_salt"
    private const val PBKDF2_ITERATIONS = 150_000
    private const val KEY_BITS = 256
    private const val GCM_NONCE_BYTES = 12
    private const val GCM_TAG_BITS = 128
    private const val INDEX_VERSION = 1

    private fun vaultDirectory(context: Context): File =
        File(context.filesDir, "filepilot_safe_vault").apply { mkdirs() }

    private fun indexFile(context: Context) = File(vaultDirectory(context), "index.enc")

    fun isConfigured(context: Context): Boolean {
        val salt = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(SALT_KEY, null)
        return !salt.isNullOrBlank() && indexFile(context).isFile
    }

    fun isValidPin(pin: String): Boolean = pin.length in 4..12 && pin.all { it in '0'..'9' }

    fun initialize(context: Context, pin: String): Result<List<SecureVaultItem>> = runCatching {
        require(isValidPin(pin)) { "PIN must contain 4 to 12 digits." }
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        check(!isConfigured(context)) { "Safe Folder is already configured." }

        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val key = deriveKey(pin, salt)
        val index = indexFile(context)
        writeEncryptedIndex(index, emptyList(), key)
        val stored = android.util.Base64.encodeToString(salt, android.util.Base64.NO_WRAP)
        if (!prefs.edit().putString(SALT_KEY, stored).commit()) {
            index.delete()
            error("Could not save the Safe Folder settings.")
        }
        emptyList()
    }

    fun unlock(context: Context, pin: String): Result<List<SecureVaultItem>> = runCatching {
        require(isValidPin(pin)) { "Enter a valid PIN." }
        val key = keyForPin(context, pin)
        readEncryptedIndex(indexFile(context), key)
    }

    fun addUri(
        context: Context,
        uri: Uri,
        displayName: String,
        mimeType: String?,
        pin: String,
    ): Result<SecureVaultItem> = runCatching {
        val key = keyForPin(context, pin)
        val current = readEncryptedIndex(indexFile(context), key)
        val safeName = displayName.substringAfterLast('/').substringAfterLast('\\').ifBlank { "file" }
        val id = java.util.UUID.randomUUID().toString()
        val destination = File(vaultDirectory(context), id + ".enc")
        val input = context.contentResolver.openInputStream(uri)
            ?: error("The selected file could not be opened.")
        val length = encryptToFile(input, destination, key)
        val item = SecureVaultItem(id, safeName, mimeType, length, System.currentTimeMillis())
        try {
            writeEncryptedIndex(indexFile(context), current + item, key)
        } catch (error: Throwable) {
            destination.delete()
            throw error
        }
        item
    }

    fun moveEntry(
        context: Context,
        entry: FileEntry,
        pin: String,
    ): Result<SecureMoveResult> = runCatching {
        val key = keyForPin(context, pin)
        val current = readEncryptedIndex(indexFile(context), key)
        val id = java.util.UUID.randomUUID().toString()
        val destination = File(vaultDirectory(context), id + ".enc")
        val input = openEntry(context, entry)
        val length = encryptToFile(input, destination, key)
        val item = SecureVaultItem(id, entry.name, entry.mimeType, length, System.currentTimeMillis())
        try {
            writeEncryptedIndex(indexFile(context), current + item, key)
        } catch (error: Throwable) {
            destination.delete()
            throw error
        }
        val removed = FileRepository.delete(context, entry).isSuccess
        SecureMoveResult(item, removed)
    }

    fun remove(context: Context, item: SecureVaultItem, pin: String): Result<List<SecureVaultItem>> = runCatching {
        val key = keyForPin(context, pin)
        val current = readEncryptedIndex(indexFile(context), key)
        val remaining = current.filterNot { it.id == item.id }
        check(remaining.size != current.size) { "Item not found." }
        writeEncryptedIndex(indexFile(context), remaining, key)
        File(vaultDirectory(context), item.id + ".enc").delete()
        remaining
    }

    fun restoreToUri(
        context: Context,
        item: SecureVaultItem,
        pin: String,
        destinationUri: Uri,
    ): Result<Unit> = runCatching {
        val key = keyForPin(context, pin)
        val current = readEncryptedIndex(indexFile(context), key)
        check(current.any { it.id == item.id }) { "Item not found." }
        val encrypted = File(vaultDirectory(context), item.id + ".enc")
        val output = context.contentResolver.openOutputStream(destinationUri, "wt")
            ?: error("The destination could not be opened.")
        output.use { stream ->
            decryptFileToStream(encrypted, stream, key)
        }
    }

    fun openEntry(context: Context, entry: FileEntry): InputStream {
        return if (entry.location.startsWith("content://")) {
            context.contentResolver.openInputStream(Uri.parse(entry.location))
                ?: error("The selected file could not be opened.")
        } else {
            val file = File(entry.location)
            require(file.isFile && file.canRead()) { "The selected file is not readable." }
            FileInputStream(file)
        }
    }

    private fun keyForPin(context: Context, pin: String): SecretKey {
        require(isValidPin(pin)) { "Enter a valid PIN." }
        val encodedSalt = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(SALT_KEY, null)
            ?: error("Safe Folder has not been configured.")
        val salt = android.util.Base64.decode(encodedSalt, android.util.Base64.NO_WRAP)
        return deriveKey(pin, salt)
    }

    private fun deriveKey(pin: String, salt: ByteArray): SecretKey {
        val spec = PBEKeySpec(pin.toCharArray(), salt, PBKDF2_ITERATIONS, KEY_BITS)
        return try {
            val encoded = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
            SecretKeySpec(encoded, "AES")
        } finally {
            spec.clearPassword()
        }
    }

    private fun newCipher(mode: Int, key: SecretKey, nonce: ByteArray): Cipher =
        Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(mode, key, GCMParameterSpec(GCM_TAG_BITS, nonce))
        }

    private fun encryptToFile(input: InputStream, target: File, key: SecretKey): Long {
        val nonce = ByteArray(GCM_NONCE_BYTES).also { SecureRandom().nextBytes(it) }
        var total = 0L
        try {
            input.use { source ->
                val raw = FileOutputStream(target)
                raw.write(nonce)
                val encrypted = CipherOutputStream(raw, newCipher(Cipher.ENCRYPT_MODE, key, nonce))
                try {
                    val buffer = ByteArray(32 * 1024)
                    while (true) {
                        val read = source.read(buffer)
                        if (read < 0) break
                        encrypted.write(buffer, 0, read)
                        total += read
                    }
                } finally {
                    encrypted.close()
                }
            }
        } catch (error: Throwable) {
            target.delete()
            throw error
        }
        return total
    }

    private fun decryptFileToStream(source: File, output: java.io.OutputStream, key: SecretKey) {
        FileInputStream(source).use { raw ->
            val nonce = ByteArray(GCM_NONCE_BYTES)
            var offset = 0
            while (offset < nonce.size) {
                val count = raw.read(nonce, offset, nonce.size - offset)
                if (count < 0) error("Encrypted file is damaged.")
                offset += count
            }
            val decrypted = CipherInputStream(raw, newCipher(Cipher.DECRYPT_MODE, key, nonce))
            decrypted.use { input -> input.copyTo(output, 32 * 1024) }
        }
    }

    private fun writeEncryptedIndex(file: File, items: List<SecureVaultItem>, key: SecretKey) {
        val plain = ByteArrayOutputStream().use { bytes ->
            DataOutputStream(bytes).use { data ->
                data.writeInt(INDEX_VERSION)
                data.writeInt(items.size)
                items.forEach { item ->
                    data.writeUTF(item.id)
                    data.writeUTF(item.name)
                    data.writeUTF(item.mimeType.orEmpty())
                    data.writeLong(item.sizeBytes)
                    data.writeLong(item.addedAt)
                }
            }
            bytes.toByteArray()
        }
        val nonce = ByteArray(GCM_NONCE_BYTES).also { SecureRandom().nextBytes(it) }
        val encrypted = newCipher(Cipher.ENCRYPT_MODE, key, nonce).doFinal(plain)
        val temporary = File(file.parentFile, "index.tmp")
        FileOutputStream(temporary).use { output ->
            output.write(nonce)
            output.write(encrypted)
            output.fd.sync()
        }
        if (file.exists() && !file.delete()) {
            temporary.delete()
            error("Could not update the Safe Folder index.")
        }
        if (!temporary.renameTo(file)) {
            temporary.delete()
            error("Could not save the Safe Folder index.")
        }
    }

    private fun readEncryptedIndex(file: File, key: SecretKey): List<SecureVaultItem> {
        require(file.isFile) { "Safe Folder index is missing." }
        val encrypted = file.readBytes()
        require(encrypted.size > GCM_NONCE_BYTES) { "Safe Folder index is damaged." }
        val nonce = encrypted.copyOfRange(0, GCM_NONCE_BYTES)
        val payload = encrypted.copyOfRange(GCM_NONCE_BYTES, encrypted.size)
        val plain = newCipher(Cipher.DECRYPT_MODE, key, nonce).doFinal(payload)
        DataInputStream(ByteArrayInputStream(plain)).use { data ->
            check(data.readInt() == INDEX_VERSION) { "Unsupported Safe Folder index." }
            val count = data.readInt()
            require(count in 0..100_000) { "Safe Folder index is invalid." }
            return List(count) {
                SecureVaultItem(
                    id = data.readUTF(),
                    name = data.readUTF(),
                    mimeType = data.readUTF().ifBlank { null },
                    sizeBytes = data.readLong(),
                    addedAt = data.readLong(),
                )
            }
        }
    }
}
