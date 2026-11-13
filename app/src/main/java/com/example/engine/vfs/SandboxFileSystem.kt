package com.example.engine.vfs

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import android.database.DatabaseErrorHandler
import android.database.sqlite.SQLiteDatabase
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

data class SandboxFileNode(
    val name: String,
    val relativePath: String,
    val isDirectory: Boolean,
    val sizeBytes: Long,
    val lastModified: Long
)

data class SandboxStorageStats(
    val rootPath: String,
    val totalBytes: Long,
    val filesCount: Int,
    val prefsCount: Int,
    val dbCount: Int,
    val cacheBytes: Long
)

/**
 * VirtualContextWrapper redirects all app-private storage, SharedPreferences,
 * SQLite database paths, and cache directories into an isolated per-clone
 * sandbox root without requiring root permissions.
 */
class VirtualContextWrapper(
    base: Context,
    private val cloneId: String,
    private val virtualPackageName: String,
    val virtualAndroidId: String
) : ContextWrapper(base) {

    private val sandboxRoot: File = SandboxFileSystem.ensureSandboxRoot(base, cloneId)
    private val vfsFilesDir = File(sandboxRoot, "data/files").apply { mkdirs() }
    private val vfsCacheDir = File(sandboxRoot, "data/cache").apply { mkdirs() }
    private val vfsCodeCacheDir = File(sandboxRoot, "data/code_cache").apply { mkdirs() }
    private val vfsNoBackupDir = File(sandboxRoot, "data/no_backup").apply { mkdirs() }
    private val vfsDbDir = File(sandboxRoot, "data/databases").apply { mkdirs() }

    override fun getFilesDir(): File = vfsFilesDir

    override fun getCacheDir(): File = vfsCacheDir

    override fun getCodeCacheDir(): File = vfsCodeCacheDir

    override fun getNoBackupFilesDir(): File = vfsNoBackupDir

    override fun getDataDir(): File = File(sandboxRoot, "data").apply { mkdirs() }

    override fun getDir(name: String?, mode: Int): File {
        val safeName = (name ?: "default").replace(Regex("[^a-zA-Z0-9._-]"), "_")
        return File(sandboxRoot, "data/app_$safeName").apply { mkdirs() }
    }

    override fun getDatabasePath(name: String?): File {
        val safeName = (name ?: "sandbox.db").substringAfterLast('/')
        return File(vfsDbDir, safeName)
    }

    override fun openOrCreateDatabase(
        name: String?,
        mode: Int,
        factory: SQLiteDatabase.CursorFactory?
    ): SQLiteDatabase {
        return SQLiteDatabase.openOrCreateDatabase(getDatabasePath(name), factory)
    }

    override fun openOrCreateDatabase(
        name: String?,
        mode: Int,
        factory: SQLiteDatabase.CursorFactory?,
        errorHandler: DatabaseErrorHandler?
    ): SQLiteDatabase {
        return SQLiteDatabase.openOrCreateDatabase(getDatabasePath(name).absolutePath, factory, errorHandler)
    }

    override fun getSharedPreferences(name: String?, mode: Int): SharedPreferences {
        val safeName = (name ?: "default").replace(Regex("[^a-zA-Z0-9._-]"), "_")
        return super.getSharedPreferences("vfs_${cloneId}_$safeName", MODE_PRIVATE)
    }

    override fun openFileInput(name: String?): FileInputStream {
        val target = File(vfsFilesDir, (name ?: "data.bin").substringAfterLast('/'))
        return FileInputStream(target)
    }

    override fun openFileOutput(name: String?, mode: Int): FileOutputStream {
        val target = File(vfsFilesDir, (name ?: "data.bin").substringAfterLast('/'))
        val append = (mode and MODE_APPEND) != 0
        return FileOutputStream(target, append)
    }

    override fun deleteFile(name: String?): Boolean {
        val target = File(vfsFilesDir, (name ?: return false).substringAfterLast('/'))
        return target.delete()
    }

    override fun fileList(): Array<String> {
        return vfsFilesDir.list() ?: emptyArray()
    }

    override fun getPackageName(): String = virtualPackageName
}

object SandboxFileSystem {

    private val SUB_DIRS = listOf(
        "data/files",
        "data/cache",
        "data/code_cache",
        "data/no_backup",
        "data/shared_prefs",
        "data/databases",
        "lib/arm64-v8a"
    )

    fun getSandboxBaseDir(context: Context): File {
        return File(context.filesDir, "sandboxes").apply { mkdirs() }
    }

    fun getSandboxRoot(context: Context, cloneId: String): File {
        return File(getSandboxBaseDir(context), cloneId)
    }

    fun ensureSandboxRoot(context: Context, cloneId: String): File {
        val root = getSandboxRoot(context, cloneId)
        if (!root.exists()) root.mkdirs()
        SUB_DIRS.forEach { rel ->
            File(root, rel).mkdirs()
        }
        return root
    }

    fun initializeSandboxManifest(
        context: Context,
        cloneId: String,
        sourcePackage: String,
        virtualPackage: String,
        virtualAndroidId: String,
        abi: String
    ): File {
        val root = ensureSandboxRoot(context, cloneId)
        val manifestFile = File(root, "vfs_manifest.json")
        val json = JSONObject().apply {
            put("cloneId", cloneId)
            put("sourcePackage", sourcePackage)
            put("virtualPackage", virtualPackage)
            put("virtualAndroidId", virtualAndroidId)
            put("abi", abi)
            put("vfsIsolation", "USER_SPACE_REDIRECT_V2")
            put("createdAt", System.currentTimeMillis())
        }
        manifestFile.writeText(json.toString(2))

        val envFile = File(root, "data/files/sandbox_env.properties")
        if (!envFile.exists()) {
            envFile.writeText(
                buildString {
                    appendLine("SANDBOX_ID=$cloneId")
                    appendLine("TARGET_PKG=$sourcePackage")
                    appendLine("VIRTUAL_PKG=$virtualPackage")
                    appendLine("ANDROID_ID=$virtualAndroidId")
                    appendLine("ABI_LOCK=$abi")
                }
            )
        }
        return root
    }

    fun writeSessionTokenFile(context: Context, cloneId: String, slotIndex: Int, pid: Int) {
        val root = ensureSandboxRoot(context, cloneId)
        val sessionFile = File(root, "data/files/active_session.lock")
        sessionFile.writeText(
            "slot=sandbox_p$slotIndex\npid=$pid\ntimestamp=${System.currentTimeMillis()}\n"
        )
    }

    fun writeCustomSandboxFile(context: Context, cloneId: String, fileName: String, content: String): File {
        val root = ensureSandboxRoot(context, cloneId)
        val cleanName = fileName.replace(Regex("[^a-zA-Z0-9._-]"), "_")
        val file = File(root, "data/files/$cleanName")
        file.writeText(content)
        return file
    }

    fun listSandboxFiles(context: Context, cloneId: String): List<SandboxFileNode> {
        val root = getSandboxRoot(context, cloneId)
        if (!root.exists()) return emptyList()
        val result = mutableListOf<SandboxFileNode>()
        root.walkTopDown()
            .filter { it != root }
            .take(120)
            .forEach { file ->
                val rel = file.relativeTo(root).path
                result.add(
                    SandboxFileNode(
                        name = file.name,
                        relativePath = rel,
                        isDirectory = file.isDirectory,
                        sizeBytes = if (file.isFile) file.length() else 0L,
                        lastModified = file.lastModified()
                    )
                )
            }
        return result.sortedWith(compareBy<SandboxFileNode> { !it.isDirectory }.thenBy { it.relativePath })
    }

    fun computeStorageStats(context: Context, cloneId: String): SandboxStorageStats {
        val root = getSandboxRoot(context, cloneId)
        if (!root.exists()) {
            return SandboxStorageStats(root.absolutePath, 0L, 0, 0, 0, 0L)
        }
        var totalBytes = 0L
        var filesCount = 0
        var prefsCount = 0
        var dbCount = 0
        var cacheBytes = 0L

        root.walkTopDown().filter { it.isFile }.forEach { f ->
            val size = f.length()
            totalBytes += size
            filesCount++
            val rel = f.relativeTo(root).path
            if (rel.startsWith("data/cache") || rel.startsWith("data/code_cache")) {
                cacheBytes += size
            }
            if (rel.startsWith("data/shared_prefs")) prefsCount++
            if (rel.startsWith("data/databases")) dbCount++
        }
        return SandboxStorageStats(
            rootPath = root.absolutePath,
            totalBytes = totalBytes,
            filesCount = filesCount,
            prefsCount = prefsCount,
            dbCount = dbCount,
            cacheBytes = cacheBytes
        )
    }

    fun wipeSandboxCache(context: Context, cloneId: String): Long {
        val root = getSandboxRoot(context, cloneId)
        var freed = 0L
        listOf("data/cache", "data/code_cache").forEach { sub ->
            val dir = File(root, sub)
            if (dir.exists()) {
                dir.walkTopDown().filter { it.isFile }.forEach { f ->
                    freed += f.length()
                    f.delete()
                }
            }
        }
        return freed
    }

    fun deleteSandbox(context: Context, cloneId: String): Boolean {
        val root = getSandboxRoot(context, cloneId)
        return if (root.exists()) root.deleteRecursively() else true
    }
}
