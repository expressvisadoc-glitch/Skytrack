package com.skypass.skytrack.data.remote

import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Url

interface SkyTrackApi {

    @POST
    suspend fun login(
        @Url endpoint: String,
        @Body request: LoginRequest
    ): LoginResponse

    @POST
    suspend fun startWork(
        @Url endpoint: String,
        @Header("Authorization") authorization: String,
        @Body request: AttendanceRequest
    ): AttendanceResponse

    @POST
    suspend fun endWork(
        @Url endpoint: String,
        @Header("Authorization") authorization: String,
        @Body request: AttendanceRequest
    ): AttendanceResponse

    @POST
    suspend fun startBreak(
        @Url endpoint: String,
        @Header("Authorization") authorization: String,
        @Body request: AttendanceRequest
    ): AttendanceResponse

    @POST
    suspend fun resumeWork(
        @Url endpoint: String,
        @Header("Authorization") authorization: String,
        @Body request: AttendanceRequest
    ): AttendanceResponse

    @POST
    suspend fun salarySummary(
        @Url endpoint: String,
        @Header("Authorization") authorization: String,
        @Body request: SalarySummaryRequest
    ): SalarySummaryResponse

    @POST
    suspend fun geofenceConfig(
        @Url endpoint: String,
        @Header("Authorization") authorization: String,
        @Body request: AttendanceRequest
    ): GeofenceConfigResponse

    @POST
    suspend fun attendanceToday(
        @Url endpoint: String,
        @Header("Authorization") authorization: String,
        @Body request: AttendanceRequest
    ): AttendanceResponse

    @POST
    suspend fun submitLeave(
        @Url endpoint: String,
        @Header("Authorization") authorization: String,
        @Body request: LeaveSubmitRequest
    ): LeaveResponse

    @POST
    suspend fun leaveHistory(
        @Url endpoint: String,
        @Header("Authorization") authorization: String,
        @Body request: LeaveHistoryRequest
    ): LeaveResponse

    @POST
    suspend fun leaveBalance(
        @Url endpoint: String,
        @Header("Authorization") authorization: String,
        @Body request: LeaveBalanceRequest
    ): LeaveBalanceResponse

    @POST
    suspend fun cancelLeave(
        @Url endpoint: String,
        @Header("Authorization") authorization: String,
        @Body request: CancelLeaveRequest
    ): CancelLeaveResponse

    @POST
    suspend fun attendanceMonthly(
        @Url endpoint: String,
        @Header("Authorization") authorization: String,
        @Body request: MonthlyAttendanceRequest
    ): MonthlyAttendanceResponse

    @POST
    suspend fun publicHolidays(
        @Url endpoint: String,
        @Header("Authorization") authorization: String,
        @Body request: AttendanceRequest
    ): HolidayResponse

    @POST
    suspend fun updateProfilePhoto(
        @Url endpoint: String,
        @Header("Authorization") authorization: String,
        @Body request: UpdateProfilePhotoRequest
    ): UpdateProfilePhotoResponse

    @POST
    suspend fun getProfile(
        @Url endpoint: String,
        @Header("Authorization") authorization: String,
        @Body request: AttendanceRequest
    ): ProfileResponse
}

