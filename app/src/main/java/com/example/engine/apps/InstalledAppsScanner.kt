package com.example.engine.apps

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.UUID
import java.util.zip.ZipFile

data class InstalledAppInfo(
    val packageName: String,
    val label: String,
    val versionName: String,
    val versionCode: Long,
    val targetSdk: Int,
    val minSdk: Int,
    val isSystemApp: Boolean,
    val abiList: String,
    val apkSizeMb: Float,
    val sourceApkPath: String,
    val icon: Drawable?
)

/**
 * Discovers real launchable applications via Android PackageManager (<queries> compliant)
 * and extracts ABI, targetSdk, APK size, and metadata without requiring QUERY_ALL_PACKAGES.
 */
object InstalledAppsScanner {

    private const val MAX_IMPORTED_APK_BYTES = 512L * 1024L * 1024L

    suspend fun scanLaunchableApps(context: Context): List<InstalledAppInfo> =
        withContext(Dispatchers.IO) {
            val pm = context.packageManager
            val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
            }
            val resolveInfos = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.queryIntentActivities(mainIntent, PackageManager.ResolveInfoFlags.of(0L))
            } else {
                @Suppress("DEPRECATION")
                pm.queryIntentActivities(mainIntent, 0)
            }

            val seen = HashSet<String>()
            val results = mutableListOf<InstalledAppInfo>()

            for (ri in resolveInfos) {
                val pkg = ri.activityInfo?.packageName ?: continue
                if (pkg == context.packageName || !seen.add(pkg)) continue

                try {
                    val pkgInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        pm.getPackageInfo(pkg, PackageManager.PackageInfoFlags.of(0L))
                    } else {
                        @Suppress("DEPRECATION")
                        pm.getPackageInfo(pkg, 0)
                    }
                    val appInfo = pkgInfo.applicationInfo ?: continue
                    val label = appInfo.loadLabel(pm).toString()
                    val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                    val apkFile = File(appInfo.sourceDir)
                    val apkSizeMb = if (apkFile.exists()) apkFile.length() / (1024f * 1024f) else 0f
                    val abis = detectNativeAbis(appInfo)
                    val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        pkgInfo.longVersionCode
                    } else {
                        @Suppress("DEPRECATION")
                        pkgInfo.versionCode.toLong()
                    }
                    val minSdk = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        appInfo.minSdkVersion
                    } else {
                        24
                    }

                    results.add(
                        InstalledAppInfo(
                            packageName = pkg,
                            label = label,
                            versionName = pkgInfo.versionName ?: "1.0",
                            versionCode = versionCode,
                            targetSdk = appInfo.targetSdkVersion,
                            minSdk = minSdk,
                            isSystemApp = isSystem,
                            abiList = abis,
                            apkSizeMb = apkSizeMb,
                            sourceApkPath = appInfo.sourceDir,
                            icon = runCatching { appInfo.loadIcon(pm) }.getOrNull()
                        )
                    )
                } catch (_: Exception) {
                }
            }

            results.sortedWith(compareBy<InstalledAppInfo> { it.isSystemApp }.thenBy { it.label.lowercase() })
        }

    suspend fun inspectExternalApkUri(context: Context, uri: Uri): InstalledAppInfo? =
        withContext(Dispatchers.IO) {
            val importDir = File(context.filesDir, "imported_apks").apply { mkdirs() }
            val tempFile = File(importDir, "incoming_${UUID.randomUUID()}.apk")
            var retainedFile: File? = null
            var completed = false
            try {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    tempFile.outputStream().use { output ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        var totalBytes = 0L
                        while (true) {
                            val read = input.read(buffer)
                            if (read < 0) break
                            totalBytes += read
                            if (totalBytes > MAX_IMPORTED_APK_BYTES) {
                                throw IOException("APK exceeds the 512 MiB import limit")
                            }
                            output.write(buffer, 0, read)
                        }
                    }
                } ?: return@withContext null

                val pm = context.packageManager
                val pkgInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    pm.getPackageArchiveInfo(tempFile.absolutePath, PackageManager.PackageInfoFlags.of(0L))
                } else {
                    @Suppress("DEPRECATION")
                    pm.getPackageArchiveInfo(tempFile.absolutePath, 0)
                } ?: return@withContext null

                val appInfo = pkgInfo.applicationInfo?.apply {
                    sourceDir = tempFile.absolutePath
                    publicSourceDir = tempFile.absolutePath
                } ?: return@withContext null

                val abis = detectAbisFromApkZip(tempFile.absolutePath)
                val label = appInfo.loadLabel(pm)?.toString()?.takeIf { it.isNotBlank() }
                    ?: pkgInfo.packageName
                val importedVersionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    pkgInfo.longVersionCode
                } else {
                    @Suppress("DEPRECATION")
                    pkgInfo.versionCode.toLong()
                }
                val importedSizeMb = tempFile.length() / (1024f * 1024f)
                val safePackage = pkgInfo.packageName.replace(Regex("[^A-Za-z0-9._-]"), "_")
                val retained = File(
                    importDir,
                    "${safePackage}_${importedVersionCode}_${UUID.randomUUID()}.apk"
                )
                if (!tempFile.renameTo(retained)) {
                    tempFile.copyTo(retained, overwrite = false)
                    tempFile.delete()
                }
                retainedFile = retained
                appInfo.sourceDir = retained.absolutePath
                appInfo.publicSourceDir = retained.absolutePath

                val inspected = InstalledAppInfo(
                    packageName = pkgInfo.packageName,
                    label = label,
                    versionName = pkgInfo.versionName ?: "1.0",
                    versionCode = importedVersionCode,
                    targetSdk = appInfo.targetSdkVersion,
                    minSdk = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) appInfo.minSdkVersion else 24,
                    isSystemApp = false,
                    abiList = abis,
                    apkSizeMb = importedSizeMb,
                    sourceApkPath = retained.absolutePath,
                    icon = runCatching { appInfo.loadIcon(pm) }.getOrNull()
                )
                completed = true
                inspected
            } catch (_: Exception) {
                null
            } finally {
                tempFile.delete()
                if (!completed) retainedFile?.delete()
            }
        }

    private fun detectNativeAbis(appInfo: ApplicationInfo): String {
        val libDir = appInfo.nativeLibraryDir?.let { File(it) }
        if (libDir != null && libDir.exists()) {
            val archFolder = libDir.name
            if (archFolder.contains("arm64")) return "arm64-v8a"
            if (archFolder.contains("arm")) return "armeabi-v7a"
            if (archFolder.contains("x86_64")) return "x86_64"
        }
        return detectAbisFromApkZip(appInfo.sourceDir)
    }

    private fun detectAbisFromApkZip(apkPath: String): String {
        return try {
            ZipFile(apkPath).use { zip ->
                val abis = linkedSetOf<String>()
                val entries = zip.entries()
                while (entries.hasMoreElements()) {
                    val name = entries.nextElement().name
                    if (name.startsWith("lib/")) {
                        val parts = name.split('/')
                        if (parts.size >= 2 && parts[1].isNotBlank()) {
                            abis.add(parts[1])
                        }
                    }
                }
                if (abis.isEmpty()) "noarch (DEX)" else abis.joinToString(", ")
            }
        } catch (_: Exception) {
            "arm64-v8a"
        }
    }
}
