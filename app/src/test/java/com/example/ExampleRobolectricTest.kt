package com.example

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.example.data.db.ProxyNodeEntity
import com.example.engine.binder.BinderProxyEngine
import com.example.engine.deeplink.DeepLinkAndBridgeManager
import com.example.engine.network.ProxyRotationEngine
import com.example.engine.vfs.SandboxFileSystem
import com.example.engine.vfs.VirtualContextWrapper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ExampleRobolectricTest {

    @Test
    fun `app name matches CloneCore`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("CloneCore", appName)
    }

    @Test
    fun `virtual context isolates files and preferences per clone`() {
        val base = ApplicationProvider.getApplicationContext<Context>()
        val ctx1 = VirtualContextWrapper(base, "c_1111", "com.example.vapp.c_1111", "1111222233334444")
        val ctx2 = VirtualContextWrapper(base, "c_2222", "com.example.vapp.c_2222", "5555666677778888")

        assertNotEquals(ctx1.filesDir.absolutePath, ctx2.filesDir.absolutePath)
        assertEquals("com.example.vapp.c_1111", ctx1.packageName)

        SandboxFileSystem.writeCustomSandboxFile(base, "c_1111", "token.txt", "acct_one")
        val files1 = SandboxFileSystem.listSandboxFiles(base, "c_1111")
        val files2 = SandboxFileSystem.listSandboxFiles(base, "c_2222")

        assertTrue(files1.any { it.name == "token.txt" })
        assertFalse(files2.any { it.name == "token.txt" })
    }

    @Test
    fun `binder dynamic proxy remaps content authority and resolves virtual package`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val proxy = BinderProxyEngine.createVirtualPackageProxy(
            context = context,
            hostPackage = context.packageName,
            sourcePackage = "com.target.social",
            virtualPackage = "com.example.vapp.c_abc"
        )

        assertEquals("com.target.social", proxy.resolveVirtualPackage("com.example.vapp.c_abc"))
        val remapped = proxy.remapContentAuthority("c_abc", "content://com.target.social.provider/users")
        assertTrue(remapped.contains("vproc.c_abc.com.target.social.provider"))
    }

    @Test
    fun `deep link parser handles custom scheme and universal link`() {
        val customIntent = Intent(Intent.ACTION_VIEW, Uri.parse("clonecore://session/c_9988"))
        val httpsIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://clonecore.app/s/c_7766"))

        assertEquals("c_9988", DeepLinkAndBridgeManager.parseCloneIdFromIntent(customIntent))
        assertEquals("c_7766", DeepLinkAndBridgeManager.parseCloneIdFromIntent(httpsIntent))
    }

    @Test
    fun `proxy rotation engine rotates across active nodes by latency`() {
        val engine = ProxyRotationEngine()
        val nodes = listOf(
            ProxyNodeEntity("n1", "US", "SOCKS5", "10.0.0.1", 1080, null, null, "US", 45, 1L, true, 1, 0),
            ProxyNodeEntity("n2", "EU", "HTTP", "10.0.0.2", 8080, null, null, "EU", 90, 1L, true, 1, 0),
            ProxyNodeEntity("n3", "OFF", "SOCKS5", "10.0.0.3", 1080, null, null, "AP", 12, 1L, false, 0, 1)
        )
        val first = engine.selectNextRotationNode(nodes, "Direct")
        assertEquals("n1", first?.id)
        val second = engine.selectNextRotationNode(nodes, "SOCKS5://10.0.0.1:1080")
        assertEquals("n2", second?.id)
    }
}
