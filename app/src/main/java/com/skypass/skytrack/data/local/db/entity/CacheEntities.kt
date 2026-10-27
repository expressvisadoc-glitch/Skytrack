package com.skypass.skytrack.data.local.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.skypass.skytrack.data.remote.AttendanceDto
import com.skypass.skytrack.data.remote.LeaveBalanceDataDto
import com.skypass.skytrack.data.remote.LeaveBalanceItemDto
import com.skypass.skytrack.data.remote.LeaveDto
import com.skypass.skytrack.data.remote.MonthlyAttendanceDto
import com.skypass.skytrack.data.remote.MonthlyAttendanceSummary
import com.skypass.skytrack.data.remote.PublicHoliday
import java.time.LocalDate

@Entity(tableName = "attendance_cache")
data class AttendanceCacheEntity(
    @PrimaryKey
    val date: String,
    val employeeId: String? = null,
    val name: String? = null,
    val login: String? = null,
    val logout: String? = null,
    val hours: String? = null,
    val status: String? = null,
    val breakStartedAt: String? = null,
    val totalBreakSeconds: Long? = null,
    val workedMinutes: Int? = null,
    val breakMinutes: Int? = null,
    val isOnBreak: Boolean? = null,
    val updatedAt: Long = System.currentTimeMillis()
) {
    fun toDto(): AttendanceDto = AttendanceDto(
        date = date,
        employeeId = employeeId,
        name = name,
        login = login,
        logout = logout,
        hours = hours,
        status = status,
        breakStartedAt = breakStartedAt,
        totalBreakSeconds = totalBreakSeconds,
        workedMinutes = workedMinutes,
        breakMinutes = breakMinutes,
        isOnBreak = isOnBreak
    )

    companion object {
        fun fromDto(dto: AttendanceDto, fallbackDate: String = com.skypass.skytrack.util.AttendanceDates.today()): AttendanceCacheEntity {
            val dateKey = dto.date?.takeIf { it.isNotBlank() } ?: fallbackDate
            return AttendanceCacheEntity(
                date = dateKey,
                employeeId = dto.employeeId,
                name = dto.name,
                login = dto.login,
                logout = dto.logout,
                hours = dto.hours,
                status = dto.status,
                breakStartedAt = dto.breakStartedAt,
                totalBreakSeconds = dto.totalBreakSeconds,
                workedMinutes = dto.workedMinutes,
                breakMinutes = dto.breakMinutes,
                isOnBreak = dto.isOnBreak,
                updatedAt = System.currentTimeMillis()
            )
        }
    }
}

@Entity(tableName = "leave_history_cache")
data class LeaveHistoryCacheEntity(
    @PrimaryKey
    val id: String,
    val requestId: String? = null,
    val employeeId: String? = null,
    val name: String? = null,
    val from: String? = null,
    val to: String? = null,
    val type: String? = null,
    val reason: String? = null,
    val status: String? = null,
    val leaveDays: Float? = null,
    val paidLeaveDays: Float? = null,
    val lopDays: Float? = null,
    val managerComment: String? = null,
    val updatedAt: Long = System.currentTimeMillis()
) {
    fun toDto(): LeaveDto = LeaveDto(
        id = id,
        requestId = requestId,
        employeeId = employeeId,
        name = name,
        from = from,
        to = to,
        type = type,
        reason = reason,
        status = status,
        leaveDays = leaveDays,
        paidLeaveDays = paidLeaveDays,
        lopDays = lopDays,
        managerComment = managerComment
    )

    companion object {
        fun fromDto(dto: LeaveDto, index: Int = 0): LeaveHistoryCacheEntity {
            val primaryKey = dto.id?.takeIf { it.isNotBlank() }
                ?: dto.requestId?.takeIf { it.isNotBlank() }
                ?: "${dto.from}_${dto.to}_${dto.type}_$index"

            return LeaveHistoryCacheEntity(
                id = primaryKey,
                requestId = dto.requestId,
                employeeId = dto.employeeId,
                name = dto.name,
                from = dto.from,
                to = dto.to,
                type = dto.type,
                reason = dto.reason,
                status = dto.status,
                leaveDays = dto.leaveDays,
                paidLeaveDays = dto.paidLeaveDays,
                lopDays = dto.lopDays,
                managerComment = dto.managerComment,
                updatedAt = System.currentTimeMillis()
            )
        }
    }
}

@Entity(tableName = "leave_balance_cache")
data class LeaveBalanceCacheEntity(
    @PrimaryKey
    val id: Int = 1,
    val casualAllocated: Float? = null,
    val casualAdjusted: Float? = null,
    val casualUsed: Float? = null,
    val casualBalance: Float? = null,
    val sickAllocated: Float? = null,
    val sickAdjusted: Float? = null,
    val sickUsed: Float? = null,
    val sickBalance: Float? = null,
    val updatedAt: Long = System.currentTimeMillis()
) {
    fun toDto(): LeaveBalanceDataDto = LeaveBalanceDataDto(
        casual = LeaveBalanceItemDto(
            allocated = casualAllocated,
            adjusted = casualAdjusted,
            used = casualUsed,
            balance = casualBalance
        ),
        sick = LeaveBalanceItemDto(
            allocated = sickAllocated,
            adjusted = sickAdjusted,
            used = sickUsed,
            balance = sickBalance
        )
    )

    companion object {
        fun fromDto(dto: LeaveBalanceDataDto): LeaveBalanceCacheEntity = LeaveBalanceCacheEntity(
            casualAllocated = dto.casual?.allocated,
            casualAdjusted = dto.casual?.adjusted,
            casualUsed = dto.casual?.used,
            casualBalance = dto.casual?.balance,
            sickAllocated = dto.sick?.allocated,
            sickAdjusted = dto.sick?.adjusted,
            sickUsed = dto.sick?.used,
            sickBalance = dto.sick?.balance,
            updatedAt = System.currentTimeMillis()
        )
    }
}

@Entity(tableName = "holiday_cache")
data class HolidayCacheEntity(
    @PrimaryKey
    val date: String,
    val name: String,
    val category: String = "Public Holiday",
    val updatedAt: Long = System.currentTimeMillis()
) {
    fun toModel(): PublicHoliday? = try {
        PublicHoliday(
            date = LocalDate.parse(date),
            name = name,
            category = category
        )
    } catch (_: Exception) {
        null
    }

    companion object {
        fun fromModel(model: PublicHoliday): HolidayCacheEntity = HolidayCacheEntity(
            date = model.date.toString(),
            name = model.name,
            category = model.category,
            updatedAt = System.currentTimeMillis()
        )
    }
}

@Entity(tableName = "monthly_attendance_summary_cache")
data class MonthlyAttendanceSummaryCacheEntity(
    @PrimaryKey
    val month: String,
    val workingDays: Int,
    val presentDays: Int,
    val absentDays: Int,
    val incompleteDays: Int,
    val totalHours: String,
    val averageHours: String,
    val targetHoursFormatted: String? = null,
    val totalBreakFormatted: String? = null,
    val presentPercent: Int? = null,
    val totalWorkedMinutes: Int? = null,
    val totalBreakMinutes: Int? = null,
    val updatedAt: Long = System.currentTimeMillis()
) {
    fun toModel(): MonthlyAttendanceSummary = MonthlyAttendanceSummary(
        month = month,
        workingDays = workingDays,
        presentDays = presentDays,
        absentDays = absentDays,
        incompleteDays = incompleteDays,
        totalHours = totalHours,
        averageHours = averageHours,
        targetHoursFormatted = targetHoursFormatted,
        totalBreakFormatted = totalBreakFormatted,
        presentPercent = presentPercent,
        totalWorkedMinutes = totalWorkedMinutes,
        totalBreakMinutes = totalBreakMinutes
    )

    companion object {
        fun fromModel(summary: MonthlyAttendanceSummary): MonthlyAttendanceSummaryCacheEntity =
            MonthlyAttendanceSummaryCacheEntity(
                month = summary.month,
                workingDays = summary.workingDays,
                presentDays = summary.presentDays,
                absentDays = summary.absentDays,
                incompleteDays = summary.incompleteDays,
                totalHours = summary.totalHours,
                averageHours = summary.averageHours,
                targetHoursFormatted = summary.targetHoursFormatted,
                totalBreakFormatted = summary.totalBreakFormatted,
                presentPercent = summary.presentPercent,
                totalWorkedMinutes = summary.totalWorkedMinutes,
                totalBreakMinutes = summary.totalBreakMinutes,
                updatedAt = System.currentTimeMillis()
            )
    }
}

@Entity(tableName = "monthly_attendance_cache")
data class MonthlyAttendanceCacheEntity(
    @PrimaryKey
    val key: String, // month_date
    val month: String,
    val date: String,
    val employeeId: String? = null,
    val name: String? = null,
    val login: String? = null,
    val logout: String? = null,
    val hours: String? = null,
    val breakFormatted: String? = null,
    val status: String,
    val workedMinutes: Int? = null,
    val breakMinutes: Int? = null,
    val updatedAt: Long = System.currentTimeMillis()
) {
    fun toDto(): MonthlyAttendanceDto = MonthlyAttendanceDto(
        date = date,
        employeeId = employeeId,
        name = name,
        login = login,
        logout = logout,
        hours = hours,
        breakFormatted = breakFormatted,
        status = status,
        workedMinutes = workedMinutes,
        breakMinutes = breakMinutes
    )

    companion object {
        fun fromDto(month: String, dto: MonthlyAttendanceDto): MonthlyAttendanceCacheEntity =
            MonthlyAttendanceCacheEntity(
                key = "${month}_${dto.date}",
                month = month,
                date = dto.date,
                employeeId = dto.employeeId,
                name = dto.name,
                login = dto.login,
                logout = dto.logout,
                hours = dto.hours,
                breakFormatted = dto.breakFormatted,
                status = dto.status,
                workedMinutes = dto.workedMinutes,
                breakMinutes = dto.breakMinutes,
                updatedAt = System.currentTimeMillis()
            )
    }
}
