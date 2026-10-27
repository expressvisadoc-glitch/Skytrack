package com.skypass.skytrack.ui.monthly

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

// Design Palette
private val BrandRed = Color(0xFFEA384C)
private val SurfaceBase = Color(0xFFF1F5F9)
private val SurfaceCard = Color(0xFFFFFFFF)
private val Slate900 = Color(0xFF0F172A)
private val Slate800 = Color(0xFF1E293B)
private val Slate700 = Color(0xFF334155)
private val Slate600 = Color(0xFF475569)
private val Slate500 = Color(0xFF64748B)
private val Slate400 = Color(0xFF94A3B8)
private val Slate200 = Color(0xFFE2E8F0)
private val Slate100 = Color(0xFFF1F5F9)

@Composable
fun TimesheetPdfViewerDialog(
    pdfFile: File,
    monthTitle: String,
    employeeName: String,
    onDismiss: () -> Unit,
    // Also used for payslips; defaults keep the timesheet wording
    documentLabel: String = "Timesheet",
    shareSubject: String = "Monthly Timesheet - $monthTitle"
) {
    val context = LocalContext.current
    var pages by remember { mutableStateOf<List<Bitmap>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var renderError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(pdfFile) {
        isLoading = true
        renderError = null
        try {
            val renderedBitmaps = withContext(Dispatchers.IO) {
                renderPdfToBitmaps(pdfFile)
            }
            pages = renderedBitmaps
        } catch (e: Exception) {
            renderError = e.message ?: "Failed to render PDF preview"
        } finally {
            isLoading = false
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false
        )
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = SurfaceBase
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                // Top Application Bar
                TopPdfViewerBar(
                    documentLabel = documentLabel,
                    monthTitle = monthTitle,
                    employeeName = employeeName,
                    onClose = onDismiss,
                    onOpenExternal = {
                        openPdfInExternalViewer(context, pdfFile)
                    },
                    onShare = {
                        sharePdfFile(context, pdfFile, shareSubject, documentLabel)
                    }
                )

                // Main Document View Area
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    when {
                        isLoading -> {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                CircularProgressIndicator(
                                    color = BrandRed,
                                    modifier = Modifier.size(44.dp)
                                )
                                Text(
                                    text = "Rendering $documentLabel PDF...",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Slate600
                                )
                            }
                        }

                        renderError != null -> {
                            Column(
                                modifier = Modifier.padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Text(
                                    text = "Unable to preview document",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Slate800
                                )
                                Text(
                                    text = renderError ?: "",
                                    fontSize = 12.sp,
                                    color = Slate500
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Button(
                                    onClick = { openPdfInExternalViewer(context, pdfFile) },
                                    colors = ButtonDefaults.buttonColors(containerColor = BrandRed)
                                ) {
                                    Text("Open in External PDF App")
                                }
                            }
                        }

                        else -> {
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalArrangement = Arrangement.spacedBy(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                itemsIndexed(pages) { index, bitmap ->
                                    PdfPageCard(
                                        bitmap = bitmap,
                                        pageIndex = index + 1,
                                        totalPages = pages.size
                                    )
                                }
                            }
                        }
                    }
                }

                // Bottom Action Dock
                BottomPdfActionsDock(
                    onOpenExternal = {
                        openPdfInExternalViewer(context, pdfFile)
                    },
                    onShare = {
                        sharePdfFile(context, pdfFile, shareSubject, documentLabel)
                    }
                )
            }
        }
    }
}

@Composable
private fun TopPdfViewerBar(
    monthTitle: String,
    employeeName: String,
    onClose: () -> Unit,
    onOpenExternal: () -> Unit,
    onShare: () -> Unit,
    documentLabel: String = "Timesheet"
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = SurfaceCard,
        shadowElevation = 3.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                IconButton(
                    onClick = onClose,
                    modifier = Modifier
                        .size(36.dp)
                        .background(Slate100, CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Close",
                        tint = Slate800,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Column {
                    Text(
                        text = "$documentLabel Document",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Slate900,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "$employeeName • $monthTitle",
                        fontSize = 11.sp,
                        color = Slate500,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                IconButton(
                    onClick = onShare,
                    modifier = Modifier
                        .size(36.dp)
                        .background(Slate100, CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Share,
                        contentDescription = "Share",
                        tint = Slate700,
                        modifier = Modifier.size(18.dp)
                    )
                }

                IconButton(
                    onClick = onOpenExternal,
                    modifier = Modifier
                        .size(36.dp)
                        .background(Slate100, CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
                        contentDescription = "Open in PDF App",
                        tint = BrandRed,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun PdfPageCard(
    bitmap: Bitmap,
    pageIndex: Int,
    totalPages: Int
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth()
    ) {
        // Page Badge
        if (totalPages > 1) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Slate800,
                modifier = Modifier.padding(bottom = 6.dp)
            ) {
                Text(
                    text = "Page $pageIndex of $totalPages",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                )
            }
        }

        // Rendered Page Surface
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(elevation = 6.dp, shape = RoundedCornerShape(8.dp), spotColor = Color.Black.copy(alpha = 0.15f))
                .clip(RoundedCornerShape(8.dp))
                .border(BorderStroke(1.dp, Slate200), RoundedCornerShape(8.dp)),
            color = Color.White
        ) {
            val aspectRatio = bitmap.width.toFloat() / bitmap.height.toFloat().coerceAtLeast(1f)
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "Page $pageIndex",
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(aspectRatio)
            )
        }
    }
}

@Composable
private fun BottomPdfActionsDock(
    onOpenExternal: () -> Unit,
    onShare: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = SurfaceCard,
        shadowElevation = 8.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Share Secondary Button
            OutlinedButton(
                onClick = onShare,
                modifier = Modifier
                    .weight(1f)
                    .height(46.dp),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.5.dp, Slate200),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = Slate800
                )
            ) {
                Icon(
                    imageVector = Icons.Rounded.Share,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = Slate700
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Share",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            // Open in External App Primary Button
            Button(
                onClick = onOpenExternal,
                modifier = Modifier
                    .weight(1.3f)
                    .height(46.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = BrandRed
                ),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = Color.White
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Open PDF",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }
    }
}

/**
 * Renders all pages of a PDF file to a list of Android Bitmaps using standard PdfRenderer.
 */
private fun renderPdfToBitmaps(pdfFile: File): List<Bitmap> {
    val bitmaps = mutableListOf<Bitmap>()
    var pfd: ParcelFileDescriptor? = null
    var renderer: PdfRenderer? = null

    try {
        pfd = ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY)
        renderer = PdfRenderer(pfd)
        val pageCount = renderer.pageCount

        // 2.0x scale gives crisp text on mobile screens
        val scale = 2.0f

        for (i in 0 until pageCount) {
            val page = renderer.openPage(i)
            val width = (page.width * scale).toInt()
            val height = (page.height * scale).toInt()

            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            canvas.drawColor(AndroidColor.WHITE)

            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            page.close()

            bitmaps.add(bitmap)
        }
    } finally {
        try {
            renderer?.close()
        } catch (_: Exception) {}
        try {
            pfd?.close()
        } catch (_: Exception) {}
    }

    return bitmaps
}

/**
 * Launches an external PDF viewer intent using FileProvider.
 */
fun openPdfInExternalViewer(context: Context, file: File) {
    try {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/pdf")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        val chooser = Intent.createChooser(intent, "Open Timesheet PDF")
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    } catch (e: Exception) {
        Toast.makeText(
            context,
            "No PDF viewer application found on device. PDF saved to cache.",
            Toast.LENGTH_LONG
        ).show()
    }
}

/**
 * Shares the PDF file via Android Share Sheet using FileProvider.
 */
fun sharePdfFile(context: Context, file: File, subject: String, documentLabel: String = "Timesheet") {
    try {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(
                Intent.EXTRA_TEXT,
                if (documentLabel == "Timesheet") "Here is the SkyTrack Monthly Attendance Timesheet PDF." else "Here is the SkyTrack $documentLabel PDF."
            )
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        val chooser = Intent.createChooser(intent, "Share $documentLabel PDF")
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    } catch (e: Exception) {
        Toast.makeText(
            context,
            "Unable to share PDF: ${e.message}",
            Toast.LENGTH_SHORT
        ).show()
    }
}
