package com.example.engine.deeplink

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.net.Uri
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.example.MainActivity
import com.example.data.db.CloneInstanceEntity
import org.json.JSONObject

object DeepLinkAndBridgeManager {

    const val EXTRA_CLONE_ID = "extra_clone_id"

    fun buildSessionDeepLink(cloneId: String): String {
        return "clonecore://session/$cloneId"
    }

    fun buildUniversalHttpsLink(cloneId: String): String {
        return "https://clonecore.app/s/$cloneId"
    }

    fun parseCloneIdFromIntent(intent: Intent?): String? {
        if (intent == null) return null
        intent.getStringExtra(EXTRA_CLONE_ID)?.takeIf { it.isNotBlank() }?.let { return it }

        val data: Uri = intent.data ?: return null
        if (data.scheme == "clonecore" && data.host == "session") {
            return data.pathSegments.firstOrNull()
        }
        if (data.scheme == "https" && data.host == "clonecore.app") {
            val segments = data.pathSegments
            if (segments.size >= 2 && segments[0] == "s") {
                return segments[1]
            }
        }
        return null
    }

    fun requestPinCloneShortcut(
        context: Context,
        clone: CloneInstanceEntity,
        appIcon: Drawable?
    ): Boolean {
        if (!ShortcutManagerCompat.isRequestPinShortcutSupported(context)) {
            return false
        }

        val launchIntent = Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            data = Uri.parse(clone.deepLinkUri)
            putExtra(EXTRA_CLONE_ID, clone.id)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val badgedBitmap = createBadgedShortcutBitmap(appIcon, clone.cloneIndex)
        val shortcut = ShortcutInfoCompat.Builder(context, "clone_${clone.id}")
            .setShortLabel(clone.cloneLabel.take(24))
            .setLongLabel("${clone.appLabel} (${clone.cloneLabel})")
            .setIcon(IconCompat.createWithBitmap(badgedBitmap))
            .setIntent(launchIntent)
            .build()

        return ShortcutManagerCompat.requestPinShortcut(context, shortcut, null)
    }

    fun buildCrossPlatformBridgePayload(
        clone: CloneInstanceEntity,
        relayPort: Int
    ): String {
        val json = JSONObject().apply {
            put("bridgeProtocol", "CloneCore-Universal-Bridge/1.0")
            put("cloneId", clone.id)
            put("cloneLabel", clone.cloneLabel)
            put("sourcePackage", clone.sourcePackageName)
            put("virtualPackage", clone.virtualPackageName)
            put("processSlot", ":sandbox_p${clone.slotIndex}")
            put("deepLinkUri", clone.deepLinkUri)
            put("universalLink", buildUniversalHttpsLink(clone.id))
            put(
                "adbLaunchCommand",
                "adb shell am start -a android.intent.action.VIEW -d \"${clone.deepLinkUri}\""
            )
            put("virtualAndroidId", clone.virtualAndroidId)
            put("proxyRelay", if (relayPort > 0) "127.0.0.1:$relayPort" else "disabled")
            put("upstreamProxy", clone.currentProxyLabel)
        }
        return json.toString(2)
    }

    private fun createBadgedShortcutBitmap(drawable: Drawable?, index: Int): Bitmap {
        val size = 192
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(Color.parseColor("#0B111E"))

        if (drawable != null) {
            drawable.setBounds(18, 18, size - 18, size - 18)
            drawable.draw(canvas)
        } else {
            val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#00E5FF")
                style = Paint.Style.STROKE
                strokeWidth = 10f
            }
            canvas.drawRoundRect(RectF(32f, 32f, size - 32f, size - 32f), 28f, 28f, p)
        }

        // Draw clone index badge at bottom-right
        val badgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#00E5FF")
        }
        canvas.drawCircle(size - 38f, size - 38f, 30f, badgePaint)

        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#080D16")
            textSize = 28f
            textAlign = Paint.Align.CENTER
            isFakeBoldText = true
        }
        canvas.drawText("#$index", size - 38f, size - 28f, textPaint)
        return bmp
    }
}
