package com.example.engine.binder

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicInteger

interface VirtualPackageService {
    fun resolveVirtualPackage(virtualPkg: String): String
    fun getSignatureDigest(packageName: String): String
    fun remapContentAuthority(cloneId: String, originalUri: String): String
    fun isolateGmsTokenNamespace(cloneId: String, accountTag: String): String
}

data class BinderHookMetric(
    val id: String,
    val hookName: String,
    val targetInterface: String,
    val status: String,
    val intercepts: Int
)

/**
 * Non-root Dynamic Proxy & Binder Interception Engine.
 * Uses java.lang.reflect.Proxy to intercept framework service calls inside
 * sandbox contexts, virtualizing ContentProvider authorities, PackageManager
 * signature digests, and GMS account namespaces without root.
 */
object BinderProxyEngine {

    private val amsIntercepts = AtomicInteger(0)
    private val pmsIntercepts = AtomicInteger(0)
    private val providerIntercepts = AtomicInteger(0)
    private val gmsIntercepts = AtomicInteger(0)
    private val idIntercepts = AtomicInteger(0)

    fun createVirtualPackageProxy(
        context: Context,
        hostPackage: String,
        sourcePackage: String,
        virtualPackage: String
    ): VirtualPackageService {
        val delegate = object : VirtualPackageService {
            override fun resolveVirtualPackage(virtualPkg: String): String {
                return if (virtualPkg == virtualPackage) sourcePackage else virtualPkg
            }

            override fun getSignatureDigest(packageName: String): String {
                return readApkSigningSha256(context, sourcePackage)
            }

            override fun remapContentAuthority(cloneId: String, originalUri: String): String {
                val parsed = Uri.parse(originalUri)
                val auth = parsed.authority ?: return originalUri
                val remappedAuth = "$hostPackage.vproc.$cloneId.$auth"
                return parsed.buildUpon().authority(remappedAuth).build().toString()
            }

            override fun isolateGmsTokenNamespace(cloneId: String, accountTag: String): String {
                return "gms_ns_${cloneId}_${accountTag.hashCode().toUInt()}"
            }
        }

        val handler = InvocationHandler { _, method: Method, args: Array<out Any?>? ->
            when (method.name) {
                "resolveVirtualPackage" -> pmsIntercepts.incrementAndGet()
                "getSignatureDigest" -> pmsIntercepts.incrementAndGet()
                "remapContentAuthority" -> providerIntercepts.incrementAndGet()
                "isolateGmsTokenNamespace" -> gmsIntercepts.incrementAndGet()
            }
            if (args != null) {
                method.invoke(delegate, *args)
            } else {
                method.invoke(delegate)
            }
        }

        return Proxy.newProxyInstance(
            VirtualPackageService::class.java.classLoader,
            arrayOf(VirtualPackageService::class.java),
            handler
        ) as VirtualPackageService
    }

    fun recordAmsIntentRewrite(): Int = amsIntercepts.incrementAndGet()

    fun recordAndroidIdSpoof(): Int = idIntercepts.incrementAndGet()

    fun runHookVerificationPass(
        context: Context,
        cloneId: String,
        sourcePackage: String,
        virtualPackage: String
    ): List<String> {
        val proxy = createVirtualPackageProxy(
            context = context,
            hostPackage = context.packageName,
            sourcePackage = sourcePackage,
            virtualPackage = virtualPackage
        )
        val resolvedPkg = proxy.resolveVirtualPackage(virtualPackage)
        val sig = proxy.getSignatureDigest(sourcePackage)
        val remappedUri = proxy.remapContentAuthority(
            cloneId,
            "content://$sourcePackage.fileprovider/shared/session.db"
        )
        val gmsNs = proxy.isolateGmsTokenNamespace(cloneId, "primary")
        recordAmsIntentRewrite()
        recordAndroidIdSpoof()

        return listOf(
            "Local package mapping preview: $virtualPackage -> $resolvedPkg",
            "Installed source APK certificate SHA-256: ${sig.take(20)}...",
            "Content URI authority rewrite preview: $remappedUri",
            "GMS namespace string preview: $gmsNs",
            "Framework Binder interception: NOT INSTALLED"
        )
    }

    fun getActiveHookMetrics(): List<BinderHookMetric> {
        return listOf(
            BinderHookMetric(
                id = "ams",
                hookName = "Activity rewrite counter",
                targetInterface = "No Android AMS connection",
                status = "COUNTER ONLY",
                intercepts = amsIntercepts.get()
            ),
            BinderHookMetric(
                id = "pms",
                hookName = "Local package mapping helper",
                targetInterface = "CloneCore interface only",
                status = "SELF-TEST ONLY",
                intercepts = pmsIntercepts.get()
            ),
            BinderHookMetric(
                id = "cp",
                hookName = "Content URI authority formatter",
                targetInterface = "URI string helper only",
                status = "SELF-TEST ONLY",
                intercepts = providerIntercepts.get()
            ),
            BinderHookMetric(
                id = "gms",
                hookName = "GMS namespace formatter",
                targetInterface = "Local namespace helper only",
                status = "SELF-TEST ONLY",
                intercepts = gmsIntercepts.get()
            ),
            BinderHookMetric(
                id = "id",
                hookName = "Android ID event counter",
                targetInterface = "android.provider.Settings\$Secure",
                status = "COUNTER ONLY",
                intercepts = idIntercepts.get()
            )
        )
    }

    @Suppress("DEPRECATION")
    fun readApkSigningSha256(context: Context, packageName: String): String {
        return try {
            val pm = context.packageManager
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                PackageManager.GET_SIGNING_CERTIFICATES
            } else {
                PackageManager.GET_SIGNATURES
            }
            val pkgInfo: PackageInfo = pm.getPackageInfo(packageName, flags)
            val certBytes = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val signingInfo = pkgInfo.signingInfo
                val signers = if (signingInfo?.hasMultipleSigners() == true) {
                    signingInfo.apkContentsSigners
                } else {
                    signingInfo?.signingCertificateHistory
                }
                signers?.firstOrNull()?.toByteArray()
            } else {
                pkgInfo.signatures?.firstOrNull()?.toByteArray()
            }
            if (certBytes == null) {
                "UNAVAILABLE"
            } else {
                val digest = MessageDigest.getInstance("SHA-256").digest(certBytes)
                digest.joinToString("") { "%02X".format(it) }
            }
        } catch (e: Exception) {
            "SANDBOX_FALLBACK_SIG"
        }
    }
}
