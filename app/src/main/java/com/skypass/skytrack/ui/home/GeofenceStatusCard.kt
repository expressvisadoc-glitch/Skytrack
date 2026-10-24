package com.skypass.skytrack.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.GpsNotFixed
import androidx.compose.material.icons.rounded.LocationOff
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.skypass.skytrack.data.location.GeofenceFeedback
import com.skypass.skytrack.data.location.GeofenceFeedback.Kind

private data class GeofenceCardContent(
    val title: String,
    val message: String,
    val icon: ImageVector,
    val tint: Color,
    val background: Color
)

private fun GeofenceFeedback.toContent(): GeofenceCardContent = when (kind) {
    Kind.CHECKING -> GeofenceCardContent(
        "Checking your location…",
        "Comparing your position with the office location.",
        Icons.Rounded.GpsNotFixed, Slate600, Slate50
    )
    Kind.INSIDE -> GeofenceCardContent(
        "You appear to be at the office",
        "About $distanceMeters m from the office (allowed within $radiusMeters m, accuracy ±$accuracyMeters m). " +
            "Your location is verified again when you check in or out.",
        Icons.Rounded.LocationOn, Emerald600, Emerald50
    )
    Kind.OUTSIDE -> GeofenceCardContent(
        "You appear to be outside the office area",
        "About $distanceMeters m from the office; check-in and check-out are allowed within $radiusMeters m. " +
            "Breaks are not affected.",
        Icons.Rounded.LocationOff, BrandDarkColor, BrandSubtleColor
    )
    Kind.POOR_ACCURACY -> GeofenceCardContent(
        "Location accuracy is low",
        "Current accuracy is ±$accuracyMeters m; ±$limit m or better is needed. " +
            "Move near a window or outdoors, turn on Wi-Fi and precise location, then retry.",
        Icons.Rounded.WarningAmber, Amber600, Amber50
    )
    Kind.STALE_LOCATION -> GeofenceCardContent(
        "Location is out of date",
        "Your last location is older than $limit seconds. Tap Retry for a fresh reading.",
        Icons.Rounded.WarningAmber, Amber600, Amber50
    )
    Kind.MOCK_LOCATION -> GeofenceCardContent(
        "Mock location detected",
        "Disable any mock location apps to check in or check out.",
        Icons.Rounded.WarningAmber, BrandDarkColor, BrandSubtleColor
    )
    Kind.NOT_CONFIGURED -> GeofenceCardContent(
        "Office location not set up",
        "Your administrator has not configured the office location yet, so check-in and check-out are unavailable.",
        Icons.Rounded.LocationOff, Amber600, Amber50
    )
    Kind.INACTIVE -> GeofenceCardContent(
        "Location verification is disabled",
        "Your administrator has disabled office location verification, so check-in and check-out are unavailable.",
        Icons.Rounded.LocationOff, Amber600, Amber50
    )
    Kind.PERMISSION_REQUIRED -> GeofenceCardContent(
        "Location permission needed",
        "SkyTrack uses your location only when you check in, check out or take a break. It never tracks you in the background.",
        Icons.Rounded.LocationOff, Amber600, Amber50
    )
    Kind.SERVICES_DISABLED -> GeofenceCardContent(
        "Location services are off",
        "Turn on location services to check in or check out.",
        Icons.Rounded.LocationOff, Amber600, Amber50
    )
    Kind.TIMEOUT -> GeofenceCardContent(
        "Couldn't get your location in time",
        "Move to an open area or near a window, then retry.",
        Icons.Rounded.GpsNotFixed, Amber600, Amber50
    )
    Kind.LOCATION_UNAVAILABLE -> GeofenceCardContent(
        "Location unavailable",
        "Your device could not determine its location. Check that location is on, then retry.",
        Icons.Rounded.GpsNotFixed, Amber600, Amber50
    )
    Kind.CONFIG_UNAVAILABLE -> GeofenceCardContent(
        "Couldn't load office location settings",
        detail ?: "Please check your connection and retry.",
        Icons.Rounded.CloudOff, Amber600, Amber50
    )
    Kind.OFFLINE -> GeofenceCardContent(
        "You're offline",
        "Check-in and check-out need an internet connection so the server can verify your location.",
        Icons.Rounded.CloudOff, Slate600, Slate50
    )
}

@Composable
fun GeofenceStatusCard(
    feedback: GeofenceFeedback,
    onRetry: () -> Unit,
    onRequestPermission: () -> Unit,
    onOpenAppSettings: () -> Unit,
    onOpenLocationSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val content = feedback.toContent()

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = CardSurface,
        border = BorderStroke(1.dp, Slate200)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.Top
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(content.background, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                if (feedback.kind == Kind.CHECKING) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = content.tint
                    )
                } else {
                    Icon(
                        imageVector = content.icon,
                        contentDescription = null,
                        tint = content.tint,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = content.title,
                    color = Slate900,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = content.message,
                    color = Slate600,
                    fontSize = 12.sp,
                    lineHeight = 16.sp
                )

                val actions = when (feedback.kind) {
                    Kind.CHECKING, Kind.INSIDE -> emptyList()
                    Kind.PERMISSION_REQUIRED -> listOf(
                        "Allow location" to onRequestPermission,
                        "App settings" to onOpenAppSettings
                    )
                    Kind.SERVICES_DISABLED -> listOf(
                        "Turn on location" to onOpenLocationSettings,
                        "Retry" to onRetry
                    )
                    else -> listOf("Retry" to onRetry)
                }
                if (actions.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        actions.forEach { (label, action) ->
                            OutlinedButton(
                                onClick = action,
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, Slate300),
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                modifier = Modifier.height(32.dp)
                            ) {
                                Text(text = label, color = Slate800, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }
        }
    }
}
