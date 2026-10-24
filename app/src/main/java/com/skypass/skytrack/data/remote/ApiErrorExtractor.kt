package com.skypass.skytrack.data.remote

import com.google.gson.JsonParser
import retrofit2.HttpException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

object ApiErrorExtractor {

    /**
     * Extracts a user-friendly error message from any Throwable.
     * Prevents raw HTTP status codes (e.g. 401, 500) from being displayed to the user.
     */
    fun getMessage(throwable: Throwable, defaultMessage: String = "An unexpected error occurred. Please try again."): String {
        return when (throwable) {
            is UnknownHostException, is ConnectException ->
                "Unable to connect to SkyTrack server. Check your internet connection."

            is SocketTimeoutException ->
                "Connection timed out. Please try again."

            is HttpException -> {
                val parsed = extractFromHttpException(throwable)
                parsed ?: mapHttpCode(throwable.code(), defaultMessage)
            }

            else -> {
                val raw = throwable.message.orEmpty().trim()
                if (raw.isBlank()) return defaultMessage
                cleanErrorString(raw, defaultMessage)
            }
        }
    }

    fun extractFromHttpException(httpException: HttpException): String? {
        val errorBody = try {
            httpException.response()?.errorBody()?.string()
        } catch (_: Exception) {
            null
        }

        if (!errorBody.isNullOrBlank()) {
            try {
                val json = JsonParser.parseString(errorBody).asJsonObject
                val message = json.get("message")?.asString?.takeIf { it.isNotBlank() }
                if (message != null) {
                    return cleanErrorString(message, "Request failed. Please try again.")
                }
                val error = json.get("error")?.asString?.takeIf { it.isNotBlank() }
                if (error != null) {
                    return formatErrorCodeToUserFriendly(error)
                }
            } catch (_: Exception) {
                // Not valid JSON
            }
        }
        return null
    }

    /**
     * Reads the error body once and returns its machine-readable `error` code together with a
     * user-friendly message. The error body can only be consumed once per response.
     */
    fun extractCodeAndMessage(httpException: HttpException, defaultMessage: String): Pair<String?, String> {
        val errorBody = try {
            httpException.response()?.errorBody()?.string()
        } catch (_: Exception) {
            null
        }

        if (!errorBody.isNullOrBlank()) {
            try {
                val json = JsonParser.parseString(errorBody).asJsonObject
                val error = json.get("error")?.takeIf { it.isJsonPrimitive }?.asString?.takeIf { it.isNotBlank() }
                val message = json.get("message")?.takeIf { it.isJsonPrimitive }?.asString?.takeIf { it.isNotBlank() }
                return when {
                    message != null -> error to cleanErrorString(message, defaultMessage)
                    error != null -> error to formatErrorCodeToUserFriendly(error)
                    else -> null to mapHttpCode(httpException.code(), defaultMessage)
                }
            } catch (_: Exception) {
                // Not valid JSON
            }
        }
        return null to mapHttpCode(httpException.code(), defaultMessage)
    }

    fun mapHttpCode(code: Int, defaultMessage: String): String {
        return when (code) {
            400 -> "Invalid request. Please check your inputs."
            401 -> "Invalid Employee ID or password."
            403 -> "You do not have permission to perform this action."
            404 -> "Requested service could not be found."
            408 -> "Request timed out. Please try again."
            429 -> "Too many requests. Please wait a moment and try again."
            in 500..599 -> "SkyTrack server is temporarily unavailable. Please try again later."
            else -> defaultMessage
        }
    }

    fun cleanErrorString(raw: String, defaultMessage: String): String {
        // Match exact or prefix HTTP status codes like "HTTP 401 Unauthorized", "401", "HTTP 500", etc.
        val httpPattern = Regex("""\bHTTP\s*(\d{3})\b""", RegexOption.IGNORE_CASE)
        val match = httpPattern.find(raw)
        if (match != null) {
            val code = match.groupValues[1].toIntOrNull() ?: 0
            return mapHttpCode(code, defaultMessage)
        }
        if (raw.matches(Regex("""^\d{3}$"""))) {
            val code = raw.toIntOrNull() ?: 0
            return mapHttpCode(code, defaultMessage)
        }
        return raw
    }

    private fun formatErrorCodeToUserFriendly(error: String): String {
        return when (error.lowercase()) {
            "invalid_credentials" -> "Invalid Employee ID or password."
            "invalid_session", "session_expired", "auth_required" -> "Your session has expired. Please sign in again."
            "already_started" -> "Work has already been started for today."
            "not_started" -> "You have not started work today."
            "already_ended" -> "Work has already been ended for today."
            "already_on_break" -> "Cannot start another break while already on a break."
            "no_active_break" -> "Cannot end a break when no break is active."
            "active_break" -> "Cannot end work while a break is still active. Please end your break first."
            else -> error.replace('_', ' ').replaceFirstChar { it.uppercase() }
        }
    }
}
