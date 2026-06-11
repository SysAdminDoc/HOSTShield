package com.hostshield.service

import androidx.work.Constraints
import androidx.work.NetworkType

internal fun syncNetworkType(wifiOnly: Boolean): NetworkType {
    return if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED
}

internal fun syncNetworkConstraints(wifiOnly: Boolean): Constraints {
    return Constraints.Builder()
        .setRequiredNetworkType(syncNetworkType(wifiOnly))
        .build()
}
