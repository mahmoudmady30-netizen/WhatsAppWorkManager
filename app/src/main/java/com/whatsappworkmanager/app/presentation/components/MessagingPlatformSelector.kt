package com.whatsappworkmanager.app.presentation.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.whatsappworkmanager.app.domain.model.MessagingPlatform

private fun MessagingPlatform.shortLabel(): String = when (this) {
    MessagingPlatform.WHATSAPP -> "WhatsApp"
    MessagingPlatform.WHATSAPP_BUSINESS -> "Business"
    MessagingPlatform.MESSENGER -> "Messenger"
}

@Composable
fun MessagingPlatformIcon(
    platform: MessagingPlatform,
    modifier: Modifier = Modifier,
    tint: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.primary
) {
    val icon = when (platform) {
        MessagingPlatform.WHATSAPP -> androidx.compose.material.icons.Icons.Filled.Chat
        MessagingPlatform.WHATSAPP_BUSINESS -> androidx.compose.material.icons.Icons.Filled.Business
        MessagingPlatform.MESSENGER -> androidx.compose.material.icons.Icons.Filled.Forum
    }
    Icon(icon, contentDescription = platform.shortLabel(), tint = tint, modifier = modifier)
}

@Composable
fun MessagingPlatformSelector(
    selected: MessagingPlatform,
    onSelected: (MessagingPlatform) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            MessagingPlatform.entries.forEach { platform ->
                val isSelected = selected == platform
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onSelected(platform) },
                    shape = RoundedCornerShape(14.dp),
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                    },
                    tonalElevation = if (isSelected) 3.dp else 0.dp
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 9.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Box(
                            modifier = Modifier.size(26.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            MessagingPlatformIcon(
                                platform = platform,
                                modifier = Modifier.size(21.dp),
                                tint = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary
                            )
                        }
                        Text(
                            text = platform.shortLabel(),
                            maxLines = 1,
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) MaterialTheme.colorScheme.onPrimary
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun NotificationSourceBadge(
    platform: MessagingPlatform? = null,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            if (platform == null) {
                Icon(Icons.Filled.Notifications, contentDescription = "Notifications", modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                Text("Notifications", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
            } else {
                MessagingPlatformIcon(platform, Modifier.size(14.dp))
                Text(platform.shortLabel(), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
fun MessagingPlatformsBadge(
    platforms: Set<MessagingPlatform>,
    modifier: Modifier = Modifier
) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
        if (platforms.isEmpty()) NotificationSourceBadge()
        else platforms.sortedBy { it.ordinal }.forEach { NotificationSourceBadge(it) }
    }
}

@Composable
fun MessagingPlatformBadge(
    platform: MessagingPlatform,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            MessagingPlatformIcon(platform, modifier = Modifier.size(15.dp))
            Text(
                text = platform.shortLabel(),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
