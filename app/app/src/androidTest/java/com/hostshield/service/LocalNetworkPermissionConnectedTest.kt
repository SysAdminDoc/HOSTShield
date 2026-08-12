package com.hostshield.service

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.core.content.ContextCompat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LocalNetworkPermissionConnectedTest {

    @Test
    fun api37ManifestAndDnsPortExemptionArePresent() {
        assumeTrue("ACCESS_LOCAL_NETWORK starts on API 37", Build.VERSION.SDK_INT >= 37)

        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals(37, context.applicationInfo.targetSdkVersion)
        val packageInfo = context.packageManager.getPackageInfo(
            context.packageName,
            PackageManager.GET_PERMISSIONS
        )

        assertTrue(
            "API 37 manifest must declare ACCESS_LOCAL_NETWORK",
            packageInfo.requestedPermissions.orEmpty().contains(Manifest.permission.ACCESS_LOCAL_NETWORK)
        )
        assertFalse(
            "DNS traffic to port 53 is exempt from the local-network permission",
            localDnsRequiresLocalNetworkPermission(
                platformSdk = Build.VERSION.SDK_INT,
                targetSdk = 37,
                listenPort = 53
            )
        )
        assertTrue(
            "A non-53 LAN listener requires the permission for target SDK 37",
            localDnsRequiresLocalNetworkPermission(
                platformSdk = Build.VERSION.SDK_INT,
                targetSdk = 37,
                listenPort = LOCAL_DNS_DEFAULT_PORT
            )
        )
        assertTrue(
            "Port-53 DNS remains allowed by the service guard",
            LocalDnsServerService.hasLocalNetworkPermission(context, 53)
        )
        assertEquals(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_LOCAL_NETWORK
            ) == PackageManager.PERMISSION_GRANTED,
            LocalDnsServerService.hasLocalNetworkPermission(context, LOCAL_DNS_DEFAULT_PORT)
        )
    }
}
