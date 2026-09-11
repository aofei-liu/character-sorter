package io.github.aofeiliu.charsorter.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@Composable
fun LoginScreen(busy: Boolean, onLogin: (String, String) -> Unit) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    val canSubmit = username.isNotBlank() && password.isNotBlank()

    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 28.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(bottom = 44.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            LoginMark()
            Text(
                "Character\nSorter".uppercase(),
                style = CharSorterType.AppTitle,
                color = CharSorterColor.Ink,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp)
            )
            Text(
                "Sign in with your charsorter.lndyn.com account.",
                style = CharSorterType.SubtitleBody,
                color = CharSorterColor.Muted,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 10.dp)
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            OutlinedTextField(
                value = username,
                onValueChange = { username = it },
                label = { Text("Username", style = CharSorterType.FieldLabel) },
                textStyle = CharSorterType.FieldValue,
                singleLine = true,
                shape = CharSorterShape.LoginField,
                colors = loginFieldColors(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Password", style = CharSorterType.FieldLabel) },
                textStyle = CharSorterType.FieldValue,
                singleLine = true,
                shape = CharSorterShape.LoginField,
                colors = loginFieldColors(),
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth()
            )
            if (busy) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    CircularProgressIndicator(
                        color = CharSorterColor.AccentDark,
                        trackColor = CharSorterColor.AccentLight.copy(alpha = 0.25f)
                    )
                    Text("Signing in…", style = CharSorterType.SpinnerLabel, color = CharSorterColor.Muted)
                }
            } else {
                Button(
                    onClick = { onLogin(username, password) },
                    enabled = canSubmit,
                    shape = CharSorterShape.Pill,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.Transparent,
                        disabledContainerColor = Color.Transparent
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 52.dp)
                        .padding(top = 6.dp)
                        .background(
                            brush = if (canSubmit) {
                                Brush.linearGradient(listOf(CharSorterColor.AccentLight, CharSorterColor.AccentDark))
                            } else {
                                SolidColor(CharSorterColor.DisabledFillBorder)
                            },
                            shape = CharSorterShape.Pill
                        )
                ) {
                    Text(
                        "Log in",
                        style = CharSorterType.ButtonPrimary,
                        color = if (canSubmit) CharSorterColor.OnAccent else CharSorterColor.DisabledText
                    )
                }
            }
        }
    }
}

@Composable
private fun loginFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = CharSorterColor.AccentDark,
    unfocusedBorderColor = CharSorterColor.AccentDark.copy(alpha = 0.45f),
    focusedContainerColor = Color.White.copy(alpha = 0.72f),
    unfocusedContainerColor = Color.White.copy(alpha = 0.72f),
    focusedLabelColor = CharSorterColor.Muted,
    unfocusedLabelColor = CharSorterColor.Muted,
    focusedTextColor = CharSorterColor.Ink,
    unfocusedTextColor = CharSorterColor.Ink,
    cursorColor = CharSorterColor.AccentDark
)

/** The ascending-bars app mark shown above the title on the login screen. */
@Composable
private fun LoginMark() {
    Box(
        modifier = Modifier
            .size(76.dp)
            .background(
                brush = Brush.linearGradient(listOf(Color.White, CharSorterColor.CardFillEnd)),
                shape = RoundedCornerShape(20.dp)
            )
            .padding(horizontal = 17.dp, vertical = 20.dp)
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.BottomCenter
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                Box(Modifier.size(width = 10.dp, height = 15.dp).background(CharSorterColor.AccentLight, RoundedCornerShape(3.dp)))
                Box(Modifier.size(width = 10.dp, height = 25.dp).background(CharSorterColor.AccentMid, RoundedCornerShape(3.dp)))
                Box(Modifier.size(width = 10.dp, height = 36.dp).background(CharSorterColor.AccentDark, RoundedCornerShape(3.dp)))
            }
        }
    }
}
