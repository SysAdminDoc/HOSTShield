package com.hostshield.service

import android.content.pm.ServiceInfo
import android.os.Build

internal object ProtectionForegroundServiceTypes {
    fun runtimeType(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SYSTEM_EXEMPTED
        } else {
            0
        }
}
