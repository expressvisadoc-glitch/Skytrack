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
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.skypass.skytrack.data.remote.SalaryDto
import java.text.NumberFormat
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

private val rupeeFormat: NumberFormat = NumberFormat.getCurrencyInstance(Locale("en", "IN")).apply {
    maximumFractionDigits = 0
}

internal fun formatRupees(amount: Double?): String = amount?.let { rupeeFormat.format(it) } ?: "—"

internal fun formatDayCount(days: Double?): String {
    val d = days ?: 0.0
    return if (d % 1.0 == 0.0) d.toInt().toString() else String.format(Locale.ENGLISH, "%.2f", d).trimEnd('0').trimEnd('.')
}

internal fun salaryStatusMessage(salary: SalaryDto): String? = when (salary.status) {
    "ok" -> null
    "no_salary" -> "Salary not set yet. Please contact your administrator."
    "not_employed" -> "You were not employed during this month."
    "no_working_days" -> "This month has no working days."
    else -> "Salary could not be calculated."
}

private val dayTypeLabels = mapOf(
    "absent" to "Absent (no check-in)",
    "lop_leave" to "Unpaid (LOP) leave",
    "late" to "Late check-in"
)

@Composable
fun SalaryCard(
    state: SalaryCardState,
    onToggle: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showBreakdown by remember { mutableStateOf(false) }
    val salary = state.salary
    val monthLabel = salary?.month?.let {
        runCatching { YearMonth.parse(it).format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH)) }.getOrNull()
    } ?: "This month"

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = CardSurface,
        border = BorderStroke(1.dp, Slate200)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(36.dp).background(Emerald50, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Rounded.AccountBalanceWallet, contentDescription = null, tint = Emerald600, modifier = Modifier.size(20.dp))
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("This month's salary", color = Slate900, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Text(
                        if (state.isRevealed) "$monthLabel · provisional" else "Hidden",
                        color = Slate500,
                        fontSize = 12.sp
                    )
                }
                IconButton(onClick = {
                    showBreakdown = false
                    onToggle()
                }) {
                    Icon(
                        if (state.isRevealed) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                        contentDescription = if (state.isRevealed) "Hide salary" else "Show salary",
                        tint = Slate600
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            when {
                !state.isRevealed -> Text("₹ • • • • •", color = Slate300, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
                state.isLoading && salary == null -> Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = Emerald600)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Calculating…", color = Slate500, fontSize = 13.sp)
                }
                state.error != null -> Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(state.error, color = BrandDarkColor, fontSize = 12.sp, modifier = Modifier.weight(1f))
                    TextButton(onClick = onRetry) { Text("Retry", color = BrandDarkColor, fontWeight = FontWeight.Bold) }
                }
                salary != null && salaryStatusMessage(salary) != null ->
                    Text(salaryStatusMessage(salary).orEmpty(), color = Slate600, fontSize = 13.sp)
                salary != null -> {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Expected net pay", color = Slate500, fontSize = 12.sp)
                            Text(formatRupees(salary.netPay), color = Slate900, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("Gross ${formatRupees(salary.monthlyGross)}", color = Slate500, fontSize = 12.sp)
                            Text("Deductions −${formatRupees(salary.totalDeductions)}", color = BrandDarkColor, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                    TextButton(onClick = { showBreakdown = !showBreakdown }, modifier = Modifier.padding(top = 2.dp)) {
                        Text(if (showBreakdown) "Hide breakdown" else "View breakdown", color = BrandColor, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    if (showBreakdown) {
                        SalaryBreakdown(salary)
                    }
                }
            }
        }
    }
}

@Composable
private fun SalaryBreakdown(salary: SalaryDto) {
    val lateRule = if (salary.lateMarksPerUnit != null && salary.lateLopDaysPerUnit != null) {
        " (${salary.lateMarksPerUnit} = ${formatDayCount(salary.lateLopDaysPerUnit)} day)"
    } else ""
    val lines = buildList {
        add(Triple("Monthly gross", "${formatDayCount(salary.workingDaysInMonth)} working days", formatRupees(salary.monthlyGross)))
        if (salary.earnedGross != null && salary.earnedGross != salary.monthlyGross) {
            add(Triple("Payable for days employed", "${formatDayCount(salary.paidWorkingDays)} of ${formatDayCount(salary.workingDaysInMonth)} days", formatRupees(salary.earnedGross)))
        }
        add(Triple("Absences", "${formatDayCount(salary.absenceDays)} day(s)", "−${formatRupees(salary.absenceAmount)}"))
        add(Triple("Unpaid (LOP) leave", "${formatDayCount(salary.lopLeaveDays)} day(s)", "−${formatRupees(salary.lopLeaveAmount)}"))
        add(Triple("Late check-ins", "${salary.lateMarks ?: 0} mark(s)$lateRule → ${formatDayCount(salary.lateLopDays)} day", "−${formatRupees(salary.lateAmount)}"))
    }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        lines.forEach { (label, detail, amount) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(label, color = Slate800, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Text(detail, color = Slate500, fontSize = 11.sp)
                }
                Text(amount, color = if (amount.startsWith("−")) BrandDarkColor else Slate800, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Expected net pay", color = Slate900, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text(formatRupees(salary.netPay), color = Emerald600, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
        }

        val days = salary.days.orEmpty()
        if (days.isNotEmpty()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text("DAYS AFFECTING PAY", color = Slate400, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            days.forEach { day ->
                val dateLabel = runCatching {
                    LocalDate.parse(day.date).format(DateTimeFormatter.ofPattern("EEE, d MMM", Locale.ENGLISH))
                }.getOrDefault(day.date)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Slate50, RoundedCornerShape(10.dp))
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("$dateLabel · ${dayTypeLabels[day.type] ?: day.type}", color = Slate700, fontSize = 12.sp, modifier = Modifier.weight(1f))
                    Text(
                        if (day.type == "late") "late mark" else "−${formatRupees(day.amount)}",
                        color = Slate500,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
        Text(
            "Provisional until payroll is locked. Remaining working days are assumed worked; today counts once it is over.",
            color = Slate400,
            fontSize = 11.sp,
            lineHeight = 14.sp
        )
    }
}
