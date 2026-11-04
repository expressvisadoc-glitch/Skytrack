package com.skypass.skytrack.ui.notifications

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.NotificationsNone
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.skypass.skytrack.data.local.Session
import com.skypass.skytrack.data.remote.NotificationDto
import com.skypass.skytrack.ui.home.BrandColor
import com.skypass.skytrack.ui.home.CardSurface
import com.skypass.skytrack.ui.home.Slate100
import com.skypass.skytrack.ui.home.Slate400
import com.skypass.skytrack.ui.home.Slate500
import com.skypass.skytrack.ui.home.Slate600
import com.skypass.skytrack.ui.home.Slate700
import com.skypass.skytrack.ui.home.Slate900
import com.skypass.skytrack.ui.home.SurfaceBase
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun NotificationsScreen(
    session: Session,
    viewModel: NotificationsViewModel,
    onBack: () -> Unit,
    onOpenScreen: (String) -> Unit
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(session.token) {
        viewModel.open(session.token)
    }
    BackHandler(onBack = onBack)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(SurfaceBase)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp)
                .padding(bottom = 24.dp)
        ) {
            NotificationsTopBar(onBack = onBack)

            Spacer(modifier = Modifier.height(16.dp))

            when {
                state.isLoading && state.items.isEmpty() -> {
                    Box(modifier = Modifier.fillMaxWidth().padding(top = 48.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = BrandColor, strokeWidth = 2.dp, modifier = Modifier.size(28.dp))
                    }
                }
                state.error != null && state.items.isEmpty() -> {
                    EmptyMessage(
                        title = "Notifications unavailable",
                        message = state.error.orEmpty(),
                        actionLabel = "Try again",
                        onAction = { viewModel.open(session.token) }
                    )
                }
                state.items.isEmpty() -> {
                    EmptyMessage(
                        title = "No notifications yet",
                        message = "Shift reminders and updates about your attendance, leave and salary will appear here."
                    )
                }
                else -> {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        state.items.forEach { item ->
                            NotificationRow(item = item, onClick = { onOpenScreen(item.data?.screen ?: "home") })
                        }
                    }
                    if (state.hasMore) {
                        Spacer(modifier = Modifier.height(14.dp))
                        OutlinedButton(
                            onClick = { viewModel.loadMore(session.token) },
                            enabled = !state.isLoadingMore,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, Slate100)
                        ) {
                            Text(
                                text = if (state.isLoadingMore) "Loading…" else "Show older",
                                color = Slate700,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NotificationsTopBar(onBack: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Surface(
            onClick = onBack,
            modifier = Modifier.size(40.dp),
            shape = RoundedCornerShape(16.dp),
            color = CardSurface,
            border = BorderStroke(1.dp, Slate100),
            shadowElevation = 1.dp
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = "Back",
                    tint = Slate700,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        Column {
            Text(
                text = "INBOX",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = Slate400,
                letterSpacing = 0.8.sp
            )
            Text(
                text = "Notifications",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Slate900,
                letterSpacing = (-0.4).sp
            )
        }
    }
}

@Composable
private fun NotificationRow(item: NotificationDto, onClick: () -> Unit) {
    val unread = item.readAt == null
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = CardSurface,
        border = BorderStroke(1.dp, Slate100),
        shadowElevation = 1.dp
    ) {
        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.Top) {
            Box(
                modifier = Modifier
                    .padding(top = 6.dp)
                    .size(8.dp)
                    .background(if (unread) BrandColor else Slate100, CircleShape)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = item.title.orEmpty(),
                        modifier = Modifier.weight(1f),
                        color = Slate900,
                        fontSize = 14.sp,
                        fontWeight = if (unread) FontWeight.Bold else FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = formatNotificationTime(item.createdAt),
                        color = Slate400,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = item.body.orEmpty(),
                    color = Slate600,
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )
            }
        }
    }
}

@Composable
private fun EmptyMessage(title: String, message: String, actionLabel: String? = null, onAction: () -> Unit = {}) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 48.dp, start = 12.dp, end = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .background(CardSurface, RoundedCornerShape(16.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.NotificationsNone,
                contentDescription = null,
                tint = Slate400,
                modifier = Modifier.size(26.dp)
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        Text(text = title, color = Slate900, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(4.dp))
        Text(text = message, color = Slate500, fontSize = 13.sp, textAlign = TextAlign.Center, lineHeight = 18.sp)
        if (actionLabel != null) {
            Spacer(modifier = Modifier.height(14.dp))
            OutlinedButton(onClick = onAction, shape = RoundedCornerShape(14.dp), border = BorderStroke(1.dp, Slate100)) {
                Text(text = actionLabel, color = Slate700, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

/** "Just now", "12 min ago", "3 h ago", then the local date and time */
internal fun formatNotificationTime(createdAt: String?, now: Instant = Instant.now(), zone: ZoneId = ZoneId.systemDefault()): String {
    val instant = createdAt?.let { runCatching { Instant.parse(it) }.getOrNull() ?: runCatching { java.time.OffsetDateTime.parse(it).toInstant() }.getOrNull() }
        ?: return ""
    val minutes = Duration.between(instant, now).toMinutes()
    return when {
        minutes < 1 -> "Just now"
        minutes < 60 -> "$minutes min ago"
        minutes < 24 * 60 -> "${minutes / 60} h ago"
        else -> DateTimeFormatter.ofPattern("d MMM, h:mm a", Locale.ENGLISH).withZone(zone).format(instant)
    }
}
