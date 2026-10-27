package com.skypass.skytrack.ui.monthly

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
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

private val Slate900 = Color(0xFF0F172A)
private val Slate600 = Color(0xFF475569)
private val Slate500 = Color(0xFF64748B)
private val Slate400 = Color(0xFF94A3B8)
private val Slate200 = Color(0xFFE2E8F0)
private val Red50 = Color(0xFFFEF2F2)
private val Red700 = Color(0xFFB91C1C)

private fun monthLabel(month: String): String = runCatching {
    YearMonth.parse(month).format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH))
}.getOrDefault(month)

/** Payslips for locked payroll months; net amounts hidden until the eye icon is tapped. */
@Composable
fun PayslipsSection(
    state: PayslipsState,
    onOpen: (String) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showAmounts by remember { mutableStateOf(false) }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = Color.White,
        border = BorderStroke(1.dp, Slate200)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(36.dp).background(Red50, CircleShape), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Description, contentDescription = null, tint = Red700, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Payslips", color = Slate900, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Text("Issued once payroll for a month is locked", color = Slate500, fontSize = 12.sp)
                }
                if (state.payslips.isNotEmpty()) {
                    IconButton(onClick = { showAmounts = !showAmounts }) {
                        Icon(
                            if (showAmounts) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                            contentDescription = if (showAmounts) "Hide amounts" else "Show amounts",
                            tint = Slate600
                        )
                    }
                }
            }

            when {
                state.isLoading && state.payslips.isEmpty() -> Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = Red700)
                    Spacer(Modifier.width(8.dp))
                    Text("Loading payslips…", color = Slate500, fontSize = 13.sp)
                }
                state.payslips.isEmpty() && state.error == null ->
                    Text("No payslips yet. They appear here after payroll for a month is locked.", color = Slate500, fontSize = 13.sp)
                else -> state.payslips.forEach { item ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(monthLabel(item.month), color = Slate900, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                            val net = if (showAmounts) PayslipPdfGenerator.formatAmount(item.netPay) else "••••"
                            val revised = if (item.version > 1) " · revised" else ""
                            Text("${item.payslipNumber}$revised · Net $net", color = Slate400, fontSize = 11.sp)
                        }
                        OutlinedButton(
                            onClick = { onOpen(item.month) },
                            enabled = state.openingMonth == null,
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, Slate200),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                            modifier = Modifier.height(34.dp)
                        ) {
                            if (state.openingMonth == item.month) {
                                CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp, color = Red700)
                            } else {
                                Icon(Icons.Rounded.PictureAsPdf, contentDescription = null, tint = Red700, modifier = Modifier.size(16.dp))
                            }
                            Spacer(Modifier.width(6.dp))
                            Text("View", color = Slate900, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }

            state.error?.let { message ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(message, color = Red700, fontSize = 12.sp, modifier = Modifier.weight(1f))
                    TextButton(onClick = onRetry) { Text("Retry", color = Red700, fontWeight = FontWeight.Bold) }
                }
            }
        }
    }
}
