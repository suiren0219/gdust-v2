package com.menglimi.gdust.v2.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.security.keystore.KeyPermanentlyInvalidatedException
import java.io.FileOutputStream
import android.util.AtomicFile
import com.google.gson.JsonObject
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Optional personal-cloud credentials; encrypted with Android Keystore, excluded from backup. */
internal interface CloudMcpConfigStore {
    suspend fun load(): CloudMcpConfig
    suspend fun save(config: CloudMcpConfig)
}

internal class CloudMcpPrefs(context: Context) : CloudMcpConfigStore {
    private val app = context.applicationContext
    private val file get() = AtomicFile(File(app.noBackupFilesDir, FILE_NAME))

    override suspend fun load(): CloudMcpConfig = withContext(Dispatchers.IO) {
        synchronized(lock) {
            if (!file.baseFile.exists() && !File(file.baseFile.path + ".bak").exists()) return@synchronized CloudMcpConfig()
            try {
                val encrypted = file.openRead().use { it.readNBytes(MAX_CONFIG_BYTES + 1) }
                require(encrypted.size in (IV_BYTES + TAG_BYTES + 1)..MAX_CONFIG_BYTES)
                val cipher = Cipher.getInstance(TRANSFORMATION)
                cipher.init(Cipher.DECRYPT_MODE, key(create = false), GCMParameterSpec(TAG_BYTES * 8, encrypted, 0, IV_BYTES))
                val clear = cipher.doFinal(encrypted, IV_BYTES, encrypted.size - IV_BYTES)
                try {
                    val root = McpJson.objectFrom(clear.toString(Charsets.UTF_8), MAX_CONFIG_BYTES)
                    require(McpJson.int(root, "version", VERSION..VERSION) == VERSION)
                    CloudMcpConfig(McpJson.text(root, "url", 2048), McpJson.text(root, "token", 4096),
                        McpJson.bool(root, "enabled"))
                } finally { clear.fill(0) }
            } catch (_: Exception) {
                throw IllegalStateException("云端 MCP 配置无法解密，请重新填写。")
            }
        }
    }

    override suspend fun save(config: CloudMcpConfig) = withContext(Dispatchers.IO) { synchronized(lock) {
        val clear = JsonObject().apply {
            addProperty("version", VERSION); addProperty("url", config.url)
            addProperty("token", config.token); addProperty("enabled", config.enabled)
        }.toString().toByteArray(Charsets.UTF_8)
        require(clear.size < MAX_CONFIG_BYTES - IV_BYTES - TAG_BYTES)
        try {
            val cipher = encryptionCipher()
            val encrypted = cipher.iv + cipher.doFinal(clear)
            val stream = file.startWrite()
            try { stream.write(encrypted); file.finishWrite(stream) }
            catch (failure: Exception) { file.failWrite(stream); throw failure }
        } finally { clear.fill(0) }
    } }

    private fun key(create: Boolean = true): SecretKey {
        val ks = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (ks.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        check(create) { "原加密密钥不可用" }
        backupUnreadableConfig()
        return generateKey()
    }

    private fun encryptionCipher(): Cipher {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        try { cipher.init(Cipher.ENCRYPT_MODE, key()) }
        catch (_: KeyPermanentlyInvalidatedException) {
            // 只在用户重新填写并明确保存时恢复；加载失败不重建密钥或删除原密文。
            backupUnreadableConfig()
            cipher.init(Cipher.ENCRYPT_MODE, generateKey())
        }
        return cipher
    }

    private fun backupUnreadableConfig() {
        if (!file.baseFile.exists() && !File(file.baseFile.path + ".bak").exists()) return
        val bytes = file.openRead().use { it.readNBytes(MAX_CONFIG_BYTES + 1) }
        require(bytes.size <= MAX_CONFIG_BYTES)
        val copy = File.createTempFile("gdust_cloud_mcp_unreadable_", ".enc", app.noBackupFilesDir)
        FileOutputStream(copy).use { it.write(bytes); it.fd.sync() }
    }

    private fun generateKey(): SecretKey = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE).run {
            init(KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setKeySize(256).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).setRandomizedEncryptionRequired(true).build())
        generateKey()
    }

    companion object {
        private val lock = Any()
        private const val FILE_NAME = "gdust_cloud_mcp.enc"
        private const val KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "gdust_cloud_mcp_v1"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val VERSION = 1
        private const val IV_BYTES = 12
        private const val TAG_BYTES = 16
        private const val MAX_CONFIG_BYTES = 16 * 1024
    }
}
