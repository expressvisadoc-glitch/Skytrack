package com.skypass.skytrack.ui.profile

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val Crimson = Color(0xFFC62828)
private val TextMain = Color(0xFF1E293B)
private val TextMuted = Color(0xFF64748B)
private val ErrorRed = Color(0xFFDC2626)

/** Asks for the current password before fingerprint sign-in is turned on */
@Composable
fun BiometricSetupDialog(
    busy: Boolean,
    error: String?,
    onConfirm: (password: String) -> Unit,
    onDismiss: () -> Unit,
    onPasswordEdited: () -> Unit
) {
    var password by remember { mutableStateOf("") }
    var visible by remember { mutableStateOf(false) }
    val submit = { if (!busy && password.isNotEmpty()) onConfirm(password) }

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        shape = RoundedCornerShape(20.dp),
        containerColor = Color.White,
        icon = { Icon(Icons.Rounded.Fingerprint, contentDescription = null, tint = Crimson, modifier = Modifier.size(32.dp)) },
        title = {
            Text("Turn on fingerprint sign-in", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = TextMain)
        },
        text = {
            Column {
                Text(
                    "Enter your password to confirm. It is stored on this phone, locked by your fingerprint, " +
                        "so you can sign in without typing it.",
                    fontSize = 13.sp,
                    color = TextMuted
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = {
                        password = it
                        onPasswordEdited()
                    },
                    label = { Text("Password") },
                    singleLine = true,
                    enabled = !busy,
                    isError = error != null,
                    visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { submit() }),
                    trailingIcon = {
                        IconButton(onClick = { visible = !visible }) {
                            Icon(
                                if (visible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                contentDescription = if (visible) "Hide password" else "Show password",
                                tint = TextMuted
                            )
                        }
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Crimson,
                        focusedLabelColor = Crimson,
                        cursorColor = Crimson
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 14.dp)
                )
                if (error != null) {
                    Text(error, fontSize = 12.sp, color = ErrorRed, modifier = Modifier.padding(top = 6.dp))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = submit, enabled = !busy && password.isNotEmpty()) {
                if (busy) {
                    CircularProgressIndicator(strokeWidth = 2.dp, color = Crimson, modifier = Modifier.size(18.dp))
                } else {
                    Text("Continue", color = Crimson, fontWeight = FontWeight.SemiBold)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !busy) {
                Text("Cancel", color = TextMuted)
            }
        }
    )
}
