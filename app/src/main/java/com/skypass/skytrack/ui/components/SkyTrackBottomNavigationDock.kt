package com.skypass.skytrack.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.DateRange
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

enum class SkyTrackNavTab {
    HOME,
    LEAVES,
    STATISTICS,
    PROFILE
}

private val DockBgColor = Color(0xFF1E2530)
private val BrandAccent = Color(0xFFEF4444)
private val InactiveTint = Color(0xFF8E95A5)

@Composable
fun SkyTrackBottomNavigationDock(
    currentTab: SkyTrackNavTab,
    modifier: Modifier = Modifier,
    onHomeClick: () -> Unit = {},
    onLeavesClick: () -> Unit = {},
    onAnalyticsClick: () -> Unit = {},
    onProfileClick: () -> Unit = {}
) {
    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier
                .widthIn(max = 340.dp)
                .shadow(
                    elevation = 24.dp,
                    shape = CircleShape,
                    spotColor = Color.Black.copy(alpha = 0.35f)
                )
                .clip(CircleShape)
                // Dark glass: slightly see-through with a light top edge
                .background(
                    androidx.compose.ui.graphics.Brush.verticalGradient(
                        listOf(DockBgColor.copy(alpha = 0.92f), DockBgColor.copy(alpha = 0.97f))
                    )
                )
                .border(
                    1.dp,
                    androidx.compose.ui.graphics.Brush.verticalGradient(
                        listOf(Color.White.copy(alpha = 0.22f), Color.White.copy(alpha = 0.04f))
                    ),
                    CircleShape
                )
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Home Tab
            NavDockItem(
                title = "Home",
                icon = Icons.Rounded.Home,
                isActive = currentTab == SkyTrackNavTab.HOME,
                onClick = onHomeClick
            )

            // Leaves Tab
            NavDockItem(
                title = "Leaves",
                icon = Icons.Rounded.DateRange,
                isActive = currentTab == SkyTrackNavTab.LEAVES,
                onClick = onLeavesClick
            )

            // Statistics Tab
            NavDockItem(
                title = "Stats",
                icon = Icons.Rounded.BarChart,
                isActive = currentTab == SkyTrackNavTab.STATISTICS,
                onClick = onAnalyticsClick
            )

            // Profile Tab
            NavDockItem(
                title = "Profile",
                icon = Icons.Rounded.Person,
                isActive = currentTab == SkyTrackNavTab.PROFILE,
                onClick = onProfileClick
            )
        }
    }
}

@Composable
private fun NavDockItem(
    title: String,
    icon: ImageVector,
    isActive: Boolean,
    onClick: () -> Unit
) {
    if (isActive) {
        Row(
            modifier = Modifier
                .shadow(
                    elevation = 2.dp,
                    shape = CircleShape,
                    spotColor = BrandAccent.copy(alpha = 0.3f)
                )
                .clip(CircleShape)
                .background(BrandAccent)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClick
                )
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = Color.White,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = title,
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.2.sp
            )
        }
    } else {
        IconButton(
            onClick = onClick,
            modifier = Modifier.size(40.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = InactiveTint,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
