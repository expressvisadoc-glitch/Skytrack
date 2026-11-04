package com.skypass.skytrack.ui.home

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.view.MotionEvent
import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.graphics.createBitmap
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.skypass.skytrack.BuildConfig
import com.skypass.skytrack.R
import com.skypass.skytrack.data.location.GeofenceEvaluator
import com.skypass.skytrack.data.location.GeofenceMapData
import java.io.File
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.CopyrightOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polygon
import org.osmdroid.views.overlay.Polyline

private const val ZONE_COLOR = 0xFFE53935.toInt()
private const val INSIDE_COLOR = 0xFF059669.toInt()
private const val OUTSIDE_COLOR = 0xFF2563EB.toInt()

// Beyond this the view stays on the office instead of zooming out to include a far-away employee
private const val MAX_FIT_DISTANCE_M = 5000.0

/**
 * OpenStreetMap preview of the office geofence: the office, the allowed radius and the employee's own
 * position (location dot plus accuracy circle). Pinch to zoom; the page keeps scrolling outside the map.
 */
@Composable
fun GeofenceMap(data: GeofenceMapData, modifier: Modifier = Modifier) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val mapView = rememberMapView()

    DisposableEffect(lifecycleOwner, mapView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapView.onDetach()
        }
    }

    AndroidView(
        factory = { mapView },
        update = { view -> view.showGeofence(data) },
        modifier = modifier
            .fillMaxWidth()
            .height(190.dp)
            .clip(RoundedCornerShape(16.dp))
    )
}

@SuppressLint("ClickableViewAccessibility")
@Composable
private fun rememberMapView(): MapView {
    val context = androidx.compose.ui.platform.LocalContext.current
    return remember {
        // Tiles are cached in the app's cache folder; OpenStreetMap requires an identifying user agent
        Configuration.getInstance().apply {
            userAgentValue = BuildConfig.APPLICATION_ID
            val base = File(context.cacheDir, "osmdroid")
            osmdroidBasePath = base
            osmdroidTileCache = File(base, "tiles")
        }
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
            isTilesScaledToDpi = true
            minZoomLevel = 4.0
            maxZoomLevel = 19.0
            contentDescription = "Map of the office area and your location"
            overlays.add(CopyrightOverlay(context))
            // Keep gestures on the map instead of scrolling the Home screen
            setOnTouchListener { view, event ->
                if (event.action == MotionEvent.ACTION_DOWN) view.parent?.requestDisallowInterceptTouchEvent(true)
                false
            }
        }
    }
}

private fun MapView.showGeofence(data: GeofenceMapData) {
    if (tag == data) return
    tag = data
    val copyright = overlays.filterIsInstance<CopyrightOverlay>()
    overlays.clear()
    overlays.addAll(copyright)

    val density = resources.displayMetrics.density
    val office = GeoPoint(data.officeLatitude, data.officeLongitude)
    val radius = data.radiusMeters.toDouble()

    // Allowed zone: tinted area only, no border
    overlays.add(circle(office, radius).apply {
        fillPaint.color = withAlpha(ZONE_COLOR, 0.18f)
        outlinePaint.apply { color = 0; strokeWidth = 0f }
    })
    // Radius line to the eastern edge, with its length just outside the edge (readable for small radii)
    val edge = office.destinationPoint(radius, 90.0)
    overlays.add(Polyline(this).apply {
        setPoints(listOf(office, edge))
        outlinePaint.apply {
            color = withAlpha(ZONE_COLOR, 0.9f)
            strokeWidth = 1.5f * density
            pathEffect = DashPathEffect(floatArrayOf(5 * density, 4 * density), 0f)
        }
        setOnClickListener { _, _, _ -> true }
    })
    overlays.add(label(edge, "${data.radiusMeters} m"))

    var box = BoundingBox.fromGeoPoints(listOf(office.destinationPoint(radius, 0.0), office.destinationPoint(radius, 180.0), office.destinationPoint(radius, 90.0), office.destinationPoint(radius, 270.0)))

    if (data.hasUser) {
        val user = GeoPoint(data.userLatitude!!, data.userLongitude!!)
        val tone = if (data.isInside) INSIDE_COLOR else OUTSIDE_COLOR
        val accuracy = (data.userAccuracyMeters ?: 0f).toDouble()
        if (accuracy > 0) {
            overlays.add(circle(user, accuracy).apply {
                fillPaint.color = withAlpha(tone, 0.10f)
                outlinePaint.apply {
                    color = withAlpha(tone, 0.7f)
                    strokeWidth = 1.5f * density
                    pathEffect = DashPathEffect(floatArrayOf(4 * density, 4 * density), 0f)
                }
            })
        }
        overlays.add(marker(office, R.drawable.ic_map_office, ZONE_COLOR, 30, "Office"))
        overlays.add(locationDot(user, tone))
        val distance = GeofenceEvaluator.distanceMeters(office.latitude, office.longitude, user.latitude, user.longitude)
        if (distance <= MAX_FIT_DISTANCE_M) {
            val reach = accuracy.coerceAtMost(MAX_FIT_DISTANCE_M)
            box = box.concat(BoundingBox.fromGeoPoints(listOf(user.destinationPoint(reach, 0.0), user.destinationPoint(reach, 180.0), user.destinationPoint(reach, 90.0), user.destinationPoint(reach, 270.0))))
        }
    } else {
        overlays.add(marker(office, R.drawable.ic_map_office, ZONE_COLOR, 30, "Office"))
    }

    val target = box
    val border = (28 * density).toInt()
    if (width > 0 && height > 0) {
        zoomToBoundingBox(target, false, border)
    } else {
        addOnFirstLayoutListener { _, _, _, _, _ -> zoomToBoundingBox(target, false, border) }
    }
    invalidate()
}

private fun MapView.circle(center: GeoPoint, radius: Double) = Polygon(this).apply {
    points = Polygon.pointsAsCircle(center, radius)
    setOnClickListener { _, _, _ -> true }
}

private fun MapView.marker(point: GeoPoint, @DrawableRes icon: Int, color: Int, sizeDp: Int, title: String) = Marker(this).apply {
    position = point
    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
    this.icon = BitmapDrawable(resources, markerBitmap(context, icon, color, sizeDp))
    this.title = title
    setInfoWindow(null)
    setOnMarkerClickListener { _, _ -> true }
}

/** The employee's location: a dot in the status colour with a white edge (same as the web map). */
private fun MapView.locationDot(point: GeoPoint, color: Int) = Marker(this).apply {
    position = point
    icon = BitmapDrawable(resources, dotBitmap(context, color))
    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
    title = "You"
    setInfoWindow(null)
    setOnMarkerClickListener { _, _ -> true }
}

private fun dotBitmap(context: Context, color: Int): Bitmap {
    val density = context.resources.displayMetrics.density
    val radius = 9 * density
    val pad = 4 * density
    val size = ((radius + pad) * 2).toInt()
    val bitmap = createBitmap(size, size)
    val canvas = Canvas(bitmap)
    val c = size / 2f
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    paint.color = 0xFFFFFFFF.toInt()
    paint.setShadowLayer(3 * density, 0f, 1 * density, 0x66000000)
    canvas.drawCircle(c, c, radius, paint)
    paint.clearShadowLayer()
    paint.color = color
    canvas.drawCircle(c, c, radius - 3 * density, paint)
    return bitmap
}

private fun MapView.label(point: GeoPoint, text: String) = Marker(this).apply {
    position = point
    // Left edge slightly right of the point
    setAnchor(-0.08f, Marker.ANCHOR_CENTER)
    icon = BitmapDrawable(resources, labelBitmap(context, text))
    setInfoWindow(null)
    setOnMarkerClickListener { _, _ -> true }
}

/** Round badge: soft shadow, white ring, coloured disc and a white icon. */
private fun markerBitmap(context: Context, @DrawableRes iconRes: Int, color: Int, sizeDp: Int): Bitmap {
    val density = context.resources.displayMetrics.density
    val size = (sizeDp * density).toInt()
    val pad = (4 * density).toInt()
    val bitmap = createBitmap(size + pad * 2, size + pad * 2)
    val canvas = Canvas(bitmap)
    val cx = bitmap.width / 2f
    val cy = bitmap.height / 2f
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    paint.color = 0xFFFFFFFF.toInt()
    paint.setShadowLayer(3 * density, 0f, 1 * density, 0x55000000)
    canvas.drawCircle(cx, cy, size / 2f, paint)
    paint.clearShadowLayer()
    paint.color = color
    canvas.drawCircle(cx, cy, size / 2f - 2.5f * density, paint)
    ContextCompat.getDrawable(context, iconRes)?.let { drawable ->
        val iconSize = (size * 0.56f).toInt()
        val left = (cx - iconSize / 2f).toInt()
        val top = (cy - iconSize / 2f).toInt()
        drawable.setBounds(left, top, left + iconSize, top + iconSize)
        drawable.draw(canvas)
    }
    return bitmap
}

/** Small white pill with the radius length. */
private fun labelBitmap(context: Context, text: String): Bitmap {
    val density = context.resources.displayMetrics.density
    val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ZONE_COLOR
        textSize = 11 * density
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    val padH = 7 * density
    val padV = 3.5f * density
    val textWidth = textPaint.measureText(text)
    val metrics = textPaint.fontMetrics
    val width = (textWidth + padH * 2 + 4 * density).toInt()
    val height = (metrics.descent - metrics.ascent + padV * 2 + 4 * density).toInt()
    val bitmap = createBitmap(width, height)
    val canvas = Canvas(bitmap)
    val rect = RectF(2 * density, 2 * density, width - 2 * density, height - 2 * density)
    val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFFFFF.toInt()
        setShadowLayer(2 * density, 0f, 0.5f * density, 0x40000000)
    }
    canvas.drawRoundRect(rect, rect.height() / 2, rect.height() / 2, bg)
    val border = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1 * density
        color = withAlpha(ZONE_COLOR, 0.5f)
    }
    canvas.drawRoundRect(rect, rect.height() / 2, rect.height() / 2, border)
    canvas.drawText(text, rect.left + padH, rect.top + padV - metrics.ascent, textPaint)
    return bitmap
}

private fun withAlpha(color: Int, alpha: Float): Int = (color and 0x00FFFFFF) or ((alpha * 255).toInt() shl 24)
