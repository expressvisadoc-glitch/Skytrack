package com.skypass.skytrack.data.auth

import android.content.Context
import android.content.ContextWrapper
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import javax.crypto.Cipher
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

/** Shows the system biometric prompt and unlocks a Keystore cipher with it. Strong biometrics (fingerprint) only. */
object BiometricAuth {

    enum class Availability { AVAILABLE, NOT_ENROLLED, UNAVAILABLE }

    sealed interface Outcome {
        data class Success(val cipher: Cipher) : Outcome
        /** The user closed the prompt or chose to use the password instead */
        data object Cancelled : Outcome
        data class Failed(val message: String) : Outcome
    }

    fun availability(context: Context): Availability =
        when (BiometricManager.from(context).canAuthenticate(BIOMETRIC_STRONG)) {
            BiometricManager.BIOMETRIC_SUCCESS -> Availability.AVAILABLE
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> Availability.NOT_ENROLLED
            else -> Availability.UNAVAILABLE
        }

    /** Must be called on the main thread; cancelling the coroutine closes the prompt */
    suspend fun authenticate(
        activity: FragmentActivity,
        title: String,
        subtitle: String,
        negativeText: String,
        cipher: Cipher
    ): Outcome = suspendCancellableCoroutine { continuation ->
        val prompt = BiometricPrompt(
            activity,
            ContextCompat.getMainExecutor(activity),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    val unlocked = result.cryptoObject?.cipher
                    if (!continuation.isActive) return
                    continuation.resume(
                        if (unlocked != null) Outcome.Success(unlocked)
                        else Outcome.Failed("Biometric check did not complete. Please try again.")
                    )
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    if (!continuation.isActive) return
                    val cancelled = errorCode == BiometricPrompt.ERROR_USER_CANCELED ||
                        errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON ||
                        errorCode == BiometricPrompt.ERROR_CANCELED
                    continuation.resume(if (cancelled) Outcome.Cancelled else Outcome.Failed(errString.toString()))
                }
                // onAuthenticationFailed (finger not recognised) is shown inside the prompt, which stays open
            }
        )
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setNegativeButtonText(negativeText)
            .setAllowedAuthenticators(BIOMETRIC_STRONG)
            .setConfirmationRequired(false)
            .build()
        prompt.authenticate(info, BiometricPrompt.CryptoObject(cipher))
        continuation.invokeOnCancellation { prompt.cancelAuthentication() }
    }
}

/** The FragmentActivity behind a Compose context, needed by the biometric prompt */
tailrec fun Context.findFragmentActivity(): FragmentActivity? = when (this) {
    is FragmentActivity -> this
    is ContextWrapper -> baseContext.findFragmentActivity()
    else -> null
}
