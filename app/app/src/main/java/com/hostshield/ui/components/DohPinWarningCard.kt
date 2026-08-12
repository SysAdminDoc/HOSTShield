package com.hostshield.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hostshield.R
import com.hostshield.service.DohPinFreshnessMonitor
import com.hostshield.service.DohPinManifest
import com.hostshield.ui.theme.Red
import com.hostshield.ui.theme.TextDim
import com.hostshield.ui.theme.Yellow

@Composable
fun DohPinWarningCard(
    warning: DohPinFreshnessMonitor.Warning,
    modifier: Modifier = Modifier
) {
    val expired = warning.freshness == DohPinManifest.Freshness.EXPIRED
    val accent = if (expired) Red else Yellow
    val title = stringResource(
        if (expired) R.string.warning_doh_pin_expired_title
        else R.string.warning_doh_pin_review_title
    )
    val message = stringResource(
        if (expired) R.string.warning_doh_pin_expired_message
        else R.string.warning_doh_pin_review_message,
        warning.providerLabel,
        warning.date
    )

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = accent.copy(alpha = 0.09f),
        modifier = modifier
            .fillMaxWidth()
            .semantics { contentDescription = "$title. $message" }
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                Icons.Filled.Warning,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(16.dp).padding(top = 2.dp)
            )
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    title,
                    color = accent,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.sp,
                    lineHeight = 16.sp
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    message,
                    color = TextDim,
                    fontSize = 11.sp,
                    lineHeight = 16.sp
                )
            }
        }
    }
}
