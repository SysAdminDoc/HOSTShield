package com.hostshield.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RootUtilTest {

    @Test
    fun `selectSystemlessHostsPath returns first supported module path`() {
        assertEquals(
            "/data/adb/modules/hosts/system/etc/hosts",
            RootUtil.selectSystemlessHostsPath(
                listOf(
                    "",
                    "/data/adb/modules/hosts/system/etc/hosts",
                    "/data/adb/modules/bindhosts/system/etc/hosts"
                )
            )
        )
    }

    @Test
    fun `selectSystemlessHostsPath supports bindhosts and KernelSU module paths`() {
        assertEquals(
            "/data/adb/modules/bindhosts/system/etc/hosts",
            RootUtil.selectSystemlessHostsPath(listOf("/data/adb/modules/bindhosts/system/etc/hosts"))
        )
        assertEquals(
            "/data/adb/modules/systemless-hosts-KernelSU-module/system/etc/hosts",
            RootUtil.selectSystemlessHostsPath(
                listOf("/data/adb/modules/systemless-hosts-KernelSU-module/system/etc/hosts")
            )
        )
    }

    @Test
    fun `selectSystemlessHostsPath ignores unsupported paths`() {
        assertNull(RootUtil.selectSystemlessHostsPath(listOf("/system/etc/hosts", "yes", "unknown")))
    }
}
