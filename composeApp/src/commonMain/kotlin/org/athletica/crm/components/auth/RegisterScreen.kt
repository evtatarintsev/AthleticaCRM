package org.athletica.crm.components.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import org.athletica.crm.platformAvailableTimezones
import org.athletica.crm.platformCurrentTimezone

/**
 * Экран регистрации новой организации.
 *
 * [errorMessage] — сообщение об ошибке, `null` если ошибки нет,
 * [onErrorDismissed] — вызывается после того, как snackbar скрыт,
 * [timezone] — выбранный часовой пояс,
 * [onTimezoneChange] — callback изменения часового пояса,
 * [onRegister] — callback кнопки "Зарегистрироваться",
 * [onNavigateToLogin] — вызывается при нажатии "Войти".
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RegisterScreen(
    errorMessage: String? = null,
    onErrorDismissed: () -> Unit = {},
    timezone: String = platformCurrentTimezone(),
    onTimezoneChange: (String) -> Unit = {},
    onRegister: (organizationName: String, name: String, email: String, password: String, timezone: String) -> Unit = { _, _, _, _, _ -> },
    onNavigateToLogin: () -> Unit = {},
) {
    val focusManager = LocalFocusManager.current
    val snackbarHostState = remember { SnackbarHostState() }

    var organizationName by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    val availableZones = remember { platformAvailableTimezones() }
    var timezoneExpanded by remember { mutableStateOf(false) }
    var timezoneQuery by remember { mutableStateOf("") }
    val filteredZones =
        remember(timezoneQuery) {
            if (timezoneQuery.isEmpty()) {
                availableZones
            } else {
                availableZones.filter { it.contains(timezoneQuery, ignoreCase = true) }
            }
        }

    LaunchedEffect(errorMessage) {
        if (errorMessage != null) {
            snackbarHostState.showSnackbar(errorMessage)
            onErrorDismissed()
        }
    }

    val isFormValid =
        organizationName.isNotBlank() &&
            name.isNotBlank() &&
            email.isNotBlank() &&
            password.isNotBlank()

    Scaffold(
        snackbarHost = {
            SnackbarHost(snackbarHostState) { data ->
                Snackbar(
                    snackbarData = data,
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        },
    ) { innerPadding ->
        Box(
            contentAlignment = Alignment.Center,
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.width(320.dp),
            ) {
                Text(
                    text = "Регистрация",
                    style = MaterialTheme.typography.headlineMedium,
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = organizationName,
                    onValueChange = { organizationName = it },
                    label = { Text("Название организации") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    keyboardActions =
                        KeyboardActions(
                            onNext = { focusManager.moveFocus(FocusDirection.Down) },
                        ),
                    modifier = Modifier.fillMaxWidth(),
                )

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Ваше имя") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    keyboardActions =
                        KeyboardActions(
                            onNext = { focusManager.moveFocus(FocusDirection.Down) },
                        ),
                    modifier = Modifier.fillMaxWidth(),
                )

                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Email") },
                    singleLine = true,
                    keyboardOptions =
                        KeyboardOptions(
                            keyboardType = KeyboardType.Email,
                            imeAction = ImeAction.Next,
                        ),
                    keyboardActions =
                        KeyboardActions(
                            onNext = { focusManager.moveFocus(FocusDirection.Down) },
                        ),
                    modifier = Modifier.fillMaxWidth(),
                )

                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Пароль") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions =
                        KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Done,
                        ),
                    keyboardActions =
                        KeyboardActions(
                            onDone = {
                                focusManager.clearFocus()
                                if (isFormValid) {
                                    onRegister(organizationName, name, email, password, timezone)
                                }
                            },
                        ),
                    modifier = Modifier.fillMaxWidth(),
                )

                ExposedDropdownMenuBox(
                    expanded = timezoneExpanded,
                    onExpandedChange = { expanded ->
                        timezoneExpanded = expanded
                        if (expanded) timezoneQuery = "" else timezoneQuery = ""
                    },
                ) {
                    OutlinedTextField(
                        value = if (timezoneExpanded) timezoneQuery else timezone,
                        onValueChange = { query ->
                            timezoneQuery = query
                            timezoneExpanded = true
                        },
                        label = { Text("Часовой пояс") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = timezoneExpanded) },
                        singleLine = true,
                        colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                        modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryEditable).fillMaxWidth(),
                    )
                    ExposedDropdownMenu(
                        expanded = timezoneExpanded,
                        onDismissRequest = { timezoneExpanded = false },
                    ) {
                        filteredZones.take(100).forEach { zone ->
                            DropdownMenuItem(
                                text = { Text(zone) },
                                onClick = {
                                    onTimezoneChange(zone)
                                    timezoneExpanded = false
                                },
                            )
                        }
                    }
                }

                Button(
                    onClick = { onRegister(organizationName, name, email, password, timezone) },
                    enabled = isFormValid,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Зарегистрироваться")
                }

                val primary = MaterialTheme.colorScheme.primary
                Text(
                    text =
                        buildAnnotatedString {
                            append("Уже есть аккаунт? ")
                            withLink(LinkAnnotation.Clickable(tag = "login") { onNavigateToLogin() }) {
                                withStyle(SpanStyle(color = primary, textDecoration = TextDecoration.Underline)) {
                                    append("Войти")
                                }
                            }
                        },
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}
