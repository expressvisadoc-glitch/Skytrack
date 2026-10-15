package com.skypass.skytrack.data.remote

import com.google.gson.annotations.SerializedName

data class LoginRequest(
    @SerializedName("action") val action: String = "login",
    @SerializedName("employeeId") val employeeId: String,
    @SerializedName("password") val password: String
)

data class LoginResponse(
    @SerializedName("success") val success: Boolean,
    @SerializedName("message") val message: String? = null,
    @SerializedName("error") val error: String? = null,
    @SerializedName("session") val session: SessionDto? = null,
    @SerializedName("employee") val employee: EmployeeDto? = null
)

data class SessionDto(
    @SerializedName("token") val token: String,
    @SerializedName("expiresInSeconds") val expiresInSeconds: Long
)

data class EmployeeDto(
    @SerializedName("employeeId") val employeeId: String,
    @SerializedName("name") val name: String,
    @SerializedName("role") val role: String,
    @SerializedName("managerName") val managerName: String? = null,
    @SerializedName("departmentName") val departmentName: String? = null,
    @SerializedName("workShiftName") val workShiftName: String? = null,
    @SerializedName("attendancePercent") val attendancePercent: String? = null,
    @SerializedName("leaveQuota") val leaveQuota: String? = null,
    @SerializedName("ytdOvertime") val ytdOvertime: String? = null,
    @SerializedName("createdAt", alternate = ["created_at"])
    val createdAt: String? = null,
    @SerializedName("joiningDate", alternate = ["joining_date"])
    val joiningDate: String? = null,
    @SerializedName("profilePhotoUrl", alternate = ["profile_photo_url", "avatar"])
    val profilePhotoUrl: String? = null
)

data class AttendanceRequest(
    @SerializedName("action") val action: String,
    @SerializedName("token") val token: String
)

data class AttendanceResponse(
    @SerializedName("success") val success: Boolean,
    @SerializedName("message") val message: String? = null,
    @SerializedName("error") val error: String? = null,
    @SerializedName("attendance") val attendance: AttendanceDto? = null
)

data class AttendanceDto(
    val date: String? = null,
    val employeeId: String? = null,
    val name: String? = null,
    @com.google.gson.annotations.SerializedName("checkIn")
    val login: String? = null,
    @com.google.gson.annotations.SerializedName("checkOut")
    val logout: String? = null,
    val hours: String? = null,
    val status: String? = null,
    val breakStartedAt: String? = null,
    val totalBreakSeconds: Long? = null,
    val workedMinutes: Int? = null,
    val breakMinutes: Int? = null,
    val isOnBreak: Boolean? = null
)

data class LeaveSubmitRequest(
    @SerializedName("action") val action: String = "leave_submit",
    @SerializedName("token") val token: String,
    @SerializedName("from") val from: String,
    @SerializedName("to") val to: String,
    @SerializedName("type") val type: String,
    @SerializedName("reason") val reason: String
)

data class LeaveResponse(
    @SerializedName("success") val success: Boolean,
    @SerializedName("message") val message: String? = null,
    @SerializedName("error") val error: String? = null,
    @SerializedName("leave") val leave: LeaveDto? = null,
    @SerializedName("leaves") val leaves: List<LeaveDto>? = null
)

data class LeaveDto(
    @SerializedName("id") val id: String? = null,
    @SerializedName("requestId", alternate = ["request_id"]) val requestId: String? = null,
    @SerializedName("employeeId", alternate = ["employee_id"]) val employeeId: String? = null,
    @SerializedName("name") val name: String? = null,
    @SerializedName("from", alternate = ["from_date", "fromDate"]) val from: String? = null,
    @SerializedName("to", alternate = ["to_date", "toDate"]) val to: String? = null,
    @SerializedName("type", alternate = ["leave_type", "leaveType"]) val type: String? = null,
    @SerializedName("reason") val reason: String? = null,
    @SerializedName("status") val status: String? = null,
    @SerializedName("leaveDays", alternate = ["leave_days"]) val leaveDays: Float? = null,
    @SerializedName("paidLeaveDays", alternate = ["paid_leave_days"]) val paidLeaveDays: Float? = null,
    @SerializedName("lopDays", alternate = ["lop_days"]) val lopDays: Float? = null,
    @SerializedName("managerComment", alternate = ["manager_comment"]) val managerComment: String? = null
)

data class LeaveBalanceRequest(
    @SerializedName("action") val action: String = "leave_balance"
)

data class LeaveHistoryRequest(
    @SerializedName("action") val action: String = "leave_history"
)

data class CancelLeaveRequest(
    @SerializedName("action") val action: String = "cancel_leave",
    @SerializedName("requestId", alternate = ["request_id", "id"]) val requestId: String
)

data class CancelLeaveResponse(
    @SerializedName("success") val success: Boolean,
    @SerializedName("message") val message: String? = null,
    @SerializedName("error") val error: String? = null,
    @SerializedName("leave") val leave: LeaveDto? = null
)

data class LeaveBalanceItemDto(
    @SerializedName("allocated") val allocated: Float? = null,
    @SerializedName("adjusted") val adjusted: Float? = null,
    @SerializedName("used") val used: Float? = null,
    @SerializedName("balance") val balance: Float? = null
)

data class LeaveBalanceDataDto(
    @SerializedName("casual") val casual: LeaveBalanceItemDto? = null,
    @SerializedName("sick") val sick: LeaveBalanceItemDto? = null
)

data class LeaveBalanceResponse(
    @SerializedName("success") val success: Boolean,
    @SerializedName("message") val message: String? = null,
    @SerializedName("error") val error: String? = null,
    @SerializedName("balance") val balance: LeaveBalanceDataDto? = null
)

data class MonthlyAttendanceRequest(
    @SerializedName("action") val action: String = "attendance_monthly",
    @SerializedName("token") val token: String,
    @SerializedName("month") val month: String
)

data class MonthlyAttendanceResponse(
    val success: Boolean,
    val message: String? = null,
    val error: String? = null,
    val summary: MonthlyAttendanceSummary? = null,
    val attendance: List<MonthlyAttendanceDto>? = null,
    val data: MonthlyAttendanceDataDto? = null,
    val employee: MonthlyEmployeeDto? = null
) {
    val actualSummary: MonthlyAttendanceSummary? get() = data?.summary ?: summary
    val actualAttendance: List<MonthlyAttendanceDto> get() = data?.attendance ?: attendance ?: emptyList()
}

data class MonthlyEmployeeDto(
    val id: String? = null,
    @SerializedName("employeeId", alternate = ["employee_id"]) val employeeId: String? = null,
    val name: String? = null,
    @SerializedName("createdAt", alternate = ["created_at"]) val createdAt: String? = null,
    @SerializedName("joiningDate", alternate = ["joining_date"]) val joiningDate: String? = null
)

data class MonthlyAttendanceDataDto(
    val summary: MonthlyAttendanceSummary? = null,
    val attendance: List<MonthlyAttendanceDto> = emptyList()
)

data class MonthlyAttendanceSummary(
    val month: String,
    @SerializedName("workingDays", alternate = ["working_days", "required_working_days"]) val workingDays: Int,
    @SerializedName("presentDays", alternate = ["present_days"]) val presentDays: Int,
    @SerializedName("absentDays", alternate = ["absent_days"]) val absentDays: Int,
    @SerializedName("incompleteDays", alternate = ["incomplete_days"]) val incompleteDays: Int,
    @SerializedName("leaveDays", alternate = ["leave_days", "approved_leave_days"]) val leaveDays: Int? = null,
    @SerializedName("totalHours", alternate = ["total_hours", "total_active_formatted"]) val totalHours: String,
    @SerializedName("averageHours", alternate = ["average_hours", "average_active_formatted"]) val averageHours: String,
    @SerializedName("targetHoursFormatted", alternate = ["target_hours_formatted", "required_hours_formatted"]) val targetHoursFormatted: String? = null,
    @SerializedName("totalBreakFormatted", alternate = ["total_break_formatted"]) val totalBreakFormatted: String? = null,
    @SerializedName("overtimeFormatted", alternate = ["overtime_formatted"]) val overtimeFormatted: String? = null,
    @SerializedName("differenceFormatted", alternate = ["difference_formatted"]) val differenceFormatted: String? = null,
    @SerializedName("presentPercent", alternate = ["present_percent"]) val presentPercent: Int? = null,
    val totalWorkedMinutes: Int? = null,
    val totalBreakMinutes: Int? = null
)

data class MonthlyAttendanceDto(
    val date: String,
    @SerializedName("employeeId", alternate = ["employee_id"]) val employeeId: String? = null,
    val name: String? = null,
    @SerializedName("login") val login: String? = null,
    @SerializedName("logout") val logout: String? = null,
    @SerializedName("check_in") val rawCheckIn: String? = null,
    @SerializedName("check_out") val rawCheckOut: String? = null,
    @SerializedName("hours", alternate = ["active_formatted", "worked_hours"]) val hours: String? = null,
    @SerializedName("breakFormatted", alternate = ["break_formatted"]) val breakFormatted: String? = null,
    val status: String,
    val workedMinutes: Int? = null,
    val breakMinutes: Int? = null
)

data class HolidayDto(
    val id: String? = null,
    val date: String? = null,
    val name: String? = null,
    val category: String? = null
)

data class HolidayResponse(
    val success: Boolean,
    val message: String? = null,
    val error: String? = null,
    val holidays: List<HolidayDto>? = null
)

data class PublicHoliday(
    val date: java.time.LocalDate,
    val name: String,
    val category: String = "Public Holiday"
)

data class UpdateProfilePhotoRequest(
    val action: String = "update_profile_photo",
    val photo: String? = null
)

data class UpdateProfilePhotoResponse(
    val success: Boolean,
    val message: String? = null,
    val error: String? = null,
    @SerializedName("profilePhotoUrl", alternate = ["profile_photo_url"])
    val profilePhotoUrl: String? = null
)

data class ProfileResponse(
    val success: Boolean,
    val message: String? = null,
    val error: String? = null,
    val employee: EmployeeDto? = null
)


